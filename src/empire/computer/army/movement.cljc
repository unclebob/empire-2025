;; mutation-tested: 2026-03-02
(ns empire.computer.army.movement
  "Shared movement and passability helpers for computer armies."
  (:require [empire.application.runtime :as app-runtime]
            [empire.application.state :as app-state]
            [empire.computer.core :as core]
            [empire.debug :as debug]
            [empire.computer.movement :as computer-movement]
            [empire.computer.movement :as computer-movement]))

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
  ((:read-runtime-state @state-ctx) k))

(defn- write-runtime-state!
  [k v]
  ((:write-runtime-state! @state-ctx) k v))

(defmulti on-same-continent? (fn [& _] :default))

(defmethod on-same-continent? :default
  [country-a country-b]
  ((:on-same-continent? @state-ctx) country-a country-b))

(defmulti merge-continents! (fn [& _] :default))

(defmethod merge-continents! :default
  [stamp-id existing-cid]
  ((:merge-continents! @state-ctx) stamp-id existing-cid))

(defmulti adjacent-to-sea? (fn [& _] :default))

(defmethod adjacent-to-sea? :default
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

(defmulti ensure-coastal-registry (fn [& _] :default))

(defmethod ensure-coastal-registry :default
  [country-id]
  (when (empty? (get (read-runtime-state :coastal-cells-by-country) country-id))
    (seed-coastal-registry country-id)))

(defn- merge-neighbor-continents!
  [all-pos country-id game-map]
  (doseq [p all-pos]
    (let [cid (:country-id (get-in game-map p))]
      (when (and cid (not= cid country-id))
        (merge-continents! country-id cid)))))

(defn- local-coastal-cells
  [all-pos country-id game-map]
  (filter (fn [p]
            (let [cell (get-in game-map p)]
              (and cell
                   (= :land (:type cell))
                   (or (nil? (:country-id cell))
                       (on-same-continent? country-id (:country-id cell)))
                   (adjacent-to-sea? p))))
          all-pos))

(defn- update-coastal-registry!
  [country-id coastal]
  (let [registry (or (read-runtime-state :coastal-cells-by-country) {})]
    (write-runtime-state! :coastal-cells-by-country
                          (update registry country-id
                                  (fn [s] (into (or s #{}) coastal))))))

(defmulti register-coastal-cells (fn [& _] :default))

(defmethod register-coastal-cells :default
  [pos country-id]
  (when country-id
    (let [game-map (current-world)
          all-pos (cons pos (core/get-neighbors pos))]
      (merge-neighbor-continents! all-pos country-id game-map)
      (let [coastal (local-coastal-cells all-pos country-id game-map)]
        (when (seq coastal)
          (update-coastal-registry! country-id coastal))))))

(defmulti sovereign-passable? (fn [& _] :default))

(defmethod sovereign-passable? :default
  [country-id cell]
  (and cell
       (#{:land :city} (:type cell))
       (not= :computer (:city-status cell))
       (or (nil? country-id)
           (= :city (:type cell))
           (nil? (:country-id cell))
           (on-same-continent? country-id (:country-id cell)))))

(defmulti get-passable-neighbors (fn [& _] :default))

(defmethod get-passable-neighbors :default
  [pos country-id]
  (let [game-map (current-world)]
    (filter (fn [neighbor]
              (sovereign-passable? country-id (get-in game-map neighbor)))
            (core/get-neighbors pos))))

(defmulti get-empty-passable-neighbors (fn [& _] :default))

(defmethod get-empty-passable-neighbors :default
  [pos country-id]
  (let [game-map (current-world)]
    (filter (fn [neighbor]
              (let [cell (get-in game-map neighbor)]
                (nil? (:contents cell))))
            (get-passable-neighbors pos country-id))))

(defmulti find-nearest-unclaimed (fn [& _] :default))

(defmethod find-nearest-unclaimed :default
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

(defmulti try-move (fn [& _] :default))

(defmethod try-move :default
  [pos target]
  (when (core/move-unit-to pos target)
    (debug/log-computer-event! :army-move pos {:to target})
    (update-game-map! update-in (conj target :contents :move-history)
                      update-move-history pos)
    (computer-movement/update-cell-visibility! pos :computer)
    (computer-movement/update-cell-visibility! target :computer)
    (register-coastal-cells target
                            (:country-id (get-in (current-world) (conj target :contents))))
    target))

(defn- sovereignty-passability-fn
  "Returns a passability function for A* that respects sovereignty for the given country-id."
  [country-id]
  (fn [cell] (sovereign-passable? country-id cell)))

(defmulti move-toward-objective (fn [& _] :default))

(defmethod move-toward-objective :default
  [pos objective country-id]
  (let [unit (get-in (current-world) (conj pos :contents))
        history (set (:move-history unit))
        pass-fn (when country-id (sovereignty-passability-fn country-id))
        preferred (computer-movement/next-step pos objective :army pass-fn country-id)]
    (or (when (and preferred (not (history preferred)))
          (try-move pos preferred))
        (let [empty-neighbors (get-empty-passable-neighbors pos country-id)
              filtered (remove history empty-neighbors)]
          (when (seq filtered)
            (let [sorted (sort-by #(core/distance % objective) filtered)]
              (try-move pos (first sorted))))))))

(defmulti in-bounds? (fn [& _] :default))

(defmethod in-bounds? :default
  [pos]
  (let [[c r] pos
        game-map (current-world)]
    (and (>= c 0) (>= r 0)
         (< c (count game-map))
         (< r (count (first game-map))))))
