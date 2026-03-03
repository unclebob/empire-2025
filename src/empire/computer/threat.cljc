;; mutation-tested: 2026-03-02
(ns empire.computer.threat
  (:require [empire.application.runtime :as app-runtime]
            [empire.units.dispatcher :as dispatcher]))

(def ^:private state-ctx
  (delay (app-runtime/default-state-ctx)))

(defn- read-runtime-state
  [k]
  ((:read-runtime-state @state-ctx) k))

(def ^:private threat-values
  {:battleship 10 :carrier 8 :destroyer 6 :submarine 5
   :fighter 4 :patrol-boat 3 :army 2 :transport 1})

(defn unit-threat
  "Returns threat value for a unit type.
   Higher values = more dangerous."
  [unit-type]
  (get threat-values unit-type 0))

(defn threat-level
  "Calculates threat level at position based on nearby enemy units.
   Checks all cells within radius 2 of position.
   Returns sum of threat values for nearby enemy units."
  [computer-map position]
  (let [radius 2
        [px py] position]
    (reduce + 0
            (for [dx (range (- radius) (inc radius))
                  dy (range (- radius) (inc radius))
                  :let [x (+ px dx)
                        y (+ py dy)
                        cell (get-in computer-map [x y])]
                  :when (and cell
                             (:contents cell)
                             (= (:owner (:contents cell)) :player))]
              (unit-threat (:type (:contents cell)))))))

(defn safe-moves
  "Filters moves to avoid high-threat areas when unit is damaged.
   Returns moves sorted by threat level (safest first).
   If unit is at full health, returns all moves unchanged."
  [computer-map _position unit possible-moves]
  (let [max-hits (dispatcher/hits (:type unit))
        current-hits (:hits unit max-hits)
        damaged? (< current-hits max-hits)]
    (if damaged?
      (sort-by #(threat-level computer-map %) possible-moves)
      possible-moves)))

(defn should-retreat?
  "Returns true if the unit should retreat rather than engage."
  [pos unit computer-map]
  (let [unit-type (:type unit)
        max-hits (dispatcher/hits unit-type)
        current-hits (:hits unit max-hits)
        threat (threat-level computer-map pos)]
    (or
      ;; Damaged and under threat
      (and (< current-hits max-hits) (> threat 3))
      ;; Transport carrying armies - always cautious
      (and (= unit-type :transport)
           (> (:army-count unit 0) 0)
           (> threat 5))
      ;; Severely damaged (< 50% health)
      (< current-hits (/ max-hits 2)))))

(defn- find-visible-cities
  "Finds cities visible on computer-map matching the status predicate."
  [status-pred]
  (let [comp-map (read-runtime-state :computer-map)]
    (for [i (range (count comp-map))
          j (range (count (first comp-map)))
          :let [cell (get-in comp-map [i j])]
        :when (and (= (:type cell) :city)
                   (status-pred (:city-status cell)))]
      [i j])))

(defn distance
  "Manhattan distance between two positions."
  [[x1 y1] [x2 y2]]
  (+ (Math/abs (- x2 x1)) (Math/abs (- y2 y1))))

(defn find-nearest-friendly-base
  "Finds the nearest computer-owned city."
  [pos _unit-type]
  (let [cities (find-visible-cities #{:computer})]
    (when (seq cities)
      (apply min-key #(distance pos %) cities))))

(defn retreat-move
  "Returns best retreat move toward nearest friendly city.
   Returns nil if no safe retreat available."
  [pos unit computer-map passable-moves]
  (when (seq passable-moves)
    (let [nearest-city (find-nearest-friendly-base pos (:type unit))]
      (when nearest-city
        (let [safe (safe-moves computer-map pos unit passable-moves)]
          (when (seq safe)
            ;; Pick move that's both safe and moves toward base
            (apply min-key #(+ (distance % nearest-city)
                               (* 2 (threat-level computer-map %)))
                   safe)))))))
