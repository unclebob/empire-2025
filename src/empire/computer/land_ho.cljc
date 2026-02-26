(ns empire.computer.land-ho
  "Round-start assignment of land-ho invasion targets to transports.
   Checks the FIFO queue of discovered free cities and assigns the nearest
   qualifying transport to invade each target."
  (:require [empire.atoms :as atoms]
            [empire.computer.core :as core]
            [empire.movement.pathfinding-bfs :as pathfinding-bfs]))

(defn- find-sailing-transports
  "Returns list of [pos transport] for computer transports in sailing mode
   with 4+ armies."
  []
  (let [game-map @atoms/game-map
        height (count game-map)
        width (count (first game-map))]
    (for [r (range height)
          c (range width)
          :let [cell (get-in game-map [r c])
                unit (:contents cell)]
          :when (and unit
                     (= :transport (:type unit))
                     (= :computer (:owner unit))
                     (= :sailing (:transport-mission unit))
                     (>= (:army-count unit 0) 4))]
      [[r c] unit])))

(defn- nearest-transport
  "Returns [pos transport] of the transport nearest to target, or nil."
  [transports target]
  (when (seq transports)
    (apply min-key (fn [[pos _]] (core/distance pos target)) transports)))

(defn assign-land-ho-invasion
  "At round start, try to assign the first land-ho target to the nearest
   qualifying transport. One assignment per round."
  []
  (when-let [targets (seq @atoms/land-ho-targets)]
    (let [target (first targets)
          transports (find-sailing-transports)]
      (if (empty? transports)
        nil ;; No qualifying transports -- leave target at front
        (let [[tpos _] (nearest-transport transports target)
              path (pathfinding-bfs/bfs-to-land-ho-target
                     tpos target @atoms/computer-map)]
          (if path
            (do
              ;; Assign transport to invading mode
              (swap! atoms/game-map assoc-in
                     (conj tpos :contents :transport-mission) :invading)
              (swap! atoms/game-map assoc-in
                     (conj tpos :contents :invasion-target) target)
              (swap! atoms/game-map assoc-in
                     (conj tpos :contents :invasion-path) (vec path))
              ;; Remove target from queue
              (swap! atoms/land-ho-targets #(vec (rest %))))
            ;; BFS failed -- move target to end of queue
            (swap! atoms/land-ho-targets
                   #(conj (vec (rest %)) target))))))))
