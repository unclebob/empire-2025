(ns empire.ui.util.rendering.format
  (:require [empire.application.production-status :as production-status]
            [empire.config :as config]
            [empire.containers.helpers :as uc]
            [empire.units.dispatcher :as dispatcher]))

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

(defn format-production-status
  "Formats production status string: unit counts and exploration %.
   Format: A:n F:n T:n D:n S:n P:n C:n B:n Z:n | nn%"
  [game-map player-map]
  (production-status/format-production-status game-map player-map))
