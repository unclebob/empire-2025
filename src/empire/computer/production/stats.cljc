;; mutation-tested: 2026-03-02
(ns empire.computer.production.stats
  (:require [empire.application.runtime :as app-runtime]
            [empire.movement.map-utils :as map-utils]))

(def ^:private state-ctx
  (delay (app-runtime/default-state-ctx)))

(defn- current-world
  []
  ((:load-world @state-ctx)))

(defn- read-runtime-state
  [k]
  ((:read-runtime-state @state-ctx) k))

(defn- write-runtime-state!
  [k v]
  ((:write-runtime-state! @state-ctx) k v))

(defn- get-neighbors [pos]
  (map-utils/get-matching-neighbors pos (current-world) map-utils/neighbor-offsets some?))

(defn city-is-coastal? [city-pos]
  (some (fn [neighbor]
          (= :sea (:type (get-in (current-world) neighbor))))
        (get-neighbors city-pos)))

(defn- coastal? [game-map pos]
  (some (fn [n] (= :sea (:type (get-in game-map n))))
        (map-utils/get-matching-neighbors pos game-map map-utils/neighbor-offsets some?)))

(defn- update-country [acc cid k f]
  (update-in acc [cid k] (fnil f 0)))

(defn- land-or-city? [cell-type]
  (contains? #{:land :city} cell-type))

(defn- unoccupied-coastal-land? [cell-type cell]
  (and (= :land cell-type) (nil? (:contents cell))))

(defn- unexplored-cell? [comp-map i j]
  (nil? (get-in comp-map [i j])))

(defn- coastal-computer-city? [cell-type cell]
  (and (= :city cell-type) (= :computer (:city-status cell))))

(defn- computer-unit-with-country? [unit]
  (and unit (= :computer (:owner unit)) (:country-id unit)))

(defn- accumulate-coastal-terrain [acc cid comp-map i j cell-type cell]
  (cond-> (update-country acc cid :coastal-cell-count inc)
    (unoccupied-coastal-land? cell-type cell)
    (assoc-in [cid :has-unoccupied-coastal-cells?] true)
    (unexplored-cell? comp-map i j)
    (assoc-in [cid :has-unexplored-coastal?] true)
    (coastal-computer-city? cell-type cell)
    (update-in [cid :coastal-city-positions] (fnil conj #{}) [i j])))

(defn- scan-cell-terrain [acc game-map comp-map i j cell]
  (let [cid (:country-id cell)
        cell-type (:type cell)]
    (if (and cid (land-or-city? cell-type) (coastal? game-map [i j]))
      (accumulate-coastal-terrain acc cid comp-map i j cell-type cell)
      acc)))

(defn- accumulate-army [acc ucid cell-type is-coastal]
  (cond-> (update-country acc ucid :army-count inc)
    (and is-coastal (= :land cell-type))
    (assoc-in [ucid :has-coastal-army?] true)
    (land-or-city? cell-type)
    (update-country ucid :land-army-count inc)))

(defn- accumulate-transport [acc ucid unit]
  (-> (update-country acc ucid :army-count #(+ % (get unit :army-count 0)))
      (update-in [ucid :transports] (fnil conj []) unit)))

(defn- coastal-land-or-city? [game-map cell-type pos]
  (and (land-or-city? cell-type) (coastal? game-map pos)))

(defn- scan-cell-unit [acc game-map i j cell]
  (let [unit (:contents cell)
        ucid (:country-id unit)]
    (if-not (computer-unit-with-country? unit)
      acc
      (let [cell-type (:type cell)
            is-coastal (coastal-land-or-city? game-map cell-type [i j])]
        (case (:type unit)
          :army (accumulate-army acc ucid cell-type is-coastal)
          :transport (accumulate-transport acc ucid unit)
          :patrol-boat (update-country acc ucid :patrol-boat-count inc)
          acc)))))

(defn- scan-cell [acc game-map comp-map i j]
  (let [cell (get-in game-map [i j])]
    (-> acc
        (scan-cell-terrain game-map comp-map i j cell)
        (scan-cell-unit game-map i j cell))))

(defn- derive-stats [raw]
  (reduce-kv
    (fn [m cid stats]
      (let [coastal-cells (get stats :coastal-cell-count 0)
            land-armies (get stats :land-army-count 0)
            transports (get stats :transports [])
            all-full-or-unloading (every? (fn [t]
                                            (or (>= (get t :army-count 0) 6)
                                                (= :unloading (:transport-mission t))))
                                          transports)
            has-unadopted (boolean (some #(nil? (:escort-destroyer-id %)) transports))]
        (assoc m cid
               (-> (dissoc stats :transports :has-unexplored-coastal? :has-coastal-army?)
                   (assoc :has-waiting-armies?
                          (and (:has-coastal-army? stats)
                               (or (empty? transports) all-full-or-unloading)))
                   (assoc :has-unadopted-transport? has-unadopted)
                   (assoc :coastal-explored? (not (:has-unexplored-coastal? stats)))
                   (assoc :army-limit-reached?
                          (and (pos? coastal-cells)
                               (>= land-armies (* 2/3 coastal-cells))))))))
    {} raw))

(defn rebuild-country-stats! []
  (let [game-map (current-world)
        comp-map (read-runtime-state :computer-map)
        rows (count (first game-map))
        cols (count game-map)
        raw (reduce (fn [acc i]
                      (reduce (fn [acc j]
                                (scan-cell acc game-map comp-map i j))
                              acc (range rows)))
                    {} (range cols))]
    (write-runtime-state! :country-stats (derive-stats raw))))

(defn count-computer-units []
  (let [game-map (current-world)
        units (for [i (range (count game-map))
                    j (range (count (first game-map)))
                    :let [unit (:contents (get-in game-map [i j]))]
                    :when (and unit (= :computer (:owner unit)))]
                (:type unit))]
    (frequencies units)))

(defn count-computer-cities []
  (let [game-map (current-world)]
    (count (for [i (range (count game-map))
                 j (range (count (first game-map)))
                 :let [cell (get-in game-map [i j])]
               :when (and (= :city (:type cell))
                          (= :computer (:city-status cell)))]
             [i j]))))

(defn count-country-armies [country-id]
  (get-in (or (read-runtime-state :country-stats) {}) [country-id :army-count] 0))

(defn count-country-coastal-cells [country-id]
  (get-in (or (read-runtime-state :country-stats) {}) [country-id :coastal-cell-count] 0))

(defn country-coastal-cells-explored? [country-id]
  (get-in (or (read-runtime-state :country-stats) {}) [country-id :coastal-explored?] true))

(defn country-has-waiting-armies? [country-id]
  (boolean (get-in (or (read-runtime-state :country-stats) {})
                   [country-id :has-waiting-armies?])))

(defn count-all-computer-fighters []
  (let [game-map (current-world)]
    (count (for [i (range (count game-map))
                 j (range (count (first game-map)))
                 :let [unit (:contents (get-in game-map [i j]))]
               :when (and unit
                          (= :computer (:owner unit))
                          (= :fighter (:type unit)))]
             true))))

(defn count-country-patrol-boats [country-id]
  (get-in (or (read-runtime-state :country-stats) {}) [country-id :patrol-boat-count] 0))

(defn country-has-unadopted-transport? [country-id]
  (boolean (get-in (or (read-runtime-state :country-stats) {})
                   [country-id :has-unadopted-transport?])))

(defn has-unoccupied-coastal-cells? [country-id]
  (boolean (get-in (or (read-runtime-state :country-stats) {})
                   [country-id :has-unoccupied-coastal-cells?])))

(defn country-has-other-coastal-city? [city-pos country-id]
  (let [positions (get-in (or (read-runtime-state :country-stats) {})
                          [country-id :coastal-city-positions] #{})]
    (some #(not= city-pos %) positions)))

(defn country-army-limit-reached? [country-id]
  (boolean (get-in (or (read-runtime-state :country-stats) {})
                   [country-id :army-limit-reached?])))
