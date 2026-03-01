(ns empire.computer.land-ho
  "Round-start assignment of land-ho invasion targets to transports.
   Checks the FIFO queue of discovered free cities and assigns the nearest
   qualifying transport to invade each target."
  (:require [empire.adapters.state.runtime :as runtime-state]
            [empire.application.ports :as ports]
            [empire.application.runtime :as app-runtime]
            [empire.application.state :as app-state]
            [empire.computer.core :as core]
            [empire.movement.pathfinding-bfs :as pathfinding-bfs]))

(def ^:private state-ctx
  (delay (app-runtime/default-state-ctx)))

(defn- update-game-map!
  [f & args]
  (apply app-state/update-world! @state-ctx f args))

(defn- current-world
  []
  ((:load-world @state-ctx)))

(defn- read-runtime-state
  [k]
  (let [store (runtime-state/runtime-state-store)]
    (ports/read-runtime-state store k)))

(defn- write-runtime-state!
  [k v]
  (let [store (runtime-state/runtime-state-store)]
    (ports/write-runtime-state! store k v)))

(defn- find-sailing-transports
  "Returns list of [pos transport] for computer transports in sailing mode
   with 4+ armies."
  []
  (let [game-map (current-world)
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
  (when-let [targets (seq (read-runtime-state :land-ho-targets))]
    (let [target (first targets)
          transports (find-sailing-transports)]
      (if (empty? transports)
        nil ;; No qualifying transports -- leave target at front
        (let [[tpos _] (nearest-transport transports target)
              path (pathfinding-bfs/bfs-to-land-ho-target
                     tpos target (read-runtime-state :computer-map))]
          (if path
            (do
              ;; Assign transport to invading mode
              (update-game-map! assoc-in
                                (conj tpos :contents :transport-mission) :invading)
              (update-game-map! assoc-in
                                (conj tpos :contents :invasion-target) target)
              (update-game-map! assoc-in
                                (conj tpos :contents :invasion-path) (vec path))
              ;; Remove target from queue
              (write-runtime-state! :land-ho-targets (vec (rest targets))))
            ;; BFS failed -- move target to end of queue
            (write-runtime-state! :land-ho-targets
                                  (conj (vec (rest targets)) target))))))))
