(ns empire.ui.util.rendering.display.text
  (:require [clojure.string :as str]
            [empire.config.core :as config]
            [empire.ui.util.rendering.format :as fmt]))

(def ^:private inspector-summary-max 56)
(def ^:private inspector-detail-max 48)
(def ^:private status-center-max 18)
(def ^:private status-right-max 24)

(defn should-show-error?
  "Returns true if the error message should be shown."
  [error-until]
  (< (System/currentTimeMillis) error-until))

(defn resolve-banner
  "Resolves the highest-priority banner message for the HUD."
  [error-message error-until attention-message turn-message]
  (cond
    (and (should-show-error? error-until) (seq error-message))
    {:kind :error :text error-message}

    (seq attention-message)
    {:kind :attention :text attention-message}

    (seq turn-message)
    {:kind :result :text turn-message}

    :else
    {:kind :empty :text nil}))

(defn- active-banners
  [error-message error-until attention-message turn-message]
  (remove nil?
          [(when (and (should-show-error? error-until) (seq error-message))
             {:kind :error :text error-message})
           (when (seq attention-message)
             {:kind :attention :text attention-message})
           (when (seq turn-message)
             {:kind :result :text turn-message})]))

(defn resolve-banner-pair
  "Resolves the primary and secondary banner messages for the HUD."
  [error-message error-until attention-message turn-message]
  (let [[primary secondary] (active-banners error-message error-until attention-message turn-message)]
    {:primary (or primary {:kind :empty :text nil})
     :secondary (when (and secondary
                           (not= (:text primary) (:text secondary)))
                  secondary)}))

(defn resolve-banner-list
  "Resolves up to three active banner messages in priority order."
  [error-message error-until attention-message turn-message]
  (->> [(when (and (should-show-error? error-until) (seq error-message))
          {:kind :error :text error-message})
        (when (seq attention-message)
          {:kind :attention :text attention-message})
        (when (seq turn-message)
          {:kind :result :text turn-message})]
       (remove nil?)
       (distinct)
       (take 3)))

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

(defn- order-context-text
  [order-type coords]
  (when coords
    (case order-type
      :flight (str "Flight " (format-coords coords))
      :march (str "March " (format-coords coords))
      :waypoint (str "Waypoint " (format-coords coords))
      nil)))

(defn- cell-order-context
  [cell]
  (let [city-marching-orders (:marching-orders cell)
        city-flight-path (:flight-path cell)
        waypoint-orders (get-in cell [:waypoint :marching-orders])]
    (cond
      (= city-marching-orders :lookaround) "Lookaround"
      city-flight-path (order-context-text :flight city-flight-path)
      city-marching-orders (order-context-text :march city-marching-orders)
      waypoint-orders (order-context-text :waypoint waypoint-orders)
      :else nil)))

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

(defn resolve-turn-text
  "Returns the turn text to display, falling back to destination."
  [turn-message destination]
  (cond
    (seq turn-message) turn-message
    destination (format (:destination config/messages) (first destination) (second destination))
    :else nil))

(defn resolve-round-status-text
  "Returns the round status text with optional PAUSED prefix."
  [round-number paused pause-requested]
  (let [round-str (str "Round: " round-number)]
    (if (fmt/should-show-paused? paused pause-requested)
      {:text (str "PAUSED  " round-str) :paused? true :round-str round-str}
      {:text round-str :paused? false})))

(defn resolve-center-lines
  "Returns up to three center-region lines.
   Always derived from debug-message text."
  [_map-to-display _major-invasion-state _round-number debug-message]
  (->> (str/split (or debug-message "") #"\n")
       (take 3)
       (filter seq)
       vec))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T12:04:09.175173-05:00", :module-hash "565093956", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 4, :hash "833626816"} {:id "def/inspector-summary-max", :kind "def", :line 6, :end-line 6, :hash "1852984366"} {:id "def/inspector-detail-max", :kind "def", :line 7, :end-line 7, :hash "1224724967"} {:id "def/status-center-max", :kind "def", :line 8, :end-line 8, :hash "-580134895"} {:id "def/status-right-max", :kind "def", :line 9, :end-line 9, :hash "-539755630"} {:id "defn/should-show-error?", :kind "defn", :line 11, :end-line 14, :hash "1593526722"} {:id "defn/resolve-banner", :kind "defn", :line 16, :end-line 30, :hash "1089634649"} {:id "defn-/active-banners", :kind "defn-", :line 32, :end-line 40, :hash "1327196984"} {:id "defn/resolve-banner-pair", :kind "defn", :line 42, :end-line 49, :hash "169264082"} {:id "defn/resolve-banner-list", :kind "defn", :line 51, :end-line 62, :hash "1958103346"} {:id "defn-/map-display-label", :kind "defn-", :line 64, :end-line 68, :hash "-1751761517"} {:id "defn-/round-label", :kind "defn-", :line 70, :end-line 72, :hash "2093795019"} {:id "defn-/handicap-label", :kind "defn-", :line 74, :end-line 77, :hash "-1493260915"} {:id "defn-/ellipsize", :kind "defn-", :line 79, :end-line 85, :hash "969107049"} {:id "defn-/format-coords", :kind "defn-", :line 87, :end-line 89, :hash "1411646979"} {:id "defn-/order-context-text", :kind "defn-", :line 91, :end-line 98, :hash "1958696046"} {:id "defn-/cell-order-context", :kind "defn-", :line 100, :end-line 110, :hash "-1545886776"} {:id "defn-/unit-order-context", :kind "defn-", :line 112, :end-line 120, :hash "-1291338553"} {:id "defn-/resolve-order-context", :kind "defn-", :line 122, :end-line 127, :hash "-1106020974"} {:id "defn/resolve-status-line", :kind "defn", :line 129, :end-line 143, :hash "-1283773035"} {:id "defn/resolve-inspector-lines", :kind "defn", :line 145, :end-line 151, :hash "1384942978"} {:id "defn/resolve-turn-text", :kind "defn", :line 153, :end-line 159, :hash "218744102"} {:id "defn/resolve-round-status-text", :kind "defn", :line 161, :end-line 167, :hash "2046987515"} {:id "defn/resolve-center-lines", :kind "defn", :line 169, :end-line 176, :hash "245117443"}]}
;; clj-mutate-manifest-end
