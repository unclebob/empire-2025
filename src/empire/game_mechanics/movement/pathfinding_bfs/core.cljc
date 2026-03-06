(ns empire.game-mechanics.movement.pathfinding-bfs.core
  "Shared BFS helpers for movement pathfinding."
  (:require [empire.game-mechanics.movement.map-utils :as map-utils]))

(defn update-first-match
  [flag? current-best new-value]
  (if (and flag? (nil? current-best)) new-value current-best))

(defn bfs-sea-neighbors
  [current visited passable-sea?]
  (let [[x y] current]
    (for [[dx dy] map-utils/neighbor-offsets
          :let [nx (+ x dx) ny (+ y dy) n [nx ny]]
          :when (and (not (visited n))
                     (passable-sea? n))]
      n)))

(defn build-coast-path
  [best-primary best-secondary came-from start]
  (when-let [target (or best-primary best-secondary)]
    (vec (rest (map-utils/reconstruct-path came-from start target)))))

(defn passable-sea?
  "Returns true if pos is a known sea cell on the given map."
  [the-map pos]
  (let [cell (get-in the-map pos)]
    (and cell (= :sea (:type cell)))))
