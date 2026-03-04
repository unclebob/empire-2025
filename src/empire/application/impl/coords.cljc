;; mutation-tested: no
(ns empire.application.impl.coords
  (:require [empire.application.coords :as coords]))

(defmethod coords/screen->cell :default
  [pixel-x pixel-y map-pixel-width map-pixel-height map-rows map-cols]
  (let [cell-w (/ map-pixel-width map-rows)
        cell-h (/ map-pixel-height map-cols)]
    [(int (Math/floor (/ pixel-x cell-w)))
     (int (Math/floor (/ pixel-y cell-h)))]))
