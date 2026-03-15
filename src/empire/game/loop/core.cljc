(ns empire.game.loop.core
  "Round orchestration: start-new-round, advance-game, update-map.
   Delegates round setup to round-setup and item processing to item-processing."
  (:require [empire.game.production-status :as production-status]
            [empire.game-mechanics.debug.integrity :as integrity]
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
            [empire.player.production :as player-production]
            [empire.game.loop.control-decisions :as decisions]))

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

(defn- handicap-active?
  []
  (decisions/handicap-active? (sa/read-state :handicap-rounds-remaining)))

(defn- current-player-items
  []
  (decisions/current-player-items (sa/read-state :handicap-rounds-remaining)
                                  (build-player-items)))

(defn- update-handicap-before-round!
  []
  (let [remaining (sa/read-state :handicap-rounds-remaining)
        display (sa/read-state :handicap-display-rounds)
        decision (decisions/handicap-update remaining display)]
    (case (:action decision)
      :count-down (do
                    (sa/write-state! :handicap-rounds-remaining (:remaining decision))
                    (sa/write-state! :handicap-display-rounds (:display decision)))
      :clear-display (sa/write-state! :handicap-display-rounds nil)
      nil)))

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
  (let [player-items (current-player-items)
        computer-items (vec (build-computer-items))]
    (sa/write-state! :player-items player-items)
    (sa/write-state! :computer-items computer-items)
    (computer-production/rebuild-country-stats!)
    (army/assign-city-attacks)
    (when-let [game-over (decisions/game-over-action (sa/read-state :game-over-check-enabled)
                                                     player-items
                                                     computer-items
                                                     (sa/read-state :handicap-rounds-remaining))]
      (declare-game-over! (:message game-over))))
  (sa/write-state! :waiting-for-input false)
  (sa/write-state! :attention-message "")
  (sa/write-state! :cells-needing-attention [])
  (sa/write-state! :production-status
                   (production-status/format-production-status (sa/current-world)
                                                               (sa/read-state :player-map)))
  (integrity/check-world-integrity!))

(defn- both-lists-empty? []
  (and (empty? (sa/read-state :player-items))
       (empty? (sa/read-state :computer-items))))

(defn advance-game
  "Advances the game by processing player items, then computer items.
   Processes multiple non-attention items per frame for faster rounds."
  []
  (case (decisions/advance-game-action {:load-menu-open (sa/read-state :load-menu-open)
                                        :save-menu-open (sa/read-state :save-menu-open)
                                        :paused (sa/read-state :paused)
                                        :both-lists-empty? (both-lists-empty?)
                                        :pause-requested (sa/read-state :pause-requested)
                                        :waiting-for-input (sa/read-state :waiting-for-input)
                                        :player-items (sa/read-state :player-items)})
    :pause (do
             (sa/write-state! :paused true)
             (sa/write-state! :pause-requested false))
    :new-round (do
                 (when (pos? (sa/read-state :round-number))
                   (update-handicap-before-round!))
                 (start-new-round))
    :process-player (item-processing/process-player-items-batch)
    :process-computer (item-processing/process-computer-items)
    nil))

(defn advance-game-batch
  "Calls advance-game up to advances-per-frame times per frame.
   Stops early when paused, waiting for input, or no items to process."
  []
  (loop [remaining config/advances-per-frame]
    (when (pos? remaining)
      (advance-game)
      (when (decisions/continue-batch? remaining
                                       (sa/read-state :paused)
                                       (sa/read-state :waiting-for-input)
                                       (sa/read-state :player-items)
                                       (sa/read-state :computer-items))
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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-15T16:30:07.477367-05:00", :module-hash "1787457006", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 19, :hash "-333995231"} {:id "defn/update-player-map", :kind "defn", :line 21, :end-line 28, :hash "2102522450"} {:id "defn/update-computer-map", :kind "defn", :line 30, :end-line 37, :hash "-1953237338"} {:id "defn/build-player-items", :kind "defn", :line 39, :end-line 48, :hash "1741102605"} {:id "defn/build-computer-items", :kind "defn", :line 50, :end-line 59, :hash "-1394268341"} {:id "defn-/declare-game-over!", :kind "defn-", :line 61, :end-line 68, :hash "1518773855"} {:id "defn/item-processed", :kind "defn", :line 70, :end-line 75, :hash "-1477047071"} {:id "defn-/handicap-active?", :kind "defn-", :line 77, :end-line 79, :hash "-1620006900"} {:id "defn-/current-player-items", :kind "defn-", :line 81, :end-line 84, :hash "461726394"} {:id "defn-/update-handicap-before-round!", :kind "defn-", :line 86, :end-line 96, :hash "-293884731"} {:id "def/remove-dead-units", :kind "def", :line 99, :end-line 99, :hash "2048804574"} {:id "def/reset-steps-remaining", :kind "def", :line 100, :end-line 100, :hash "-2073169805"} {:id "def/wake-airport-fighters", :kind "def", :line 101, :end-line 101, :hash "879726790"} {:id "def/wake-carrier-fighters", :kind "def", :line 102, :end-line 102, :hash "840162374"} {:id "def/consume-sentry-fighter-fuel", :kind "def", :line 103, :end-line 103, :hash "-647149942"} {:id "def/wake-sentries-seeing-enemy", :kind "def", :line 104, :end-line 104, :hash "-1383325288"} {:id "def/move-satellites", :kind "def", :line 105, :end-line 105, :hash "493297701"} {:id "def/repair-damaged-ships", :kind "def", :line 106, :end-line 106, :hash "2109716449"} {:id "def/move-current-unit", :kind "def", :line 109, :end-line 109, :hash "122457069"} {:id "def/move-explore-unit", :kind "def", :line 110, :end-line 110, :hash "-1327293082"} {:id "def/move-coastline-unit", :kind "def", :line 111, :end-line 111, :hash "-314642053"} {:id "defn/start-new-round", :kind "defn", :line 113, :end-line 153, :hash "-1228699986"} {:id "defn-/both-lists-empty?", :kind "defn-", :line 155, :end-line 157, :hash "-2026920266"} {:id "defn/advance-game", :kind "defn", :line 159, :end-line 179, :hash "1975527481"} {:id "defn/advance-game-batch", :kind "defn", :line 181, :end-line 193, :hash "1612417613"} {:id "defn/toggle-pause", :kind "defn", :line 195, :end-line 203, :hash "1938644392"} {:id "defn/step-one-round", :kind "defn", :line 205, :end-line 213, :hash "-1584583582"} {:id "defn/update-map", :kind "defn", :line 215, :end-line 220, :hash "-297625781"}]}
;; clj-mutate-manifest-end
