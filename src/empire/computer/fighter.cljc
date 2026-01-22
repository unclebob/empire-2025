(ns empire.computer.fighter
  (:require [empire.atoms :as atoms]
            [empire.combat :as combat]
            [empire.computer.core :as core]
            [empire.pathfinding :as pathfinding]))

(defn- find-adjacent-target
  "Finds an adjacent attackable target.
   Uses computer-map to respect fog of war. Returns coords or nil."
  [pos]
  (first (filter (fn [neighbor]
                   (core/attackable-target? (get-in @atoms/computer-map neighbor)))
                 (core/get-neighbors pos))))

(defn- can-fighter-move-to?
  "Returns true if a fighter can move to this cell (fighters can fly anywhere)."
  [cell]
  (and cell
       (or (nil? (:contents cell))
           (= (:owner (:contents cell)) :player))))

(defn- find-passable-fighter-neighbors
  "Returns neighbors a fighter can move to."
  [pos]
  (filter (fn [neighbor]
            (let [cell (get-in @atoms/game-map neighbor)]
              (and (can-fighter-move-to? cell)
                   (not (and (:contents cell)
                             (= (:owner (:contents cell)) :computer))))))
          (core/get-neighbors pos)))

(defn- find-nearest-friendly-city
  "Finds the nearest computer-owned city."
  [pos]
  (let [cities (core/find-visible-cities #{:computer})]
    (when (seq cities)
      (apply min-key #(core/distance pos %) cities))))

(defn decide-fighter-move
  "Decides where a computer fighter should move. Returns target coords or nil.
   Priority: 1) Attack adjacent target 2) Return to base if low fuel 3) Explore.
   Uses A* pathfinding for better navigation."
  [pos fuel]
  (let [adjacent-target (find-adjacent-target pos)
        passable (find-passable-fighter-neighbors pos)
        nearest-city (find-nearest-friendly-city pos)
        dist-to-city (when nearest-city (core/distance pos nearest-city))]
    (cond
      ;; Attack adjacent target
      adjacent-target
      adjacent-target

      ;; No valid moves
      (empty? passable)
      nil

      ;; Return to base if fuel is low (fuel <= distance to city + 2 buffer for safety)
      (and nearest-city dist-to-city (<= fuel (+ dist-to-city 2)))
      (or (pathfinding/next-step pos nearest-city :fighter)
          (core/move-toward pos nearest-city passable))

      ;; Explore - move toward unexplored or random
      :else
      (first passable))))

(defn process-fighter
  "Processes a computer fighter's turn."
  [pos unit]
  (let [fuel (:fuel unit 20)]
    (when-let [target (decide-fighter-move pos fuel)]
      (let [target-cell (get-in @atoms/game-map target)]
        (cond
          ;; Attack player unit
          (and (:contents target-cell)
               (= (:owner (:contents target-cell)) :player))
          (combat/attempt-attack pos target)

          ;; Land at friendly city
          (and (= (:type target-cell) :city)
               (= (:city-status target-cell) :computer))
          (do
            ;; Remove fighter from current position
            (swap! atoms/game-map update-in pos dissoc :contents)
            ;; Add to city airport
            (swap! atoms/game-map update-in (conj target :fighter-count) (fnil inc 0)))

          ;; Normal move - consume fuel
          :else
          (do
            (core/move-unit-to pos target)
            (swap! atoms/game-map update-in (conj target :contents :fuel) dec))))))
  nil)
