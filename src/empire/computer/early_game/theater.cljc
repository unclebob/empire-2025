(ns empire.computer.early-game.theater
  (:require [empire.computer.early-game.roles :as roles]
            [empire.computer.shared.grid :as grid]
            [empire.computer.shared.world-query :as world-query]
            [empire.computer.ship.lake-naval :as lake-naval]
            [empire.computer.land-objectives :as land-objectives]
            [empire.state.api :as sa]))

(def ^:private invasion-missions
  #{:invading :load-for-invasion :find-armies-for-invasion})

(def ^:private transport-item->role
  {:army :CA
   :fighter :CF
   :transport :CT
   :patrol-boat :CP
   :satellite :CA})

(declare computer-city?)

(defn- known-land-cell?
  [computer-map pos]
  (let [cell (get-in computer-map pos)]
    (contains? #{:land :city} (:type cell))))

(defn- compute-usable-coastal-city-positions
  [computer-map lakes]
  (into {}
        (for [x (range (count computer-map))
              y (range (count (first computer-map)))
              :let [pos [x y]
                    cell (get-in computer-map pos)]
              :when (computer-city? cell)]
          [pos
           (boolean
            (some (fn [neighbor]
                    (let [neighbor-cell (get-in computer-map neighbor)]
                      (and (= :sea (:type neighbor-cell))
                           (not (contains? lakes neighbor)))))
                  (world-query/get-neighbors pos)))])))

(def ^:private usable-coastal-cache (atom nil))

(defn city-usable-coastal?
  [city-pos]
  (let [positions (or @usable-coastal-cache
                      (let [result (compute-usable-coastal-city-positions
                                    (sa/read-state :computer-map) (lake-naval/known-lake-cells))]
                        (reset! usable-coastal-cache result)
                        result))]
    (boolean (get positions city-pos))))

(defn- theater-positions
  [start-pos]
  (let [computer-map (sa/read-state :computer-map)]
    (->> (or (land-objectives/flood-fill-continent start-pos) #{})
         (filter #(known-land-cell? computer-map %))
         set)))

(defn- computer-city?
  [cell]
  (and (= :city (:type cell))
       (= :computer (:city-status cell))))

(defn- city-info
  [pos]
  (let [cell (get-in (sa/read-state :computer-map) pos)
        production (get (sa/read-state :production) pos)
        opening-role (:opening-role cell)
        production-role (transport-item->role (:item production))
        role (or opening-role production-role)]
    {:pos pos
     :cell cell
     :country-id (:country-id cell)
     :coastal? (city-usable-coastal? pos)
     :role role
     :production production
     :pinned? (map? production)}))

(defn- loading-transport-on-landmass?
  [positions pos unit]
  (and (= :computer (:owner unit))
       (= :transport (:type unit))
       (= :loading (:transport-mission unit))
       (contains? positions pos)))

(defn- transport-producer
  [pos production]
  (when (= :transport (:item production))
    [pos (:remaining-rounds production)]))

(defn- staging-allowed-positions
  [positions transport-producers]
  (if (seq transport-producers)
    (set (filter (fn [pos]
                   (some (fn [[producer-pos remaining]]
                           (<= remaining (grid/distance pos producer-pos)))
                         transport-producers))
                 positions))
    #{}))

(defn- army-on-landmass?
  [positions pos unit]
  (and (= :computer (:owner unit))
       (= :army (:type unit))
       (contains? positions pos)))

(defn- conquered-city-count
  [owned-city-count]
  (max 0 (dec owned-city-count)))

(defn- phase
  [{:keys [army-count conquered-city-count]}]
  (if (or (>= (or (sa/read-state :round-number) 0) 30)
          (>= army-count 6)
          (>= conquered-city-count 2))
    :phase-2
    :phase-1))

(defn- active-invasion-transport?
  [unit]
  (let [mission (:transport-mission unit)]
    (and (= :transport (:type unit))
         (= :computer (:owner unit))
         (or (contains? invasion-missions mission)
             (and (= :unloading mission)
                  (or (:major-invasion unit)
                      (:invasion-target unit)
                      (:major-invasion-target unit)))))))

(def ^:private invasion-started-cache (atom ::unset))
(def ^:private opening-active-cache (atom ::unset))
(def ^:private theater-summary-cache (atom {}))

(defn clear-theater-caches! []
  (reset! invasion-started-cache ::unset)
  (reset! opening-active-cache ::unset)
  (reset! theater-summary-cache {})
  (reset! usable-coastal-cache nil))

(defn- compute-invasion-started? []
  (let [world (sa/read-state :computer-map)]
    (boolean
     (or (some (fn [col]
                 (some (fn [cell]
                         (active-invasion-transport? (:contents cell)))
                       col))
               world)
         (some (fn [col]
                 (some (fn [cell]
                         (= :move-to-coast-for-invasion
                            (get-in cell [:contents :mode])))
                       col))
               world)))))

(defn invasion-started?
  []
  (if (= ::unset @invasion-started-cache)
    (let [result (compute-invasion-started?)]
      (reset! invasion-started-cache result)
      result)
    @invasion-started-cache))

(defn opening-active?
  []
  (if (= ::unset @opening-active-cache)
    (let [result (and (number? (sa/read-state :round-number))
                      (not (invasion-started?)))]
      (reset! opening-active-cache result)
      result)
    @opening-active-cache))

(defn- cached-theater-summary
  [_computer-map _production _invasion-started? start-pos]
  (get @theater-summary-cache start-pos))

(defn- store-theater-summary!
  [_computer-map _production _invasion-started? summary]
  (doseq [pos (:positions summary)]
    (swap! theater-summary-cache assoc pos summary))
  summary)

(defn theater-summary
  [start-pos]
  (let [computer-map (sa/read-state :computer-map)
        production (sa/read-state :production)
        invasion-started? (invasion-started?)]
    (or (cached-theater-summary computer-map production invasion-started? start-pos)
        (let [positions (theater-positions start-pos)
              city-infos (->> positions
                              (filter #(computer-city? (get-in computer-map %)))
                              (map city-info)
                              vec)
              loading-transport-positions (->> positions
                                               (filter (fn [pos]
                                                         (loading-transport-on-landmass?
                                                          positions
                                                          pos
                                                          (get-in computer-map (conj pos :contents)))))
                                               vec)
              transport-producers (->> positions
                                       (keep (fn [pos]
                                               (transport-producer pos (get production pos))))
                                       vec)
              staging-allowed-positions (staging-allowed-positions positions transport-producers)
              coastal-count (count (filter :coastal? city-infos))
              inland-count (- (count city-infos) coastal-count)
              army-count (count (for [pos positions
                                      :let [unit (get-in computer-map (conj pos :contents))]
                                      :when (army-on-landmass? positions pos unit)]
                                  pos))
              summary {:theater-key (first (sort positions))
                       :positions positions
                       :cities city-infos
                       :loading-transport-positions loading-transport-positions
                       :transport-producers transport-producers
                       :staging-allowed-positions staging-allowed-positions
                       :coastal-count coastal-count
                       :landlocked-count inland-count
                       :army-count army-count
                       :conquered-city-count (conquered-city-count (count city-infos))}
              summary-with-phase (assoc summary :phase (phase summary))
              summary-with-roles (assoc summary-with-phase
                                   :role-plan (roles/theater-role-plan summary-with-phase))]
          (store-theater-summary! computer-map production invasion-started? summary-with-roles)))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-26T22:49:46.422852-05:00", :module-hash "1417354022", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 7, :hash "1031695753"} {:id "def/invasion-missions", :kind "def", :line 9, :end-line 10, :hash "618070090"} {:id "def/transport-item->role", :kind "def", :line 12, :end-line 17, :hash "-910150492"} {:id "form/3/declare", :kind "declare", :line 19, :end-line 19, :hash "-1552065191"} {:id "defn-/known-land-cell?", :kind "defn-", :line 21, :end-line 24, :hash "729866723"} {:id "defn-/lake-cells", :kind "defn-", :line 26, :end-line 29, :hash "-1686524640"} {:id "defn-/compute-usable-coastal-city-positions", :kind "defn-", :line 31, :end-line 45, :hash "313248468"} {:id "def/usable-coastal-cache", :kind "def", :line 47, :end-line 47, :hash "1732523560"} {:id "defn/city-usable-coastal?", :kind "defn", :line 49, :end-line 56, :hash "-1934156278"} {:id "defn-/theater-positions", :kind "defn-", :line 58, :end-line 63, :hash "1854125332"} {:id "defn-/computer-city?", :kind "defn-", :line 65, :end-line 68, :hash "-1224059993"} {:id "defn-/city-info", :kind "defn-", :line 70, :end-line 83, :hash "169931597"} {:id "defn-/loading-transport-on-landmass?", :kind "defn-", :line 85, :end-line 90, :hash "203547763"} {:id "defn-/transport-producer", :kind "defn-", :line 92, :end-line 95, :hash "2080422187"} {:id "defn-/staging-allowed-positions", :kind "defn-", :line 97, :end-line 105, :hash "2132851851"} {:id "defn-/army-on-landmass?", :kind "defn-", :line 107, :end-line 111, :hash "-233653345"} {:id "defn-/conquered-city-count", :kind "defn-", :line 113, :end-line 115, :hash "259874816"} {:id "defn-/phase", :kind "defn-", :line 117, :end-line 123, :hash "-2134968157"} {:id "defn-/active-invasion-transport?", :kind "defn-", :line 125, :end-line 134, :hash "1914935708"} {:id "def/invasion-started-cache", :kind "def", :line 136, :end-line 136, :hash "1940027302"} {:id "def/opening-active-cache", :kind "def", :line 137, :end-line 137, :hash "-373624000"} {:id "def/theater-summary-cache", :kind "def", :line 138, :end-line 138, :hash "-674848239"} {:id "defn/clear-theater-caches!", :kind "defn", :line 140, :end-line 144, :hash "-445443271"} {:id "defn-/compute-invasion-started?", :kind "defn-", :line 146, :end-line 159, :hash "-1685330290"} {:id "defn/invasion-started?", :kind "defn", :line 161, :end-line 167, :hash "311756128"} {:id "defn/opening-active?", :kind "defn", :line 169, :end-line 176, :hash "1941014746"} {:id "defn-/cached-theater-summary", :kind "defn-", :line 178, :end-line 180, :hash "-799258668"} {:id "defn-/store-theater-summary!", :kind "defn-", :line 182, :end-line 186, :hash "-1465076778"} {:id "defn/theater-summary", :kind "defn", :line 188, :end-line 230, :hash "962758419"}]}
;; clj-mutate-manifest-end
