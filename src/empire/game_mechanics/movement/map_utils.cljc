(ns empire.game-mechanics.movement.map-utils
  (:require [empire.state.api :as sa]
            [empire.game-mechanics.spatial.neighbors :as neighbors]
            [empire.config.units.dispatcher :as dispatcher]))

(defn- current-world
  []
  (sa/current-world))

(defn- read-runtime-state
  [k]
  (sa/read-state k))

(def neighbor-offsets neighbors/neighbor-offsets)

(def orthogonal-offsets neighbors/orthogonal-offsets)

(defn process-map
  [the-map f]
  (vec (for [i (range (count the-map))]
         (vec (for [j (range (count (first the-map)))]
                (f i j the-map))))))

(defn resolve-map-source
  [map-source]
  (cond
    (vector? map-source) map-source
    (keyword? map-source) (read-runtime-state map-source)
    :else @map-source))

(defn filter-map
  [the-map pred]
  (for [i (range (count the-map))
        j (range (count (first the-map)))
        :let [current (get-in the-map [i j])]
        :when (pred current)]
    [i j]))

(defn any-neighbor-matches?
  [pos the-map offsets pred]
  (neighbors/any-neighbor-matches? pos the-map offsets pred))

(defn get-matching-neighbors
  [pos the-map offsets pred]
  (neighbors/get-matching-neighbors pos the-map offsets pred))

(defn valid-empty-cell?
  [terrain-type cell]
  (and cell
       (= terrain-type (:type cell))
       (nil? (:contents cell))))

(defn get-valid-empty-neighbor-moves
  [pos current-map terrain-type]
  (get-matching-neighbors pos (resolve-map-source current-map) neighbor-offsets
                          #(valid-empty-cell? terrain-type %)))

(defn on-coast?
  [cell-x cell-y]
  (any-neighbor-matches? [cell-x cell-y] (current-world) neighbor-offsets
                         #(= :sea (:type %))))

(defn on-map?
  [x y]
  (let [[map-w map-h] (read-runtime-state :map-screen-dimensions)]
    (and (>= x 0) (< x map-w)
         (>= y 0) (< y map-h))))

(defn determine-cell-coordinates
  [x y]
  (let [[map-w map-h] (read-runtime-state :map-screen-dimensions)
        world (current-world)
        cols (count world)
        rows (count (first world))
        cell-w (/ map-w cols)
        cell-h (/ map-h rows)]
    [(int (Math/floor (/ x cell-w))) (int (Math/floor (/ y cell-h)))]))

(defn city?
  [[x y]]
  (= :city (:type (get-in (current-world) [x y]))))

(defn blink?
  [period-ms]
  (even? (quot (System/currentTimeMillis) period-ms)))

;; Terrain geometry helpers

(defn- adjacent-to-terrain?
  [pos current-map offsets terrain-type]
  (any-neighbor-matches? pos (resolve-map-source current-map) offsets
                         #(= terrain-type (:type %))))

(defn adjacent-to-land?
  [pos current-map]
  (adjacent-to-terrain? pos current-map neighbor-offsets :land))

(defn orthogonally-adjacent-to-land?
  [pos current-map]
  (adjacent-to-terrain? pos current-map orthogonal-offsets :land))

(defn completely-surrounded-by-sea?
  [pos current-map]
  (not (adjacent-to-land? pos current-map)))

(defn in-bay?
  [pos current-map]
  (>= (neighbors/count-matching-neighbors pos (resolve-map-source current-map) neighbor-offsets
                                          #(= :land (:type %)))
      4))

(defn adjacent-to-sea?
  [pos current-map]
  (adjacent-to-terrain? pos current-map neighbor-offsets :sea))

(defn at-map-edge?
  [pos current-map]
  (let [[x y] pos
        map-data (resolve-map-source current-map)
        height (count map-data)
        width (count (first map-data))]
    (or (zero? x) (zero? y)
        (= x (dec height))
        (= y (dec width)))))

(defn reconstruct-path
  [came-from start goal]
  (loop [pos goal
         path (list goal)]
    (if (= pos start)
      (vec path)
      (let [prev (came-from pos)]
        (recur prev (cons prev path))))))

(defn passable?
  [unit-type cell]
  (and cell
       (not= (:type cell) :unexplored)
       (dispatcher/can-move-to? unit-type cell)))

(defn get-passable-neighbors
  ([pos unit-type game-map]
   (get-passable-neighbors pos unit-type game-map nil))
  ([pos unit-type game-map passability-fn]
   (let [[x y] pos
         check-fn (or passability-fn (partial passable? unit-type))]
     (filter (fn [[nx ny]]
               (let [cell (get-in game-map [nx ny])]
                 (check-fn cell)))
             (map (fn [[dx dy]] [(+ x dx) (+ y dy)]) neighbor-offsets)))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T01:09:08.369424-05:00", :module-hash "1254627404", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 4, :hash "-968676051"} {:id "defn-/current-world", :kind "defn-", :line 6, :end-line 8, :hash "-640438772"} {:id "defn-/read-runtime-state", :kind "defn-", :line 10, :end-line 12, :hash "2315423"} {:id "def/neighbor-offsets", :kind "def", :line 14, :end-line 14, :hash "-552726403"} {:id "def/orthogonal-offsets", :kind "def", :line 16, :end-line 16, :hash "1658125727"} {:id "defn/process-map", :kind "defn", :line 18, :end-line 22, :hash "-607001133"} {:id "defn/resolve-map-source", :kind "defn", :line 24, :end-line 29, :hash "123476457"} {:id "defn/filter-map", :kind "defn", :line 31, :end-line 37, :hash "1093890821"} {:id "defn/any-neighbor-matches?", :kind "defn", :line 39, :end-line 41, :hash "764995370"} {:id "defn/get-matching-neighbors", :kind "defn", :line 43, :end-line 45, :hash "265663512"} {:id "defn/on-coast?", :kind "defn", :line 47, :end-line 50, :hash "-753745093"} {:id "defn/on-map?", :kind "defn", :line 52, :end-line 56, :hash "1974758442"} {:id "defn/determine-cell-coordinates", :kind "defn", :line 58, :end-line 66, :hash "1705955567"} {:id "defn/city?", :kind "defn", :line 68, :end-line 70, :hash "-390712276"} {:id "defn/blink?", :kind "defn", :line 72, :end-line 74, :hash "-797527907"} {:id "defn/adjacent-to-land?", :kind "defn", :line 78, :end-line 81, :hash "-1589259476"} {:id "defn/orthogonally-adjacent-to-land?", :kind "defn", :line 83, :end-line 86, :hash "371905033"} {:id "defn/completely-surrounded-by-sea?", :kind "defn", :line 88, :end-line 90, :hash "660836702"} {:id "defn/in-bay?", :kind "defn", :line 92, :end-line 96, :hash "604471717"} {:id "defn/adjacent-to-sea?", :kind "defn", :line 98, :end-line 101, :hash "1750449254"} {:id "defn/at-map-edge?", :kind "defn", :line 103, :end-line 111, :hash "1418167734"} {:id "defn/reconstruct-path", :kind "defn", :line 113, :end-line 120, :hash "1843363494"} {:id "defn/passable?", :kind "defn", :line 122, :end-line 126, :hash "619548818"} {:id "defn/get-passable-neighbors", :kind "defn", :line 128, :end-line 137, :hash "142712809"}]}
;; clj-mutate-manifest-end
