(ns empire.test.builders
  (:require [clojure.string :as str]
            [empire.config.units.config :as unit-config]
            [empire.config.units.dispatcher :as dispatcher]
            [empire.test.state :as state]))

(defn- keyword-map-updater
  [map-source]
  (or (when (or (= map-source :game-map)
                (identical? map-source (state/game-map-atom)))
        state/update-test-world!)
      (when (or (= map-source :player-map)
                (identical? map-source (state/player-map-atom)))
        state/update-test-player-map!)
      (when (or (= map-source :computer-map)
                (identical? map-source (state/computer-map-atom)))
        state/update-test-computer-map!)))

(defn- update-map-atom!
  [map-source f & args]
  (if-let [updater (keyword-map-updater map-source)]
    (apply updater f args)
    (apply swap! map-source f args)))

(defn- map-value
  [game-map-source]
  (cond
    (= game-map-source :game-map) (state/read-test-state :game-map)
    (= game-map-source :player-map) (state/read-test-state :player-map)
    (= game-map-source :computer-map) (state/read-test-state :computer-map)
    (vector? game-map-source)
    game-map-source
    :else @game-map-source))

(defn- make-unit [unit-type owner]
  (merge {:type unit-type :owner owner :hits (dispatcher/hits unit-type)}
         (dispatcher/initial-state unit-type)))

(def ^:private char->unit-type
  {\A :army      \a :army
   \T :transport \t :transport
   \D :destroyer \d :destroyer
   \P :patrol-boat \p :patrol-boat
   \C :carrier   \c :carrier
   \B :battleship \b :battleship
   \S :submarine \s :submarine
   \F :fighter   \f :fighter
   \J :fighter   \j :fighter
   \V :satellite \v :satellite})

(def ^:private nil-chars #{\space \. \-})

(def ^:private special-chars
  {\~ {:type :sea}
   \# {:type :land}
   \= {:type :sea :label "="}
   \% {:type :land :label "%"}
   \+ {:type :city :city-status :free}
   \O {:type :city :city-status :player}
   \X {:type :city :city-status :computer}
   \* {:type :land :waypoint true}})

(def ^:private char->terrain
  {\A :land \a :land \F :land \f :land \V :land \v :land
   \T :sea  \t :sea  \D :sea  \d :sea  \P :sea  \p :sea
   \C :sea  \c :sea  \B :sea  \b :sea  \S :sea  \s :sea
   \J :sea  \j :sea})

(defn char->cell [c]
  (cond
    (contains? nil-chars c) nil
    (contains? special-chars c) (get special-chars c)
    (contains? char->unit-type c)
    (let [owner (if (Character/isUpperCase c) :player :computer)]
      {:type (get char->terrain c)
       :contents (make-unit (get char->unit-type c) owner)})
    :else (throw (ex-info (str "Unknown map char: " c) {:char c}))))

(defn build-test-map [strings]
  (let [rows (mapv (fn [row-str] (mapv char->cell row-str)) strings)]
    (apply mapv vector rows)))

(defn set-test-world-with-country!
  [strings country-id]
  (let [world (build-test-map strings)
        width (count world)
        height (count (first world))
        world-with-country
        (reduce (fn [m [col row]]
                  (if (= :land (get-in m [col row :type]))
                    (assoc-in m [col row :country-id] country-id)
                    m))
                world
                (for [col (range width)
                      row (range height)]
                  [col row]))]
    (state/set-test-world! world-with-country)
    (state/set-test-computer-map! world-with-country)))

(defn visibility-mask [grid]
  (mapv (fn [col] (mapv some? col)) grid))

(defn territory-mask [grid]
  (mapv (fn [col]
          (mapv (fn [cell]
                  (cond
                    (nil? cell) nil
                    (= :sea (:type cell)) :sea
                    (:country-id cell) (:country-id cell)
                    :else nil))
                col))
        grid))

(defn build-territory-expected [strings]
  (let [rows (mapv (fn [row-str]
                     (mapv (fn [c]
                             (cond
                               (= c \~) :sea
                               (= c \.) nil
                               (Character/isDigit c) (Character/digit c 10)
                               :else nil))
                           row-str))
                   strings)]
    (apply mapv vector rows)))

(defn build-sparse-test-map
  "Builds a rows x cols map of unexplored (nil) cells, then overlays specific cells.
   overlays is a map of [row col] -> character."
  [rows cols overlays]
  (let [base (vec (repeat cols (vec (repeat rows nil))))]
    (reduce (fn [m [[r c] ch]]
              (assoc-in m [c r] (char->cell ch)))
            base overlays)))

(defn- find-unit-pos [game-map unit-spec]
  (let [c (first unit-spec)
        unit-type (get char->unit-type c)
        owner (if (Character/isUpperCase c) :player :computer)
        n (if (> (count unit-spec) 1)
            (Integer/parseInt (subs unit-spec 1))
            1)
        positions (for [row-idx (range (count game-map))
                        col-idx (range (count (nth game-map row-idx)))
                        :let [cell (get-in game-map [row-idx col-idx])
                              contents (:contents cell)]
                        :when (and contents
                                   (= unit-type (:type contents))
                                   (= owner (:owner contents)))]
                    [row-idx col-idx])]
    (nth positions (dec n) nil)))

(defn set-test-unit [game-map-atom unit-spec & kvs]
  (let [game-map (map-value game-map-atom)
        pos (find-unit-pos game-map unit-spec)]
    (when (nil? pos)
      (throw (ex-info (str "Unit not found: " unit-spec) {:unit-spec unit-spec})))
    (update-map-atom! game-map-atom update-in (conj pos :contents) merge (apply hash-map kvs))))

(defn- matches-filters? [unit filters]
  (every? (fn [[k v]] (= v (get unit k))) filters))

(defn- parsed-unit-spec
  [unit-spec]
  (let [c (first unit-spec)]
    {:unit-type (get char->unit-type c)
     :owner (if (Character/isUpperCase c) :player :computer)
     :n (if (> (count unit-spec) 1)
          (Integer/parseInt (subs unit-spec 1))
          1)}))

(defn- content-unit-matches
  [game-map unit-type owner filters]
  (for [row-idx (range (count game-map))
        col-idx (range (count (nth game-map row-idx)))
        :let [cell (get-in game-map [row-idx col-idx])
              contents (:contents cell)]
        :when (and contents
                   (= unit-type (:type contents))
                   (= owner (:owner contents))
                   (matches-filters? contents filters))]
    {:pos [row-idx col-idx] :unit contents}))

(defn- city-owner
  [cell]
  ({:player :player
    :computer :computer} (:city-status cell)))

(defn- airport-fighter
  [owner]
  {:type :fighter
   :mode :awake
   :owner owner
   :fuel unit-config/fighter-fuel
   :from-airport true})

(defn- airport-fighter-matches
  [game-map unit-type owner filters]
  (when (= unit-type :fighter)
    (for [row-idx (range (count game-map))
          col-idx (range (count (nth game-map row-idx)))
          :let [cell (get-in game-map [row-idx col-idx])
                airport-unit (airport-fighter owner)]
          :when (and (= :city (:type cell))
                     (= owner (city-owner cell))
                     (pos? (:fighter-count cell 0))
                     (matches-filters? airport-unit filters))]
      {:pos [row-idx col-idx] :unit airport-unit})))

(defn get-test-unit [game-map-atom unit-spec & {:as filters}]
  (let [{:keys [unit-type owner n]} (parsed-unit-spec unit-spec)
        game-map (map-value game-map-atom)
        contents-matches (content-unit-matches game-map unit-type owner filters)
        airport-matches (airport-fighter-matches game-map unit-type owner filters)
        matches (concat contents-matches airport-matches)]
    (nth matches (dec n) nil)))

(def ^:private char->city-status
  {\O :player
   \X :computer
   \+ :free})

(defn- parse-city-spec [city-spec]
  (let [c (first city-spec)
        n (if (> (count city-spec) 1)
            (Integer/parseInt (subs city-spec 1))
            1)]
    [(get char->city-status c) n]))

(defn get-test-city [game-map-atom city-spec]
  (let [[city-status n] (parse-city-spec city-spec)
        game-map (map-value game-map-atom)
        matches (for [row-idx (range (count game-map))
                      col-idx (range (count (nth game-map row-idx)))
                      :let [cell (get-in game-map [row-idx col-idx])]
                      :when (and (= :city (:type cell))
                                 (= city-status (:city-status cell)))]
                  {:pos [row-idx col-idx] :cell cell})]
    (nth matches (dec n) nil)))

(defn get-test-cell [game-map-atom cell-spec]
  (let [label (str (first cell-spec))
        n (if (> (count cell-spec) 1)
            (Integer/parseInt (subs cell-spec 1))
            1)
        game-map (map-value game-map-atom)
        matches (for [row-idx (range (count game-map))
                      col-idx (range (count (nth game-map row-idx)))
                      :let [cell (get-in game-map [row-idx col-idx])]
                      :when (= label (:label cell))]
                  {:pos [row-idx col-idx] :cell cell})]
    (nth matches (dec n) nil)))

(defn make-initial-test-map [rows cols value]
  (vec (repeat cols (vec (repeat rows value)))))

(defn message-matches?
  "Checks if a message template matches an actual message string.
   If the template contains format placeholders (%s, %d), converts
   to a regex pattern. Otherwise does a plain substring check."
  [template actual]
  (if (or (str/includes? template "%s") (str/includes? template "%d"))
    (let [escaped (str/replace template #"[.*+?^${}()|\\@\[\]]" "\\\\$0")
          pattern (-> escaped
                      (str/replace "%s" ".*")
                      (str/replace "%d" "\\d+"))]
      (some? (re-find (re-pattern pattern) actual)))
    (str/includes? actual template)))
