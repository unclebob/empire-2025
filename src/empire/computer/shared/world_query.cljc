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

(defn passable-sea-neighbors
  [pos allowed-occupant-owner]
  (let [game-map (sa/read-state :computer-map)]
    (filter (fn [neighbor]
              (let [cell (get-in game-map neighbor)]
                (and (or (nil? cell)
                         (= :sea (:type cell))
                         (= :unexplored (:type cell)))
                     (or (nil? (:contents cell))
                         (= allowed-occupant-owner (:owner (:contents cell)))))))
            (get-neighbors pos))))

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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-26T23:20:18.411606-05:00", :module-hash "1541382041", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 3, :hash "-1029111029"} {:id "defn/get-neighbors", :kind "defn", :line 5, :end-line 7, :hash "-525407268"} {:id "defn/attackable-target?", :kind "defn", :line 9, :end-line 15, :hash "-1762839441"} {:id "defn/find-visible-cities", :kind "defn", :line 17, :end-line 25, :hash "-1520668987"} {:id "defn/adjacent-to-computer-unexplored?", :kind "defn", :line 27, :end-line 31, :hash "-818678017"} {:id "defn/find-visible-player-units", :kind "defn", :line 33, :end-line 41, :hash "-1696683775"}]}
;; clj-mutate-manifest-end
