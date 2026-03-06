(ns empire.test.utils
  (:require [clojure.string :as str]
            [empire.state.api :as sa]
            [empire.computer.land-objectives :as land-objectives]
            [empire.game-mechanics.movement.pathfinding :as pathfinding]
            [empire.game-mechanics.movement.pathfinding-bfs :as pathfinding-bfs]
            [empire.game-mechanics.movement.visibility :as visibility]
            [empire.config.units.dispatcher :as dispatcher]))

(defn read-test-state
  [k]
  (sa/read-state k))

(defn set-test-state!
  [k v]
  (sa/write-state! k v))

(defn update-test-state!
  [k f & args]
  (set-test-state! k (apply f (read-test-state k) args)))

(defn read-test-world
  []
  (sa/current-world))

(defn game-map-atom
  []
  :game-map)

(defn player-map-atom
  []
  :player-map)

(defn computer-map-atom
  []
  :computer-map)

(defn set-test-world!
  [world]
  (reset! (sa/world-atom) world))

(defn update-test-world!
  [f & args]
  (apply sa/update-world! f args))

(defn set-test-player-map!
  [player-map]
  (set-test-state! :player-map player-map))

(defn update-test-player-map!
  [f & args]
  (apply update-test-state! :player-map f args))

(defn set-test-computer-map!
  [computer-map]
  (set-test-state! :computer-map computer-map))

(defn update-test-computer-map!
  [f & args]
  (apply update-test-state! :computer-map f args))

(defn- update-map-atom!
  [map-source f & args]
  (cond
    (= map-source :game-map) (apply update-test-world! f args)
    (= map-source :player-map) (apply update-test-player-map! f args)
    (= map-source :computer-map) (apply update-test-computer-map! f args)
    (identical? map-source (game-map-atom)) (apply update-test-world! f args)
    (identical? map-source (player-map-atom)) (apply update-test-player-map! f args)
    (identical? map-source (computer-map-atom)) (apply update-test-computer-map! f args)
    :else (apply swap! map-source f args)))

(defn- map-value
  [game-map-source]
  (cond
    (= game-map-source :game-map) (read-test-state :game-map)
    (= game-map-source :player-map) (read-test-state :player-map)
    (= game-map-source :computer-map) (read-test-state :computer-map)
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

(defn get-test-unit [game-map-atom unit-spec & {:as filters}]
  (let [c (first unit-spec)
        unit-type (get char->unit-type c)
        owner (if (Character/isUpperCase c) :player :computer)
        n (if (> (count unit-spec) 1)
            (Integer/parseInt (subs unit-spec 1))
            1)
        game-map (map-value game-map-atom)
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

(defn reset-all-atoms! []
  (set-test-state! :random-seed nil)
  (set-test-state! :map-size [0 0])
  (set-test-state! :map-size-constants {})
  (set-test-state! :last-key nil)
  (set-test-state! :backtick-pressed false)
  (set-test-state! :map-screen-dimensions [0 0])
  (set-test-state! :text-area-dimensions [0 0 0 0])
  (set-test-state! :map-to-display :player-map)
  (set-test-state! :round-number 0)
  (set-test-state! :last-clicked-cell nil)
  (set-test-state! :text-font nil)
  (set-test-state! :production-char-font nil)
  (set-test-state! :production {})
  (set-test-world! nil)
  (set-test-state! :player-map {})
  (set-test-state! :cells-needing-attention [])
  (set-test-state! :player-items [])
  (set-test-state! :waiting-for-input false)
  (set-test-state! :attention-message "")
  (set-test-state! :turn-message "")
  (set-test-state! :turn-message-until 0)
  (set-test-state! :hover-message "")
  (set-test-state! :error-message "")
  (set-test-state! :error-until 0)
  (set-test-state! :production-status "")
  (set-test-state! :computer-map {})
  (set-test-state! :destination nil)
  (set-test-state! :paused false)
  (set-test-state! :game-over-check-enabled false)
  (set-test-state! :pause-requested false)
  (set-test-state! :computer-items [])
  (set-test-state! :computer-turn false)
  (set-test-state! :next-transport-id 1)
  (set-test-state! :next-country-id 1)
  (set-test-state! :continent-groups {})
  (set-test-state! :next-unload-event-id 1)
  (set-test-state! :next-destroyer-id 1)
  (set-test-state! :next-carrier-id 1)
  (set-test-state! :next-escort-id 1)
  (set-test-state! :claimed-objectives #{})
  (set-test-state! :claimed-transport-targets #{})
  (set-test-state! :claimed-patrol-targets #{})
  (set-test-state! :last-transport-city {})
  (set-test-state! :fighter-leg-records {})
  (set-test-state! :computer-city-positions #{})
  (set-test-state! :computer-carrier-positions #{})
  (set-test-state! :country-stats {})
  (set-test-state! :coastal-cells-by-country {})
  (set-test-state! :coast-walkers-produced {})
  (set-test-state! :patrol-boats-produced {})
  (set-test-state! :seen-coast #{})
  (set-test-state! :land-ho-targets [])
  (set-test-state! :major-invasion-state {:active? false
                                          :detection-points #{}
                                          :target-land-set #{}
                                          :started-round nil})
  (set-test-state! :transport-fully-loaded? false)
  (set-test-state! :early-patrol-boat-produced? false)
  (set-test-state! :early-satellite-produced? false)
  (set-test-state! :distant-city-pairs nil)
  (set-test-state! :lake-max-cells 0)
  (set-test-state! :known-lake-cells #{})
  (set-test-state! :computer-event-log [])
  (set-test-state! :action-log [])
  (set-test-state! :player-movement-log [])
  (set-test-state! :load-menu-open false)
  (set-test-state! :load-menu-files [])
  (set-test-state! :load-menu-hovered nil)
  (pathfinding/clear-path-cache)
  (pathfinding-bfs/clear-bfs-caches)
  (land-objectives/clear-continent-cache!)
  (visibility/drain-detections!))

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
