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

(defn- handicap-active?
  []
  (pos? (or (sa/read-state :handicap-rounds-remaining) 0)))

(defn- current-player-items
  []
  (if (handicap-active?)
    []
    (vec (build-player-items))))

(defn- update-handicap-before-round!
  []
  (let [remaining (sa/read-state :handicap-rounds-remaining)
        display (sa/read-state :handicap-display-rounds)]
    (cond
      (pos? (or remaining 0))
      (let [next-remaining (dec remaining)]
        (sa/write-state! :handicap-rounds-remaining next-remaining)
        (sa/write-state! :handicap-display-rounds next-remaining))

      (zero? (or remaining 0))
      (when (zero? (or display -1))
        (sa/write-state! :handicap-display-rounds nil))

      :else nil)))

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
    (when (sa/read-state :game-over-check-enabled)
      (cond
        (and (empty? player-items) (not (handicap-active?)))
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
    (do
      (when (pos? (sa/read-state :round-number))
        (update-handicap-before-round!))
      (start-new-round))))

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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T12:00:03.546677-05:00", :module-hash "-1360302862", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 17, :hash "-153605337"} {:id "defn/update-player-map", :kind "defn", :line 19, :end-line 26, :hash "2102522450"} {:id "defn/update-computer-map", :kind "defn", :line 28, :end-line 35, :hash "-1953237338"} {:id "defn/build-player-items", :kind "defn", :line 37, :end-line 46, :hash "1741102605"} {:id "defn/build-computer-items", :kind "defn", :line 48, :end-line 57, :hash "-1394268341"} {:id "defn-/declare-game-over!", :kind "defn-", :line 59, :end-line 66, :hash "1518773855"} {:id "defn/item-processed", :kind "defn", :line 68, :end-line 73, :hash "-1477047071"} {:id "def/remove-dead-units", :kind "def", :line 76, :end-line 76, :hash "2048804574"} {:id "def/reset-steps-remaining", :kind "def", :line 77, :end-line 77, :hash "-2073169805"} {:id "def/wake-airport-fighters", :kind "def", :line 78, :end-line 78, :hash "879726790"} {:id "def/wake-carrier-fighters", :kind "def", :line 79, :end-line 79, :hash "840162374"} {:id "def/consume-sentry-fighter-fuel", :kind "def", :line 80, :end-line 80, :hash "-647149942"} {:id "def/wake-sentries-seeing-enemy", :kind "def", :line 81, :end-line 81, :hash "-1383325288"} {:id "def/move-satellites", :kind "def", :line 82, :end-line 82, :hash "493297701"} {:id "def/repair-damaged-ships", :kind "def", :line 83, :end-line 83, :hash "2109716449"} {:id "def/move-current-unit", :kind "def", :line 86, :end-line 86, :hash "122457069"} {:id "def/move-explore-unit", :kind "def", :line 87, :end-line 87, :hash "-1327293082"} {:id "def/move-coastline-unit", :kind "def", :line 88, :end-line 88, :hash "-314642053"} {:id "defn/start-new-round", :kind "defn", :line 90, :end-line 133, :hash "-271215164"} {:id "defn-/both-lists-empty?", :kind "defn-", :line 135, :end-line 137, :hash "-2026920266"} {:id "defn-/handle-pause-or-new-round", :kind "defn-", :line 139, :end-line 143, :hash "641553017"} {:id "defn/advance-game", :kind "defn", :line 145, :end-line 156, :hash "310765398"} {:id "defn/advance-game-batch", :kind "defn", :line 158, :end-line 170, :hash "-782026222"} {:id "defn/toggle-pause", :kind "defn", :line 172, :end-line 180, :hash "1938644392"} {:id "defn/step-one-round", :kind "defn", :line 182, :end-line 190, :hash "-1584583582"} {:id "defn/update-map", :kind "defn", :line 192, :end-line 197, :hash "-297625781"}]}
;; clj-mutate-manifest-end
