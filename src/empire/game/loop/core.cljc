(ns empire.game.loop.core
  "Round orchestration: start-new-round, advance-game, update-map.
   Delegates round setup to round-setup and item processing to item-processing."
  (:require [empire.game.production-status :as production-status]
            [empire.game-mechanics.debug.logging :as debug-logging]
            [empire.game-mechanics.debug.integrity :as integrity]
            [empire.game-mechanics.unit-stamping :as unit-stamping]
            [empire.game-mechanics.visibility :as visibility]
            [empire.game-mechanics.movement.pathfinding :as pathfinding]
            [empire.game-mechanics.movement.pathfinding-bfs :as pathfinding-bfs]
            [empire.state.api :as sa]
            [empire.config.core :as config]
            [empire.computer.army :as army]
            [empire.computer.land-objectives :as land-objectives]
            [empire.computer.production :as computer-production]
            [empire.computer.threat-response-impl :as threat-response]
            [empire.game.loop.round-setup :as round-setup]
            [empire.game.loop.item-processing :as item-processing]
            [empire.player.production :as player-production]
            [empire.game.loop.control-decisions :as decisions]
            [empire.game.loop.monitor :as monitor]
            [empire.computer.fighter.movement-impl :as fighter-movement-impl]
            [empire.computer.fighter.flight-decisions :as flight-decisions]
            [empire.computer.fighter.exploration :as fighter-exploration]
            [empire.computer.production.stats :as production-stats]
            [empire.computer.production.decisions :as production-decisions]
            [empire.computer.ship.carrier :as carrier]
            [empire.computer.early-game.theater :as theater]
            [empire.computer.shared.transport-query :as transport-query]))

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

(defn- apply-round-start-state!
  [{:keys [player-items computer-items game-over waiting-for-input attention-message cells-needing-attention]}]
  (sa/write-state! :player-items player-items)
  (sa/write-state! :computer-items computer-items)
  (when game-over
    (declare-game-over! (:message game-over)))
  (sa/write-state! :waiting-for-input waiting-for-input)
  (sa/write-state! :attention-message attention-message)
  (sa/write-state! :cells-needing-attention cells-needing-attention))

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
  (fighter-movement-impl/clear-refueling-cache!)
  (flight-decisions/clear-active-targets-cache!)
  (fighter-exploration/clear-unexplored-distance-cache!)
  (production-stats/clear-asset-cache!)
  (production-decisions/clear-produced-transport-cache!)
  (carrier/clear-carrier-caches!)
  (theater/clear-theater-caches!)
  (transport-query/clear-loading-transport-cache!)
  (unit-stamping/backfill-missing-computer-unit-ids!)
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
  (debug-logging/begin-computer-unit-log-round!)
  (let [player-items (current-player-items)
        computer-items (vec (build-computer-items))
        round-state (decisions/round-start-state
                     {:handicap-rounds-remaining (sa/read-state :handicap-rounds-remaining)
                      :player-items player-items
                      :computer-items computer-items
                      :game-over-check-enabled (sa/read-state :game-over-check-enabled)})]
    (apply-round-start-state! round-state)
    (computer-production/rebuild-country-stats!)
    (army/assign-city-attacks)
    (army/assign-transport-staging))
  (sa/write-state! :production-status
                   (production-status/format-production-status (sa/current-world)
                                                               (sa/read-state :player-map)))
  (integrity/check-world-integrity!))

(defn start-new-round-timed
  "Like start-new-round but returns a vector of [phase-name elapsed-ms] pairs."
  []
  (let [phases (transient [])]
    (conj! phases (monitor/time-phase :increment-round (sa/update-state! :round-number inc)))
    (conj! phases (monitor/time-phase :clear-path-cache (pathfinding/clear-path-cache)))
    (conj! phases (monitor/time-phase :clear-bfs-caches (pathfinding-bfs/clear-bfs-caches)))
    (conj! phases (monitor/time-phase :clear-continent-cache (land-objectives/clear-continent-cache!)))
    (conj! phases (monitor/time-phase :clear-refueling-cache (fighter-movement-impl/clear-refueling-cache!)))
    (conj! phases (monitor/time-phase :clear-flight-targets-cache (flight-decisions/clear-active-targets-cache!)))
    (conj! phases (monitor/time-phase :clear-unexplored-cache (fighter-exploration/clear-unexplored-distance-cache!)))
    (conj! phases (monitor/time-phase :clear-asset-cache (production-stats/clear-asset-cache!)))
    (conj! phases (monitor/time-phase :clear-transport-cache (production-decisions/clear-produced-transport-cache!)))
    (conj! phases (monitor/time-phase :clear-carrier-caches (carrier/clear-carrier-caches!)))
    (conj! phases (monitor/time-phase :clear-theater-caches (theater/clear-theater-caches!)))
    (conj! phases (monitor/time-phase :clear-transport-query-cache (transport-query/clear-loading-transport-cache!)))
    (conj! phases (monitor/time-phase :backfill-unit-ids (unit-stamping/backfill-missing-computer-unit-ids!)))
    (conj! phases (monitor/time-phase :move-satellites (round-setup/move-satellites)))
    (conj! phases (monitor/time-phase :consume-sentry-fuel (round-setup/consume-sentry-fighter-fuel)))
    (conj! phases (monitor/time-phase :wake-sentries (round-setup/wake-sentries-seeing-enemy)))
    (conj! phases (monitor/time-phase :remove-dead-units (round-setup/remove-dead-units)))
    (conj! phases (monitor/time-phase :mark-lake-locked (round-setup/mark-lake-locked-ships)))
    (conj! phases (monitor/time-phase :evacuate-lake-boats (round-setup/evacuate-lake-patrol-boats)))
    (conj! phases (monitor/time-phase :update-production (player-production/update-production)))
    (conj! phases (monitor/time-phase :repair-ships (round-setup/repair-damaged-ships)))
    (conj! phases (monitor/time-phase :reset-steps (round-setup/reset-steps-remaining)))
    (conj! phases (monitor/time-phase :wake-airport-fighters (round-setup/wake-airport-fighters)))
    (conj! phases (monitor/time-phase :threat-response (threat-response/on-round-start!)))
    (sa/write-state! :claimed-objectives #{})
    (sa/write-state! :claimed-transport-targets #{})
    (sa/write-state! :claimed-patrol-targets #{})
    (debug-logging/begin-computer-unit-log-round!)
    (conj! phases (monitor/time-phase :build-round-state
                    (let [player-items (current-player-items)
                          computer-items (vec (build-computer-items))
                          round-state (decisions/round-start-state
                                       {:handicap-rounds-remaining (sa/read-state :handicap-rounds-remaining)
                                        :player-items player-items
                                        :computer-items computer-items
                                        :game-over-check-enabled (sa/read-state :game-over-check-enabled)})]
                      (apply-round-start-state! round-state)
                      (computer-production/rebuild-country-stats!)
                      (army/assign-city-attacks)
                      (army/assign-transport-staging))))
    (conj! phases (monitor/time-phase :production-status
                    (sa/write-state! :production-status
                                     (production-status/format-production-status (sa/current-world)
                                                                                 (sa/read-state :player-map)))))
    (conj! phases (monitor/time-phase :integrity-check (integrity/check-world-integrity!)))
    (persistent! phases)))

(defn- both-lists-empty? []
  (and (empty? (sa/read-state :player-items))
       (empty? (sa/read-state :computer-items))))

(defn- process-player-action!
  []
  (item-processing/process-player-items-batch))

(defn- apply-advance-game-action!
  [action]
  (case action
    :pause (do
             (sa/write-state! :paused true)
             (sa/write-state! :pause-requested false))
    :new-round (do
                 (when (pos? (sa/read-state :round-number))
                   (update-handicap-before-round!))
                 (if monitor/*phase-sink*
                   (swap! monitor/*phase-sink* into (start-new-round-timed))
                   (start-new-round)))
    :process-player (if monitor/*phase-sink*
                      (swap! monitor/*phase-sink* conj
                             (monitor/time-phase :process-player (process-player-action!)))
                      (process-player-action!))
    :process-computer (item-processing/process-computer-items)
    nil))

(defn advance-game
  "Advances the game by processing player items, then computer items.
   Processes multiple non-attention items per frame for faster rounds."
  []
  (apply-advance-game-action!
   (decisions/advance-game-action {:load-menu-open (sa/read-state :load-menu-open)
                                   :save-menu-open (sa/read-state :save-menu-open)
                                   :paused (sa/read-state :paused)
                                   :both-lists-empty? (both-lists-empty?)
                                   :pause-requested (sa/read-state :pause-requested)
                                   :waiting-for-input (sa/read-state :waiting-for-input)
                                   :player-items (sa/read-state :player-items)})))

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
  (case (decisions/toggle-pause-action (sa/read-state :paused))
    :resume (do
      (sa/write-state! :paused false)
      (sa/write-state! :pause-requested false))
    :request-pause (sa/write-state! :pause-requested true)
    nil))

(defn step-one-round
  "When paused, advances one round then pauses again."
  []
  (case (decisions/step-one-round-action {:paused? (sa/read-state :paused)
                                          :player-items (sa/read-state :player-items)
                                          :computer-items (sa/read-state :computer-items)})
    :start-round (do
                   (sa/write-state! :paused false)
                   (sa/write-state! :pause-requested true)
                   (start-new-round))
    :resume-one-round (do
                        (sa/write-state! :paused false)
                        (sa/write-state! :pause-requested true))
    nil))

(defn update-map
  "Updates the game map state."
  []
  (update-player-map)
  (update-computer-map)
  (advance-game))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-16T15:48:07.84725-05:00", :module-hash "1486415462", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 20, :hash "-1322799113"} {:id "defn/update-player-map", :kind "defn", :line 22, :end-line 29, :hash "2102522450"} {:id "defn/update-computer-map", :kind "defn", :line 31, :end-line 38, :hash "-1953237338"} {:id "defn/build-player-items", :kind "defn", :line 40, :end-line 49, :hash "1741102605"} {:id "defn/build-computer-items", :kind "defn", :line 51, :end-line 60, :hash "-1394268341"} {:id "defn-/declare-game-over!", :kind "defn-", :line 62, :end-line 69, :hash "1518773855"} {:id "defn/item-processed", :kind "defn", :line 71, :end-line 76, :hash "-1477047071"} {:id "defn-/handicap-active?", :kind "defn-", :line 78, :end-line 80, :hash "-1620006900"} {:id "defn-/current-player-items", :kind "defn-", :line 82, :end-line 85, :hash "461726394"} {:id "defn-/update-handicap-before-round!", :kind "defn-", :line 87, :end-line 97, :hash "-293884731"} {:id "defn-/apply-round-start-state!", :kind "defn-", :line 99, :end-line 107, :hash "-1011890827"} {:id "def/remove-dead-units", :kind "def", :line 110, :end-line 110, :hash "2048804574"} {:id "def/reset-steps-remaining", :kind "def", :line 111, :end-line 111, :hash "-2073169805"} {:id "def/wake-airport-fighters", :kind "def", :line 112, :end-line 112, :hash "879726790"} {:id "def/wake-carrier-fighters", :kind "def", :line 113, :end-line 113, :hash "840162374"} {:id "def/consume-sentry-fighter-fuel", :kind "def", :line 114, :end-line 114, :hash "-647149942"} {:id "def/wake-sentries-seeing-enemy", :kind "def", :line 115, :end-line 115, :hash "-1383325288"} {:id "def/move-satellites", :kind "def", :line 116, :end-line 116, :hash "493297701"} {:id "def/repair-damaged-ships", :kind "def", :line 117, :end-line 117, :hash "2109716449"} {:id "def/move-current-unit", :kind "def", :line 120, :end-line 120, :hash "122457069"} {:id "def/move-explore-unit", :kind "def", :line 121, :end-line 121, :hash "-1327293082"} {:id "def/move-coastline-unit", :kind "def", :line 122, :end-line 122, :hash "-314642053"} {:id "defn/start-new-round", :kind "defn", :line 124, :end-line 161, :hash "602851811"} {:id "defn-/both-lists-empty?", :kind "defn-", :line 163, :end-line 165, :hash "-2026920266"} {:id "defn-/self-play-waiting?", :kind "defn-", :line 167, :end-line 170, :hash "-1267063830"} {:id "defn-/process-player-action!", :kind "defn-", :line 172, :end-line 176, :hash "-1356972447"} {:id "defn-/apply-advance-game-action!", :kind "defn-", :line 178, :end-line 190, :hash "150827694"} {:id "defn/advance-game", :kind "defn", :line 192, :end-line 205, :hash "1792315727"} {:id "defn/advance-game-batch", :kind "defn", :line 207, :end-line 219, :hash "1612417613"} {:id "defn/toggle-pause", :kind "defn", :line 221, :end-line 230, :hash "-551704113"} {:id "defn/step-one-round", :kind "defn", :line 232, :end-line 245, :hash "-168868623"} {:id "defn/update-map", :kind "defn", :line 247, :end-line 252, :hash "-297625781"}]}
;; clj-mutate-manifest-end
