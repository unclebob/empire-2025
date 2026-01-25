(ns empire.ui.coordinates
  "Pure coordinate conversion functions for screen/cell mapping.
   No Quil or atom dependencies - purely mathematical transformations.")

(defn in-map-bounds?
  "Returns true if pixel coordinates are within the map display area.
   Pure function - takes dimensions as parameters."
  [pixel-x pixel-y map-width map-height]
  (and (>= pixel-x 0) (< pixel-x map-width)
       (>= pixel-y 0) (< pixel-y map-height)))

(defn screen->cell
  "Converts screen pixel coordinates to map cell coordinates [row col].
   Pure function - takes dimensions as parameters.
   Note: Uses legacy formula where width is divided by rows and height by cols."
  [pixel-x pixel-y map-pixel-width map-pixel-height map-rows map-cols]
  ;; The cell width is screen width / number of columns in a row (map-rows in our indexing)
  ;; The cell height is screen height / number of rows (map-cols in our indexing)
  ;; This matches the original behavior where variable names were swapped
  (let [cell-w (/ map-pixel-width map-rows)
        cell-h (/ map-pixel-height map-cols)]
    [(int (Math/floor (/ pixel-x cell-w)))
     (int (Math/floor (/ pixel-y cell-h)))]))

(defn cell->screen
  "Converts map cell coordinates [row col] to screen pixel coordinates (top-left corner).
   Pure function - takes dimensions as parameters."
  [row col map-pixel-width map-pixel-height map-rows map-cols]
  (let [cell-w (/ map-pixel-width map-cols)
        cell-h (/ map-pixel-height map-rows)]
    [(* col cell-w) (* row cell-h)]))

(defn cell-center->screen
  "Converts map cell coordinates [row col] to screen pixel coordinates (center of cell).
   Pure function - takes dimensions as parameters."
  [row col map-pixel-width map-pixel-height map-rows map-cols]
  (let [cell-w (/ map-pixel-width map-cols)
        cell-h (/ map-pixel-height map-rows)]
    [(+ (* col cell-w) (/ cell-w 2))
     (+ (* row cell-h) (/ cell-h 2))]))

(defn adjacent?
  "Returns true if two positions are adjacent (including diagonals).
   Pure function."
  [[r1 c1] [r2 c2]]
  (and (<= (abs (- r1 r2)) 1)
       (<= (abs (- c1 c2)) 1)
       (not (and (= r1 r2) (= c1 c2)))))

(defn chebyshev-distance
  "Returns Chebyshev distance (max of row/col differences) between two positions.
   This is the number of king moves on a chess board."
  [[r1 c1] [r2 c2]]
  (max (abs (- r1 r2)) (abs (- c1 c2))))

(defn manhattan-distance
  "Returns Manhattan distance (sum of row/col differences) between two positions."
  [[r1 c1] [r2 c2]]
  (+ (abs (- r1 r2)) (abs (- c1 c2))))

(defn extend-to-edge
  "Extends from position in direction [dr dc] until reaching map boundary.
   Returns the edge position. Pure function."
  [[row col] [dr dc] map-rows map-cols]
  (loop [r row c col]
    (let [nr (+ r dr)
          nc (+ c dc)]
      (if (and (>= nr 0) (< nr map-rows)
               (>= nc 0) (< nc map-cols))
        (recur nr nc)
        [r c]))))
