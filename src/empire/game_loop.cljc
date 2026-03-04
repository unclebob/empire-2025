;; mutation-tested: 2026-02-25
(ns empire.game-loop
  "Round orchestration: start-new-round, advance-game, update-map.
   Delegates round setup to round-setup and item processing to item-processing."
  (:require [empire.application.production-status :as production-status]
            [empire.application.movement-services :as movement-services]
            [empire.application.runtime :as app-runtime]
            [empire.config :as config]
            [empire.computer.army :as army]
            [empire.computer.land-ho :as land-ho]
            [empire.computer.land-objectives :as land-objectives]
            [empire.computer.production :as computer-production]
            [empire.computer.threat-response :as threat-response]
            [empire.game-loop.round-setup :as round-setup]
            [empire.game-loop.item-processing :as item-processing]
            [empire.player.production :as player-production]))

(def ^:private state-ctx
  (delay (app-runtime/default-state-ctx)))

(defn- current-world
  []
  ((:load-world @state-ctx)))

(defn- read-runtime-state
  [k]
  ((:read-runtime-state @state-ctx) k))

(defn- write-runtime-state!
  [k v]
  ((:write-runtime-state! @state-ctx) k v))

(defn- update-runtime-state!
  [k f & args]
  (let [current (read-runtime-state k)
        next-state (apply f current args)]
    (write-runtime-state! k next-state)))

(defn update-player-map
  "Reveals cells near player-owned units on the visible map."
  []
  (when-let [updated (movement-services/update-combatant-map-state
                      (read-runtime-state :player-map)
                      :player
                      (current-world))]
    (write-runtime-state! :player-map updated)))

(defn update-computer-map
  "Updates the computer's visible map by revealing cells near computer-owned units."
  []
  (when-let [updated (movement-services/update-combatant-map-state
                      (read-runtime-state :computer-map)
                      :computer
                      (current-world))]
    (write-runtime-state! :computer-map updated)))

(defn build-player-items
  "Builds list of player city/unit coordinates to process this round."
  []
  (let [world (current-world)]
    (for [i (range (count world))
          j (range (count (first world)))
          :let [cell (get-in world [i j])]
        :when (or (= (:city-status cell) :player)
                  (= (:owner (:contents cell)) :player))]
      [i j])))

(defn build-computer-items
  "Builds list of computer city/unit coordinates to process this round."
  []
  (let [world (current-world)]
    (for [i (range (count world))
          j (range (count (first world)))
          :let [cell (get-in world [i j])]
        :when (or (= (:city-status cell) :computer)
                  (= (:owner (:contents cell)) :computer))]
      [i j])))

(defn item-processed
  "Called when user input has been processed for current item.
   Victory check happens in item-processing/process-player-items-batch."
  []
  (write-runtime-state! :waiting-for-input false)
  (write-runtime-state! :cells-needing-attention []))

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
  (update-runtime-state! :round-number inc)
  (movement-services/clear-path-cache!)
  (movement-services/clear-bfs-caches!)
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
  (write-runtime-state! :claimed-objectives #{})
  (write-runtime-state! :claimed-transport-targets #{})
  (write-runtime-state! :claimed-patrol-targets #{})
  (land-ho/assign-land-ho-invasion)
  (let [player-items (vec (build-player-items))
        computer-items (vec (build-computer-items))]
    (write-runtime-state! :player-items player-items)
    (write-runtime-state! :computer-items computer-items)
    (computer-production/rebuild-country-stats!)
    (army/assign-city-attacks)
    ;; Check for game over: no player cities or units
    (when (and (read-runtime-state :game-over-check-enabled) (empty? player-items))
      (write-runtime-state! :paused true)
      (write-runtime-state! :error-message "****GAME OVER*****")
      (write-runtime-state! :error-until Long/MAX_VALUE)
      (write-runtime-state! :map-to-display :actual-map))
    ;; Check for victory: no computer cities or units
    (when (and (read-runtime-state :game-over-check-enabled) (empty? computer-items))
      (write-runtime-state! :paused true)
      (write-runtime-state! :error-message "****YOU WIN!*****")
      (write-runtime-state! :error-until Long/MAX_VALUE)
      (write-runtime-state! :map-to-display :actual-map)))
  (write-runtime-state! :waiting-for-input false)
  (write-runtime-state! :attention-message "")
  (write-runtime-state! :cells-needing-attention [])
  (write-runtime-state! :production-status
                        (production-status/format-production-status (current-world)
                                                                    (read-runtime-state :player-map))))

(defn- both-lists-empty? []
  (and (empty? (read-runtime-state :player-items))
       (empty? (read-runtime-state :computer-items))))

(defn- handle-pause-or-new-round []
  (if (read-runtime-state :pause-requested)
    (do (write-runtime-state! :paused true)
        (write-runtime-state! :pause-requested false))
    (start-new-round)))

(defn advance-game
  "Advances the game by processing player items, then computer items.
   Processes multiple non-attention items per frame for faster rounds."
  []
  (cond
    (read-runtime-state :load-menu-open) nil
    (read-runtime-state :paused) nil
    (both-lists-empty?) (handle-pause-or-new-round)
    (read-runtime-state :waiting-for-input) nil
    (seq (read-runtime-state :player-items)) (item-processing/process-player-items-batch)
    :else (item-processing/process-computer-items)))

(defn advance-game-batch
  "Calls advance-game up to advances-per-frame times per frame.
   Stops early when paused, waiting for input, or no items to process."
  []
  (loop [remaining config/advances-per-frame]
    (when (pos? remaining)
      (advance-game)
      (when (and (> remaining 1)
                 (not (read-runtime-state :paused))
                 (not (read-runtime-state :waiting-for-input))
                 (or (seq (read-runtime-state :player-items))
                     (seq (read-runtime-state :computer-items))))
        (recur (dec remaining))))))

(defn toggle-pause
  "Toggles pause state. If running, requests pause at end of round.
   If paused, resumes immediately."
  []
  (if (read-runtime-state :paused)
    (do
      (write-runtime-state! :paused false)
      (write-runtime-state! :pause-requested false))
    (write-runtime-state! :pause-requested true)))

(defn step-one-round
  "When paused, advances one round then pauses again."
  []
  (when (read-runtime-state :paused)
    (write-runtime-state! :paused false)
    (write-runtime-state! :pause-requested true)
    (when (and (empty? (read-runtime-state :player-items))
               (empty? (read-runtime-state :computer-items)))
      (start-new-round))))

(defn update-map
  "Updates the game map state."
  []
  (update-player-map)
  (update-computer-map)
  (advance-game))
