;; mutation-tested: 2026-03-02
(ns empire.computer.fighter-movement
  "Fighter movement primitives: combat, hopping, fuel management."
  (:require [empire.game-mechanics.movement.pathfinding-bfs :as pathfinding-bfs]
            [empire.game-mechanics.movement.visibility :as visibility]
            [empire.state.api :as sa]
            [empire.computer.core :as core]
            [empire.computer.ship-carrier :as ship-carrier]
            [empire.game-mechanics.services.combat :as combat]
            [empire.config.core :as config]))

(defn- update-cell-visibility!
  ([pos owner]
   (visibility/update-cell-visibility pos owner))
  ([pos owner unit]
   (visibility/update-cell-visibility pos owner unit)))

(defn get-passable-neighbors
  [pos]
  (let [game-map (sa/current-world)
        height (count game-map)
        width (count (first game-map))]
    (filter (fn [[r c]]
              (and (>= r 0) (< r height)
                   (>= c 0) (< c width)))
            (core/get-neighbors pos))))

(defn occupied?
  [pos]
  (some? (get-in (sa/current-world) (conj pos :contents))))

(defn- diagonal-move?
  "Returns true if moving from pos to target involves both row and column change."
  [[r1 c1] [r2 c2]]
  (and (not= r1 r2) (not= c1 c2)))

(defn friendly-occupied?
  [pos]
  (let [contents (get-in (sa/current-world) (conj pos :contents))]
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
  [[r1 c1] [r2 c2]]
  [(Integer/signum (- r2 r1)) (Integer/signum (- c2 c1))])

(defn- sidestep-around-blocker
  "Fallback when forward hop chain is blocked: take one unoccupied side step
   that does not increase distance to target."
  [pos target blocked-pos passable]
  (let [current-dist (core/distance pos target)
        candidates (->> passable
                        (remove #(= % blocked-pos))
                        (remove occupied?)
                        (map (fn [n]
                               {:pos n
                                :dist (core/distance n target)
                                :diag? (diagonal-move? pos n)}))
                        (filter #(<= (:dist %) current-dist)))]
    (when-let [best (:pos (first (sort-by (fn [{:keys [dist diag? pos]}]
                                            [dist (if diag? 0 1) pos])
                                          candidates)))]
      {:dest best :hops 1})))

(defn in-bounds?
  [[r c]]
  (let [game-map (sa/current-world)
        height (count game-map)
        width (count (first game-map))]
    (and (>= r 0) (< r height)
         (>= c 0) (< c width))))

(defn- scan-friendly-hop-chain
  [start direction]
  (let [[dr dc] direction
        [br bc] start]
    (loop [sr br sc bc hops 1]
      (let [next-pos [(+ sr dr) (+ sc dc)]]
        (when (in-bounds? next-pos)
          (if-not (occupied? next-pos)
            {:dest next-pos :hops (inc hops)}
            (if (friendly-occupied? next-pos)
              (recur (+ sr dr) (+ sc dc) (inc hops))
              {:dest next-pos :hops (inc hops) :attack true})))))))

(defn- hop-or-sidestep
  [pos target best passable]
  (let [direction (direction-from pos best)]
    (or (scan-friendly-hop-chain best direction)
        (sidestep-around-blocker pos target best passable))))

(defn hop-over-friendly
  [pos target]
  (let [passable (get-passable-neighbors pos)
        best (best-neighbor-toward pos target passable)]
    (when best
      (if-not (occupied? best)
        {:dest best :hops 1}
        (when (friendly-occupied? best)
          (hop-or-sidestep pos target best passable))))))

(defn find-adjacent-enemy
  [pos]
  (let [game-map (sa/current-world)]
    (first (filter (fn [neighbor]
                     (let [cell (get-in game-map neighbor)
                           unit (:contents cell)]
                       (and unit
                            (= :player (:owner unit))
                            (not= :satellite (:type unit)))))
                   (core/get-neighbors pos)))))

(defn attack-enemy
  [fighter-pos enemy-pos]
  (let [attacker (get-in (sa/current-world) (conj fighter-pos :contents))
        defender (get-in (sa/current-world) (conj enemy-pos :contents))
        result (combat/resolve-combat attacker defender)]
    ;; Remove attacker from original position
    (sa/update-world! update-in fighter-pos dissoc :contents)
    (if (= :attacker (:winner result))
      ;; Attacker won - move to enemy position
      (do
        (sa/update-world! assoc-in (conj enemy-pos :contents) (:survivor result))
        (update-cell-visibility! fighter-pos :computer)
        (update-cell-visibility! enemy-pos :computer)
        enemy-pos)
      ;; Attacker lost
      (do
        (update-cell-visibility! fighter-pos :computer)
        nil))))

(defn find-nearest-refueling-site
  [pos]
  (let [sites (ship-carrier/find-refueling-sites)]
    (when (seq sites)
      (apply min-key (partial core/distance pos) sites))))

(defn distance-to
  [[r1 c1] [r2 c2]]
  (+ (Math/abs (- r1 r2)) (Math/abs (- c1 c2))))

(defn- fuel-to-return
  "Calculate fuel needed to return to nearest refueling site."
  [pos]
  (if-let [site (find-nearest-refueling-site pos)]
    (distance-to pos site)
    999))

(defn should-return-to-refuel?
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
  [pos city-pos]
  (let [_fighter (get-in (sa/current-world) (conj pos :contents))]
    ;; Remove from current position
    (sa/update-world! update-in pos dissoc :contents)
    ;; Add to city's airport
    (sa/update-world! update-in (conj city-pos :fighter-count) (fnil inc 0))
    (update-cell-visibility! pos :computer)
    :landed))

(defn consume-fighter-fuel
  [pos]
  (let [unit (get-in (sa/current-world) (conj pos :contents))
        new-fuel (dec (:fuel unit config/fighter-fuel))]
    (if (<= new-fuel 0)
      (do (sa/update-world! update-in pos dissoc :contents)
          (update-cell-visibility! pos :computer)
          false)
      (do (sa/update-world! assoc-in (conj pos :contents :fuel) new-fuel)
          true))))

(defn consume-hop-fuel
  [pos hops]
  (loop [remaining (dec hops)]
    (if (<= remaining 0)
      true
      (if (consume-fighter-fuel pos)
        (recur (dec remaining))
        false))))

(defn execute-hop
  [from-pos {:keys [dest hops attack]}]
  (let [new-pos (if attack
                  (attack-enemy from-pos dest)
                  (when (core/move-unit-to from-pos dest)
                    (update-cell-visibility! from-pos :computer)
                    (update-cell-visibility! dest :computer)
                    dest))]
    (when new-pos
      (when (consume-hop-fuel new-pos hops)
        {:pos new-pos :hops hops}))))

(defn do-patrol
  [pos]
  (when-let [target (find-patrol-target pos)]
    (when-let [hop (hop-over-friendly pos target)]
      (execute-hop pos hop))))
