(ns empire.ui.quil.rendering.messages
  (:require [empire.state.api :as sa]
            [empire.config.core :as config]
            [empire.config.rendering :as rendering]
            [empire.ui.util.rendering.display :as display]
            [empire.ui.util.rendering.hud-tooltips :as hud-tooltips]
            [quil.core :as q]))

(def ^:private banner-error-color [255 80 80])
(def ^:private banner-attention-color [255 215 64])
(def ^:private banner-result-color [235 245 255])
(def ^:private hud-text-color [230 230 230])
(def ^:private hud-status-color [190 198 208])
(def ^:private hud-secondary-color [160 166 174])
(def ^:private hud-tooltip-background [245 239 200])
(def ^:private hud-tooltip-border [70 64 40])
(def ^:private hud-tooltip-text [20 20 20])

(defn- draw-text-right-justified
  "Draws text right-justified against the given right edge at vertical position y."
  [text right-edge y]
  (let [text-width (q/text-width text)
        x (- right-edge text-width)]
    (q/text text x y)))

(defn- banner-color [kind]
  (case kind
    :error banner-error-color
    :attention banner-attention-color
    :result banner-result-color
    hud-text-color))

(defn- soften-color
  [color]
  (mapv #(int (+ (* % 0.6) 70)) color))

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
  "Returns tooltip text for a recognized status-row token under the mouse."
  [mouse-x mouse-y text-x text-y text-w left center right production-status]
  (let [row-top (+ text-y rendering/msg-banner-separator-y)
        row-bottom (+ text-y config/msg-line-3-y)
        right-edge (- (+ text-x text-w) rendering/status-right-padding)
        center-x (+ text-x (/ text-w 2))
        center-width (q/text-width (or center ""))
        center-start (- center-x (/ center-width 2))
        right-start (- right-edge (q/text-width (or right "")))
        spans (concat
               (token-spans left (+ text-x rendering/status-left-padding) row-top row-bottom)
               (token-spans center center-start row-top row-bottom)
               (token-spans right right-start row-top row-bottom))]
    (some-> (hovered-token mouse-x mouse-y spans)
            (hud-tooltips/status-token-tooltip production-status))))

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
    (q/text tooltip (+ box-x padding) (+ box-y 16))
    (apply q/fill hud-text-color)))

(defn- draw-banner
  [text-x text-y]
  (let [banners (display/resolve-banner-list (sa/read-state :error-message)
                                             (sa/read-state :error-until)
                                             (sa/read-state :attention-message)
                                             (sa/read-state :turn-message))]
    (when (seq banners)
      (let [base-x (+ text-x config/msg-left-padding)
            y (+ text-y config/msg-line-1-y)]
        (loop [remaining banners
               x base-x
               first? true]
          (when-let [{:keys [kind text]} (first remaining)]
            (let [render-text (str (when-not first? " | ") text)
                  color (banner-color kind)
                  render-color (if first? color (soften-color color))]
              (apply q/fill render-color)
              (q/text render-text x y)
              (recur (next remaining)
                     (+ x (q/text-width render-text))
                     false)))))
      (apply q/fill hud-text-color))))

(defn- draw-status
  [text-x text-y text-w]
  (let [{:keys [left center right]}
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
        center-x (+ text-x (/ text-w 2))]
    (when left
      (apply q/fill hud-status-color)
      (q/text left (+ text-x rendering/status-left-padding) (+ text-y config/msg-line-2-y)))
    (when center
      (apply q/fill hud-text-color)
      (let [msg-width (q/text-width center)]
        (q/text center (- center-x (/ msg-width 2)) (+ text-y config/msg-line-2-y))))
    (when right
      (apply q/fill hud-status-color)
      (draw-text-right-justified right right-edge (+ text-y config/msg-line-2-y)))
    (apply q/fill hud-text-color)))

(defn- draw-inspector
  [text-x text-y]
  (let [{:keys [summary detail]}
        (display/resolve-inspector-lines (sa/read-state :hover-message))]
    (apply q/fill hud-text-color)
    (when summary
      (q/text summary (+ text-x config/msg-left-padding) (+ text-y config/msg-line-3-y)))
    (when detail
      (apply q/fill hud-secondary-color)
      (q/text detail (+ text-x config/msg-left-padding) (+ text-y rendering/msg-line-4-y))
      (apply q/fill hud-text-color))))

;; --- Message area master function ---

(defn draw-message-area
  "Draws the redesigned bottom HUD."
  []
  (let [[text-x text-y text-w _] (sa/read-state :text-area-dimensions)
        [_ _ _ text-h] (sa/read-state :text-area-dimensions)
        top-separator-y (- text-y config/msg-separator-offset)
        banner-separator-y (+ text-y rendering/msg-banner-separator-y)
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
        production-status (sa/read-state :production-status)
        mouse-x (q/mouse-x)
        mouse-y (q/mouse-y)
        tooltip (hud-tooltip mouse-x mouse-y text-x text-y text-w left center right production-status)]
    (q/no-stroke)
    (apply q/fill rendering/hud-background-color)
    (q/rect text-x text-y text-w text-h)
    (apply q/stroke rendering/hud-top-separator-color)
    (q/line text-x top-separator-y (+ text-x text-w) top-separator-y)
    (apply q/stroke rendering/hud-banner-separator-color)
    (q/line text-x banner-separator-y (+ text-x text-w) banner-separator-y)
    (q/text-font (sa/read-state :text-font))
    (apply q/fill hud-text-color)
    (draw-banner text-x text-y)
    (draw-status text-x text-y text-w)
    (draw-inspector text-x text-y)
    (when tooltip
      (draw-tooltip tooltip mouse-x mouse-y))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T02:42:20.853292-05:00", :module-hash "492272842", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 7, :hash "1580887966"} {:id "def/banner-error-color", :kind "def", :line 9, :end-line 9, :hash "117545043"} {:id "def/banner-attention-color", :kind "def", :line 10, :end-line 10, :hash "-320121614"} {:id "def/banner-result-color", :kind "def", :line 11, :end-line 11, :hash "1601020709"} {:id "def/hud-text-color", :kind "def", :line 12, :end-line 12, :hash "-1064068390"} {:id "def/hud-status-color", :kind "def", :line 13, :end-line 13, :hash "-673357353"} {:id "def/hud-secondary-color", :kind "def", :line 14, :end-line 14, :hash "942372744"} {:id "def/hud-tooltip-background", :kind "def", :line 15, :end-line 15, :hash "-1470863357"} {:id "def/hud-tooltip-border", :kind "def", :line 16, :end-line 16, :hash "-1372214356"} {:id "def/hud-tooltip-text", :kind "def", :line 17, :end-line 17, :hash "-1497255467"} {:id "defn-/draw-text-right-justified", :kind "defn-", :line 19, :end-line 24, :hash "-787062085"} {:id "defn-/banner-color", :kind "defn-", :line 26, :end-line 31, :hash "1492030370"} {:id "defn-/soften-color", :kind "defn-", :line 33, :end-line 35, :hash "-1707812362"} {:id "defn-/token-spans", :kind "defn-", :line 37, :end-line 54, :hash "-336658796"} {:id "defn-/inside-span?", :kind "defn-", :line 56, :end-line 59, :hash "-1589197964"} {:id "defn-/hovered-token", :kind "defn-", :line 61, :end-line 66, :hash "-1016660004"} {:id "defn/hud-tooltip", :kind "defn", :line 68, :end-line 83, :hash "-838720228"} {:id "defn/tooltip-box-position", :kind "defn", :line 85, :end-line 98, :hash "837235884"} {:id "defn-/draw-tooltip", :kind "defn-", :line 100, :end-line 112, :hash "1779301122"} {:id "defn-/draw-banner", :kind "defn-", :line 114, :end-line 135, :hash "694854181"} {:id "defn-/draw-status", :kind "defn-", :line 137, :end-line 161, :hash "1284236922"} {:id "defn-/draw-inspector", :kind "defn-", :line 163, :end-line 173, :hash "1866044165"} {:id "defn/draw-message-area", :kind "defn", :line 177, :end-line 211, :hash "-1286985703"}]}
;; clj-mutate-manifest-end
