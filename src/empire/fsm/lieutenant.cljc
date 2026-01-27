(ns empire.fsm.lieutenant
  "Lieutenant FSM - operational commander for a base/territory.
   Controls cities, assigns missions to units, forms squads."
  (:require [empire.fsm.engine :as engine]
            [empire.fsm.context :as context]
            [empire.atoms :as atoms]
            [empire.movement.map-utils :as map-utils]))

(defn- has-unit-needs-orders?
  "Guard: check if there's a unit-needs-orders event in queue."
  [ctx]
  (context/has-event? ctx :unit-needs-orders))

(defn- has-any-event?
  "Guard: check if there's any event in queue."
  [ctx]
  (boolean (seq (:event-queue ctx))))

(defn- coastline-quota-met?
  "Guard: check if 2 coastline explorers are commissioned."
  [ctx]
  (>= (get (:entity ctx) :coastline-explorer-count 0) 2))

(defn- interior-quota-met?
  "Guard: check if 2 interior explorers are commissioned."
  [ctx]
  (>= (get (:entity ctx) :interior-explorer-count 0) 2))

(defn- transport-quota-met?
  "Guard: check if 6 waiting armies are recruited."
  [ctx]
  (>= (get (:entity ctx) :waiting-army-count 0) 6))

(def lieutenant-fsm
  "FSM transitions for the Lieutenant.
   Format: state-grouped with 3-tuples [guard-fn new-state action-fn]"
  [[:start-exploring-coastline
    [coastline-quota-met?   :start-exploring-interior (constantly nil)]
    [(constantly false)     :start-exploring-coastline identity]]
   [:start-exploring-interior
    [interior-quota-met?    :recruiting-for-transport (constantly nil)]
    [(constantly false)     :start-exploring-interior identity]]
   [:recruiting-for-transport
    [transport-quota-met?   :waiting-for-transport (constantly nil)]
    [(constantly false)     :recruiting-for-transport identity]]
   [:waiting-for-transport
    [(constantly false)     :waiting-for-transport identity]]])

(defn create-lieutenant
  "Create a new Lieutenant with the given name and assigned city."
  [name city-coords]
  {:fsm lieutenant-fsm
   :fsm-state :start-exploring-coastline
   :fsm-data {:mission-type :explore-conquer}
   :event-queue []
   :name name
   :cities [city-coords]
   :direct-reports []
   :free-cities-known []
   :beach-candidates []
   :known-coastal-cells #{}
   :known-landlocked-cells #{}
   :frontier-coastal-cells #{}
   :coastline-explorer-count 0
   :interior-explorer-count 0
   :waiting-army-count 0})

(defn- classify-cell
  "Returns :coastal if pos is land adjacent to sea, :landlocked if land not adjacent to sea, nil otherwise."
  [pos]
  (let [cell (get-in @atoms/game-map pos)]
    (when (or (= :land (:type cell))
              (= :city (:type cell)))
      (if (map-utils/adjacent-to-sea? pos atoms/game-map)
        :coastal
        :landlocked))))

(def ^:private city-visibility-radius
  "Visibility radius around a city (3x3 area)."
  1)

(defn- get-explored-cells-around
  "Returns a set of all explored (non-unexplored) land positions within visibility radius of the given position."
  [center-pos]
  (let [computer-map @atoms/computer-map
        game-map @atoms/game-map
        [cr cc] center-pos
        height (count game-map)
        width (count (first game-map))]
    (set
     (for [dr (range (- city-visibility-radius) (inc city-visibility-radius))
           dc (range (- city-visibility-radius) (inc city-visibility-radius))
           :let [r (+ cr dr)
                 c (+ cc dc)]
           :when (and (>= r 0) (< r height)
                      (>= c 0) (< c width))
           :let [comp-cell (get-in computer-map [r c])
                 game-cell (get-in game-map [r c])]
           :when (and comp-cell
                      (not= :unexplored (:type comp-cell))
                      (or (= :land (:type game-cell))
                          (= :city (:type game-cell))))]
       [r c]))))

(defn initialize-with-visible-cells
  "Initialize a Lieutenant with knowledge of visible cells around its city.
   Classifies explored land cells as coastal or landlocked."
  [lieutenant]
  (let [city-coords (first (:cities lieutenant))
        explored-cells (get-explored-cells-around city-coords)
        classified (map (fn [pos] [pos (classify-cell pos)]) explored-cells)
        coastal-cells (set (map first (filter #(= :coastal (second %)) classified)))
        landlocked-cells (set (map first (filter #(= :landlocked (second %)) classified)))]
    (-> lieutenant
        (update :known-coastal-cells into coastal-cells)
        (update :known-landlocked-cells into landlocked-cells))))

(defn city-name
  "Generate city name for the nth city (0-indexed) under this lieutenant."
  [lieutenant idx]
  (str (:name lieutenant) "-" (inc idx)))

(defn- create-explorer
  "Create an explorer unit record for a direct report."
  [unit-id coords mission-type]
  {:unit-id unit-id
   :coords coords
   :mission-type mission-type
   :fsm-state :exploring
   :status :active})

(defn- mission-for-counts
  "Returns the next mission type based on current counts, starting from a given phase.
   Phase 0 = coastline, 1 = interior, 2 = waiting, 3+ = nil (done)."
  [coastline-count interior-count waiting-count starting-phase]
  (cond
    (and (<= starting-phase 0) (< coastline-count 2)) :explore-coastline
    (and (<= starting-phase 1) (< interior-count 2)) :explore-interior
    (and (<= starting-phase 2) (< waiting-count 6)) :hurry-up-and-wait
    :else nil))

(def ^:private state->phase
  {:start-exploring-coastline 0
   :start-exploring-interior 1
   :recruiting-for-transport 2
   :waiting-for-transport 3})

(defn- determine-mission-type
  "Determine which mission to assign based on FSM state and counts.
   FSM state acts as a floor - we never regress to earlier phases.
   Within current phase, counts determine if we've met quota for transition."
  [lieutenant]
  (let [phase (get state->phase (:fsm-state lieutenant) 0)
        coastline-count (get lieutenant :coastline-explorer-count 0)
        interior-count (get lieutenant :interior-explorer-count 0)
        waiting-count (get lieutenant :waiting-army-count 0)]
    (mission-for-counts coastline-count interior-count waiting-count phase)))

(defn- increment-explorer-count
  "Increment the appropriate explorer count for the given mission type."
  [lieutenant mission-type]
  (case mission-type
    :explore-coastline (update lieutenant :coastline-explorer-count inc)
    :explore-interior  (update lieutenant :interior-explorer-count inc)
    :hurry-up-and-wait (update lieutenant :waiting-army-count inc)
    lieutenant))

(defn- decrement-explorer-count
  "Decrement the appropriate explorer count for the given mission type."
  [lieutenant mission-type]
  (case mission-type
    :explore-coastline (update lieutenant :coastline-explorer-count dec)
    :explore-interior  (update lieutenant :interior-explorer-count dec)
    :hurry-up-and-wait (update lieutenant :waiting-army-count dec)
    lieutenant))

(defn- handle-unit-needs-orders
  "Handle unit-needs-orders event: assign explorer mission based on FSM state.
   In :waiting-for-transport state, no mission is assigned (army stays awake)."
  [lieutenant event]
  (let [mission-type (determine-mission-type lieutenant)]
    (if (nil? mission-type)
      ;; In waiting-for-transport: don't assign mission, leave army awake
      lieutenant
      ;; Normal case: assign mission
      (let [unit-id (get-in event [:data :unit-id])
            coords (get-in event [:data :coords])
            explorer (create-explorer unit-id coords mission-type)]
        (-> lieutenant
            (update :direct-reports conj explorer)
            (increment-explorer-count mission-type))))))

(defn- handle-free-city-found
  "Handle free-city-found event: add to known list if not duplicate."
  [lieutenant event]
  (let [coords (get-in event [:data :coords])
        known (:free-cities-known lieutenant)]
    (if (some #(= % coords) known)
      lieutenant
      (update lieutenant :free-cities-known conj coords))))

(defn- handle-city-conquered
  "Handle city-conquered event: add to cities, remove from free-cities-known."
  [lieutenant event]
  (let [coords (get-in event [:data :coords])]
    (-> lieutenant
        (update :cities conj coords)
        (update :free-cities-known #(vec (remove #{coords} %))))))

(defn- handle-coastline-mapped
  "Handle coastline-mapped event: add beach candidate if not duplicate."
  [lieutenant event]
  (let [coords (get-in event [:data :coords])
        known (:beach-candidates lieutenant)]
    (if (some #(= % coords) known)
      lieutenant
      (update lieutenant :beach-candidates conj coords))))

(defn- handle-cells-discovered
  "Handle cells-discovered event: update known cell sets based on terrain type."
  [lieutenant event]
  (let [cells (get-in event [:data :cells])]
    (reduce (fn [lt {:keys [pos terrain]}]
              (case terrain
                :coastal (update lt :known-coastal-cells conj pos)
                :landlocked (update lt :known-landlocked-cells conj pos)
                lt))
            lieutenant
            cells)))

(defn- find-report-by-unit-id
  "Find a direct report by unit-id."
  [lieutenant unit-id]
  (first (filter #(= (:unit-id %) unit-id) (:direct-reports lieutenant))))

(defn- handle-mission-ended
  "Handle mission-ended event: mark unit as ended and decrement count."
  [lieutenant event]
  (let [unit-id (get-in event [:data :unit-id])
        reason (get-in event [:data :reason])
        report (find-report-by-unit-id lieutenant unit-id)
        mission-type (:mission-type report)]
    (-> lieutenant
        (update :direct-reports
                (fn [reports]
                  (mapv (fn [r]
                          (if (= (:unit-id r) unit-id)
                            (assoc r :status :ended :end-reason reason)
                            r))
                        reports)))
        (decrement-explorer-count mission-type))))

(defn- process-event
  "Process a single event from the queue."
  [lieutenant event]
  (case (:type event)
    :unit-needs-orders (handle-unit-needs-orders lieutenant event)
    :mission-ended (handle-mission-ended lieutenant event)
    :free-city-found (handle-free-city-found lieutenant event)
    :city-conquered (handle-city-conquered lieutenant event)
    :coastline-mapped (handle-coastline-mapped lieutenant event)
    :cells-discovered (handle-cells-discovered lieutenant event)
    lieutenant))

(defn- manhattan-distance
  "Calculate Manhattan distance between two positions."
  [[r1 c1] [r2 c2]]
  (+ (Math/abs (- r1 r2)) (Math/abs (- c1 c2))))

(defn- has-unexplored-neighbor?
  "Returns true if pos has any unexplored neighbor in the computer-map."
  [pos]
  (let [computer-map @atoms/computer-map]
    (some (fn [[dr dc]]
            (let [nr (+ (first pos) dr)
                  nc (+ (second pos) dc)
                  cell (get-in computer-map [nr nc])]
              (= :unexplored (:type cell))))
          map-utils/neighbor-offsets)))

(defn get-exploration-target
  "Find the best coastal cell to start exploring from.
   Returns the nearest known coastal cell that is adjacent to unexplored territory,
   or nil if no such cell exists or if current-pos is already a frontier cell."
  [lieutenant current-pos]
  (let [coastal-cells (:known-coastal-cells lieutenant)
        ;; Filter to frontier cells (coastal + adjacent to unexplored)
        frontier-cells (filter has-unexplored-neighbor? coastal-cells)
        ;; Exclude current position
        frontier-cells (remove #(= % current-pos) frontier-cells)]
    (when (seq frontier-cells)
      ;; Find nearest by Manhattan distance
      (->> frontier-cells
           (sort-by #(manhattan-distance current-pos %))
           first))))

(defn get-interior-exploration-target
  "Find the best landlocked cell to start interior exploration from.
   Returns the nearest known landlocked cell adjacent to unexplored territory,
   or nil if no such cell exists or if current-pos is already a good starting point."
  [lieutenant current-pos]
  (let [landlocked-cells (:known-landlocked-cells lieutenant)
        ;; Filter to cells adjacent to unexplored
        frontier-cells (filter has-unexplored-neighbor? landlocked-cells)
        ;; Exclude current position
        frontier-cells (remove #(= % current-pos) frontier-cells)]
    (when (seq frontier-cells)
      (->> frontier-cells
           (sort-by #(manhattan-distance current-pos %))
           first))))

(defn- is-empty-land-cell?
  "Returns true if the position is empty land (no unit contents)."
  [pos]
  (let [cell (get-in @atoms/game-map pos)]
    (and (= :land (:type cell))
         (nil? (:contents cell)))))

(defn get-waiting-area
  "Find an empty explored coastal cell for a reserve army.
   Returns the nearest known coastal cell that is empty,
   or nil if no such cell exists."
  [lieutenant current-pos]
  (let [coastal-cells (:known-coastal-cells lieutenant)
        ;; Filter to empty cells
        empty-cells (filter is-empty-land-cell? coastal-cells)
        ;; Exclude current position
        candidates (remove #(= % current-pos) empty-cells)]
    (when (seq candidates)
      (->> candidates
           (sort-by #(manhattan-distance current-pos %))
           first))))

(defn get-mission-for-unit
  "Determine mission assignment for a newly awakened army.
   Returns {:mission-type :target} where target may be nil."
  [lieutenant current-pos]
  (let [mission-type (determine-mission-type lieutenant)]
    (case mission-type
      :explore-coastline {:mission-type :explore-coastline
                          :target (get-exploration-target lieutenant current-pos)}
      :explore-interior {:mission-type :explore-interior
                         :target (get-interior-exploration-target lieutenant current-pos)}
      :hurry-up-and-wait {:mission-type :hurry-up-and-wait
                          :target (get-waiting-area lieutenant current-pos)}
      {:mission-type mission-type :target nil})))

(defn should-produce?
  "Returns true if Lieutenant's cities should continue producing.
   Production stops when Lieutenant enters :waiting-for-transport state."
  [lieutenant]
  (not= :waiting-for-transport (:fsm-state lieutenant)))

(defn process-lieutenant
  "Process one step of the Lieutenant FSM.
   Handles state transitions and event processing."
  [lieutenant]
  (let [ctx (context/build-context lieutenant)
        ;; Step the FSM (handles state transitions)
        lt-after-step (engine/step lieutenant ctx)
        ;; Pop and process one event if any
        [event lt-after-pop] (engine/pop-event lt-after-step)]
    (if event
      (process-event lt-after-pop event)
      lt-after-pop)))
