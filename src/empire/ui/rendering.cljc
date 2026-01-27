(ns empire.ui.rendering
  (:require [clojure.string :as str]
            [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.movement.map-utils :as map-utils]
            [empire.ui.rendering-util :as ru]
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
   Assumes font is already set. Computer units show as lowercase."
  [col row cell cell-w cell-h attention-coords blink-unit?]
  (when-let [display-unit (ru/determine-display-unit col row cell attention-coords blink-unit?)]
    (let [[r g b] (config/unit->color display-unit)
          char (config/item-chars (:type display-unit))
          char (if (= :computer (:owner display-unit)) (str/lower-case char) char)]
      (q/fill r g b)
      (q/text char (+ (* col cell-w) 2) (+ (* row cell-h) 12)))))

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
  "Updates hover-message and debug-hover-lines based on mouse position.
   Shows contents from the currently displayed map."
  []
  (let [x (q/mouse-x)
        y (q/mouse-y)]
    (if (map-utils/on-map? x y)
      (let [[cx cy] (map-utils/determine-cell-coordinates x y)
            coords [cx cy]
            the-map (case @atoms/map-to-display
                      :player-map @atoms/player-map
                      :computer-map @atoms/computer-map
                      :actual-map @atoms/game-map)
            cell (get-in the-map coords)
            production (get @atoms/production coords)
            ;; Look up mission info for computer units
            mission-info (when (and (:contents cell)
                                    (= :computer (:owner (:contents cell))))
                           (ru/find-mission-info coords))
            status (ru/format-hover-status coords cell production mission-info)
            debug-lines (ru/format-cell-debug coords cell production mission-info)]
        (reset! atoms/hover-message (or status ""))
        (reset! atoms/debug-hover-lines debug-lines))
      (do
        (reset! atoms/hover-message "")
        (reset! atoms/debug-hover-lines ["" "" ""])))))

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
  "Draws flashing red warning on line 3 if active."
  [text-x text-y]
  (when (< (System/currentTimeMillis) @atoms/line3-until)
    (when (and (seq @atoms/line3-message)
               (map-utils/blink? 500))
      (q/fill 255 0 0)
      (q/text @atoms/line3-message (+ text-x 10) (+ text-y 50))
      (q/fill 255))))

(defn- draw-text-right-justified
  "Draws text right-justified within the status area."
  [text right-edge y]
  (let [text-width (q/text-width text)
        x (- right-edge text-width)]
    (q/text text x y)))

(defn- draw-debug-window
  "Draws the debug window (middle section, 3 lines), centered.
   Shows debug hover lines when hovering over map cells,
   or debug-message when set (e.g., debug file output)."
  [text-x text-y text-w]
  (let [center-x (+ text-x (/ text-w 2))
        [line1 line2 line3] @atoms/debug-hover-lines
        has-hover-content? (or (seq line1) (seq line2) (seq line3))]
    (if has-hover-content?
      ;; Show debug hover lines (cell contents)
      (do
        (when (seq line1)
          (let [width (q/text-width line1)
                x (- center-x (/ width 2))]
            (q/text line1 x (+ text-y 10))))
        (when (seq line2)
          (let [width (q/text-width line2)
                x (- center-x (/ width 2))]
            (q/text line2 x (+ text-y 30))))
        (when (seq line3)
          (let [width (q/text-width line3)
                x (- center-x (/ width 2))]
            (q/text line3 x (+ text-y 50)))))
      ;; Fallback to debug-message if no hover content
      (when (seq @atoms/debug-message)
        (let [msg @atoms/debug-message
              msg-width (q/text-width msg)
              x (- center-x (/ msg-width 2))]
          (q/text msg x (+ text-y 10)))))))

(defn- draw-status
  "Draws the status area on the right (3 lines), right-justified."
  [text-x text-y text-w]
  (let [right-edge (+ text-x text-w)
        round-str (str "Round: " @atoms/round-number)
        dest @atoms/destination
        dest-str (if dest (str "Dest: " (first dest) "," (second dest)) "")]
    (draw-text-right-justified round-str right-edge (+ text-y 10))
    (if (ru/should-show-paused? @atoms/paused @atoms/pause-requested)
      (do
        (q/fill 255 0 0)
        (draw-text-right-justified "PAUSED" right-edge (+ text-y 30))
        (q/fill 255))
      (draw-text-right-justified dest-str right-edge (+ text-y 30)))
    (when (seq @atoms/hover-message)
      (draw-text-right-justified @atoms/hover-message right-edge (+ text-y 50)))))

(defn draw-debug-selection-rectangle
  "Draws the debug selection rectangle when a drag is in progress.
   Shows a semi-transparent rectangle with visible border from drag start to current position."
  []
  (when-let [start @atoms/debug-drag-start]
    (when-let [current @atoms/debug-drag-current]
      (let [[start-x start-y] start
            [current-x current-y] current
            x (min start-x current-x)
            y (min start-y current-y)
            width (Math/abs (- current-x start-x))
            height (Math/abs (- current-y start-y))]
        ;; Draw semi-transparent fill (light blue with 40% opacity)
        (q/fill 100 150 200 100)
        (q/rect x y width height)
        ;; Draw visible border (white stroke)
        (q/stroke 255)
        (q/stroke-weight 2)
        (q/no-fill)
        (q/rect x y width height)
        ;; Reset stroke settings
        (q/stroke-weight 1)
        (q/no-stroke)))))

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
    (draw-debug-window text-x text-y text-w)
    (draw-status text-x text-y text-w)))
