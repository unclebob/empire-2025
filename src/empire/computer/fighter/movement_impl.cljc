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

(def ^:private refueling-cache (atom nil))

(defn clear-refueling-cache! [] (reset! refueling-cache nil))

(defn- cached-refueling-sites []
  (or @refueling-cache
      (let [result (refueling/scan-refueling-positions (sa/read-state :computer-map))]
        (reset! refueling-cache result)
        result)))

(defn find-nearest-refueling-site
  [pos]
  (let [{:keys [cities carriers]} (cached-refueling-sites)
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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-26T21:27:26.514131-05:00", :module-hash "389198152", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 12, :hash "-147640284"} {:id "defn/update-cell-visibility!", :kind "defn", :line 14, :end-line 18, :hash "-470680957"} {:id "defn/get-passable-neighbors", :kind "defn", :line 20, :end-line 28, :hash "54360740"} {:id "defn/occupied?", :kind "defn", :line 30, :end-line 32, :hash "967736517"} {:id "defn/diagonal-move?", :kind "defn", :line 34, :end-line 36, :hash "1919777645"} {:id "defn/friendly-occupied?", :kind "defn", :line 38, :end-line 41, :hash "728561647"} {:id "defn/attackable-enemy-cell?", :kind "defn", :line 43, :end-line 49, :hash "-459540635"} {:id "defn/best-neighbor-toward", :kind "defn", :line 51, :end-line 61, :hash "-2078300510"} {:id "defn/direction-from", :kind "defn", :line 63, :end-line 65, :hash "991026287"} {:id "defn/sidestep-around-blocker", :kind "defn", :line 67, :end-line 81, :hash "-675147096"} {:id "defn/in-bounds?", :kind "defn", :line 83, :end-line 89, :hash "2029996192"} {:id "defn/scan-friendly-hop-chain", :kind "defn", :line 91, :end-line 104, :hash "63301940"} {:id "defn/hop-or-sidestep", :kind "defn", :line 106, :end-line 115, :hash "-650268497"} {:id "defn/hop-over-friendly", :kind "defn", :line 117, :end-line 125, :hash "-1417246495"} {:id "defn/find-adjacent-enemy", :kind "defn", :line 127, :end-line 132, :hash "-2021752784"} {:id "defn/attack-enemy", :kind "defn", :line 134, :end-line 136, :hash "353946579"} {:id "def/refueling-cache", :kind "def", :line 138, :end-line 138, :hash "-406239957"} {:id "defn/clear-refueling-cache!", :kind "defn", :line 140, :end-line 140, :hash "893416778"} {:id "defn-/cached-refueling-sites", :kind "defn-", :line 142, :end-line 146, :hash "-1606935065"} {:id "defn/find-nearest-refueling-site", :kind "defn", :line 148, :end-line 153, :hash "1252228915"} {:id "defn/distance-to", :kind "defn", :line 155, :end-line 157, :hash "-384303346"} {:id "defn/fuel-to-return", :kind "defn", :line 159, :end-line 163, :hash "-1382156279"} {:id "defn/should-return-to-refuel?", :kind "defn", :line 165, :end-line 168, :hash "976392460"} {:id "defn/find-patrol-target", :kind "defn", :line 170, :end-line 175, :hash "194155150"} {:id "def/fighter-speed", :kind "def", :line 177, :end-line 177, :hash "1924563297"} {:id "defn/land-at-city", :kind "defn", :line 179, :end-line 181, :hash "-1896829106"} {:id "defn/consume-fighter-fuel", :kind "defn", :line 183, :end-line 185, :hash "1008756463"} {:id "defn/consume-hop-fuel", :kind "defn", :line 187, :end-line 194, :hash "-298186365"} {:id "defn/execute-hop", :kind "defn", :line 196, :end-line 207, :hash "1151580198"} {:id "defn/do-patrol", :kind "defn", :line 209, :end-line 216, :hash "-2098508622"}]}
;; clj-mutate-manifest-end
