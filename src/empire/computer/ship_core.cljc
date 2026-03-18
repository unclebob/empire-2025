(ns empire.computer.ship-core
  "Core ship utilities shared by patrol, escort, and carrier sub-modules."
  (:require [empire.game-mechanics.movement.pathfinding-bfs :as pathfinding-bfs]
            [empire.game-mechanics.movement.visibility :as visibility]
            [empire.state.api :as sa]
            [empire.game-mechanics.services.ship-action-resolution :as ship-action-resolution]
            [empire.computer.core :as core]
            [empire.computer.threat :as threat]
            [empire.game-mechanics.movement.map-utils :as map-utils]
            [empire.game-mechanics.containers.helpers :as uc]))

(def ^:private sea-path-inflation-threshold 2)

(defn- direct-step
  [from to]
  (let [[fr fc] from
        [tr tc] to
        dr (Long/signum (- tr fr))
        dc (Long/signum (- tc fc))]
    [(+ fr dr) (+ fc dc)]))

(defn- between-cells
  "Cells strictly between from and to along king-step direct line."
  [from to]
  (loop [current from
         cells []]
    (if (= current to)
      cells
      (let [next-pos (direct-step current to)]
        (if (= next-pos to)
          cells
          (recur next-pos (conj cells next-pos)))))))

(defn- sea-or-unexplored?
  [cell]
  (or (nil? cell)
      (= :sea (:type cell))
      (= :unexplored (:type cell))))

(defn- direct-sea-corridor?
  [from to computer-map]
  (every? (fn [pos] (sea-or-unexplored? (get-in computer-map pos)))
          (between-cells from to)))

(defn- adjacent?
  [[r1 c1] [r2 c2]]
  (and (<= (Math/abs (- r2 r1)) 1)
       (<= (Math/abs (- c2 c1)) 1)
       (not= [r1 c1] [r2 c2])))

(defn- sea-path-target?
  [current start target computer-map]
  (let [target-cell (get-in computer-map target)
        target-sea? (= :sea (:type target-cell))]
    (or (and target-sea? (= current target))
        (and (not= current start)
             (not target-sea?)
             (adjacent? current target)))))

(defn- bfs-sea-path-to-target
  "Path over known sea on computer-map.
   Returns sea path excluding start; for land targets, ends adjacent."
  [start target computer-map]
  (let [passable-sea? (fn [pos] (= :sea (:type (get-in computer-map pos))))]
    (when (and (not= start target)
               (passable-sea? start)
               (not (and (not= :sea (:type (get-in computer-map target)))
                         (adjacent? start target))))
      (loop [queue (conj clojure.lang.PersistentQueue/EMPTY start)
             visited #{start}
             came-from {}]
        (when (seq queue)
          (let [current (peek queue)]
            (if (sea-path-target? current start target computer-map)
              (vec (rest (map-utils/reconstruct-path came-from start current)))
              (let [neighbors (->> (core/neighbors-in-map computer-map current)
                                   (filter passable-sea?)
                                   (remove visited))
                    next-came-from (reduce #(assoc %1 %2 current) came-from neighbors)]
                (recur (reduce conj (pop queue) neighbors)
                       (into visited neighbors)
                       next-came-from)))))))))

(defn- inflated-sea-path?
  [sea-path from target]
  (let [cheb (core/chebyshev-distance from target)]
    (and (seq sea-path)
         (pos? cheb)
         (>= (count sea-path) (* sea-path-inflation-threshold cheb)))))

(defn- select-next-sea-step
  [from target passable]
  (let [computer-map (sa/read-state :computer-map)
        sea-path (bfs-sea-path-to-target from target computer-map)
        use-direct? (and (inflated-sea-path? sea-path from target)
                         (direct-sea-corridor? from target computer-map))
        direct-next (core/move-toward from target passable)
        sea-next (when (seq sea-path)
                   (let [step (first sea-path)]
                     (when (some #{step} passable) step)))]
    (or (when use-direct? direct-next)
        sea-next
        direct-next)))

(defn- update-cell-visibility!
  ([pos owner]
   (visibility/update-cell-visibility pos owner))
  ([pos owner unit]
   (visibility/update-cell-visibility pos owner unit)))

(defn get-passable-sea-neighbors
  [pos]
  (let [game-map (sa/read-state :computer-map)]
    (filter (fn [neighbor]
              (let [cell (get-in game-map neighbor)]
                (and (or (nil? cell)
                         (= :sea (:type cell))
                         (= :unexplored (:type cell)))
                     (or (nil? (:contents cell))
                         (= :player (:owner (:contents cell)))))))
            (core/get-neighbors pos))))

(defn find-adjacent-enemy-ship
  [pos]
  (let [game-map (sa/read-state :computer-map)]
    (first (filter (fn [neighbor]
                     (let [cell (get-in game-map neighbor)
                           unit (:contents cell)]
                       (and unit
                            (= :player (:owner unit))
                            (#{:patrol-boat :destroyer :submarine :transport
                               :carrier :battleship} (:type unit)))))
                   (core/get-neighbors pos)))))

(defn attack-enemy
  [ship-pos enemy-pos]
  (ship-action-resolution/attack-enemy ship-pos enemy-pos))

(defn move-toward
  [pos target]
  (let [passable (get-passable-sea-neighbors pos)
        closest (select-next-sea-step pos target passable)]
    (when closest
      (core/move-unit-to pos closest)
      (update-cell-visibility! pos :computer)
      (update-cell-visibility! closest :computer)
      closest)))

(defn explore-sea
  [pos ship-type]
  (when-let [target (pathfinding-bfs/find-nearest-unexplored pos ship-type)]
    (move-toward pos target)))

(defn find-player-ship-sighting
  [pos]
  (let [player-units (core/find-visible-player-units)]
    (when (seq player-units)
      (apply min-key (partial core/distance pos) player-units))))

(defn retreat-if-damaged
  [pos unit]
  (let [comp-map (sa/read-state :computer-map)]
    (when (threat/should-retreat? pos unit comp-map)
    (let [passable (get-passable-sea-neighbors pos)]
      (threat/retreat-move pos unit comp-map passable)))))

(defn find-computer-transports
  []
  (let [game-map (sa/read-state :computer-map)]
    (for [i (range (count game-map))
          j (range (count (first game-map)))
          :let [cell (get-in game-map [i j])
                unit (:contents cell)]
          :when (and unit
                     (= :computer (:owner unit))
                     (= :transport (:type unit)))]
      [i j])))

(defn find-nearest-transport
  [pos]
  (let [transports (find-computer-transports)]
    (when (seq transports)
      (apply min-key (partial core/distance pos) transports))))

(defn find-adjacent-dock-city
  [pos unit]
  (first (filter (fn [neighbor]
                   (uc/ship-can-dock? unit (get-in (sa/read-state :computer-map) neighbor)))
                 (core/get-neighbors pos))))

(defn dock-computer-ship
  [ship-pos city-pos]
  (ship-action-resolution/dock-computer-ship ship-pos city-pos))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T11:58:22.205477-05:00", :module-hash "-253430828", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 10, :hash "-324906273"} {:id "def/sea-path-inflation-threshold", :kind "def", :line 12, :end-line 12, :hash "2026838488"} {:id "defn-/direct-step", :kind "defn-", :line 14, :end-line 20, :hash "1079454387"} {:id "defn-/between-cells", :kind "defn-", :line 22, :end-line 32, :hash "-288339949"} {:id "defn-/sea-or-unexplored?", :kind "defn-", :line 34, :end-line 38, :hash "-1587463409"} {:id "defn-/direct-sea-corridor?", :kind "defn-", :line 40, :end-line 43, :hash "279328966"} {:id "defn-/adjacent?", :kind "defn-", :line 45, :end-line 49, :hash "1493627061"} {:id "defn-/sea-path-target?", :kind "defn-", :line 51, :end-line 58, :hash "1464776401"} {:id "defn-/bfs-sea-path-to-target", :kind "defn-", :line 60, :end-line 84, :hash "1309353292"} {:id "defn-/inflated-sea-path?", :kind "defn-", :line 86, :end-line 91, :hash "800765839"} {:id "defn-/select-next-sea-step", :kind "defn-", :line 93, :end-line 105, :hash "834364011"} {:id "defn-/update-cell-visibility!", :kind "defn-", :line 107, :end-line 111, :hash "907462422"} {:id "defn-/set-turn-message!", :kind "defn-", :line 113, :end-line 118, :hash "961870185"} {:id "defn/get-passable-sea-neighbors", :kind "defn", :line 120, :end-line 129, :hash "1457023441"} {:id "defn/find-adjacent-enemy-ship", :kind "defn", :line 131, :end-line 141, :hash "-245366111"} {:id "defn/attack-enemy", :kind "defn", :line 143, :end-line 171, :hash "997138377"} {:id "defn/move-toward", :kind "defn", :line 173, :end-line 181, :hash "637002133"} {:id "defn/explore-sea", :kind "defn", :line 183, :end-line 186, :hash "1692522331"} {:id "defn/find-player-ship-sighting", :kind "defn", :line 188, :end-line 192, :hash "431441058"} {:id "defn/retreat-if-damaged", :kind "defn", :line 194, :end-line 199, :hash "342171448"} {:id "defn/find-computer-transports", :kind "defn", :line 201, :end-line 211, :hash "1501158988"} {:id "defn/find-nearest-transport", :kind "defn", :line 213, :end-line 217, :hash "1701883798"} {:id "defn/find-adjacent-dock-city", :kind "defn", :line 219, :end-line 223, :hash "145248969"} {:id "defn/dock-computer-ship", :kind "defn", :line 225, :end-line 234, :hash "-1778011344"}]}
;; clj-mutate-manifest-end
