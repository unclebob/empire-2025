(ns empire.ui.util.rendering.display.text
  (:require [clojure.string :as str]
            [empire.config.core :as config]
            [empire.ui.util.rendering.format :as fmt]))

(def ^:private inspector-summary-max 56)
(def ^:private inspector-detail-max 48)
(def ^:private status-center-max 18)
(def ^:private status-right-max 24)

(defn- map-display-label
  [map-to-display]
  ({:player-map nil
    :computer-map "Comp"
    :actual-map "Actual"} map-to-display))

(defn- round-label
  [round-number]
  (str "R" round-number))

(defn- handicap-label
  [handicap-display-rounds]
  (when (some? handicap-display-rounds)
    (str "HC" handicap-display-rounds)))

(defn- ellipsize
  [s max-len]
  (when (seq s)
    (let [trimmed (str/trim s)]
      (if (<= (count trimmed) max-len)
        trimmed
        (str (str/trim (subs trimmed 0 (max 0 (- max-len 3)))) "...")))))

(defn- format-coords
  [[x y]]
  (str x "," y))

(def ^:private order-context-labels
  {:flight "Flight "
   :march "March "
   :waypoint "Waypoint "})

(defn- order-context-text
  [order-type coords]
  (when-let [label (and coords (order-context-labels order-type))]
    (str label (format-coords coords))))

(defn- cell-order-context
  [cell]
  (let [city-marching-orders (:marching-orders cell)
        city-flight-path (:flight-path cell)
        waypoint-orders (get-in cell [:waypoint :marching-orders])]
    (cond
      (= city-marching-orders :lookaround) "Lookaround"
      city-flight-path (order-context-text :flight city-flight-path)
      city-marching-orders (order-context-text :march city-marching-orders)
      :else (order-context-text :waypoint waypoint-orders))))

(defn- unit-order-context
  [cell]
  (let [contents (:contents cell)
        unit-marching-orders (:marching-orders contents)
        unit-flight-path (:flight-path contents)]
    (cond
      unit-flight-path (order-context-text :flight unit-flight-path)
      unit-marching-orders (order-context-text :march unit-marching-orders)
      :else nil)))

(defn- resolve-order-context
  [current-world attention-coords]
  (when-let [coords (first attention-coords)]
    (let [cell (get-in current-world coords)]
      (or (cell-order-context cell)
          (unit-order-context cell)))))

(defn resolve-status-line
  "Builds the compact left/center/right status line fields for the redesigned HUD."
  [round-number handicap-display-rounds paused pause-requested map-to-display destination production-status current-world attention-coords]
  (let [map-label (map-display-label map-to-display)
        left-parts (remove nil? [(when (fmt/should-show-paused? paused pause-requested) "PAUSED")
                                 (round-label round-number)
                                 (handicap-label handicap-display-rounds)
                                 map-label])
        center (or (when destination
                     (str "Dest " (format-coords destination)))
                   (resolve-order-context current-world attention-coords))
        right (fmt/compact-production-status production-status)]
    {:left (when (seq left-parts) (str/join "  " left-parts))
     :center (ellipsize center status-center-max)
     :right (ellipsize right status-right-max)}))

(defn resolve-inspector-lines
  "Returns the inspector summary/detail lines for the redesigned HUD.
   Splits hover text into summary/detail and truncates both lines."
  [hover-message]
  (let [{:keys [summary detail]} (fmt/split-hover-status hover-message)]
    {:summary (ellipsize summary inspector-summary-max)
     :detail (ellipsize detail inspector-detail-max)}))

(defn- non-empty-zone
  [message]
  (when (seq message) message))

(defn resolve-attention-zone
  "Returns the attention zone text, or nil if empty."
  [attention-message]
  (non-empty-zone attention-message))

(defn resolve-warning-zone
  "Returns the warning zone text, or nil if empty."
  [warning-message]
  (non-empty-zone warning-message))

(defn resolve-command-zone
  "Returns the command response zone text, or nil if empty."
  [command-message]
  (non-empty-zone command-message))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-01T16:10:58.303671-05:00", :module-hash "-73492892", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "833626816"} {:id "def/inspector-summary-max", :kind "def", :line 6, :end-line nil, :hash "1852984366"} {:id "def/inspector-detail-max", :kind "def", :line 7, :end-line nil, :hash "1224724967"} {:id "def/status-center-max", :kind "def", :line 8, :end-line nil, :hash "-580134895"} {:id "def/status-right-max", :kind "def", :line 9, :end-line nil, :hash "-539755630"} {:id "defn-/map-display-label", :kind "defn-", :line 11, :end-line nil, :hash "-1751761517"} {:id "defn-/round-label", :kind "defn-", :line 17, :end-line nil, :hash "2093795019"} {:id "defn-/handicap-label", :kind "defn-", :line 21, :end-line nil, :hash "-1493260915"} {:id "defn-/ellipsize", :kind "defn-", :line 26, :end-line nil, :hash "969107049"} {:id "defn-/format-coords", :kind "defn-", :line 34, :end-line nil, :hash "1411646979"} {:id "def/order-context-labels", :kind "def", :line 38, :end-line nil, :hash "-1144367723"} {:id "defn-/order-context-text", :kind "defn-", :line 43, :end-line nil, :hash "-101981665"} {:id "defn-/cell-order-context", :kind "defn-", :line 48, :end-line nil, :hash "-1798849193"} {:id "defn-/unit-order-context", :kind "defn-", :line 59, :end-line nil, :hash "-1291338553"} {:id "defn-/resolve-order-context", :kind "defn-", :line 69, :end-line nil, :hash "-1106020974"} {:id "defn/resolve-status-line", :kind "defn", :line 76, :end-line nil, :hash "-1283773035"} {:id "defn/resolve-inspector-lines", :kind "defn", :line 92, :end-line nil, :hash "1384942978"} {:id "defn-/non-empty-zone", :kind "defn-", :line 100, :end-line nil, :hash "716274352"} {:id "defn/resolve-attention-zone", :kind "defn", :line 104, :end-line nil, :hash "-1659849673"} {:id "defn/resolve-warning-zone", :kind "defn", :line 109, :end-line nil, :hash "-2074879412"} {:id "defn/resolve-command-zone", :kind "defn", :line 114, :end-line nil, :hash "2066075863"}]}
;; clj-mutate-manifest-end
