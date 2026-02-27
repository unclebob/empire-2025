(ns empire.computer.ship-core
  "Core ship utilities shared by patrol, escort, and carrier sub-modules."
  (:require [empire.atoms :as atoms]
            [empire.combat :as combat]
            [empire.computer.core :as core]
            [empire.computer.threat :as threat]
            [empire.containers.helpers :as uc]
            [empire.movement.pathfinding-bfs :as pathfinding-bfs]
            [empire.movement.visibility :as visibility]))

(defn get-passable-sea-neighbors
  "Returns passable sea neighbors for a ship."
  [pos]
  (let [game-map @atoms/game-map]
    (filter (fn [neighbor]
              (let [cell (get-in game-map neighbor)]
                (and cell
                     (= :sea (:type cell))
                     (or (nil? (:contents cell))
                         (= :player (:owner (:contents cell)))))))
            (core/get-neighbors pos))))

(defn find-adjacent-enemy-ship
  "Finds an adjacent enemy ship to attack."
  [pos]
  (let [game-map @atoms/game-map]
    (first (filter (fn [neighbor]
                     (let [cell (get-in game-map neighbor)
                           unit (:contents cell)]
                       (and unit
                            (= :player (:owner unit))
                            (#{:patrol-boat :destroyer :submarine :transport
                               :carrier :battleship} (:type unit)))))
                   (core/get-neighbors pos)))))

(defn attack-enemy
  "Attack an adjacent enemy. Returns new position or nil if ship died."
  [ship-pos enemy-pos]
  (let [attacker (get-in @atoms/game-map (conj ship-pos :contents))
        defender (get-in @atoms/game-map (conj enemy-pos :contents))
        result (combat/resolve-combat attacker defender)
        dead-unit (if (= :attacker (:winner result)) defender attacker)]
    (swap! atoms/game-map update-in ship-pos dissoc :contents)
    (if (= :attacker (:winner result))
      (do
        (swap! atoms/game-map assoc-in (conj enemy-pos :contents) (:survivor result))
        (visibility/update-cell-visibility ship-pos :computer)
        (visibility/update-cell-visibility enemy-pos :computer)
        (combat/clear-escort-on-death dead-unit)
        enemy-pos)
      (do
        (visibility/update-cell-visibility ship-pos :computer)
        (combat/clear-escort-on-death dead-unit)
        nil))))

(defn move-toward
  "Move ship one step toward target."
  [pos target]
  (let [passable (get-passable-sea-neighbors pos)
        closest (core/move-toward pos target passable)]
    (when closest
      (core/move-unit-to pos closest)
      (visibility/update-cell-visibility pos :computer)
      (visibility/update-cell-visibility closest :computer)
      closest)))

(defn explore-sea
  "Move toward unexplored sea via BFS. Stays put if all sea is explored."
  [pos ship-type]
  (when-let [target (pathfinding-bfs/find-nearest-unexplored pos ship-type)]
    (move-toward pos target)))

(defn find-player-ship-sighting
  "Find the nearest visible player ship position."
  [pos]
  (let [player-units (core/find-visible-player-units)]
    (when (seq player-units)
      (apply min-key (partial core/distance pos) player-units))))

(defn retreat-if-damaged
  "If damaged and under threat, retreat toward friendly city."
  [pos unit]
  (when (threat/should-retreat? pos unit @atoms/computer-map)
    (let [passable (get-passable-sea-neighbors pos)]
      (threat/retreat-move pos unit @atoms/computer-map passable))))

(defn find-computer-transports
  "Find computer transports to protect."
  []
  (let [game-map @atoms/game-map]
    (for [i (range (count game-map))
          j (range (count (first game-map)))
          :let [cell (get-in game-map [i j])
                unit (:contents cell)]
          :when (and unit
                     (= :computer (:owner unit))
                     (= :transport (:type unit)))]
      [i j])))

(defn find-nearest-transport
  "Find the nearest computer transport."
  [pos]
  (let [transports (find-computer-transports)]
    (when (seq transports)
      (apply min-key (partial core/distance pos) transports))))

(defn find-adjacent-dock-city
  "Finds an adjacent friendly city where a damaged ship can dock for repair."
  [pos unit]
  (first (filter (fn [neighbor]
                   (uc/ship-can-dock? unit (get-in @atoms/game-map neighbor)))
                 (core/get-neighbors pos))))

(defn dock-computer-ship
  "Docks a damaged computer ship into a friendly city's shipyard."
  [ship-pos city-pos]
  (let [cell (get-in @atoms/game-map ship-pos)
        unit (:contents cell)
        city-cell (get-in @atoms/game-map city-pos)
        updated-city (uc/add-ship-to-shipyard city-cell (:type unit) (:hits unit))]
    (swap! atoms/game-map assoc-in ship-pos (dissoc cell :contents))
    (swap! atoms/game-map assoc-in city-pos updated-city)
    (visibility/update-cell-visibility city-pos :computer)
    city-pos))
