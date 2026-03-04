;; mutation-tested: 2026-03-02
(ns empire.computer.fighter-movement
  "Fighter movement primitives: combat, hopping, fuel management."
  (:require [empire.application.runtime :as app-runtime]
            [empire.application.ports.movement :as movement-port]
            [empire.application.state :as app-state]
            [empire.computer.core :as core]
            [empire.computer.ship :as ship]
            [empire.combat :as combat]
            [empire.config :as config]))

(def ^:private state-ctx
  (delay (app-runtime/default-state-ctx)))

(defn- update-game-map!
  [f & args]
  (apply app-state/update-world! @state-ctx f args))

(defn- current-world
  []
  ((:load-world @state-ctx)))

(defn- movement-services
  []
  (:movement-port @state-ctx))

(defn- update-cell-visibility!
  ([pos owner]
   (movement-port/movement-update-cell-visibility (movement-services) pos owner))
  ([pos owner unit]
   (movement-port/movement-update-cell-visibility-with-unit (movement-services) pos owner unit)))

(defmulti get-passable-neighbors (fn [& _] :default))
(defmethod get-passable-neighbors :default
  [pos]
  (let [game-map (current-world)
        height (count game-map)
        width (count (first game-map))]
    (filter (fn [[r c]]
              (and (>= r 0) (< r height)
                   (>= c 0) (< c width)))
            (core/get-neighbors pos))))

(defmulti occupied? (fn [& _] :default))
(defmethod occupied? :default
  [pos]
  (some? (get-in (current-world) (conj pos :contents))))

(defn- diagonal-move?
  "Returns true if moving from pos to target involves both row and column change."
  [[r1 c1] [r2 c2]]
  (and (not= r1 r2) (not= c1 c2)))

(defmulti friendly-occupied? (fn [& _] :default))
(defmethod friendly-occupied? :default
  [pos]
  (let [contents (get-in (current-world) (conj pos :contents))]
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

(defmulti direction-from (fn [& _] :default))
(defmethod direction-from :default
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

(defmulti in-bounds? (fn [& _] :default))
(defmethod in-bounds? :default
  [[r c]]
  (let [game-map (current-world)
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

(defmulti hop-over-friendly (fn [& _] :default))
(defmethod hop-over-friendly :default
  [pos target]
  (let [passable (get-passable-neighbors pos)
        best (best-neighbor-toward pos target passable)]
    (when best
      (if-not (occupied? best)
        {:dest best :hops 1}
        (when (friendly-occupied? best)
          (hop-or-sidestep pos target best passable))))))

(defmulti find-adjacent-enemy (fn [& _] :default))
(defmethod find-adjacent-enemy :default
  [pos]
  (let [game-map (current-world)]
    (first (filter (fn [neighbor]
                     (let [cell (get-in game-map neighbor)
                           unit (:contents cell)]
                       (and unit
                            (= :player (:owner unit))
                            (not= :satellite (:type unit)))))
                   (core/get-neighbors pos)))))

(defmulti attack-enemy (fn [& _] :default))
(defmethod attack-enemy :default
  [fighter-pos enemy-pos]
  (let [attacker (get-in (current-world) (conj fighter-pos :contents))
        defender (get-in (current-world) (conj enemy-pos :contents))
        result (combat/resolve-combat attacker defender)]
    ;; Remove attacker from original position
    (update-game-map! update-in fighter-pos dissoc :contents)
    (if (= :attacker (:winner result))
      ;; Attacker won - move to enemy position
      (do
        (update-game-map! assoc-in (conj enemy-pos :contents) (:survivor result))
        (update-cell-visibility! fighter-pos :computer)
        (update-cell-visibility! enemy-pos :computer)
        enemy-pos)
      ;; Attacker lost
      (do
        (update-cell-visibility! fighter-pos :computer)
        nil))))

(defmulti find-nearest-refueling-site (fn [& _] :default))
(defmethod find-nearest-refueling-site :default
  [pos]
  (let [sites (ship/find-refueling-sites)]
    (when (seq sites)
      (apply min-key (partial core/distance pos) sites))))

(defmulti distance-to (fn [& _] :default))
(defmethod distance-to :default
  [[r1 c1] [r2 c2]]
  (+ (Math/abs (- r1 r2)) (Math/abs (- c1 c2))))

(defn- fuel-to-return
  "Calculate fuel needed to return to nearest refueling site."
  [pos]
  (if-let [site (find-nearest-refueling-site pos)]
    (distance-to pos site)
    999))

(defmulti should-return-to-refuel? (fn [& _] :default))
(defmethod should-return-to-refuel? :default
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
      (movement-port/movement-find-nearest-unexplored (movement-services) pos :fighter))))

(def fighter-speed 8)

(defmulti land-at-city (fn [& _] :default))
(defmethod land-at-city :default
  [pos city-pos]
  (let [_fighter (get-in (current-world) (conj pos :contents))]
    ;; Remove from current position
    (update-game-map! update-in pos dissoc :contents)
    ;; Add to city's airport
    (update-game-map! update-in (conj city-pos :fighter-count) (fnil inc 0))
    (update-cell-visibility! pos :computer)
    :landed))

(defmulti consume-fighter-fuel (fn [& _] :default))
(defmethod consume-fighter-fuel :default
  [pos]
  (let [unit (get-in (current-world) (conj pos :contents))
        new-fuel (dec (:fuel unit config/fighter-fuel))]
    (if (<= new-fuel 0)
      (do (update-game-map! update-in pos dissoc :contents)
          (update-cell-visibility! pos :computer)
          false)
      (do (update-game-map! assoc-in (conj pos :contents :fuel) new-fuel)
          true))))

(defmulti consume-hop-fuel (fn [& _] :default))
(defmethod consume-hop-fuel :default
  [pos hops]
  (loop [remaining (dec hops)]
    (if (<= remaining 0)
      true
      (if (consume-fighter-fuel pos)
        (recur (dec remaining))
        false))))

(defmulti execute-hop (fn [& _] :default))
(defmethod execute-hop :default
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

(defmulti do-patrol (fn [& _] :default))
(defmethod do-patrol :default
  [pos]
  (when-let [target (find-patrol-target pos)]
    (when-let [hop (hop-over-friendly pos target)]
      (execute-hop pos hop))))
