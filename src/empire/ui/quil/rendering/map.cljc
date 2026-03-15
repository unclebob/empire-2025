(ns empire.ui.quil.rendering.map
  (:require [clojure.string :as str]
            [empire.state.api :as sa]
            [empire.config.core :as config]
            [empire.game-mechanics.containers.helpers :as uc]
            [empire.game-mechanics.movement.map-utils :as map-utils]
            [empire.ui.util.rendering.display :as display]
            [quil.core :as q]))

(defn draw-production-indicators
  "Draws production indicator for a city cell. Assumes font is already set."
  [row col cell cell-w cell-h production map-to-display]
  (when-let [{:keys [prod-char progress remaining dark-color]}
             (display/production-indicator-data row col cell production map-to-display)]
    (when (and (> progress 0) (> remaining 0))
      (let [[r g b] dark-color]
        (q/fill r g b 128))
      (q/rect (* col cell-w) (+ (* row cell-h) (- cell-h (* cell-h progress))) cell-w (* cell-h progress)))
    (let [[r g b] config/production-color]
      (q/fill r g b))
    (q/text prod-char (+ (* col cell-w) config/cell-char-x-offset) (+ (* row cell-h) config/cell-char-y-offset))))

(defn- attention-airport-fighter?
  [col row cell attention-coords]
  (and (= :city (:type cell))
       (nil? (:contents cell))
       (uc/has-awake? cell :awake-fighters)
       (= [col row] (first attention-coords))))

(defn- city-production-overrides-airport?
  [cell production-indicator]
  (and (= :city (:type cell))
       (nil? (:contents cell))
       (pos? (uc/get-count cell :fighter-count))
       production-indicator))

(defn- draw-unit
  "Draws a unit on the map cell, handling attention blinking for contained units.
   Assumes font is already set. Computer units show as lowercase."
  [col row cell cell-w cell-h attention-coords blink-attention? blink-unit?]
  (when-let [display-unit (display/determine-display-unit col row cell attention-coords blink-unit?)]
    (let [[r g b] (display/attention-unit-color display-unit col row attention-coords blink-attention?)
          char (config/item-chars (:type display-unit))
          char (if (= :computer (:owner display-unit)) (str/lower-case char) char)]
      (q/fill r g b)
      (q/text char (+ (* col cell-w) config/cell-char-x-offset) (+ (* row cell-h) config/cell-char-y-offset)))))

(defn- draw-waypoint
  "Draws a waypoint marker on the map cell if it has a waypoint and no contents.
   Assumes font is already set."
  [col row display-cell world-cell cell-w cell-h]
  (when (and (not= :unexplored (:type display-cell))
             (:waypoint world-cell)
             (nil? (:contents display-cell)))
    (let [[r g b] config/waypoint-color]
      (q/fill r g b)
      (q/text "*" (+ (* col cell-w) config/cell-char-x-offset) (+ (* row cell-h) config/cell-char-y-offset)))))

(defn draw-debug-selection-rectangle
  "Draws the debug selection rectangle if a drag is active.
   Uses screen coordinates from debug-drag-start and debug-drag-current atoms."
  []
  (when-let [start (sa/read-state :debug-drag-start)]
    (when-let [current (sa/read-state :debug-drag-current)]
      (let [[x1 y1] start
            [x2 y2] current
            left (min x1 x2)
            top (min y1 y2)
            width (abs (- x2 x1))
            height (abs (- y2 y1))]
        (q/no-fill)
        (q/stroke 255 255 0)
        (q/stroke-weight 2)
        (q/rect left top width height)
        (q/stroke-weight 1)))))

(defn draw-map
  "Draws the map on the screen."
  [the-map]
  (let [[map-w map-h] (sa/read-state :map-screen-dimensions)
        world (sa/read-state :game-map)
        cols (count the-map)
        rows (count (first the-map))
        cell-w (/ map-w cols)
        cell-h (/ map-h rows)
        attention-coords (sa/read-state :cells-needing-attention)
        production (sa/read-state :production)
        map-to-display (sa/read-state :map-to-display)
        blink-attention? (map-utils/blink? 125)
        blink-completed? (map-utils/blink? 500)
        blink-unit? (map-utils/blink? 250)
        cells-by-color (display/group-cells-by-color the-map
                                                     attention-coords
                                                     production
                                                     blink-attention?
                                                     blink-completed?
                                                     map-to-display)]
    (q/no-stroke)
    ;; Draw all rects batched by color
    (doseq [[color cells] cells-by-color]
      (let [[r g b] color]
        (q/fill r g b)
        (doseq [{:keys [col row]} cells]
          (q/rect (* col cell-w) (* row cell-h) cell-w cell-h))))
    ;; Draw grid
    (q/stroke 0)
    (doseq [col (range (inc cols))]
      (q/line (* col cell-w) 0 (* col cell-w) map-h))
    (doseq [row (range (inc rows))]
      (q/line 0 (* row cell-h) map-w (* row cell-h)))
    ;; Draw production indicators, units, and waypoints (set font once)
    (q/text-font (sa/read-state :production-char-font))
    (doseq [[_ cells] cells-by-color]
      (doseq [{:keys [col row cell]} cells]
        (let [production-indicator (display/production-indicator-data row col cell production map-to-display)
              attention-airport? (attention-airport-fighter? col row cell attention-coords)
              hide-production? attention-airport?
              hide-airport-unit? (and (not attention-airport?)
                                      (city-production-overrides-airport? cell production-indicator))]
          (when-not hide-production?
            (draw-production-indicators row col cell cell-w cell-h production map-to-display))
          (when-not hide-airport-unit?
            (draw-unit col row cell cell-w cell-h attention-coords blink-attention? blink-unit?)))
        (draw-waypoint col row cell (get-in world [col row]) cell-w cell-h)))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T13:53:45.052163-05:00", :module-hash "-1024084162", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 7, :hash "-1169683959"} {:id "defn/draw-production-indicators", :kind "defn", :line 9, :end-line 20, :hash "1768886334"} {:id "defn-/draw-unit", :kind "defn-", :line 22, :end-line 31, :hash "429368515"} {:id "defn-/draw-waypoint", :kind "defn-", :line 33, :end-line 40, :hash "1569607689"} {:id "defn/draw-debug-selection-rectangle", :kind "defn", :line 42, :end-line 58, :hash "251657802"} {:id "defn/draw-map", :kind "defn", :line 60, :end-line 99, :hash "-915592608"}]}
;; clj-mutate-manifest-end
