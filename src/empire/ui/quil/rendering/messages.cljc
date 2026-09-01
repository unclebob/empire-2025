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

(defn- clamp-tooltip-axis
  [preferred mouse box-size screen-size]
  (cond
    (<= (+ preferred box-size) screen-size) preferred
    (>= (- mouse 12 box-size) 0) (- mouse 12 box-size)
    :else (max 0 (- screen-size box-size))))

(defn tooltip-box-position
  "Places the tooltip inside the current window bounds."
  [mouse-x mouse-y box-w box-h screen-w screen-h]
  [(clamp-tooltip-axis (+ mouse-x 12) mouse-x box-w screen-w)
   (clamp-tooltip-axis (+ mouse-y 12) mouse-y box-h screen-h)])

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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-01T16:25:22.015452-05:00", :module-hash "799621568", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "1580887966"} {:id "def/attention-color", :kind "def", :line 9, :end-line nil, :hash "1242058600"} {:id "def/warning-color", :kind "def", :line 10, :end-line nil, :hash "-1795980594"} {:id "def/command-color", :kind "def", :line 11, :end-line nil, :hash "-623997271"} {:id "def/status-color", :kind "def", :line 12, :end-line nil, :hash "-420674161"} {:id "def/inspector-color", :kind "def", :line 13, :end-line nil, :hash "-800520892"} {:id "def/hud-tooltip-background", :kind "def", :line 14, :end-line nil, :hash "-1470863357"} {:id "def/hud-tooltip-border", :kind "def", :line 15, :end-line nil, :hash "-1372214356"} {:id "def/hud-tooltip-text", :kind "def", :line 16, :end-line nil, :hash "-1497255467"} {:id "defn-/draw-text-right-justified", :kind "defn-", :line 18, :end-line nil, :hash "-787062085"} {:id "defn-/token-spans", :kind "defn-", :line 25, :end-line nil, :hash "-336658796"} {:id "defn-/inside-span?", :kind "defn-", :line 44, :end-line nil, :hash "-1589197964"} {:id "defn-/hovered-token", :kind "defn-", :line 49, :end-line nil, :hash "-1016660004"} {:id "defn/hud-tooltip", :kind "defn", :line 56, :end-line nil, :hash "925049507"} {:id "defn-/clamp-tooltip-axis", :kind "defn-", :line 76, :end-line nil, :hash "-1437524012"} {:id "defn/tooltip-box-position", :kind "defn", :line 83, :end-line nil, :hash "1714944756"} {:id "defn-/draw-tooltip", :kind "defn-", :line 89, :end-line nil, :hash "-481019929"} {:id "defn-/draw-zone-text", :kind "defn-", :line 102, :end-line nil, :hash "690428341"} {:id "defn-/draw-attention-zone", :kind "defn-", :line 108, :end-line nil, :hash "-675152509"} {:id "defn-/draw-warning-zone", :kind "defn-", :line 116, :end-line nil, :hash "-984946374"} {:id "defn-/draw-command-zone", :kind "defn-", :line 124, :end-line nil, :hash "1574922329"} {:id "defn-/draw-status-zone", :kind "defn-", :line 132, :end-line nil, :hash "905993222"} {:id "defn-/draw-inspector-zones", :kind "defn-", :line 154, :end-line nil, :hash "459879946"} {:id "defn-/draw-grid-separators", :kind "defn-", :line 166, :end-line nil, :hash "1737374437"} {:id "defn/draw-message-area", :kind "defn", :line 179, :end-line nil, :hash "2025325887"}]}
;; clj-mutate-manifest-end
