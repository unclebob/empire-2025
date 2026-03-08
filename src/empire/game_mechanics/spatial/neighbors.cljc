(ns empire.game-mechanics.spatial.neighbors)

(def neighbor-offsets
  "Offsets for the 8 adjacent cells (excludes center)."
  [[-1 -1] [-1 0] [-1 1] [0 -1] [0 1] [1 -1] [1 0] [1 1]])

(def orthogonal-offsets
  "Offsets for the 4 orthogonally adjacent cells (N, S, E, W)."
  [[-1 0] [1 0] [0 -1] [0 1]])

(defn any-neighbor-matches?
  [pos the-map offsets pred]
  (let [[x y] pos
        height (count the-map)
        width (count (first the-map))]
    (some (fn [[dx dy]]
            (let [nx (+ x dx)
                  ny (+ y dy)]
              (and (>= nx 0) (< nx height)
                   (>= ny 0) (< ny width)
                   (pred (get-in the-map [nx ny])))))
          offsets)))

(defn count-matching-neighbors
  "Counts neighbors (using given offsets) that satisfy the predicate."
  [pos the-map offsets pred]
  (let [[x y] pos
        height (count the-map)
        width (count (first the-map))]
    (count (filter (fn [[dx dy]]
                     (let [nx (+ x dx)
                           ny (+ y dy)]
                       (and (>= nx 0) (< nx height)
                            (>= ny 0) (< ny width)
                            (pred (get-in the-map [nx ny])))))
                   offsets))))

(defn get-matching-neighbors
  [pos the-map offsets pred]
  (let [[x y] pos
        height (count the-map)
        width (count (first the-map))]
    (for [[dx dy] offsets
          :let [nx (+ x dx)
                ny (+ y dy)
                cell (when (and (>= nx 0) (< nx height)
                                (>= ny 0) (< ny width))
                       (get-in the-map [nx ny]))]
          :when (and cell (pred cell))]
      [nx ny])))
