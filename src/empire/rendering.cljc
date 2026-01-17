(ns empire.rendering
  (:require [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.map-utils :as map-utils]
            [empire.rendering-util :as ru]
            [quil.core :as q]))

(defn draw-production-indicators
  "Draws production indicator for a city cell. Assumes font is already set."
  [i j cell cell-w cell-h]
  (when (= :city (:type cell))
    (when-let [prod (@atoms/production [j i])]
      (when (and (map? prod) (:item prod))
        ;; Draw production progress thermometer
        (let [total (config/item-cost (:item prod))
              remaining (:remaining-rounds prod)
              progress (/ (- total remaining) (double total))
              base-color (config/color-of cell)
              dark-color (mapv #(* % 0.5) base-color)]
          (when (and (> progress 0) (> remaining 0))
            (let [[r g b] dark-color]
              (q/fill r g b 128))
            (let [bar-height (* cell-h progress)]
              (q/rect (* j cell-w) (+ (* i cell-h) (- cell-h bar-height)) cell-w bar-height))))
        ;; Draw production character
        (let [[r g b] config/production-color]
          (q/fill r g b))
        (q/text (config/item-chars (:item prod)) (+ (* j cell-w) 2) (+ (* i cell-h) 12))))))


(defn- draw-unit
  "Draws a unit on the map cell, handling attention blinking for contained units.
   Assumes font is already set."
  [col row cell cell-w cell-h attention-coords blink-unit?]
  (when-let [display-unit (ru/determine-display-unit col row cell attention-coords blink-unit?)]
    (let [[r g b] (config/mode->color (:mode display-unit))]
      (q/fill r g b)
      (q/text (config/item-chars (:type display-unit)) (+ (* col cell-w) 2) (+ (* row cell-h) 12)))))

(defn- draw-waypoint
  "Draws a waypoint marker on the map cell if it has a waypoint and no contents.
   Assumes font is already set."
  [col row cell cell-w cell-h]
  (when (and (:waypoint cell) (nil? (:contents cell)))
    (let [[r g b] config/waypoint-color]
      (q/fill r g b)
      (q/text "*" (+ (* col cell-w) 2) (+ (* row cell-h) 12)))))

(defn draw-map
  "Draws the map on the screen."
  [the-map]
  (let [[map-w map-h] @atoms/map-screen-dimensions
        cols (count the-map)
        rows (count (first the-map))
        cell-w (/ map-w cols)
        cell-h (/ map-h rows)
        attention-coords @atoms/cells-needing-attention
        production @atoms/production
        blink-attention? (map-utils/blink? 125)
        blink-completed? (map-utils/blink? 500)
        blink-unit? (map-utils/blink? 250)
        cells-by-color (ru/group-cells-by-color the-map attention-coords production blink-attention? blink-completed?)]
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
    (q/text-font @atoms/production-char-font)
    (doseq [[_ cells] cells-by-color]
      (doseq [{:keys [col row cell]} cells]
        (draw-production-indicators row col cell cell-w cell-h)
        (draw-unit col row cell cell-w cell-h attention-coords blink-unit?)
        (draw-waypoint col row cell cell-w cell-h)))))

(defn update-hover-status
  "Updates line2-message based on mouse position, unless a confirmation message is active."
  []
  (when (>= (System/currentTimeMillis) @atoms/confirmation-until)
    (let [x (q/mouse-x)
          y (q/mouse-y)]
      (if (map-utils/on-map? x y)
        (let [[cx cy] (map-utils/determine-cell-coordinates x y)
              coords [cx cy]
              cell (get-in @atoms/player-map coords)
              production (get @atoms/production coords)
              status (ru/format-hover-status cell production)]
          (reset! atoms/line2-message (or status "")))
        (reset! atoms/line2-message "")))))

(defn- draw-line-1
  "Draws the main message on line 1."
  [text-x text-y]
  (when (seq @atoms/message)
    (q/text @atoms/message (+ text-x 10) (+ text-y 10))))

(defn- draw-line-2
  "Draws content on line 2."
  [text-x text-y]
  (when (seq @atoms/line2-message)
    (q/text @atoms/line2-message (+ text-x 10) (+ text-y 30))))

(defn- draw-line-3
  "Draws the flashing red message on line 3."
  [text-x text-y]
  (if (>= (System/currentTimeMillis) @atoms/line3-until)
    (reset! atoms/line3-message "")
    (when (and (seq @atoms/line3-message)
               (map-utils/blink? 500))
      (q/fill 255 0 0)
      (q/text @atoms/line3-message (+ text-x 10) (+ text-y 50))
      (q/fill 255))))

(defn- draw-status
  "Draws the status area on the right (3 lines, 20 chars wide)."
  [text-x text-y text-w]
  (let [char-width (q/text-width "M")
        status-width (* 20 char-width)
        status-x (- (+ text-x text-w) status-width)
        dest @atoms/destination
        dest-str (if dest (str "Dest: " (first dest) "," (second dest)) "")]
    (q/text (str "Round: " @atoms/round-number) status-x (+ text-y 10))
    (if @atoms/paused
      (do
        (q/fill 255 0 0)
        (q/text "PAUSED" status-x (+ text-y 30))
        (q/fill 255))
      (q/text dest-str status-x (+ text-y 30)))))

(defn draw-message-area
  "Draws the message area including separator line and messages."
  []
  (let [[text-x text-y text-w _] @atoms/text-area-dimensions]
    (q/stroke 255)
    (q/line text-x (- text-y 4) (+ text-x text-w) (- text-y 4))
    (q/text-font @atoms/text-font)
    (q/fill 255)
    (draw-line-1 text-x text-y)
    (draw-line-2 text-x text-y)
    (draw-line-3 text-x text-y)
    (draw-status text-x text-y text-w)))
