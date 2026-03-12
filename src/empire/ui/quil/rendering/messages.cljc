(ns empire.ui.quil.rendering.messages
  (:require [empire.state.api :as sa]
            [empire.config.core :as config]
            [empire.config.rendering :as rendering]
            [empire.ui.util.rendering.display :as display]
            [quil.core :as q]))

(def ^:private banner-error-color [255 80 80])
(def ^:private banner-attention-color [255 215 64])
(def ^:private banner-result-color [235 245 255])
(def ^:private hud-text-color [230 230 230])
(def ^:private hud-secondary-color [170 170 170])

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

(defn- draw-banner
  [text-x text-y]
  (let [{:keys [kind text]} (display/resolve-banner (sa/read-state :error-message)
                                                    (sa/read-state :error-until)
                                                    (sa/read-state :attention-message)
                                                    (sa/read-state :turn-message))]
    (when text
      (apply q/fill (banner-color kind))
      (q/text text (+ text-x config/msg-left-padding) (+ text-y config/msg-line-1-y))
      (apply q/fill hud-text-color))))

(defn- draw-status
  [text-x text-y text-w]
  (let [{:keys [left center right]}
        (display/resolve-status-line (sa/read-state :round-number)
                                     (sa/read-state :paused)
                                     (sa/read-state :pause-requested)
                                     (sa/read-state :map-to-display)
                                     (sa/read-state :destination)
                                     (sa/read-state :production-status)
                                     (sa/current-world)
                                     (sa/read-state :cells-needing-attention))
        right-edge (- (+ text-x text-w) rendering/status-right-padding)
        center-x (+ text-x (/ text-w 2))]
    (apply q/fill hud-secondary-color)
    (when left
      (q/text left (+ text-x rendering/status-left-padding) (+ text-y config/msg-line-2-y)))
    (when center
      (let [msg-width (q/text-width center)]
        (q/text center (- center-x (/ msg-width 2)) (+ text-y config/msg-line-2-y))))
    (when right
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
        top-separator-y (- text-y config/msg-separator-offset)
        banner-separator-y (+ text-y rendering/msg-banner-separator-y)]
    (q/stroke 255)
    (q/line text-x top-separator-y (+ text-x text-w) top-separator-y)
    (q/line text-x banner-separator-y (+ text-x text-w) banner-separator-y)
    (q/text-font (sa/read-state :text-font))
    (apply q/fill hud-text-color)
    (draw-banner text-x text-y)
    (draw-status text-x text-y text-w)
    (draw-inspector text-x text-y)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T15:35:01.368136-05:00", :module-hash "1691766687", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 6, :hash "-2065200641"} {:id "def/banner-error-color", :kind "def", :line 8, :end-line 8, :hash "117545043"} {:id "def/banner-attention-color", :kind "def", :line 9, :end-line 9, :hash "-320121614"} {:id "def/banner-result-color", :kind "def", :line 10, :end-line 10, :hash "1601020709"} {:id "def/hud-text-color", :kind "def", :line 11, :end-line 11, :hash "-1064068390"} {:id "def/hud-secondary-color", :kind "def", :line 12, :end-line 12, :hash "-1855887430"} {:id "defn-/draw-text-right-justified", :kind "defn-", :line 14, :end-line 19, :hash "-787062085"} {:id "defn-/banner-color", :kind "defn-", :line 21, :end-line 26, :hash "1492030370"} {:id "defn-/draw-banner", :kind "defn-", :line 28, :end-line 37, :hash "-1311356262"} {:id "defn-/draw-status", :kind "defn-", :line 39, :end-line 60, :hash "-533535522"} {:id "defn-/draw-inspector", :kind "defn-", :line 62, :end-line 72, :hash "1866044165"} {:id "defn/draw-message-area", :kind "defn", :line 76, :end-line 89, :hash "-768691005"}]}
;; clj-mutate-manifest-end
