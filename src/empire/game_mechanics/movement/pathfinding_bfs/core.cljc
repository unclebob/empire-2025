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

(defn transport-passable-sea?
  "Returns true if pos is usable by a transport path.
   Start may contain the transport itself; later path cells must be empty sea."
  [the-map start pos]
  (let [cell (get-in the-map pos)]
    (and cell
         (= :sea (:type cell))
         (or (= pos start)
             (nil? (:contents cell))))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T12:01:37.691835-05:00", :module-hash "-1177968417", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 3, :hash "1770237734"} {:id "defn/update-first-match", :kind "defn", :line 5, :end-line 7, :hash "-1796435012"} {:id "defn/bfs-sea-neighbors", :kind "defn", :line 9, :end-line 16, :hash "1694187772"} {:id "defn/build-coast-path", :kind "defn", :line 18, :end-line 21, :hash "925901257"} {:id "defn/passable-sea?", :kind "defn", :line 23, :end-line 27, :hash "-1999367331"}]}
;; clj-mutate-manifest-end
