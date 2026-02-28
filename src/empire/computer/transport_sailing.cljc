;; mutation-tested: 2026-02-28
(ns empire.computer.transport-sailing
  "Transport sailing — path following, retreating, and invasion missions."
  (:require [empire.atoms :as atoms]
            [empire.computer.core :as core]
            [empire.computer.transport-core :as tc]
            [empire.computer.transport-unloading :as unloading]
            [empire.movement.pathfinding-bfs :as pathfinding-bfs]
            [empire.movement.visibility :as visibility]))

(defn- passable-sea?
  "Returns true if pos is a passable sea cell for a transport."
  [pos]
  (let [cell (get-in @atoms/game-map pos)]
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
    pos @atoms/computer-map))

(defn- sail-retreat
  [pos sail-path]
  (let [retreat (first (tc/get-passable-sea-neighbors pos))]
    (when (core/move-unit-to pos retreat)
      (visibility/update-cell-visibility pos :computer)
      (visibility/update-cell-visibility retreat :computer)
      (swap! atoms/game-map assoc-in
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
          (swap! atoms/game-map assoc-in
                 (conj step2 :contents :sail-path) remaining2)
          (unloading/try-opportunistic-unload step2)
          step2)
      (do (swap! atoms/game-map assoc-in
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
  (let [transport (get-in @atoms/game-map (conj pos :contents))
        sail-path (:sail-path transport)
        army-count (:army-count transport 0)]
    (cond
      (and (empty? sail-path) (zero? army-count))
      (tc/set-transport-mission pos :loading)

      (and (empty? sail-path) (pos? army-count))
      (do (tc/set-transport-mission pos :unloading)
          (unloading/try-opportunistic-unload pos))

      (seq sail-path)
      (sail-follow-path pos sail-path))))

(defn process-invading-mission
  "Follow precomputed invasion path. Steps up to 2 cells per round.
   When path exhausted, transition to unloading with coast-crawl."
  [pos]
  (let [transport (get-in @atoms/game-map (conj pos :contents))
        path (:invasion-path transport)]
    (if (empty? path)
      (tc/set-transport-mission pos :unloading)
      (let [step1 (first path)
            remaining1 (vec (rest path))]
        (if (core/move-unit-to pos step1)
          (do
            (visibility/update-cell-visibility pos :computer)
            (visibility/update-cell-visibility step1 :computer)
            (if (empty? remaining1)
              (do (swap! atoms/game-map update-in (conj step1 :contents) dissoc :invasion-path)
                  (tc/set-transport-mission step1 :unloading))
              (let [step2 (first remaining1)
                    remaining2 (vec (rest remaining1))]
                (if (core/move-unit-to step1 step2)
                  (do
                    (visibility/update-cell-visibility step1 :computer)
                    (visibility/update-cell-visibility step2 :computer)
                    (if (empty? remaining2)
                      (do (swap! atoms/game-map update-in (conj step2 :contents) dissoc :invasion-path)
                          (tc/set-transport-mission step2 :unloading))
                      (swap! atoms/game-map assoc-in
                             (conj step2 :contents :invasion-path) remaining2)))
                  (swap! atoms/game-map assoc-in
                         (conj step1 :contents :invasion-path) remaining1)))))
          ;; Blocked — keep path for retry next round
          nil)))))
