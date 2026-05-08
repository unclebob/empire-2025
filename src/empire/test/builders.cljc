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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-05-07T16:14:50.581552-05:00", :module-hash "434491723", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 5, :hash "-1295350077"} {:id "defn-/keyword-map-updater", :kind "defn-", :line 7, :end-line 17, :hash "308705907"} {:id "defn-/update-map-atom!", :kind "defn-", :line 19, :end-line 23, :hash "1791544403"} {:id "defn-/map-value", :kind "defn-", :line 25, :end-line 33, :hash "-487645973"} {:id "defn-/make-unit", :kind "defn-", :line 35, :end-line 37, :hash "-1341488376"} {:id "def/char->unit-type", :kind "def", :line 39, :end-line 49, :hash "1375355051"} {:id "def/nil-chars", :kind "def", :line 51, :end-line 51, :hash "-1389181202"} {:id "def/special-chars", :kind "def", :line 53, :end-line 61, :hash "-251180346"} {:id "def/char->terrain", :kind "def", :line 63, :end-line 67, :hash "1464974908"} {:id "defn/char->cell", :kind "defn", :line 69, :end-line 77, :hash "-1970204313"} {:id "defn/build-test-map", :kind "defn", :line 79, :end-line 81, :hash "1624720329"} {:id "defn/set-test-world-with-country!", :kind "defn", :line 83, :end-line 98, :hash "2063757531"} {:id "defn/visibility-mask", :kind "defn", :line 100, :end-line 101, :hash "1885659006"} {:id "defn/territory-mask", :kind "defn", :line 103, :end-line 112, :hash "-1873482580"} {:id "defn/build-territory-expected", :kind "defn", :line 114, :end-line 124, :hash "1165535004"} {:id "defn/build-sparse-test-map", :kind "defn", :line 126, :end-line 133, :hash "290351272"} {:id "defn-/find-unit-pos", :kind "defn-", :line 135, :end-line 150, :hash "-364024954"} {:id "defn/set-test-unit", :kind "defn", :line 152, :end-line 157, :hash "-1462174338"} {:id "defn-/matches-filters?", :kind "defn-", :line 159, :end-line 160, :hash "-1749912378"} {:id "defn-/parsed-unit-spec", :kind "defn-", :line 162, :end-line 169, :hash "871089070"} {:id "defn-/content-unit-matches", :kind "defn-", :line 171, :end-line 181, :hash "158870284"} {:id "defn-/city-owner", :kind "defn-", :line 183, :end-line 186, :hash "-1911008758"} {:id "defn-/airport-fighter", :kind "defn-", :line 188, :end-line 194, :hash "1170626959"} {:id "defn-/airport-fighter-matches", :kind "defn-", :line 196, :end-line 207, :hash "2095134033"} {:id "defn/get-test-unit", :kind "defn", :line 209, :end-line 215, :hash "57616484"} {:id "def/char->city-status", :kind "def", :line 217, :end-line 220, :hash "-1366028044"} {:id "defn-/parse-city-spec", :kind "defn-", :line 222, :end-line 227, :hash "767469184"} {:id "defn/get-test-city", :kind "defn", :line 229, :end-line 238, :hash "-557180583"} {:id "defn/get-test-cell", :kind "defn", :line 240, :end-line 251, :hash "-1969505989"} {:id "defn/make-initial-test-map", :kind "defn", :line 253, :end-line 254, :hash "-738326074"} {:id "defn/message-matches?", :kind "defn", :line 256, :end-line 267, :hash "-1753245316"}]}
;; clj-mutate-manifest-end
