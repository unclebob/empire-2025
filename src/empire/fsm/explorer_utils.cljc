(ns empire.fsm.explorer-utils
  "Shared utilities for FSM explorers.
   Common functions for movement, event creation, and backtracking."
  (:require [empire.atoms :as atoms]
            [empire.movement.map-utils :as map-utils]
            [empire.ui.coordinates :as coords]))

(def backtrack-limit 10)

;; --- Movement Utilities ---

(defn get-valid-moves
  "Get all valid moves for army at given position (empty land cells)."
  [pos game-map]
  (map-utils/get-matching-neighbors pos game-map map-utils/neighbor-offsets
                                    #(and (= :land (:type %))
                                          (nil? (:contents %)))))

(defn update-recent-moves
  "Add new position to recent-moves, keeping only last backtrack-limit entries."
  [recent-moves new-pos]
  (let [updated (conj (vec recent-moves) new-pos)]
    (if (> (count updated) backtrack-limit)
      (vec (drop (- (count updated) backtrack-limit) updated))
      updated)))

;; --- Terrain Detection ---

(defn on-coast?
  "Returns true if the position IS adjacent to sea."
  [pos]
  (map-utils/adjacent-to-sea? pos atoms/game-map))

(defn is-landlocked?
  "Returns true if the position is NOT adjacent to sea."
  [pos]
  (not (on-coast? pos)))

(defn- get-terrain-type
  "Returns :coastal if pos is land adjacent to sea, :landlocked otherwise."
  [pos]
  (if (on-coast? pos)
    :coastal
    :landlocked))

;; --- Free City Detection ---

(defn find-adjacent-free-city
  "Find a free city adjacent to the given position. Returns [row col] or nil."
  [pos]
  (first (map-utils/get-matching-neighbors pos @atoms/game-map map-utils/neighbor-offsets
                                           #(and (= :city (:type %))
                                                 (= :free (:city-status %))))))

;; --- Event Creation ---

(defn make-cells-discovered-event
  "Create a :cells-discovered event for the given position."
  [pos]
  {:type :cells-discovered
   :priority :low
   :data {:cells [{:pos pos :terrain (get-terrain-type pos)}]}})

(defn make-free-city-event
  "Create a :free-city-found event for the given city position."
  [city-pos]
  {:type :free-city-found
   :priority :high
   :data {:coords city-pos}})

(defn make-base-events
  "Create base events for any exploration action (cells-discovered, free-city)."
  [pos]
  (let [free-city (find-adjacent-free-city pos)]
    (cond-> [(make-cells-discovered-event pos)]
      free-city (conj (make-free-city-event free-city)))))

(defn make-mission-ended-event
  "Create a :mission-ended event with the given reason."
  [unit-id reason]
  {:type :mission-ended
   :priority :high
   :data {:unit-id unit-id :reason reason}})

;; --- Unexplored Cell Counting ---

(defn count-unexplored-neighbors
  "Count how many neighbors of pos are unexplored in the computer-map."
  [pos computer-map]
  (count (filter (fn [[dr dc]]
                   (let [nr (+ (first pos) dr)
                         nc (+ (second pos) dc)
                         cell (get-in computer-map [nr nc])]
                     (= :unexplored (:type cell))))
                 map-utils/neighbor-offsets)))

;; --- Position Utilities ---

(defn adjacent?
  "Returns true if pos1 and pos2 are orthogonally or diagonally adjacent."
  [pos1 pos2]
  (let [[r1 c1] pos1
        [r2 c2] pos2
        dr (Math/abs (- r1 r2))
        dc (Math/abs (- c1 c2))]
    (and (<= dr 1) (<= dc 1) (not (and (= dr 0) (= dc 0))))))

(defn blocked-by-army-or-city?
  "Returns true if the cell at pos contains a friendly army or is a friendly city."
  [pos game-map]
  (let [cell (get-in game-map pos)]
    (or
     (and (= :city (:type cell))
          (= :computer (:city-status cell)))
     (and (:contents cell)
          (= :army (:type (:contents cell)))
          (= :computer (:owner (:contents cell)))))))

;; --- Sidestep Movement ---

(defn find-sidestep-move
  "Find a move that makes progress toward target while avoiding direct path.
   Prefers non-backtrack moves. Returns [row col] or nil."
  [pos target recent-moves game-map]
  (let [valid-moves (get-valid-moves pos game-map)
        non-backtrack (remove (set recent-moves) valid-moves)
        moves-to-try (if (seq non-backtrack) non-backtrack valid-moves)]
    (when (seq moves-to-try)
      (let [scored (map (fn [m] [m (coords/manhattan-distance m target)]) moves-to-try)
            min-dist (apply min (map second scored))
            best-moves (filter #(= min-dist (second %)) scored)]
        (first (rand-nth best-moves))))))

;; --- Common Guards ---

(defn stuck?
  "Guard: Returns true if there are no valid moves available."
  [pos game-map]
  (empty? (get-valid-moves pos game-map)))

(defn always
  "Guard: Always returns true."
  [_ctx]
  true)
