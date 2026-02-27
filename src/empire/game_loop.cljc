;; mutation-tested: 2026-02-25
(ns empire.game-loop
  "Round orchestration: start-new-round, advance-game, update-map.
   Delegates round setup to round-setup and item processing to item-processing."
  (:require [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.computer.army :as army]
            [empire.computer.land-ho :as land-ho]
            [empire.computer.land-objectives :as land-objectives]
            [empire.computer.production :as computer-production]
            [empire.movement.pathfinding :as pathfinding]
            [empire.movement.pathfinding-bfs :as pathfinding-bfs]
            [empire.movement.visibility :as visibility]
            [empire.player.production :as production]
            [empire.game-loop.round-setup :as round-setup]
            [empire.game-loop.item-processing :as item-processing]
            [empire.ui.util.rendering.format :as ru]))

(defn update-player-map
  "Reveals cells near player-owned units on the visible map."
  []
  (visibility/update-combatant-map atoms/player-map :player))

(defn update-computer-map
  "Updates the computer's visible map by revealing cells near computer-owned units."
  []
  (visibility/update-combatant-map atoms/computer-map :computer))

(defn build-player-items
  "Builds list of player city/unit coordinates to process this round."
  []
  (for [i (range (count @atoms/game-map))
        j (range (count (first @atoms/game-map)))
        :let [cell (get-in @atoms/game-map [i j])]
        :when (or (= (:city-status cell) :player)
                  (= (:owner (:contents cell)) :player))]
    [i j]))

(defn build-computer-items
  "Builds list of computer city/unit coordinates to process this round."
  []
  (for [i (range (count @atoms/game-map))
        j (range (count (first @atoms/game-map)))
        :let [cell (get-in @atoms/game-map [i j])]
        :when (or (= (:city-status cell) :computer)
                  (= (:owner (:contents cell)) :computer))]
    [i j]))

(defn item-processed
  "Called when user input has been processed for current item.
   Victory check happens in item-processing/process-player-items-batch."
  []
  (reset! atoms/waiting-for-input false)
  (reset! atoms/cells-needing-attention []))

;; Delegate round-setup functions for backward compatibility
(def remove-dead-units round-setup/remove-dead-units)
(def reset-steps-remaining round-setup/reset-steps-remaining)
(def wake-airport-fighters round-setup/wake-airport-fighters)
(def wake-carrier-fighters round-setup/wake-carrier-fighters)
(def consume-sentry-fighter-fuel round-setup/consume-sentry-fighter-fuel)
(def wake-sentries-seeing-enemy round-setup/wake-sentries-seeing-enemy)
(def move-satellites round-setup/move-satellites)
(def repair-damaged-ships round-setup/repair-damaged-ships)

;; Delegate item-processing functions for backward compatibility
(def move-current-unit item-processing/move-current-unit)
(def move-explore-unit item-processing/move-explore-unit)
(def move-coastline-unit item-processing/move-coastline-unit)

(defn start-new-round
  "Starts a new round by building player and computer items lists and updating game state."
  []
  (swap! atoms/round-number inc)
  (pathfinding/clear-path-cache)
  (pathfinding-bfs/clear-bfs-caches)
  (land-objectives/clear-continent-cache!)
  (round-setup/move-satellites)
  (round-setup/consume-sentry-fighter-fuel)
  (round-setup/wake-sentries-seeing-enemy)
  (round-setup/remove-dead-units)
  (production/update-production)
  (round-setup/repair-damaged-ships)
  (round-setup/reset-steps-remaining)
  (round-setup/wake-airport-fighters)
  ;; Carrier fighters stay asleep until 'u' is pressed - do not auto-wake at round start
  (reset! atoms/claimed-objectives #{})
  (reset! atoms/claimed-transport-targets #{})
  (reset! atoms/claimed-patrol-targets #{})
  (land-ho/assign-land-ho-invasion)
  (let [player-items (vec (build-player-items))
        computer-items (vec (build-computer-items))]
    (reset! atoms/player-items player-items)
    (reset! atoms/computer-items computer-items)
    (computer-production/rebuild-country-stats!)
    (army/assign-city-attacks)
    ;; Check for game over: no player cities or units
    (when (and @atoms/game-over-check-enabled (empty? player-items))
      (reset! atoms/paused true)
      (reset! atoms/error-message "****GAME OVER*****")
      (reset! atoms/error-until Long/MAX_VALUE)
      (reset! atoms/map-to-display :actual-map))
    ;; Check for victory: no computer cities or units
    (when (and @atoms/game-over-check-enabled (empty? computer-items))
      (reset! atoms/paused true)
      (reset! atoms/error-message "****YOU WIN!*****")
      (reset! atoms/error-until Long/MAX_VALUE)
      (reset! atoms/map-to-display :actual-map)))
  (reset! atoms/waiting-for-input false)
  (reset! atoms/attention-message "")
  (reset! atoms/cells-needing-attention [])
  (reset! atoms/production-status (ru/format-production-status @atoms/game-map @atoms/player-map)))

(defn- both-lists-empty? []
  (and (empty? @atoms/player-items) (empty? @atoms/computer-items)))

(defn- handle-pause-or-new-round []
  (if @atoms/pause-requested
    (do (reset! atoms/paused true) (reset! atoms/pause-requested false))
    (start-new-round)))

(defn advance-game
  "Advances the game by processing player items, then computer items.
   Processes multiple non-attention items per frame for faster rounds."
  []
  (cond
    @atoms/load-menu-open nil
    @atoms/paused nil
    (both-lists-empty?) (handle-pause-or-new-round)
    @atoms/waiting-for-input nil
    (seq @atoms/player-items) (item-processing/process-player-items-batch)
    :else (item-processing/process-computer-items)))

(defn advance-game-batch
  "Calls advance-game up to advances-per-frame times per frame.
   Stops early when paused, waiting for input, or no items to process."
  []
  (loop [remaining config/advances-per-frame]
    (when (pos? remaining)
      (advance-game)
      (when (and (> remaining 1)
                 (not @atoms/paused)
                 (not @atoms/waiting-for-input)
                 (or (seq @atoms/player-items) (seq @atoms/computer-items)))
        (recur (dec remaining))))))

(defn toggle-pause
  "Toggles pause state. If running, requests pause at end of round.
   If paused, resumes immediately."
  []
  (if @atoms/paused
    (do
      (reset! atoms/paused false)
      (reset! atoms/pause-requested false))
    (reset! atoms/pause-requested true)))

(defn step-one-round
  "When paused, advances one round then pauses again."
  []
  (when @atoms/paused
    (reset! atoms/paused false)
    (reset! atoms/pause-requested true)
    (when (and (empty? @atoms/player-items) (empty? @atoms/computer-items))
      (start-new-round))))

(defn update-map
  "Updates the game map state."
  []
  (update-player-map)
  (update-computer-map)
  (advance-game))
