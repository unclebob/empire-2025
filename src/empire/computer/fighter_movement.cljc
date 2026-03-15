(ns empire.computer.fighter-movement
  "Fighter movement primitives: combat, hopping, fuel management."
  (:require [empire.game-mechanics.movement.pathfinding-bfs :as pathfinding-bfs]
            [empire.game-mechanics.movement.visibility :as visibility]
            [empire.state.api :as sa]
            [empire.computer.core :as core]
            [empire.computer.ship-carrier :as ship-carrier]
            [empire.game-mechanics.services.combat :as combat]
            [empire.config.units.dispatcher :as dispatcher]
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

(defn- ensure-hits
  [unit]
  (cond-> unit
    (and unit (nil? (:hits unit)))
    (assoc :hits (dispatcher/hits (:type unit)))))

(defn attack-enemy
  [fighter-pos enemy-pos]
  (let [world (sa/current-world)
        attacker (ensure-hits (get-in world (conj fighter-pos :contents)))
        target-cell (get-in world enemy-pos)
        defender (ensure-hits (:contents target-cell))]
    (when (and attacker
               (attackable-enemy-cell? target-cell)
               (number? (:hits attacker))
               (number? (:hits defender)))
      (let [result (combat/resolve-combat attacker defender)]
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
        valid-fighter? (and (= :fighter (:type unit))
                            (= :computer (:owner unit)))
        new-fuel (dec (:fuel unit config/fighter-fuel))]
    (if-not valid-fighter?
      (do
        (binding [*out* *err*]
          (println "Invalid fighter fuel update at" pos "contents:" unit)
          (.printStackTrace (Throwable. (str "consume-fighter-fuel called on non-computer-fighter at " pos))
                            ^java.io.PrintWriter *err*))
        false)
      (if (<= new-fuel 0)
        (do (sa/update-world! update-in pos dissoc :contents)
            (update-cell-visibility! pos :computer)
            false)
        (do (sa/update-world! assoc-in (conj pos :contents :fuel) new-fuel)
            true)))))

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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T11:57:46.559089-05:00", :module-hash "-534203623", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 9, :hash "-798324200"} {:id "defn-/update-cell-visibility!", :kind "defn-", :line 11, :end-line 15, :hash "907462422"} {:id "defn/get-passable-neighbors", :kind "defn", :line 17, :end-line 25, :hash "-616653474"} {:id "defn/occupied?", :kind "defn", :line 27, :end-line 29, :hash "-1460113126"} {:id "defn-/diagonal-move?", :kind "defn-", :line 31, :end-line 34, :hash "68518053"} {:id "defn/friendly-occupied?", :kind "defn", :line 36, :end-line 39, :hash "-774112230"} {:id "defn-/best-neighbor-toward", :kind "defn-", :line 41, :end-line 53, :hash "2138493857"} {:id "defn/direction-from", :kind "defn", :line 55, :end-line 57, :hash "991026287"} {:id "defn-/sidestep-around-blocker", :kind "defn-", :line 59, :end-line 75, :hash "535921844"} {:id "defn/in-bounds?", :kind "defn", :line 77, :end-line 83, :hash "-1890501250"} {:id "defn-/scan-friendly-hop-chain", :kind "defn-", :line 85, :end-line 96, :hash "1108247313"} {:id "defn-/hop-or-sidestep", :kind "defn-", :line 98, :end-line 102, :hash "1816122956"} {:id "defn/hop-over-friendly", :kind "defn", :line 104, :end-line 112, :hash "748504707"} {:id "defn/find-adjacent-enemy", :kind "defn", :line 114, :end-line 123, :hash "-1680420050"} {:id "defn/attack-enemy", :kind "defn", :line 125, :end-line 142, :hash "2054690730"} {:id "defn/find-nearest-refueling-site", :kind "defn", :line 144, :end-line 148, :hash "410620433"} {:id "defn/distance-to", :kind "defn", :line 150, :end-line 152, :hash "-384303346"} {:id "defn-/fuel-to-return", :kind "defn-", :line 154, :end-line 159, :hash "-1040089235"} {:id "defn/should-return-to-refuel?", :kind "defn", :line 161, :end-line 164, :hash "976392460"} {:id "defn-/find-patrol-target", :kind "defn-", :line 166, :end-line 173, :hash "1812154550"} {:id "def/fighter-speed", :kind "def", :line 175, :end-line 175, :hash "1924563297"} {:id "defn/land-at-city", :kind "defn", :line 177, :end-line 185, :hash "457630485"} {:id "defn/consume-fighter-fuel", :kind "defn", :line 187, :end-line 196, :hash "249638789"} {:id "defn/consume-hop-fuel", :kind "defn", :line 198, :end-line 205, :hash "1201016941"} {:id "defn/execute-hop", :kind "defn", :line 207, :end-line 217, :hash "533363632"} {:id "defn/do-patrol", :kind "defn", :line 219, :end-line 223, :hash "-1363119000"}]}
;; clj-mutate-manifest-end
