(ns empire.computer.fighter-movement
  "Fighter movement primitives: combat, hopping, fuel management."
  (:require [empire.game-mechanics.movement.pathfinding-bfs :as pathfinding-bfs]
            [empire.game-mechanics.movement.visibility :as visibility]
            [empire.state.api :as sa]
            [empire.computer.core :as core]
            [empire.computer.fighter-movement-decisions :as decisions]
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

(defn- attackable-enemy-cell?
  [cell]
  (let [unit (:contents cell)]
    (and (not= :city (:type cell))
         unit
         (= :player (:owner unit))
         (not= :satellite (:type unit)))))

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
          (let [cell (get-in (sa/current-world) next-pos)]
            (if-not (occupied? next-pos)
              {:dest next-pos :hops (inc hops)}
              (if (friendly-occupied? next-pos)
                (recur (+ sr dr) (+ sc dc) (inc hops))
                (when (attackable-enemy-cell? cell)
                  {:dest next-pos :hops (inc hops) :attack true})))))))))

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
                     (attackable-enemy-cell? (get-in game-map neighbor)))
                   (core/get-neighbors pos)))))

(defn attack-enemy
  [fighter-pos enemy-pos]
  (let [world (sa/current-world)
        {:keys [attacker defender attackable?]} (decisions/attack-context world
                                                                          fighter-pos
                                                                          enemy-pos
                                                                          attackable-enemy-cell?)]
    (when attackable?
      (let [result (combat/resolve-combat attacker defender)]
        ;; Remove attacker from original position
        (sa/update-world! update-in fighter-pos dissoc :contents)
        (case (decisions/attack-result-action (:winner result))
          :occupy-target
          ;; Attacker won - move to enemy position
          (do
            (sa/update-world! assoc-in (conj enemy-pos :contents) (:survivor result))
            (update-cell-visibility! fighter-pos :computer)
            (update-cell-visibility! enemy-pos :computer)
            enemy-pos)
          ;; Attacker lost
          (do
            (update-cell-visibility! fighter-pos :computer)
            nil))))))

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
  (let [fighter (get-in (sa/current-world) (conj pos :contents))]
    ;; Remove from current position
    (sa/update-world! update-in pos dissoc :contents)
    ;; Add to city's airport
    (sa/update-world! update-in (conj city-pos :fighter-count) (fnil inc 0))
    (when (:kamikazee fighter)
      (sa/update-world! update-in (conj city-pos :kamikazee-fighter-count) (fnil inc 0))
      (sa/update-world! update-in (conj city-pos :awake-kamikazee-fighters) (fnil inc 0)))
    (sa/update-world! update-in (conj city-pos :awake-fighters) (fnil inc 0))
    (update-cell-visibility! pos :computer)
    :landed))

(defn consume-fighter-fuel
  [pos]
  (let [unit (get-in (sa/current-world) (conj pos :contents))
        action (decisions/fuel-action unit config/fighter-fuel)]
    (case (:action action)
      :invalid false
      :destroy (do (sa/update-world! update-in pos dissoc :contents)
                   (update-cell-visibility! pos :computer)
                   false)
      :update-fuel (do
                     (sa/update-world! assoc-in (conj pos :contents :fuel) (:fuel action))
                     true)
      false)))

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
      (let [result (decisions/hop-result new-pos hops (consume-hop-fuel new-pos hops))]
        (when (= :moved (:result result))
          {:pos (:pos result) :hops (:hops result)})))))

(defn do-patrol
  [pos]
  (let [target (find-patrol-target pos)
        hop (when target (hop-over-friendly pos target))]
    (when (= :execute-hop
             (decisions/patrol-action {:has-target? (boolean target)
                                       :has-hop? (boolean hop)}))
      (execute-hop pos hop))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-16T12:15:22.698067-05:00", :module-hash "-438377979", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 10, :hash "1934453474"} {:id "defn-/update-cell-visibility!", :kind "defn-", :line 12, :end-line 16, :hash "907462422"} {:id "defn/get-passable-neighbors", :kind "defn", :line 18, :end-line 26, :hash "-616653474"} {:id "defn/occupied?", :kind "defn", :line 28, :end-line 30, :hash "-1460113126"} {:id "defn-/diagonal-move?", :kind "defn-", :line 32, :end-line 35, :hash "68518053"} {:id "defn/friendly-occupied?", :kind "defn", :line 37, :end-line 40, :hash "-774112230"} {:id "defn-/attackable-enemy-cell?", :kind "defn-", :line 42, :end-line 48, :hash "301860701"} {:id "defn-/best-neighbor-toward", :kind "defn-", :line 50, :end-line 62, :hash "1798301840"} {:id "defn/direction-from", :kind "defn", :line 64, :end-line 66, :hash "991026287"} {:id "defn-/sidestep-around-blocker", :kind "defn-", :line 68, :end-line 84, :hash "1580573371"} {:id "defn/in-bounds?", :kind "defn", :line 86, :end-line 92, :hash "-1890501250"} {:id "defn-/scan-friendly-hop-chain", :kind "defn-", :line 94, :end-line 107, :hash "621153402"} {:id "defn-/hop-or-sidestep", :kind "defn-", :line 109, :end-line 113, :hash "1816122956"} {:id "defn/hop-over-friendly", :kind "defn", :line 115, :end-line 123, :hash "748504707"} {:id "defn/find-adjacent-enemy", :kind "defn", :line 125, :end-line 130, :hash "1188801027"} {:id "defn/attack-enemy", :kind "defn", :line 132, :end-line 154, :hash "-1207609576"} {:id "defn/find-nearest-refueling-site", :kind "defn", :line 156, :end-line 160, :hash "410620433"} {:id "defn/distance-to", :kind "defn", :line 162, :end-line 164, :hash "-384303346"} {:id "defn-/fuel-to-return", :kind "defn-", :line 166, :end-line 171, :hash "-1040089235"} {:id "defn/should-return-to-refuel?", :kind "defn", :line 173, :end-line 176, :hash "976392460"} {:id "defn-/find-patrol-target", :kind "defn-", :line 178, :end-line 185, :hash "1812154550"} {:id "def/fighter-speed", :kind "def", :line 187, :end-line 187, :hash "1924563297"} {:id "defn/land-at-city", :kind "defn", :line 189, :end-line 201, :hash "2088911727"} {:id "defn/consume-fighter-fuel", :kind "defn", :line 203, :end-line 215, :hash "-279440253"} {:id "defn/consume-hop-fuel", :kind "defn", :line 217, :end-line 224, :hash "1201016941"} {:id "defn/execute-hop", :kind "defn", :line 226, :end-line 237, :hash "-1404398638"} {:id "defn/do-patrol", :kind "defn", :line 239, :end-line 246, :hash "-2098508622"}]}
;; clj-mutate-manifest-end
