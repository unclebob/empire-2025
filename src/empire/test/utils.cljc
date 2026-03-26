(ns empire.test.utils
  (:require [clojure.string :as str]
            [empire.state.api :as sa]
            [empire.state.world :as world]
            [empire.state.computer :as computer-state]
            [empire.state.player :as player-state]
            [empire.state.ui :as ui-state]
            [empire.computer.land-objectives :as land-objectives]
            [empire.game-mechanics.movement.pathfinding :as pathfinding]
            [empire.game-mechanics.movement.pathfinding-bfs :as pathfinding-bfs]
            [empire.game-mechanics.visibility :as visibility]
            [empire.config.units.dispatcher :as dispatcher]
            [empire.computer.fighter.movement-impl :as fighter-movement-impl]
            [empire.computer.fighter.flight-decisions :as flight-decisions]
            [empire.computer.fighter.exploration :as fighter-exploration]
            [empire.computer.production.stats :as production-stats]
            [empire.computer.production.decisions :as production-decisions]
            [empire.computer.ship.carrier :as carrier]
            [empire.computer.early-game.theater :as theater]))

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
  (sa/write-state! :game-map world))

(defn set-test-cell!
  [pos cell]
  (sa/update-world! assoc-in pos cell))

(defn set-test-contents!
  [pos unit]
  (sa/update-world! assoc-in (conj pos :contents) unit))

(defn clear-test-contents!
  [pos]
  (sa/update-world! assoc-in (conj pos :contents) nil))

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

(defn mission-ctx []
  {:current-world read-test-world
   :update-game-map! update-test-world!
   :load-major-invasion-state #(read-test-state :major-invasion-state)})

(defn set-major-invasion-state!
  [state]
  (set-test-state! :major-invasion-state state))

(defn set-kamikazee-fighter!
  [pos attrs]
  (update-test-world! update-in (conj pos :contents)
                      merge
                      {:type :fighter
                       :owner :computer
                       :hits 1
                       :major-invasion true
                       :kamikazee true}
                      attrs))

(defn seed-airport-kamikazees!
  [city-pos total awake]
  (update-test-world! assoc-in (conj city-pos :fighter-count) total)
  (update-test-world! assoc-in (conj city-pos :awake-fighters) awake)
  (update-test-world! assoc-in (conj city-pos :kamikazee-fighter-count) total)
  (update-test-world! assoc-in (conj city-pos :awake-kamikazee-fighters) awake))

(defn- keyword-map-updater
  [map-source]
  (or (when (or (= map-source :game-map)
                (identical? map-source (game-map-atom)))
        update-test-world!)
      (when (or (= map-source :player-map)
                (identical? map-source (player-map-atom)))
        update-test-player-map!)
      (when (or (= map-source :computer-map)
                (identical? map-source (computer-map-atom)))
        update-test-computer-map!)))

(defn- update-map-atom!
  [map-source f & args]
  (if-let [updater (keyword-map-updater map-source)]
    (apply updater f args)
    (apply swap! map-source f args)))

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
    (set-test-world! world-with-country)
    (set-test-computer-map! world-with-country)))

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
  (reset! world/state world/defaults)
  (reset! computer-state/state computer-state/defaults)
  (reset! player-state/state player-state/defaults)
  (reset! ui-state/state ui-state/defaults)
  (sa/write-state! :game-over-check-enabled false)
  (sa/write-state! :integrity-check-enabled false)
  (pathfinding/clear-path-cache)
  (pathfinding-bfs/clear-bfs-caches)
  (land-objectives/clear-continent-cache!)
  (fighter-movement-impl/clear-refueling-cache!)
  (flight-decisions/clear-active-targets-cache!)
  (fighter-exploration/clear-unexplored-distance-cache!)
  (production-stats/clear-asset-cache!)
  (production-decisions/clear-produced-transport-cache!)
  (carrier/clear-carrier-caches!)
  (theater/clear-theater-caches!)
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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T12:02:57.263598-05:00", :module-hash "-175536797", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 12, :hash "371255735"} {:id "defn/read-test-state", :kind "defn", :line 14, :end-line 16, :hash "589015934"} {:id "defn/set-test-state!", :kind "defn", :line 18, :end-line 20, :hash "870665925"} {:id "defn/update-test-state!", :kind "defn", :line 22, :end-line 24, :hash "-1050730755"} {:id "defn/read-test-world", :kind "defn", :line 26, :end-line 28, :hash "-1610409745"} {:id "defn/game-map-atom", :kind "defn", :line 30, :end-line 32, :hash "-1480579774"} {:id "defn/player-map-atom", :kind "defn", :line 34, :end-line 36, :hash "176409169"} {:id "defn/computer-map-atom", :kind "defn", :line 38, :end-line 40, :hash "-2115588914"} {:id "defn/set-test-world!", :kind "defn", :line 42, :end-line 44, :hash "-1130383465"} {:id "defn/update-test-world!", :kind "defn", :line 46, :end-line 48, :hash "1015896768"} {:id "defn/set-test-player-map!", :kind "defn", :line 50, :end-line 52, :hash "1917677314"} {:id "defn/update-test-player-map!", :kind "defn", :line 54, :end-line 56, :hash "2047748625"} {:id "defn/set-test-computer-map!", :kind "defn", :line 58, :end-line 60, :hash "-1539947896"} {:id "defn/update-test-computer-map!", :kind "defn", :line 62, :end-line 64, :hash "1539668718"} {:id "defn-/update-map-atom!", :kind "defn-", :line 66, :end-line 75, :hash "-525332097"} {:id "defn-/map-value", :kind "defn-", :line 77, :end-line 85, :hash "-103685210"} {:id "defn-/make-unit", :kind "defn-", :line 87, :end-line 89, :hash "-1341488376"} {:id "def/char->unit-type", :kind "def", :line 91, :end-line 101, :hash "1375355051"} {:id "def/nil-chars", :kind "def", :line 103, :end-line 103, :hash "-1389181202"} {:id "def/special-chars", :kind "def", :line 105, :end-line 113, :hash "-251180346"} {:id "def/char->terrain", :kind "def", :line 115, :end-line 119, :hash "1464974908"} {:id "defn/char->cell", :kind "defn", :line 121, :end-line 129, :hash "-1970204313"} {:id "defn/build-test-map", :kind "defn", :line 131, :end-line 133, :hash "1624720329"} {:id "defn/visibility-mask", :kind "defn", :line 135, :end-line 136, :hash "1885659006"} {:id "defn/territory-mask", :kind "defn", :line 138, :end-line 147, :hash "-1873482580"} {:id "defn/build-territory-expected", :kind "defn", :line 149, :end-line 159, :hash "1165535004"} {:id "defn/build-sparse-test-map", :kind "defn", :line 161, :end-line 168, :hash "290351272"} {:id "defn-/find-unit-pos", :kind "defn-", :line 170, :end-line 185, :hash "-364024954"} {:id "defn/set-test-unit", :kind "defn", :line 187, :end-line 192, :hash "-1462174338"} {:id "defn-/matches-filters?", :kind "defn-", :line 194, :end-line 195, :hash "-1749912378"} {:id "defn/get-test-unit", :kind "defn", :line 197, :end-line 214, :hash "-656213044"} {:id "def/char->city-status", :kind "def", :line 216, :end-line 219, :hash "-1366028044"} {:id "defn-/parse-city-spec", :kind "defn-", :line 221, :end-line 226, :hash "767469184"} {:id "defn/get-test-city", :kind "defn", :line 228, :end-line 237, :hash "-557180583"} {:id "defn/get-test-cell", :kind "defn", :line 239, :end-line 250, :hash "-1969505989"} {:id "defn/make-initial-test-map", :kind "defn", :line 252, :end-line 253, :hash "-738326074"} {:id "defn/reset-all-atoms!", :kind "defn", :line 255, :end-line 264, :hash "1190818203"} {:id "defn/message-matches?", :kind "defn", :line 266, :end-line 277, :hash "-1753245316"}]}
;; clj-mutate-manifest-end
