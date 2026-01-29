(ns empire.computer.ship
  "Computer ship module - VMS Empire style ship movement.
   Attack adjacent enemies, explore sea, protect transports, patrol."
  (:require [empire.atoms :as atoms]
            [empire.player.combat :as combat]
            [empire.computer.core :as core]
            [empire.computer.threat :as threat]
            [empire.movement.visibility :as visibility]))

(defn- get-passable-sea-neighbors
  "Returns passable sea neighbors for a ship."
  [pos]
  (let [game-map @atoms/game-map]
    (filter (fn [neighbor]
              (let [cell (get-in game-map neighbor)]
                (and cell
                     (= :sea (:type cell))
                     (or (nil? (:contents cell))
                         (= :player (:owner (:contents cell)))))))  ; Can attack player
            (core/get-neighbors pos))))

(defn- find-adjacent-enemy-ship
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

(defn- attack-enemy
  "Attack an adjacent enemy. Returns new position or nil if ship died."
  [ship-pos enemy-pos]
  (let [attacker (get-in @atoms/game-map (conj ship-pos :contents))
        defender (get-in @atoms/game-map (conj enemy-pos :contents))
        result (combat/resolve-combat attacker defender)]
    ;; Remove attacker from original position
    (swap! atoms/game-map update-in ship-pos dissoc :contents)
    (if (= :attacker (:winner result))
      ;; Attacker won - move to enemy position
      (do
        (swap! atoms/game-map assoc-in (conj enemy-pos :contents) (:survivor result))
        (visibility/update-cell-visibility ship-pos :computer)
        (visibility/update-cell-visibility enemy-pos :computer)
        enemy-pos)
      ;; Attacker lost
      (do
        (visibility/update-cell-visibility ship-pos :computer)
        nil))))

(defn- find-computer-transports
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

(defn- distance-to
  "Manhattan distance between two positions."
  [[r1 c1] [r2 c2]]
  (+ (Math/abs (- r1 r2)) (Math/abs (- c1 c2))))

(defn- find-nearest-transport
  "Find the nearest computer transport."
  [pos]
  (let [transports (find-computer-transports)]
    (when (seq transports)
      (apply min-key (partial distance-to pos) transports))))

(defn- move-toward
  "Move ship one step toward target."
  [pos target]
  (let [passable (get-passable-sea-neighbors pos)
        closest (core/move-toward pos target passable)]
    (when closest
      (core/move-unit-to pos closest)
      (visibility/update-cell-visibility pos :computer)
      (visibility/update-cell-visibility closest :computer)
      closest)))

(defn- explore-sea
  "Move toward unexplored sea territory."
  [pos]
  (let [passable (get-passable-sea-neighbors pos)
        frontier (filter core/adjacent-to-computer-unexplored? passable)
        target (or (first frontier) (first passable))]
    (when target
      (core/move-unit-to pos target)
      (visibility/update-cell-visibility pos :computer)
      (visibility/update-cell-visibility target :computer)
      target)))

(defn- find-player-ship-sighting
  "Find a recently seen player ship position."
  []
  ;; Look in computer-map for player ships
  (first (core/find-visible-player-units)))

(defn- retreat-if-damaged
  "If damaged and under threat, retreat toward friendly city."
  [pos unit]
  (when (threat/should-retreat? pos unit @atoms/computer-map)
    (let [passable (get-passable-sea-neighbors pos)]
      (threat/retreat-move pos unit @atoms/computer-map passable))))

(defn process-ship
  "Processes a computer ship using VMS Empire style logic.
   Priority: Retreat if damaged > Attack adjacent > Escort transports > Hunt enemies > Explore
   Returns nil after processing - ships only move once per round."
  [pos ship-type]
  (let [cell (get-in @atoms/game-map pos)
        unit (:contents cell)]
    (when (and unit
               (= :computer (:owner unit))
               (= ship-type (:type unit)))

      ;; Priority 0: Retreat if damaged and under threat
      (if-let [retreat-pos (retreat-if-damaged pos unit)]
        (do
          (core/move-unit-to pos retreat-pos)
          (visibility/update-cell-visibility pos :computer)
          (visibility/update-cell-visibility retreat-pos :computer))

        ;; Priority 1: Attack adjacent enemy ship
        (if-let [enemy-pos (find-adjacent-enemy-ship pos)]
          (attack-enemy pos enemy-pos)

          ;; Priority 2: Destroyers escort transports
          (if (and (= :destroyer ship-type)
                   (find-nearest-transport pos))
            (let [transport-pos (find-nearest-transport pos)]
              (if (> (distance-to pos transport-pos) 2)
                (move-toward pos transport-pos)
                ;; Already close - patrol nearby
                (explore-sea pos)))

            ;; Priority 3: Hunt player ships
            (if-let [enemy-sighting (find-player-ship-sighting)]
              (move-toward pos enemy-sighting)

              ;; Priority 4: Explore sea
              (explore-sea pos)))))))
  nil)
