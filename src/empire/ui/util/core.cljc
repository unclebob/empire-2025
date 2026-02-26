(ns empire.ui.util.core
  (:require [empire.atoms :as atoms]
            [empire.config :as config]))

(defn screen->cell
  "Converts screen pixel coordinates to map cell coordinates [row col].
   Pure function - takes dimensions as parameters.
   Note: Uses legacy formula where width is divided by rows and height by cols."
  [pixel-x pixel-y map-pixel-width map-pixel-height map-rows map-cols]
  (let [cell-w (/ map-pixel-width map-rows)
        cell-h (/ map-pixel-height map-cols)]
    [(int (Math/floor (/ pixel-x cell-w)))
     (int (Math/floor (/ pixel-y cell-h)))]))

(defn compute-screen-dimensions
  "Computes pixel rendering dimensions from known map-size and fixed cell-size.
   Returns a map with :map-screen-dimensions and :text-area-dimensions."
  [cols rows cell-w cell-h]
  (let [map-display-w (* cols cell-w)
        map-display-h (* rows cell-h)
        text-h (* config/text-area-rows cell-h)
        text-x 0
        text-y (+ map-display-h config/text-area-gap)
        text-w map-display-w]
    {:map-screen-dimensions [map-display-w map-display-h]
     :text-area-dimensions [text-x text-y text-w text-h]}))

(defn calculate-screen-dimensions
  "Sets pixel rendering dimensions from known map-size and fixed cell-size."
  []
  (let [[cols rows] @atoms/map-size
        [cell-w cell-h] config/cell-size
        dims (compute-screen-dimensions cols rows cell-w cell-h)]
    (reset! atoms/map-screen-dimensions (:map-screen-dimensions dims))
    (reset! atoms/text-area-dimensions (:text-area-dimensions dims))))

(defn- screen-dimensions []
  (let [screen (.getScreenSize (java.awt.Toolkit/getDefaultToolkit))]
    [(.width screen) (.height screen)]))

(defn key-released [_ _]
  (reset! atoms/last-key nil))
