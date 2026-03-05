;; mutation-tested: 2026-03-02
(ns empire.computer.transport-sailing-path
  (:require [empire.computer.movement :as computer-movement]))

(defn passable-sea?
  "Returns true if pos is a passable sea cell for a transport."
  [world pos]
  (let [cell (get-in world pos)]
    (and cell
         (= :sea (:type cell))
         (or (nil? (:contents cell))
             (= :computer (:owner (:contents cell)))))))

(defn continue-pos
  "Returns pos + direction vector, or nil if out of bounds or not passable sea."
  [world from to]
  (let [dr (- (first to) (first from))
        dc (- (second to) (second from))
        candidate [(+ (first to) dr) (+ (second to) dc)]]
    (when (passable-sea? world candidate) candidate)))

(defn compute-sail-path
  "Compute BFS path from transport position to best coastal target.
   Looks 4 levels past first hit; prefers unowned coast over unexplored."
  [pos computer-map]
  (computer-movement/bfs-to-coast-target pos computer-map))
