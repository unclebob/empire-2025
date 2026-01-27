(ns empire.fsm.interior-explorer
  "Interior Explorer FSM - drives army exploration of landlocked territory.
   Phase 0: Move to Lieutenant-directed starting position (if any).
   Phase 1: Explore interior, preferring unexplored cells and avoiding coast."
  (:require [empire.atoms :as atoms]
            [empire.movement.map-utils :as map-utils]
            [empire.movement.pathfinding :as pathfinding]
            [empire.fsm.context :as context]))

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

(defn- valid-interior-move?
  "Returns true if cell is valid for interior exploration (land, empty, not coastal)."
  [cell pos]
  (and (= :land (:type cell))
       (nil? (:contents cell))
       (is-landlocked? pos)))

(defn- get-valid-interior-moves
  "Get all valid moves for interior exploration at given position."
  [pos game-map]
  (map-utils/get-matching-neighbors pos game-map map-utils/neighbor-offsets
                                    #(and (= :land (:type %))
                                          (nil? (:contents %)))))

(defn- pick-interior-move
  "Pick a move for interior exploration. Prefers:
   1. Non-backtrack, landlocked moves with most unexplored neighbors
   2. Non-backtrack landlocked moves
   3. Landlocked moves with most unexplored neighbors
   4. Any valid move"
  [pos all-moves recent-moves computer-map]
  (let [landlocked-moves (filter #(is-landlocked? %) all-moves)
        non-backtrack-landlocked (remove (set recent-moves) landlocked-moves)
        non-backtrack-any (remove (set recent-moves) all-moves)

        score-by-unexplored (fn [moves]
                              (when (seq moves)
                                (let [scored (map (fn [m] [m (count-unexplored-neighbors m computer-map)]) moves)
                                      max-score (apply max (map second scored))
                                      best (filter #(= (second %) max-score) scored)]
                                  (first (rand-nth best)))))]
    (cond
      ;; Prefer landlocked, non-backtrack, with unexplored neighbors
      (seq non-backtrack-landlocked) (score-by-unexplored non-backtrack-landlocked)
      ;; Fallback to landlocked with unexplored
      (seq landlocked-moves) (score-by-unexplored landlocked-moves)
      ;; Fallback to any non-backtrack
      (seq non-backtrack-any) (score-by-unexplored non-backtrack-any)
      ;; Last resort: any move
      (seq all-moves) (rand-nth all-moves)
      :else nil)))

;; --- Guards ---

(defn- at-destination?
  "Guard: Returns true if current position equals destination."
  [ctx]
  (let [fsm-data (get-in ctx [:entity :fsm-data])
        pos (:position fsm-data)
        dest (:destination fsm-data)]
    (or (nil? dest) (= pos dest))))

(defn- not-at-destination?
  "Guard: Returns true if not at destination."
  [ctx]
  (not (at-destination? ctx)))

(defn- always [_ctx] true)

(defn- stuck?
  "Guard: Returns true if there are no valid moves available."
  [ctx]
  (let [fsm-data (get-in ctx [:entity :fsm-data])
        pos (:position fsm-data)
        game-map @atoms/game-map
        all-moves (get-valid-interior-moves pos game-map)]
    (empty? all-moves)))

;; --- Actions ---

(defn- explore-interior-action
  "Action: Compute next move while exploring interior.
   Returns {:move-to [row col], :recent-moves [...], :events [...]}"
  [ctx]
  (let [fsm-data (get-in ctx [:entity :fsm-data])
        pos (:position fsm-data)
        recent-moves (or (:recent-moves fsm-data) [])
        game-map @atoms/game-map
        computer-map @atoms/computer-map
        all-moves (get-valid-interior-moves pos game-map)
        next-pos (pick-interior-move pos all-moves recent-moves computer-map)
        ;; Check for adjacent free cities
        free-city (find-adjacent-free-city pos)
        events (cond-> [(make-cells-discovered-event pos)]
                 free-city (conj (make-free-city-event free-city)))]
    (when next-pos
      {:move-to next-pos
       :recent-moves (update-recent-moves recent-moves next-pos)
       :events events})))

;; Public version for testing
(def explore-interior-action-public explore-interior-action)

(defn- move-to-start-action
  "Action: Move toward destination using pathfinding.
   Returns {:move-to [row col], :recent-moves [...]}"
  [ctx]
  (let [fsm-data (get-in ctx [:entity :fsm-data])
        pos (:position fsm-data)
        dest (:destination fsm-data)
        recent-moves (or (:recent-moves fsm-data) [])
        next-pos (pathfinding/next-step-toward pos dest)]
    (when next-pos
      {:move-to next-pos
       :destination dest
       :recent-moves (update-recent-moves recent-moves next-pos)})))

;; Public version for testing
(def move-to-start-action-public move-to-start-action)

(defn- arrive-at-start-action
  "Action: Called when arriving at destination. Sets up for interior exploration
   and computes the first exploration move."
  [ctx]
  (let [fsm-data (get-in ctx [:entity :fsm-data])
        pos (:position fsm-data)
        setup-data {:destination nil
                    :recent-moves [pos]}
        updated-ctx (update-in ctx [:entity :fsm-data] merge setup-data)
        next-action-result (explore-interior-action updated-ctx)]
    (merge setup-data next-action-result)))

(defn- terminal-action
  "Action: Called when the explorer is stuck with no valid moves.
   Emits a :mission-ended event to notify the Lieutenant."
  [ctx]
  (let [unit-id (get-in ctx [:entity :fsm-data :unit-id])]
    {:events [{:type :mission-ended
               :priority :high
               :data {:unit-id unit-id :reason :stuck}}]}))

;; --- FSM Definition ---

(def interior-explorer-fsm
  "FSM transitions for interior explorer.
   Format: [current-state guard-fn new-state action-fn]

   States:
   - :moving-to-start    - Moving to Lieutenant-directed starting position
   - :exploring-interior - Exploring landlocked territory
   - [:terminal :stuck]  - No valid moves available, mission ended"
  [;; Moving to start transitions
   [:moving-to-start     stuck?              [:terminal :stuck]  terminal-action]
   [:moving-to-start     at-destination?     :exploring-interior arrive-at-start-action]
   [:moving-to-start     not-at-destination? :moving-to-start    move-to-start-action]
   ;; Exploring interior transitions
   [:exploring-interior  stuck?              [:terminal :stuck]  terminal-action]
   [:exploring-interior  always              :exploring-interior explore-interior-action]])

;; --- Create Explorer ---

(defn create-interior-explorer-data
  "Create FSM data for a new interior explorer mission at given position.
   Optional target parameter sets destination for :moving-to-start state.
   Optional unit-id parameter identifies the unit for mission tracking."
  ([pos]
   (create-interior-explorer-data pos nil nil))
  ([pos target]
   (create-interior-explorer-data pos target nil))
  ([pos target unit-id]
   (let [has-target? (and target (not= pos target))]
     {:fsm interior-explorer-fsm
      :fsm-state (if has-target? :moving-to-start :exploring-interior)
      :fsm-data {:position pos
                 :destination (when has-target? target)
                 :recent-moves [pos]
                 :unit-id unit-id}})))
