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
            [empire.computer.shared.transport-query :as transport-query]
            [empire.computer.threat-response.probe :as invasion-probe]
            [empire.game-mechanics.services.game-over :as game-over]))

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

(def declare-game-over! game-over/declare-game-over!)

(defn clear-major-invasion-probe-log!
  []
  (invasion-probe/clear-log!))

(defn- apply-round-start-state!
  [{:keys [player-items computer-items game-over waiting-for-input attention-message cells-needing-attention]}]
  (sa/write-state! :player-items player-items)
  (sa/write-state! :computer-items computer-items)
  (when game-over
    (declare-game-over! (:message game-over)))
  (sa/write-state! :waiting-for-input waiting-for-input)
  (sa/write-state! :attention-message attention-message)
  (sa/write-state! :cells-needing-attention cells-needing-attention))

(defn- owned-item-coordinates
  [world owner]
  (for [i (range (count world))
        j (range (count (first world)))
        :let [cell (get-in world [i j])]
        :when (or (= (:city-status cell) owner)
                  (= (:owner (:contents cell)) owner))]
    [i j]))

(defn build-player-items
  "Builds list of player city/unit coordinates to process this round."
  []
  (owned-item-coordinates (sa/current-world) :player))

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
        items (owned-item-coordinates world :computer)]
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
  (round-setup/remove-armies-at-sea)
  (round-setup/mark-lake-locked-ships)
  (round-setup/evacuate-lake-patrol-boats)
  (player-production/update-production)
  (round-setup/repair-damaged-ships)
  (round-setup/reset-steps-remaining)
  (round-setup/clear-flight-path-launched)
  (round-setup/wake-airport-fighters)
  (threat-response/on-round-start!)
  ;; Carrier fighters stay asleep until 'u' is pressed - do not auto-wake at round start
  (sa/write-state! :claimed-objectives #{})
  (sa/write-state! :claimed-transport-targets #{})
  (sa/write-state! :claimed-patrol-targets #{})
  (debug-logging/begin-computer-unit-log-round!)
  (let [raw-player-items (vec (build-player-items))
        player-items (current-player-items raw-player-items)
        computer-items (vec (build-computer-items))
        handicap-rounds-remaining (sa/read-state :handicap-rounds-remaining)
        round-state (decisions/round-start-state
                     {:handicap-rounds-remaining handicap-rounds-remaining
                      :player-items player-items
                      :computer-items computer-items
                      :game-over-check-enabled (sa/read-state :game-over-check-enabled)})]
    (debug-logging/log-round-start-state!
     {:handicap-rounds-remaining handicap-rounds-remaining
      :built-player-items-count (count raw-player-items)
      :current-player-items-count (count player-items)
      :computer-items-count (count computer-items)})
    (apply-round-start-state! round-state)
    (computer-production/rebuild-country-stats!)
    (army/assign-city-attacks)
    (army/assign-transport-staging))
  (sa/write-state! :production-status
                   (production-status/format-production-status (sa/current-world)
                                                               (sa/read-state :player-map)))
  (integrity/check-world-integrity!))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-01T15:12:08.208921-05:00", :module-hash "969437014", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "-778272141"} {:id "defn/handicap-active?", :kind "defn", :line 28, :end-line nil, :hash "1445275643"} {:id "defn/current-player-items", :kind "defn", :line 32, :end-line nil, :hash "-1375653350"} {:id "defn/update-handicap-before-round!", :kind "defn", :line 37, :end-line nil, :hash "-1774148636"} {:id "def/declare-game-over!", :kind "def", :line 49, :end-line nil, :hash "-687657130"} {:id "defn/clear-major-invasion-probe-log!", :kind "defn", :line 51, :end-line nil, :hash "-1698473431"} {:id "defn-/apply-round-start-state!", :kind "defn-", :line 55, :end-line nil, :hash "-1011890827"} {:id "defn-/owned-item-coordinates", :kind "defn-", :line 65, :end-line nil, :hash "604747286"} {:id "defn/build-player-items", :kind "defn", :line 74, :end-line nil, :hash "-2116360024"} {:id "def/type->priority", :kind "def", :line 79, :end-line nil, :hash "1946935597"} {:id "defn-/unit-processing-order", :kind "defn-", :line 83, :end-line nil, :hash "-780389486"} {:id "defn/build-computer-items", :kind "defn", :line 91, :end-line nil, :hash "-2001454996"} {:id "defn/start-new-round", :kind "defn", :line 99, :end-line nil, :hash "1266033542"}]}
;; clj-mutate-manifest-end
