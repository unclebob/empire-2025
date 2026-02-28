(ns empire.movement.pathfinding-bfs
  "Facade namespace for BFS movement/pathfinding utilities.
   Delegates implementation to focused submodules."
  (:require [empire.movement.pathfinding-bfs.cache :as cache]
            [empire.movement.pathfinding-bfs.coast-targeting :as coast-targeting]
            [empire.movement.pathfinding-bfs.exploration :as exploration]
            [empire.movement.pathfinding-bfs.transport :as transport]))

(defn clear-bfs-caches
  "Clears all BFS caches. Called at start of each round."
  []
  (cache/clear-bfs-caches))

(defn sea-reaches-edge? [pos]
  (coast-targeting/sea-reaches-edge? pos))

(defn find-nearest-unexplored [start unit-type]
  (exploration/find-nearest-unexplored start unit-type))

(defn find-nearest-unexplored-coastline [start unit-type]
  (exploration/find-nearest-unexplored-coastline start unit-type))

(defn bfs-to-unexplored-coast [start computer-map]
  (exploration/bfs-to-unexplored-coast start computer-map))

(defn bfs-to-unowned-coast [start computer-map game-map]
  (coast-targeting/bfs-to-unowned-coast start computer-map game-map))

(defn bfs-to-coast-target [start computer-map]
  (coast-targeting/bfs-to-coast-target start computer-map))

(defn bfs-to-unseen-coast [start computer-map excluded]
  (exploration/bfs-to-unseen-coast start computer-map excluded))

(defn find-nearest-unload-position [start target-continent]
  (transport/find-nearest-unload-position start target-continent))

(defn bfs-to-land-ho-target [start target-city computer-map]
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
