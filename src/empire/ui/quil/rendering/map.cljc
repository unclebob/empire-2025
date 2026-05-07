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
       (pos? (:fighter-count cell 0))
       (= [col row] (first attention-coords))))

(defn- city-production-overrides-airport?
  [cell production-indicator]
  (and (= :city (:type cell))
       (nil? (:contents cell))
       (pos? (uc/get-count cell :fighter-count))
       production-indicator))

(defn- unit-display-char
  [display-unit]
  (let [char (config/item-chars (:type display-unit))]
    (if (= :computer (:owner display-unit))
      (str/lower-case char)
      char)))

(defn- draw-unit-char
  [col row cell-w cell-h display-unit cell-flashing?]
  (let [[r g b] (display/attention-unit-color display-unit cell-flashing?)]
    (q/fill r g b)
    (q/text (unit-display-char display-unit)
            (+ (* col cell-w) config/cell-char-x-offset)
            (+ (* row cell-h) config/cell-char-y-offset))))

(defn- draw-attention-city-placeholder
  [col row cell-w cell-h cell-flashing?]
  (let [[r g b] (if cell-flashing? [0 0 0] [255 255 255])]
    (q/fill r g b)
    (q/text "?"
            (+ (* col cell-w) config/cell-char-x-offset)
            (+ (* row cell-h) config/cell-char-y-offset))))

(defn- draw-unit
  "Draws a unit on the map cell, handling attention blinking for contained units.
   Assumes font is already set. Computer units show as lowercase."
  [col row cell cell-w cell-h attention-coords blink-attention? blink-unit? cell-flashing?]
  (let [display-unit (display/determine-display-unit col row cell attention-coords blink-unit?)
        is-attention-cell? (and (seq attention-coords) (= [col row] (first attention-coords)))]
    (if display-unit
      (draw-unit-char col row cell-w cell-h display-unit cell-flashing?)
      (when (and is-attention-cell? (= :city (:type cell)))
        (draw-attention-city-placeholder col row cell-w cell-h cell-flashing?)))))

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

(defn- attention-ring-visible?
  []
  (try
    (zero? (mod (q/frame-count) 30))
    (catch Throwable _
      false)))

(defn- draw-attention-ring
  [attention-coords cell-w cell-h map-to-display]
  (when-let [[col row] (first attention-coords)]
    (when (and (attention-ring-visible?)
               (not= :computer-map map-to-display))
      (let [center-x (+ (* col cell-w) (/ cell-w 2.0))
            center-y (+ (* row cell-h) (/ cell-h 2.0))
            diameter (* 2.0 cell-h)]
        (q/no-fill)
        (q/stroke 255 255 255)
        (q/stroke-weight 2)
        (q/ellipse center-x center-y diameter diameter)
        (q/stroke-weight 1)))))

(defn- hovered-transport-path
  [the-map]
  (when-let [[col row :as coords] (sa/read-state :hover-cell)]
    (when-let [cell (get-in the-map coords)]
      (let [unit (:contents cell)]
        (when (= :transport (:type unit))
          (:sail-path unit))))))

(defn- draw-hovered-transport-path
  [the-map cell-w cell-h map-to-display]
  (when (and (= :computer-map map-to-display)
             (seq (hovered-transport-path the-map)))
    (let [path (hovered-transport-path the-map)]
      (q/no-fill)
      (q/stroke 255 255 255)
      (q/stroke-weight 3)
      (doseq [[col row] path]
        (q/rect (* col cell-w) (* row cell-h) cell-w cell-h))
      (q/stroke-weight 1)
      (q/no-stroke))))

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

(defn- map-render-context
  [the-map]
  (let [[map-w map-h] (sa/read-state :map-screen-dimensions)
        cols (count the-map)
        rows (count (first the-map))]
    {:map-w map-w
     :map-h map-h
     :world (sa/read-state :game-map)
     :cols cols
     :rows rows
     :cell-w (/ map-w cols)
     :cell-h (/ map-h rows)
     :attention-coords (sa/read-state :cells-needing-attention)
     :production (sa/read-state :production)
     :map-to-display (sa/read-state :map-to-display)
     :blink-attention? (map-utils/blink? 125)
     :blink-completed? (map-utils/blink? 500)
     :blink-unit? (map-utils/blink? 250)}))

(defn- draw-cell-backgrounds
  [{:keys [cell-w cell-h]} cells-by-color]
  (q/no-stroke)
  (doseq [[color cells] cells-by-color]
    (let [[r g b] color]
      (q/fill r g b)
      (doseq [{:keys [col row]} cells]
        (q/rect (* col cell-w) (* row cell-h) cell-w cell-h)))))

(defn- draw-grid
  [{:keys [cols rows cell-w cell-h map-w map-h]}]
  (q/stroke 0)
  (doseq [col (range (inc cols))]
    (q/line (* col cell-w) 0 (* col cell-w) map-h))
  (doseq [row (range (inc rows))]
    (q/line 0 (* row cell-h) map-w (* row cell-h))))

(defn- attention-cell?
  [col row attention-coords]
  (and (seq attention-coords)
       (= [col row] (first attention-coords))))

(defn- overlay-cell
  [world col row cell is-attention-cell?]
  (if is-attention-cell?
    (get-in world [col row])
    cell))

(defn- hide-airport-unit?
  [attention-airport? cell production-indicator]
  (and (not attention-airport?)
       (city-production-overrides-airport? cell production-indicator)))

(defn- cell-flashing?
  [cell production col row is-attention-cell? blink-attention? blink-completed?]
  (or (and is-attention-cell? blink-attention?)
      (and (display/completed-production-city? cell production [col row])
           blink-completed?)))

(defn- draw-cell-production
  [row col cell cell-w cell-h production map-to-display hide-production?]
  (when-not hide-production?
    (draw-production-indicators row col cell cell-w cell-h production map-to-display)))

(defn- draw-cell-unit
  [{:keys [cell-w cell-h attention-coords production blink-attention? blink-completed? blink-unit?]}
   col row cell hide-unit? is-attention-cell?]
  (when-not hide-unit?
    (draw-unit col row cell cell-w cell-h attention-coords blink-attention? blink-unit?
               (cell-flashing? cell production col row is-attention-cell?
                               blink-attention? blink-completed?))))

(defn- draw-cell-overlays
  [{:keys [world cell-w cell-h attention-coords production map-to-display
           blink-attention? blink-completed? blink-unit?]}
   {:keys [col row cell]}]
  (let [is-attention-cell? (attention-cell? col row attention-coords)
        display-cell (overlay-cell world col row cell is-attention-cell?)
        production-indicator (display/production-indicator-data row col display-cell production map-to-display)
        attention-airport? (attention-airport-fighter? col row display-cell attention-coords)
        hide-production? (or attention-airport? is-attention-cell?)
        hide-unit? (hide-airport-unit? attention-airport? display-cell production-indicator)]
    (draw-cell-production row col display-cell cell-w cell-h production map-to-display hide-production?)
    (draw-cell-unit {:cell-w cell-w :cell-h cell-h :attention-coords attention-coords
                     :production production :blink-attention? blink-attention?
                     :blink-completed? blink-completed? :blink-unit? blink-unit?}
                    col row display-cell hide-unit? is-attention-cell?)
    (draw-waypoint col row cell (get-in world [col row]) cell-w cell-h)))

(defn- draw-map-overlays
  [ctx cells-by-color]
  (q/text-font (sa/read-state :production-char-font))
  (doseq [[_ cells] cells-by-color
          cell-entry cells]
    (draw-cell-overlays ctx cell-entry)))

(defn draw-map
  "Draws the map on the screen."
  [the-map]
  (let [{:keys [attention-coords production map-to-display blink-attention?
                blink-completed? cell-w cell-h]
         :as ctx} (map-render-context the-map)
        cells-by-color (display/group-cells-by-color the-map
                                                     attention-coords
                                                     production
                                                     blink-attention?
                                                     blink-completed?
                                                     map-to-display)]
    (draw-cell-backgrounds ctx cells-by-color)
    (draw-grid ctx)
    (draw-hovered-transport-path the-map cell-w cell-h map-to-display)
    (draw-map-overlays ctx cells-by-color)
    (draw-attention-ring attention-coords cell-w cell-h map-to-display)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T02:39:48.008411-05:00", :module-hash "818008730", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 8, :hash "705388234"} {:id "defn/draw-production-indicators", :kind "defn", :line 10, :end-line 21, :hash "1768886334"} {:id "defn-/attention-airport-fighter?", :kind "defn-", :line 23, :end-line 28, :hash "-538091190"} {:id "defn-/city-production-overrides-airport?", :kind "defn-", :line 30, :end-line 35, :hash "2015153415"} {:id "defn-/draw-unit", :kind "defn-", :line 37, :end-line 46, :hash "429368515"} {:id "defn-/draw-waypoint", :kind "defn-", :line 48, :end-line 57, :hash "1854377246"} {:id "defn-/attention-ring-visible?", :kind "defn-", :line 59, :end-line 64, :hash "487940903"} {:id "defn-/draw-attention-ring", :kind "defn-", :line 66, :end-line 78, :hash "-1263691961"} {:id "defn-/hovered-transport-path", :kind "defn-", :line 80, :end-line 86, :hash "986462475"} {:id "defn-/draw-hovered-transport-path", :kind "defn-", :line 88, :end-line 99, :hash "1045198712"} {:id "defn/draw-debug-selection-rectangle", :kind "defn", :line 101, :end-line 117, :hash "251657802"} {:id "defn/draw-map", :kind "defn", :line 119, :end-line 168, :hash "-636828904"}]}
;; clj-mutate-manifest-end
