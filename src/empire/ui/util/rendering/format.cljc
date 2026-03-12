(ns empire.ui.util.rendering.format
  (:require [empire.game.production-status :as production-status]
            [empire.config.core :as config]
            [empire.game-mechanics.containers.helpers :as uc]
            [empire.config.units.dispatcher :as dispatcher]))

(defn should-show-paused?
  "Returns true if the PAUSED message should be displayed."
  [paused pause-requested]
  (or paused pause-requested))

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

(defn format-unit-status
  "Formats status string for a unit."
  [unit]
  (let [max-hits (config/item-hits (:type unit))
        hits (or (:hits unit) max-hits)]
    (str (name (:owner unit)) " " (name (:type unit))
         " [" hits "/" max-hits "]"
         (unit-fuel-str unit)
         (unit-cargo-str unit)
         (transport-mission-str unit)
         (army-mission-str unit)
         (patrol-mode-str unit)
         (unit-orders-str unit)
         " " (name (:mode unit)))))

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

(defn format-city-status
  "Formats status string for a city. Production is the production entry for this city, or nil."
  [cell production]
  (let [status (:city-status cell)
        fighters (:fighter-count cell 0)
        shipyard (uc/get-shipyard-ships cell)]
    (str "city:" (name status)
         (when (and (= status :player) production)
           (str " producing:" (if (= production :none) "none" (name (:item production)))))
         (when (pos? fighters) (str " fighters:" fighters))
         (when (:marching-orders cell)
           (if (= (:marching-orders cell) :lookaround) " lookaround" " march"))
         (when (:flight-path cell) " flight")
         (format-shipyard shipyard))))

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
                      (:contents cell) (format-unit-status (:contents cell))
                      (= (:type cell) :city) (format-city-status cell production)
                      (:waypoint cell) (format-waypoint-status (:waypoint cell))
                      :else nil)]
    (str "[" (first coords) "," (second coords) "] " status)))

(declare compact-detail)

(defn- split-unit-hover
  [coords-part rest]
  (let [[owner unit-type hits & detail-tokens] (clojure.string/split rest #" ")]
    {:summary (str coords-part " " owner " " unit-type " " hits)
     :detail (when (seq detail-tokens)
               (compact-detail (clojure.string/join " " detail-tokens)))}))

(defn- compact-detail-token
  [token]
  (cond
    (clojure.string/starts-with? token "producing:") (clojure.string/replace token "producing:" "prod:")
    (clojure.string/starts-with? token "fighters:") (clojure.string/replace token "fighters:" "ftrs:")
    (clojure.string/starts-with? token "timeout:") (clojure.string/replace token "timeout:" "to:")
    (clojure.string/starts-with? token "mission:") (clojure.string/replace token "mission:" "mis:")
    :else token))

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

(defn- split-waypoint-hover
  [coords-part rest]
  (let [[waypoint-text & detail-tokens] (clojure.string/split rest #" ")]
    {:summary (str coords-part " " (clojure.string/capitalize waypoint-text))
     :detail (when (seq detail-tokens)
               (compact-detail (clojure.string/join " " detail-tokens)))}))

(defn- hover-kind
  [rest]
  (cond
    (clojure.string/starts-with? rest "city:") :city
    (clojure.string/starts-with? rest "waypoint") :waypoint
    :else :unit))

(defn split-hover-status
  "Splits a formatted hover string into inspector summary/detail lines."
  [hover-status]
  (if-not (seq hover-status)
    {:summary nil :detail nil}
    (let [[coords-part rest] (clojure.string/split hover-status #" " 2)]
      (if-not (seq rest)
        {:summary hover-status :detail nil}
        (case (hover-kind rest)
          :city (split-city-hover coords-part rest)
          :waypoint (split-waypoint-hover coords-part rest)
          (split-unit-hover coords-part rest))))))

(defn format-production-status
  "Formats production status string: unit counts and exploration %.
   Format: A:n F:n T:n D:n S:n P:n C:n B:n Z:n | nn%"
  [game-map player-map]
  (production-status/format-production-status game-map player-map))

(defn- parse-count-entry
  [entry]
  (let [[label count-str] (clojure.string/split entry #":" 2)
        count (when (seq count-str)
                (parse-long count-str))]
    {:label label
     :count count}))

(defn- compact-nonzero-units
  [counts-part]
  (->> (clojure.string/split (or counts-part "") #" ")
       (map parse-count-entry)
       (keep (fn [{:keys [label count]}]
               (when (and label count (pos? count))
                 (str label count))))
       vec))

(defn- summarize-nonzero-units
  [nonzero-units]
  (let [visible-units (take 3 nonzero-units)
        hidden-count (- (count nonzero-units) (count visible-units))]
    (str (clojure.string/join " " visible-units)
         (when (pos? hidden-count)
           (str " +" hidden-count)))))

(defn- parseable-production-status?
  [counts-part nonzero-units]
      (or (seq nonzero-units)
      (clojure.string/includes? (or counts-part "") "A:")))

(defn- join-production-summary
  [units-summary pct-part]
  (str units-summary
       (when (seq pct-part)
         (str " | " pct-part))))

(defn compact-production-status
  "Compacts a production status string for the HUD status line."
  [production-status]
  (when (seq production-status)
    (let [[counts-part pct-part] (clojure.string/split production-status #" \| " 2)
          nonzero-units (compact-nonzero-units counts-part)
          parseable? (parseable-production-status? counts-part nonzero-units)
          units-summary (if (seq nonzero-units)
                          (summarize-nonzero-units nonzero-units)
                          "0 units")]
      (if-not parseable?
        production-status
        (join-production-summary units-summary pct-part)))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T16:19:42.300662-05:00", :module-hash "841015092", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 5, :hash "434536401"} {:id "defn/should-show-paused?", :kind "defn", :line 7, :end-line 10, :hash "1556317815"} {:id "defn-/unit-fuel-str", :kind "defn-", :line 12, :end-line 14, :hash "1572131136"} {:id "defn-/unit-cargo-str", :kind "defn-", :line 16, :end-line 20, :hash "-912894557"} {:id "defn-/transport-mission-str", :kind "defn-", :line 22, :end-line 27, :hash "2125337359"} {:id "defn-/patrol-mode-str", :kind "defn-", :line 29, :end-line 31, :hash "1917316096"} {:id "defn-/army-mission-str", :kind "defn-", :line 33, :end-line 35, :hash "18689968"} {:id "defn-/unit-orders-str", :kind "defn-", :line 37, :end-line 41, :hash "-636057977"} {:id "defn/format-unit-status", :kind "defn", :line 43, :end-line 56, :hash "1526979702"} {:id "defn-/format-ship-for-dock", :kind "defn-", :line 58, :end-line 63, :hash "2019898018"} {:id "defn-/format-shipyard", :kind "defn-", :line 65, :end-line 69, :hash "1584376118"} {:id "defn/format-city-status", :kind "defn", :line 71, :end-line 84, :hash "-1175931009"} {:id "defn/format-waypoint-status", :kind "defn", :line 86, :end-line 92, :hash "1023450594"} {:id "defn/format-hover-status", :kind "defn", :line 94, :end-line 103, :hash "115637379"} {:id "form/14/declare", :kind "declare", :line 105, :end-line 105, :hash "1539091516"} {:id "defn-/split-unit-hover", :kind "defn-", :line 107, :end-line 112, :hash "-181459465"} {:id "defn-/compact-detail-token", :kind "defn-", :line 114, :end-line 121, :hash "-1028236552"} {:id "defn-/compact-detail", :kind "defn-", :line 123, :end-line 128, :hash "-468040357"} {:id "defn-/split-city-hover", :kind "defn-", :line 130, :end-line 139, :hash "-1457307672"} {:id "defn-/split-waypoint-hover", :kind "defn-", :line 141, :end-line 146, :hash "-1536619894"} {:id "defn-/hover-kind", :kind "defn-", :line 148, :end-line 153, :hash "1578687087"} {:id "defn/split-hover-status", :kind "defn", :line 155, :end-line 166, :hash "1155669426"} {:id "defn/format-production-status", :kind "defn", :line 168, :end-line 172, :hash "1957771995"} {:id "defn-/parse-count-entry", :kind "defn-", :line 174, :end-line 180, :hash "-1237319404"} {:id "defn-/compact-nonzero-units", :kind "defn-", :line 182, :end-line 189, :hash "131416162"} {:id "defn-/summarize-nonzero-units", :kind "defn-", :line 191, :end-line 197, :hash "-489438097"} {:id "defn-/parseable-production-status?", :kind "defn-", :line 199, :end-line 202, :hash "1843558349"} {:id "defn-/join-production-summary", :kind "defn-", :line 204, :end-line 208, :hash "-2105547342"} {:id "defn/compact-production-status", :kind "defn", :line 210, :end-line 222, :hash "644266267"}]}
;; clj-mutate-manifest-end
