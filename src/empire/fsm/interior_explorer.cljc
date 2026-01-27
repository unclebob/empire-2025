(ns empire.fsm.interior-explorer
  "Interior Explorer FSM - drives army exploration of landlocked territory.
   Phase 0: Move to Lieutenant-directed starting position (if any).
   Phase 1: Diagonal sweep away from initial city.
   Phase 2: Raster pattern (back-and-forth between coasts with 2-step advancement)."
  (:require [empire.atoms :as atoms]
            [empire.movement.pathfinding :as pathfinding]
            [empire.fsm.explorer-utils :as utils]))

;; --- BFS for Finding Unexplored ---

(defn- find-nearest-unexplored
  "BFS to find nearest position with unexplored neighbors reachable from pos.
   Returns [row col] of nearest position with unexplored exposure, or nil."
  [pos game-map computer-map]
  (loop [queue (conj clojure.lang.PersistentQueue/EMPTY pos)
         visited #{pos}]
    (if (empty? queue)
      nil
      (let [current (peek queue)
            queue (pop queue)
            neighbors (utils/get-valid-moves current game-map)
            unvisited (remove visited neighbors)]
        ;; Check if current position has unexplored neighbors
        (if (pos? (utils/count-unexplored-neighbors current computer-map))
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
        (if (and (pos? (utils/count-unexplored-neighbors current computer-map))
                 (not= current pos))
          ;; Return first step of path
          (first path)
          ;; Continue searching
          (let [neighbors (utils/get-valid-moves current game-map)
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
  (let [all-moves (utils/get-valid-moves pos game-map)
        non-backtrack (remove (set recent-moves) all-moves)
        moves-to-try (if (seq non-backtrack) non-backtrack all-moves)
        [dr dc] direction
        diagonal-target [(+ (first pos) dr) (+ (second pos) dc)]]
    (cond
      ;; Prefer diagonal move if valid and available
      (some #(= % diagonal-target) moves-to-try)
      diagonal-target
      ;; Otherwise pick move with most unexplored neighbors
      (seq moves-to-try)
      (let [scored (map (fn [m] [m (utils/count-unexplored-neighbors m computer-map)]) moves-to-try)
            max-score (apply max (map second scored))
            best-moves (filter #(= (second %) max-score) scored)]
        (first (rand-nth best-moves)))
      :else nil)))

(defn- pick-raster-move
  "Pick a move for raster pattern.
   Moves in raster-direction along raster-axis."
  [pos raster-axis raster-direction recent-moves game-map computer-map]
  (let [all-moves (utils/get-valid-moves pos game-map)
        non-backtrack (remove (set recent-moves) all-moves)
        moves-to-try (if (seq non-backtrack) non-backtrack all-moves)
        [pr pc] pos
        ;; Primary movement direction based on raster axis and direction
        primary-target (if (= raster-axis :vertical)
                         [(+ pr raster-direction) pc]
                         [pr (+ pc raster-direction)])]
    (cond
      ;; Prefer primary raster direction if available
      (some #(= % primary-target) moves-to-try)
      primary-target
      ;; Otherwise pick best by unexplored exposure
      (seq moves-to-try)
      (let [scored (map (fn [m] [m (utils/count-unexplored-neighbors m computer-map)]) moves-to-try)
            max-score (apply max (map second scored))
            best-moves (filter #(= (second %) max-score) scored)]
        (first (rand-nth best-moves)))
      :else nil)))

(defn- compute-advancement-move
  "Compute 2-step diagonal advancement move when flipping raster direction.
   Advances perpendicular to raster-axis in the explore-direction."
  [pos raster-axis explore-direction game-map]
  (let [[dr dc] explore-direction
        ;; Advance perpendicular to raster axis
        advance-dir (if (= raster-axis :vertical) [0 dc] [dr 0])
        [ar ac] advance-dir
        ;; Try 2 steps, fall back to 1 step
        two-step [(+ (first pos) (* 2 ar)) (+ (second pos) (* 2 ac))]
        one-step [(+ (first pos) ar) (+ (second pos) ac)]
        valid-moves (utils/get-valid-moves pos game-map)]
    (cond
      (some #(= % two-step) valid-moves) two-step
      (some #(= % one-step) valid-moves) one-step
      :else nil)))

;; --- Guards ---

(defn- stuck?
  "Guard: Returns true if there are no valid moves available."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        all-moves (utils/get-valid-moves pos @atoms/game-map)]
    (empty? all-moves)))

(defn- at-target?
  "Guard: Returns true if current position equals destination (or no destination)."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        dest (get-in ctx [:entity :fsm-data :destination])]
    (or (nil? dest) (= pos dest))))

(defn- not-at-target?
  "Guard: Returns true if not at destination."
  [ctx]
  (not (at-target? ctx)))

(defn- no-reachable-unexplored?
  "Guard: Returns true if BFS finds no unexplored cells reachable."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])]
    (not (has-reachable-unexplored? pos @atoms/game-map @atoms/computer-map))))

(defn- reached-coast?
  "Guard: Returns true if position is adjacent to sea."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])]
    (utils/on-coast? pos)))

(defn- can-continue-exploring?
  "Guard: Returns true if we can continue diagonal exploration."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        direction (or (get-in ctx [:entity :fsm-data :explore-direction]) [1 1])
        recent-moves (or (get-in ctx [:entity :fsm-data :recent-moves]) [])]
    (boolean (pick-diagonal-move pos direction recent-moves @atoms/game-map @atoms/computer-map))))

(defn- can-continue-rastering?
  "Guard: Returns true if we can continue raster pattern."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        raster-axis (or (get-in ctx [:entity :fsm-data :raster-axis]) :vertical)
        raster-direction (or (get-in ctx [:entity :fsm-data :raster-direction]) 1)
        recent-moves (or (get-in ctx [:entity :fsm-data :recent-moves]) [])]
    (boolean (pick-raster-move pos raster-axis raster-direction recent-moves @atoms/game-map @atoms/computer-map))))

(defn- needs-routing?
  "Guard: Returns true if blocked but BFS can find path to unexplored."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])]
    (boolean (find-path-to-unexplored pos @atoms/game-map @atoms/computer-map))))

(defn- always [_ctx] true)

;; --- Actions ---

(defn- terminal-stuck-action
  "Action: Called when the explorer is stuck with no valid moves."
  [ctx]
  (let [unit-id (get-in ctx [:entity :fsm-data :unit-id])]
    {:events [(utils/make-mission-ended-event unit-id :stuck)]}))

(defn- terminal-no-unexplored-action
  "Action: Called when no unexplored cells are reachable."
  [ctx]
  (let [unit-id (get-in ctx [:entity :fsm-data :unit-id])]
    {:events [(utils/make-mission-ended-event unit-id :no-unexplored)]}))

(defn- move-to-target-action
  "Action: Move toward destination using pathfinding."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        dest (get-in ctx [:entity :fsm-data :destination])
        recent-moves (or (get-in ctx [:entity :fsm-data :recent-moves]) [])
        next-pos (pathfinding/next-step-toward pos dest)]
    (when next-pos
      {:move-to next-pos
       :destination dest
       :recent-moves (utils/update-recent-moves recent-moves next-pos)
       :events (utils/make-base-events pos)})))

(defn- arrive-at-target-action
  "Action: Called when arriving at target. Sets up for exploration."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        initial-city (or (get-in ctx [:entity :fsm-data :initial-city]) pos)
        direction (pick-initial-direction pos initial-city)
        recent-moves []
        next-pos (pick-diagonal-move pos direction recent-moves @atoms/game-map @atoms/computer-map)]
    (merge
     {:destination nil
      :explore-direction direction
      :initial-city initial-city
      :recent-moves (if next-pos (utils/update-recent-moves [] next-pos) [])
      :events (utils/make-base-events pos)}
     (when next-pos {:move-to next-pos}))))

(defn- explore-diagonal-action
  "Action: Move diagonally, prefer unexplored exposure."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        direction (or (get-in ctx [:entity :fsm-data :explore-direction]) [1 1])
        recent-moves (or (get-in ctx [:entity :fsm-data :recent-moves]) [])
        next-pos (pick-diagonal-move pos direction recent-moves @atoms/game-map @atoms/computer-map)]
    (when next-pos
      {:move-to next-pos
       :recent-moves (utils/update-recent-moves recent-moves next-pos)
       :events (utils/make-base-events pos)})))

(defn- start-raster-action
  "Action: Transition to rastering, set up axis and direction."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        initial-city (or (get-in ctx [:entity :fsm-data :initial-city]) pos)
        raster-axis (determine-raster-axis pos initial-city)
        raster-direction 1
        recent-moves (or (get-in ctx [:entity :fsm-data :recent-moves]) [])
        next-pos (pick-raster-move pos raster-axis raster-direction recent-moves @atoms/game-map @atoms/computer-map)]
    (merge
     {:raster-axis raster-axis
      :raster-direction raster-direction
      :recent-moves (if next-pos (utils/update-recent-moves recent-moves next-pos) recent-moves)
      :events (utils/make-base-events pos)}
     (when next-pos {:move-to next-pos}))))

(defn- raster-action
  "Action: Continue raster sweep. When hitting edge, flip direction and advance 2 steps."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        raster-axis (or (get-in ctx [:entity :fsm-data :raster-axis]) :vertical)
        raster-direction (or (get-in ctx [:entity :fsm-data :raster-direction]) 1)
        explore-direction (or (get-in ctx [:entity :fsm-data :explore-direction]) [1 1])
        recent-moves (or (get-in ctx [:entity :fsm-data :recent-moves]) [])
        game-map @atoms/game-map
        computer-map @atoms/computer-map
        ;; Check if primary direction is blocked
        [pr pc] pos
        primary-target (if (= raster-axis :vertical)
                         [(+ pr raster-direction) pc]
                         [pr (+ pc raster-direction)])
        primary-blocked (not (some #(= % primary-target) (utils/get-valid-moves pos game-map)))]
    (if primary-blocked
      ;; Flip direction and try to advance 2 steps perpendicular
      (let [new-direction (- raster-direction)
            advance-pos (compute-advancement-move pos raster-axis explore-direction game-map)
            next-pos (or advance-pos
                         (pick-raster-move pos raster-axis new-direction recent-moves game-map computer-map))]
        (merge
         {:raster-direction new-direction
          :recent-moves (if next-pos (utils/update-recent-moves recent-moves next-pos) recent-moves)
          :events (utils/make-base-events pos)}
         (when next-pos {:move-to next-pos})))
      ;; Continue in primary direction
      (let [next-pos (pick-raster-move pos raster-axis raster-direction recent-moves game-map computer-map)]
        (merge
         {:raster-direction raster-direction
          :recent-moves (if next-pos (utils/update-recent-moves recent-moves next-pos) recent-moves)
          :events (utils/make-base-events pos)}
         (when next-pos {:move-to next-pos}))))))

(defn- route-around-action
  "Action: Use BFS to route around obstacles toward unexplored."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        recent-moves (or (get-in ctx [:entity :fsm-data :recent-moves]) [])
        next-pos (find-path-to-unexplored pos @atoms/game-map @atoms/computer-map)]
    (when next-pos
      {:move-to next-pos
       :recent-moves (utils/update-recent-moves recent-moves next-pos)
       :events (utils/make-base-events pos)})))

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
