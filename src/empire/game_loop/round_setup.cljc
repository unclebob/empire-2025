;; mutation-tested: 2026-02-26
(ns empire.game-loop.round-setup
  "Round initialization: satellite moves, fuel consumption, sentry waking,
   dead unit removal, repair, step resets."
  (:require [empire.application.runtime :as app-runtime]
            [empire.application.state :as app-state]
            [clojure.set :as set]
            [empire.config :as config]
            [empire.containers.ops :as container-ops]
            [empire.containers.helpers :as uc]
            [empire.domain.world.round-setup :as domain-round-setup]
            [empire.game-loop.round-setup.satellites :as satellites]
            [empire.movement.lakes :as lakes]
            [empire.movement.map-utils :as map-utils]
            [empire.movement.satellite :as satellite]
            [empire.movement.visibility :as visibility]
            [empire.movement.wake-conditions :as wake]
            [empire.units.dispatcher :as dispatcher]))

(def ^:private state-ctx
  (delay (app-runtime/default-state-ctx)))

(defn- current-world
  []
  ((:load-world @state-ctx)))

(defn- update-game-map!
  [f & args]
  (apply app-state/update-world! @state-ctx f args))

(defn- read-runtime-state
  [k]
  ((:read-runtime-state @state-ctx) k))

(defn- write-runtime-state!
  [k v]
  ((:write-runtime-state! @state-ctx) k v))

(defn- update-runtime-state!
  [k f & args]
  (let [current (read-runtime-state k)
        next-state (apply f current args)]
    (write-runtime-state! k next-state)))

(defn- set-error-message!
  [msg ms]
  (write-runtime-state! :error-message msg)
  (write-runtime-state! :error-until (+ (System/currentTimeMillis) ms)))

(defn- world-ref
  [world]
  (atom world))

(defn dead-unit? [contents]
  (domain-round-setup/dead-unit? contents))

(defn computer-carrier? [contents]
  (domain-round-setup/computer-carrier? contents))

(defn remove-dead-units
  "Removes units with hits at or below zero."
  []
  (let [world (current-world)]
    (doseq [i (range (count world))
            j (range (count (first world)))
            :let [cell (get-in world [i j])
                contents (:contents cell)]
            :when (dead-unit? contents)]
      (when (computer-carrier? contents)
        (update-runtime-state! :computer-carrier-positions disj [i j]))
      (update-game-map! assoc-in [i j] (dissoc cell :contents))
      (visibility/update-cell-visibility [i j] (:owner contents)))))

(defn reset-steps-remaining
  "Resets steps-remaining for all player units at start of round."
  []
  (let [world (current-world)]
    (doseq [i (range (count world))
            j (range (count (first world)))
            :let [cell (get-in world [i j])
                unit (:contents cell)]
            :when (and unit (= (:owner unit) :player))]
      (let [steps (or (dispatcher/effective-speed (:type unit) (:hits unit)) 1)]
        (update-game-map! assoc-in [i j :contents :steps-remaining] steps)))))

(defn wake-airport-fighters
  "Wakes all fighters in player city airports at start of round.
   Fighters will be auto-launched if the city has a flight-path,
   otherwise they will demand attention."
  []
  (let [world (current-world)]
    (doseq [i (range (count world))
            j (range (count (first world)))
            :let [cell (get-in world [i j])]
            :when (and (= (:type cell) :city)
                       (= (:city-status cell) :player)
                       (pos? (uc/get-count cell :fighter-count)))]
      (let [total (uc/get-count cell :fighter-count)]
        (update-game-map! assoc-in [i j :awake-fighters] total)))))

(defn wake-carrier-fighters
  "Wakes all fighters on player carriers at start of round.
   Fighters will be auto-launched if the carrier has a flight-path,
   otherwise they will demand attention."
  []
  (let [world (current-world)]
    (doseq [i (range (count world))
            j (range (count (first world)))
            :let [cell (get-in world [i j])
                  unit (:contents cell)]
            :when (and unit
                       (= :carrier (:type unit))
                       (= :player (:owner unit))
                       (pos? (uc/get-count unit :fighter-count)))]
      (let [total (uc/get-count unit :fighter-count)]
        (update-game-map! assoc-in [i j :contents :awake-fighters] total)))))

(defn- bingo-fuel? [pos new-fuel]
  (let [world (current-world)]
    (domain-round-setup/bingo-fuel?
     new-fuel
     (wake/friendly-city-in-range? pos new-fuel (world-ref world)))))

(defn- fuel-action [new-fuel pos]
  (domain-round-setup/fuel-action new-fuel (bingo-fuel? pos new-fuel)))

(defn- apply-fuel-action [pos action new-fuel]
  (case action
    :crashed (do (set-error-message! (:fighter-crashed config/messages) config/error-message-duration)
                 (update-game-map! assoc-in (conj pos :contents :hits) 0))
    :out-of-fuel (update-game-map! update-in (conj pos :contents)
                                   #(assoc % :fuel new-fuel :mode :awake :reason :fighter-out-of-fuel))
    :bingo (update-game-map! update-in (conj pos :contents)
                             #(assoc % :fuel new-fuel :mode :awake :reason :fighter-bingo))
    :burn (update-game-map! assoc-in (conj pos :contents :fuel) new-fuel)))

(defn consume-sentry-fighter-fuel
  "Consumes fuel for sentry fighters each round, applying fuel warnings."
  []
  (let [world (current-world)]
    (doseq [i (range (count world))
            j (range (count (first world)))
            :let [cell (get-in world [i j])
                  unit (:contents cell)]
            :when (and unit
                       (= :fighter (:type unit))
                       (= :sentry (:mode unit)))]
      (let [new-fuel (dec (:fuel unit config/fighter-fuel))]
        (apply-fuel-action [i j] (fuel-action new-fuel [i j]) new-fuel)))))

(defn wake-sentries-seeing-enemy
  "Wakes player sentry units that can see an enemy unit."
  []
  (let [world (current-world)
        world-atom (world-ref world)]
    (doseq [i (range (count world))
            j (range (count (first world)))
            :let [cell (get-in world [i j])
                  unit (:contents cell)]
            :when (and unit
                       (= :player (:owner unit))
                       (= :sentry (:mode unit))
                       (wake/enemy-unit-visible? unit [i j] world-atom))]
      (update-game-map! update-in [i j :contents]
                        #(assoc % :mode :awake :reason :enemy-spotted)))))

(defn move-satellites
  "Moves all satellites according to their speed.
   Removes satellites with turns-remaining at or below zero."
  []
  (satellites/move-satellites!
   {:current-world current-world
    :update-game-map! update-game-map!
    :update-visibility! visibility/update-cell-visibility
    :move-satellite satellite/move-satellite
    :satellite-speed (config/unit-speed :satellite)}))

(defn- find-adjacent-empty-sea
  "Returns the first adjacent empty sea cell, or nil if none."
  [pos]
  (first (map-utils/get-matching-neighbors
          pos (current-world) map-utils/neighbor-offsets
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
                    pos (current-world) map-utils/neighbor-offsets
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
          :let [unit (get-in (current-world) (conj pos :contents))]
          :when (and unit
                     (= :computer (:owner unit))
                     (= :army (:type unit))
                     (:country-id unit))]
    (let [target (get coast-target-by-pos pos)]
      (update-game-map! update-in (conj pos :contents)
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
  (let [known (or (read-runtime-state :known-lake-cells) #{})
        newly-discovered (set/difference lake-cells known)]
    (when (seq newly-discovered)
      (doseq [landmass (landmasses-adjacent-to-lake-cells computer-map newly-discovered)]
        (let [coast-seeds (ocean-coast-cells-in-landmass computer-map lake-cells landmass)
              target-map (if (seq coast-seeds)
                           (nearest-ocean-coast-targets computer-map landmass coast-seeds)
                           {})]
          (wake-and-retask-landmass-armies! landmass target-map))))
    (write-runtime-state! :known-lake-cells lake-cells)))

(defn- mark-evacuated-transport-for-unload!
  [pos unit]
  (when (and (= :transport (:type unit))
             (= :computer (:owner unit))
             (pos? (:army-count unit 0)))
    (update-game-map! update-in (conj pos :contents)
                      #(-> %
                           (assoc :never-reload? true)
                           (assoc :transport-mission :land-locked)
                           (dissoc :sail-path :invasion-path :invasion-path-origin)))))

(defn- evacuable-lake-shore-city-unit
  [computer-map lake-cells pos]
  (let [cell (get-in (current-world) pos)
        unit (:contents cell)]
    (when (and (= :city (:type cell))
               (#{:player :computer} (:city-status cell))
               (evacuable-ship-types (:type unit))
               (lake-shore-city? computer-map lake-cells pos))
      unit)))

(defn- evacuate-city-ship!
  [pos unit lake-cells]
  (when-let [target (find-adjacent-empty-sea-preferring-ocean pos lake-cells)]
    (when (update-game-map! assoc-in (conj target :contents) unit)
      (update-game-map! assoc-in (conj pos :contents) nil)
      (mark-evacuated-transport-for-unload! target unit)
      (visibility/update-cell-visibility pos (:owner unit))
      (visibility/update-cell-visibility target (:owner unit)))))

(defn evacuate-lake-patrol-boats
  "Moves ships out of lake-shore city cells before production.
   Preserves full unit state by moving unit directly to adjacent empty sea.
   If no adjacent sea cell is available, unit remains in city for this round."
  []
  (let [computer-map (read-runtime-state :computer-map)
        lake-max-cells (read-runtime-state :lake-max-cells)
        lake-cells (lakes/lake-cells computer-map lake-max-cells)
        world (current-world)
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
  (update-game-map! assoc-in (conj pos :contents :lake-locked?) true)
  (when (= :transport (:type unit))
    (update-game-map! assoc-in (conj pos :contents :never-reload?) true)
    (when (pos? (:army-count unit 0))
      (update-game-map! assoc-in (conj pos :contents :transport-mission) :land-locked))))

(defn- clear-ship-lake-lock!
  [pos]
  (update-game-map! update-in (conj pos :contents) dissoc :lake-locked?))

(defn mark-lake-locked-ships
  "Marks computer ships located in known lakes as :lake-locked?.
   Lake-locked transports are forced into unloading when carrying armies."
  []
  (let [computer-map (read-runtime-state :computer-map)
        lake-max-cells (read-runtime-state :lake-max-cells)
        lake-cells (lakes/lake-cells computer-map lake-max-cells)
        world (current-world)]
    (retask-armies-for-new-lakes! computer-map lake-cells)
    (doseq [r (range (count world))
            c (range (count (first world)))
            :let [pos [r c]
                  unit (get-in (current-world) (conj pos :contents))]
            :when (lake-locked-ship? unit)]
      (if (contains? lake-cells pos)
        (lock-ship-for-lake! pos unit)
        (clear-ship-lake-lock! pos)))))

(defn- repair-city-ships
  "Repairs all ships in a city's shipyard by 1 hit each.
   Launches fully repaired ships to city cell or adjacent sea."
  [city-coords]
  (let [cell (get-in (current-world) city-coords)
        shipyard (uc/get-shipyard-ships cell)]
    (when (seq shipyard)
      ;; First, repair all ships
      (let [repaired-ships (mapv uc/repair-ship shipyard)]
        (update-game-map! assoc-in (conj city-coords :shipyard) repaired-ships))
      ;; Then, launch fully repaired ships
      ;; Process from end to avoid index shifting issues
      (let [updated-cell (get-in (current-world) city-coords)
            updated-shipyard (uc/get-shipyard-ships updated-cell)]
        (doseq [i (reverse (range (count updated-shipyard)))]
          (let [current-cell (get-in (current-world) city-coords)
                ship (get-in current-cell [:shipyard i])]
            (when (uc/ship-fully-repaired? ship)
              (let [launch-pos (if (nil? (:contents current-cell))
                                 city-coords
                                 (find-adjacent-empty-sea city-coords))]
                (when launch-pos
                  (container-ops/launch-ship-from-shipyard city-coords i launch-pos))))))))))

(defn repair-damaged-ships
  "Repairs ships in all friendly city shipyards by 1 hit per round.
   Launches fully repaired ships onto the map if the city cell is empty."
  []
  (let [world (current-world)]
    (doseq [i (range (count world))
            j (range (count (first world)))
            :let [cell (get-in world [i j])]
            :when (and (= (:type cell) :city)
                       (#{:player :computer} (:city-status cell))
                       (seq (uc/get-shipyard-ships cell)))]
      (repair-city-ships [i j]))))
