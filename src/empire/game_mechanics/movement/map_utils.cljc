;; mutation-tested: 2026-02-22
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

(defn adjacent-to-land?
  [pos current-map]
  (any-neighbor-matches? pos (resolve-map-source current-map) neighbor-offsets
                         #(= :land (:type %))))

(defn orthogonally-adjacent-to-land?
  [pos current-map]
  (any-neighbor-matches? pos (resolve-map-source current-map) orthogonal-offsets
                         #(= :land (:type %))))

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
  (any-neighbor-matches? pos (resolve-map-source current-map) neighbor-offsets
                         #(= :sea (:type %))))

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
