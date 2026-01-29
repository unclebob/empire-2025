(ns empire.computer.fighter
  "Computer fighter module - VMS Empire style fighter movement.
   Attack adjacent enemies, patrol within fuel range, return to city."
  (:require [empire.atoms :as atoms]
            [empire.computer.core :as core]
            [empire.player.combat :as combat]
            [empire.movement.visibility :as visibility]
            [empire.config :as config]))

(defn- get-passable-neighbors
  "Returns passable neighbors for a fighter (can fly over anything except off-map)."
  [pos]
  (let [game-map @atoms/game-map
        height (count game-map)
        width (count (first game-map))]
    (filter (fn [[r c]]
              (and (>= r 0) (< r height)
                   (>= c 0) (< c width)))
            (core/get-neighbors pos))))

(defn- find-adjacent-enemy
  "Finds an adjacent enemy unit to attack (not cities - fighters can't conquer)."
  [pos]
  (let [game-map @atoms/game-map]
    (first (filter (fn [neighbor]
                     (let [cell (get-in game-map neighbor)
                           unit (:contents cell)]
                       (and unit
                            (= :player (:owner unit)))))
                   (core/get-neighbors pos)))))

(defn- attack-enemy
  "Attack an adjacent enemy. Returns new position or nil if fighter died."
  [fighter-pos enemy-pos]
  (let [attacker (get-in @atoms/game-map (conj fighter-pos :contents))
        defender (get-in @atoms/game-map (conj enemy-pos :contents))
        result (combat/resolve-combat attacker defender)]
    ;; Remove attacker from original position
    (swap! atoms/game-map update-in fighter-pos dissoc :contents)
    (if (= :attacker (:winner result))
      ;; Attacker won - move to enemy position
      (do
        (swap! atoms/game-map assoc-in (conj enemy-pos :contents) (:survivor result))
        (visibility/update-cell-visibility fighter-pos :computer)
        (visibility/update-cell-visibility enemy-pos :computer)
        enemy-pos)
      ;; Attacker lost
      (do
        (visibility/update-cell-visibility fighter-pos :computer)
        nil))))

(defn- find-nearest-friendly-city
  "Find the nearest computer-owned city."
  [pos]
  (let [cities (core/find-visible-cities #{:computer})]
    (when (seq cities)
      (apply min-key
             (fn [[r c]]
               (let [[pr pc] pos]
                 (+ (Math/abs (- r pr)) (Math/abs (- c pc)))))
             cities))))

(defn- distance-to
  "Manhattan distance between two positions."
  [[r1 c1] [r2 c2]]
  (+ (Math/abs (- r1 r2)) (Math/abs (- c1 c2))))

(defn- fuel-to-return
  "Calculate fuel needed to return to nearest city."
  [pos]
  (if-let [city (find-nearest-friendly-city pos)]
    (distance-to pos city)
    999))  ; Very high if no city

(defn- should-return-to-city?
  "Returns true if fighter should head back to refuel."
  [pos fuel]
  (let [return-distance (fuel-to-return pos)]
    ;; Need to return if fuel is just barely enough (with 1-2 margin)
    (<= fuel (+ return-distance 2))))

(defn- move-toward-city
  "Move fighter one step toward nearest city."
  [pos]
  (when-let [city (find-nearest-friendly-city pos)]
    (let [passable (get-passable-neighbors pos)
          closest (core/move-toward pos city passable)]
      (when closest
        (core/move-unit-to pos closest)
        (visibility/update-cell-visibility pos :computer)
        (visibility/update-cell-visibility closest :computer)
        closest))))

(defn- find-patrol-target
  "Find something interesting to patrol toward."
  [pos]
  (let [player-units (core/find-visible-player-units)
        passable (get-passable-neighbors pos)
        ;; Cells that are themselves unexplored in computer-map
        unexplored-passable (filter #(nil? (get-in @atoms/computer-map %)) passable)
        ;; Or cells adjacent to unexplored
        near-unexplored (filter core/adjacent-to-computer-unexplored? passable)]
    (cond
      (seq player-units)
      (apply min-key (partial distance-to pos) player-units)

      (seq unexplored-passable)
      (first unexplored-passable)

      (seq near-unexplored)
      (first near-unexplored)

      :else nil)))

(defn- do-patrol
  "Execute one patrol step toward a target or unexplored area."
  [pos]
  (let [target (find-patrol-target pos)
        passable (get-passable-neighbors pos)]
    (if target
      ;; Move toward target
      (let [closest (core/move-toward pos target passable)]
        (when closest
          (core/move-unit-to pos closest)
          (visibility/update-cell-visibility pos :computer)
          (visibility/update-cell-visibility closest :computer)
          closest))
      ;; Random patrol - prefer unexplored edges
      (let [frontier (filter core/adjacent-to-computer-unexplored? passable)
            target (or (first frontier) (first passable))]
        (when target
          (core/move-unit-to pos target)
          (visibility/update-cell-visibility pos :computer)
          (visibility/update-cell-visibility target :computer)
          target)))))

(def ^:private fighter-speed 8)

(defn- land-at-city
  "Land fighter at city to refuel."
  [pos city-pos]
  (let [_fighter (get-in @atoms/game-map (conj pos :contents))]
    ;; Remove from current position
    (swap! atoms/game-map update-in pos dissoc :contents)
    ;; Add to city's airport
    (swap! atoms/game-map update-in (conj city-pos :fighter-count) (fnil inc 0))
    (visibility/update-cell-visibility pos :computer)
    :landed))

(defn- consume-fighter-fuel
  "Decrement fuel on the fighter at pos. Returns false if fighter died."
  [pos]
  (let [unit (get-in @atoms/game-map (conj pos :contents))
        new-fuel (dec (:fuel unit config/fighter-fuel))]
    (if (<= new-fuel 0)
      (do (swap! atoms/game-map update-in pos dissoc :contents)
          (visibility/update-cell-visibility pos :computer)
          false)
      (do (swap! atoms/game-map assoc-in (conj pos :contents :fuel) new-fuel)
          true))))

(defn- move-fighter-once
  "Execute one step of fighter priority logic.
   Returns new position if moved and alive, :landed if landed at city, nil if died or stuck."
  [pos unit]
  (let [fuel (:fuel unit config/fighter-fuel)]
    ;; Priority 1: Attack adjacent enemy
    (if-let [enemy-pos (find-adjacent-enemy pos)]
      (when-let [new-pos (attack-enemy pos enemy-pos)]
        (if (consume-fighter-fuel new-pos)
          new-pos
          nil))

      ;; Priority 2: Return to city if low on fuel
      (if (should-return-to-city? pos fuel)
        (let [city (find-nearest-friendly-city pos)]
          (cond
            (and city (= pos city))
            (land-at-city pos city)

            (and city (some #{city} (core/get-neighbors pos)))
            (land-at-city pos city)

            city
            (when-let [new-pos (move-toward-city pos)]
              (if (consume-fighter-fuel new-pos)
                new-pos
                nil))

            ;; No city to return to - patrol desperately
            :else
            (when-let [new-pos (do-patrol pos)]
              (if (consume-fighter-fuel new-pos)
                new-pos
                nil))))

        ;; Priority 3: Patrol
        (when-let [new-pos (do-patrol pos)]
          (if (consume-fighter-fuel new-pos)
            new-pos
            nil))))))

(defn process-fighter
  "Processes a computer fighter using VMS Empire style logic.
   Moves up to fighter-speed (8) cells per round, consuming fuel each step.
   Priority each step: Attack adjacent > Return to city if low fuel > Patrol
   Returns nil."
  [pos unit]
  (when (and unit (= :computer (:owner unit)) (= :fighter (:type unit)))
    (loop [current-pos pos
           steps-remaining fighter-speed]
      (when (pos? steps-remaining)
        (let [unit (get-in @atoms/game-map (conj current-pos :contents))]
          (when (and unit (= :fighter (:type unit)))
            (let [result (move-fighter-once current-pos unit)]
              (when (and result (not= result :landed))
                (recur result (dec steps-remaining)))))))))
  nil)
