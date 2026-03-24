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

(defn- lake-cells
  []
  (lake-naval/lake-cells (sa/read-state :computer-map)
                         (sa/read-state :lake-max-cells)))

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

(defn city-usable-coastal?
  [city-pos]
  (let [computer-map (sa/read-state :computer-map)
        lake-max-cells (sa/read-state :lake-max-cells)]
    (if (and (= computer-map (sa/read-state :usable-coastal-city-source))
             (= lake-max-cells (sa/read-state :usable-coastal-city-lake-max-cells)))
      (boolean (get (sa/read-state :usable-coastal-city-positions) city-pos))
      (let [lakes (lake-cells)
            positions (compute-usable-coastal-city-positions computer-map lakes)]
        (sa/write-state! :usable-coastal-city-source computer-map)
        (sa/write-state! :usable-coastal-city-lake-max-cells lake-max-cells)
        (sa/write-state! :usable-coastal-city-positions positions)
        (boolean (get positions city-pos))))))

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

(defn invasion-started?
  []
  (let [world (sa/read-state :computer-map)]
    (if (= world (sa/read-state :early-game-invasion-started-source))
      (boolean (sa/read-state :early-game-invasion-started-result))
      (let [started? (boolean
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
                                world)))]
        (sa/write-state! :early-game-invasion-started-source world)
        (sa/write-state! :early-game-invasion-started-result started?)
        started?))))

(defn opening-active?
  []
  (let [round-number (sa/read-state :round-number)
        world (sa/read-state :computer-map)]
    (if (and (= world (sa/read-state :early-game-opening-active-source))
             (= round-number (sa/read-state :early-game-opening-active-round)))
      (boolean (sa/read-state :early-game-opening-active-result))
      (let [active? (and (number? round-number)
                         (not (invasion-started?)))]
        (sa/write-state! :early-game-opening-active-source world)
        (sa/write-state! :early-game-opening-active-round round-number)
        (sa/write-state! :early-game-opening-active-result active?)
        active?))))

(defn- cached-theater-summary
  [computer-map production invasion-started? start-pos]
  (when (and (= computer-map (sa/read-state :early-game-theater-cache-source))
             (= production (sa/read-state :early-game-theater-cache-production))
             (= invasion-started? (sa/read-state :early-game-theater-cache-invasion-started?)))
    (get (sa/read-state :early-game-theater-cache) start-pos)))

(defn- store-theater-summary!
  [computer-map production invasion-started? summary]
  (when-not (= computer-map (sa/read-state :early-game-theater-cache-source))
    (sa/write-state! :early-game-theater-cache-source computer-map))
  (when-not (= production (sa/read-state :early-game-theater-cache-production))
    (sa/write-state! :early-game-theater-cache-production production))
  (when-not (= invasion-started? (sa/read-state :early-game-theater-cache-invasion-started?))
    (sa/write-state! :early-game-theater-cache-invasion-started? invasion-started?))
  (sa/update-state! :early-game-theater-cache
                    (fn [cache]
                      (reduce (fn [acc pos]
                                (assoc acc pos summary))
                              (or cache {})
                              (:positions summary))))
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
;; {:version 1, :tested-at "2026-03-13T15:59:39.292965-05:00", :module-hash "-1990901196", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 5, :hash "705477899"} {:id "def/invasion-missions", :kind "def", :line 7, :end-line 8, :hash "618070090"} {:id "def/transport-item->role", :kind "def", :line 10, :end-line 15, :hash "-910150492"} {:id "defn-/known-land-cell?", :kind "defn-", :line 17, :end-line 20, :hash "729866723"} {:id "defn-/lake-cells", :kind "defn-", :line 22, :end-line 25, :hash "-1686524640"} {:id "defn/city-usable-coastal?", :kind "defn", :line 27, :end-line 35, :hash "-1931668197"} {:id "defn-/theater-positions", :kind "defn-", :line 37, :end-line 42, :hash "54183605"} {:id "defn-/computer-city?", :kind "defn-", :line 44, :end-line 47, :hash "-1224059993"} {:id "defn-/city-info", :kind "defn-", :line 49, :end-line 62, :hash "1965251756"} {:id "defn-/army-on-landmass?", :kind "defn-", :line 64, :end-line 68, :hash "-233653345"} {:id "defn-/conquered-city-count", :kind "defn-", :line 70, :end-line 72, :hash "259874816"} {:id "defn-/phase", :kind "defn-", :line 74, :end-line 80, :hash "-2134968157"} {:id "defn-/active-invasion-transport?", :kind "defn-", :line 82, :end-line 91, :hash "1914935708"} {:id "defn/invasion-started?", :kind "defn", :line 93, :end-line 107, :hash "-1945983022"} {:id "defn/opening-active?", :kind "defn", :line 109, :end-line 112, :hash "1531037016"} {:id "defn/theater-summary", :kind "defn", :line 114, :end-line 135, :hash "1812566457"}]}
;; clj-mutate-manifest-end
