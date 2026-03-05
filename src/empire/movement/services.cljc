(ns empire.movement.services
  (:require [empire.movement.lakes :as lakes]
            [empire.movement.map-utils :as map-utils]
            [empire.movement.pathfinding :as pathfinding]
            [empire.movement.pathfinding-bfs :as pathfinding-bfs]
            [empire.movement.satellite :as satellite]
            [empire.movement.visibility :as visibility]
            [empire.movement.wake-conditions :as wake]))

(def neighbor-offsets map-utils/neighbor-offsets)

(defn clear-path-cache!
  []
  (pathfinding/clear-path-cache))

(defn clear-bfs-caches!
  []
  (pathfinding-bfs/clear-bfs-caches))

(defn update-combatant-map-state
  [current-map owner world]
  (visibility/update-combatant-map-state current-map owner world))

(defn update-cell-visibility
  [pos owner]
  (visibility/update-cell-visibility pos owner))

(defn move-satellite
  [coords]
  (satellite/move-satellite coords))

(defn friendly-city-in-range?
  [pos fuel world-ref]
  (wake/friendly-city-in-range? pos fuel world-ref))

(defn enemy-unit-visible?
  [unit pos world-ref]
  (wake/enemy-unit-visible? unit pos world-ref))

(defn lake-cells
  [world lake-max-cells]
  (lakes/lake-cells world lake-max-cells))

(defn process-map
  [the-map f]
  (map-utils/process-map the-map f))

(defn filter-map
  [the-map pred]
  (map-utils/filter-map the-map pred))

(defn get-matching-neighbors
  [pos the-map offsets pred]
  (map-utils/get-matching-neighbors pos the-map offsets pred))

(defn any-neighbor-matches?
  [pos the-map offsets pred]
  (map-utils/any-neighbor-matches? pos the-map offsets pred))
