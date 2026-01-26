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

(def lieutenant-fsm
  "FSM transitions for the Lieutenant.
   Format: [current-state guard-fn new-state action-fn]"
  [[:initializing has-unit-needs-orders? :exploring (constantly nil)]
   [:exploring (constantly false) :exploring identity]
   [:established (constantly false) :established identity]])

(defn create-lieutenant
  "Create a new Lieutenant with the given name and assigned city."
  [name city-coords]
  {:fsm lieutenant-fsm
   :fsm-state :initializing
   :fsm-data {:mission-type :explore-conquer}
   :event-queue []
   :name name
   :cities [city-coords]
   :direct-reports []
   :free-cities-known []
   :beach-candidates []
   :known-coastal-cells #{}
   :known-landlocked-cells #{}
   :frontier-coastal-cells #{}})

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
  [unit-id coords]
  {:unit-id unit-id
   :coords coords
   :mission-type :explore-coastline  ; Default to coastline exploration
   :fsm-state :exploring})

(defn- handle-unit-needs-orders
  "Handle unit-needs-orders event: assign explorer mission."
  [lieutenant event]
  (let [unit-id (get-in event [:data :unit-id])
        coords (get-in event [:data :coords])
        explorer (create-explorer unit-id coords)]
    (update lieutenant :direct-reports conj explorer)))

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

(defn- process-event
  "Process a single event from the queue."
  [lieutenant event]
  (case (:type event)
    :unit-needs-orders (handle-unit-needs-orders lieutenant event)
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
