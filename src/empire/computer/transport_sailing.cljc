;; mutation-tested: 2026-02-28
(ns empire.computer.transport-sailing
  "Transport sailing — path following, retreating, and invasion missions."
  (:require [empire.adapters.state.runtime :as runtime-state]
            [empire.application.ports :as ports]
            [empire.application.runtime :as app-runtime]
            [empire.application.state :as app-state]
            [empire.computer.core :as core]
            [empire.computer.transport-core :as tc]
            [empire.computer.transport-unloading :as unloading]
            [empire.movement.pathfinding-bfs :as pathfinding-bfs]
            [empire.movement.visibility :as visibility]))

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

(defn- passable-sea?
  "Returns true if pos is a passable sea cell for a transport."
  [pos]
  (let [cell (get-in (current-world) pos)]
    (and cell
         (= :sea (:type cell))
         (or (nil? (:contents cell))
             (= :computer (:owner (:contents cell)))))))

(defn- continue-pos
  "Returns pos + direction vector, or nil if out of bounds or not passable sea."
  [from to]
  (let [dr (- (first to) (first from))
        dc (- (second to) (second from))
        candidate [(+ (first to) dr) (+ (second to) dc)]]
    (when (passable-sea? candidate) candidate)))

(defn compute-sail-path
  "Compute BFS path from transport position to best coastal target.
   Looks 4 levels past first hit; prefers unowned coast over unexplored."
  [pos]
  (pathfinding-bfs/bfs-to-coast-target
    pos (read-runtime-state :computer-map)))

(defn- launch-from-city-to-sea
  [pos transport]
  (let [world (current-world)
        cell-type (get-in world (conj pos :type))]
    (when (= :city cell-type)
      (let [target-ref (or (:invasion-target transport)
                           (:major-invasion-target transport)
                           (:pickup-continent-pos transport)
                           pos)
            options (->> (core/get-neighbors pos)
                         (filter (fn [n]
                                   (let [c (get-in world n)]
                                     (and c
                                          (= :sea (:type c))
                                          (nil? (:contents c))))))
                         (sort-by (fn [n]
                                    [(core/chebyshev-distance n target-ref) n])))]
        (when-let [sea-pos (first options)]
          (when (core/move-unit-to pos sea-pos)
            (visibility/update-cell-visibility pos :computer)
            (visibility/update-cell-visibility sea-pos :computer)
            sea-pos))))))

(defn- sail-retreat
  [pos sail-path]
  (let [retreat (first (tc/get-passable-sea-neighbors pos))]
    (when (core/move-unit-to pos retreat)
      (visibility/update-cell-visibility pos :computer)
      (visibility/update-cell-visibility retreat :computer)
      (update-game-map! assoc-in
                        (conj retreat :contents :sail-path)
                        (vec (cons pos sail-path)))
      retreat)))

(defn- sail-take-second-step
  [from-pos next-pos remaining]
  (let [step2 (or (first remaining) (continue-pos from-pos next-pos))
        remaining2 (if (seq remaining) (vec (rest remaining)) [])
        moved2 (when step2 (core/move-unit-to next-pos step2))]
    (if moved2
      (do (visibility/update-cell-visibility next-pos :computer)
          (visibility/update-cell-visibility step2 :computer)
          (update-game-map! assoc-in
                            (conj step2 :contents :sail-path) remaining2)
          (unloading/try-opportunistic-unload step2)
          step2)
      (do (update-game-map! assoc-in
                            (conj next-pos :contents :sail-path) remaining)
          (unloading/try-opportunistic-unload next-pos)
          next-pos))))

(defn- sail-follow-path
  [pos sail-path]
  (let [next-pos (first sail-path)
        remaining (vec (rest sail-path))]
    (if (core/move-unit-to pos next-pos)
      (do (visibility/update-cell-visibility pos :computer)
          (visibility/update-cell-visibility next-pos :computer)
          (sail-take-second-step pos next-pos remaining))
      (sail-retreat pos sail-path))))

(defn process-sailing-mission
  [pos]
  (let [transport (get-in (current-world) (conj pos :contents))
        sail-path (:sail-path transport)
        army-count (:army-count transport 0)]
    (cond
      (and (empty? sail-path) (zero? army-count))
      (tc/set-transport-mission pos :loading)

      (and (empty? sail-path) (pos? army-count))
      (if-let [sea-pos (launch-from-city-to-sea pos transport)]
        (let [transport' (get-in (current-world) (conj sea-pos :contents))]
          (if (unloading/has-nearby-unloadable-land? sea-pos transport' 5)
            (do (tc/set-transport-mission sea-pos :unloading)
                (unloading/try-opportunistic-unload sea-pos))
            (when-let [new-path (seq (compute-sail-path sea-pos))]
              (update-game-map! assoc-in (conj sea-pos :contents :sail-path) (vec new-path))
              (sail-follow-path sea-pos (vec new-path)))))
        (if (unloading/has-nearby-unloadable-land? pos transport 5)
          (do (tc/set-transport-mission pos :unloading)
              (unloading/try-opportunistic-unload pos))
          (when-let [new-path (seq (compute-sail-path pos))]
            (update-game-map! assoc-in (conj pos :contents :sail-path) (vec new-path))
            (sail-follow-path pos (vec new-path)))))

      (seq sail-path)
      (sail-follow-path pos sail-path))))

(defn process-invading-mission
  "Follow precomputed invasion path. Steps up to 2 cells per round.
   When path exhausted, transition to unloading with coast-crawl."
  [pos]
  (let [transport (get-in (current-world) (conj pos :contents))
        path (:invasion-path transport)
        target (or (:invasion-target transport) (:major-invasion-target transport))]
    (letfn [(invading-step [from]
              (let [neighbors (->> (tc/get-passable-sea-neighbors from)
                                   (filter #(nil? (get-in (current-world) (conj % :contents)))))
                    current-distance (if target
                                       (core/chebyshev-distance from target)
                                       ##Inf)
                    better (if target
                             (filter #(< (core/chebyshev-distance % target) current-distance) neighbors)
                             neighbors)
                    chosen (first (sort-by (fn [p]
                                             [(if target
                                                (core/chebyshev-distance p target)
                                                0)
                                              p])
                                           better))]
                (when (and chosen (core/move-unit-to from chosen))
                  (visibility/update-cell-visibility from :computer)
                  (visibility/update-cell-visibility chosen :computer)
                  ;; Force recompute from new position next round.
                  (update-game-map! update-in (conj chosen :contents)
                                    dissoc :invasion-path :invasion-path-origin)
                  chosen)))]
    (if (empty? path)
      (if target
        (let [pos1 (or (invading-step pos) pos)
              pos2 (or (invading-step pos1) pos1)
              t2 (get-in (current-world) (conj pos2 :contents))
              in-unload-zone? (<= (core/chebyshev-distance pos2 target) 2)]
          (when (or in-unload-zone?
                    (and t2 (unloading/has-nearby-unloadable-land? pos2 t2 5)))
            (tc/set-transport-mission pos2 :unloading)))
        (tc/set-transport-mission pos :unloading))
      (let [step1 (first path)
            remaining1 (vec (rest path))]
        (if (core/move-unit-to pos step1)
          (do
            (visibility/update-cell-visibility pos :computer)
            (visibility/update-cell-visibility step1 :computer)
            (if (empty? remaining1)
              (do (update-game-map! update-in (conj step1 :contents)
                                    dissoc :invasion-path :invasion-path-origin)
                  (tc/set-transport-mission step1 :unloading))
              (let [step2 (first remaining1)
                    remaining2 (vec (rest remaining1))]
                (if (core/move-unit-to step1 step2)
                  (do
                    (visibility/update-cell-visibility step1 :computer)
                    (visibility/update-cell-visibility step2 :computer)
                    (if (empty? remaining2)
                      (do (update-game-map! update-in (conj step2 :contents)
                                            dissoc :invasion-path :invasion-path-origin)
                          (tc/set-transport-mission step2 :unloading))
                      (update-game-map! update-in (conj step2 :contents)
                                        assoc :invasion-path remaining2
                                        :invasion-path-origin step2)))
          (update-game-map! update-in (conj step1 :contents)
                                    assoc :invasion-path remaining1
                                    :invasion-path-origin step1)))))
          ;; Blocked — sidestep toward target when possible, otherwise retry next round.
          (invading-step pos)))))))
