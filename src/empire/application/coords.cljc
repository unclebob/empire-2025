;; mutation-tested: no
(ns empire.application.coords)

(defn screen->cell
  "Converts screen pixel coordinates to map cell coordinates [row col].
   Note: Uses legacy formula where width is divided by rows and height by cols."
  [pixel-x pixel-y map-pixel-width map-pixel-height map-rows map-cols]
  (let [cell-w (/ map-pixel-width map-rows)
        cell-h (/ map-pixel-height map-cols)]
    [(int (Math/floor (/ pixel-x cell-w)))
     (int (Math/floor (/ pixel-y cell-h)))]))
