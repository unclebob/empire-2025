(ns empire.map-utils
  (:require [empire.atoms :as atoms]))

(def neighbor-offsets
  "Offsets for the 8 adjacent cells (excludes center)."
  [[-1 -1] [-1 0] [-1 1] [0 -1] [0 1] [1 -1] [1 0] [1 1]])

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

(defn on-coast?
  "Checks if a cell is adjacent to sea."
  [cell-x cell-y]
  (let [game-map-val @atoms/game-map
        cols (count game-map-val)
        rows (count (first game-map-val))]
    (some (fn [[dx dy]]
            (let [nx (+ cell-x dx)
                  ny (+ cell-y dy)]
              (and (>= nx 0) (< nx cols)
                   (>= ny 0) (< ny rows)
                   (= :sea (:type (get-in game-map-val [nx ny]))))))
          neighbor-offsets)))

(defn on-map?
  "Returns true if the pixel coordinates are within the map display area."
  [x y]
  (let [[map-w map-h] @atoms/map-screen-dimensions]
    (and (>= x 0) (< x map-w)
         (>= y 0) (< y map-h))))

(defn determine-cell-coordinates
  "Converts mouse coordinates to map cell coordinates."
  [x y]
  (let [[map-w map-h] @atoms/map-screen-dimensions
        cols (count @atoms/game-map)
        rows (count (first @atoms/game-map))
        cell-w (/ map-w cols)
        cell-h (/ map-h rows)]
    [(int (Math/floor (/ x cell-w))) (int (Math/floor (/ y cell-h)))]))

(defn city?
  "Returns true if the cell at coords is a city."
  [[x y]]
  (= :city (:type (get-in @atoms/game-map [x y]))))