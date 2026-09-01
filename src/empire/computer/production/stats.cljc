(ns empire.computer.production.stats
  (:require [empire.state.api :as sa]
            [empire.computer.shared.world-query :as world-query]))

(defn- get-neighbors [pos]
  (filter #(some? (get-in (sa/read-state :computer-map) %))
          (world-query/get-neighbors pos)))

(defn city-is-coastal? [city-pos]
  (let [computer-map (sa/read-state :computer-map)]
    (some (fn [neighbor]
            (= :sea (:type (get-in computer-map neighbor))))
          (get-neighbors city-pos))))

(defn- coastal? [visible-map pos]
  (some (fn [n] (= :sea (:type (get-in visible-map n))))
        (filter #(some? (get-in visible-map %))
                (world-query/get-neighbors pos))))

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

(defn- accumulate-scanned-unit
  [acc ucid unit cell-type is-coastal]
  (case (:type unit)
    :army (accumulate-army acc ucid cell-type is-coastal)
    :transport (accumulate-transport acc ucid unit)
    :patrol-boat (update-country acc ucid :patrol-boat-count inc)
    acc))

(defn- scan-cell-unit [acc comp-map i j]
  (let [unit (:contents (get-in comp-map [i j]))
        ucid (:country-id unit)]
    (if-not (computer-unit-with-country? unit)
      acc
      (let [cell-type (or (:type (get-in comp-map [i j]))
                          :unexplored)
            is-coastal (coastal-land-or-city? comp-map cell-type [i j])]
        (accumulate-scanned-unit acc ucid unit cell-type is-coastal)))))

(defn- scan-cell [acc comp-map i j]
  (-> acc
      (scan-cell-terrain comp-map i j)
      (scan-cell-unit comp-map i j)))

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

(defn- computer-owned-unit?
  [unit]
  (and unit (= :computer (:owner unit))))

(defn- computer-city-cell?
  [cell]
  (and (= :city (:type cell))
       (= :computer (:city-status cell))))

(defn- scan-cell-assets
  [acc cell]
  (let [unit (:contents cell)
        computer-unit? (computer-owned-unit? unit)]
    (cond-> acc
      computer-unit?
      (update :unit-counts
              (fn [counts]
                (update (or counts {}) (:type unit) (fnil inc 0))))

      (computer-city-cell? cell)
      (update :computer-city-count inc)

      (and computer-unit? (= :fighter (:type unit)))
      (update :computer-fighter-count inc))))

(defn scan-computer-assets [comp-map]
  (reduce
   (fn [acc column]
     (reduce scan-cell-assets acc column))
   {:unit-counts {}
    :computer-city-count 0
    :computer-fighter-count 0}
   comp-map))

(def ^:private asset-cache (atom nil))

(defn clear-asset-cache! [] (reset! asset-cache nil))

(defn rebuild-country-stats! []
  (let [comp-map (sa/read-state :computer-map)
        rows (count (first comp-map))
        cols (count comp-map)
        raw (reduce (fn [acc i]
                      (reduce (fn [acc j]
                                (scan-cell acc comp-map i j))
                              acc (range rows)))
                    {} (range cols))
        asset-counts (scan-computer-assets comp-map)]
    (sa/write-state! :country-stats (derive-stats raw))
    (reset! asset-cache asset-counts)))

(defn- current-asset-counts []
  (or @asset-cache
      (let [result (scan-computer-assets (sa/read-state :computer-map))]
        (reset! asset-cache result)
        result)))

(defn count-computer-units []
  (:unit-counts (current-asset-counts)))

(defn count-computer-cities []
  (:computer-city-count (current-asset-counts)))

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
  (:computer-fighter-count (current-asset-counts)))

(defn count-carrier-producers []
  (count (filter (fn [[_coords prod]]
                   (and (map? prod)
                        (= :carrier (:item prod))))
                 (sa/read-state :production))))

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
;; {:version 1, :tested-at "2026-09-01T16:18:30.645831-05:00", :module-hash "-1867695976", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "165047349"} {:id "defn-/get-neighbors", :kind "defn-", :line 5, :end-line nil, :hash "68561550"} {:id "defn/city-is-coastal?", :kind "defn", :line 9, :end-line nil, :hash "1641264784"} {:id "defn-/coastal?", :kind "defn-", :line 15, :end-line nil, :hash "-261036923"} {:id "defn-/update-country", :kind "defn-", :line 20, :end-line nil, :hash "1264170932"} {:id "defn-/land-or-city?", :kind "defn-", :line 23, :end-line nil, :hash "938934598"} {:id "defn-/unoccupied-coastal-land?", :kind "defn-", :line 26, :end-line nil, :hash "-1060931663"} {:id "defn-/unexplored-cell?", :kind "defn-", :line 29, :end-line nil, :hash "-1311494332"} {:id "defn-/coastal-computer-city?", :kind "defn-", :line 32, :end-line nil, :hash "-184583193"} {:id "defn-/computer-unit-with-country?", :kind "defn-", :line 35, :end-line nil, :hash "-1256622862"} {:id "defn-/accumulate-coastal-terrain", :kind "defn-", :line 38, :end-line nil, :hash "-194087817"} {:id "defn-/scan-cell-terrain", :kind "defn-", :line 47, :end-line nil, :hash "1666323538"} {:id "defn-/accumulate-army", :kind "defn-", :line 55, :end-line nil, :hash "1494818776"} {:id "defn-/accumulate-transport", :kind "defn-", :line 62, :end-line nil, :hash "-212083715"} {:id "defn-/coastal-land-or-city?", :kind "defn-", :line 66, :end-line nil, :hash "2052839069"} {:id "defn-/accumulate-scanned-unit", :kind "defn-", :line 69, :end-line nil, :hash "702460564"} {:id "defn-/scan-cell-unit", :kind "defn-", :line 77, :end-line nil, :hash "-1837015668"} {:id "defn-/scan-cell", :kind "defn-", :line 87, :end-line nil, :hash "-1255819850"} {:id "defn-/derive-stats", :kind "defn-", :line 92, :end-line nil, :hash "-1505881565"} {:id "defn-/computer-owned-unit?", :kind "defn-", :line 115, :end-line nil, :hash "-639754121"} {:id "defn-/computer-city-cell?", :kind "defn-", :line 119, :end-line nil, :hash "415812035"} {:id "defn-/scan-cell-assets", :kind "defn-", :line 124, :end-line nil, :hash "-334648637"} {:id "defn/scan-computer-assets", :kind "defn", :line 140, :end-line nil, :hash "-1396265898"} {:id "def/asset-cache", :kind "def", :line 149, :end-line nil, :hash "-675496310"} {:id "defn/clear-asset-cache!", :kind "defn", :line 151, :end-line nil, :hash "-82281937"} {:id "defn/rebuild-country-stats!", :kind "defn", :line 153, :end-line nil, :hash "-973473336"} {:id "defn-/current-asset-counts", :kind "defn-", :line 166, :end-line nil, :hash "-2096612076"} {:id "defn/count-computer-units", :kind "defn", :line 172, :end-line nil, :hash "-328736772"} {:id "defn/count-computer-cities", :kind "defn", :line 175, :end-line nil, :hash "-2049813028"} {:id "defn/count-country-armies", :kind "defn", :line 178, :end-line nil, :hash "-1218457171"} {:id "defn/count-country-coastal-cells", :kind "defn", :line 181, :end-line nil, :hash "1857517052"} {:id "defn/country-coastal-cells-explored?", :kind "defn", :line 184, :end-line nil, :hash "-1701809254"} {:id "defn/country-has-waiting-armies?", :kind "defn", :line 187, :end-line nil, :hash "376173458"} {:id "defn/count-all-computer-fighters", :kind "defn", :line 191, :end-line nil, :hash "1778770512"} {:id "defn/count-carrier-producers", :kind "defn", :line 194, :end-line nil, :hash "-1191728176"} {:id "defn/count-country-patrol-boats", :kind "defn", :line 200, :end-line nil, :hash "1218129403"} {:id "defn/country-has-unadopted-transport?", :kind "defn", :line 203, :end-line nil, :hash "-1721744299"} {:id "defn/has-unoccupied-coastal-cells?", :kind "defn", :line 207, :end-line nil, :hash "679262937"} {:id "defn/country-has-other-coastal-city?", :kind "defn", :line 211, :end-line nil, :hash "405099722"} {:id "defn/country-army-limit-reached?", :kind "defn", :line 216, :end-line nil, :hash "1263578448"}]}
;; clj-mutate-manifest-end
