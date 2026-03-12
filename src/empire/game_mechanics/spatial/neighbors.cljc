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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T12:02:28.699189-05:00", :module-hash "1359217092", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "1511182993"} {:id "def/neighbor-offsets", :kind "def", :line 3, :end-line 5, :hash "-312270892"} {:id "def/orthogonal-offsets", :kind "def", :line 7, :end-line 9, :hash "1537641137"} {:id "defn/any-neighbor-matches?", :kind "defn", :line 11, :end-line 22, :hash "-154676456"} {:id "defn/count-matching-neighbors", :kind "defn", :line 24, :end-line 36, :hash "-1695105535"} {:id "defn/get-matching-neighbors", :kind "defn", :line 38, :end-line 50, :hash "-840978453"}]}
;; clj-mutate-manifest-end
