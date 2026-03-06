;; mutation-tested: no
(ns empire.computer.movement
  (:require [empire.game-mechanics.movement.lakes :as lakes]
            [empire.game-mechanics.movement.pathfinding :as pathfinding]
            [empire.game-mechanics.movement.pathfinding-bfs :as pathfinding-bfs]
            [empire.game-mechanics.movement.visibility :as visibility]))

(defn update-cell-visibility!
  [pos owner]
  (visibility/update-cell-visibility pos owner))

(defn update-cell-visibility-with-unit!
  [pos owner unit]
  (visibility/update-cell-visibility pos owner unit))

(defn find-nearest-unexplored
  [pos unit-type]
  (pathfinding-bfs/find-nearest-unexplored pos unit-type))

(defn bfs-to-unseen-coast
  [pos computer-map claimed-targets]
  (pathfinding-bfs/bfs-to-unseen-coast pos computer-map claimed-targets))

(defn bfs-to-land-ho-target
  [from target computer-map]
  (pathfinding-bfs/bfs-to-land-ho-target from target computer-map))

(defn bfs-to-coast-target
  [from computer-map]
  (pathfinding-bfs/bfs-to-coast-target from computer-map))

(defn next-step
  ([from target unit-type]
   (pathfinding/next-step from target unit-type nil nil))
  ([from target unit-type passability-fn cache-key-extra]
   (pathfinding/next-step from target unit-type passability-fn cache-key-extra)))

(defn lake-cells
  [world lake-max-cells]
  (lakes/lake-cells world lake-max-cells))
