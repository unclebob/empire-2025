(ns empire.fsm.coastline-explorer
  "Coastline Explorer FSM - drives army exploration behavior.
   Phase 1: Head in random direction until reaching coast.
   Phase 2: Follow coastline, avoiding backtracking."
  (:require [empire.atoms :as atoms]
            [empire.movement.map-utils :as map-utils]
            [empire.fsm.context :as context]))

(def backtrack-limit 10)

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
    (boolean (map-utils/adjacent-to-sea? pos atoms/game-map))))

(defn- not-on-coast?
  "Guard: Returns true if unit is not on a coastal cell."
  [ctx]
  (not (on-coast? ctx)))

(defn- always
  "Guard: Always returns true."
  [_ctx]
  true)

;; --- Actions ---

(defn- seek-coast-action
  "Action: Compute next move while seeking the coast.
   Returns {:move-to [row col], :recent-moves [...], :found-coast bool}."
  [ctx]
  (let [fsm-data (get-in ctx [:entity :fsm-data])
        pos (:position fsm-data)
        recent-moves (or (:recent-moves fsm-data) [])
        explore-direction (:explore-direction fsm-data)
        all-moves (context/get-valid-army-moves ctx pos)
        next-pos (pick-seeking-move pos all-moves recent-moves explore-direction)
        on-coast? (boolean (map-utils/adjacent-to-sea? pos atoms/game-map))]
    (when next-pos
      {:move-to next-pos
       :recent-moves (update-recent-moves recent-moves next-pos)
       :found-coast on-coast?})))

(defn- follow-coast-action
  "Action: Compute next move while following the coast.
   Prefers moves that expose more unexplored territory.
   Returns {:move-to [row col], :recent-moves [...], :found-coast true}."
  [ctx]
  (let [fsm-data (get-in ctx [:entity :fsm-data])
        pos (:position fsm-data)
        recent-moves (or (:recent-moves fsm-data) [])
        computer-map (:computer-map ctx)
        all-moves (context/get-valid-army-moves ctx pos)
        next-pos (pick-following-move pos all-moves recent-moves computer-map)]
    (when next-pos
      {:move-to next-pos
       :recent-moves (update-recent-moves recent-moves next-pos)
       :found-coast true})))

;; --- FSM Definition ---

(def coastline-explorer-fsm
  "FSM transitions for coastline explorer.
   Format: [current-state guard-fn new-state action-fn]

   States:
   - :seeking-coast  - Heading in random direction toward coast
   - :following-coast - Following the coastline"
  [[:seeking-coast on-coast? :following-coast follow-coast-action]
   [:seeking-coast not-on-coast? :seeking-coast seek-coast-action]
   [:following-coast always :following-coast follow-coast-action]])

;; --- Create Explorer ---

(defn create-explorer-data
  "Create FSM data for a new coastline explorer mission at given position."
  [pos]
  (let [on-coast? (boolean (map-utils/adjacent-to-sea? pos atoms/game-map))]
    {:fsm coastline-explorer-fsm
     :fsm-state (if on-coast? :following-coast :seeking-coast)
     :fsm-data {:position pos
                :explore-direction (random-direction)
                :recent-moves [pos]
                :found-coast on-coast?}}))
