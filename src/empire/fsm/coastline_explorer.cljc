(ns empire.fsm.coastline-explorer
  "Coastline Explorer FSM - drives army exploration behavior.
   Phase 1: Head in random direction until reaching coast.
   Phase 2: Follow coastline, avoiding backtracking.
   Phase 3: Skirt around port cities blocking the coastal path."
  (:require [empire.atoms :as atoms]
            [empire.movement.map-utils :as map-utils]
            [empire.fsm.context :as context]))

(def backtrack-limit 10)

;; --- City Detection ---

(defn find-adjacent-free-city
  "Find a free city adjacent to the given position. Returns [row col] or nil."
  [pos]
  (first (map-utils/get-matching-neighbors pos @atoms/game-map map-utils/neighbor-offsets
                                           #(and (= :city (:type %))
                                                 (= :free (:city-status %))))))

(defn find-adjacent-port-city
  "Find any city adjacent to position that is also adjacent to sea (port city).
   Returns [row col] or nil. Does not care about city ownership."
  [pos]
  (first (filter (fn [city-pos]
                   (map-utils/adjacent-to-sea? city-pos atoms/game-map))
                 (map-utils/get-matching-neighbors pos @atoms/game-map map-utils/neighbor-offsets
                                                   #(= :city (:type %))))))

(defn- make-free-city-event
  "Create a :free-city-found event for the given city position."
  [city-pos]
  {:type :free-city-found
   :priority :high
   :data {:coords city-pos}})

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

(defn- count-unexplored-neighbors
  "Count how many neighbors of pos are unexplored in the computer-map."
  [pos computer-map]
  (count (filter (fn [[dr dc]]
                   (let [nr (+ (first pos) dr)
                         nc (+ (second pos) dc)
                         cell (get-in computer-map [nr nc])]
                     (= :unexplored (:type cell))))
                 map-utils/neighbor-offsets)))

(defn- pick-best-by-unexplored
  "From a list of moves, pick the one(s) with most unexplored neighbors.
   Returns a random choice among the best moves."
  [moves computer-map]
  (when (seq moves)
    (let [scored (map (fn [m] [m (count-unexplored-neighbors m computer-map)]) moves)
          max-score (apply max (map second scored))
          best-moves (map first (filter #(= (second %) max-score) scored))]
      (rand-nth best-moves))))

(defn pick-following-move
  "Pick a move while following the coast. Stay adjacent to sea, avoid backtracking.
   Prefers moves that expose more unexplored territory."
  [pos all-moves recent-moves computer-map]
  (let [coastal-moves (filter #(map-utils/adjacent-to-sea? % atoms/game-map) all-moves)
        non-backtrack-coastal (remove (set recent-moves) coastal-moves)
        non-backtrack-any (remove (set recent-moves) all-moves)]
    (cond
      (seq non-backtrack-coastal) (pick-best-by-unexplored non-backtrack-coastal computer-map)
      (seq coastal-moves) (pick-best-by-unexplored coastal-moves computer-map)
      (seq non-backtrack-any) (pick-best-by-unexplored non-backtrack-any computer-map)
      (seq all-moves) (rand-nth all-moves)
      :else nil)))

(defn- adjacent-to?
  "Returns true if pos1 is adjacent to pos2 (including diagonals)."
  [[r1 c1] [r2 c2]]
  (and (<= (Math/abs (- r1 r2)) 1)
       (<= (Math/abs (- c1 c2)) 1)
       (not (and (= r1 r2) (= c1 c2)))))

(defn pick-skirting-move
  "Pick a move while skirting around a city. Stay adjacent to the city while
   moving toward the coast. Avoid backtracking."
  [pos all-moves recent-moves city-pos computer-map]
  (let [;; Prefer moves that stay adjacent to the city being skirted
        adjacent-to-city (filter #(adjacent-to? % city-pos) all-moves)
        ;; Among those, prefer moves that get us closer to coast
        coastal-adjacent (filter #(map-utils/adjacent-to-sea? % atoms/game-map) adjacent-to-city)
        ;; Avoid backtracking
        non-backtrack-coastal (remove (set recent-moves) coastal-adjacent)
        non-backtrack-adjacent (remove (set recent-moves) adjacent-to-city)
        non-backtrack-any (remove (set recent-moves) all-moves)]
    (cond
      ;; Best: adjacent to city, coastal, not backtracking
      (seq non-backtrack-coastal) (pick-best-by-unexplored non-backtrack-coastal computer-map)
      ;; Good: adjacent to city, coastal (even if backtracking)
      (seq coastal-adjacent) (pick-best-by-unexplored coastal-adjacent computer-map)
      ;; OK: adjacent to city, not backtracking
      (seq non-backtrack-adjacent) (pick-best-by-unexplored non-backtrack-adjacent computer-map)
      ;; Fallback: any adjacent to city
      (seq adjacent-to-city) (pick-best-by-unexplored adjacent-to-city computer-map)
      ;; Last resort: any non-backtrack move
      (seq non-backtrack-any) (pick-best-by-unexplored non-backtrack-any computer-map)
      ;; Truly stuck
      (seq all-moves) (rand-nth all-moves)
      :else nil)))

;; --- Unit Integration ---

(defn random-direction
  "Returns a random direction offset from the 8 possible directions."
  []
  (rand-nth [[-1 -1] [-1 0] [-1 1] [0 -1] [0 1] [1 -1] [1 0] [1 1]]))

(defn update-recent-moves
  "Adds new position to recent-moves, keeping only the last backtrack-limit entries."
  [recent-moves new-pos]
  (let [updated (conj (vec recent-moves) new-pos)]
    (if (> (count updated) backtrack-limit)
      (vec (drop (- (count updated) backtrack-limit) updated))
      updated)))

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
        free-city (find-adjacent-free-city pos)
        events (when free-city [(make-free-city-event free-city)])]
    (when next-pos
      (cond-> {:move-to next-pos
               :recent-moves (update-recent-moves recent-moves next-pos)
               :found-coast on-coast?}
        events (assoc :events events)))))

;; Public version for testing
(def seek-coast-action-public seek-coast-action)

(defn- follow-coast-action
  "Action: Compute next move while following the coast.
   Prefers moves that expose more unexplored territory.
   Returns {:move-to [row col], :recent-moves [...], :found-coast true, :events [...]}."
  [ctx]
  (let [fsm-data (get-in ctx [:entity :fsm-data])
        pos (:position fsm-data)
        recent-moves (or (:recent-moves fsm-data) [])
        computer-map (:computer-map ctx)
        all-moves (context/get-valid-army-moves ctx pos)
        next-pos (pick-following-move pos all-moves recent-moves computer-map)
        ;; Check for adjacent free cities to report
        free-city (find-adjacent-free-city pos)
        events (when free-city [(make-free-city-event free-city)])]
    (when next-pos
      (cond-> {:move-to next-pos
               :recent-moves (update-recent-moves recent-moves next-pos)
               :found-coast true}
        events (assoc :events events)))))

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
        city-pos (or (:city-being-skirted fsm-data)
                     (find-adjacent-port-city pos))
        ;; Record start position if not already set
        start-pos (or (:skirt-start-pos fsm-data) pos)
        next-pos (when city-pos
                   (pick-skirting-move pos all-moves recent-moves city-pos computer-map))
        ;; Check for adjacent free cities to report
        free-city (find-adjacent-free-city pos)
        events (when free-city [(make-free-city-event free-city)])]
    (when next-pos
      (cond-> {:move-to next-pos
               :recent-moves (update-recent-moves recent-moves next-pos)
               :city-being-skirted city-pos
               :skirt-start-pos start-pos
               :found-coast true}
        events (assoc :events events)))))

;; Public version for testing
(def skirt-city-action-public skirt-city-action)

;; --- FSM Definition ---

(def coastline-explorer-fsm
  "FSM transitions for coastline explorer.
   Format: [current-state guard-fn new-state action-fn]

   States:
   - :seeking-coast   - Heading in random direction toward coast
   - :following-coast - Following the coastline
   - :skirting-city   - Walking around a port city blocking the coastal path"
  [[:seeking-coast    on-coast?      :following-coast follow-coast-action]
   [:seeking-coast    not-on-coast?  :seeking-coast   seek-coast-action]
   [:following-coast  at-port-city?  :skirting-city   skirt-city-action]
   [:following-coast  always         :following-coast follow-coast-action]
   [:skirting-city    back-on-coast? :following-coast follow-coast-action]
   [:skirting-city    always         :skirting-city   skirt-city-action]])

;; --- Create Explorer ---

(defn create-explorer-data
  "Create FSM data for a new coastline explorer mission at given position."
  [pos]
  (let [on-coast? (map-utils/adjacent-to-sea? pos atoms/game-map)]
    {:fsm coastline-explorer-fsm
     :fsm-state (if on-coast? :following-coast :seeking-coast)
     :fsm-data {:position pos
                :explore-direction (random-direction)
                :recent-moves [pos]
                :found-coast on-coast?}}))
