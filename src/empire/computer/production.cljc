;; mutation-tested: 2026-02-22
(ns empire.computer.production
  "Computer production module - priority-based production."
  (:require [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.movement.map-utils :as map-utils]
            [empire.player.production :as production]
            [empire.computer.ship :as ship]))

;; Preserved utilities

(defn- get-neighbors
  "Returns valid neighbor coordinates for a position."
  [pos]
  (map-utils/get-matching-neighbors pos @atoms/game-map map-utils/neighbor-offsets
                                    some?))

(defn city-is-coastal?
  "Returns true if city has adjacent sea cells."
  [city-pos]
  (some (fn [neighbor]
          (= :sea (:type (get-in @atoms/game-map neighbor))))
        (get-neighbors city-pos)))

(defn count-computer-units
  "Counts computer units by type. Returns map of type to count."
  []
  (let [units (for [i (range (count @atoms/game-map))
                    j (range (count (first @atoms/game-map)))
                    :let [cell (get-in @atoms/game-map [i j])
                          unit (:contents cell)]
                    :when (and unit (= :computer (:owner unit)))]
                (:type unit))]
    (frequencies units)))

(defn count-computer-cities
  "Counts the number of computer-owned cities."
  []
  (count (for [i (range (count @atoms/game-map))
               j (range (count (first @atoms/game-map)))
               :let [cell (get-in @atoms/game-map [i j])]
               :when (and (= :city (:type cell))
                          (= :computer (:city-status cell)))]
           [i j])))


(defn- count-country-transports
  "Counts live transports belonging to the given country-id."
  [country-id]
  (count (for [i (range (count @atoms/game-map))
               j (range (count (first @atoms/game-map)))
               :let [cell (get-in @atoms/game-map [i j])
                     unit (:contents cell)]
               :when (and unit
                          (= :computer (:owner unit))
                          (= :transport (:type unit))
                          (= country-id (:country-id unit)))]
           true)))

(defn count-country-armies
  "Counts live armies belonging to the given country-id,
   including armies aboard transports of the same country."
  [country-id]
  (reduce
    (fn [total [_i row]]
      (reduce
        (fn [total [_j cell]]
          (let [unit (:contents cell)]
            (cond
              (and unit
                   (= :computer (:owner unit))
                   (= :army (:type unit))
                   (= country-id (:country-id unit)))
              (inc total)

              (and unit
                   (= :computer (:owner unit))
                   (= :transport (:type unit))
                   (= country-id (:country-id unit)))
              (+ total (get unit :army-count 0))

              :else total)))
        total
        (map-indexed vector row)))
    0
    (map-indexed vector @atoms/game-map)))

(defn count-country-coastal-cells
  "Counts land cells with matching country-id that are adjacent to sea."
  [country-id]
  (let [game-map @atoms/game-map]
    (count (for [i (range (count game-map))
                 j (range (count (first game-map)))
                 :let [cell (get-in game-map [i j])]
                 :when (and (= :land (:type cell))
                            (= country-id (:country-id cell))
                            (some (fn [n]
                                    (= :sea (:type (get-in game-map n))))
                                  (get-neighbors [i j])))]
             true))))

(defn country-coastal-cells-explored?
  "Returns true if all coastal cells of the country are visible on computer-map."
  [country-id]
  (let [game-map @atoms/game-map
        comp-map @atoms/computer-map]
    (every? (fn [[i j]]
              (some? (get-in comp-map [i j])))
            (for [i (range (count game-map))
                  j (range (count (first game-map)))
                  :let [cell (get-in game-map [i j])]
                  :when (and (= :land (:type cell))
                             (= country-id (:country-id cell))
                             (some (fn [n] (= :sea (:type (get-in game-map n))))
                                   (get-neighbors [i j])))]
              [i j]))))

(defn count-country-coastal-armies
  "Counts computer armies on coastal cells (land adjacent to sea) with matching country-id."
  [country-id]
  (let [game-map @atoms/game-map]
    (count (for [i (range (count game-map))
                 j (range (count (first game-map)))
                 :let [cell (get-in game-map [i j])
                       unit (:contents cell)]
                 :when (and unit
                            (= :computer (:owner unit))
                            (= :army (:type unit))
                            (= country-id (:country-id unit))
                            (= :land (:type cell))
                            (some (fn [n]
                                    (= :sea (:type (get-in game-map n))))
                                  (get-neighbors [i j])))]
             true))))

(defn country-has-waiting-armies?
  "Returns true if country has coastal armies and all transports are full or unloading."
  [country-id]
  (let [game-map @atoms/game-map
        has-coastal-army (some (fn [[i row]]
                                 (some (fn [[j cell]]
                                         (let [unit (:contents cell)]
                                           (and unit
                                                (= :computer (:owner unit))
                                                (= :army (:type unit))
                                                (= country-id (:country-id unit))
                                                (= :land (:type cell))
                                                (some (fn [n]
                                                        (= :sea (:type (get-in game-map n))))
                                                      (get-neighbors [i j])))))
                                       (map-indexed vector row)))
                               (map-indexed vector game-map))
        transports (for [i (range (count game-map))
                         j (range (count (first game-map)))
                         :let [cell (get-in game-map [i j])
                               unit (:contents cell)]
                         :when (and unit
                                    (= :computer (:owner unit))
                                    (= :transport (:type unit))
                                    (= country-id (:country-id unit)))]
                     unit)]
    (and has-coastal-army
         (or (empty? transports)
             (every? (fn [t]
                       (or (>= (:army-count t 0) 6)
                           (= :unloading (:transport-mission t))))
                     transports)))))

(defn- count-all-computer-fighters
  "Counts all live computer fighters globally."
  []
  (count (for [i (range (count @atoms/game-map))
               j (range (count (first @atoms/game-map)))
               :let [cell (get-in @atoms/game-map [i j])
                     unit (:contents cell)]
               :when (and unit
                          (= :computer (:owner unit))
                          (= :fighter (:type unit)))]
           true)))

(defn- count-country-patrol-boats
  "Counts live computer patrol boats belonging to the given country-id."
  [country-id]
  (count (for [i (range (count @atoms/game-map))
               j (range (count (first @atoms/game-map)))
               :let [cell (get-in @atoms/game-map [i j])
                     unit (:contents cell)]
               :when (and unit
                          (= :computer (:owner unit))
                          (= :patrol-boat (:type unit))
                          (= country-id (:country-id unit)))]
           true)))

(defn country-city-producing?
  "Returns true if any other computer city in this country is already producing the given unit type."
  [city-pos country-id unit-type]
  (some (fn [[coords prod]]
          (and (map? prod)
               (= unit-type (:item prod))
               (not= coords city-pos)
               (let [cell (get-in @atoms/game-map coords)]
                 (and (= :city (:type cell))
                      (= :computer (:city-status cell))
                      (= country-id (:country-id cell))))))
        @atoms/production))

(defn country-city-producing-armies?
  "Returns true if any other computer city in this country is already producing armies."
  [city-pos country-id]
  (country-city-producing? city-pos country-id :army))

(defn- country-city-producing-transports?
  "Returns true if any other computer city in this country is already producing transports."
  [city-pos country-id]
  (country-city-producing? city-pos country-id :transport))

(defn- country-city-producing-destroyers?
  "Returns true if any other computer city in this country is already producing destroyers."
  [city-pos country-id]
  (country-city-producing? city-pos country-id :destroyer))

(defn- country-has-unadopted-transport?
  "Returns true if the country has a transport without an escort destroyer."
  [country-id]
  (some (fn [[i row]]
          (some (fn [[j cell]]
                  (let [unit (:contents cell)]
                    (and unit
                         (= :transport (:type unit))
                         (= :computer (:owner unit))
                         (= country-id (:country-id unit))
                         (nil? (:escort-destroyer-id unit)))))
                (map-indexed vector row)))
        (map-indexed vector @atoms/game-map)))

(defn has-unoccupied-coastal-cells?
  "Returns true if the country has any coastal land cell with no unit on it."
  [country-id]
  (let [game-map @atoms/game-map]
    (boolean
      (some (fn [i]
              (some (fn [j]
                      (let [cell (get-in game-map [i j])]
                        (and (= :land (:type cell))
                             (= country-id (:country-id cell))
                             (nil? (:contents cell))
                             (some (fn [n] (= :sea (:type (get-in game-map n))))
                                   (get-neighbors [i j])))))
                    (range (count (first game-map)))))
            (range (count game-map))))))

(defn- country-has-other-coastal-city?
  "Returns true if the country has another coastal city besides city-pos."
  [city-pos country-id]
  (let [game-map @atoms/game-map]
    (some (fn [i]
            (some (fn [j]
                    (let [cell (get-in game-map [i j])]
                      (and (= :city (:type cell))
                           (= :computer (:city-status cell))
                           (= country-id (:country-id cell))
                           (not= city-pos [i j])
                           (city-is-coastal? [i j]))))
                  (range (count (first game-map)))))
          (range (count game-map)))))

(defn- should-rotate-transport?
  "Returns true if this city should skip transport production to let another city produce."
  [city-pos country-id]
  (and (= city-pos (get @atoms/last-transport-city country-id))
       (country-has-other-coastal-city? city-pos country-id)))

(defn- count-country-land-armies
  "Counts armies on land/city cells belonging to the given country-id.
   Excludes armies aboard transports."
  [country-id]
  (count (for [i (range (count @atoms/game-map))
               j (range (count (first @atoms/game-map)))
               :let [unit (get-in @atoms/game-map [i j :contents])]
               :when (and unit
                          (= :army (:type unit))
                          (= :computer (:owner unit))
                          (= country-id (:country-id unit)))]
           true)))

(defn- country-army-limit-reached?
  "Returns true if the country has at least 2/3 as many land armies as coastal land cells."
  [country-id]
  (let [coastal-cells (count-country-coastal-cells country-id)]
    (and (pos? coastal-cells)
         (>= (count-country-land-armies country-id) (* 2/3 coastal-cells)))))

(defn- decide-country-production
  "Per-country production priorities. Returns unit type or nil."
  [city-pos country-id coastal? unit-counts]
  (cond
    ;; 1. Transport: coastal, enough armies, waiting armies with all transports full/unloading
    (and coastal?
         (>= (count-country-armies country-id) config/armies-before-transport)
         (country-has-waiting-armies? country-id)
         (not (country-city-producing-transports? city-pos country-id))
         (not (should-rotate-transport? city-pos country-id)))
    (do (swap! atoms/last-transport-city assoc country-id city-pos)
        :transport)

    ;; 2. Army: unoccupied coastal cells exist and army limit not reached
    (and (has-unoccupied-coastal-cells? country-id)
         (not (country-army-limit-reached? country-id)))
    :army

    ;; 3. Patrol boat: < 4 per country, coastal
    (and coastal?
         (< (count-country-patrol-boats country-id) config/max-patrol-boats-per-country))
    :patrol-boat

    ;; 4. Destroyer: global cap, country has unadopted transport, no other city producing
    (and coastal?
         (< (get unit-counts :destroyer 0) (get unit-counts :transport 0))
         (country-has-unadopted-transport? country-id)
         (not (country-city-producing-destroyers? city-pos country-id)))
    :destroyer

    ;; 5. Fighter: total fighters < total computer cities
    (< (count-all-computer-fighters) (count-computer-cities))
    :fighter))

(defn- count-carrier-producers
  "Counts computer cities currently producing carriers."
  []
  (count (filter (fn [[_coords prod]]
                   (and (map? prod)
                        (= :carrier (:item prod))))
                 @atoms/production)))

(defn- decide-global-production
  "Global production priorities. Returns unit type. CC=5."
  [coastal? unit-counts]
  (cond
    ;; 5. Carrier: enough cities, under fleet cap, under producer cap, valid position
    (and coastal?
         (> (count-computer-cities) config/carrier-city-threshold)
         (< (get unit-counts :carrier 0) config/max-live-carriers)
         (< (count-carrier-producers) config/max-carrier-producers)
         (ship/find-carrier-position))
    :carrier

    ;; 6. Battleship: BB < carriers
    (and coastal?
         (< (get unit-counts :battleship 0)
            (get unit-counts :carrier 0)))
    :battleship

    ;; 7. Submarine: Sub < 2*carriers
    (and coastal?
         (< (get unit-counts :submarine 0)
            (* 2 (get unit-counts :carrier 0))))
    :submarine

    ;; 8. Satellite: enough cities, under cap
    (and (> (count-computer-cities) config/satellite-city-threshold)
         (< (get unit-counts :satellite 0) config/max-satellites))
    :satellite

    ;; 9. No production needed — city stays idle
    :else nil))

(defn- has-inland-computer-city?
  "Returns true if any computer city is inland (not coastal)."
  []
  (some (fn [i]
          (some (fn [j]
                  (let [cell (get-in @atoms/game-map [i j])]
                    (and (= :city (:type cell))
                         (= :computer (:city-status cell))
                         (not (city-is-coastal? [i j])))))
                (range (count (first @atoms/game-map)))))
        (range (count @atoms/game-map))))

(defn- decide-early-production
  "One-shot early production after first transport fully loaded.
   Patrol boat first (coastal city), then satellite (inland preferred).
   Returns unit type or nil."
  [city-pos coastal?]
  (when @atoms/transport-fully-loaded?
    (cond
      (and coastal? (not @atoms/early-patrol-boat-produced?))
      (do (reset! atoms/early-patrol-boat-produced? true)
          :patrol-boat)

      (and @atoms/early-patrol-boat-produced?
           (not @atoms/early-satellite-produced?))
      (cond
        (not coastal?)
        (do (reset! atoms/early-satellite-produced? true)
            :satellite)

        (not (has-inland-computer-city?))
        (do (reset! atoms/early-satellite-produced? true)
            :satellite)))))

(defn decide-production
  "Decide what a computer city should produce. Returns unit type keyword.
   Early one-shots first, then per-country, then global."
  [city-pos]
  (let [city-cell (get-in @atoms/game-map city-pos)
        country-id (:country-id city-cell)
        coastal? (city-is-coastal? city-pos)
        unit-counts (count-computer-units)]
    (or (decide-early-production city-pos coastal?)
        (when country-id
          (decide-country-production city-pos country-id coastal? unit-counts))
        (when country-id
          (decide-global-production coastal? unit-counts))
        (when-not (and country-id (country-army-limit-reached? country-id))
          :army))))

(defn process-computer-city
  "Processes a computer city - sets production if not already set."
  [pos]
  (let [current-production (get @atoms/production pos)]
    (when (nil? current-production)
      (when-let [unit-type (decide-production pos)]
        (production/set-city-production pos unit-type)))))
