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

(defn- coastal? [game-map pos]
  (some (fn [n] (= :sea (:type (get-in game-map n))))
        (map-utils/get-matching-neighbors pos game-map map-utils/neighbor-offsets some?)))

(defn- update-country [acc cid k f]
  (update-in acc [cid k] (fnil f 0)))

(defn- scan-cell-terrain [acc game-map comp-map i j cell]
  (let [cid (:country-id cell)]
    (if-not cid
      acc
      (let [cell-type (:type cell)
            is-coastal (and (#{:land :city} cell-type) (coastal? game-map [i j]))]
        (cond-> acc
          is-coastal
          (update-country cid :coastal-cell-count inc)
          (and is-coastal (= :land cell-type) (nil? (:contents cell)))
          (assoc-in [cid :has-unoccupied-coastal-cells?] true)
          (and is-coastal (not (some? (get-in comp-map [i j]))))
          (assoc-in [cid :has-unexplored-coastal?] true)
          (and (= :city cell-type) (= :computer (:city-status cell)) is-coastal)
          (update-in [cid :coastal-city-positions] (fnil conj #{}) [i j]))))))

(defn- scan-cell-unit [acc game-map i j cell]
  (let [unit (:contents cell)
        ucid (:country-id unit)]
    (if-not (and unit (= :computer (:owner unit)) ucid)
      acc
      (let [cell-type (:type cell)
            unit-type (:type unit)
            is-coastal (and (#{:land :city} cell-type) (coastal? game-map [i j]))]
        (cond-> acc
          (and is-coastal (= :land cell-type) (= :army unit-type))
          (assoc-in [ucid :has-coastal-army?] true)
          (= :army unit-type)
          (update-country ucid :army-count inc)
          (and (= :army unit-type) (#{:land :city} cell-type))
          (update-country ucid :land-army-count inc)
          (= :transport unit-type)
          (-> (update-country ucid :army-count #(+ % (get unit :army-count 0)))
              (update-in [ucid :transports] (fnil conj []) unit))
          (= :patrol-boat unit-type)
          (update-country ucid :patrol-boat-count inc))))))

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

(defn rebuild-country-stats!
  "Single scan of game-map building per-country stats cache."
  []
  (let [game-map @atoms/game-map
        comp-map @atoms/computer-map
        rows (count (first game-map))
        cols (count game-map)
        raw (reduce (fn [acc i]
                      (reduce (fn [acc j]
                                (scan-cell acc game-map comp-map i j))
                              acc (range rows)))
                    {} (range cols))]
    (reset! atoms/country-stats (derive-stats raw))))

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


(defn count-country-armies
  "Counts live armies belonging to the given country-id,
   including armies aboard transports of the same country."
  [country-id]
  (get-in @atoms/country-stats [country-id :army-count] 0))

(defn count-country-coastal-cells
  "Counts land and city cells with matching country-id that are adjacent to sea."
  [country-id]
  (get-in @atoms/country-stats [country-id :coastal-cell-count] 0))

(defn country-coastal-cells-explored?
  "Returns true if all coastal cells of the country are visible on computer-map."
  [country-id]
  (get-in @atoms/country-stats [country-id :coastal-explored?] true))

(defn country-has-waiting-armies?
  "Returns true if country has coastal armies and all transports are full or unloading."
  [country-id]
  (boolean (get-in @atoms/country-stats [country-id :has-waiting-armies?])))

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
  (get-in @atoms/country-stats [country-id :patrol-boat-count] 0))

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

(defn- country-city-producing-destroyers?
  "Returns true if any other computer city in this country is already producing destroyers."
  [city-pos country-id]
  (country-city-producing? city-pos country-id :destroyer))

(defn- country-has-unadopted-transport?
  "Returns true if the country has a transport without an escort destroyer."
  [country-id]
  (boolean (get-in @atoms/country-stats [country-id :has-unadopted-transport?])))

(defn has-unoccupied-coastal-cells?
  "Returns true if the country has any coastal land cell with no unit on it."
  [country-id]
  (boolean (get-in @atoms/country-stats [country-id :has-unoccupied-coastal-cells?])))

(defn- country-has-other-coastal-city?
  "Returns true if the country has another coastal city besides city-pos."
  [city-pos country-id]
  (let [positions (get-in @atoms/country-stats [country-id :coastal-city-positions] #{})]
    (some #(not= city-pos %) positions)))

(defn- should-rotate-transport?
  "Returns true if this city should skip transport production to let another city produce."
  [city-pos country-id]
  (and (= city-pos (get @atoms/last-transport-city country-id))
       (country-has-other-coastal-city? city-pos country-id)))

(defn- count-country-land-armies
  "Counts armies on land/city cells belonging to the given country-id.
   Excludes armies aboard transports."
  [country-id]
  (get-in @atoms/country-stats [country-id :land-army-count] 0))

(defn- country-army-limit-reached?
  "Returns true if the country has at least 2/3 as many land armies as coastal land cells."
  [country-id]
  (boolean (get-in @atoms/country-stats [country-id :army-limit-reached?])))

(defn- decide-country-production
  "Per-country production priorities. Returns unit type or nil."
  [city-pos country-id coastal? unit-counts]
  (cond
    ;; 1. Transport: coastal, enough armies, waiting armies with all transports full/unloading
    (and coastal?
         (>= (count-country-armies country-id) config/armies-before-transport)
         (country-has-waiting-armies? country-id)
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
