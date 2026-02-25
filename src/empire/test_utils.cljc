(ns empire.test-utils
  (:require [clojure.string :as str]
            [empire.atoms :as atoms]
            [empire.computer.land-objectives :as land-objectives]
            [empire.movement.pathfinding :as pathfinding]
            [empire.units.dispatcher :as dispatcher]))

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
  (let [pos (find-unit-pos @game-map-atom unit-spec)]
    (when (nil? pos)
      (throw (ex-info (str "Unit not found: " unit-spec) {:unit-spec unit-spec})))
    (swap! game-map-atom update-in (conj pos :contents) merge (apply hash-map kvs))))

(defn- matches-filters? [unit filters]
  (every? (fn [[k v]] (= v (get unit k))) filters))

(defn get-test-unit [game-map-atom unit-spec & {:as filters}]
  (let [c (first unit-spec)
        unit-type (get char->unit-type c)
        owner (if (Character/isUpperCase c) :player :computer)
        n (if (> (count unit-spec) 1)
            (Integer/parseInt (subs unit-spec 1))
            1)
        game-map @game-map-atom
        matches (for [row-idx (range (count game-map))
                      col-idx (range (count (nth game-map row-idx)))
                      :let [cell (get-in game-map [row-idx col-idx])
                            contents (:contents cell)]
                      :when (and contents
                                 (= unit-type (:type contents))
                                 (= owner (:owner contents))
                                 (matches-filters? contents filters))]
                  {:pos [row-idx col-idx] :unit contents})]
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
        game-map @game-map-atom
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
        game-map @game-map-atom
        matches (for [row-idx (range (count game-map))
                      col-idx (range (count (nth game-map row-idx)))
                      :let [cell (get-in game-map [row-idx col-idx])]
                      :when (= label (:label cell))]
                  {:pos [row-idx col-idx] :cell cell})]
    (nth matches (dec n) nil)))

(defn make-initial-test-map [rows cols value]
  (vec (repeat cols (vec (repeat rows value)))))

(defn reset-all-atoms! []
  (reset! atoms/random-seed nil)
  (reset! atoms/map-size [0 0])
  (reset! atoms/map-size-constants {})
  (reset! atoms/last-key nil)
  (reset! atoms/backtick-pressed false)
  (reset! atoms/map-screen-dimensions [0 0])
  (reset! atoms/text-area-dimensions [0 0 0 0])
  (reset! atoms/map-to-display :player-map)
  (reset! atoms/round-number 0)
  (reset! atoms/last-clicked-cell nil)
  (reset! atoms/text-font nil)
  (reset! atoms/production-char-font nil)
  (reset! atoms/production {})
  (reset! atoms/game-map nil)
  (reset! atoms/player-map {})
  (reset! atoms/cells-needing-attention [])
  (reset! atoms/player-items [])
  (reset! atoms/waiting-for-input false)
  (reset! atoms/attention-message "")
  (reset! atoms/turn-message "")
  (reset! atoms/turn-message-until 0)
  (reset! atoms/hover-message "")
  (reset! atoms/error-message "")
  (reset! atoms/error-until 0)
  (reset! atoms/production-status "")
  (reset! atoms/computer-map {})
  (reset! atoms/destination nil)
  (reset! atoms/paused false)
  (reset! atoms/game-over-check-enabled false)
  (reset! atoms/pause-requested false)
  (reset! atoms/computer-items [])
  (reset! atoms/computer-turn false)
  (reset! atoms/next-transport-id 1)
  (reset! atoms/next-country-id 1)
  (reset! atoms/next-unload-event-id 1)
  (reset! atoms/next-destroyer-id 1)
  (reset! atoms/next-carrier-id 1)
  (reset! atoms/next-escort-id 1)
  (reset! atoms/claimed-objectives #{})
  (reset! atoms/claimed-transport-targets #{})
  (reset! atoms/last-transport-city {})
  (reset! atoms/fighter-leg-records {})
  (reset! atoms/coast-walkers-produced {})
  (reset! atoms/patrol-boats-produced {})
  (reset! atoms/seen-coast #{})
  (reset! atoms/sea-lane-network {:nodes {} :segments {} :pos->node {} :pos->seg {}
                                   :next-node-id 1 :next-segment-id 1})
  (reset! atoms/distant-city-pairs nil)
  (reset! atoms/computer-event-log [])
  (reset! atoms/action-log [])
  (reset! atoms/player-movement-log [])
  (reset! atoms/load-menu-open false)
  (reset! atoms/load-menu-files [])
  (reset! atoms/load-menu-hovered nil)
  (pathfinding/clear-path-cache)
  (land-objectives/clear-continent-cache!))

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
