(ns empire.computer.shared.grid)

(def neighbor-offsets
  [[-1 -1] [-1 0] [-1 1]
   [0 -1]          [0 1]
   [1 -1]  [1 0]  [1 1]])

(defn neighbors-in-map
  [the-map [r c]]
  (if (and (sequential? the-map) (seq the-map) (sequential? (first the-map)))
    (let [height (count the-map)
          width (count (first the-map))]
      (for [[dr dc] neighbor-offsets
            :let [nr (+ r dr)
                  nc (+ c dc)]
            :when (and (<= 0 nr) (< nr height)
                       (<= 0 nc) (< nc width))]
        [nr nc]))
    []))

(defn in-bounds?
  [the-map [r c]]
  (and (<= 0 r) (< r (count the-map))
       (<= 0 c) (< c (count (first the-map)))))

(defn bounded-conj
  [coll value limit]
  (let [v (conj (or coll []) value)]
    (if (> (count v) limit)
      (subvec v (- (count v) limit))
      v)))

(defn adjacent?
  "Returns true if pos1 and pos2 are adjacent (including diagonally)."
  [pos1 pos2]
  (let [[r1 c1] pos1
        [r2 c2] pos2
        dr (Math/abs (- r2 r1))
        dc (Math/abs (- c2 c1))]
    (and (<= dr 1) (<= dc 1) (not (and (zero? dr) (zero? dc))))))

(defn distance
  [[x1 y1] [x2 y2]]
  (+ (Math/abs (- x2 x1)) (Math/abs (- y2 y1))))

(defn chebyshev-distance
  [[r1 c1] [r2 c2]]
  (max (Math/abs (- r2 r1)) (Math/abs (- c2 c1))))

(defn inflated-path?
  [path from target threshold]
  (let [cheb (chebyshev-distance from target)]
    (and (seq path)
         (pos? cheb)
         (>= (count path) (* threshold cheb)))))

(defn move-toward
  [pos target passable-neighbors]
  (when (seq passable-neighbors)
    (apply min-key #(distance % target) passable-neighbors)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-26T23:12:24.393431-05:00", :module-hash "987777557", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "588851628"} {:id "def/neighbor-offsets", :kind "def", :line 3, :end-line 6, :hash "-1254756339"} {:id "defn/neighbors-in-map", :kind "defn", :line 8, :end-line 19, :hash "-2068233344"} {:id "defn/adjacent?", :kind "defn", :line 21, :end-line 28, :hash "-1801643981"} {:id "defn/distance", :kind "defn", :line 30, :end-line 32, :hash "403209233"} {:id "defn/chebyshev-distance", :kind "defn", :line 34, :end-line 36, :hash "274637302"} {:id "defn/move-toward", :kind "defn", :line 38, :end-line 41, :hash "2054359074"}]}
;; clj-mutate-manifest-end
