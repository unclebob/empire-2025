(ns empire.computer.ship
  (:require [empire.atoms :as atoms]
            [empire.combat :as combat]
            [empire.computer.core :as core]
            [empire.computer.threat :as threat]
            [empire.pathfinding :as pathfinding]))

(defn- find-adjacent-ship-target
  "Finds an adjacent attackable target for a ship (must be on sea).
   Ships can only attack player units on sea cells, not cities or land units.
   Uses computer-map to respect fog of war. Returns coords or nil."
  [pos]
  (first (filter (fn [neighbor]
                   (let [cell (get-in @atoms/computer-map neighbor)]
                     (and (= :sea (:type cell))
                          (:contents cell)
                          (= (:owner (:contents cell)) :player))))
                 (core/get-neighbors pos))))

(defn- can-ship-move-to?
  "Returns true if a ship can move to this cell."
  [cell]
  (and (= (:type cell) :sea)
       (or (nil? (:contents cell))
           (= (:owner (:contents cell)) :player))))

(defn find-passable-ship-neighbors
  "Returns neighbors a ship can move to."
  [pos]
  (filter (fn [neighbor]
            (let [cell (get-in @atoms/game-map neighbor)]
              (and (can-ship-move-to? cell)
                   (not (and (:contents cell)
                             (= (:owner (:contents cell)) :computer))))))
          (core/get-neighbors pos)))

(defn decide-ship-move
  "Decides where a computer ship should move. Returns target coords or nil.
   Priority: 1) Attack adjacent player ship 2) Retreat if damaged 3) Move toward player unit
   4) Patrol. Uses threat avoidance when damaged."
  [pos ship-type]
  (let [cell (get-in @atoms/game-map pos)
        unit (:contents cell)
        adjacent-target (find-adjacent-ship-target pos)
        passable (find-passable-ship-neighbors pos)]
    (cond
      ;; Attack adjacent target
      adjacent-target
      adjacent-target

      ;; Check if should retreat
      (threat/should-retreat? pos unit @atoms/computer-map)
      (threat/retreat-move pos unit @atoms/computer-map passable)

      ;; No valid moves
      (empty? passable)
      nil

      ;; Move toward nearest player unit with threat awareness
      :else
      (let [player-units (core/find-visible-player-units)
            safe-passable (threat/safe-moves @atoms/computer-map pos unit passable)]
        (if (seq player-units)
          (let [nearest (apply min-key #(core/distance pos %) player-units)]
            (or (pathfinding/next-step pos nearest ship-type)
                (core/move-toward pos nearest safe-passable)))
          ;; Patrol - pick safe random neighbor
          (if (seq safe-passable)
            (rand-nth safe-passable)
            (rand-nth passable)))))))

(defn process-ship
  "Processes a computer ship's turn."
  [pos ship-type]
  (when-let [target (decide-ship-move pos ship-type)]
    (let [target-cell (get-in @atoms/game-map target)]
      (if (and (:contents target-cell)
               (= (:owner (:contents target-cell)) :player))
        ;; Attack player unit
        (combat/attempt-attack pos target)
        ;; Normal move
        (core/move-unit-to pos target))))
  nil)
