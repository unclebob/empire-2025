;; mutation-tested: 2026-03-03
(ns empire.computer.transport-sailing
  "Transport sailing — path following, retreating, and invasion missions."
  (:require [empire.application.ports.movement-execution :as movement-port]
            [empire.application.state-access :as sa]
            [empire.computer.core :as core]
            [empire.computer.transport-core :as tc]
            [empire.computer.transport-sailing-path :as sailing-path]
            [empire.computer.transport-unloading :as unloading]))

(def ^:private invasion-unload-radius 2)
(def ^:private invasion-threat-unload-radius 3)
(def ^:private invasion-threat-scan-radius 2)

(def ^:private player-ship-types
  #{:patrol-boat :destroyer :submarine :transport :carrier :battleship})

(defn- movement-services
  []
  (:movement-port (sa/state-ctx)))

(defn- update-cell-visibility!
  [pos owner]
  (movement-port/movement-update-cell-visibility (movement-services) pos owner))

(defn- enemy-ship-near-target?
  [target radius]
  (let [world (sa/current-world)
        [tx ty] target
        min-x (max 0 (- tx radius))
        max-x (min (dec (count world)) (+ tx radius))
        min-y (max 0 (- ty radius))
        max-y (min (dec (count (first world))) (+ ty radius))]
    (boolean
     (some true?
           (for [x (range min-x (inc max-x))
                 y (range min-y (inc max-y))]
             (let [u (get-in world [x y :contents])]
               (and u
                    (= :player (:owner u))
                    (contains? player-ship-types (:type u)))))))))
(defn compute-sail-path
  "Compute BFS path from transport position to best coastal target.
   Looks 4 levels past first hit; prefers unowned coast over unexplored."
  [pos]
  (sailing-path/compute-sail-path
    pos
    (sa/read-state :computer-map)))

(defn- launch-from-city-to-sea
  [pos transport]
  (let [world (sa/current-world)
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
            (update-cell-visibility! pos :computer)
            (update-cell-visibility! sea-pos :computer)
            sea-pos))))))

(defn- sail-retreat
  [pos sail-path]
  (let [retreat (first (tc/get-passable-sea-neighbors pos))]
    (when (core/move-unit-to pos retreat)
      (update-cell-visibility! pos :computer)
      (update-cell-visibility! retreat :computer)
      (sa/update-world! assoc-in
                        (conj retreat :contents :sail-path)
                        (vec (cons pos sail-path)))
      retreat)))

(defn- sail-take-second-step
  [from-pos next-pos remaining]
  (let [step2 (or (first remaining)
                  (sailing-path/continue-pos (sa/current-world) from-pos next-pos))
        remaining2 (if (seq remaining) (vec (rest remaining)) [])
        moved2 (when step2 (core/move-unit-to next-pos step2))]
    (if moved2
      (do (update-cell-visibility! next-pos :computer)
          (update-cell-visibility! step2 :computer)
          (sa/update-world! assoc-in
                            (conj step2 :contents :sail-path) remaining2)
          (unloading/try-opportunistic-unload step2)
          step2)
      (do (sa/update-world! assoc-in
                            (conj next-pos :contents :sail-path) remaining)
          (unloading/try-opportunistic-unload next-pos)
          next-pos))))

(defn- sail-follow-path
  [pos sail-path]
  (let [next-pos (first sail-path)
        remaining (vec (rest sail-path))]
    (if (core/move-unit-to pos next-pos)
      (do (update-cell-visibility! pos :computer)
          (update-cell-visibility! next-pos :computer)
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
      (sa/update-world! assoc-in (conj pos :contents :sail-path) sail-path)
      (sail-follow-path pos sail-path))))

(defn- maybe-unload-or-sail!
  [pos transport]
  (if (unloading/has-nearby-unloadable-land? pos transport 5)
    (set-unloading-and-try! pos)
    (or (compute-and-follow-sail-path! pos)
        ;; No path and no adjacent coast at all: switch to unloading crawl mode.
        (when-not (some (fn [n]
                          (let [cell (get-in (sa/current-world) n)]
                            (and cell (#{:land :city} (:type cell)))))
                        (core/get-neighbors pos))
          (set-unloading-and-try! pos)))))

(defn- handle-loaded-transport-without-path!
  [pos transport]
  (if-let [sea-pos (launch-from-city-to-sea pos transport)]
    (let [transport' (get-in (sa/current-world) (conj sea-pos :contents))]
      (maybe-unload-or-sail! sea-pos transport'))
    (maybe-unload-or-sail! pos transport)))

(defn- sailing-state
  [sail-path army-count never-reload?]
  (or ({[true true false] :empty-reload
        [true true true] :empty-never-reload
        [true false false] :loaded-no-path
        [true false true] :loaded-no-path}
       [(empty? sail-path) (zero? army-count) (boolean never-reload?)])
      (when (seq sail-path) :follow-path)))

(defn- loaded-no-path-action
  [pos transport]
  (let [city-cell? (= :city (:type (get-in (sa/current-world) pos)))
        adjacent-land? (some (fn [n]
                               (let [cell (get-in (sa/current-world) n)]
                                 (and cell (#{:land :city} (:type cell)))))
                             (core/get-neighbors pos))]
    (cond
      city-cell? (handle-loaded-transport-without-path! pos transport)
      adjacent-land? (maybe-unload-or-sail! pos transport)
      :else (set-unloading-and-try! pos))))

(defn- follow-path-action
  [pos sail-path]
  (sail-follow-path pos sail-path))

(defn- empty-never-reload-action
  [pos]
  (when-let [new-path (seq (compute-sail-path pos))]
    (sa/update-world! assoc-in (conj pos :contents :sail-path) (vec new-path))
    (sail-follow-path pos (vec new-path))))

(defn- mission-handler
  [state pos transport sail-path]
  ({:empty-reload (fn [] (tc/set-transport-mission pos :loading))
    :empty-never-reload (fn [] (empty-never-reload-action pos))
    :loaded-no-path (fn [] (loaded-no-path-action pos transport))
    :follow-path (fn [] (follow-path-action pos sail-path))}
   state))

(defn process-sailing-mission
  [pos]
  (let [transport (get-in (sa/current-world) (conj pos :contents))
        sail-path (:sail-path transport)
        army-count (:army-count transport 0)
        never-reload? (:never-reload? transport)
        state (sailing-state sail-path army-count never-reload?)]
    (when-let [handler (mission-handler state pos transport sail-path)]
      (handler))))

(defn- clear-invasion-path!
  [pos]
  (sa/update-world! update-in (conj pos :contents)
                    dissoc :invasion-path :invasion-path-origin))

(defn- store-invasion-path!
  [pos remaining]
  (sa/update-world! update-in (conj pos :contents)
                    assoc :invasion-path remaining
                    :invasion-path-origin pos))

(defn- move-invasion-step!
  [from to]
  (when (core/move-unit-to from to)
    (update-cell-visibility! from :computer)
    (update-cell-visibility! to :computer)
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
  (let [radius (if (enemy-ship-near-target? target invasion-threat-scan-radius)
                 invasion-threat-unload-radius
                 invasion-unload-radius)]
    (or (<= (core/chebyshev-distance pos target) radius)
        (and transport (unloading/has-nearby-unloadable-land? pos transport 5)))))

(defn- retreat-away-from-target!
  [pos target]
  (let [world (sa/current-world)
        current-distance (core/chebyshev-distance pos target)
        candidates (->> (tc/get-passable-sea-neighbors pos)
                        (filter #(nil? (get-in world (conj % :contents))))
                        (filter #(< current-distance (core/chebyshev-distance % target))))
        chosen (first (sort-by (fn [p]
                                 [(- (core/chebyshev-distance p target)) p])
                               candidates))]
    (when (and chosen (core/move-unit-to pos chosen))
      (update-cell-visibility! pos :computer)
      (update-cell-visibility! chosen :computer)
      chosen)))

(defn- handle-invasion-threat-near-target!
  [pos target]
  (when (and target
             (<= (core/chebyshev-distance pos target) invasion-threat-unload-radius)
             (enemy-ship-near-target? target invasion-threat-scan-radius))
    (if-let [retreated (retreat-away-from-target! pos target)]
      (tc/set-transport-mission retreated :unloading)
      (tc/set-transport-mission pos :unloading))
    true))

(defn- continue-invading-without-path!
  [pos target invading-step]
  (if target
    (let [pos1 (or (invading-step pos) pos)
          pos2 (or (invading-step pos1) pos1)
          transport2 (get-in (sa/current-world) (conj pos2 :contents))]
      (when (unload-zone? pos2 target transport2)
        (tc/set-transport-mission pos2 :unloading)))
    (tc/set-transport-mission pos :unloading)))

(defn- choose-invading-step
  [from target]
  (let [world (sa/current-world)
        transport (get-in world (conj from :contents))
        last-pos (:invasion-last-pos transport)
        neighbors (->> (tc/get-passable-sea-neighbors from)
                       (filter #(nil? (get-in world (conj % :contents)))))
        candidates (if (and last-pos (> (count neighbors) 1))
                     (remove #(= % last-pos) neighbors)
                     neighbors)
        current-distance (if target
                           (core/chebyshev-distance from target)
                           ##Inf)
        better (if target
                 (filter #(< (core/chebyshev-distance % target) current-distance) candidates)
                 candidates)
        pool (if (seq better) better candidates)]
    (first (sort-by (fn [p]
                      [(if target
                         (core/chebyshev-distance p target)
                         0)
                       p])
                    pool))))

(defn- invading-step
  [from target]
  (when-let [chosen (choose-invading-step from target)]
    (when (core/move-unit-to from chosen)
      (update-cell-visibility! from :computer)
      (update-cell-visibility! chosen :computer)
      ;; Force recompute from new position next round.
      (clear-invasion-path! chosen)
      (sa/update-world! assoc-in (conj chosen :contents :invasion-last-pos) from)
      chosen)))

(defn process-invading-mission
  "Follow precomputed invasion path. Steps up to 2 cells per round.
   When path exhausted, transition to unloading with coast-crawl."
  [pos]
  (let [transport (get-in (sa/current-world) (conj pos :contents))
        path (:invasion-path transport)
        target (or (:invasion-target transport) (:major-invasion-target transport))]
    (if (handle-invasion-threat-near-target! pos target)
      nil
      (if (empty? path)
        (continue-invading-without-path! pos target #(invading-step % target))
        (when (= :blocked (continue-invading-via-path! pos path))
          ;; Blocked — sidestep toward target when possible, otherwise retry next round.
          (invading-step pos target))))))
