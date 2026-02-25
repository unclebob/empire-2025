(ns empire.ui.rendering
  (:require [clojure.string :as str]
            [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.movement.map-utils :as map-utils]
            [empire.save-load :as save-load]
            [empire.ui.rendering-util :as ru]
            [quil.core :as q]))

(defn draw-production-indicators
  "Draws production indicator for a city cell. Assumes font is already set."
  [row col cell cell-w cell-h]
  (when-let [{:keys [prod-char progress remaining dark-color]}
             (ru/production-indicator-data row col cell @atoms/production)]
    (when (and (> progress 0) (> remaining 0))
      (let [[r g b] dark-color]
        (q/fill r g b 128))
      (q/rect (* col cell-w) (+ (* row cell-h) (- cell-h (* cell-h progress))) cell-w (* cell-h progress)))
    (let [[r g b] config/production-color]
      (q/fill r g b))
    (q/text prod-char (+ (* col cell-w) config/cell-char-x-offset) (+ (* row cell-h) config/cell-char-y-offset))))


(defn- draw-unit
  "Draws a unit on the map cell, handling attention blinking for contained units.
   Assumes font is already set. Computer units show as lowercase."
  [col row cell cell-w cell-h attention-coords blink-unit?]
  (when-let [display-unit (ru/determine-display-unit col row cell attention-coords blink-unit?)]
    (let [[r g b] (config/unit->color display-unit)
          char (config/item-chars (:type display-unit))
          char (if (= :computer (:owner display-unit)) (str/lower-case char) char)]
      (q/fill r g b)
      (q/text char (+ (* col cell-w) config/cell-char-x-offset) (+ (* row cell-h) config/cell-char-y-offset)))))

(defn- draw-waypoint
  "Draws a waypoint marker on the map cell if it has a waypoint and no contents.
   Assumes font is already set."
  [col row cell cell-w cell-h]
  (when (and (:waypoint cell) (nil? (:contents cell)))
    (let [[r g b] config/waypoint-color]
      (q/fill r g b)
      (q/text "*" (+ (* col cell-w) config/cell-char-x-offset) (+ (* row cell-h) config/cell-char-y-offset)))))

(defn draw-debug-selection-rectangle
  "Draws the debug selection rectangle if a drag is active.
   Uses screen coordinates from debug-drag-start and debug-drag-current atoms."
  []
  (when-let [start @atoms/debug-drag-start]
    (when-let [current @atoms/debug-drag-current]
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
  "Updates hover-message based on mouse position.
   Shows contents from the currently displayed map.
   Also updates load-menu-hovered when menu is open."
  []
  (let [x (q/mouse-x)
        y (q/mouse-y)]
    (when @atoms/load-menu-open
      (let [files @atoms/load-menu-files
            geom (save-load/menu-geometry (q/width) (q/height) (count files))
            idx (save-load/hovered-file-index x y geom (count files))]
        (reset! atoms/load-menu-hovered idx)))
    (reset! atoms/hover-message
            (if (map-utils/on-map? x y)
              (let [coords (vec (map-utils/determine-cell-coordinates x y))
                    the-map (ru/resolve-display-map @atoms/map-to-display
                              @atoms/player-map @atoms/computer-map @atoms/game-map)]
                (ru/compute-hover-message the-map @atoms/production coords))
              ""))))

(defn- draw-text-right-justified
  "Draws text right-justified against the given right edge at vertical position y."
  [text right-edge y]
  (let [text-width (q/text-width text)
        x (- right-edge text-width)]
    (q/text text x y)))

;; --- Game Info region (left-justified, 37.5%) ---

(defn- draw-attention
  "Draws the attention message (row 1) left-justified in the Game Info region."
  [left-x text-y]
  (when (seq @atoms/attention-message)
    (q/text @atoms/attention-message (+ left-x config/msg-left-padding) (+ text-y config/msg-line-1-y))))

(defn- draw-turn
  "Draws the turn message (row 2) left-justified in the Game Info region.
   Falls back to destination display when no turn message is active."
  [left-x text-y]
  (let [msg @atoms/turn-message
        dest @atoms/destination]
    (cond
      (seq msg)
      (q/text msg (+ left-x config/msg-left-padding) (+ text-y config/msg-line-2-y))

      dest
      (let [dest-str (format (:destination config/messages) (first dest) (second dest))]
        (q/text dest-str (+ left-x config/msg-left-padding) (+ text-y config/msg-line-2-y))))))

(defn- draw-error
  "Draws the error message (row 3) in red, flashing, left-justified in the Game Info region."
  [left-x text-y]
  (when (< (System/currentTimeMillis) @atoms/error-until)
    (when (and (seq @atoms/error-message)
               (map-utils/blink? 500))
      (q/fill 255 0 0)
      (q/text @atoms/error-message (+ left-x config/msg-left-padding) (+ text-y config/msg-line-3-y))
      (q/fill 255))))

(defn- draw-game-info
  "Draws the Game Info region (left): attention, turn, error."
  [left-x text-y]
  (draw-attention left-x text-y)
  (draw-turn left-x text-y)
  (draw-error left-x text-y))

;; --- Debug region (centered, 25%) ---

(defn- draw-debug
  "Draws the debug message centered in the Debug region."
  [debug-x debug-w text-y]
  (when (seq @atoms/debug-message)
    (let [center-x (+ debug-x (/ debug-w 2))
          lines (str/split @atoms/debug-message #"\n")
          y-offsets [config/msg-line-1-y config/msg-line-2-y config/msg-line-3-y]]
      (q/fill 0 255 255)
      (doseq [[line y-off] (map vector (take 3 lines) y-offsets)]
        (let [msg-width (q/text-width line)
              msg-x (- center-x (/ msg-width 2))]
          (q/text line msg-x (+ text-y y-off))))
      (q/fill 255))))

;; --- Game Status region (right-justified, 37.5%) ---

(defn- draw-round-status
  "Draws round status (row 1) right-justified. Prepends red PAUSED when paused."
  [right-edge text-y]
  (let [round-str (str "Round: " @atoms/round-number)]
    (if (ru/should-show-paused? @atoms/paused @atoms/pause-requested)
      (let [full-str (str "PAUSED  " round-str)
            full-width (q/text-width full-str)
            x (- right-edge full-width)
            paused-width (q/text-width "PAUSED  ")]
        (q/fill 255 0 0)
        (q/text "PAUSED  " x (+ text-y config/msg-line-1-y))
        (q/fill 255)
        (q/text round-str (+ x paused-width) (+ text-y config/msg-line-1-y)))
      (draw-text-right-justified round-str right-edge (+ text-y config/msg-line-1-y)))))

(defn- draw-hover-info
  "Draws hover info (row 2) right-justified in the Game Status region."
  [right-edge text-y]
  (when (seq @atoms/hover-message)
    (draw-text-right-justified @atoms/hover-message right-edge (+ text-y config/msg-line-2-y))))

(defn- draw-production-status
  "Draws production status (row 3) right-justified in the Game Status region."
  [right-edge text-y]
  (when (seq @atoms/production-status)
    (draw-text-right-justified @atoms/production-status right-edge (+ text-y config/msg-line-3-y))))

(defn- draw-game-status
  "Draws the Game Status region (right): round status, hover info, production."
  [right-edge text-y]
  (draw-round-status right-edge text-y)
  (draw-hover-info right-edge text-y)
  (draw-production-status right-edge text-y))

;; --- Message area master function ---

(defn draw-message-area
  "Draws the three-region message area below the map."
  []
  (let [[text-x text-y text-w _] @atoms/text-area-dimensions
        info-w (* text-w config/game-info-width-fraction)
        debug-w (* text-w config/debug-width-fraction)
        debug-x (+ text-x info-w)
        right-edge (+ text-x text-w)]
    (q/stroke 255)
    (q/line text-x (- text-y config/msg-separator-offset) (+ text-x text-w) (- text-y config/msg-separator-offset))
    (q/text-font @atoms/text-font)
    (q/fill 255)
    (draw-game-info text-x text-y)
    (draw-debug debug-x debug-w text-y)
    (draw-game-status right-edge text-y)))

(defn draw-load-menu
  "Draws the load game menu overlay when open."
  []
  (when @atoms/load-menu-open
    (let [screen-w (q/width)
          screen-h (q/height)
          files @atoms/load-menu-files
          file-count (count files)
          geom (save-load/menu-geometry screen-w screen-h file-count)
          hovered @atoms/load-menu-hovered]
      ;; Semi-transparent overlay
      (q/fill 0 0 0 128)
      (q/rect 0 0 screen-w screen-h)
      ;; Menu background
      (q/fill 40 40 40)
      (q/stroke 255)
      (q/stroke-weight 2)
      (q/rect (:left geom) (:top geom) (:width geom) (:height geom))
      (q/stroke-weight 1)
      ;; Title
      (q/text-font @atoms/text-font)
      (q/fill 255)
      (q/text "Load Game" (+ (:left geom) save-load/menu-padding) (+ (:top geom) save-load/menu-padding 15))
      ;; File list
      (if (empty? files)
        (do
          (q/fill 180 180 180)
          (q/text "No saved games found" (+ (:left geom) save-load/menu-padding) (+ (:content-top geom) 15)))
        (doseq [[idx filename] (map-indexed vector files)]
          (let [y (+ (:content-top geom) (* idx save-load/menu-item-height))]
            (if (= idx hovered)
              ;; Inverse colors for hover
              (do
                (q/fill 255)
                (q/no-stroke)
                (q/rect (:left geom) y (:width geom) save-load/menu-item-height)
                (q/fill 0)
                (q/text filename (+ (:left geom) save-load/menu-padding) (+ y 17)))
              ;; Normal colors
              (do
                (q/fill 255)
                (q/text filename (+ (:left geom) save-load/menu-padding) (+ y 17))))))))))
