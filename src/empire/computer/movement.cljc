;; mutation-tested: no
(ns empire.computer.movement
  (:require [empire.application.ports.movement :as movement-port]
            [empire.application.state-access :as sa]))

(defn- movement-services
  []
  (:movement-port (sa/state-ctx)))

(defn update-cell-visibility!
  [pos owner]
  (movement-port/movement-update-cell-visibility (movement-services) pos owner))

(defn update-cell-visibility-with-unit!
  [pos owner unit]
  (movement-port/movement-update-cell-visibility-with-unit (movement-services) pos owner unit))

(defn find-nearest-unexplored
  [pos unit-type]
  (movement-port/movement-find-nearest-unexplored (movement-services) pos unit-type))

(defn bfs-to-unseen-coast
  [pos computer-map claimed-targets]
  (movement-port/movement-bfs-to-unseen-coast (movement-services) pos computer-map claimed-targets))

(defn bfs-to-land-ho-target
  [from target computer-map]
  (movement-port/movement-bfs-to-land-ho-target (movement-services) from target computer-map))

(defn bfs-to-coast-target
  [from computer-map]
  (movement-port/movement-bfs-to-coast-target (movement-services) from computer-map))

(defn next-step
  ([from target unit-type]
   (movement-port/movement-next-step (movement-services) from target unit-type nil nil))
  ([from target unit-type passability-fn cache-key-extra]
   (movement-port/movement-next-step (movement-services) from target unit-type passability-fn cache-key-extra)))

(defn lake-cells
  [world lake-max-cells]
  (movement-port/movement-lake-cells (movement-services) world lake-max-cells))
