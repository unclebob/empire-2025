(ns empire.game.loop.round-setup.lakes
  (:require [empire.state.api :as sa]
            [empire.game-mechanics.movement.map-utils :as map-utils]
            [empire.game-mechanics.movement.lakes :as lakes]
            [empire.game-mechanics.visibility :as visibility]
            [clojure.set :as set]))

(defn find-adjacent-empty-sea
  "Returns the first adjacent empty sea cell, or nil if none."
  [pos]
  (first (map-utils/get-matching-neighbors
          pos (sa/current-world) map-utils/neighbor-offsets
          #(and (= :sea (:type %)) (nil? (:contents %))))))

(defn- lake-shore-city?
  [computer-map lake-cells pos]
  (and (= :city (get-in computer-map (conj pos :type)))
       (some (fn [n]
             (and (= :sea (get-in computer-map (conj n :type)))
                    (contains? lake-cells n)))
             (map-utils/get-matching-neighbors pos computer-map map-utils/neighbor-offsets some?))))

(defn- find-adjacent-empty-sea-preferring-ocean
  [pos lake-cells]
  (let [candidates (map-utils/get-matching-neighbors
                    pos (sa/current-world) map-utils/neighbor-offsets
                    #(and (= :sea (:type %)) (nil? (:contents %))))
        ocean (remove lake-cells candidates)]
    (or (first ocean) (first candidates))))

(def ^:private evacuable-ship-types
  #{:patrol-boat :destroyer :submarine :battleship :carrier :transport})

(def ^:private lake-lockable-ship-types
  #{:patrol-boat :destroyer :submarine :battleship :carrier :transport})

(defn- land-or-city?
  [computer-map pos]
  (let [t (get-in computer-map (conj pos :type))]
    (or (= :land t) (= :city t))))

(defn- ocean-sea-cell?
  [computer-map lake-cells pos]
  (and (= :sea (get-in computer-map (conj pos :type)))
       (not (contains? lake-cells pos))))

(defn- flood-fill-landmass
  [computer-map start]
  (loop [queue (conj clojure.lang.PersistentQueue/EMPTY start)
         visited #{start}]
    (if (empty? queue)
      visited
      (let [current (peek queue)
              next-candidates (for [n (map-utils/get-matching-neighbors
                                     current computer-map map-utils/neighbor-offsets some?)
                                  :when (and (not (contains? visited n))
                                             (land-or-city? computer-map n))]
                              n)]
        (recur (into (pop queue) next-candidates)
               (into visited next-candidates))))))

(defn- landmasses-adjacent-to-lake-cells
  [computer-map lake-cells]
  (let [adjacent-land-seeds (set (for [lake-pos lake-cells
                                       n (map-utils/get-matching-neighbors
                                          lake-pos computer-map map-utils/neighbor-offsets some?)
                                       :when (land-or-city? computer-map n)]
                                   n))]
    (loop [remaining adjacent-land-seeds
           masses []]
      (if-let [seed (first remaining)]
        (let [mass (flood-fill-landmass computer-map seed)]
          (recur (set/difference remaining mass)
                 (conj masses mass)))
        masses))))

(defn- ocean-coast-cells-in-landmass
  [computer-map lake-cells landmass]
  (set (filter (fn [pos]
                 (and (land-or-city? computer-map pos)
                      (some (fn [n] (ocean-sea-cell? computer-map lake-cells n))
                            (map-utils/get-matching-neighbors
                             pos computer-map map-utils/neighbor-offsets some?))))
               landmass)))

(defn- nearest-ocean-coast-targets
  [computer-map landmass coast-seeds]
  (let [landmass-set (set landmass)]
    (loop [queue (into clojure.lang.PersistentQueue/EMPTY
                       (map (fn [seed] [seed seed]) coast-seeds))
           assigned (into {} (map (fn [seed] [seed seed]) coast-seeds))]
      (if (empty? queue)
        assigned
        (let [[current seed] (peek queue)
              neighbors (for [n (map-utils/get-matching-neighbors
                                 current computer-map map-utils/neighbor-offsets some?)
                              :when (and (contains? landmass-set n)
                                         (land-or-city? computer-map n)
                                         (not (contains? assigned n)))]
                          n)]
          (recur (into (pop queue) (map (fn [n] [n seed]) neighbors))
                 (reduce (fn [m n] (assoc m n seed)) assigned neighbors)))))))

(defn- wake-and-retask-landmass-armies!
  [landmass coast-target-by-pos]
  (doseq [pos landmass
          :let [unit (get-in (sa/current-world) (conj pos :contents))]
          :when (and unit
                     (= :computer (:owner unit))
                     (= :army (:type unit))
                     (:country-id unit))]
    (let [target (get coast-target-by-pos pos)]
      (sa/update-world! update-in (conj pos :contents)
                        #(cond-> (-> %
                                     (assoc :mode :move-to-coast-for-invasion)
                                     (assoc :lake-retask? true)
                                     (dissoc :attack-target
                                             :random-explore-direction
                                             :interior-explore-direction
                                             :coast-direction
                                             :coast-start
                                             :coast-visited
                                             :reason))
                           target (assoc :coast-target target))))))

(defn- retask-armies-for-new-lakes!
  [computer-map lake-cells]
  (let [known (or (sa/read-state :known-lake-cells) #{})
        newly-discovered (set/difference lake-cells known)]
    (when (seq newly-discovered)
      (doseq [landmass (landmasses-adjacent-to-lake-cells computer-map newly-discovered)]
        (let [coast-seeds (ocean-coast-cells-in-landmass computer-map lake-cells landmass)
              target-map (if (seq coast-seeds)
                           (nearest-ocean-coast-targets computer-map landmass coast-seeds)
                           {})]
          (wake-and-retask-landmass-armies! landmass target-map))))
    (sa/write-state! :known-lake-cells lake-cells)))

(defn- mark-evacuated-transport-for-unload!
  [pos unit]
  (when (and (= :transport (:type unit))
             (= :computer (:owner unit))
             (pos? (:army-count unit 0)))
    (sa/update-world! update-in (conj pos :contents)
                      #(-> %
                           (assoc :never-reload? true)
                           (assoc :transport-mission :land-locked)
                           (dissoc :sail-path :invasion-path :invasion-path-origin)))))

(defn- evacuable-lake-shore-city-unit
  [computer-map lake-cells pos]
  (let [cell (get-in (sa/current-world) pos)
        unit (:contents cell)]
    (when (and (= :city (:type cell))
               (#{:player :computer} (:city-status cell))
               (evacuable-ship-types (:type unit))
               (lake-shore-city? computer-map lake-cells pos))
      unit)))

(defn- evacuate-city-ship!
  [pos unit lake-cells]
  (when-let [target (find-adjacent-empty-sea-preferring-ocean pos lake-cells)]
    (when (sa/update-world! assoc-in (conj target :contents) unit)
      (sa/update-world! assoc-in (conj pos :contents) nil)
      (mark-evacuated-transport-for-unload! target unit)
      (visibility/update-cell-visibility pos (:owner unit))
      (visibility/update-cell-visibility target (:owner unit)))))

(defn evacuate-lake-patrol-boats
  "Moves ships out of lake-shore city cells before production.
   Preserves full unit state by moving unit directly to adjacent empty sea.
   If no adjacent sea cell is available, unit remains in city for this round."
  []
  (let [computer-map (sa/read-state :computer-map)
        lake-max-cells (sa/read-state :lake-max-cells)
        lake-cells (lakes/lake-cells computer-map lake-max-cells)
        world (sa/current-world)
        rows (count world)
        cols (count (first world))]
    (when (seq lake-cells)
      (doseq [r (range rows)
              c (range cols)
              :let [pos [r c]]]
        (when-let [unit (evacuable-lake-shore-city-unit computer-map lake-cells pos)]
          (evacuate-city-ship! pos unit lake-cells))))))

(defn- lake-locked-ship?
  [unit]
  (and unit
       (= :computer (:owner unit))
       (lake-lockable-ship-types (:type unit))))

(defn- lock-ship-for-lake!
  [pos unit]
  (sa/update-world! assoc-in (conj pos :contents :lake-locked?) true)
  (when (= :transport (:type unit))
    (sa/update-world! assoc-in (conj pos :contents :never-reload?) true)
    (when (pos? (:army-count unit 0))
      (sa/update-world! assoc-in (conj pos :contents :transport-mission) :land-locked))))

(defn- clear-ship-lake-lock!
  [pos]
  (sa/update-world! update-in (conj pos :contents) dissoc :lake-locked?))

(defn mark-lake-locked-ships
  "Marks computer ships located in known lakes as :lake-locked?.
   Lake-locked transports are forced into unloading when carrying armies."
  []
  (let [computer-map (sa/read-state :computer-map)
        lake-max-cells (sa/read-state :lake-max-cells)
        lake-cells (lakes/lake-cells computer-map lake-max-cells)
        world (sa/current-world)]
    (retask-armies-for-new-lakes! computer-map lake-cells)
    (doseq [r (range (count world))
            c (range (count (first world)))
            :let [pos [r c]
                  unit (get-in (sa/current-world) (conj pos :contents))]
            :when (lake-locked-ship? unit)]
      (if (contains? lake-cells pos)
        (lock-ship-for-lake! pos unit)
        (clear-ship-lake-lock! pos)))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T12:00:12.687225-05:00", :module-hash "1796332944", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 6, :hash "-111006145"} {:id "defn/find-adjacent-empty-sea", :kind "defn", :line 8, :end-line 13, :hash "-2051021745"} {:id "defn-/lake-shore-city?", :kind "defn-", :line 15, :end-line 21, :hash "-1399784896"} {:id "defn-/find-adjacent-empty-sea-preferring-ocean", :kind "defn-", :line 23, :end-line 29, :hash "1646179833"} {:id "def/evacuable-ship-types", :kind "def", :line 31, :end-line 32, :hash "341681932"} {:id "def/lake-lockable-ship-types", :kind "def", :line 34, :end-line 35, :hash "-2113889102"} {:id "defn-/land-or-city?", :kind "defn-", :line 37, :end-line 40, :hash "-1785134228"} {:id "defn-/ocean-sea-cell?", :kind "defn-", :line 42, :end-line 45, :hash "1442189343"} {:id "defn-/flood-fill-landmass", :kind "defn-", :line 47, :end-line 60, :hash "1069130046"} {:id "defn-/landmasses-adjacent-to-lake-cells", :kind "defn-", :line 62, :end-line 75, :hash "-48339262"} {:id "defn-/ocean-coast-cells-in-landmass", :kind "defn-", :line 77, :end-line 84, :hash "-78460705"} {:id "defn-/nearest-ocean-coast-targets", :kind "defn-", :line 86, :end-line 102, :hash "-1625849077"} {:id "defn-/wake-and-retask-landmass-armies!", :kind "defn-", :line 104, :end-line 124, :hash "-1148689776"} {:id "defn-/retask-armies-for-new-lakes!", :kind "defn-", :line 126, :end-line 137, :hash "992680144"} {:id "defn-/mark-evacuated-transport-for-unload!", :kind "defn-", :line 139, :end-line 148, :hash "190196640"} {:id "defn-/evacuable-lake-shore-city-unit", :kind "defn-", :line 150, :end-line 158, :hash "-1675671506"} {:id "defn-/evacuate-city-ship!", :kind "defn-", :line 160, :end-line 167, :hash "-954783996"} {:id "defn/evacuate-lake-patrol-boats", :kind "defn", :line 169, :end-line 185, :hash "1391051551"} {:id "defn-/lake-locked-ship?", :kind "defn-", :line 187, :end-line 191, :hash "-856046332"} {:id "defn-/lock-ship-for-lake!", :kind "defn-", :line 193, :end-line 199, :hash "-1699526295"} {:id "defn-/clear-ship-lake-lock!", :kind "defn-", :line 201, :end-line 203, :hash "-899867636"} {:id "defn/mark-lake-locked-ships", :kind "defn", :line 205, :end-line 221, :hash "66219368"}]}
;; clj-mutate-manifest-end
