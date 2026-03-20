(ns empire.computer.shared.world-query
  (:require [empire.computer.shared.grid :as grid]
            [empire.state.api :as sa]))

(defn get-neighbors
  [pos]
  (grid/neighbors-in-map (sa/read-state :computer-map) pos))

(defn attackable-target?
  [cell]
  (or (and (= (:type cell) :city)
           (#{:player :free} (:city-status cell)))
      (and (:contents cell)
           (= (:owner (:contents cell)) :player)
           (not= :satellite (:type (:contents cell))))))

(defn find-visible-cities
  [status-pred]
  (let [comp-map (sa/read-state :computer-map)]
    (for [i (range (count comp-map))
          j (range (count (first comp-map)))
          :let [cell (get-in comp-map [i j])]
          :when (and (= (:type cell) :city)
                     (status-pred (:city-status cell)))]
      [i j])))

(defn adjacent-to-computer-unexplored?
  [pos]
  (let [comp-map (sa/read-state :computer-map)]
    (boolean (some #(nil? (get-in comp-map %))
                   (grid/neighbors-in-map comp-map pos)))))

(defn find-visible-player-units
  []
  (let [comp-map (sa/read-state :computer-map)]
    (for [i (range (count comp-map))
          j (range (count (first comp-map)))
          :let [cell (get-in comp-map [i j])
                contents (:contents cell)]
          :when (and contents (= (:owner contents) :player))]
      [i j])))
