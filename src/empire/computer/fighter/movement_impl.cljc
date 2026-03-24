(ns empire.computer.fighter.movement-impl
  (:require [empire.computer.fighter.action-resolution :as fighter-action-resolution]
            [empire.config.domain.core.refueling :as refueling]
            [empire.game-mechanics.movement.pathfinding-bfs :as pathfinding-bfs]
            [empire.game-mechanics.visibility :as visibility]
            [empire.state.api :as sa]
            [empire.computer.fighter.movement-decisions :as decisions]
            [empire.computer.ship.carrier :as ship-carrier]
            [empire.computer.shared.action-resolution :as action-resolution]
            [empire.computer.shared.grid :as grid]
            [empire.computer.shared.world-query :as world-query]
            [empire.config.core :as config]))

(defn update-cell-visibility!
  ([pos owner]
   (visibility/update-cell-visibility pos owner))
  ([pos owner unit]
   (visibility/update-cell-visibility pos owner unit)))

(defn get-passable-neighbors
  [pos]
  (let [game-map (sa/read-state :computer-map)
        height (count game-map)
        width (count (first game-map))]
    (filter (fn [[r c]]
              (and (>= r 0) (< r height)
                   (>= c 0) (< c width)))
            (world-query/get-neighbors pos))))

(defn occupied?
  [pos]
  (some? (get-in (sa/read-state :computer-map) (conj pos :contents))))

(defn diagonal-move?
  [[r1 c1] [r2 c2]]
  (and (not= r1 r2) (not= c1 c2)))

(defn friendly-occupied?
  [pos]
  (let [contents (get-in (sa/read-state :computer-map) (conj pos :contents))]
    (and (some? contents) (= :computer (:owner contents)))))

(defn attackable-enemy-cell?
  [cell]
  (let [unit (:contents cell)]
    (and (not= :city (:type cell))
         unit
         (= :player (:owner unit))
         (not= :satellite (:type unit)))))

(defn best-neighbor-toward
  [pos target passable-neighbors]
  (when (seq passable-neighbors)
    (let [scored (map (fn [n]
                        [n (grid/distance n target) (if (diagonal-move? pos n) 0 1)])
                      passable-neighbors)
          best-dist (apply min (map second scored))
          at-best-dist (filter #(= best-dist (second %)) scored)
          best-diag (apply min (map #(nth % 2) at-best-dist))
          candidates (filter #(= best-diag (nth % 2)) at-best-dist)]
      (first (last candidates)))))

(defn direction-from
  [[r1 c1] [r2 c2]]
  [(Integer/signum (- r2 r1)) (Integer/signum (- c2 c1))])

(defn sidestep-around-blocker
  [pos target blocked-pos passable occupied?-fn]
  (let [current-dist (grid/distance pos target)
        candidates (->> passable
                        (remove #(= % blocked-pos))
                        (remove occupied?-fn)
                        (map (fn [n]
                               {:pos n
                                :dist (grid/distance n target)
                                :diag? (diagonal-move? pos n)}))
                        (filter #(<= (:dist %) current-dist)))]
    (when-let [best (:pos (first (sort-by (fn [{:keys [dist diag? pos]}]
                                            [dist (if diag? 0 1) pos])
                                          candidates)))]
      {:dest best :hops 1})))

(defn in-bounds?
  [[r c]]
  (let [game-map (sa/read-state :computer-map)
        height (count game-map)
        width (count (first game-map))]
    (and (>= r 0) (< r height)
         (>= c 0) (< c width))))

(defn scan-friendly-hop-chain
  [start direction in-bounds?-fn occupied?-fn friendly-occupied?-fn attackable-enemy-cell?-fn]
  (let [[dr dc] direction
        [br bc] start]
    (loop [sr br sc bc hops 1]
      (let [next-pos [(+ sr dr) (+ sc dc)]]
        (when (in-bounds?-fn next-pos)
          (let [cell (get-in (sa/read-state :computer-map) next-pos)]
            (if-not (occupied?-fn next-pos)
              {:dest next-pos :hops (inc hops)}
              (if (friendly-occupied?-fn next-pos)
                (recur (+ sr dr) (+ sc dc) (inc hops))
                (when (attackable-enemy-cell?-fn cell)
                  {:dest next-pos :hops (inc hops) :attack true})))))))))

(defn hop-or-sidestep
  [pos target best passable occupied?-fn]
  (let [direction (direction-from pos best)]
    (or (scan-friendly-hop-chain best
                                 direction
                                 in-bounds?
                                 occupied?
                                 friendly-occupied?
                                 attackable-enemy-cell?)
        (sidestep-around-blocker pos target best passable occupied?-fn))))

(defn hop-over-friendly
  [pos target]
  (let [passable (get-passable-neighbors pos)
        best (best-neighbor-toward pos target passable)]
    (when best
      (if-not (occupied? best)
        {:dest best :hops 1}
        (when (friendly-occupied? best)
          (hop-or-sidestep pos target best passable occupied?))))))

(defn find-adjacent-enemy
  [pos]
  (let [game-map (sa/read-state :computer-map)]
    (first (filter (fn [neighbor]
                     (attackable-enemy-cell? (get-in game-map neighbor)))
                   (world-query/get-neighbors pos)))))

(defn attack-enemy
  [fighter-pos enemy-pos]
  (fighter-action-resolution/attack-enemy fighter-pos enemy-pos attackable-enemy-cell?))

(defn find-nearest-refueling-site
  [pos]
  (let [{:keys [cities carriers]}
        (refueling/scan-refueling-positions (sa/read-state :computer-map))
        sites (concat cities carriers)]
    (when (seq sites)
      (apply min-key (partial grid/distance pos) sites))))

(defn distance-to
  [[r1 c1] [r2 c2]]
  (+ (Math/abs (- r1 r2)) (Math/abs (- c1 c2))))

(defn fuel-to-return
  [pos]
  (if-let [site (find-nearest-refueling-site pos)]
    (distance-to pos site)
    999))

(defn should-return-to-refuel?
  [pos fuel]
  (let [return-distance (fuel-to-return pos)]
    (<= fuel (+ return-distance 2))))

(defn find-patrol-target
  [pos]
  (let [player-units (world-query/find-visible-player-units)]
    (if (seq player-units)
      (apply min-key (partial distance-to pos) player-units)
      (pathfinding-bfs/find-nearest-unexplored pos :fighter))))

(def fighter-speed 8)

(defn land-at-city
  [pos city-pos]
  (fighter-action-resolution/land-at-city pos city-pos))

(defn consume-fighter-fuel
  [pos]
  (fighter-action-resolution/consume-fighter-fuel pos))

(defn consume-hop-fuel
  [pos hops consume-fighter-fuel-fn]
  (loop [remaining (dec hops)]
    (if (<= remaining 0)
      true
      (if (consume-fighter-fuel-fn pos)
        (recur (dec remaining))
        false))))

(defn execute-hop
  [from-pos {:keys [dest hops attack]}]
  (let [new-pos (if attack
                  (attack-enemy from-pos dest)
                  (when (action-resolution/move-unit-to from-pos dest)
                    (update-cell-visibility! from-pos :computer)
                    (update-cell-visibility! dest :computer)
                    dest))]
    (when new-pos
      (let [result (decisions/hop-result new-pos hops (consume-hop-fuel new-pos hops consume-fighter-fuel))]
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
