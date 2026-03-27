(ns empire.test.state
  (:require [empire.state.api :as sa]
            [empire.state.world :as world]
            [empire.state.computer :as computer-state]
            [empire.state.player :as player-state]
            [empire.state.ui :as ui-state]
            [empire.computer.land-objectives :as land-objectives]
            [empire.game-mechanics.movement.pathfinding :as pathfinding]
            [empire.game-mechanics.movement.pathfinding-bfs :as pathfinding-bfs]
            [empire.game-mechanics.visibility :as visibility]
            [empire.computer.fighter.movement-impl :as fighter-movement-impl]
            [empire.computer.fighter.flight-decisions :as flight-decisions]
            [empire.computer.fighter.exploration :as fighter-exploration]
            [empire.computer.production.stats :as production-stats]
            [empire.computer.production.decisions :as production-decisions]
            [empire.computer.ship.carrier :as carrier]
            [empire.computer.early-game.theater :as theater]
            [empire.computer.shared.transport-query :as transport-query]))

(defn read-test-state
  [k]
  (sa/read-state k))

(defn set-test-state!
  [k v]
  (sa/write-state! k v))

(defn update-test-state!
  [k f & args]
  (set-test-state! k (apply f (read-test-state k) args)))

(defn read-test-world
  []
  (sa/current-world))

(defn game-map-atom
  []
  :game-map)

(defn player-map-atom
  []
  :player-map)

(defn computer-map-atom
  []
  :computer-map)

(defn set-test-world!
  [world]
  (sa/write-state! :game-map world))

(defn set-test-cell!
  [pos cell]
  (sa/update-world! assoc-in pos cell))

(defn set-test-contents!
  [pos unit]
  (sa/update-world! assoc-in (conj pos :contents) unit))

(defn clear-test-contents!
  [pos]
  (sa/update-world! assoc-in (conj pos :contents) nil))

(defn update-test-world!
  [f & args]
  (apply sa/update-world! f args))

(defn set-test-player-map!
  [player-map]
  (set-test-state! :player-map player-map))

(defn update-test-player-map!
  [f & args]
  (apply update-test-state! :player-map f args))

(defn set-test-computer-map!
  [computer-map]
  (set-test-state! :computer-map computer-map))

(defn update-test-computer-map!
  [f & args]
  (apply update-test-state! :computer-map f args))

(defn mission-ctx []
  {:current-world read-test-world
   :update-game-map! update-test-world!
   :load-major-invasion-state #(read-test-state :major-invasion-state)})

(defn set-major-invasion-state!
  [state]
  (set-test-state! :major-invasion-state state))

(defn set-kamikazee-fighter!
  [pos attrs]
  (update-test-world! update-in (conj pos :contents)
                      merge
                      {:type :fighter
                       :owner :computer
                       :hits 1
                       :major-invasion true
                       :kamikazee true}
                      attrs))

(defn seed-airport-kamikazees!
  [city-pos total awake]
  (update-test-world! assoc-in (conj city-pos :fighter-count) total)
  (update-test-world! assoc-in (conj city-pos :awake-fighters) awake)
  (update-test-world! assoc-in (conj city-pos :kamikazee-fighter-count) total)
  (update-test-world! assoc-in (conj city-pos :awake-kamikazee-fighters) awake))

(defn reset-all-atoms! []
  (reset! world/state world/defaults)
  (reset! computer-state/state computer-state/defaults)
  (reset! player-state/state player-state/defaults)
  (reset! ui-state/state ui-state/defaults)
  (sa/write-state! :game-over-check-enabled false)
  (sa/write-state! :integrity-check-enabled false)
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
  (visibility/drain-detections!))
