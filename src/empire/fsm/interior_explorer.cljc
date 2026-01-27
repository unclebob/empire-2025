(ns empire.fsm.interior-explorer
  "Interior Explorer FSM - drives army exploration of landlocked territory.
   Phase 0: Move to Lieutenant-directed starting position (if any).
   Phase 1: Diagonal sweep away from initial city.
   Phase 2: Raster pattern (back-and-forth between coasts with 2-step advancement)."
  (:require [empire.atoms :as atoms]
            [empire.movement.map-utils :as map-utils]
            [empire.movement.pathfinding :as pathfinding]))

(def backtrack-limit 10)

;; --- Event Creation ---

(defn- get-terrain-type
  "Returns :coastal if pos is land adjacent to sea, :landlocked otherwise."
  [pos]
  (if (map-utils/adjacent-to-sea? pos atoms/game-map)
    :coastal
    :landlocked))

(defn- make-cells-discovered-event
  "Create a :cells-discovered event for the given position."
  [pos]
  {:type :cells-discovered
   :priority :low
   :data {:cells [{:pos pos :terrain (get-terrain-type pos)}]}})

(defn find-adjacent-free-city
  "Find a free city adjacent to the given position. Returns [row col] or nil."
  [pos]
  (first (map-utils/get-matching-neighbors pos @atoms/game-map map-utils/neighbor-offsets
                                           #(and (= :city (:type %))
                                                 (= :free (:city-status %))))))

(defn- make-free-city-event
  "Create a :free-city-found event for the given city position."
  [city-pos]
  {:type :free-city-found
   :priority :high
   :data {:coords city-pos}})

;; --- Movement Logic ---

(defn- update-recent-moves
  "Add new position to recent-moves, keeping only last backtrack-limit entries."
  [recent-moves new-pos]
  (let [updated (conj (vec recent-moves) new-pos)]
    (if (> (count updated) backtrack-limit)
      (vec (drop 1 updated))
      updated)))

(defn- count-unexplored-neighbors
  "Count how many neighbors of pos are unexplored in the computer-map."
  [pos computer-map]
  (count (filter (fn [[dr dc]]
                   (let [nr (+ (first pos) dr)
                         nc (+ (second pos) dc)
                         cell (get-in computer-map [nr nc])]
                     (= :unexplored (:type cell))))
                 map-utils/neighbor-offsets)))

(defn- is-landlocked?
  "Returns true if the position is NOT adjacent to sea."
  [pos]
  (not (map-utils/adjacent-to-sea? pos atoms/game-map)))

(defn- on-coast?
  "Returns true if the position IS adjacent to sea."
  [pos]
  (map-utils/adjacent-to-sea? pos atoms/game-map))

(defn- get-valid-moves
  "Get all valid moves for exploration at given position (empty land cells)."
  [pos game-map]
  (map-utils/get-matching-neighbors pos game-map map-utils/neighbor-offsets
                                    #(and (= :land (:type %))
                                          (nil? (:contents %)))))

(defn- get-valid-interior-moves
  "Get valid moves that are landlocked (not coastal)."
  [pos game-map]
  (filter is-landlocked? (get-valid-moves pos game-map)))

;; --- BFS for Finding Unexplored ---

(defn- find-nearest-unexplored
  "BFS to find nearest unexplored cell reachable from pos.
   Returns [row col] of nearest unexplored, or nil if none reachable."
  [pos game-map computer-map]
  (loop [queue (conj clojure.lang.PersistentQueue/EMPTY pos)
         visited #{pos}]
    (if (empty? queue)
      nil
      (let [current (peek queue)
            queue (pop queue)
            neighbors (get-valid-moves current game-map)
            unvisited (remove visited neighbors)]
        ;; Check if current position has unexplored neighbors
        (if (pos? (count-unexplored-neighbors current computer-map))
          current
          (recur (into queue unvisited)
                 (into visited unvisited)))))))

(defn- has-reachable-unexplored?
  "Returns true if there are any unexplored cells reachable from pos."
  [pos game-map computer-map]
  (boolean (find-nearest-unexplored pos game-map computer-map)))

(defn- find-path-to-unexplored
  "BFS to find path toward nearest unexplored cell.
   Returns next step [row col] toward unexplored, or nil."
  [pos game-map computer-map]
  (loop [queue (conj clojure.lang.PersistentQueue/EMPTY [pos []])
         visited #{pos}]
    (if (empty? queue)
      nil
      (let [[current path] (peek queue)
            queue (pop queue)]
        ;; Check if current position has unexplored neighbors
        (if (and (pos? (count-unexplored-neighbors current computer-map))
                 (not= current pos))
          ;; Return first step of path
          (first path)
          ;; Continue searching
          (let [neighbors (get-valid-moves current game-map)
                unvisited (remove visited neighbors)
                new-entries (map (fn [n] [n (conj path n)]) unvisited)]
            (recur (into queue new-entries)
                   (into visited unvisited))))))))

;; --- Direction Helpers ---

(def diagonal-directions
  "The four diagonal directions."
  [[1 1] [1 -1] [-1 1] [-1 -1]])

(defn- pick-initial-direction
  "Choose a diagonal direction away from the initial city.
   If no initial city, pick randomly."
  [pos initial-city]
  (if initial-city
    (let [[pr pc] pos
          [cr cc] initial-city
          dr (if (>= pr cr) 1 -1)
          dc (if (>= pc cc) 1 -1)]
      [dr dc])
    (rand-nth diagonal-directions)))

(defn- determine-raster-axis
  "Determine raster axis based on initial city position.
   Returns :vertical or :horizontal."
  [pos initial-city]
  (if initial-city
    (let [[pr pc] pos
          [cr cc] initial-city
          vertical-dist (Math/abs (- pr cr))
          horizontal-dist (Math/abs (- pc cc))]
      (if (>= horizontal-dist vertical-dist) :vertical :horizontal))
    (rand-nth [:vertical :horizontal])))

;; --- Move Selection ---

(defn- pick-diagonal-move
  "Pick a move in the diagonal direction, preferring unexplored exposure.
   Falls back to any valid move if diagonal blocked."
  [pos direction recent-moves game-map computer-map]
  (let [all-moves (get-valid-moves pos game-map)
        non-backtrack (remove (set recent-moves) all-moves)
        moves-to-try (if (seq non-backtrack) non-backtrack all-moves)
        [dr dc] direction
        diagonal-target [(+ (first pos) dr) (+ (second pos) dc)]
        ;; Score moves by unexplored neighbors
        score-move (fn [m] (count-unexplored-neighbors m computer-map))
        scored (map (fn [m] [m (score-move m)]) moves-to-try)]
    (cond
      ;; Prefer diagonal move if valid and available
      (some #(= % diagonal-target) moves-to-try)
      diagonal-target
      ;; Otherwise pick move with most unexplored neighbors
      (seq scored)
      (first (rand-nth (filter #(= (second %) (apply max (map second scored))) scored)))
      :else nil)))

(defn- pick-raster-move
  "Pick a move for raster pattern.
   Moves in raster-direction along raster-axis, advancing when hitting coast."
  [pos raster-axis raster-direction recent-moves game-map computer-map]
  (let [all-moves (get-valid-moves pos game-map)
        non-backtrack (remove (set recent-moves) all-moves)
        moves-to-try (if (seq non-backtrack) non-backtrack all-moves)
        [pr pc] pos
        ;; Primary movement direction based on raster axis and direction
        primary-target (if (= raster-axis :vertical)
                         [(+ pr raster-direction) pc]
                         [pr (+ pc raster-direction)])
        ;; Score by unexplored
        score-move (fn [m] (count-unexplored-neighbors m computer-map))
        scored (map (fn [m] [m (score-move m)]) moves-to-try)]
    (cond
      ;; Prefer primary raster direction if available
      (some #(= % primary-target) moves-to-try)
      primary-target
      ;; Otherwise pick best by unexplored exposure
      (seq scored)
      (first (rand-nth (filter #(= (second %) (apply max (map second scored))) scored)))
      :else nil)))

;; --- Guards ---

(defn- stuck?
  "Guard: Returns true if there are no valid moves available."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        game-map @atoms/game-map
        all-moves (get-valid-moves pos game-map)]
    (empty? all-moves)))

(defn- at-target?
  "Guard: Returns true if current position equals destination (or no destination)."
  [ctx]
  (let [fsm-data (get-in ctx [:entity :fsm-data])
        pos (:position fsm-data)
        dest (:destination fsm-data)]
    (or (nil? dest) (= pos dest))))

(defn- not-at-target?
  "Guard: Returns true if not at destination."
  [ctx]
  (not (at-target? ctx)))

(defn- no-reachable-unexplored?
  "Guard: Returns true if BFS finds no unexplored cells reachable."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        game-map @atoms/game-map
        computer-map @atoms/computer-map]
    (not (has-reachable-unexplored? pos game-map computer-map))))

(defn- reached-coast?
  "Guard: Returns true if position is adjacent to sea."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])]
    (on-coast? pos)))

(defn- can-continue-exploring?
  "Guard: Returns true if we can continue diagonal exploration."
  [ctx]
  (let [fsm-data (get-in ctx [:entity :fsm-data])
        pos (:position fsm-data)
        direction (or (:explore-direction fsm-data) [1 1])
        recent-moves (or (:recent-moves fsm-data) [])
        game-map @atoms/game-map
        computer-map @atoms/computer-map]
    (boolean (pick-diagonal-move pos direction recent-moves game-map computer-map))))

(defn- can-continue-rastering?
  "Guard: Returns true if we can continue raster pattern."
  [ctx]
  (let [fsm-data (get-in ctx [:entity :fsm-data])
        pos (:position fsm-data)
        raster-axis (or (:raster-axis fsm-data) :vertical)
        raster-direction (or (:raster-direction fsm-data) 1)
        recent-moves (or (:recent-moves fsm-data) [])
        game-map @atoms/game-map
        computer-map @atoms/computer-map]
    (boolean (pick-raster-move pos raster-axis raster-direction recent-moves game-map computer-map))))

(defn- needs-routing?
  "Guard: Returns true if blocked but BFS can find path to unexplored."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        game-map @atoms/game-map
        computer-map @atoms/computer-map]
    (boolean (find-path-to-unexplored pos game-map computer-map))))

(defn- always [_ctx] true)

;; --- Actions ---

(defn- make-base-events
  "Create base events for any exploration action (cells-discovered, free-city)."
  [pos]
  (let [free-city (find-adjacent-free-city pos)]
    (cond-> [(make-cells-discovered-event pos)]
      free-city (conj (make-free-city-event free-city)))))

(defn- terminal-stuck-action
  "Action: Called when the explorer is stuck with no valid moves."
  [ctx]
  (let [unit-id (get-in ctx [:entity :fsm-data :unit-id])]
    {:events [{:type :mission-ended
               :priority :high
               :data {:unit-id unit-id :reason :stuck}}]}))

(defn- terminal-no-unexplored-action
  "Action: Called when no unexplored cells are reachable."
  [ctx]
  (let [unit-id (get-in ctx [:entity :fsm-data :unit-id])]
    {:events [{:type :mission-ended
               :priority :high
               :data {:unit-id unit-id :reason :no-unexplored}}]}))

(defn- move-to-target-action
  "Action: Move toward destination using pathfinding."
  [ctx]
  (let [fsm-data (get-in ctx [:entity :fsm-data])
        pos (:position fsm-data)
        dest (:destination fsm-data)
        recent-moves (or (:recent-moves fsm-data) [])
        next-pos (pathfinding/next-step-toward pos dest)]
    (when next-pos
      {:move-to next-pos
       :destination dest
       :recent-moves (update-recent-moves recent-moves next-pos)
       :events (make-base-events pos)})))

(defn- arrive-at-target-action
  "Action: Called when arriving at target. Sets up for exploration."
  [ctx]
  (let [fsm-data (get-in ctx [:entity :fsm-data])
        pos (:position fsm-data)
        initial-city (or (:initial-city fsm-data) pos)
        direction (pick-initial-direction pos initial-city)
        ;; Also compute first exploration move
        game-map @atoms/game-map
        computer-map @atoms/computer-map
        recent-moves []
        next-pos (pick-diagonal-move pos direction recent-moves game-map computer-map)]
    (merge
     {:destination nil
      :explore-direction direction
      :initial-city initial-city
      :recent-moves (if next-pos (update-recent-moves [] next-pos) [])
      :events (make-base-events pos)}
     (when next-pos {:move-to next-pos}))))

(defn- explore-diagonal-action
  "Action: Move diagonally, prefer unexplored exposure."
  [ctx]
  (let [fsm-data (get-in ctx [:entity :fsm-data])
        pos (:position fsm-data)
        direction (or (:explore-direction fsm-data) [1 1])
        recent-moves (or (:recent-moves fsm-data) [])
        game-map @atoms/game-map
        computer-map @atoms/computer-map
        next-pos (pick-diagonal-move pos direction recent-moves game-map computer-map)]
    (when next-pos
      {:move-to next-pos
       :recent-moves (update-recent-moves recent-moves next-pos)
       :events (make-base-events pos)})))

(defn- start-raster-action
  "Action: Transition to rastering, set up axis and direction."
  [ctx]
  (let [fsm-data (get-in ctx [:entity :fsm-data])
        pos (:position fsm-data)
        initial-city (or (:initial-city fsm-data) pos)
        raster-axis (determine-raster-axis pos initial-city)
        ;; Start with direction 1, will flip when hitting coast
        raster-direction 1
        recent-moves (or (:recent-moves fsm-data) [])
        game-map @atoms/game-map
        computer-map @atoms/computer-map
        next-pos (pick-raster-move pos raster-axis raster-direction recent-moves game-map computer-map)]
    (merge
     {:raster-axis raster-axis
      :raster-direction raster-direction
      :recent-moves (if next-pos (update-recent-moves recent-moves next-pos) recent-moves)
      :events (make-base-events pos)}
     (when next-pos {:move-to next-pos}))))

(defn- raster-action
  "Action: Continue raster sweep, flip direction at coast."
  [ctx]
  (let [fsm-data (get-in ctx [:entity :fsm-data])
        pos (:position fsm-data)
        raster-axis (or (:raster-axis fsm-data) :vertical)
        raster-direction (or (:raster-direction fsm-data) 1)
        recent-moves (or (:recent-moves fsm-data) [])
        game-map @atoms/game-map
        computer-map @atoms/computer-map
        ;; Check if we need to flip direction (at coast)
        [pr pc] pos
        primary-target (if (= raster-axis :vertical)
                         [(+ pr raster-direction) pc]
                         [pr (+ pc raster-direction)])
        primary-blocked (not (some #(= % primary-target) (get-valid-moves pos game-map)))
        new-direction (if primary-blocked (- raster-direction) raster-direction)
        next-pos (pick-raster-move pos raster-axis new-direction recent-moves game-map computer-map)]
    (merge
     {:raster-direction new-direction
      :recent-moves (if next-pos (update-recent-moves recent-moves next-pos) recent-moves)
      :events (make-base-events pos)}
     (when next-pos {:move-to next-pos}))))

(defn- route-around-action
  "Action: Use BFS to route around obstacles toward unexplored."
  [ctx]
  (let [fsm-data (get-in ctx [:entity :fsm-data])
        pos (:position fsm-data)
        recent-moves (or (:recent-moves fsm-data) [])
        game-map @atoms/game-map
        computer-map @atoms/computer-map
        next-pos (find-path-to-unexplored pos game-map computer-map)]
    (when next-pos
      {:move-to next-pos
       :recent-moves (update-recent-moves recent-moves next-pos)
       :events (make-base-events pos)})))

;; --- FSM Definition ---

(def interior-explorer-fsm
  "FSM transitions for interior explorer.
   Format: [current-state guard-fn new-state action-fn]

   States:
   - :moving-to-target      - Moving to Lieutenant-directed starting position
   - :exploring             - Diagonal sweep away from initial city
   - :rastering             - Back-and-forth between coasts
   - [:terminal :stuck]     - No valid moves available
   - [:terminal :no-unexplored] - Mission complete, no unexplored cells"
  [;; Moving to target transitions
   [:moving-to-target  stuck?               [:terminal :stuck]           terminal-stuck-action]
   [:moving-to-target  at-target?           :exploring                   arrive-at-target-action]
   [:moving-to-target  not-at-target?       :moving-to-target            move-to-target-action]

   ;; Exploring transitions (diagonal sweep)
   [:exploring  stuck?                  [:terminal :stuck]          terminal-stuck-action]
   [:exploring  no-reachable-unexplored? [:terminal :no-unexplored] terminal-no-unexplored-action]
   [:exploring  reached-coast?          :rastering                  start-raster-action]
   [:exploring  can-continue-exploring? :exploring                  explore-diagonal-action]
   [:exploring  needs-routing?          :exploring                  route-around-action]

   ;; Rastering transitions
   [:rastering  stuck?                  [:terminal :stuck]          terminal-stuck-action]
   [:rastering  no-reachable-unexplored? [:terminal :no-unexplored] terminal-no-unexplored-action]
   [:rastering  can-continue-rastering? :rastering                  raster-action]
   [:rastering  needs-routing?          :rastering                  route-around-action]])

;; --- Create Explorer ---

(defn create-interior-explorer-data
  "Create FSM data for a new interior explorer mission at given position.
   Optional target parameter sets destination for :moving-to-target state.
   Optional unit-id parameter identifies the unit for mission tracking.
   Optional initial-city parameter sets the 'away from' direction preference."
  ([pos]
   (create-interior-explorer-data pos nil nil nil))
  ([pos target]
   (create-interior-explorer-data pos target nil nil))
  ([pos target unit-id]
   (create-interior-explorer-data pos target unit-id nil))
  ([pos target unit-id initial-city]
   (let [has-target? (and target (not= pos target))
         initial-direction (pick-initial-direction pos (or initial-city pos))]
     {:fsm interior-explorer-fsm
      :fsm-state (if has-target? :moving-to-target :exploring)
      :fsm-data {:position pos
                 :destination (when has-target? target)
                 :initial-city initial-city
                 :explore-direction initial-direction
                 :recent-moves [pos]
                 :unit-id unit-id}})))

;; Public versions for testing
(def explore-interior-action-public explore-diagonal-action)
(def move-to-start-action-public move-to-target-action)
