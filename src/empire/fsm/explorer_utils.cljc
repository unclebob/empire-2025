(ns empire.fsm.explorer-utils
  "Shared utilities for FSM explorers.
   Common functions for movement, event creation, and backtracking."
  (:require [empire.atoms :as atoms]
            [empire.movement.map-utils :as map-utils]))

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
