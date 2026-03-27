(ns empire.game.loop.round-start
  "Round start: cache clearing, handicap updates, and new-round orchestration."
  (:require [empire.game.production-status :as production-status]
            [empire.game-mechanics.debug.logging :as debug-logging]
            [empire.game-mechanics.debug.integrity :as integrity]
            [empire.game-mechanics.unit-stamping :as unit-stamping]
            [empire.game-mechanics.movement.pathfinding :as pathfinding]
            [empire.game-mechanics.movement.pathfinding-bfs :as pathfinding-bfs]
            [empire.state.api :as sa]
            [empire.computer.army :as army]
            [empire.computer.land-objectives :as land-objectives]
            [empire.computer.production :as computer-production]
            [empire.computer.threat-response-impl :as threat-response]
            [empire.game.loop.round-setup :as round-setup]
            [empire.game.loop.control-decisions :as decisions]
            [empire.player.production :as player-production]
            [empire.computer.fighter.movement-impl :as fighter-movement-impl]
            [empire.computer.fighter.flight-decisions :as flight-decisions]
            [empire.computer.fighter.exploration :as fighter-exploration]
            [empire.computer.production.stats :as production-stats]
            [empire.computer.production.decisions :as production-decisions]
            [empire.computer.ship.carrier :as carrier]
            [empire.computer.early-game.theater :as theater]
            [empire.computer.shared.transport-query :as transport-query]))

(defn handicap-active?
  []
  (decisions/handicap-active? (sa/read-state :handicap-rounds-remaining)))

(defn current-player-items
  [player-items]
  (decisions/current-player-items (sa/read-state :handicap-rounds-remaining)
                                  player-items))

(defn update-handicap-before-round!
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

(defn declare-game-over!
  [message]
  (sa/write-state! :paused true)
  (sa/write-state! :error-message message)
  (sa/write-state! :error-until Long/MAX_VALUE)
  (sa/write-state! :map-to-display :actual-map)
  (sa/write-state! :player-items [])
  (sa/write-state! :computer-items []))

(defn- apply-round-start-state!
  [{:keys [player-items computer-items game-over waiting-for-input attention-message cells-needing-attention]}]
  (sa/write-state! :player-items player-items)
  (sa/write-state! :computer-items computer-items)
  (when game-over
    (declare-game-over! (:message game-over)))
  (sa/write-state! :waiting-for-input waiting-for-input)
  (sa/write-state! :attention-message attention-message)
  (sa/write-state! :cells-needing-attention cells-needing-attention))

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

(def ^:private type->priority
  {:transport 1 :patrol-boat 2 :destroyer 2 :submarine 2
   :battleship 2 :carrier 2 :fighter 3 :army 4})

(defn- unit-processing-order
  "Returns sort priority for computer unit processing.
   Cities first, then transports, then naval, then fighters, then armies."
  [cell]
  (if (= :city (:type cell))
    0
    (get type->priority (:type (:contents cell)) 5)))

(defn build-computer-items
  "Builds list of computer city/unit coordinates to process this round.
   Ordered: cities, transports, naval, fighters, armies."
  []
  (let [world (sa/current-world)
        items (for [i (range (count world))
                    j (range (count (first world)))
                    :let [cell (get-in world [i j])]
                    :when (or (= (:city-status cell) :computer)
                              (= (:owner (:contents cell)) :computer))]
                [i j])]
    (sort-by #(unit-processing-order (get-in world %)) items)))

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
  (let [player-items (current-player-items (build-player-items))
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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T11:49:35.575891-05:00", :module-hash "153878891", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 24, :hash "1882679365"} {:id "defn/handicap-active?", :kind "defn", :line 26, :end-line 28, :hash "1445275643"} {:id "defn/current-player-items", :kind "defn", :line 30, :end-line 33, :hash "-1375653350"} {:id "defn/update-handicap-before-round!", :kind "defn", :line 35, :end-line 45, :hash "-1774148636"} {:id "defn/declare-game-over!", :kind "defn", :line 47, :end-line 54, :hash "-1723377422"} {:id "defn-/apply-round-start-state!", :kind "defn-", :line 56, :end-line 64, :hash "-1011890827"} {:id "defn/build-player-items", :kind "defn", :line 66, :end-line 75, :hash "1741102605"} {:id "defn-/unit-processing-order", :kind "defn-", :line 77, :end-line 92, :hash "1278546162"} {:id "defn/build-computer-items", :kind "defn", :line 94, :end-line 105, :hash "-1523296034"} {:id "defn/start-new-round", :kind "defn", :line 107, :end-line 153, :hash "553208786"}]}
;; clj-mutate-manifest-end
