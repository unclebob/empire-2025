(ns empire.computer.ship.core
  "Core ship utilities shared by patrol, escort, and carrier sub-modules."
  (:require [empire.game-mechanics.movement.pathfinding-bfs :as pathfinding-bfs]
            [empire.game-mechanics.visibility :as visibility]
            [empire.state.api :as sa]
            [empire.game-mechanics.services.ship-action-resolution :as ship-action-resolution]
            [empire.computer.shared.action-resolution :as action-resolution]
            [empire.computer.shared.grid :as grid]
            [empire.computer.shared.threat :as threat]
            [empire.computer.shared.world-query :as world-query]
            [empire.game-mechanics.movement.map-utils :as map-utils]
            [empire.game-mechanics.containers.helpers :as uc]
            [empire.game-mechanics.movement.ray-pathfinding :as ray]))

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

(defn- sea-path-to-target
  "Path over known sea on computer-map using ray+crawl with BFS fallback.
   Returns sea path excluding start; for land targets, ends adjacent."
  [start target computer-map]
  (let [sea-target? (= :sea (:type (get-in computer-map target)))
        effective-target (if sea-target?
                           target
                           ;; For land targets, find nearest sea cell adjacent to target
                           (first (filter (fn [n]
                                           (and (= :sea (:type (get-in computer-map n)))
                                                (not= n start)))
                                         (grid/neighbors-in-map computer-map target))))]
    (when (and effective-target
               (not= start effective-target)
               (= :sea (:type (get-in computer-map start))))
      (ray/find-sea-path start effective-target computer-map))))

(defn- inflated-sea-path?
  [sea-path from target]
  (let [cheb (grid/chebyshev-distance from target)]
    (and (seq sea-path)
         (pos? cheb)
         (>= (count sea-path) (* sea-path-inflation-threshold cheb)))))

(defn- select-next-sea-step
  [from target passable]
  (let [computer-map (sa/read-state :computer-map)
        sea-path (sea-path-to-target from target computer-map)
        use-direct? (and (inflated-sea-path? sea-path from target)
                         (direct-sea-corridor? from target computer-map))
        direct-next (grid/move-toward from target passable)
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
            (world-query/get-neighbors pos))))

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
                   (world-query/get-neighbors pos)))))

(defn attack-enemy
  [ship-pos enemy-pos]
  (ship-action-resolution/attack-enemy ship-pos enemy-pos))

(defn move-toward
  [pos target]
  (let [passable (get-passable-sea-neighbors pos)
        closest (select-next-sea-step pos target passable)]
    (when closest
      (action-resolution/move-unit-to pos closest)
      (update-cell-visibility! pos :computer)
      (update-cell-visibility! closest :computer)
      closest)))

(defn explore-sea
  [pos ship-type]
  (when-let [target (pathfinding-bfs/find-nearest-unexplored pos ship-type)]
    (move-toward pos target)))

(defn find-player-ship-sighting
  [pos]
  (let [player-units (world-query/find-visible-player-units)]
    (when (seq player-units)
      (apply min-key (partial grid/distance pos) player-units))))

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
      (apply min-key (partial grid/distance pos) transports))))

(defn find-adjacent-dock-city
  [pos unit]
  (first (filter (fn [neighbor]
                   (uc/ship-can-dock? unit (get-in (sa/read-state :computer-map) neighbor)))
                 (world-query/get-neighbors pos))))

(defn dock-computer-ship
  [ship-pos city-pos]
  (ship-action-resolution/dock-computer-ship ship-pos city-pos))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-26T23:26:19.026231-05:00", :module-hash "1451271875", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 12, :hash "634097454"} {:id "def/sea-path-inflation-threshold", :kind "def", :line 14, :end-line 14, :hash "2026838488"} {:id "defn-/direct-step", :kind "defn-", :line 16, :end-line 22, :hash "1079454387"} {:id "defn-/between-cells", :kind "defn-", :line 24, :end-line 34, :hash "-288339949"} {:id "defn-/sea-or-unexplored?", :kind "defn-", :line 36, :end-line 40, :hash "-1587463409"} {:id "defn-/direct-sea-corridor?", :kind "defn-", :line 42, :end-line 45, :hash "279328966"} {:id "defn-/adjacent?", :kind "defn-", :line 47, :end-line 51, :hash "1493627061"} {:id "defn-/sea-path-target?", :kind "defn-", :line 53, :end-line 60, :hash "1464776401"} {:id "defn-/bfs-sea-path-to-target", :kind "defn-", :line 62, :end-line 84, :hash "404990494"} {:id "defn-/inflated-sea-path?", :kind "defn-", :line 86, :end-line 91, :hash "-876139511"} {:id "defn-/select-next-sea-step", :kind "defn-", :line 93, :end-line 105, :hash "-817400003"} {:id "defn-/update-cell-visibility!", :kind "defn-", :line 107, :end-line 111, :hash "907462422"} {:id "defn/get-passable-sea-neighbors", :kind "defn", :line 113, :end-line 123, :hash "-1822906073"} {:id "defn/find-adjacent-enemy-ship", :kind "defn", :line 125, :end-line 135, :hash "1526505427"} {:id "defn/attack-enemy", :kind "defn", :line 137, :end-line 139, :hash "-616189443"} {:id "defn/move-toward", :kind "defn", :line 141, :end-line 149, :hash "-10107255"} {:id "defn/explore-sea", :kind "defn", :line 151, :end-line 154, :hash "1692522331"} {:id "defn/find-player-ship-sighting", :kind "defn", :line 156, :end-line 160, :hash "-1529931547"} {:id "defn/retreat-if-damaged", :kind "defn", :line 162, :end-line 167, :hash "342171448"} {:id "defn/find-computer-transports", :kind "defn", :line 169, :end-line 179, :hash "1312577557"} {:id "defn/find-nearest-transport", :kind "defn", :line 181, :end-line 185, :hash "-463490702"} {:id "defn/find-adjacent-dock-city", :kind "defn", :line 187, :end-line 191, :hash "2116566367"} {:id "defn/dock-computer-ship", :kind "defn", :line 193, :end-line 195, :hash "1813373741"}]}
;; clj-mutate-manifest-end
