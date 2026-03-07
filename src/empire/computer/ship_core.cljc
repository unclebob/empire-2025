;; mutation-tested: 2026-03-03
(ns empire.computer.ship-core
  "Core ship utilities shared by patrol, escort, and carrier sub-modules."
  (:require [empire.game-mechanics.movement.pathfinding-bfs :as pathfinding-bfs]
            [empire.game-mechanics.movement.visibility :as visibility]
            [empire.state.api :as sa]
            [empire.game-mechanics.services.combat :as combat]
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
    (cond
      (= start target) []
      (not (passable-sea? start)) nil
      (and (not= :sea (:type (get-in computer-map target)))
           (adjacent? start target)) []
      :else
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

(defn- set-turn-message!
  [msg ms]
  (sa/write-state! :turn-message msg)
  (sa/write-state! :turn-message-until (if (= ms Long/MAX_VALUE)
                                               Long/MAX_VALUE
                                               (+ (System/currentTimeMillis) ms))))

(defn get-passable-sea-neighbors
  [pos]
  (let [game-map (sa/current-world)]
    (filter (fn [neighbor]
              (let [cell (get-in game-map neighbor)]
                (and cell
                     (= :sea (:type cell))
                     (or (nil? (:contents cell))
                         (= :player (:owner (:contents cell)))))))
            (core/get-neighbors pos))))

(defn find-adjacent-enemy-ship
  [pos]
  (let [game-map (sa/current-world)]
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
  (let [attacker (get-in (sa/current-world) (conj ship-pos :contents))
        defender (get-in (sa/current-world) (conj enemy-pos :contents))
        result (combat/resolve-combat attacker defender)
        message (combat/format-combat-status (:log result)
                                             (:type attacker)
                                             (:type defender)
                                             (:winner result))
        dead-unit (if (= :attacker (:winner result)) defender attacker)]
    (set-turn-message! message Long/MAX_VALUE)
    (sa/update-world! update-in ship-pos dissoc :contents)
    (if (= :attacker (:winner result))
      (do
        (sa/update-world! assoc-in (conj enemy-pos :contents) (:survivor result))
        (when (= :carrier (:type attacker))
          (sa/update-state! :computer-carrier-positions disj ship-pos)
          (sa/update-state! :computer-carrier-positions (fnil conj #{}) enemy-pos))
        (update-cell-visibility! ship-pos :computer)
        (update-cell-visibility! enemy-pos :computer)
        (combat/clear-escort-on-death dead-unit)
        enemy-pos)
      (do
        (sa/update-world! assoc-in (conj enemy-pos :contents) (:survivor result))
        (when (= :carrier (:type attacker))
          (sa/update-state! :computer-carrier-positions disj ship-pos))
        (update-cell-visibility! ship-pos :computer)
        (combat/clear-escort-on-death dead-unit)
        nil))))

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
  (let [game-map (sa/current-world)]
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
                   (uc/ship-can-dock? unit (get-in (sa/current-world) neighbor)))
                 (core/get-neighbors pos))))

(defn dock-computer-ship
  [ship-pos city-pos]
  (let [cell (get-in (sa/current-world) ship-pos)
        unit (:contents cell)
        city-cell (get-in (sa/current-world) city-pos)
        updated-city (uc/add-ship-to-shipyard city-cell (:type unit) (:hits unit))]
    (sa/update-world! assoc-in ship-pos (dissoc cell :contents))
    (sa/update-world! assoc-in city-pos updated-city)
    (update-cell-visibility! city-pos :computer)
    city-pos))
