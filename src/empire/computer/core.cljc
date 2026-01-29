(ns empire.computer.core
  "Shared utilities for computer AI modules."
  (:require [empire.atoms :as atoms]
            [empire.move-log :as move-log]
            [empire.movement.map-utils :as map-utils]
            [empire.movement.visibility :as visibility]))

(defn get-neighbors
  "Returns valid neighbor coordinates for a position."
  [pos]
  (map-utils/get-matching-neighbors pos @atoms/game-map map-utils/neighbor-offsets
                                    some?))

(defn distance
  "Manhattan distance between two positions."
  [[x1 y1] [x2 y2]]
  (+ (Math/abs (- x2 x1)) (Math/abs (- y2 y1))))

(defn attackable-target?
  "Returns true if the cell contains an attackable target for the computer."
  [cell]
  (or (and (= (:type cell) :city)
           (#{:player :free} (:city-status cell)))
      (and (:contents cell)
           (= (:owner (:contents cell)) :player))))

(defn find-visible-cities
  "Finds cities visible on computer-map matching the status predicate."
  [status-pred]
  (for [i (range (count @atoms/computer-map))
        j (range (count (first @atoms/computer-map)))
        :let [cell (get-in @atoms/computer-map [i j])]
        :when (and (= (:type cell) :city)
                   (status-pred (:city-status cell)))]
    [i j]))

(defn move-toward
  "Returns the neighbor that moves closest to target."
  [pos target passable-neighbors]
  (when (seq passable-neighbors)
    (apply min-key #(distance % target) passable-neighbors)))

(defn adjacent-to-computer-unexplored?
  "Returns true if the position has an adjacent unexplored cell on computer-map."
  [pos]
  (map-utils/any-neighbor-matches? pos @atoms/computer-map map-utils/neighbor-offsets
                                   nil?))

(defn move-unit-to
  "Moves a unit from from-pos to to-pos. Returns to-pos if moved, nil if target occupied."
  [from-pos to-pos]
  (let [from-cell (get-in @atoms/game-map from-pos)
        to-cell (get-in @atoms/game-map to-pos)
        unit (:contents from-cell)]
    (if (:contents to-cell)
      nil
      (do
        (move-log/log-move! from-pos to-pos (:type unit) (:owner unit) "computer-ai")
        (swap! atoms/game-map assoc-in from-pos (dissoc from-cell :contents))
        (swap! atoms/game-map assoc-in (conj to-pos :contents) unit)
        to-pos))))

(defn attempt-conquest-computer
  "Computer army attempts to conquer a city. Returns new position or nil if army died."
  [army-pos city-pos]
  (let [army-cell (get-in @atoms/game-map army-pos)
        city-cell (get-in @atoms/game-map city-pos)]
    (if (< (rand) 0.5)
      ;; Success - conquer the city, army dies
      (do
        (swap! atoms/game-map assoc-in army-pos (dissoc army-cell :contents))
        (swap! atoms/game-map assoc-in city-pos (assoc city-cell :city-status :computer))
        (visibility/update-cell-visibility army-pos :computer)
        (visibility/update-cell-visibility city-pos :computer)
        nil)
      ;; Failure - army dies
      (do
        (swap! atoms/game-map assoc-in army-pos (dissoc army-cell :contents))
        (visibility/update-cell-visibility army-pos :computer)
        nil))))

(defn- adjacent?
  "Returns true if pos1 and pos2 are adjacent (including diagonally)."
  [pos1 pos2]
  (let [[r1 c1] pos1
        [r2 c2] pos2
        dr (Math/abs (- r2 r1))
        dc (Math/abs (- c2 c1))]
    (and (<= dr 1) (<= dc 1) (not (and (zero? dr) (zero? dc))))))

(defn board-transport
  "Loads army onto transport. Removes army from pos, increments transport army count.
   Verifies adjacency before loading - throws if positions are not adjacent."
  [army-pos transport-pos]
  (when-not (adjacent? army-pos transport-pos)
    (throw (ex-info "Cannot board transport from non-adjacent cell"
                    {:army-pos army-pos :transport-pos transport-pos})))
  (swap! atoms/game-map update-in army-pos dissoc :contents)
  (swap! atoms/game-map update-in (conj transport-pos :contents :army-count) (fnil inc 0)))

(defn find-visible-player-units
  "Finds player units visible on computer-map."
  []
  (for [i (range (count @atoms/computer-map))
        j (range (count (first @atoms/computer-map)))
        :let [cell (get-in @atoms/computer-map [i j])
              contents (:contents cell)]
        :when (and contents (= (:owner contents) :player))]
    [i j]))

;; Army-Transport Coordination (used by army module)

(defn find-loading-transport
  "Finds a transport in loading state that has room."
  []
  (first (for [i (range (count @atoms/game-map))
               j (range (count (first @atoms/game-map)))
               :let [cell (get-in @atoms/game-map [i j])
                     unit (:contents cell)]
               :when (and unit
                          (= :computer (:owner unit))
                          (= :transport (:type unit))
                          (= :loading (:transport-mission unit))
                          (< (:army-count unit 0) 6))]
           [i j])))

(defn find-adjacent-loading-transport
  "Finds an adjacent loading transport with room."
  [pos]
  (first (filter (fn [neighbor]
                   (let [cell (get-in @atoms/game-map neighbor)
                         unit (:contents cell)]
                     (and unit
                          (= :computer (:owner unit))
                          (= :transport (:type unit))
                          (= :loading (:transport-mission unit))
                          (< (:army-count unit 0) 6))))
                 (get-neighbors pos))))

(defn army-should-board-transport?
  "Returns true if army should move toward a loading transport.
   Only returns true if there are loading transports AND no land route to targets."
  [army-pos pathfinding-next-step-fn]
  (when (find-loading-transport)  ; Only check if there's a transport to board
    (let [free-cities (find-visible-cities #{:free})
          player-cities (find-visible-cities #{:player})
          all-targets (concat free-cities player-cities)]
      ;; Board transport if no cities reachable by land
      (and (seq all-targets)
           (not-any? #(pathfinding-next-step-fn army-pos % :army) all-targets)))))
