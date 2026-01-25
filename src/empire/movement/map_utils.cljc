(ns empire.movement.map-utils
  (:require [empire.atoms :as atoms]
            [empire.ui.coordinates :as coords]))

(def neighbor-offsets
  "Offsets for the 8 adjacent cells (excludes center)."
  [[-1 -1] [-1 0] [-1 1] [0 -1] [0 1] [1 -1] [1 0] [1 1]])

(def orthogonal-offsets
  "Offsets for the 4 orthogonally adjacent cells (N, S, E, W)."
  [[-1 0] [1 0] [0 -1] [0 1]])

(defn get-cell
  "Returns the cell from atoms/game-map at the given coordinates."
  ([x y]
   (get-in @atoms/game-map [y x]))
  ([[x y]]
   (get-cell x y)))

(defn set-cell
  "Sets the cell in atoms/game-map at the given coordinates to the new cell value."
  ([x y cell]
   (swap! atoms/game-map assoc-in [y x] cell))
  ([[x y] cell]
   (set-cell x y cell)))

(defn process-map
  "Processes the map by applying f to each cell, where f takes i j and the-map."
  [the-map f]
  (vec (for [i (range (count the-map))]
         (vec (for [j (range (count (first the-map)))]
                (f i j the-map))))))

(defn filter-map
  "Scans the map and returns positions [i j] where the predicate is true."
  [the-map pred]
  (for [i (range (count the-map))
        j (range (count (first the-map)))
        :let [current (get-in the-map [i j])]
        :when (pred current)]
    [i j]))

(defn any-neighbor-matches?
  "Returns true if any neighbor (using given offsets) satisfies the predicate."
  [pos the-map offsets pred]
  (let [[x y] pos
        height (count the-map)
        width (count (first the-map))]
    (boolean
      (some (fn [[dx dy]]
              (let [nx (+ x dx)
                    ny (+ y dy)]
                (and (>= nx 0) (< nx height)
                     (>= ny 0) (< ny width)
                     (pred (get-in the-map [nx ny])))))
            offsets))))

(defn- count-matching-neighbors
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
  "Returns positions of neighbors (using given offsets) that satisfy the predicate."
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

(defn on-coast?
  "Checks if a cell is adjacent to sea."
  [cell-x cell-y]
  (any-neighbor-matches? [cell-x cell-y] @atoms/game-map neighbor-offsets
                         #(= :sea (:type %))))

(defn on-map?
  "Returns true if the pixel coordinates are within the map display area."
  [x y]
  (let [[map-w map-h] @atoms/map-screen-dimensions]
    (coords/in-map-bounds? x y map-w map-h)))

(defn determine-cell-coordinates
  "Converts mouse coordinates to map cell coordinates [col row].
   Note: game-map is indexed [row][col], but screen coords use x=col, y=row."
  [x y]
  (let [[map-w map-h] @atoms/map-screen-dimensions
        ;; game-map outer count = rows (height), inner count = cols (width)
        map-rows (count @atoms/game-map)
        map-cols (count (first @atoms/game-map))]
    ;; Original code divided map-w by rows and map-h by cols (swapped)
    ;; to get pixels-per-cell-x and pixels-per-cell-y
    (coords/screen->cell x y map-w map-h map-rows map-cols)))

(defn city?
  "Returns true if the cell at coords is a city."
  [[x y]]
  (= :city (:type (get-in @atoms/game-map [x y]))))

(defn blink?
  "Returns true during the 'on' phase of a blink cycle with the given period in milliseconds."
  [period-ms]
  (even? (quot (System/currentTimeMillis) period-ms)))

;; Terrain geometry helpers

(defn adjacent-to-land?
  "Returns true if the position is adjacent to a land cell."
  [pos current-map]
  (any-neighbor-matches? pos @current-map neighbor-offsets
                         #(= :land (:type %))))

(defn orthogonally-adjacent-to-land?
  "Returns true if the position is orthogonally adjacent to a land cell (N/S/E/W only)."
  [pos current-map]
  (any-neighbor-matches? pos @current-map orthogonal-offsets
                         #(= :land (:type %))))

(defn completely-surrounded-by-sea?
  "Returns true if the position has no adjacent land cells (completely in open sea)."
  [pos current-map]
  (not (adjacent-to-land? pos current-map)))

(defn in-bay?
  "Returns true if the position is in a bay - surrounded by 4 or more land cells."
  [pos current-map]
  (>= (count-matching-neighbors pos @current-map neighbor-offsets
                                #(= :land (:type %)))
      4))

(defn adjacent-to-sea?
  "Returns true if the position has an adjacent sea cell."
  [pos current-map]
  (any-neighbor-matches? pos @current-map neighbor-offsets
                         #(= :sea (:type %))))

(defn at-map-edge?
  "Returns true if position is at the edge of the map."
  [pos current-map]
  (let [[x y] pos
        height (count @current-map)
        width (count (first @current-map))]
    (or (zero? x) (zero? y)
        (= x (dec height))
        (= y (dec width)))))

;; Ownership predicates - consolidated from movement.cljc and visibility.cljc

(defn is-players?
  "Returns true if the cell is owned by the player."
  [cell]
  (or (= (:city-status cell) :player)
      (= (:owner (:contents cell)) :player)))

(defn is-computers?
  "Returns true if the cell is owned by the computer."
  [cell]
  (or (= (:city-status cell) :computer)
      (= (:owner (:contents cell)) :computer)))