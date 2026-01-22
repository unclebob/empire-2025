(ns empire.computer.army
  (:require [empire.atoms :as atoms]
            [empire.combat :as combat]
            [empire.computer.core :as core]
            [empire.computer.threat :as threat]
            [empire.pathfinding :as pathfinding]))

(defn- find-adjacent-army-target
  "Finds an adjacent attackable target for an army (must be on land/city).
   Uses computer-map to respect fog of war. Returns coords or nil."
  [pos]
  (first (filter (fn [neighbor]
                   (let [cell (get-in @atoms/computer-map neighbor)]
                     (and (#{:land :city} (:type cell))
                          (core/attackable-target? cell))))
                 (core/get-neighbors pos))))

(defn- can-army-move-to?
  "Returns true if an army can move to this cell (land only, not cities)."
  [cell]
  (and (= :land (:type cell))
       (or (nil? (:contents cell))
           (= (:owner (:contents cell)) :player))))

(defn- find-passable-neighbors
  "Returns neighbors an army can move to."
  [pos]
  (filter (fn [neighbor]
            (let [cell (get-in @atoms/game-map neighbor)]
              (and (can-army-move-to? cell)
                   (not (and (:contents cell)
                             (= (:owner (:contents cell)) :computer))))))
          (core/get-neighbors pos)))

(defn- get-explore-moves
  "Returns passable moves that are adjacent to unexplored cells."
  [passable]
  (filter core/adjacent-to-computer-unexplored? passable))

(defn- move-toward-city-or-explore
  "Moves army toward nearest free/player city using A*, or explores.
   When exploring, prefers cells adjacent to unexplored areas."
  [pos passable]
  (let [free-cities (core/find-visible-cities #{:free})
        player-cities (core/find-visible-cities #{:player})]
    (cond
      (seq free-cities)
      (let [nearest (apply min-key #(core/distance pos %) free-cities)]
        (or (pathfinding/next-step pos nearest :army)
            (core/move-toward pos nearest passable)))

      (seq player-cities)
      (let [nearest (apply min-key #(core/distance pos %) player-cities)]
        (or (pathfinding/next-step pos nearest :army)
            (core/move-toward pos nearest passable)))

      :else
      ;; Explore: prefer moves adjacent to unexplored areas
      (let [explore-moves (get-explore-moves passable)]
        (if (seq explore-moves)
          (rand-nth explore-moves)
          (when (seq passable)
            (rand-nth passable)))))))

(defn- army-should-board-transport?
  "Wrapper that passes pathfinding function to core."
  [army-pos]
  (core/army-should-board-transport? army-pos pathfinding/next-step))

(defn decide-army-move
  "Decides where a computer army should move. Returns target coords or nil.
   Priority: 1) Attack adjacent target 2) Board adjacent transport if recruited or no land route
   3) Retreat if damaged 4) Follow directed target 5) Move toward transport if should board
   6) Move toward city 7) Explore."
  [pos]
  (let [cell (get-in @atoms/game-map pos)
        unit (:contents cell)
        adjacent-target (find-adjacent-army-target pos)
        adjacent-transport (core/find-adjacent-loading-transport pos)
        passable (find-passable-neighbors pos)
        directed-target (:target unit)]
    (cond
      ;; Attack adjacent target
      adjacent-target
      adjacent-target

      ;; Board adjacent transport if recruited (has target) or no land route to cities
      (and adjacent-transport (or directed-target (army-should-board-transport? pos)))
      adjacent-transport

      ;; Retreat if damaged
      (threat/should-retreat? pos unit @atoms/computer-map)
      (threat/retreat-move pos unit @atoms/computer-map passable)

      ;; No valid land moves
      (empty? passable)
      nil

      ;; Follow directed target (from transport recruitment)
      directed-target
      (or (pathfinding/next-step pos directed-target :army)
          (core/move-toward pos directed-target passable))

      ;; Move toward loading transport if should board
      (army-should-board-transport? pos)
      (when-let [transport (core/find-loading-transport)]
        (or (pathfinding/next-step pos transport :army)
            (move-toward-city-or-explore pos passable)))

      ;; Normal movement toward city or explore
      :else
      (move-toward-city-or-explore pos passable))))

(defn process-army
  "Processes a computer army's turn."
  [pos]
  (when-let [target (decide-army-move pos)]
    (let [target-cell (get-in @atoms/game-map target)
          target-unit (:contents target-cell)]
      (cond
        ;; Attack player unit
        (and target-unit (= (:owner target-unit) :player))
        (combat/attempt-attack pos target)

        ;; Attack hostile city (player or free)
        (and (= (:type target-cell) :city)
             (#{:player :free} (:city-status target-cell)))
        (core/attempt-conquest-computer pos target)

        ;; Board friendly transport
        (and target-unit
             (= :computer (:owner target-unit))
             (= :transport (:type target-unit)))
        (core/board-transport pos target)

        ;; Normal move
        :else
        (core/move-unit-to pos target))))
  nil)
