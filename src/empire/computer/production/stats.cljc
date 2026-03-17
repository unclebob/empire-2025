(ns empire.computer.production.stats
  (:require [empire.state.api :as sa]
            [empire.computer.core :as core]))

(defn- get-neighbors [pos]
  (filter #(some? (get-in (sa/current-world) %))
          (core/get-neighbors pos)))

(defn city-is-coastal? [city-pos]
  (let [computer-map (sa/read-state :computer-map)]
    (some (fn [neighbor]
            (= :sea (:type (get-in computer-map neighbor))))
          (get-neighbors city-pos))))

(defn- coastal? [visible-map pos]
  (some (fn [n] (= :sea (:type (get-in visible-map n))))
        (filter #(some? (get-in visible-map %))
                (core/get-neighbors pos))))

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

(defn- scan-cell-terrain [acc comp-map i j]
  (let [cell (get-in comp-map [i j])
        cid (:country-id cell)
        cell-type (:type cell)]
    (if (and cell cid (land-or-city? cell-type) (coastal? comp-map [i j]))
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

(defn- coastal-land-or-city? [comp-map cell-type pos]
  (and (land-or-city? cell-type) (coastal? comp-map pos)))

(defn- scan-cell-unit [acc world-cell comp-map i j]
  (let [unit (:contents world-cell)
        ucid (:country-id unit)]
    (if-not (computer-unit-with-country? unit)
      acc
      (let [cell-type (or (:type (get-in comp-map [i j]))
                          (:type world-cell))
            is-coastal (coastal-land-or-city? comp-map cell-type [i j])]
        (case (:type unit)
          :army (accumulate-army acc ucid cell-type is-coastal)
          :transport (accumulate-transport acc ucid unit)
          :patrol-boat (update-country acc ucid :patrol-boat-count inc)
          acc)))))

(defn- scan-cell [acc game-map comp-map i j]
  (let [world-cell (get-in game-map [i j])]
    (-> acc
        (scan-cell-terrain comp-map i j)
        (scan-cell-unit world-cell comp-map i j))))

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
  (let [game-map (sa/current-world)
        comp-map (sa/read-state :computer-map)
        rows (count (first game-map))
        cols (count game-map)
        raw (reduce (fn [acc i]
                      (reduce (fn [acc j]
                                (scan-cell acc game-map comp-map i j))
                              acc (range rows)))
                    {} (range cols))]
    (sa/write-state! :country-stats (derive-stats raw))))

(defn count-computer-units []
  (let [game-map (sa/current-world)
        units (for [i (range (count game-map))
                    j (range (count (first game-map)))
                    :let [unit (:contents (get-in game-map [i j]))]
                    :when (and unit (= :computer (:owner unit)))]
                (:type unit))]
    (frequencies units)))

(defn count-computer-cities []
  (let [game-map (sa/current-world)]
    (count (for [i (range (count game-map))
                 j (range (count (first game-map)))
                 :let [cell (get-in game-map [i j])]
                 :when (and (= :city (:type cell))
                            (= :computer (:city-status cell)))]
             [i j]))))

(defn count-country-armies [country-id]
  (get-in (or (sa/read-state :country-stats) {}) [country-id :army-count] 0))

(defn count-country-coastal-cells [country-id]
  (get-in (or (sa/read-state :country-stats) {}) [country-id :coastal-cell-count] 0))

(defn country-coastal-cells-explored? [country-id]
  (get-in (or (sa/read-state :country-stats) {}) [country-id :coastal-explored?] true))

(defn country-has-waiting-armies? [country-id]
  (boolean (get-in (or (sa/read-state :country-stats) {})
                   [country-id :has-waiting-armies?])))

(defn count-all-computer-fighters []
  (let [game-map (sa/current-world)]
    (count (for [i (range (count game-map))
                 j (range (count (first game-map)))
                 :let [unit (:contents (get-in game-map [i j]))]
                 :when (and unit
                            (= :computer (:owner unit))
                            (= :fighter (:type unit)))]
             true))))

(defn count-country-patrol-boats [country-id]
  (get-in (or (sa/read-state :country-stats) {}) [country-id :patrol-boat-count] 0))

(defn country-has-unadopted-transport? [country-id]
  (boolean (get-in (or (sa/read-state :country-stats) {})
                   [country-id :has-unadopted-transport?])))

(defn has-unoccupied-coastal-cells? [country-id]
  (boolean (get-in (or (sa/read-state :country-stats) {})
                   [country-id :has-unoccupied-coastal-cells?])))

(defn country-has-other-coastal-city? [city-pos country-id]
  (let [positions (get-in (or (sa/read-state :country-stats) {})
                          [country-id :coastal-city-positions] #{})]
    (some #(not= city-pos %) positions)))

(defn country-army-limit-reached? [country-id]
  (boolean (get-in (or (sa/read-state :country-stats) {})
                   [country-id :army-limit-reached?])))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T11:58:11.020195-05:00", :module-hash "-411811203", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 3, :hash "20063030"} {:id "defn-/get-neighbors", :kind "defn-", :line 5, :end-line 7, :hash "-964540968"} {:id "defn/city-is-coastal?", :kind "defn", :line 9, :end-line 12, :hash "1330833581"} {:id "defn-/coastal?", :kind "defn-", :line 14, :end-line 17, :hash "1982172515"} {:id "defn-/update-country", :kind "defn-", :line 19, :end-line 20, :hash "1264170932"} {:id "defn-/land-or-city?", :kind "defn-", :line 22, :end-line 23, :hash "938934598"} {:id "defn-/unoccupied-coastal-land?", :kind "defn-", :line 25, :end-line 26, :hash "-1060931663"} {:id "defn-/unexplored-cell?", :kind "defn-", :line 28, :end-line 29, :hash "-1311494332"} {:id "defn-/coastal-computer-city?", :kind "defn-", :line 31, :end-line 32, :hash "-184583193"} {:id "defn-/computer-unit-with-country?", :kind "defn-", :line 34, :end-line 35, :hash "-1256622862"} {:id "defn-/accumulate-coastal-terrain", :kind "defn-", :line 37, :end-line 44, :hash "-194087817"} {:id "defn-/scan-cell-terrain", :kind "defn-", :line 46, :end-line 51, :hash "960362527"} {:id "defn-/accumulate-army", :kind "defn-", :line 53, :end-line 58, :hash "1494818776"} {:id "defn-/accumulate-transport", :kind "defn-", :line 60, :end-line 62, :hash "-916533110"} {:id "defn-/coastal-land-or-city?", :kind "defn-", :line 64, :end-line 65, :hash "647976474"} {:id "defn-/scan-cell-unit", :kind "defn-", :line 67, :end-line 78, :hash "-1864733647"} {:id "defn-/scan-cell", :kind "defn-", :line 80, :end-line 84, :hash "901377044"} {:id "defn-/derive-stats", :kind "defn-", :line 86, :end-line 107, :hash "1750711129"} {:id "defn/rebuild-country-stats!", :kind "defn", :line 109, :end-line 119, :hash "1789761365"} {:id "defn/count-computer-units", :kind "defn", :line 121, :end-line 128, :hash "1222401108"} {:id "defn/count-computer-cities", :kind "defn", :line 130, :end-line 137, :hash "761864797"} {:id "defn/count-country-armies", :kind "defn", :line 139, :end-line 140, :hash "-1218457171"} {:id "defn/count-country-coastal-cells", :kind "defn", :line 142, :end-line 143, :hash "1857517052"} {:id "defn/country-coastal-cells-explored?", :kind "defn", :line 145, :end-line 146, :hash "-1701809254"} {:id "defn/country-has-waiting-armies?", :kind "defn", :line 148, :end-line 150, :hash "376173458"} {:id "defn/count-all-computer-fighters", :kind "defn", :line 152, :end-line 160, :hash "330658618"} {:id "defn/count-country-patrol-boats", :kind "defn", :line 162, :end-line 163, :hash "1218129403"} {:id "defn/country-has-unadopted-transport?", :kind "defn", :line 165, :end-line 167, :hash "-1721744299"} {:id "defn/has-unoccupied-coastal-cells?", :kind "defn", :line 169, :end-line 171, :hash "679262937"} {:id "defn/country-has-other-coastal-city?", :kind "defn", :line 173, :end-line 176, :hash "-2027538523"} {:id "defn/country-army-limit-reached?", :kind "defn", :line 178, :end-line 180, :hash "1263578448"}]}
;; clj-mutate-manifest-end
