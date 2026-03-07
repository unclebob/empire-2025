;; mutation-tested: 2026-02-25
(ns empire.game.loop.core
  "Round orchestration: start-new-round, advance-game, update-map.
   Delegates round setup to round-setup and item processing to item-processing."
  (:require [empire.game.production-status :as production-status]
            [empire.game-mechanics.movement.visibility :as visibility]
            [empire.game-mechanics.movement.pathfinding :as pathfinding]
            [empire.game-mechanics.movement.pathfinding-bfs :as pathfinding-bfs]
            [empire.state.api :as sa]
            [empire.config.core :as config]
            [empire.computer.army :as army]
            [empire.computer.land-ho :as land-ho]
            [empire.computer.land-objectives :as land-objectives]
            [empire.computer.production :as computer-production]
            [empire.computer.threat-response :as threat-response]
            [empire.game.loop.round-setup :as round-setup]
            [empire.game.loop.item-processing :as item-processing]
            [empire.player.production :as player-production]))

(defn update-player-map
  "Reveals cells near player-owned units on the visible map."
  []
  (when-let [updated (visibility/update-combatant-map-state
                      (sa/read-state :player-map)
                      :player
                      (sa/current-world))]
    (sa/write-state! :player-map updated)))

(defn update-computer-map
  "Updates the computer's visible map by revealing cells near computer-owned units."
  []
  (when-let [updated (visibility/update-combatant-map-state
                      (sa/read-state :computer-map)
                      :computer
                      (sa/current-world))]
    (sa/write-state! :computer-map updated)))

(defn build-player-items
  "Builds list of player city/unit coordinates to process this round."
  []
  (let [world (sa/current-world)]
    (for [i (range (count world))
          j (range (count (first world)))
          :let [cell (get-in world [i j])]
        :when (or (= (:city-status cell) :player)
                  (= (:owner (:contents cell)) :player))]
      [i j])))

(defn build-computer-items
  "Builds list of computer city/unit coordinates to process this round."
  []
  (let [world (sa/current-world)]
    (for [i (range (count world))
          j (range (count (first world)))
          :let [cell (get-in world [i j])]
        :when (or (= (:city-status cell) :computer)
                  (= (:owner (:contents cell)) :computer))]
      [i j])))

(defn- declare-game-over!
  [message]
  (sa/write-state! :paused true)
  (sa/write-state! :error-message message)
  (sa/write-state! :error-until Long/MAX_VALUE)
  (sa/write-state! :map-to-display :actual-map)
  (sa/write-state! :player-items [])
  (sa/write-state! :computer-items []))

(defn item-processed
  "Called when user input has been processed for current item.
   Victory check happens in item-processing/process-player-items-batch."
  []
  (sa/write-state! :waiting-for-input false)
  (sa/write-state! :cells-needing-attention []))

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
  (sa/update-state! :round-number inc)
  (pathfinding/clear-path-cache)
  (pathfinding-bfs/clear-bfs-caches)
  (land-objectives/clear-continent-cache!)
  (round-setup/move-satellites)
  (round-setup/consume-sentry-fighter-fuel)
  (round-setup/wake-sentries-seeing-enemy)
  (round-setup/remove-dead-units)
  (round-setup/mark-lake-locked-ships)
  (round-setup/evacuate-lake-patrol-boats)
  (player-production/update-production)
  (round-setup/repair-damaged-ships)
  (round-setup/reset-steps-remaining)
  (round-setup/wake-airport-fighters)
  (threat-response/on-round-start!)
  ;; Carrier fighters stay asleep until 'u' is pressed - do not auto-wake at round start
  (sa/write-state! :claimed-objectives #{})
  (sa/write-state! :claimed-transport-targets #{})
  (sa/write-state! :claimed-patrol-targets #{})
  (land-ho/assign-land-ho-invasion)
  (let [player-items (vec (build-player-items))
        computer-items (vec (build-computer-items))]
    (sa/write-state! :player-items player-items)
    (sa/write-state! :computer-items computer-items)
    (computer-production/rebuild-country-stats!)
    (army/assign-city-attacks)
    (when (sa/read-state :game-over-check-enabled)
      (cond
        (empty? player-items)
        (declare-game-over! "****GAME OVER*****  You Lose")

        (empty? computer-items)
        (declare-game-over! "****GAME OVER*****  I Resign  YOU WIN!")

        :else nil)))
  (sa/write-state! :waiting-for-input false)
  (sa/write-state! :attention-message "")
  (sa/write-state! :cells-needing-attention [])
  (sa/write-state! :production-status
                        (production-status/format-production-status (sa/current-world)
                                                                    (sa/read-state :player-map))))

(defn- both-lists-empty? []
  (and (empty? (sa/read-state :player-items))
       (empty? (sa/read-state :computer-items))))

(defn- handle-pause-or-new-round []
  (if (sa/read-state :pause-requested)
    (do (sa/write-state! :paused true)
        (sa/write-state! :pause-requested false))
    (start-new-round)))

(defn advance-game
  "Advances the game by processing player items, then computer items.
   Processes multiple non-attention items per frame for faster rounds."
  []
  (cond
    (sa/read-state :load-menu-open) nil
    (sa/read-state :save-menu-open) nil
    (sa/read-state :paused) nil
    (both-lists-empty?) (handle-pause-or-new-round)
    (sa/read-state :waiting-for-input) nil
    (seq (sa/read-state :player-items)) (item-processing/process-player-items-batch)
    :else (item-processing/process-computer-items)))

(defn advance-game-batch
  "Calls advance-game up to advances-per-frame times per frame.
   Stops early when paused, waiting for input, or no items to process."
  []
  (loop [remaining config/advances-per-frame]
    (when (pos? remaining)
      (advance-game)
      (when (and (> remaining 1)
                 (not (sa/read-state :paused))
                 (not (sa/read-state :waiting-for-input))
                 (or (seq (sa/read-state :player-items))
                     (seq (sa/read-state :computer-items))))
        (recur (dec remaining))))))

(defn toggle-pause
  "Toggles pause state. If running, requests pause at end of round.
   If paused, resumes immediately."
  []
  (if (sa/read-state :paused)
    (do
      (sa/write-state! :paused false)
      (sa/write-state! :pause-requested false))
    (sa/write-state! :pause-requested true)))

(defn step-one-round
  "When paused, advances one round then pauses again."
  []
  (when (sa/read-state :paused)
    (sa/write-state! :paused false)
    (sa/write-state! :pause-requested true)
    (when (and (empty? (sa/read-state :player-items))
               (empty? (sa/read-state :computer-items)))
      (start-new-round))))

(defn update-map
  "Updates the game map state."
  []
  (update-player-map)
  (update-computer-map)
  (advance-game))
