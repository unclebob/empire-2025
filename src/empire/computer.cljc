(ns empire.computer
  (:require [empire.atoms :as atoms]
            [empire.combat :as combat]
            [empire.config :as config]
            [empire.movement.map-utils :as map-utils]
            [empire.pathfinding :as pathfinding]
            [empire.production :as production]
            [empire.units.dispatcher :as dispatcher]))

;; Forward declarations for army-transport coordination and transport processing
(declare find-adjacent-loading-transport find-loading-transport army-should-board-transport? process-transport)

(defn- get-neighbors
  "Returns valid neighbor coordinates for a position."
  [pos]
  (map-utils/get-matching-neighbors pos @atoms/game-map map-utils/neighbor-offsets
                                    some?))

(defn- attackable-target?
  "Returns true if the cell contains an attackable target for the computer."
  [cell]
  (or (and (= (:type cell) :city)
           (#{:player :free} (:city-status cell)))
      (and (:contents cell)
           (= (:owner (:contents cell)) :player))))

(defn- find-adjacent-army-target
  "Finds an adjacent attackable target for an army (must be on land/city).
   Uses computer-map to respect fog of war. Returns coords or nil."
  [pos]
  (first (filter (fn [neighbor]
                   (let [cell (get-in @atoms/computer-map neighbor)]
                     (and (#{:land :city} (:type cell))
                          (attackable-target? cell))))
                 (get-neighbors pos))))

(defn- find-adjacent-target
  "Finds an adjacent attackable target.
   Uses computer-map to respect fog of war. Returns coords or nil."
  [pos]
  (first (filter (fn [neighbor]
                   (attackable-target? (get-in @atoms/computer-map neighbor)))
                 (get-neighbors pos))))

(defn- find-adjacent-ship-target
  "Finds an adjacent attackable target for a ship (must be on sea).
   Ships can only attack player units on sea cells, not cities or land units.
   Uses computer-map to respect fog of war. Returns coords or nil."
  [pos]
  (first (filter (fn [neighbor]
                   (let [cell (get-in @atoms/computer-map neighbor)]
                     (and (= :sea (:type cell))
                          (:contents cell)
                          (= (:owner (:contents cell)) :player))))
                 (get-neighbors pos))))

(defn- can-army-move-to?
  "Returns true if an army can move to this cell (land only, not cities)."
  [cell]
  (and (= :land (:type cell))
       (or (nil? (:contents cell))
           (= (:owner (:contents cell)) :player))))

(defn- find-passable-neighbors
  "Returns neighbors an army can move to."
  [pos]
  (filter (fn [neighbor]
            (let [cell (get-in @atoms/game-map neighbor)]
              (and (can-army-move-to? cell)
                   (not (and (:contents cell)
                             (= (:owner (:contents cell)) :computer))))))
          (get-neighbors pos)))

(defn distance
  "Manhattan distance between two positions."
  [[x1 y1] [x2 y2]]
  (+ (Math/abs (- x2 x1)) (Math/abs (- y2 y1))))

(defn- find-visible-cities
  "Finds cities visible on computer-map matching the status predicate."
  [status-pred]
  (for [i (range (count @atoms/computer-map))
        j (range (count (first @atoms/computer-map)))
        :let [cell (get-in @atoms/computer-map [i j])]
        :when (and (= (:type cell) :city)
                   (status-pred (:city-status cell)))]
    [i j]))

(defn- move-toward
  "Returns the neighbor that moves closest to target."
  [pos target passable-neighbors]
  (when (seq passable-neighbors)
    (apply min-key #(distance % target) passable-neighbors)))

;; Threat Assessment Functions

(defn unit-threat
  "Returns threat value for a unit type.
   Higher values = more dangerous."
  [unit-type]
  (case unit-type
    :battleship 10
    :carrier 8
    :destroyer 6
    :submarine 5
    :fighter 4
    :patrol-boat 3
    :army 2
    :transport 1
    0))

(defn threat-level
  "Calculates threat level at position based on nearby enemy units.
   Checks all cells within radius 2 of position.
   Returns sum of threat values for nearby enemy units."
  [computer-map position]
  (let [radius 2
        [px py] position]
    (reduce + 0
            (for [dx (range (- radius) (inc radius))
                  dy (range (- radius) (inc radius))
                  :let [x (+ px dx)
                        y (+ py dy)
                        cell (get-in computer-map [x y])]
                  :when (and cell
                             (:contents cell)
                             (= (:owner (:contents cell)) :player))]
              (unit-threat (:type (:contents cell)))))))

(defn safe-moves
  "Filters moves to avoid high-threat areas when unit is damaged.
   Returns moves sorted by threat level (safest first).
   If unit is at full health, returns all moves unchanged."
  [computer-map _position unit possible-moves]
  (let [max-hits (dispatcher/hits (:type unit))
        current-hits (:hits unit max-hits)
        damaged? (< current-hits max-hits)]
    (if damaged?
      (sort-by #(threat-level computer-map %) possible-moves)
      possible-moves)))

(defn should-retreat?
  "Returns true if the unit should retreat rather than engage."
  [pos unit computer-map]
  (let [unit-type (:type unit)
        max-hits (dispatcher/hits unit-type)
        current-hits (:hits unit max-hits)
        threat (threat-level computer-map pos)]
    (or
      ;; Damaged and under threat
      (and (< current-hits max-hits) (> threat 3))
      ;; Transport carrying armies - always cautious
      (and (= unit-type :transport)
           (> (:army-count unit 0) 0)
           (> threat 5))
      ;; Severely damaged (< 50% health)
      (< current-hits (/ max-hits 2)))))

(defn- find-nearest-friendly-base
  "Finds the nearest computer-owned city."
  [pos _unit-type]
  (let [cities (find-visible-cities #{:computer})]
    (when (seq cities)
      (apply min-key #(distance pos %) cities))))

(defn retreat-move
  "Returns best retreat move toward nearest friendly city.
   Returns nil if no safe retreat available."
  [pos unit computer-map passable-moves]
  (when (seq passable-moves)
    (let [nearest-city (find-nearest-friendly-base pos (:type unit))]
      (when nearest-city
        (let [safe (safe-moves computer-map pos unit passable-moves)]
          (when (seq safe)
            ;; Pick move that's both safe and moves toward base
            (apply min-key #(+ (distance % nearest-city)
                               (* 2 (threat-level computer-map %)))
                   safe)))))))

(defn- move-toward-city-or-explore
  "Moves army toward nearest free/player city using A*, or explores."
  [pos passable]
  (let [free-cities (find-visible-cities #{:free})
        player-cities (find-visible-cities #{:player})]
    (cond
      (seq free-cities)
      (let [nearest (apply min-key #(distance pos %) free-cities)]
        (or (pathfinding/next-step pos nearest :army)
            (move-toward pos nearest passable)))

      (seq player-cities)
      (let [nearest (apply min-key #(distance pos %) player-cities)]
        (or (pathfinding/next-step pos nearest :army)
            (move-toward pos nearest passable)))

      :else
      (first passable))))

(defn decide-army-move
  "Decides where a computer army should move. Returns target coords or nil.
   Priority: 1) Attack adjacent target 2) Board adjacent transport 3) Retreat if damaged
   4) Move toward transport if should board 5) Move toward city 6) Explore."
  [pos]
  (let [cell (get-in @atoms/game-map pos)
        unit (:contents cell)
        adjacent-target (find-adjacent-army-target pos)
        adjacent-transport (find-adjacent-loading-transport pos)
        passable (find-passable-neighbors pos)]
    (cond
      ;; Attack adjacent target
      adjacent-target
      adjacent-target

      ;; Board adjacent transport if no land route to targets
      (and adjacent-transport (army-should-board-transport? pos))
      adjacent-transport

      ;; Retreat if damaged
      (should-retreat? pos unit @atoms/computer-map)
      (retreat-move pos unit @atoms/computer-map passable)

      ;; No valid land moves
      (empty? passable)
      nil

      ;; Move toward loading transport if should board
      (army-should-board-transport? pos)
      (when-let [transport (find-loading-transport)]
        (or (pathfinding/next-step pos transport :army)
            (move-toward-city-or-explore pos passable)))

      ;; Normal movement toward city or explore
      :else
      (move-toward-city-or-explore pos passable))))

(defn- can-ship-move-to?
  "Returns true if a ship can move to this cell."
  [cell]
  (and (= (:type cell) :sea)
       (or (nil? (:contents cell))
           (= (:owner (:contents cell)) :player))))

(defn- find-passable-ship-neighbors
  "Returns neighbors a ship can move to."
  [pos]
  (filter (fn [neighbor]
            (let [cell (get-in @atoms/game-map neighbor)]
              (and (can-ship-move-to? cell)
                   (not (and (:contents cell)
                             (= (:owner (:contents cell)) :computer))))))
          (get-neighbors pos)))

(defn- find-visible-player-units
  "Finds player units visible on computer-map."
  []
  (for [i (range (count @atoms/computer-map))
        j (range (count (first @atoms/computer-map)))
        :let [cell (get-in @atoms/computer-map [i j])
              contents (:contents cell)]
        :when (and contents (= (:owner contents) :player))]
    [i j]))

(defn decide-ship-move
  "Decides where a computer ship should move. Returns target coords or nil.
   Priority: 1) Attack adjacent player ship 2) Retreat if damaged 3) Move toward player unit
   4) Patrol. Uses threat avoidance when damaged."
  [pos ship-type]
  (let [cell (get-in @atoms/game-map pos)
        unit (:contents cell)
        adjacent-target (find-adjacent-ship-target pos)
        passable (find-passable-ship-neighbors pos)]
    (cond
      ;; Attack adjacent target
      adjacent-target
      adjacent-target

      ;; Check if should retreat
      (should-retreat? pos unit @atoms/computer-map)
      (retreat-move pos unit @atoms/computer-map passable)

      ;; No valid moves
      (empty? passable)
      nil

      ;; Move toward nearest player unit with threat awareness
      :else
      (let [player-units (find-visible-player-units)
            safe-passable (safe-moves @atoms/computer-map pos unit passable)]
        (if (seq player-units)
          (let [nearest (apply min-key #(distance pos %) player-units)]
            (or (pathfinding/next-step pos nearest ship-type)
                (move-toward pos nearest safe-passable)))
          ;; Patrol - pick safe random neighbor
          (if (seq safe-passable)
            (rand-nth safe-passable)
            (rand-nth passable)))))))

(defn- can-fighter-move-to?
  "Returns true if a fighter can move to this cell (fighters can fly anywhere)."
  [cell]
  (and cell
       (or (nil? (:contents cell))
           (= (:owner (:contents cell)) :player))))

(defn- find-passable-fighter-neighbors
  "Returns neighbors a fighter can move to."
  [pos]
  (filter (fn [neighbor]
            (let [cell (get-in @atoms/game-map neighbor)]
              (and (can-fighter-move-to? cell)
                   (not (and (:contents cell)
                             (= (:owner (:contents cell)) :computer))))))
          (get-neighbors pos)))

(defn- find-nearest-friendly-city
  "Finds the nearest computer-owned city."
  [pos]
  (let [cities (find-visible-cities #{:computer})]
    (when (seq cities)
      (apply min-key #(distance pos %) cities))))

(defn decide-fighter-move
  "Decides where a computer fighter should move. Returns target coords or nil.
   Priority: 1) Attack adjacent target 2) Return to base if low fuel 3) Explore.
   Uses A* pathfinding for better navigation."
  [pos fuel]
  (let [adjacent-target (find-adjacent-target pos)
        passable (find-passable-fighter-neighbors pos)
        nearest-city (find-nearest-friendly-city pos)
        dist-to-city (when nearest-city (distance pos nearest-city))]
    (cond
      ;; Attack adjacent target
      adjacent-target
      adjacent-target

      ;; No valid moves
      (empty? passable)
      nil

      ;; Return to base if fuel is low (fuel <= distance to city + 2 buffer for safety)
      (and nearest-city dist-to-city (<= fuel (+ dist-to-city 2)))
      (or (pathfinding/next-step pos nearest-city :fighter)
          (move-toward pos nearest-city passable))

      ;; Explore - move toward unexplored or random
      :else
      (first passable))))

(defn- move-unit-to
  "Moves a unit from from-pos to to-pos. Returns to-pos."
  [from-pos to-pos]
  (let [from-cell (get-in @atoms/game-map from-pos)
        unit (:contents from-cell)]
    (swap! atoms/game-map assoc-in from-pos (dissoc from-cell :contents))
    (swap! atoms/game-map assoc-in (conj to-pos :contents) unit)
    to-pos))

(defn- attempt-conquest-computer
  "Computer army attempts to conquer a city. Returns new position or nil if army died."
  [army-pos city-pos]
  (let [army-cell (get-in @atoms/game-map army-pos)
        city-cell (get-in @atoms/game-map city-pos)]
    (if (< (rand) 0.5)
      ;; Success - conquer the city, army dies
      (do
        (swap! atoms/game-map assoc-in army-pos (dissoc army-cell :contents))
        (swap! atoms/game-map assoc-in city-pos (assoc city-cell :city-status :computer))
        nil)
      ;; Failure - army dies
      (do
        (swap! atoms/game-map assoc-in army-pos (dissoc army-cell :contents))
        nil))))

(defn- process-army [pos]
  (when-let [target (decide-army-move pos)]
    (let [target-cell (get-in @atoms/game-map target)]
      (cond
        ;; Attack player unit
        (and (:contents target-cell)
             (= (:owner (:contents target-cell)) :player))
        (combat/attempt-attack pos target)

        ;; Attack hostile city (player or free)
        (and (= (:type target-cell) :city)
             (#{:player :free} (:city-status target-cell)))
        (attempt-conquest-computer pos target)

        ;; Normal move
        :else
        (move-unit-to pos target))))
  nil)

(defn- process-ship [pos ship-type]
  (when-let [target (decide-ship-move pos ship-type)]
    (let [target-cell (get-in @atoms/game-map target)]
      (if (and (:contents target-cell)
               (= (:owner (:contents target-cell)) :player))
        ;; Attack player unit
        (combat/attempt-attack pos target)
        ;; Normal move
        (move-unit-to pos target))))
  nil)

(defn- process-fighter [pos unit]
  (let [fuel (:fuel unit 20)]
    (when-let [target (decide-fighter-move pos fuel)]
      (let [target-cell (get-in @atoms/game-map target)]
        (cond
          ;; Attack player unit
          (and (:contents target-cell)
               (= (:owner (:contents target-cell)) :player))
          (combat/attempt-attack pos target)

          ;; Land at friendly city
          (and (= (:type target-cell) :city)
               (= (:city-status target-cell) :computer))
          (do
            ;; Remove fighter from current position
            (swap! atoms/game-map update-in pos dissoc :contents)
            ;; Add to city airport
            (swap! atoms/game-map update-in [target :fighter-count] (fnil inc 0)))

          ;; Normal move - consume fuel
          :else
          (do
            (move-unit-to pos target)
            (swap! atoms/game-map update-in (conj target :contents :fuel) dec))))))
  nil)

(defn process-computer-unit
  "Processes a single computer unit's turn. Returns nil when done."
  [pos]
  (let [cell (get-in @atoms/game-map pos)
        unit (:contents cell)]
    (when (and unit (= (:owner unit) :computer))
      (case (:type unit)
        :army (process-army pos)
        :fighter (process-fighter pos unit)
        :transport (process-transport pos)
        (:destroyer :submarine :patrol-boat :carrier :battleship)
        (process-ship pos (:type unit))
        ;; Satellite - no processing needed
        nil))))

;; Smart Production Functions

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

(defn- need-transports?
  "Returns true if we need more transports for invasion."
  [unit-counts]
  (let [armies (get unit-counts :army 0)
        transports (get unit-counts :transport 0)]
    (and (> armies 3)
         (< transports (max 1 (quot armies 4))))))

(defn- need-fighters?
  "Returns true if we need air support.
   Only request fighters when we have some ground forces to support."
  [unit-counts]
  (let [fighters (get unit-counts :fighter 0)
        armies (get unit-counts :army 0)]
    (and (< fighters 2)
         (>= armies 3))))

(defn- need-warships?
  "Returns true if we need naval combat vessels."
  [unit-counts]
  (let [destroyers (get unit-counts :destroyer 0)
        patrol-boats (get unit-counts :patrol-boat 0)]
    (< (+ destroyers patrol-boats) 2)))

(defn decide-production
  "Decides what a computer city should produce based on strategic needs."
  [city-pos]
  (let [unit-counts (count-computer-units)
        coastal? (city-is-coastal? city-pos)]
    (cond
      ;; Coastal cities can build naval units
      (and coastal? (need-transports? unit-counts))
      :transport

      (and coastal? (need-warships? unit-counts))
      (rand-nth [:destroyer :patrol-boat])

      (need-fighters? unit-counts)
      :fighter

      ;; Default to armies
      :else
      :army)))

(defn process-computer-city
  "Processes a computer city. Sets production if none exists."
  [pos]
  (when-not (@atoms/production pos)
    (let [unit-type (decide-production pos)]
      (production/set-city-production pos unit-type))))

;; Transport Operations

(defn find-loading-dock
  "Finds nearest sea position adjacent to a coastal computer city for loading."
  [transport-pos]
  (let [coastal-cities (filter city-is-coastal? (find-visible-cities #{:computer}))
        ;; Get sea positions adjacent to each coastal city
        dock-positions (for [city coastal-cities
                              neighbor (get-neighbors city)
                              :let [cell (get-in @atoms/game-map neighbor)]
                              :when (= :sea (:type cell))]
                          neighbor)]
    (when (seq dock-positions)
      (apply min-key #(distance transport-pos %) dock-positions))))

(defn find-invasion-target
  "Finds best shore position near enemy/free city for invasion.
   Returns sea cell adjacent to land near target city, or nil."
  []
  (let [target-cities (concat (find-visible-cities #{:free})
                               (find-visible-cities #{:player}))]
    (when (seq target-cities)
      ;; Find shore tiles (sea adjacent to land) near target cities
      (let [shores (for [city target-cities
                         neighbor (get-neighbors city)
                         :let [cell (get-in @atoms/game-map neighbor)]
                         :when (= :sea (:type cell))]
                     neighbor)]
        (first shores)))))

(defn find-disembark-target
  "Finds adjacent empty land cell for army disembarkation."
  [transport-pos]
  (first (filter (fn [neighbor]
                   (let [cell (get-in @atoms/game-map neighbor)]
                     (and (= :land (:type cell))
                          (nil? (:contents cell)))))
                 (get-neighbors transport-pos))))

(defn- adjacent-to-land?
  "Returns true if position has adjacent land cell."
  [pos]
  (some (fn [neighbor]
          (#{:land :city} (:type (get-in @atoms/game-map neighbor))))
        (get-neighbors pos)))

(defn- set-transport-mission
  "Sets transport mission state."
  [pos mission target]
  (swap! atoms/game-map update-in (conj pos :contents)
         assoc :transport-mission mission :transport-target target))

(defn- load-adjacent-army
  "Loads an adjacent computer army onto the transport. Returns true if loaded."
  [transport-pos]
  (let [adjacent-army (first (filter (fn [neighbor]
                                        (let [cell (get-in @atoms/game-map neighbor)
                                              unit (:contents cell)]
                                          (and unit
                                               (= :army (:type unit))
                                               (= :computer (:owner unit)))))
                                      (get-neighbors transport-pos)))]
    (when adjacent-army
      ;; Remove army from map
      (swap! atoms/game-map update-in adjacent-army dissoc :contents)
      ;; Add to transport
      (swap! atoms/game-map update-in (conj transport-pos :contents :army-count)
             (fnil inc 0))
      true)))

(defn- disembark-army
  "Disembarks one army from transport to land position."
  [transport-pos land-pos]
  (let [transport (get-in @atoms/game-map (conj transport-pos :contents))]
    (when (pos? (:army-count transport 0))
      ;; Create army on land
      (swap! atoms/game-map assoc-in (conj land-pos :contents)
             {:type :army :owner :computer :hits 1})
      ;; Decrement transport army count
      (swap! atoms/game-map update-in (conj transport-pos :contents :army-count) dec))))

(defn- transport-move-idle
  "Handles transport in idle state - find dock or start invasion."
  [pos transport]
  (let [army-count (:army-count transport 0)]
    (if (pos? army-count)
      ;; Has armies but no mission - find invasion target
      (when-let [target (find-invasion-target)]
        (set-transport-mission pos :en-route target)
        (pathfinding/next-step pos target :transport))
      ;; Empty - find dock to load
      (when-let [dock (find-loading-dock pos)]
        (if (= pos dock)
          (do (set-transport-mission pos :loading nil) nil)
          (pathfinding/next-step pos dock :transport))))))

(defn- transport-move-loading
  "Handles transport in loading state - load armies, depart when ready."
  [pos transport]
  (let [army-count (:army-count transport 0)
        timeout (:loading-timeout transport 0)]
    (cond
      (>= army-count 6)
      (when-let [target (find-invasion-target)]
        (set-transport-mission pos :en-route target)
        nil)

      (> timeout 3)
      (when (pos? army-count)
        (when-let [target (find-invasion-target)]
          (set-transport-mission pos :en-route target))
        nil)

      :else
      (do
        (swap! atoms/game-map update-in (conj pos :contents :loading-timeout) (fnil inc 0))
        (load-adjacent-army pos)
        nil))))

(defn- transport-move-en-route
  "Handles transport in en-route state - navigate to target shore."
  [pos transport]
  (let [target (:transport-target transport)]
    (if (adjacent-to-land? pos)
      (do (set-transport-mission pos :unloading target) nil)
      (or (pathfinding/next-step pos target :transport)
          (first (find-passable-ship-neighbors pos))))))

(defn- transport-move-unloading
  "Handles transport in unloading state - disembark armies, return to base."
  [pos transport]
  (let [army-count (:army-count transport 0)]
    (if (zero? army-count)
      (do (set-transport-mission pos :idle nil)
          (when-let [base (find-loading-dock pos)]
            (pathfinding/next-step pos base :transport)))
      (when-let [land (find-disembark-target pos)]
        (disembark-army pos land)
        nil))))

(defn decide-transport-move
  "Decides transport movement based on mission state."
  [pos]
  (let [cell (get-in @atoms/game-map pos)
        transport (:contents cell)
        mission (:transport-mission transport :idle)]
    (case mission
      :idle (transport-move-idle pos transport)
      :loading (transport-move-loading pos transport)
      :en-route (transport-move-en-route pos transport)
      :unloading (transport-move-unloading pos transport)
      nil)))

(defn- process-transport
  "Processes a transport unit."
  [pos]
  (when-let [target (decide-transport-move pos)]
    (let [target-cell (get-in @atoms/game-map target)]
      (if (and (:contents target-cell)
               (= (:owner (:contents target-cell)) :player))
        ;; Attack player unit (unlikely but possible)
        (combat/attempt-attack pos target)
        ;; Normal move
        (move-unit-to pos target))))
  nil)

;; Army-Transport Coordination

(defn- find-loading-transport
  "Finds a transport in loading state that has room."
  []
  (first (for [i (range (count @atoms/game-map))
               j (range (count (first @atoms/game-map)))
               :let [cell (get-in @atoms/game-map [i j])
                     unit (:contents cell)]
               :when (and unit
                          (= :computer (:owner unit))
                          (= :transport (:type unit))
                          (= :loading (:transport-mission unit))
                          (< (:army-count unit 0) 6))]
           [i j])))

(defn- find-adjacent-loading-transport
  "Finds an adjacent loading transport with room."
  [pos]
  (first (filter (fn [neighbor]
                   (let [cell (get-in @atoms/game-map neighbor)
                         unit (:contents cell)]
                     (and unit
                          (= :computer (:owner unit))
                          (= :transport (:type unit))
                          (= :loading (:transport-mission unit))
                          (< (:army-count unit 0) 6))))
                 (get-neighbors pos))))

(defn- army-should-board-transport?
  "Returns true if army should move toward a loading transport.
   Only returns true if there are loading transports AND no land route to targets."
  [army-pos]
  (when (find-loading-transport)  ; Only check if there's a transport to board
    (let [free-cities (find-visible-cities #{:free})
          player-cities (find-visible-cities #{:player})
          all-targets (concat free-cities player-cities)]
      ;; Board transport if no cities reachable by land
      (and (seq all-targets)
           (not-any? #(pathfinding/next-step army-pos % :army) all-targets)))))
