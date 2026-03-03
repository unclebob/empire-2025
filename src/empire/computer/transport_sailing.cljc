;; mutation-tested: 2026-03-02
(ns empire.computer.transport-sailing
  "Transport sailing — path following, retreating, and invasion missions."
  (:require [empire.application.runtime :as app-runtime]
            [empire.application.state :as app-state]
            [empire.computer.core :as core]
            [empire.computer.transport-core :as tc]
            [empire.computer.transport-sailing.path :as sailing-path]
            [empire.computer.transport-unloading :as unloading]
            [empire.movement.visibility :as visibility]))

(def ^:private state-ctx
  (delay (app-runtime/default-state-ctx)))

(defn- update-game-map!
  [f & args]
  (apply app-state/update-world! @state-ctx f args))

(defn- current-world
  []
  ((:load-world @state-ctx)))
(defn compute-sail-path
  "Compute BFS path from transport position to best coastal target.
   Looks 4 levels past first hit; prefers unowned coast over unexplored."
  [pos]
  (sailing-path/compute-sail-path
    pos
    ((:read-runtime-state @state-ctx) :computer-map)))

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
  (let [step2 (or (first remaining)
                  (sailing-path/continue-pos (current-world) from-pos next-pos))
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

(defn- set-unloading-and-try!
  [pos]
  (tc/set-transport-mission pos :unloading)
  (unloading/try-opportunistic-unload pos))

(defn- compute-and-follow-sail-path!
  [pos]
  (when-let [new-path (seq (compute-sail-path pos))]
    (let [sail-path (vec new-path)]
      (update-game-map! assoc-in (conj pos :contents :sail-path) sail-path)
      (sail-follow-path pos sail-path))))

(defn- maybe-unload-or-sail!
  [pos transport]
  (if (unloading/has-nearby-unloadable-land? pos transport 5)
    (set-unloading-and-try! pos)
    (or (compute-and-follow-sail-path! pos)
        ;; No path and no adjacent coast at all: switch to unloading crawl mode.
        (when-not (some (fn [n]
                          (let [cell (get-in (current-world) n)]
                            (and cell (#{:land :city} (:type cell)))))
                        (core/get-neighbors pos))
          (set-unloading-and-try! pos)))))

(defn- handle-loaded-transport-without-path!
  [pos transport]
  (if-let [sea-pos (launch-from-city-to-sea pos transport)]
    (let [transport' (get-in (current-world) (conj sea-pos :contents))]
      (maybe-unload-or-sail! sea-pos transport'))
    (maybe-unload-or-sail! pos transport)))

(defn process-sailing-mission
  [pos]
  (let [transport (get-in (current-world) (conj pos :contents))
        sail-path (:sail-path transport)
        army-count (:army-count transport 0)
        never-reload? (:never-reload? transport)
        city-cell? (= :city (:type (get-in (current-world) pos)))
        adjacent-land? (some (fn [n]
                               (let [cell (get-in (current-world) n)]
                                 (and cell (#{:land :city} (:type cell)))))
                             (core/get-neighbors pos))]
    (cond
      (and (empty? sail-path) (zero? army-count) (not never-reload?))
      (tc/set-transport-mission pos :loading)

      (and (empty? sail-path) (zero? army-count) never-reload?)
      (when-let [new-path (seq (compute-sail-path pos))]
        (update-game-map! assoc-in (conj pos :contents :sail-path) (vec new-path))
        (sail-follow-path pos (vec new-path)))

      (and (empty? sail-path) (pos? army-count))
      (cond
        city-cell?
        (handle-loaded-transport-without-path! pos transport)

        adjacent-land?
        (maybe-unload-or-sail! pos transport)

        :else
        (set-unloading-and-try! pos))

      (seq sail-path)
      (sail-follow-path pos sail-path))))

(defn- clear-invasion-path!
  [pos]
  (update-game-map! update-in (conj pos :contents)
                    dissoc :invasion-path :invasion-path-origin))

(defn- store-invasion-path!
  [pos remaining]
  (update-game-map! update-in (conj pos :contents)
                    assoc :invasion-path remaining
                    :invasion-path-origin pos))

(defn- move-invasion-step!
  [from to]
  (when (core/move-unit-to from to)
    (visibility/update-cell-visibility from :computer)
    (visibility/update-cell-visibility to :computer)
    to))

(defn- finish-invading-at!
  [pos]
  (clear-invasion-path! pos)
  (tc/set-transport-mission pos :unloading))

(defn- continue-invading-via-path!
  [pos path]
  (let [step1 (first path)
        remaining1 (vec (rest path))]
    (if-let [step1-pos (move-invasion-step! pos step1)]
      (if (empty? remaining1)
        (finish-invading-at! step1-pos)
        (let [step2 (first remaining1)
              remaining2 (vec (rest remaining1))]
          (if-let [step2-pos (move-invasion-step! step1-pos step2)]
            (if (empty? remaining2)
              (finish-invading-at! step2-pos)
              (store-invasion-path! step2-pos remaining2))
            (store-invasion-path! step1-pos remaining1))))
      :blocked)))

(defn- unload-zone?
  [pos target transport]
  (or (<= (core/chebyshev-distance pos target) 2)
      (and transport (unloading/has-nearby-unloadable-land? pos transport 5))))

(defn- continue-invading-without-path!
  [pos target invading-step]
  (if target
    (let [pos1 (or (invading-step pos) pos)
          pos2 (or (invading-step pos1) pos1)
          transport2 (get-in (current-world) (conj pos2 :contents))]
      (when (unload-zone? pos2 target transport2)
        (tc/set-transport-mission pos2 :unloading)))
    (tc/set-transport-mission pos :unloading)))

(defn- choose-invading-step
  [from target]
  (let [neighbors (->> (tc/get-passable-sea-neighbors from)
                       (filter #(nil? (get-in (current-world) (conj % :contents)))))
        current-distance (if target
                           (core/chebyshev-distance from target)
                           ##Inf)
        better (if target
                 (filter #(< (core/chebyshev-distance % target) current-distance) neighbors)
                 neighbors)]
    (first (sort-by (fn [p]
                      [(if target
                         (core/chebyshev-distance p target)
                         0)
                       p])
                    better))))

(defn- invading-step
  [from target]
  (when-let [chosen (choose-invading-step from target)]
    (when (core/move-unit-to from chosen)
      (visibility/update-cell-visibility from :computer)
      (visibility/update-cell-visibility chosen :computer)
      ;; Force recompute from new position next round.
      (clear-invasion-path! chosen)
      chosen)))

(defn process-invading-mission
  "Follow precomputed invasion path. Steps up to 2 cells per round.
   When path exhausted, transition to unloading with coast-crawl."
  [pos]
  (let [transport (get-in (current-world) (conj pos :contents))
        path (:invasion-path transport)
        target (or (:invasion-target transport) (:major-invasion-target transport))]
    (if (empty? path)
      (continue-invading-without-path! pos target #(invading-step % target))
      (when (= :blocked (continue-invading-via-path! pos path))
        ;; Blocked — sidestep toward target when possible, otherwise retry next round.
        (invading-step pos target)))))
