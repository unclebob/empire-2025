(ns empire.fsm.coastline-explorer
  "Coastline Explorer FSM - drives army exploration behavior.
   Phase 0: Move to Lieutenant-directed starting position.
   Phase 1: Head in random direction until reaching coast.
   Phase 2: Follow coastline, avoiding backtracking.
   Phase 3: Skirt around port cities blocking the coastal path."
  (:require [empire.atoms :as atoms]
            [empire.movement.map-utils :as map-utils]
            [empire.movement.pathfinding :as pathfinding]
            [empire.fsm.context :as context]
            [empire.fsm.explorer-utils :as utils]
            [empire.debug :as debug]))

;; --- City Detection ---

(defn find-adjacent-port-city
  "Find any city adjacent to position that is also adjacent to sea (port city).
   Returns [row col] or nil. Does not care about city ownership."
  [pos]
  (first (filter (fn [city-pos]
                   (map-utils/adjacent-to-sea? city-pos atoms/game-map))
                 (map-utils/get-matching-neighbors pos @atoms/game-map map-utils/neighbor-offsets
                                                   #(= :city (:type %))))))

;; --- Movement Logic ---

(defn- score-move-by-direction
  "Scores a move based on how well it matches the preferred direction.
   Returns 0-2 where 2 is exact match."
  [pos target-pos [pref-dr pref-dc]]
  (let [[dr dc] [(- (first target-pos) (first pos))
                 (- (second target-pos) (second pos))]
        norm-dr (cond (pos? dr) 1 (neg? dr) -1 :else 0)
        norm-dc (cond (pos? dc) 1 (neg? dc) -1 :else 0)
        dr-match (cond (= norm-dr pref-dr) 1 (= norm-dr 0) 0.5 (= pref-dr 0) 0.5 :else 0)
        dc-match (cond (= norm-dc pref-dc) 1 (= norm-dc 0) 0.5 (= pref-dc 0) 0.5 :else 0)]
    (+ dr-match dc-match)))

(defn pick-seeking-move
  "Pick a move while seeking the coast. Prefer moves in explore-direction."
  [pos all-moves recent-moves explore-direction]
  (let [non-backtrack (remove (set recent-moves) all-moves)
        moves-to-consider (if (seq non-backtrack) non-backtrack all-moves)]
    (if explore-direction
      (let [scored (map (fn [m] [m (score-move-by-direction pos m explore-direction)]) moves-to-consider)
            max-score (apply max (map second scored))
            best-moves (map first (filter #(= (second %) max-score) scored))]
        (when (seq best-moves)
          (rand-nth best-moves)))
      (when (seq moves-to-consider)
        (rand-nth moves-to-consider)))))

(defn- pick-best-by-unexplored
  "From a list of moves, pick the one(s) with most unexplored neighbors.
   Returns a random choice among the best moves."
  [moves computer-map]
  (when (seq moves)
    (let [scored (map (fn [m] [m (utils/count-unexplored-neighbors m computer-map)]) moves)
          max-score (apply max (map second scored))
          best-moves (map first (filter #(= (second %) max-score) scored))]
      (rand-nth best-moves))))

(defn- first-move?
  "Returns true if recent-moves only contains the starting position (length 1)."
  [recent-moves]
  (= 1 (count recent-moves)))

(defn- pick-by-direction
  "From a list of moves, pick the one(s) best aligned with explore-direction.
   Returns a random choice among the best moves."
  [pos moves explore-direction]
  (when (and (seq moves) explore-direction)
    (let [scored (map (fn [m] [m (score-move-by-direction pos m explore-direction)]) moves)
          max-score (apply max (map second scored))
          best-moves (map first (filter #(= (second %) max-score) scored))]
      (rand-nth best-moves))))

(defn- pick-unexplored-fallback
  "Standard move selection preferring unexplored territory."
  [non-backtrack-coastal coastal-moves non-backtrack-any all-moves computer-map]
  (cond
    (seq non-backtrack-coastal) (pick-best-by-unexplored non-backtrack-coastal computer-map)
    (seq coastal-moves) (pick-best-by-unexplored coastal-moves computer-map)
    (seq non-backtrack-any) (pick-best-by-unexplored non-backtrack-any computer-map)
    (seq all-moves) (rand-nth all-moves)
    :else nil))

(defn pick-following-move
  "Pick a move while following the coast. Stay adjacent to sea, avoid backtracking.
   On first move (recent-moves has only starting position), uses explore-direction.
   After first move, prefers moves that expose more unexplored territory."
  [pos all-moves recent-moves computer-map explore-direction]
  (let [coastal-moves (filter #(map-utils/adjacent-to-sea? % atoms/game-map) all-moves)
        non-backtrack-coastal (remove (set recent-moves) coastal-moves)
        non-backtrack-any (remove (set recent-moves) all-moves)]
    (if (and (first-move? recent-moves) explore-direction)
      ;; First move with explore-direction: use it to pick initial direction
      (or (pick-by-direction pos non-backtrack-coastal explore-direction)
          (pick-by-direction pos coastal-moves explore-direction)
          (pick-by-direction pos non-backtrack-any explore-direction)
          (pick-unexplored-fallback non-backtrack-coastal coastal-moves non-backtrack-any all-moves computer-map))
      ;; Subsequent moves or no explore-direction: prefer unexplored territory
      (pick-unexplored-fallback non-backtrack-coastal coastal-moves non-backtrack-any all-moves computer-map))))

(defn adjacent-to?
  "Returns true if pos1 is adjacent to pos2 (including diagonals)."
  [[r1 c1] [r2 c2]]
  (and (<= (Math/abs (- r1 r2)) 1)
       (<= (Math/abs (- c1 c2)) 1)
       (not (and (= r1 r2) (= c1 c2)))))

(defn pick-skirting-move
  "Pick a move while skirting around a city. Stay adjacent to the city while
   moving toward the coast. Avoid backtracking. Returns nil if no city-adjacent
   move is available - the unit must stay adjacent to the city until reaching coast."
  [pos all-moves recent-moves city-pos computer-map]
  (let [;; Only consider moves that stay adjacent to the city being skirted
        adjacent-to-city (filter #(adjacent-to? % city-pos) all-moves)
        ;; Among those, prefer moves that get us closer to coast
        coastal-adjacent (filter #(map-utils/adjacent-to-sea? % atoms/game-map) adjacent-to-city)
        ;; Avoid backtracking
        non-backtrack-coastal (remove (set recent-moves) coastal-adjacent)
        non-backtrack-adjacent (remove (set recent-moves) adjacent-to-city)]
    (cond
      ;; Best: adjacent to city, coastal, not backtracking
      (seq non-backtrack-coastal) (pick-best-by-unexplored non-backtrack-coastal computer-map)
      ;; Good: adjacent to city, coastal (even if backtracking)
      (seq coastal-adjacent) (pick-best-by-unexplored coastal-adjacent computer-map)
      ;; OK: adjacent to city, not backtracking
      (seq non-backtrack-adjacent) (pick-best-by-unexplored non-backtrack-adjacent computer-map)
      ;; Fallback: any adjacent to city (even if backtracking)
      (seq adjacent-to-city) (pick-best-by-unexplored adjacent-to-city computer-map)
      ;; No city-adjacent moves available - unit is stuck
      :else nil)))

;; --- Unit Integration ---

(defn random-direction
  "Returns a random direction offset from the 8 possible directions."
  []
  (rand-nth [[-1 -1] [-1 0] [-1 1] [0 -1] [0 1] [1 -1] [1 0] [1 1]]))

;; --- Guards ---

(defn- on-coast?
  "Guard: Returns true if unit is on a coastal cell."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])]
    (map-utils/adjacent-to-sea? pos atoms/game-map)))

(defn- not-on-coast?
  "Guard: Returns true if unit is not on a coastal cell."
  [ctx]
  (not (on-coast? ctx)))

(defn- always
  "Guard: Always returns true."
  [_ctx]
  true)

(defn- at-destination?
  "Guard: Returns true if unit is at its destination."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        dest (get-in ctx [:entity :fsm-data :destination])]
    (= pos dest)))

(defn- not-at-destination?
  "Guard: Returns true if unit is not at its destination."
  [ctx]
  (not (at-destination? ctx)))

(defn- at-destination-on-coast?
  "Guard: Returns true if at destination and on a coastal cell."
  [ctx]
  (and (at-destination? ctx) (on-coast? ctx)))

(defn- at-destination-not-on-coast?
  "Guard: Returns true if at destination but not on a coastal cell."
  [ctx]
  (and (at-destination? ctx) (not (on-coast? ctx))))

(defn at-port-city?
  "Guard: Returns true if following coast and adjacent to a port city that blocks
   the coastal path (no valid coastal moves that bypass the city)."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        port-city (find-adjacent-port-city pos)]
    (boolean port-city)))

(defn back-on-coast?
  "Guard: Returns true when skirting a city and we've returned to the coast
   at a different position from where we started skirting."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        start-pos (get-in ctx [:entity :fsm-data :skirt-start-pos])
        on-coast (map-utils/adjacent-to-sea? pos atoms/game-map)]
    (and on-coast
         (not= pos start-pos))))

(defn- stuck?
  "Guard: Returns true if there are no valid moves available."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        all-moves (context/get-valid-army-moves ctx pos)]
    (empty? all-moves)))

;; --- Actions ---

(defn- seek-coast-action
  "Action: Compute next move while seeking the coast.
   Returns {:move-to [row col], :recent-moves [...], :found-coast bool, :events [...]}."
  [ctx]
  (let [fsm-data (get-in ctx [:entity :fsm-data])
        pos (:position fsm-data)
        recent-moves (or (:recent-moves fsm-data) [])
        explore-direction (:explore-direction fsm-data)
        all-moves (context/get-valid-army-moves ctx pos)
        next-pos (pick-seeking-move pos all-moves recent-moves explore-direction)
        on-coast? (map-utils/adjacent-to-sea? pos atoms/game-map)
        ;; Check for adjacent free cities to report
        free-city (utils/find-adjacent-free-city pos)
        ;; Build events list: always include cells-discovered, optionally free-city
        events (cond-> [(utils/make-cells-discovered-event pos)]
                 free-city (conj (utils/make-free-city-event free-city)))]
    (when next-pos
      {:move-to next-pos
       :recent-moves (utils/update-recent-moves recent-moves next-pos)
       :found-coast on-coast?
       :events events})))

;; Public version for testing
(def seek-coast-action-public seek-coast-action)

(defn- follow-coast-action
  "Action: Compute next move while following the coast.
   On first move, uses explore-direction to pick initial direction.
   After first move, prefers moves that expose more unexplored territory.
   Returns {:move-to [row col], :recent-moves [...], :found-coast true, :events [...]}."
  [ctx]
  (let [fsm-data (get-in ctx [:entity :fsm-data])
        pos (:position fsm-data)
        recent-moves (or (:recent-moves fsm-data) [])
        explore-direction (:explore-direction fsm-data)
        computer-map (:computer-map ctx)
        all-moves (context/get-valid-army-moves ctx pos)
        next-pos (pick-following-move pos all-moves recent-moves computer-map explore-direction)
        ;; Check for adjacent free cities to report
        free-city (utils/find-adjacent-free-city pos)
        ;; Build events list: always include cells-discovered, optionally free-city
        events (cond-> [(utils/make-cells-discovered-event pos)]
                 free-city (conj (utils/make-free-city-event free-city)))]
    (when next-pos
      {:move-to next-pos
       :recent-moves (utils/update-recent-moves recent-moves next-pos)
       :found-coast true
       :events events})))

;; Public version for testing
(def follow-coast-action-public follow-coast-action)

(defn- skirt-city-action
  "Action: Compute next move while skirting around a port city.
   Keeps the city adjacent while moving toward the coast on the other side.
   Returns {:move-to [row col], :recent-moves [...], :city-being-skirted [row col],
            :skirt-start-pos [row col], :events [...]}."
  [ctx]
  (let [fsm-data (get-in ctx [:entity :fsm-data])
        pos (:position fsm-data)
        recent-moves (or (:recent-moves fsm-data) [])
        computer-map (:computer-map ctx)
        all-moves (context/get-valid-army-moves ctx pos)
        ;; Get or detect the city being skirted
        ;; Only use stored city if it's still adjacent; otherwise detect fresh
        stored-city (:city-being-skirted fsm-data)
        city-pos (if (and stored-city (adjacent-to? pos stored-city))
                   stored-city
                   (find-adjacent-port-city pos))
        ;; Record start position if not already set
        start-pos (or (:skirt-start-pos fsm-data) pos)
        next-pos (when city-pos
                   (pick-skirting-move pos all-moves recent-moves city-pos computer-map))
        ;; Check for adjacent free cities to report
        free-city (utils/find-adjacent-free-city pos)
        ;; Build events list: always include cells-discovered, optionally free-city
        events (cond-> [(utils/make-cells-discovered-event pos)]
                 free-city (conj (utils/make-free-city-event free-city)))]
    ;; Debug logging when stuck
    (when (nil? next-pos)
      (let [adjacent-to-city (filter #(adjacent-to? % city-pos) all-moves)]
        (debug/log-action! [:skirt-stuck pos
                            {:city-pos city-pos
                             :all-moves (vec all-moves)
                             :adjacent-to-city (vec adjacent-to-city)
                             :recent-moves (vec recent-moves)}])))
    (when next-pos
      {:move-to next-pos
       :recent-moves (utils/update-recent-moves recent-moves next-pos)
       :city-being-skirted city-pos
       :skirt-start-pos start-pos
       :found-coast true
       :events events})))

;; Public version for testing
(def skirt-city-action-public skirt-city-action)

(defn- move-to-start-action
  "Action: Compute next move toward the destination using pathfinding.
   Returns {:move-to [row col], :destination [row col], :recent-moves [...]}."
  [ctx]
  (let [fsm-data (get-in ctx [:entity :fsm-data])
        pos (:position fsm-data)
        dest (:destination fsm-data)
        recent-moves (or (:recent-moves fsm-data) [])
        next-pos (pathfinding/next-step-toward pos dest)]
    (when next-pos
      {:move-to next-pos
       :destination dest
       :recent-moves (utils/update-recent-moves recent-moves next-pos)})))

;; Public version for testing
(def move-to-start-action-public move-to-start-action)

(defn- arrive-at-start-action
  "Action: Called when arriving at destination. Sets up for exploration
   and computes the first exploration move by delegating to the appropriate action.
   Returns exploration data based on whether destination is coastal."
  [ctx]
  (let [fsm-data (get-in ctx [:entity :fsm-data])
        pos (:position fsm-data)
        on-coast? (map-utils/adjacent-to-sea? pos atoms/game-map)
        ;; Update context with cleared destination for the next action
        setup-data {:destination nil
                    :recent-moves [pos]
                    :found-coast on-coast?}
        updated-ctx (update-in ctx [:entity :fsm-data] merge setup-data)
        ;; Delegate to the appropriate action to compute the first move
        next-action-result (if on-coast?
                             (follow-coast-action updated-ctx)
                             (seek-coast-action updated-ctx))]
    ;; Merge setup data with the action result
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

(def coastline-explorer-fsm
  "FSM transitions for coastline explorer.
   Format: [current-state guard-fn new-state action-fn]

   States:
   - :moving-to-start - Moving to Lieutenant-directed starting position
   - :seeking-coast   - Heading in random direction toward coast
   - :following-coast - Following the coastline
   - :skirting-city   - Walking around a port city blocking the coastal path
   - [:terminal :stuck] - No valid moves available, mission ended"
  [;; Moving to start transitions
   [:moving-to-start  stuck?                        [:terminal :stuck] terminal-action]
   [:moving-to-start  at-destination-on-coast?      :following-coast   arrive-at-start-action]
   [:moving-to-start  at-destination-not-on-coast?  :seeking-coast     arrive-at-start-action]
   [:moving-to-start  not-at-destination?           :moving-to-start   move-to-start-action]
   ;; Seeking coast transitions
   [:seeking-coast    stuck?         [:terminal :stuck] terminal-action]
   [:seeking-coast    on-coast?      :following-coast   follow-coast-action]
   [:seeking-coast    not-on-coast?  :seeking-coast     seek-coast-action]
   ;; Following coast transitions
   [:following-coast  stuck?         [:terminal :stuck] terminal-action]
   [:following-coast  at-port-city?  :skirting-city     skirt-city-action]
   [:following-coast  always         :following-coast   follow-coast-action]
   ;; Skirting city transitions
   [:skirting-city    stuck?         [:terminal :stuck] terminal-action]
   [:skirting-city    back-on-coast? :following-coast   follow-coast-action]
   [:skirting-city    always         :skirting-city     skirt-city-action]])

;; --- Create Explorer ---

(defn create-explorer-data
  "Create FSM data for a new coastline explorer mission at given position.
   Optional target parameter sets destination for :moving-to-start state.
   Optional unit-id parameter identifies the unit for mission tracking."
  ([pos]
   (create-explorer-data pos nil nil))
  ([pos target]
   (create-explorer-data pos target nil))
  ([pos target unit-id]
   (let [on-coast? (map-utils/adjacent-to-sea? pos atoms/game-map)
         has-target? (and target (not= pos target))]
     {:fsm coastline-explorer-fsm
      :fsm-state (cond
                   has-target? :moving-to-start
                   on-coast? :following-coast
                   :else :seeking-coast)
      :fsm-data {:position pos
                 :destination (when has-target? target)
                 :explore-direction (random-direction)
                 :recent-moves [pos]
                 :found-coast on-coast?
                 :unit-id unit-id}})))
