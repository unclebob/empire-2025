(ns empire.ui.util.rendering.format.hover
  (:require [empire.config.core :as config]
            [empire.game-mechanics.containers.helpers :as uc]
            [empire.config.units.dispatcher :as dispatcher]))

(defn should-show-paused?
  "Returns true if the PAUSED message should be displayed."
  [paused pause-requested]
  (or paused pause-requested))

(defn- safe-name
  [x fallback]
  (if (keyword? x) (name x) fallback))

(defn- unit-fuel-str [unit]
  (when (= (:type unit) :fighter)
    (str " fuel:" (:fuel unit))))

(defn- unit-cargo-str [unit]
  (case (:type unit)
    :transport (str " cargo:" (:army-count unit 0))
    :carrier (str " cargo:" (:fighter-count unit 0))
    nil))

(defn- transport-mission-str [unit]
  (when (= (:type unit) :transport)
    (let [mission (:transport-mission unit)
          timeout (:loading-timeout unit)]
      (str (when mission (str " " (name mission)))
           (when timeout (str " timeout:" timeout))))))

(defn- patrol-mode-str [unit]
  (when-let [pm (:patrol-mode unit)]
    (str " " (name pm))))

(defn- army-mission-str [unit]
  (when-let [m (and (= (:type unit) :army) (:mission unit))]
    (str " mission:" (name m))))

(defn- unit-orders-str [unit]
  (cond
    (:marching-orders unit) " march"
    (:flight-path unit) " flight"
    :else nil))

(defn- unit-target-str [unit]
  (when (and (= :moving (:mode unit))
             (vector? (:target unit))
             (= 2 (count (:target unit))))
    (str " target:" (first (:target unit)) "," (second (:target unit)))))

(defn format-unit-status
  "Formats status string for a unit."
  [unit]
  (let [max-hits (config/item-hits (:type unit))
        hits (or (:hits unit) max-hits "?")
        max-hits-str (or max-hits "?")]
    (str (safe-name (:owner unit) "unknown") " " (safe-name (:type unit) "unit")
         " [" hits "/" max-hits-str "]"
         (unit-fuel-str unit)
         (unit-cargo-str unit)
         (transport-mission-str unit)
         (army-mission-str unit)
         (patrol-mode-str unit)
         (unit-orders-str unit)
         (unit-target-str unit)
         " " (safe-name (:mode unit) "unknown"))))

(defn- format-ship-for-dock
  "Formats a single ship for dock display: T[2/3] for type[hits/max]"
  [ship]
  (let [type-char (first (dispatcher/display-char (:type ship)))
        max-hits (dispatcher/hits (:type ship))]
    (str type-char "[" (:hits ship) "/" max-hits "]")))

(defn- format-shipyard
  "Formats shipyard contents as condensed string: D[2/3],B[7/10]"
  [shipyard]
  (when (seq shipyard)
    (str " dock:" (clojure.string/join "," (map format-ship-for-dock shipyard)))))

(defn- city-production-str
  [status production]
  (when (and (= status :player) production)
    (if (= production :none)
      " producing:none"
      (str " producing:" (name (:item production))
           " rounds:" (:remaining-rounds production)))))

(defn- coordinate-pair?
  [value]
  (and (vector? value)
       (= 2 (count value))
       (number? (first value))
       (number? (second value))))

(defn- march-target
  [marching-orders]
  (cond
    (coordinate-pair? marching-orders) marching-orders
    (and (sequential? marching-orders)
         (vector? (first marching-orders))) (first marching-orders)))

(defn- marching-orders-str
  [marching-orders]
  (let [target (march-target marching-orders)]
    (cond
      (= marching-orders :lookaround) " lookaround"
      target (str " march:" (first target) "," (second target))
      marching-orders " march")))

(defn- flight-path-str
  [flight-path]
  (when (vector? flight-path)
    (str " flight:" (first flight-path) "," (second flight-path))))

(defn- city-orders-str
  [cell]
  (let [marching-orders (:marching-orders cell)
        flight-path (:flight-path cell)]
    (str (marching-orders-str marching-orders)
         (flight-path-str flight-path))))

(defn format-city-status
  "Formats status string for a city. Production is the production entry for this city, or nil."
  [cell production]
  (let [status (:city-status cell)
        fighters (:fighter-count cell 0)]
    (str "city:" (name status)
         (when-let [country-id (:country-id cell)]
           (str " cid:" country-id))
         (city-production-str status production)
         (when (pos? fighters) (str " fighters:" fighters))
         (city-orders-str cell)
         (format-shipyard (uc/get-shipyard-ships cell)))))

(defn- format-terrain-status
  [cell]
  (when (#{:land :sea} (:type cell))
    (str (name (:type cell))
         " cid:" (or (:country-id cell) "nil"))))

(defn format-waypoint-status
  "Formats status string for a waypoint."
  [waypoint]
  (let [orders (:marching-orders waypoint)]
    (if orders
      (str "waypoint -> " (first orders) "," (second orders))
      "waypoint (no orders)")))

(defn format-hover-status
  "Formats a status string for a cell. Production is the production entry for this cell, or nil.
   Coords is [col row] of the cell being hovered."
  [coords cell production]
  (when-let [status (cond
                      (= (:type cell) :city) (format-city-status cell production)
                      (:contents cell) (format-unit-status (:contents cell))
                      (:waypoint cell) (format-waypoint-status (:waypoint cell))
                      :else (format-terrain-status cell))]
    (str "[" (first coords) "," (second coords) "] " status)))

(declare compact-detail)

(defn- split-unit-hover
  [coords-part rest]
  (let [[owner unit-type hits & detail-tokens] (clojure.string/split rest #" ")]
    {:summary (str coords-part " " owner " " unit-type " " hits)
     :detail (when (seq detail-tokens)
               (compact-detail (clojure.string/join " " detail-tokens)))}))

(def ^:private detail-token-abbrevs
  [["producing:" "prod:"]
   ["rounds:" "rnd:"]
   ["fighters:" "ftrs:"]
   ["timeout:" "to:"]
   ["mission:" "mis:"]])

(defn- abbreviate-detail-token
  [token [from to]]
  (when (clojure.string/starts-with? token from)
    (clojure.string/replace token from to)))

(defn- compact-detail-token
  [token]
  (or (some #(abbreviate-detail-token token %) detail-token-abbrevs)
      token))

(defn- compact-detail
  [detail]
  (when (seq detail)
    (->> (clojure.string/split detail #" ")
         (map compact-detail-token)
         (clojure.string/join " "))))

(defn- split-city-hover
  [coords-part rest]
  (let [[city-status & detail-tokens] (clojure.string/split rest #" ")
        owner (second (clojure.string/split city-status #":" 2))
        city-summary (if owner
                       (str (clojure.string/capitalize owner) " City")
                       city-status)]
    {:summary (str coords-part " " city-summary)
     :detail (when (seq detail-tokens)
               (compact-detail (clojure.string/join " " detail-tokens)))}))

(defn- split-capitalized-hover
  [coords-part rest]
  (let [[summary-token & detail-tokens] (clojure.string/split rest #" ")]
    {:summary (str coords-part " " (clojure.string/capitalize summary-token))
     :detail (when (seq detail-tokens)
               (compact-detail (clojure.string/join " " detail-tokens)))}))

(defn- split-waypoint-hover
  [coords-part rest]
  (split-capitalized-hover coords-part rest))

(defn- split-terrain-hover
  [coords-part rest]
  (split-capitalized-hover coords-part rest))

(defn- hover-kind
  [rest]
  (cond
    (clojure.string/starts-with? rest "city:") :city
    (clojure.string/starts-with? rest "waypoint") :waypoint
    (or (clojure.string/starts-with? rest "land ")
        (clojure.string/starts-with? rest "sea ")) :terrain
    :else :unit))

(defn- split-hover-by-kind
  [coords-part rest]
  (case (hover-kind rest)
    :city (split-city-hover coords-part rest)
    :waypoint (split-waypoint-hover coords-part rest)
    :terrain (split-terrain-hover coords-part rest)
    (split-unit-hover coords-part rest)))

(defn- split-hover-rest
  [hover-status coords-part rest]
  (if-not (seq rest)
    {:summary hover-status :detail nil}
    (split-hover-by-kind coords-part rest)))

(defn split-hover-status
  "Splits a formatted hover string into inspector summary/detail lines."
  [hover-status]
  (if-not (seq hover-status)
    {:summary nil :detail nil}
    (let [[coords-part rest] (clojure.string/split hover-status #" " 2)]
      (split-hover-rest hover-status coords-part rest))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-05-07T19:20:14.778041-05:00", :module-hash "919031281", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 4, :hash "2121582387"} {:id "defn/should-show-paused?", :kind "defn", :line 6, :end-line 9, :hash "1556317815"} {:id "defn-/safe-name", :kind "defn-", :line 11, :end-line 13, :hash "-1628295260"} {:id "defn-/unit-fuel-str", :kind "defn-", :line 15, :end-line 17, :hash "1572131136"} {:id "defn-/unit-cargo-str", :kind "defn-", :line 19, :end-line 23, :hash "-912894557"} {:id "defn-/transport-mission-str", :kind "defn-", :line 25, :end-line 30, :hash "2125337359"} {:id "defn-/patrol-mode-str", :kind "defn-", :line 32, :end-line 34, :hash "1917316096"} {:id "defn-/army-mission-str", :kind "defn-", :line 36, :end-line 38, :hash "18689968"} {:id "defn-/unit-orders-str", :kind "defn-", :line 40, :end-line 44, :hash "-636057977"} {:id "defn-/unit-target-str", :kind "defn-", :line 46, :end-line 50, :hash "-543671099"} {:id "defn/format-unit-status", :kind "defn", :line 52, :end-line 67, :hash "745030943"} {:id "defn-/format-ship-for-dock", :kind "defn-", :line 69, :end-line 74, :hash "2019898018"} {:id "defn-/format-shipyard", :kind "defn-", :line 76, :end-line 80, :hash "1584376118"} {:id "defn-/city-production-str", :kind "defn-", :line 82, :end-line 88, :hash "245476064"} {:id "defn-/coordinate-pair?", :kind "defn-", :line 90, :end-line 95, :hash "-1023604492"} {:id "defn-/march-target", :kind "defn-", :line 97, :end-line 102, :hash "1344081493"} {:id "defn-/marching-orders-str", :kind "defn-", :line 104, :end-line 110, :hash "-638452953"} {:id "defn-/flight-path-str", :kind "defn-", :line 112, :end-line 115, :hash "303918737"} {:id "defn-/city-orders-str", :kind "defn-", :line 117, :end-line 122, :hash "-1367287606"} {:id "defn/format-city-status", :kind "defn", :line 124, :end-line 135, :hash "981966243"} {:id "defn-/format-terrain-status", :kind "defn-", :line 137, :end-line 141, :hash "-1429165815"} {:id "defn/format-waypoint-status", :kind "defn", :line 143, :end-line 149, :hash "1023450594"} {:id "defn/format-hover-status", :kind "defn", :line 151, :end-line 160, :hash "-1863990030"} {:id "form/23/declare", :kind "declare", :line 162, :end-line 162, :hash "1539091516"} {:id "defn-/split-unit-hover", :kind "defn-", :line 164, :end-line 169, :hash "-181459465"} {:id "defn-/compact-detail-token", :kind "defn-", :line 171, :end-line 179, :hash "-1585523871"} {:id "defn-/compact-detail", :kind "defn-", :line 181, :end-line 186, :hash "-468040357"} {:id "defn-/split-city-hover", :kind "defn-", :line 188, :end-line 197, :hash "-1457307672"} {:id "defn-/split-capitalized-hover", :kind "defn-", :line 199, :end-line 204, :hash "623314853"} {:id "defn-/split-waypoint-hover", :kind "defn-", :line 206, :end-line 208, :hash "151217740"} {:id "defn-/split-terrain-hover", :kind "defn-", :line 210, :end-line 212, :hash "2026826951"} {:id "defn-/hover-kind", :kind "defn-", :line 214, :end-line 221, :hash "-590361608"} {:id "defn/split-hover-status", :kind "defn", :line 223, :end-line 235, :hash "1412963234"}]}
;; clj-mutate-manifest-end
