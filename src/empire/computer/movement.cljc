;; mutation-tested: no
(ns empire.computer.movement
  (:require [empire.application.ports.movement-execution :as exec-ports]
            [empire.application.ports.pathfinding :as path-ports]
            [empire.state.api :as sa]))

(defn- execution-port
  []
  (:execution-port (sa/state-ctx)))

(defn- pathfinding-port
  []
  (:pathfinding-port (sa/state-ctx)))

(defn update-cell-visibility!
  [pos owner]
  (exec-ports/movement-update-cell-visibility (execution-port) pos owner))

(defn update-cell-visibility-with-unit!
  [pos owner unit]
  (exec-ports/movement-update-cell-visibility-with-unit (execution-port) pos owner unit))

(defn find-nearest-unexplored
  [pos unit-type]
  (path-ports/movement-find-nearest-unexplored (pathfinding-port) pos unit-type))

(defn bfs-to-unseen-coast
  [pos computer-map claimed-targets]
  (path-ports/movement-bfs-to-unseen-coast (pathfinding-port) pos computer-map claimed-targets))

(defn bfs-to-land-ho-target
  [from target computer-map]
  (path-ports/movement-bfs-to-land-ho-target (pathfinding-port) from target computer-map))

(defn bfs-to-coast-target
  [from computer-map]
  (path-ports/movement-bfs-to-coast-target (pathfinding-port) from computer-map))

(defn next-step
  ([from target unit-type]
   (path-ports/movement-next-step (pathfinding-port) from target unit-type nil nil))
  ([from target unit-type passability-fn cache-key-extra]
   (path-ports/movement-next-step (pathfinding-port) from target unit-type passability-fn cache-key-extra)))

(defn lake-cells
  [world lake-max-cells]
  (path-ports/movement-lake-cells (pathfinding-port) world lake-max-cells))
