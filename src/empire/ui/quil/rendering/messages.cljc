(ns empire.ui.quil.rendering.messages
  (:require [empire.state.api :as sa]
            [empire.config.core :as config]
            [empire.config.rendering :as rendering]
            [empire.ui.util.rendering.display :as display]
            [empire.ui.util.rendering.hud-tooltips :as hud-tooltips]
            [quil.core :as q]))

(def ^:private attention-color [255 215 64])
(def ^:private warning-color [255 80 80])
(def ^:private command-color [235 245 255])
(def ^:private command-color-opacity 0.7)
(def ^:private status-color [190 198 208])
(def ^:private inspector-color [190 198 208])
(def ^:private hud-tooltip-background [245 239 200])
(def ^:private hud-tooltip-border [70 64 40])
(def ^:private hud-tooltip-text [20 20 20])

(defn- draw-text-right-justified
  "Draws text right-justified against the given right edge at vertical position y."
  [text right-edge y]
  (let [text-width (q/text-width text)
        x (- right-edge text-width)]
    (q/text text x y)))

(defn- token-spans
  [text start-x y1 y2]
  (let [parts (clojure.string/split (or text "") #" ")]
    (loop [tokens parts
           x start-x
           spans []]
      (if-let [token (first tokens)]
        (let [token-width (q/text-width token)
              next-x (+ x token-width)
              gap-width (q/text-width " ")]
          (recur (next tokens)
                 (+ next-x gap-width)
                 (conj spans {:token token
                              :x1 x
                              :x2 next-x
                              :y1 y1
                              :y2 y2})))
        spans))))

(defn- inside-span?
  [mouse-x mouse-y {:keys [x1 x2 y1 y2]}]
  (and (<= x1 mouse-x x2)
       (<= y1 mouse-y y2)))

(defn- hovered-token
  [mouse-x mouse-y spans]
  (some (fn [{:keys [token] :as span}]
          (when (inside-span? mouse-x mouse-y span)
            token))
        spans))

(defn hud-tooltip
  "Returns tooltip text for a recognized status-row token under the mouse.
   Hovering anywhere in the production count area returns the full summary."
  [mouse-x mouse-y text-x text-y text-w left center right production-status]
  (let [row-top (+ text-y rendering/grid-row-1-y)
        row-bottom (+ text-y rendering/grid-row-1-y rendering/grid-row-height)
        right-edge (- (+ text-x text-w) rendering/status-right-padding)
        center-x (+ text-x (/ text-w 2))
        center-width (q/text-width (or center ""))
        center-start (- center-x (/ center-width 2))
        right-start (- right-edge (q/text-width (or right "")))]
    (if (and (<= right-start mouse-x right-edge)
             (<= row-top mouse-y row-bottom))
      (hud-tooltips/full-production-tooltip production-status)
      (let [spans (concat
                   (token-spans left (+ text-x rendering/status-left-padding) row-top row-bottom)
                   (token-spans center center-start row-top row-bottom))]
        (some-> (hovered-token mouse-x mouse-y spans)
                (hud-tooltips/status-token-tooltip production-status))))))

(defn tooltip-box-position
  "Places the tooltip inside the current window bounds."
  [mouse-x mouse-y box-w box-h screen-w screen-h]
  (let [preferred-x (+ mouse-x 12)
        preferred-y (+ mouse-y 12)
        x (cond
            (<= (+ preferred-x box-w) screen-w) preferred-x
            (>= (- mouse-x 12 box-w) 0) (- mouse-x 12 box-w)
            :else (max 0 (- screen-w box-w)))
        y (cond
            (<= (+ preferred-y box-h) screen-h) preferred-y
            (>= (- mouse-y 12 box-h) 0) (- mouse-y 12 box-h)
            :else (max 0 (- screen-h box-h)))]
    [x y]))

(defn- draw-tooltip
  [tooltip mouse-x mouse-y]
  (let [padding 6
        box-h 24
        text-w (q/text-width tooltip)
        box-w (+ text-w (* 2 padding))
        [box-x box-y] (tooltip-box-position mouse-x mouse-y box-w box-h (q/width) (q/height))]
    (apply q/stroke hud-tooltip-border)
    (apply q/fill hud-tooltip-background)
    (q/rect box-x box-y box-w box-h)
    (apply q/fill hud-tooltip-text)
    (q/text tooltip (+ box-x padding) (+ box-y 16))))

(defn- draw-zone-text
  [text x y color]
  (when (seq text)
    (apply q/fill color)
    (q/text text x y)))

(defn- draw-attention-zone
  [text-x text-y]
  (let [text (display/resolve-attention-zone (sa/read-state :attention-message))]
    (draw-zone-text text
                    (+ text-x rendering/msg-left-padding)
                    (+ text-y rendering/grid-row-1-y 16)
                    attention-color)))

(defn- draw-warning-zone
  [text-x text-y]
  (let [text (display/resolve-warning-zone (sa/read-state :warning-message))]
    (draw-zone-text text
                    (+ text-x rendering/msg-left-padding)
                    (+ text-y rendering/grid-row-2-y 16)
                    warning-color)))

(defn- draw-command-zone
  [text-x text-y]
  (let [text (display/resolve-command-zone (sa/read-state :command-message))]
    (draw-zone-text text
                    (+ text-x rendering/msg-left-padding)
                    (+ text-y rendering/grid-row-3-y 16)
                    command-color)))

(defn- draw-status-zone
  [text-x text-y text-w]
  (let [left-x (+ text-x (* text-w rendering/grid-left-fraction) rendering/status-left-padding)
        {:keys [left right]}
        (display/resolve-status-line (sa/read-state :round-number)
                                     (sa/read-state :handicap-display-rounds)
                                     (sa/read-state :paused)
                                     (sa/read-state :pause-requested)
                                     (sa/read-state :map-to-display)
                                     (sa/read-state :destination)
                                     (sa/read-state :production-status)
                                     (sa/current-world)
                                     (sa/read-state :cells-needing-attention))
        right-edge (- (+ text-x text-w) rendering/status-right-padding)
        y (+ text-y rendering/grid-row-1-y 16)]
    (when left
      (apply q/fill status-color)
      (q/text left left-x y))
    (when right
      (apply q/fill status-color)
      (draw-text-right-justified right right-edge y))))

(defn- draw-inspector-zones
  [text-x text-y text-w]
  (let [left-x (+ text-x (* text-w rendering/grid-left-fraction) rendering/status-left-padding)
        {:keys [summary detail]}
        (display/resolve-inspector-lines (sa/read-state :hover-message))]
    (draw-zone-text summary left-x
                    (+ text-y rendering/grid-row-2-y 16)
                    inspector-color)
    (draw-zone-text detail left-x
                    (+ text-y rendering/grid-row-3-y 16)
                    inspector-color)))

(defn- draw-grid-separators
  [text-x text-y text-w text-h]
  (let [col-x (+ text-x (* text-w rendering/grid-left-fraction))]
    (apply q/stroke rendering/grid-vertical-separator-color)
    (q/line col-x text-y col-x (+ text-y text-h))
    (apply q/stroke rendering/grid-separator-color)
    (q/line text-x (+ text-y rendering/grid-row-2-y -2)
            (+ text-x text-w) (+ text-y rendering/grid-row-2-y -2))
    (q/line text-x (+ text-y rendering/grid-row-3-y -2)
            (+ text-x text-w) (+ text-y rendering/grid-row-3-y -2))))

;; --- Message area master function ---

(defn draw-message-area
  "Draws the 3x2 zone-based HUD."
  []
  (let [[text-x text-y text-w text-h] (sa/read-state :text-area-dimensions)
        top-separator-y (- text-y config/msg-separator-offset)]
    (q/no-stroke)
    (apply q/fill rendering/hud-background-color)
    (q/rect text-x text-y text-w text-h)
    (apply q/stroke rendering/hud-top-separator-color)
    (q/line text-x top-separator-y (+ text-x text-w) top-separator-y)
    (draw-grid-separators text-x text-y text-w text-h)
    (q/text-font (sa/read-state :text-font))
    (draw-attention-zone text-x text-y)
    (draw-warning-zone text-x text-y)
    (draw-command-zone text-x text-y)
    (draw-status-zone text-x text-y text-w)
    (draw-inspector-zones text-x text-y text-w)
    (let [mouse-x (q/mouse-x) mouse-y (q/mouse-y)
          {:keys [left center right]}
          (display/resolve-status-line (sa/read-state :round-number)
                                       (sa/read-state :handicap-display-rounds)
                                       (sa/read-state :paused)
                                       (sa/read-state :pause-requested)
                                       (sa/read-state :map-to-display)
                                       (sa/read-state :destination)
                                       (sa/read-state :production-status)
                                       (sa/current-world)
                                       (sa/read-state :cells-needing-attention))
          tooltip (hud-tooltip mouse-x mouse-y text-x text-y text-w left center right
                               (sa/read-state :production-status))]
      (when tooltip
        (draw-tooltip tooltip mouse-x mouse-y)))))
