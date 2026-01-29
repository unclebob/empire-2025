(ns empire.test-utils
  (:require [empire.atoms :as atoms]
            [empire.pathfinding :as pathfinding]
            [empire.units.dispatcher :as dispatcher]))

(defn- make-unit [unit-type owner]
  {:type unit-type :owner owner :hits (dispatcher/hits unit-type)})

(defn- char->cell [c]
  (case c
    \~ {:type :sea}
    \# {:type :land}
    (\. \-) nil
    \+ {:type :city :city-status :free}
    \O {:type :city :city-status :player}
    \X {:type :city :city-status :computer}
    \* {:type :land :waypoint true}
    ;; Player units (uppercase)
    \A {:type :land :contents (make-unit :army :player)}
    \T {:type :sea :contents (make-unit :transport :player)}
    \D {:type :sea :contents (make-unit :destroyer :player)}
    \P {:type :sea :contents (make-unit :patrol-boat :player)}
    \C {:type :sea :contents (make-unit :carrier :player)}
    \B {:type :sea :contents (make-unit :battleship :player)}
    \S {:type :sea :contents (make-unit :submarine :player)}
    \F {:type :land :contents (make-unit :fighter :player)}
    \J {:type :sea :contents (make-unit :fighter :player)}
    \V {:type :land :contents (make-unit :satellite :player)}
    ;; Enemy units (lowercase)
    \a {:type :land :contents (make-unit :army :computer)}
    \t {:type :sea :contents (make-unit :transport :computer)}
    \d {:type :sea :contents (make-unit :destroyer :computer)}
    \p {:type :sea :contents (make-unit :patrol-boat :computer)}
    \c {:type :sea :contents (make-unit :carrier :computer)}
    \b {:type :sea :contents (make-unit :battleship :computer)}
    \s {:type :sea :contents (make-unit :submarine :computer)}
    \f {:type :land :contents (make-unit :fighter :computer)}
    \j {:type :sea :contents (make-unit :fighter :computer)}
    \v {:type :land :contents (make-unit :satellite :computer)}
    (throw (ex-info (str "Unknown map char: " c) {:char c}))))

(defn build-test-map [strings]
  (mapv (fn [row-str]
          (mapv char->cell row-str))
        strings))

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

(defn make-initial-test-map [rows cols value]
  (vec (repeat rows (vec (repeat cols value)))))

(defn reset-all-atoms! []
  (reset! atoms/map-size [0 0])
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
  (reset! atoms/message "")
  (reset! atoms/line2-message "")
  (reset! atoms/confirmation-until 0)
  (reset! atoms/hover-message "")
  (reset! atoms/line3-message "")
  (reset! atoms/line3-until 0)
  (reset! atoms/computer-map {})
  (reset! atoms/destination nil)
  (reset! atoms/paused false)
  (reset! atoms/pause-requested false)
  (reset! atoms/computer-items [])
  (reset! atoms/computer-turn false)
  (reset! atoms/next-transport-id 1)
  (reset! atoms/reserved-beaches {})
  (reset! atoms/used-unloading-beaches #{})
  (reset! atoms/beach-army-orders {})
  (reset! atoms/claimed-objectives #{})
  (reset! atoms/claimed-transport-targets #{})
  (pathfinding/clear-path-cache))
