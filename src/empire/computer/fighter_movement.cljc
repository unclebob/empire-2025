;; mutation-tested: 2026-02-27
(ns empire.computer.fighter-movement
  "Fighter movement primitives: combat, hopping, fuel management."
  (:require [empire.atoms :as atoms]
            [empire.computer.core :as core]
            [empire.computer.ship :as ship]
            [empire.combat :as combat]
            [empire.movement.pathfinding-bfs :as pathfinding-bfs]
            [empire.movement.visibility :as visibility]
            [empire.config :as config]))

(defn get-passable-neighbors
  "Returns passable neighbors for a fighter (can fly over anything except off-map)."
  [pos]
  (let [game-map @atoms/game-map
        height (count game-map)
        width (count (first game-map))]
    (filter (fn [[r c]]
              (and (>= r 0) (< r height)
                   (>= c 0) (< c width)))
            (core/get-neighbors pos))))

(defn occupied?
  "Returns true if the cell at pos has contents."
  [pos]
  (some? (get-in @atoms/game-map (conj pos :contents))))

(defn- diagonal-move?
  "Returns true if moving from pos to target involves both row and column change."
  [[r1 c1] [r2 c2]]
  (and (not= r1 r2) (not= c1 c2)))

(defn friendly-occupied?
  "Returns true if the cell at pos has a computer-owned unit."
  [pos]
  (let [contents (get-in @atoms/game-map (conj pos :contents))]
    (and (some? contents) (= :computer (:owner contents)))))

(defn- best-neighbor-toward
  "Picks the best neighbor toward target using distance + diagonal preference.
   Considers all passable neighbors, not just unoccupied ones."
  [pos target passable-neighbors]
  (when (seq passable-neighbors)
    (let [scored (map (fn [n]
                        [n (core/distance n target) (if (diagonal-move? pos n) 0 1)])
                      passable-neighbors)
          best-dist (apply min (map second scored))
          at-best-dist (filter #(= best-dist (second %)) scored)
          best-diag (apply min (map #(nth % 2) at-best-dist))
          candidates (filter #(= best-diag (nth % 2)) at-best-dist)]
      (first (last candidates)))))

(defn direction-from
  "Returns the unit direction vector [dr dc] from pos to neighbor."
  [[r1 c1] [r2 c2]]
  [(Integer/signum (- r2 r1)) (Integer/signum (- c2 c1))])

(defn in-bounds?
  "Returns true if pos is within game-map bounds."
  [[r c]]
  (let [game-map @atoms/game-map
        height (count game-map)
        width (count (first game-map))]
    (and (>= r 0) (< r height)
         (>= c 0) (< c width))))

(defn hop-over-friendly
  "When the best neighbor toward target is occupied by a computer unit, scan
   forward along the direction of travel, skipping all consecutive
   friendly-occupied cells. Land on the first empty passable cell, or
   attack an enemy at the end of the chain.
   Returns {:dest pos :hops n} or {:dest pos :hops n :attack true} or nil."
  [pos target]
  (let [passable (get-passable-neighbors pos)
        [br bc :as best] (best-neighbor-toward pos target passable)]
    (when best
      (if-not (occupied? best)
        {:dest best :hops 1}
        (when (friendly-occupied? best)
          (let [[dr dc] (direction-from pos best)]
            (loop [sr br sc bc hops 1]
              (let [next-pos [(+ sr dr) (+ sc dc)]]
                (when (in-bounds? next-pos)
                  (if-not (occupied? next-pos)
                    {:dest next-pos :hops (inc hops)}
                    (if (friendly-occupied? next-pos)
                      (recur (+ sr dr) (+ sc dc) (inc hops))
                      {:dest next-pos :hops (inc hops) :attack true})))))))))))

(defn find-adjacent-enemy
  "Finds an adjacent enemy unit to attack (not cities - fighters can't conquer)."
  [pos]
  (let [game-map @atoms/game-map]
    (first (filter (fn [neighbor]
                     (let [cell (get-in game-map neighbor)
                           unit (:contents cell)]
                       (and unit
                            (= :player (:owner unit)))))
                   (core/get-neighbors pos)))))

(defn attack-enemy
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

(defn find-nearest-refueling-site
  "Find the nearest refueling site (computer city or holding carrier)."
  [pos]
  (let [sites (ship/find-refueling-sites)]
    (when (seq sites)
      (apply min-key (partial core/distance pos) sites))))

(defn distance-to
  "Manhattan distance between two positions."
  [[r1 c1] [r2 c2]]
  (+ (Math/abs (- r1 r2)) (Math/abs (- c1 c2))))

(defn- fuel-to-return
  "Calculate fuel needed to return to nearest refueling site."
  [pos]
  (if-let [site (find-nearest-refueling-site pos)]
    (distance-to pos site)
    999))

(defn should-return-to-refuel?
  "Returns true if fighter should head back to refuel."
  [pos fuel]
  (let [return-distance (fuel-to-return pos)]
    (<= fuel (+ return-distance 2))))

(defn- find-patrol-target
  "Find something interesting to patrol toward.
   Uses BFS to find nearest unexplored territory without directional bias."
  [pos]
  (let [player-units (core/find-visible-player-units)]
    (if (seq player-units)
      (apply min-key (partial distance-to pos) player-units)
      (pathfinding-bfs/find-nearest-unexplored pos :fighter))))

(def fighter-speed 8)

(defn land-at-city
  "Land fighter at city to refuel."
  [pos city-pos]
  (let [_fighter (get-in @atoms/game-map (conj pos :contents))]
    ;; Remove from current position
    (swap! atoms/game-map update-in pos dissoc :contents)
    ;; Add to city's airport
    (swap! atoms/game-map update-in (conj city-pos :fighter-count) (fnil inc 0))
    (visibility/update-cell-visibility pos :computer)
    :landed))

(defn consume-fighter-fuel
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

(defn consume-hop-fuel
  "Burns fuel for intermediate cells in a multi-cell hop.
   For a hop of n cells, the first cell was already handled by the move,
   so we burn fuel for (n-1) intermediate cells.
   Returns true if fighter survived, false if it died."
  [pos hops]
  (loop [remaining (dec hops)]
    (if (<= remaining 0)
      true
      (if (consume-fighter-fuel pos)
        (recur (dec remaining))
        false))))

(defn execute-hop
  "Execute a hop result: move to dest or attack. Consume hop fuel.
   Returns {:pos p :hops n} or nil (fighter died)."
  [from-pos {:keys [dest hops attack]}]
  (let [new-pos (if attack
                  (attack-enemy from-pos dest)
                  (when (core/move-unit-to from-pos dest)
                    (visibility/update-cell-visibility from-pos :computer)
                    (visibility/update-cell-visibility dest :computer)
                    dest))]
    (when new-pos
      (when (consume-hop-fuel new-pos hops)
        {:pos new-pos :hops hops}))))

(defn do-patrol
  "Execute one patrol step toward a target or unexplored area.
   Returns {:pos p :hops n} or nil."
  [pos]
  (when-let [target (find-patrol-target pos)]
    (when-let [hop (hop-over-friendly pos target)]
      (execute-hop pos hop))))
