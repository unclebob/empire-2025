(ns empire.ui.rendering-util
  (:require [empire.config :as config]
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

(def ^:private unit-type-order
  [:army :fighter :transport :destroyer :submarine :patrol-boat :carrier :battleship :satellite])

(def ^:private unit-type-labels
  {:army "A" :fighter "F" :transport "T" :destroyer "D" :submarine "S"
   :patrol-boat "P" :carrier "C" :battleship "B" :satellite "Z"})

(defn format-production-status
  "Formats production status string: unit counts and exploration %.
   Format: A:n F:n T:n D:n S:n P:n C:n B:n Z:n | nn%"
  [game-map player-map]
  (let [counts (atom (zipmap unit-type-order (repeat 0)))
        total-cells (atom 0)
        explored-cells (atom 0)]
    (doseq [col (range (count game-map))
            row (range (count (first game-map)))]
      (swap! total-cells inc)
      (let [player-cell (get-in player-map [col row])]
        (when (and player-cell (not= :unexplored (:type player-cell)))
          (swap! explored-cells inc)))
      (let [cell (get-in game-map [col row])
            unit (:contents cell)]
        (when (and unit (= :player (:owner unit)))
          (swap! counts update (:type unit) inc))))
    (let [pct (if (zero? @total-cells) 0 (int (* 100 (/ @explored-cells @total-cells))))
          unit-strs (map #(str (unit-type-labels %) ":" (get @counts %)) unit-type-order)]
      (str (clojure.string/join " " unit-strs) " | " pct "%"))))

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
        is-attention-cell? (and (seq attention-coords) (= [col row] (first attention-coords)))
        show-contained? (and is-attention-cell? has-contained-unit? blink?)]
    (cond
      show-contained?
      (uc/blinking-contained-unit has-awake-airport? has-awake-carrier? has-awake-army?)

      (and is-attention-cell? has-awake-airport?)
      nil ;; Hide airport fighter on alternate blink frame

      :else
      (uc/normal-display-unit cell contents has-awake-airport? has-any-airport?))))

(defn group-cells-by-color
  "Groups map cells by their display color for batched rendering.
   Returns a map of [r g b] color to seq of {:col :row :cell} maps.
   blink-attention? and blink-completed? control flash states for attention cells and completed cities."
  [the-map attention-coords production blink-attention? blink-completed?]
  (let [cols (count the-map)
        rows (count (first the-map))
        attention-cell (first attention-coords)]
    (reduce
     (fn [acc [col row]]
       (let [cell (get-in the-map [col row])]
         (if (= :unexplored (:type cell))
           acc
           (let [color (config/color-of cell)
                 current [col row]
                 should-flash-black (= current attention-cell)
                 completed? (and (= (:type cell) :city)
                                 (not= :free (:city-status cell))
                                 (let [prod (production [col row])]
                                   (and (map? prod) (= (:remaining-rounds prod) 0))))
                 blink-white? (and completed? blink-completed?)
                 blink-black? (and should-flash-black blink-attention?)
                 final-color (cond blink-black? [0 0 0]
                                   blink-white? [255 255 255]
                                   :else color)]
             (update acc final-color conj {:col col :row row :cell cell})))))
     {}
     (for [col (range cols) row (range rows)] [col row]))))
