(ns empire.computer.army.movement
  "Shared movement and passability helpers for computer armies."
  (:require [empire.adapters.state.runtime :as runtime-state]
            [empire.application.ports :as ports]
            [empire.application.runtime :as app-runtime]
            [empire.application.state :as app-state]
            [empire.computer.core :as core]
            [empire.debug :as debug]
            [empire.movement.pathfinding :as pathfinding]
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

(defn- write-runtime-state!
  [k v]
  (let [store (runtime-state/runtime-state-store)]
    (ports/write-runtime-state! store k v)))

(defn adjacent-to-sea?
  "Returns true if position has at least one adjacent sea cell."
  [pos]
  (some (fn [neighbor]
          (= :sea (:type (get-in (current-world) neighbor))))
        (core/get-neighbors pos)))

(defn- seed-coastal-registry
  "One-time full-map scan to populate coastal cell registry for country-id.
   Called only when the registry is empty for that country."
  [country-id]
  (let [gm (current-world)
        coastal (for [i (range (count gm))
                      j (range (count (first gm)))
                      :let [cell (get-in gm [i j])]
                      :when (and (= :land (:type cell))
                                 (or (nil? (:country-id cell))
                                     (= country-id (:country-id cell)))
                                 (adjacent-to-sea? [i j]))]
                  [i j])]
    (let [registry (or (read-runtime-state :coastal-cells-by-country) {})]
      (write-runtime-state! :coastal-cells-by-country
                            (assoc registry country-id (set coastal))))))

(defn ensure-coastal-registry [country-id]
  (when (empty? (get (read-runtime-state :coastal-cells-by-country) country-id))
    (seed-coastal-registry country-id)))

(defn- merge-neighbor-continents!
  [all-pos country-id game-map]
  (doseq [p all-pos]
    (let [cid (:country-id (get-in game-map p))]
      (when (and cid (not= cid country-id))
        (runtime-state/merge-continents! country-id cid)))))

(defn- local-coastal-cells
  [all-pos country-id game-map]
  (filter (fn [p]
            (let [cell (get-in game-map p)]
              (and cell
                   (= :land (:type cell))
                   (or (nil? (:country-id cell))
                       (runtime-state/on-same-continent? country-id (:country-id cell)))
                   (adjacent-to-sea? p))))
          all-pos))

(defn- update-coastal-registry!
  [country-id coastal]
  (let [registry (or (read-runtime-state :coastal-cells-by-country) {})]
    (write-runtime-state! :coastal-cells-by-country
                          (update registry country-id
                                  (fn [s] (into (or s #{}) coastal))))))

(defn register-coastal-cells
  "Registers coastal land cells near pos for the given country-id.
   Checks pos + neighbors; adds any land cell adjacent to sea with matching country.
  Also detects continent bumps when adjacent land has a different country-id."
  [pos country-id]
  (when country-id
    (let [game-map (current-world)
          all-pos (cons pos (core/get-neighbors pos))]
      (merge-neighbor-continents! all-pos country-id game-map)
      (let [coastal (local-coastal-cells all-pos country-id game-map)]
        (when (seq coastal)
          (update-coastal-registry! country-id coastal))))))

(defn sovereign-passable?
  "Returns true if a computer army with country-id can enter the cell.
   Foreign land (different non-nil country-id) is blocked.
   Computer cities are blocked; free and player cities are passable."
  [country-id cell]
  (and cell
       (#{:land :city} (:type cell))
       (not= :computer (:city-status cell))
       (or (nil? country-id)
           (= :city (:type cell))
           (nil? (:country-id cell))
           (runtime-state/on-same-continent? country-id (:country-id cell)))))

(defn get-passable-neighbors
  "Returns passable land neighbors for an army, respecting sovereignty."
  [pos country-id]
  (let [game-map (current-world)]
    (filter (fn [neighbor]
              (sovereign-passable? country-id (get-in game-map neighbor)))
            (core/get-neighbors pos))))

(defn get-empty-passable-neighbors
  "Returns passable land neighbors with no unit occupying them."
  [pos country-id]
  (let [game-map (current-world)]
    (filter (fn [neighbor]
              (let [cell (get-in game-map neighbor)]
                (nil? (:contents cell))))
            (get-passable-neighbors pos country-id))))

(defn find-nearest-unclaimed
  "Find nearest position from candidates not in claimed-objectives."
  [candidates pos]
  (let [unclaimed (remove (or (read-runtime-state :claimed-objectives) #{}) candidates)]
    (when (seq unclaimed)
      (apply min-key #(core/distance pos %) unclaimed))))

(defn- update-move-history
  "Adds pos to move-history vector, keeping at most 4 entries."
  [history pos]
  (let [v (conj (or history []) pos)]
    (if (> (count v) 4)
      (subvec v (- (count v) 4))
      v)))

(defn try-move
  "Attempt to move army from pos to target. Returns target if moved, nil if blocked."
  [pos target]
  (when (core/move-unit-to pos target)
    (debug/log-computer-event! :army-move pos {:to target})
    (update-game-map! update-in (conj target :contents :move-history)
                      update-move-history pos)
    (visibility/update-cell-visibility pos :computer)
    (visibility/update-cell-visibility target :computer)
    (register-coastal-cells target
                            (:country-id (get-in (current-world) (conj target :contents))))
    target))

(defn- sovereignty-passability-fn
  "Returns a passability function for A* that respects sovereignty for the given country-id."
  [country-id]
  (fn [cell] (sovereign-passable? country-id cell)))

(defn move-toward-objective
  "Move army one step toward objective. If preferred step is occupied,
   try other empty neighbors sorted by distance to objective.
   Filters out cells in move-history to prevent oscillation."
  [pos objective country-id]
  (let [unit (get-in (current-world) (conj pos :contents))
        history (set (:move-history unit))
        pass-fn (when country-id (sovereignty-passability-fn country-id))
        preferred (pathfinding/next-step pos objective :army pass-fn country-id)]
    (or (when (and preferred (not (history preferred)))
          (try-move pos preferred))
        (let [empty-neighbors (get-empty-passable-neighbors pos country-id)
              filtered (remove history empty-neighbors)]
          (when (seq filtered)
            (let [sorted (sort-by #(core/distance % objective) filtered)]
              (try-move pos (first sorted))))))))

(defn in-bounds? [pos]
  (let [[c r] pos
        game-map (current-world)]
    (and (>= c 0) (>= r 0)
         (< c (count game-map))
         (< r (count (first game-map))))))
