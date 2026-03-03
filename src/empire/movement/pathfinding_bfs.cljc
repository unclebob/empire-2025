(ns empire.movement.pathfinding-bfs
  "Facade namespace for BFS movement/pathfinding utilities.
   Delegates implementation to focused submodules."
  (:require [empire.movement.pathfinding-bfs.cache :as cache]
            [empire.movement.pathfinding-bfs.coast-targeting :as coast-targeting]
            [empire.movement.pathfinding-bfs.exploration :as exploration]
            [empire.movement.pathfinding-bfs.transport :as transport]))

(defmulti clear-bfs-caches
  "Clears all BFS caches. Called at start of each round."
  (fn [& _] :default))
(defmethod clear-bfs-caches :default
  []
  (cache/clear-bfs-caches))

(defmulti sea-reaches-edge? (fn [& _] :default))
(defmethod sea-reaches-edge? :default [pos]
  (coast-targeting/sea-reaches-edge? pos))

(defmulti find-nearest-unexplored (fn [& _] :default))
(defmethod find-nearest-unexplored :default [start unit-type]
  (exploration/find-nearest-unexplored start unit-type))

(defmulti find-nearest-unexplored-coastline (fn [& _] :default))
(defmethod find-nearest-unexplored-coastline :default [start unit-type]
  (exploration/find-nearest-unexplored-coastline start unit-type))

(defmulti bfs-to-unexplored-coast (fn [& _] :default))
(defmethod bfs-to-unexplored-coast :default [start computer-map]
  (exploration/bfs-to-unexplored-coast start computer-map))

(defmulti bfs-to-unowned-coast (fn [& _] :default))
(defmethod bfs-to-unowned-coast :default [start computer-map game-map]
  (coast-targeting/bfs-to-unowned-coast start computer-map game-map))

(defmulti bfs-to-coast-target (fn [& _] :default))
(defmethod bfs-to-coast-target :default [start computer-map]
  (coast-targeting/bfs-to-coast-target start computer-map))

(defmulti bfs-to-unseen-coast (fn [& _] :default))
(defmethod bfs-to-unseen-coast :default [start computer-map excluded]
  (exploration/bfs-to-unseen-coast start computer-map excluded))

(defmulti find-nearest-unload-position (fn [& _] :default))
(defmethod find-nearest-unload-position :default [start target-continent]
  (transport/find-nearest-unload-position start target-continent))

(defmulti bfs-to-land-ho-target (fn [& _] :default))
(defmethod bfs-to-land-ho-target :default [start target-city computer-map]
  (transport/bfs-to-land-ho-target start target-city computer-map))

;; Compatibility wrappers for specs that exercise private internals via var-quote.
(defn- available-for-target? [current start depth excluded]
  (exploration/available-for-target? current start depth excluded))

(defn- unexplored-target? [current best-unexplored computer-map]
  (exploration/unexplored-target? current best-unexplored computer-map))

(defn- adjacent-to-unexplored? [pos computer-map]
  (exploration/adjacent-to-unexplored? pos computer-map))

(defn- at-exploration-frontier? [pos computer-map]
  (exploration/at-exploration-frontier? pos computer-map))

(defn- adjacent-to-target-continent-land? [pos target-continent game-map]
  (transport/adjacent-to-target-continent-land? pos target-continent game-map))
