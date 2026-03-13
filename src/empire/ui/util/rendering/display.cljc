(ns empire.ui.util.rendering.display
  (:require [clojure.string :as str]
            [empire.state.api :as sa]
            [empire.config.core :as config]
            [empire.game-mechanics.containers.helpers :as uc]
            [empire.game-mechanics.movement.lakes :as lakes]
            [empire.ui.util.rendering.format :as fmt]))

(def ^:private default-cell-color [0 0 0])
(def ^:private lake-cell-color [0 120 220])
(def ^:private attention-flash-cell-color [255 255 255])
(def ^:private attention-flash-unit-color [0 0 0])
(def ^:private attention-normal-unit-color [255 255 255])
(def ^:private inspector-summary-max 56)
(def ^:private inspector-detail-max 48)
(def ^:private status-center-max 18)
(def ^:private status-right-max 24)
(defonce ^:private lake-cache* (atom {:map nil :limit nil :cells #{}}))
(defn- safe-color
  [cell]
  (or (config/color-of cell) default-cell-color))

(defn resolve-display-map
  "Returns the appropriate map based on map-to-display keyword."
  [map-to-display player-map computer-map game-map]
  ({:player-map player-map :computer-map computer-map :actual-map game-map} map-to-display))

(defn compute-hover-message
  "Looks up cell and production at coords in the-map, returns formatted hover string.
   Returns empty string when cell has no displayable status."
  [the-map production-map coords]
  (let [cell (get-in the-map coords)
        production (get production-map coords)]
    (or (fmt/format-hover-status coords cell production) "")))

(defn compute-hover-result
  "Computes hover message from map display state and mouse position."
  [map-to-display player-map computer-map game-map production coords]
  (let [the-map (resolve-display-map map-to-display player-map computer-map game-map)]
    (compute-hover-message the-map production coords)))

(defn determine-display-unit
  "Determines which unit to display, handling attention blinking.
   attention-coords is the list of cells needing attention (or nil).
   blink? is the current blink state for contained unit display."
  [col row cell attention-coords blink?]
  (let [contents (:contents cell)
        has-awake-airport? (uc/has-awake? cell :awake-fighters)
        has-any-airport? (pos? (uc/get-count cell :fighter-count))
        has-awake-carrier? (uc/has-awake-carrier-fighter? contents)
        has-awake-army? (uc/has-awake-army-aboard? contents)
        has-contained-unit? (or has-awake-airport? has-awake-carrier? has-awake-army?)
        is-attention-cell? (and (seq attention-coords) (= [col row] (first attention-coords)))]
    (cond
      (and is-attention-cell? has-contained-unit?)
      (uc/blinking-contained-unit has-awake-airport? has-awake-carrier? has-awake-army?)

      :else
      (uc/normal-display-unit cell contents has-awake-airport? has-any-airport?))))

(defn attention-unit-color
  "Returns the color for a displayed unit, overriding attention cells for stronger blink contrast."
  [display-unit col row attention-coords blink-attention?]
  (if (and display-unit
           (seq attention-coords)
           (= [col row] (first attention-coords)))
    (if blink-attention? attention-flash-unit-color attention-normal-unit-color)
    (config/unit->color display-unit)))

(defn- show-city-production?
  [cell map-to-display]
  (and (= :city (:type cell))
       (or (= :player (:city-status cell))
           (and (= :computer (:city-status cell))
                (= :computer-map map-to-display)))))

(defn production-indicator-data
  "Returns production indicator rendering data for a cell, or nil if none needed."
  ([row col cell production]
   (production-indicator-data row col cell production :player-map))
  ([row col cell production map-to-display]
   (when-let [prod (and (show-city-production? cell map-to-display)
                        (get production [col row]))]
    (when (and (map? prod) (:item prod))
      (let [item (:item prod)
            total (config/item-cost item)
            remaining (:remaining-rounds prod)
            progress (/ (- total remaining) (double total))
            base-color (safe-color cell)
            dark-color (mapv #(* % 0.5) base-color)]
        {:prod-char (config/item-chars item)
         :progress progress
         :remaining remaining
         :dark-color dark-color})))))

(defn- lake-cells-for-display [the-map map-to-display]
  (let [lake-limit (sa/read-state :lake-max-cells)]
    (if (and (= :computer-map map-to-display) (pos? lake-limit))
      (let [{:keys [map limit cells]} @lake-cache*]
        (if (and (identical? map the-map) (= limit lake-limit))
          cells
          (let [computed (lakes/lake-cells the-map lake-limit)]
            (reset! lake-cache* {:map the-map :limit lake-limit :cells computed})
            computed)))
      #{})))

(defn- completed-production-city? [cell production current]
  (and (= (:type cell) :city)
       (= :player (:city-status cell))
       (let [prod (production current)]
         (and (map? prod) (zero? (:remaining-rounds prod))))))

(defn- cell-base-color [cell current lake-cells]
  (if (and (= :sea (:type cell))
           (contains? lake-cells current))
    lake-cell-color
    (safe-color cell)))

(defn- final-cell-color
  [cell attention-cell production blink-attention? blink-completed? current lake-cells]
  (let [base-color (cell-base-color cell current lake-cells)
        flash-attention? (and (= current attention-cell) blink-attention?)
        flash-completed? (and (completed-production-city? cell production current) blink-completed?)]
    (cond
      flash-attention? attention-flash-cell-color
      flash-completed? [255 255 255]
      :else base-color)))

(defn group-cells-by-color
  "Groups map cells by their display color for batched rendering.
   Returns a map of [r g b] color to seq of {:col :row :cell} maps.
   blink-attention? and blink-completed? control flash states for attention cells and completed cities."
  ([the-map attention-coords production blink-attention? blink-completed?]
   (group-cells-by-color the-map attention-coords production blink-attention? blink-completed? :player-map))
  ([the-map attention-coords production blink-attention? blink-completed? map-to-display]
   (let [cols (count the-map)
         rows (count (first the-map))
         attention-cell (first attention-coords)
         lake-cells (lake-cells-for-display the-map map-to-display)]
    (reduce
     (fn [acc [col row]]
       (let [cell (get-in the-map [col row])]
         (if (= :unexplored (:type cell))
           acc
           (let [current [col row]
                 final-color (final-cell-color cell attention-cell production
                                               blink-attention? blink-completed?
                                               current lake-cells)]
             (update acc final-color conj {:col col :row row :cell cell})))))
     {}
     (for [col (range cols) row (range rows)] [col row])))))

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

(defn resolve-banner-pair
  "Resolves the primary and secondary banner messages for the HUD."
  [error-message error-until attention-message turn-message]
  (let [active-banners (remove nil?
                               [(when (and (should-show-error? error-until) (seq error-message))
                                  {:kind :error :text error-message})
                                (when (seq attention-message)
                                  {:kind :attention :text attention-message})
                                (when (seq turn-message)
                                  {:kind :result :text turn-message})])
        [primary secondary] active-banners]
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
  [round-number paused pause-requested map-to-display destination production-status current-world attention-coords]
  (let [map-label (map-display-label map-to-display)
        left-parts (remove nil? [(when (fmt/should-show-paused? paused pause-requested) "PAUSED")
                                 (round-label round-number)
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
;; {:version 1, :tested-at "2026-03-13T07:25:41.214448-05:00", :module-hash "-437398857", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 7, :hash "2128578818"} {:id "def/default-cell-color", :kind "def", :line 9, :end-line 9, :hash "-1922923999"} {:id "def/lake-cell-color", :kind "def", :line 10, :end-line 10, :hash "-600733486"} {:id "def/attention-flash-cell-color", :kind "def", :line 11, :end-line 11, :hash "-652560915"} {:id "def/attention-flash-unit-color", :kind "def", :line 12, :end-line 12, :hash "2066335485"} {:id "def/attention-normal-unit-color", :kind "def", :line 13, :end-line 13, :hash "-1032802797"} {:id "def/inspector-summary-max", :kind "def", :line 14, :end-line 14, :hash "1852984366"} {:id "def/inspector-detail-max", :kind "def", :line 15, :end-line 15, :hash "1224724967"} {:id "def/status-center-max", :kind "def", :line 16, :end-line 16, :hash "-580134895"} {:id "def/status-right-max", :kind "def", :line 17, :end-line 17, :hash "-539755630"} {:id "form/10/defonce", :kind "defonce", :line 18, :end-line 18, :hash "1764353803"} {:id "defn-/safe-color", :kind "defn-", :line 19, :end-line 21, :hash "-1335025619"} {:id "defn/resolve-display-map", :kind "defn", :line 23, :end-line 26, :hash "-442023512"} {:id "defn/compute-hover-message", :kind "defn", :line 28, :end-line 34, :hash "1130106368"} {:id "defn/compute-hover-result", :kind "defn", :line 36, :end-line 40, :hash "1222432952"} {:id "defn/determine-display-unit", :kind "defn", :line 42, :end-line 59, :hash "1928759595"} {:id "defn/attention-unit-color", :kind "defn", :line 61, :end-line 68, :hash "735152350"} {:id "defn-/show-city-production?", :kind "defn-", :line 70, :end-line 75, :hash "1360271203"} {:id "defn/production-indicator-data", :kind "defn", :line 77, :end-line 94, :hash "-722847162"} {:id "defn-/lake-cells-for-display", :kind "defn-", :line 96, :end-line 105, :hash "-1833440901"} {:id "defn-/completed-production-city?", :kind "defn-", :line 107, :end-line 111, :hash "-1046378919"} {:id "defn-/cell-base-color", :kind "defn-", :line 113, :end-line 117, :hash "1147982518"} {:id "defn-/final-cell-color", :kind "defn-", :line 119, :end-line 127, :hash "-1709973204"} {:id "defn/group-cells-by-color", :kind "defn", :line 129, :end-line 151, :hash "1520872785"} {:id "defn/should-show-error?", :kind "defn", :line 153, :end-line 156, :hash "1593526722"} {:id "defn/resolve-banner", :kind "defn", :line 158, :end-line 172, :hash "1089634649"} {:id "defn/resolve-banner-pair", :kind "defn", :line 174, :end-line 188, :hash "-900746893"} {:id "defn/resolve-banner-list", :kind "defn", :line 190, :end-line 201, :hash "1958103346"} {:id "defn-/map-display-label", :kind "defn-", :line 203, :end-line 207, :hash "-1751761517"} {:id "defn-/round-label", :kind "defn-", :line 209, :end-line 211, :hash "2093795019"} {:id "defn-/ellipsize", :kind "defn-", :line 213, :end-line 219, :hash "969107049"} {:id "defn-/format-coords", :kind "defn-", :line 221, :end-line 223, :hash "1411646979"} {:id "defn-/order-context-text", :kind "defn-", :line 225, :end-line 232, :hash "1958696046"} {:id "defn-/cell-order-context", :kind "defn-", :line 234, :end-line 244, :hash "-1545886776"} {:id "defn-/unit-order-context", :kind "defn-", :line 246, :end-line 254, :hash "-1291338553"} {:id "defn-/resolve-order-context", :kind "defn-", :line 256, :end-line 261, :hash "-1106020974"} {:id "defn/resolve-status-line", :kind "defn", :line 263, :end-line 276, :hash "534827550"} {:id "defn/resolve-inspector-lines", :kind "defn", :line 278, :end-line 284, :hash "1384942978"} {:id "defn/resolve-turn-text", :kind "defn", :line 286, :end-line 292, :hash "218744102"} {:id "defn/resolve-round-status-text", :kind "defn", :line 294, :end-line 300, :hash "2046987515"} {:id "defn/resolve-center-lines", :kind "defn", :line 302, :end-line 309, :hash "245117443"}]}
;; clj-mutate-manifest-end
