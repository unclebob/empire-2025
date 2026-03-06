;; mutation-tested: 2026-03-03
(ns empire.computer.ship-core
  "Core ship utilities shared by patrol, escort, and carrier sub-modules."
  (:require [empire.application.ports.movement-execution :as movement-port]
            [empire.application.ports.pathfinding :as path-ports]
            [empire.state.api :as sa]
            [empire.application.combat :as combat]
            [empire.computer.core :as core]
            [empire.computer.threat :as threat]
            [empire.containers.helpers :as uc]))

(defn- execution-port
  []
  (:execution-port (sa/state-ctx)))

(defn- pathfinding-port
  []
  (:pathfinding-port (sa/state-ctx)))

(defn- update-cell-visibility!
  ([pos owner]
   (movement-port/movement-update-cell-visibility (execution-port) pos owner))
  ([pos owner unit]
   (movement-port/movement-update-cell-visibility-with-unit (execution-port) pos owner unit)))

(defn- set-turn-message!
  [msg ms]
  (sa/write-state! :turn-message msg)
  (sa/write-state! :turn-message-until (if (= ms Long/MAX_VALUE)
                                               Long/MAX_VALUE
                                               (+ (System/currentTimeMillis) ms))))

(defn get-passable-sea-neighbors
  [pos]
  (let [game-map (sa/current-world)]
    (filter (fn [neighbor]
              (let [cell (get-in game-map neighbor)]
                (and cell
                     (= :sea (:type cell))
                     (or (nil? (:contents cell))
                         (= :player (:owner (:contents cell)))))))
            (core/get-neighbors pos))))

(defn find-adjacent-enemy-ship
  [pos]
  (let [game-map (sa/current-world)]
    (first (filter (fn [neighbor]
                     (let [cell (get-in game-map neighbor)
                           unit (:contents cell)]
                       (and unit
                            (= :player (:owner unit))
                            (#{:patrol-boat :destroyer :submarine :transport
                               :carrier :battleship} (:type unit)))))
                   (core/get-neighbors pos)))))

(defn attack-enemy
  [ship-pos enemy-pos]
  (let [attacker (get-in (sa/current-world) (conj ship-pos :contents))
        defender (get-in (sa/current-world) (conj enemy-pos :contents))
        result (combat/resolve-combat attacker defender)
        message (combat/format-combat-status (:log result)
                                             (:type attacker)
                                             (:type defender)
                                             (:winner result))
        dead-unit (if (= :attacker (:winner result)) defender attacker)]
    (set-turn-message! message Long/MAX_VALUE)
    (sa/update-world! update-in ship-pos dissoc :contents)
    (if (= :attacker (:winner result))
      (do
        (sa/update-world! assoc-in (conj enemy-pos :contents) (:survivor result))
        (when (= :carrier (:type attacker))
          (sa/update-state! :computer-carrier-positions disj ship-pos)
          (sa/update-state! :computer-carrier-positions (fnil conj #{}) enemy-pos))
        (update-cell-visibility! ship-pos :computer)
        (update-cell-visibility! enemy-pos :computer)
        (combat/clear-escort-on-death dead-unit)
        enemy-pos)
      (do
        (sa/update-world! assoc-in (conj enemy-pos :contents) (:survivor result))
        (when (= :carrier (:type attacker))
          (sa/update-state! :computer-carrier-positions disj ship-pos))
        (update-cell-visibility! ship-pos :computer)
        (combat/clear-escort-on-death dead-unit)
        nil))))

(defn move-toward
  [pos target]
  (let [passable (get-passable-sea-neighbors pos)
        closest (core/move-toward pos target passable)]
    (when closest
      (core/move-unit-to pos closest)
      (update-cell-visibility! pos :computer)
      (update-cell-visibility! closest :computer)
      closest)))

(defn explore-sea
  [pos ship-type]
  (when-let [target (path-ports/movement-find-nearest-unexplored (pathfinding-port) pos ship-type)]
    (move-toward pos target)))

(defn find-player-ship-sighting
  [pos]
  (let [player-units (core/find-visible-player-units)]
    (when (seq player-units)
      (apply min-key (partial core/distance pos) player-units))))

(defn retreat-if-damaged
  [pos unit]
  (let [comp-map (sa/read-state :computer-map)]
    (when (threat/should-retreat? pos unit comp-map)
    (let [passable (get-passable-sea-neighbors pos)]
      (threat/retreat-move pos unit comp-map passable)))))

(defn find-computer-transports
  []
  (let [game-map (sa/current-world)]
    (for [i (range (count game-map))
          j (range (count (first game-map)))
          :let [cell (get-in game-map [i j])
                unit (:contents cell)]
          :when (and unit
                     (= :computer (:owner unit))
                     (= :transport (:type unit)))]
      [i j])))

(defn find-nearest-transport
  [pos]
  (let [transports (find-computer-transports)]
    (when (seq transports)
      (apply min-key (partial core/distance pos) transports))))

(defn find-adjacent-dock-city
  [pos unit]
  (first (filter (fn [neighbor]
                   (uc/ship-can-dock? unit (get-in (sa/current-world) neighbor)))
                 (core/get-neighbors pos))))

(defn dock-computer-ship
  [ship-pos city-pos]
  (let [cell (get-in (sa/current-world) ship-pos)
        unit (:contents cell)
        city-cell (get-in (sa/current-world) city-pos)
        updated-city (uc/add-ship-to-shipyard city-cell (:type unit) (:hits unit))]
    (sa/update-world! assoc-in ship-pos (dissoc cell :contents))
    (sa/update-world! assoc-in city-pos updated-city)
    (update-cell-visibility! city-pos :computer)
    city-pos))
