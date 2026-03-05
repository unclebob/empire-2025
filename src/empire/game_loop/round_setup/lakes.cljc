(ns empire.game-loop.round-setup.lakes
  (:require [empire.application.state-access :as sa]
            [empire.application.movement-services :as movement-services]
            [clojure.set :as set]))

(defn find-adjacent-empty-sea
  "Returns the first adjacent empty sea cell, or nil if none."
  [pos]
  (first (movement-services/get-matching-neighbors
          pos (sa/current-world) movement-services/neighbor-offsets
          #(and (= :sea (:type %)) (nil? (:contents %))))))

(defn- lake-shore-city?
  [computer-map lake-cells pos]
  (and (= :city (get-in computer-map (conj pos :type)))
       (some (fn [n]
             (and (= :sea (get-in computer-map (conj n :type)))
                    (contains? lake-cells n)))
             (movement-services/get-matching-neighbors pos computer-map movement-services/neighbor-offsets some?))))

(defn- find-adjacent-empty-sea-preferring-ocean
  [pos lake-cells]
  (let [candidates (movement-services/get-matching-neighbors
                    pos (sa/current-world) movement-services/neighbor-offsets
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
              next-candidates (for [n (movement-services/get-matching-neighbors
                                     current computer-map movement-services/neighbor-offsets some?)
                                  :when (and (not (contains? visited n))
                                             (land-or-city? computer-map n))]
                              n)]
        (recur (into (pop queue) next-candidates)
               (into visited next-candidates))))))

(defn- landmasses-adjacent-to-lake-cells
  [computer-map lake-cells]
  (let [adjacent-land-seeds (set (for [lake-pos lake-cells
                                       n (movement-services/get-matching-neighbors
                                          lake-pos computer-map movement-services/neighbor-offsets some?)
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
                            (movement-services/get-matching-neighbors
                             pos computer-map movement-services/neighbor-offsets some?))))
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
              neighbors (for [n (movement-services/get-matching-neighbors
                                 current computer-map movement-services/neighbor-offsets some?)
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
      (movement-services/update-cell-visibility pos (:owner unit))
      (movement-services/update-cell-visibility target (:owner unit)))))

(defn evacuate-lake-patrol-boats
  "Moves ships out of lake-shore city cells before production.
   Preserves full unit state by moving unit directly to adjacent empty sea.
   If no adjacent sea cell is available, unit remains in city for this round."
  []
  (let [computer-map (sa/read-state :computer-map)
        lake-max-cells (sa/read-state :lake-max-cells)
        lake-cells (movement-services/lake-cells computer-map lake-max-cells)
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
        lake-cells (movement-services/lake-cells computer-map lake-max-cells)
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
