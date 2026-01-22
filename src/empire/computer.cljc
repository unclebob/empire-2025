(ns empire.computer
  (:require [empire.atoms :as atoms]
            [empire.combat :as combat]
            [empire.config :as config]
            [empire.movement.map-utils :as map-utils]
            [empire.movement.visibility :as visibility]
            [empire.pathfinding :as pathfinding]
            [empire.production :as production]
            [empire.units.dispatcher :as dispatcher]))

;; Forward declarations for army-transport coordination and transport processing
(declare find-adjacent-loading-transport find-loading-transport army-should-board-transport? process-transport direct-armies-to-beach)

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

(defn adjacent-to-computer-unexplored?
  "Returns true if the position has an adjacent unexplored cell on computer-map."
  [pos]
  (map-utils/any-neighbor-matches? pos @atoms/computer-map map-utils/neighbor-offsets
                                   nil?))

(defn- get-explore-moves
  "Returns passable moves that are adjacent to unexplored cells."
  [passable]
  (filter adjacent-to-computer-unexplored? passable))

(defn- move-toward-city-or-explore
  "Moves army toward nearest free/player city using A*, or explores.
   When exploring, prefers cells adjacent to unexplored areas."
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
      ;; Explore: prefer moves adjacent to unexplored areas
      (let [explore-moves (get-explore-moves passable)]
        (if (seq explore-moves)
          (rand-nth explore-moves)
          (when (seq passable)
            (rand-nth passable)))))))

(defn decide-army-move
  "Decides where a computer army should move. Returns target coords or nil.
   Priority: 1) Attack adjacent target 2) Board adjacent transport if recruited or no land route
   3) Retreat if damaged 4) Follow directed target 5) Move toward transport if should board
   6) Move toward city 7) Explore."
  [pos]
  (let [cell (get-in @atoms/game-map pos)
        unit (:contents cell)
        adjacent-target (find-adjacent-army-target pos)
        adjacent-transport (find-adjacent-loading-transport pos)
        passable (find-passable-neighbors pos)
        directed-target (:target unit)]
    (cond
      ;; Attack adjacent target
      adjacent-target
      adjacent-target

      ;; Board adjacent transport if recruited (has target) or no land route to cities
      (and adjacent-transport (or directed-target (army-should-board-transport? pos)))
      adjacent-transport

      ;; Retreat if damaged
      (should-retreat? pos unit @atoms/computer-map)
      (retreat-move pos unit @atoms/computer-map passable)

      ;; No valid land moves
      (empty? passable)
      nil

      ;; Follow directed target (from transport recruitment)
      directed-target
      (or (pathfinding/next-step pos directed-target :army)
          (move-toward pos directed-target passable))

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
        (visibility/update-cell-visibility army-pos :computer)
        (visibility/update-cell-visibility city-pos :computer)
        nil)
      ;; Failure - army dies
      (do
        (swap! atoms/game-map assoc-in army-pos (dissoc army-cell :contents))
        (visibility/update-cell-visibility army-pos :computer)
        nil))))

(defn- board-transport
  "Loads army onto transport. Removes army from pos, increments transport army count."
  [army-pos transport-pos]
  (swap! atoms/game-map update-in army-pos dissoc :contents)
  (swap! atoms/game-map update-in (conj transport-pos :contents :army-count) (fnil inc 0)))

(defn- process-army [pos]
  (when-let [target (decide-army-move pos)]
    (let [target-cell (get-in @atoms/game-map target)
          target-unit (:contents target-cell)]
      (cond
        ;; Attack player unit
        (and target-unit (= (:owner target-unit) :player))
        (combat/attempt-attack pos target)

        ;; Attack hostile city (player or free)
        (and (= (:type target-cell) :city)
             (#{:player :free} (:city-status target-cell)))
        (attempt-conquest-computer pos target)

        ;; Board friendly transport
        (and target-unit
             (= :computer (:owner target-unit))
             (= :transport (:type target-unit)))
        (board-transport pos target)

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
            (swap! atoms/game-map update-in (conj target :fighter-count) (fnil inc 0)))

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
  "Returns true if we need more transports for invasion.
   Build first transport after 6 armies, then another after each 6 more."
  [unit-counts]
  (let [armies (get unit-counts :army 0)
        transports (get unit-counts :transport 0)]
    (>= armies (* 6 (inc transports)))))

(defn- need-fighters?
  "Returns true if we need air support.
   Request fighters after we have 6+ armies (past early buildup)."
  [unit-counts]
  (let [fighters (get unit-counts :fighter 0)
        armies (get unit-counts :army 0)]
    (and (>= armies 6)
         (< fighters 2))))

(defn- need-warships?
  "Returns true if we need naval combat vessels.
   Only request warships when we have enough armies for existing transports
   (i.e., at transport capacity, not still building toward next transport)."
  [unit-counts]
  (let [destroyers (get unit-counts :destroyer 0)
        patrol-boats (get unit-counts :patrol-boat 0)
        transports (get unit-counts :transport 0)
        armies (get unit-counts :army 0)]
    (and (pos? transports)
         (= armies (* 6 transports))
         (< (+ destroyers patrol-boats) 2))))

(defn decide-production
  "Decides what a computer city should produce based on strategic needs.
   Priority: build 6 armies, then transport, then 6 more armies, repeat."
  [city-pos]
  (let [unit-counts (count-computer-units)
        coastal? (city-is-coastal? city-pos)]
    (cond
      ;; Coastal cities: build transport after every 6 armies
      (and coastal? (need-transports? unit-counts))
      :transport

      ;; Warships only after we have transport capacity
      (and coastal? (need-warships? unit-counts))
      (rand-nth [:destroyer :patrol-boat])

      ;; Fighters only after we have transport capacity
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

;; Transport Operations - Beach Helpers

(defn count-land-neighbors
  "Returns count of adjacent land or city cells for a position."
  [pos]
  (count (filter (fn [neighbor]
                   (#{:land :city} (:type (get-in @atoms/game-map neighbor))))
                 (get-neighbors pos))))

(defn- adjacent-to-city?
  "Returns true if position has an adjacent city cell."
  [pos]
  (some (fn [neighbor]
          (= :city (:type (get-in @atoms/game-map neighbor))))
        (get-neighbors pos)))

(defn- friendly-unit-at?
  "Returns true if position has a computer-owned unit."
  [pos]
  (let [cell (get-in @atoms/game-map pos)]
    (and (:contents cell)
         (= :computer (:owner (:contents cell))))))

(defn good-beach?
  "Returns true if pos is a sea cell with 3+ adjacent land cells and no adjacent cities.
   Does not check occupancy - use available-beach? for finding unoccupied beaches."
  [pos]
  (let [cell (get-in @atoms/game-map pos)]
    (and (= :sea (:type cell))
         (not (adjacent-to-city? pos))
         (>= (count-land-neighbors pos) 3))))

(defn- beach-reserved-by-other?
  "Returns true if beach is reserved by a transport with a different ID."
  [beach-pos transport-id]
  (let [reserved-by (get @atoms/reserved-beaches beach-pos)]
    (and reserved-by (not= reserved-by transport-id))))

(defn reserve-beach
  "Reserves a beach for a transport. Removes any previous reservation by this transport."
  [beach-pos transport-id]
  ;; First remove any existing reservation by this transport
  (swap! atoms/reserved-beaches
         (fn [reservations]
           (let [cleaned (into {} (remove (fn [[_ tid]] (= tid transport-id)) reservations))]
             (assoc cleaned beach-pos transport-id)))))

(defn release-beach-for-transport
  "Releases any beach reservation held by the given transport ID."
  [transport-id]
  (swap! atoms/reserved-beaches
         (fn [reservations]
           (into {} (remove (fn [[_ tid]] (= tid transport-id)) reservations)))))

(defn- available-beach?
  "Returns true if pos is a good beach that is not occupied by a friendly unit."
  ([pos]
   (available-beach? pos nil))
  ([pos transport-id]
   (and (good-beach? pos)
        (not (friendly-unit-at? pos))
        (not (beach-reserved-by-other? pos transport-id)))))

(defn completely-surrounded-by-sea?
  "Returns true if position has no adjacent land cells."
  [pos]
  (not-any? (fn [neighbor]
              (#{:land :city} (:type (get-in @atoms/game-map neighbor))))
            (get-neighbors pos)))

(defn directions-away-from-land
  "Returns sea neighbors where moving increases distance from all land cells."
  [pos]
  (let [current-land-cells (filter (fn [neighbor]
                                      (#{:land :city} (:type (get-in @atoms/game-map neighbor))))
                                    (get-neighbors pos))
        sea-neighbors (filter (fn [neighbor]
                                 (= :sea (:type (get-in @atoms/game-map neighbor))))
                               (get-neighbors pos))]
    (when (seq current-land-cells)
      (filter (fn [sea-neighbor]
                ;; Check that this sea cell is farther from all land than current pos
                (every? (fn [land-cell]
                          (> (distance sea-neighbor land-cell)
                             (distance pos land-cell)))
                        current-land-cells))
              sea-neighbors))))

(defn directions-along-wall
  "Returns sea neighbors that move parallel to a wall (map edge or land barrier).
   Used for reflecting off walls when departing."
  [pos]
  (let [[row col] pos
        height (count @atoms/game-map)
        width (count (first @atoms/game-map))
        sea-neighbors (filter (fn [neighbor]
                                (= :sea (:type (get-in @atoms/game-map neighbor))))
                              (get-neighbors pos))
        ;; Check if at map edge
        at-top-edge (= row 0)
        at-bottom-edge (= row (dec height))
        at-left-edge (= col 0)
        at-right-edge (= col (dec width))
        ;; Check if adjacent to land in each direction
        land-above (and (> row 0)
                        (#{:land :city} (:type (get-in @atoms/game-map [(dec row) col]))))
        land-below (and (< row (dec height))
                        (#{:land :city} (:type (get-in @atoms/game-map [(inc row) col]))))
        land-left (and (> col 0)
                       (#{:land :city} (:type (get-in @atoms/game-map [row (dec col)]))))
        land-right (and (< col (dec width))
                        (#{:land :city} (:type (get-in @atoms/game-map [row (inc col)]))))
        ;; Determine wall direction(s)
        wall-horizontal (or at-top-edge at-bottom-edge land-above land-below)
        wall-vertical (or at-left-edge at-right-edge land-left land-right)]
    (cond
      ;; Horizontal wall - move left/right (same row)
      (and wall-horizontal (not wall-vertical))
      (filter (fn [[r _]] (= r row)) sea-neighbors)

      ;; Vertical wall - move up/down (same column)
      (and wall-vertical (not wall-horizontal))
      (filter (fn [[_ c]] (= c col)) sea-neighbors)

      ;; Corner or no wall - no clear reflection direction
      :else
      [])))

(defn find-good-beach-near-city
  "Finds an available beach (good beach not occupied by friendly unit and not reserved by another
   transport) near a computer city. Looks at cells within 2 hops of the city since beaches can't
   be adjacent to cities. Pass transport-id to allow finding beaches not reserved by other transports."
  ([]
   (find-good-beach-near-city nil))
  ([transport-id]
   (let [coastal-cities (filter city-is-coastal? (find-visible-cities #{:computer}))]
     (first (for [city coastal-cities
                  neighbor (get-neighbors city)
                  neighbor2 (get-neighbors neighbor)
                  :when (available-beach? neighbor2 transport-id)]
              neighbor2)))))

(defn find-unloading-beach-for-invasion
  "Finds an available beach near enemy/free cities for invasion.
   Looks at cells within 2 hops of the city since beaches can't be adjacent to cities."
  []
  (let [target-cities (concat (find-visible-cities #{:free})
                               (find-visible-cities #{:player}))]
    (first (for [city target-cities
                 neighbor (get-neighbors city)
                 neighbor2 (get-neighbors neighbor)
                 :when (available-beach? neighbor2)]
             neighbor2))))

;; Transport Operations - Loading Dock

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

(defn adjacent-to-land?
  "Returns true if position has adjacent land cell."
  [pos]
  (some (fn [neighbor]
          (#{:land :city} (:type (get-in @atoms/game-map neighbor))))
        (get-neighbors pos)))

(defn- set-transport-mission
  "Sets transport mission state. Clears departing-visited when leaving departing state."
  ([pos mission target]
   (swap! atoms/game-map update-in (conj pos :contents)
          (fn [contents]
            (-> contents
                (assoc :transport-mission mission :transport-target target)
                (dissoc :departing-visited)))))
  ([pos mission target origin-beach]
   (swap! atoms/game-map update-in (conj pos :contents)
          (fn [contents]
            (-> contents
                (assoc :transport-mission mission :transport-target target :origin-beach origin-beach)
                (dissoc :departing-visited))))))

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

(defn disembark-army-to-explore
  "Disembarks one army from transport to land position.
   Army starts awake - computer movement logic handles exploration."
  [transport-pos land-pos]
  (let [transport (get-in @atoms/game-map (conj transport-pos :contents))]
    (when (pos? (:army-count transport 0))
      ;; Create army on land
      (swap! atoms/game-map assoc-in (conj land-pos :contents)
             {:type :army :owner :computer :hits 1 :mode :awake})
      ;; Decrement transport army count
      (swap! atoms/game-map update-in (conj transport-pos :contents :army-count) dec))))

(defn- transport-move-idle
  "Handles transport in idle state - find good beach or start invasion."
  [pos transport]
  (let [army-count (:army-count transport 0)
        transport-id (:transport-id transport)]
    (if (pos? army-count)
      ;; Has armies but no mission - find invasion target
      (when-let [target (find-unloading-beach-for-invasion)]
        (set-transport-mission pos :en-route target)
        (pathfinding/next-step pos target :transport))
      ;; Empty - find good beach to load at
      (when-let [beach (find-good-beach-near-city transport-id)]
        (when transport-id
          (reserve-beach beach transport-id))
        (if (= pos beach)
          (do (set-transport-mission pos :loading nil beach)
              (direct-armies-to-beach pos 6)
              nil)
          (do (set-transport-mission pos :seeking-beach beach beach)
              (pathfinding/next-step pos beach :transport)))))))

(defn- transport-move-seeking-beach
  "Handles transport in seeking-beach state - navigate to good beach."
  [pos transport]
  (let [target-beach (:origin-beach transport)]
    (if (and target-beach (= pos target-beach) (good-beach? pos))
      ;; Reached good beach - switch to loading and recruit armies
      (do (set-transport-mission pos :loading nil pos)
          (direct-armies-to-beach pos 6)
          nil)
      ;; Navigate toward beach
      (when target-beach
        (or (pathfinding/next-step pos target-beach :transport)
            (first (find-passable-ship-neighbors pos)))))))

;; Counts computer armies on the map with :target set to the given position.
(defn- armies-en-route-to [target-pos]
  (count (for [i (range (count @atoms/game-map))
               j (range (count (first @atoms/game-map)))
               :let [cell (get-in @atoms/game-map [i j])
                     unit (:contents cell)]
               :when (and unit
                          (= :army (:type unit))
                          (= :computer (:owner unit))
                          (= target-pos (:target unit)))]
           [i j])))

(defn- transport-move-loading
  "Handles transport in loading state - load armies, depart when ready.
   Departs when full (6 armies) or when all recruited armies have boarded."
  [pos transport]
  (let [army-count (:army-count transport 0)
        origin-beach (:origin-beach transport)
        en-route (armies-en-route-to pos)]
    (cond
      ;; Full - start departing
      (>= army-count 6)
      (do (set-transport-mission pos :departing nil origin-beach)
          nil)

      ;; All recruited armies accounted for (boarded or died) - depart if we have any
      (and (zero? en-route) (pos? army-count))
      (do (set-transport-mission pos :departing nil origin-beach)
          nil)

      ;; Still waiting for armies
      :else
      (do (load-adjacent-army pos)
          nil))))

(defn- add-to-departing-visited
  "Adds position to departing-visited set in transport state."
  [pos]
  (swap! atoms/game-map update-in (conj pos :contents :departing-visited)
         (fnil conj #{}) pos))

(defn- pick-unvisited-or-random
  "Picks from unvisited positions first, falling back to random from all."
  [unvisited all-positions]
  (cond
    (seq unvisited) (rand-nth unvisited)
    (seq all-positions) (rand-nth (vec all-positions))
    :else nil))

(defn- pick-departing-move
  "Picks the best move while departing, avoiding visited positions.
   Priority: away-from-land > wall-parallel > unvisited passable > any passable."
  [pos visited]
  (let [away-dirs (vec (remove visited (directions-away-from-land pos)))
        wall-dirs (vec (remove visited (directions-along-wall pos)))
        all-passable (find-passable-ship-neighbors pos)
        unvisited-passable (vec (remove visited all-passable))]
    (cond
      (seq away-dirs) (rand-nth away-dirs)
      (seq wall-dirs) (rand-nth wall-dirs)
      :else (pick-unvisited-or-random unvisited-passable all-passable))))

(defn- calculate-explore-direction
  "Calculates initial explore direction - away from origin beach, favoring unexplored areas."
  [pos origin-beach]
  (let [[pr pc] pos
        [or_ oc] origin-beach
        ;; Base direction: away from origin beach
        dr (cond (> pr or_) 1 (< pr or_) -1 :else 0)
        dc (cond (> pc oc) 1 (< pc oc) -1 :else 0)
        ;; If at same position as origin, pick random direction
        base-dir (if (and (zero? dr) (zero? dc))
                   (rand-nth [[1 0] [-1 0] [0 1] [0 -1] [1 1] [1 -1] [-1 1] [-1 -1]])
                   [dr dc])]
    base-dir))

(defn- transport-move-departing
  "Handles transport in departing state - move away from land until at sea.
   Tracks visited positions to prevent cycling in complex coastlines."
  [pos transport]
  (let [origin-beach (:origin-beach transport)
        visited (or (:departing-visited transport) #{})]
    (add-to-departing-visited pos)
    (if (completely-surrounded-by-sea? pos)
      ;; At sea - switch to exploring mode with a direction
      (let [explore-dir (calculate-explore-direction pos origin-beach)]
        (swap! atoms/game-map update-in (conj pos :contents)
               #(-> %
                    (assoc :transport-mission :exploring
                           :explore-direction explore-dir)
                    (dissoc :departing-visited)))
        nil)
      ;; Move away from land, avoiding visited positions
      (pick-departing-move pos visited))))

(defn- get-adjacent-land-cells
  "Returns adjacent land cells for a position."
  [pos]
  (filter (fn [neighbor]
            (#{:land :city} (:type (get-in @atoms/game-map neighbor))))
          (get-neighbors pos)))

(defn- land-path-to-beach?
  "Returns true if any adjacent land cell has a path to the origin beach's land.
   Uses army pathfinding to check land connectivity (same continent).
   If origin-beach is on land, checks path directly to it.
   If origin-beach is sea, checks path to any adjacent land cell."
  [pos origin-beach]
  (let [adjacent-land (get-adjacent-land-cells pos)
        origin-cell (get-in @atoms/game-map origin-beach)
        ;; If origin-beach is land, it represents the original continent directly
        ;; Otherwise, find land cells adjacent to origin beach (sea)
        origin-land-targets (if (#{:land :city} (:type origin-cell))
                              [origin-beach]
                              (get-adjacent-land-cells origin-beach))]
    (when (and (seq adjacent-land) (seq origin-land-targets))
      ;; Check if any adjacent land can reach any origin land via army path
      (some (fn [land-pos]
              (some (fn [origin-land-pos]
                      (or (= land-pos origin-land-pos)
                          (pathfinding/next-step land-pos origin-land-pos :army)))
                    origin-land-targets))
            adjacent-land))))

(defn- pick-new-explore-direction
  "Picks a new exploration direction, avoiding the current blocked direction.
   Prefers directions toward unexplored areas."
  [pos current-dir]
  (let [all-dirs [[1 0] [-1 0] [0 1] [0 -1] [1 1] [1 -1] [-1 1] [-1 -1]]
        ;; Remove current direction and its opposite
        opposite-dir [(- (first current-dir)) (- (second current-dir))]
        available-dirs (remove #{current-dir opposite-dir} all-dirs)
        ;; Filter to passable directions
        passable-dirs (filter (fn [[dr dc]]
                                (let [[r c] pos
                                      new-pos [(+ r dr) (+ c dc)]
                                      cell (get-in @atoms/game-map new-pos)]
                                  (and cell
                                       (= :sea (:type cell))
                                       (nil? (:contents cell)))))
                              available-dirs)]
    (if (seq passable-dirs)
      (rand-nth (vec passable-dirs))
      ;; If no passable dirs, try any direction
      (rand-nth (vec all-dirs)))))

(defn- transport-move-exploring
  "Handles transport in exploring state - move in set direction until land.
   When adjacent to land, uses pathfinding to determine if same continent.
   If same continent (path exists to origin) - pick new direction.
   If different continent (no path) - switch to coastline-searching."
  [pos transport]
  (let [origin-beach (:origin-beach transport)
        explore-dir (:explore-direction transport)
        near-land? (adjacent-to-land? pos)
        [dr dc] (or explore-dir [1 0])  ;; Default direction if not set
        [r c] pos
        target-pos [(+ r dr) (+ c dc)]
        target-cell (get-in @atoms/game-map target-pos)
        can-move-to-target? (and target-cell
                                  (= :sea (:type target-cell))
                                  (nil? (:contents target-cell)))]
    (cond
      ;; Adjacent to land - check if same continent
      near-land?
      (if (land-path-to-beach? pos origin-beach)
        ;; Same continent - pick new direction and continue exploring
        (do (swap! atoms/game-map update-in (conj pos :contents)
                   #(assoc % :explore-direction (pick-new-explore-direction pos explore-dir)))
            ;; Try to move in new direction
            (let [new-dir (pick-new-explore-direction pos explore-dir)
                  [nr nc] [(+ r (first new-dir)) (+ c (second new-dir))]
                  new-cell (get-in @atoms/game-map [nr nc])]
              (when (and new-cell
                         (= :sea (:type new-cell))
                         (nil? (:contents new-cell)))
                [nr nc])))
        ;; Different continent - switch to coastline-searching
        (do (swap! atoms/game-map update-in (conj pos :contents)
                   #(-> %
                        (assoc :transport-mission :coastline-searching
                               :coastline-visited #{})
                        (dissoc :explore-direction)))
            nil))

      ;; Can move in current direction
      can-move-to-target?
      target-pos

      ;; Blocked but not by land - pick new direction
      :else
      (let [new-dir (pick-new-explore-direction pos explore-dir)
            [nr nc] [(+ r (first new-dir)) (+ c (second new-dir))]
            new-cell (get-in @atoms/game-map [nr nc])]
        (swap! atoms/game-map update-in (conj pos :contents)
               #(assoc % :explore-direction new-dir))
        (when (and new-cell
                   (= :sea (:type new-cell))
                   (nil? (:contents new-cell)))
          [nr nc])))))

(defn- adjacent-to-player-city?
  "Returns true if position has an adjacent player-owned city."
  [pos]
  (some (fn [neighbor]
          (let [cell (get-in @atoms/game-map neighbor)]
            (and (= :city (:type cell))
                 (= :player (:city-status cell)))))
        (get-neighbors pos)))

(defn- find-empty-land-neighbor
  "Returns first adjacent empty land cell, or nil."
  [pos]
  (first (filter (fn [neighbor]
                   (let [cell (get-in @atoms/game-map neighbor)]
                     (and (= :land (:type cell))
                          (nil? (:contents cell)))))
                 (get-neighbors pos))))

(defn can-unload-at?
  "Returns true if computer transport can unload at pos:
   - 3+ adjacent land/city cells
   - No adjacent player (enemy) cities
   - At least one empty land cell to unload to."
  [pos]
  (and (>= (count-land-neighbors pos) 3)
       (not (adjacent-to-player-city? pos))
       (find-empty-land-neighbor pos)))

(defn- pick-coastline-move
  "Picks next coastline-hugging move. Prefers unvisited cells adjacent to land."
  [pos visited]
  (let [passable (find-passable-ship-neighbors pos)
        unvisited (vec (remove visited passable))
        ;; Prefer moves that stay adjacent to land (coastline hugging)
        coastal-unvisited (vec (filter adjacent-to-land? unvisited))
        coastal-all (vec (filter adjacent-to-land? passable))
        ;; Prefer moves toward unexplored areas
        unexplored-coastal (vec (filter adjacent-to-computer-unexplored? coastal-unvisited))]
    (cond
      (seq unexplored-coastal) (rand-nth unexplored-coastal)
      (seq coastal-unvisited) (rand-nth coastal-unvisited)
      ;; Prefer any unvisited move over revisiting coastal positions
      (seq unvisited) (rand-nth unvisited)
      ;; Only revisit coastal when no other options (may be stuck)
      (seq coastal-all) (rand-nth coastal-all)
      (seq passable) (rand-nth passable)
      :else nil)))

(defn- transport-move-coastline-searching
  "Handles transport in coastline-searching state - hug coastline looking for unloading beach.
   Stops immediately when a valid unloading position is found."
  [pos transport]
  (let [origin-beach (:origin-beach transport)
        visited (or (:coastline-visited transport) #{})]
    ;; Add current position to visited
    (swap! atoms/game-map update-in (conj pos :contents :coastline-visited) (fnil conj #{}) pos)
    (if (can-unload-at? pos)
      ;; Found valid unloading beach - switch to unloading immediately
      (do (swap! atoms/game-map update-in (conj pos :contents)
                 #(-> %
                      (assoc :transport-mission :unloading)
                      (dissoc :coastline-visited)))
          nil)
      ;; Continue coastline hugging
      (pick-coastline-move pos visited))))

(defn- transport-move-en-route
  "Handles transport in en-route state - navigate to good unloading beach."
  [pos transport]
  (let [target (:transport-target transport)
        origin-beach (:origin-beach transport)]
    (cond
      ;; Reached a good beach for unloading
      (and (adjacent-to-land? pos) (good-beach? pos))
      (do (set-transport-mission pos :unloading target origin-beach) nil)

      ;; Navigate toward target
      target
      (or (pathfinding/next-step pos target :transport)
          (first (find-passable-ship-neighbors pos)))

      ;; No target - find one
      :else
      (when-let [new-target (find-unloading-beach-for-invasion)]
        (set-transport-mission pos :en-route new-target origin-beach)
        (pathfinding/next-step pos new-target :transport)))))

(defn- find-all-disembark-targets
  "Returns all adjacent empty land cells for army disembarkation."
  [pos]
  (filter (fn [neighbor]
            (let [cell (get-in @atoms/game-map neighbor)]
              (and (= :land (:type cell))
                   (nil? (:contents cell)))))
          (get-neighbors pos)))

(defn- transport-move-unloading
  "Handles transport in unloading state - disembark as many armies as possible, return to origin.
   Unloads one army to each available empty land cell in a single round."
  [pos transport]
  (let [army-count (:army-count transport 0)
        origin-beach (:origin-beach transport)]
    (if (zero? army-count)
      ;; Done unloading - return to origin beach
      (do (set-transport-mission pos :returning nil origin-beach)
          (when origin-beach
            (pathfinding/next-step pos origin-beach :transport)))
      ;; Disembark as many armies as possible
      (let [targets (find-all-disembark-targets pos)
            armies-to-unload (min army-count (count targets))]
        (doseq [land (take armies-to-unload targets)]
          (disembark-army-to-explore pos land))
        ;; Check if all armies unloaded, switch to returning
        (let [remaining (:army-count (:contents (get-in @atoms/game-map pos)) 0)]
          (when (zero? remaining)
            (set-transport-mission pos :returning nil origin-beach)))
        nil))))

(defn- transport-move-returning
  "Handles transport in returning state - navigate back to origin beach."
  [pos transport]
  (let [origin-beach (:origin-beach transport)]
    (if (and origin-beach (= pos origin-beach))
      ;; Reached origin - switch to loading and recruit armies
      (do (swap! atoms/game-map update-in (conj pos :contents) dissoc :loading-timeout)
          (set-transport-mission pos :loading nil origin-beach)
          (direct-armies-to-beach pos 6)
          nil)
      ;; Navigate toward origin beach
      (when origin-beach
        (or (pathfinding/next-step pos origin-beach :transport)
            (first (find-passable-ship-neighbors pos)))))))

(defn decide-transport-move
  "Decides transport movement based on mission state."
  [pos]
  (let [cell (get-in @atoms/game-map pos)
        transport (:contents cell)
        mission (:transport-mission transport :idle)]
    (case mission
      :idle (transport-move-idle pos transport)
      :seeking-beach (transport-move-seeking-beach pos transport)
      :loading (transport-move-loading pos transport)
      :departing (transport-move-departing pos transport)
      :exploring (transport-move-exploring pos transport)
      :coastline-searching (transport-move-coastline-searching pos transport)
      :en-route (transport-move-en-route pos transport)
      :unloading (transport-move-unloading pos transport)
      :returning (transport-move-returning pos transport)
      nil)))

(defn- process-transport
  "Processes a transport unit. Avoids moving onto friendly units."
  [pos]
  (when-let [target (decide-transport-move pos)]
    (let [target-cell (get-in @atoms/game-map target)
          target-unit (:contents target-cell)]
      (cond
        ;; Attack player unit (unlikely but possible)
        (and target-unit (= (:owner target-unit) :player))
        (combat/attempt-attack pos target)

        ;; Skip if friendly unit present (don't overwrite)
        (and target-unit (= (:owner target-unit) :computer))
        nil

        ;; Normal move (empty cell)
        :else
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

;; Army-Transport Recruitment

(defn- land-neighbors
  "Returns land cell neighbors of a position."
  [pos]
  (filter (fn [neighbor]
            (#{:land :city} (:type (get-in @atoms/game-map neighbor))))
          (get-neighbors pos)))

(defn- can-army-reach-beach?
  "Returns true if army can reach the beach position.
   If beach is sea, checks if army can reach any adjacent land cell."
  [army-pos beach-pos]
  (let [beach-cell (get-in @atoms/game-map beach-pos)]
    (if (= :sea (:type beach-cell))
      ;; Beach is sea - check if army can reach any adjacent land
      (let [adjacent-land (land-neighbors beach-pos)]
        (some #(or (= army-pos %)
                   (pathfinding/next-step army-pos % :army))
              adjacent-land))
      ;; Beach is land - check direct path
      (or (= army-pos beach-pos)
          (pathfinding/next-step army-pos beach-pos :army)))))

(defn find-nearest-armies
  "Finds the n nearest computer armies that can reach the position.
   If position is sea, finds armies that can reach adjacent land cells."
  [pos n]
  (let [armies (for [i (range (count @atoms/game-map))
                     j (range (count (first @atoms/game-map)))
                     :let [cell (get-in @atoms/game-map [i j])
                           unit (:contents cell)]
                     :when (and unit
                                (= :army (:type unit))
                                (= :computer (:owner unit)))]
                 [i j])
        reachable (filter #(can-army-reach-beach? % pos) armies)]
    (->> reachable
         (sort-by #(distance pos %))
         (take n))))

(defn direct-armies-to-beach
  "Directs the n nearest computer armies to move toward the beach position.
   Sets mission :loading so armies display as black."
  [beach-pos n]
  (let [nearest (find-nearest-armies beach-pos n)]
    (doseq [army-pos nearest]
      (swap! atoms/game-map update-in (conj army-pos :contents)
             assoc :target beach-pos :mission :loading))))
