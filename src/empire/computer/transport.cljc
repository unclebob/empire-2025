(ns empire.computer.transport
  (:require [empire.atoms :as atoms]
            [empire.combat :as combat]
            [empire.computer.core :as core]
            [empire.computer.production :as production]
            [empire.computer.ship :as ship]
            [empire.pathfinding :as pathfinding]))

;; Beach Helpers

(defn count-land-neighbors
  "Returns count of adjacent land or city cells for a position."
  [pos]
  (count (filter (fn [neighbor]
                   (#{:land :city} (:type (get-in @atoms/game-map neighbor))))
                 (core/get-neighbors pos))))

(defn- adjacent-to-city?
  "Returns true if position has an adjacent city cell."
  [pos]
  (some (fn [neighbor]
          (= :city (:type (get-in @atoms/game-map neighbor))))
        (core/get-neighbors pos)))

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
            (core/get-neighbors pos)))

(defn directions-away-from-land
  "Returns sea neighbors where moving increases distance from all land cells."
  [pos]
  (let [current-land-cells (filter (fn [neighbor]
                                      (#{:land :city} (:type (get-in @atoms/game-map neighbor))))
                                    (core/get-neighbors pos))
        sea-neighbors (filter (fn [neighbor]
                                 (= :sea (:type (get-in @atoms/game-map neighbor))))
                               (core/get-neighbors pos))]
    (when (seq current-land-cells)
      (filter (fn [sea-neighbor]
                ;; Check that this sea cell is farther from all land than current pos
                (every? (fn [land-cell]
                          (> (core/distance sea-neighbor land-cell)
                             (core/distance pos land-cell)))
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
                              (core/get-neighbors pos))
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
   (let [coastal-cities (filter production/city-is-coastal? (core/find-visible-cities #{:computer}))]
     (first (for [city coastal-cities
                  neighbor (core/get-neighbors city)
                  neighbor2 (core/get-neighbors neighbor)
                  :when (available-beach? neighbor2 transport-id)]
              neighbor2)))))

(defn find-unloading-beach-for-invasion
  "Finds an available beach near enemy/free cities for invasion.
   Looks at cells within 2 hops of the city since beaches can't be adjacent to cities."
  []
  (let [target-cities (concat (core/find-visible-cities #{:free})
                               (core/find-visible-cities #{:player}))]
    (first (for [city target-cities
                 neighbor (core/get-neighbors city)
                 neighbor2 (core/get-neighbors neighbor)
                 :when (available-beach? neighbor2)]
             neighbor2))))

;; Loading Dock and Invasion Targets

(defn find-loading-dock
  "Finds nearest sea position adjacent to a coastal computer city for loading."
  [transport-pos]
  (let [coastal-cities (filter production/city-is-coastal? (core/find-visible-cities #{:computer}))
        ;; Get sea positions adjacent to each coastal city
        dock-positions (for [city coastal-cities
                              neighbor (core/get-neighbors city)
                              :let [cell (get-in @atoms/game-map neighbor)]
                              :when (= :sea (:type cell))]
                          neighbor)]
    (when (seq dock-positions)
      (apply min-key #(core/distance transport-pos %) dock-positions))))

(defn find-invasion-target
  "Finds best shore position near enemy/free city for invasion.
   Returns sea cell adjacent to land near target city, or nil."
  []
  (let [target-cities (concat (core/find-visible-cities #{:free})
                               (core/find-visible-cities #{:player}))]
    (when (seq target-cities)
      ;; Find shore tiles (sea adjacent to land) near target cities
      (let [shores (for [city target-cities
                         neighbor (core/get-neighbors city)
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
                 (core/get-neighbors transport-pos))))

(defn adjacent-to-land?
  "Returns true if position has adjacent land cell."
  [pos]
  (some (fn [neighbor]
          (#{:land :city} (:type (get-in @atoms/game-map neighbor))))
        (core/get-neighbors pos)))

;; Transport Mission State Helpers

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
                                      (core/get-neighbors transport-pos)))]
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

;; Army Recruitment

(defn- land-neighbors
  "Returns land cell neighbors of a position."
  [pos]
  (filter (fn [neighbor]
            (#{:land :city} (:type (get-in @atoms/game-map neighbor))))
          (core/get-neighbors pos)))

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
         (sort-by #(core/distance pos %))
         (take n))))

(defn direct-armies-to-beach
  "Directs the n nearest computer armies to move toward the beach position.
   Sets mission :loading so armies display as black."
  [beach-pos n]
  (let [nearest (find-nearest-armies beach-pos n)]
    (doseq [army-pos nearest]
      (swap! atoms/game-map update-in (conj army-pos :contents)
             assoc :target beach-pos :mission :loading))))

;; Armies En Route Counter

(defn- armies-en-route-to
  "Counts computer armies on the map with :target set to the given position."
  [target-pos]
  (count (for [i (range (count @atoms/game-map))
               j (range (count (first @atoms/game-map)))
               :let [cell (get-in @atoms/game-map [i j])
                     unit (:contents cell)]
               :when (and unit
                          (= :army (:type unit))
                          (= :computer (:owner unit))
                          (= target-pos (:target unit)))]
           [i j])))

;; Transport Movement State Machine

(defn- transport-move-idle
  "Handles transport in idle state - find good beach or start invasion.
   If stuck in a city with no available beach, marks city to stop transport
   production and sends transport to explore for beach on different continent."
  [pos transport]
  (let [army-count (:army-count transport 0)
        transport-id (:transport-id transport)]
    (if (pos? army-count)
      ;; Has armies but no mission - find invasion target
      (when-let [target (find-unloading-beach-for-invasion)]
        (set-transport-mission pos :en-route target)
        (pathfinding/next-step pos target :transport))
      ;; Empty - find good beach to load at
      (if-let [beach (find-good-beach-near-city transport-id)]
        (do
          (when transport-id
            (reserve-beach beach transport-id))
          (if (= pos beach)
            (do (set-transport-mission pos :loading nil beach)
                (direct-armies-to-beach pos 6)
                nil)
            (do (set-transport-mission pos :seeking-beach beach beach)
                (pathfinding/next-step pos beach :transport))))
        ;; No beach available - check if stuck in city
        (let [cell (get-in @atoms/game-map pos)]
          (when (= :city (:type cell))
            ;; Mark city to stop producing transports
            (swap! atoms/game-map assoc-in (conj pos :no-more-transports) true)
            ;; Send transport to explore for beach on different continent
            (set-transport-mission pos :departing nil pos)
            nil))))))

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
            (first (ship/find-passable-ship-neighbors pos)))))))

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
        all-passable (ship/find-passable-ship-neighbors pos)
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
          (core/get-neighbors pos)))

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
        (core/get-neighbors pos)))

(defn- find-empty-land-neighbor
  "Returns first adjacent empty land cell, or nil."
  [pos]
  (first (filter (fn [neighbor]
                   (let [cell (get-in @atoms/game-map neighbor)]
                     (and (= :land (:type cell))
                          (nil? (:contents cell)))))
                 (core/get-neighbors pos))))

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
  (let [passable (ship/find-passable-ship-neighbors pos)
        unvisited (vec (remove visited passable))
        ;; Prefer moves that stay adjacent to land (coastline hugging)
        coastal-unvisited (vec (filter adjacent-to-land? unvisited))
        coastal-all (vec (filter adjacent-to-land? passable))
        ;; Prefer moves toward unexplored areas
        unexplored-coastal (vec (filter core/adjacent-to-computer-unexplored? coastal-unvisited))]
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
          (first (ship/find-passable-ship-neighbors pos)))

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
          (core/get-neighbors pos)))

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
            (first (ship/find-passable-ship-neighbors pos)))))))

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

(defn process-transport
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
        (core/move-unit-to pos target))))
  nil)
