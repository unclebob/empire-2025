;; mutation-tested: 2026-03-02
(ns empire.computer.army.movement
  "Shared movement and passability helpers for computer armies."
  (:require [empire.state.api :as sa]
            [empire.computer.core :as core]
            [empire.debug.logging :as debug]
            [empire.computer.movement :as computer-movement]))

(defn on-same-continent?
  [country-a country-b]
  ((:on-same-continent? (sa/state-ctx)) country-a country-b))

(defn merge-continents!
  [stamp-id existing-cid]
  ((:merge-continents! (sa/state-ctx)) stamp-id existing-cid))

(defn adjacent-to-sea?
  [pos]
  (some (fn [neighbor]
          (= :sea (:type (get-in (sa/current-world) neighbor))))
        (core/get-neighbors pos)))

(defn- seed-coastal-registry
  "One-time full-map scan to populate coastal cell registry for country-id.
   Called only when the registry is empty for that country."
  [country-id]
  (let [gm (sa/current-world)
        coastal (for [i (range (count gm))
                      j (range (count (first gm)))
                      :let [cell (get-in gm [i j])]
                      :when (and (= :land (:type cell))
                                 (or (nil? (:country-id cell))
                                     (= country-id (:country-id cell)))
                                 (adjacent-to-sea? [i j]))]
                  [i j])]
    (let [registry (or (sa/read-state :coastal-cells-by-country) {})]
      (sa/write-state! :coastal-cells-by-country
                            (assoc registry country-id (set coastal))))))

(defn ensure-coastal-registry
  [country-id]
  (when (empty? (get (sa/read-state :coastal-cells-by-country) country-id))
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
  (let [registry (or (sa/read-state :coastal-cells-by-country) {})]
    (sa/write-state! :coastal-cells-by-country
                          (update registry country-id
                                  (fn [s] (into (or s #{}) coastal))))))

(defn register-coastal-cells
  [pos country-id]
  (when country-id
    (let [game-map (sa/current-world)
          all-pos (cons pos (core/get-neighbors pos))]
      (merge-neighbor-continents! all-pos country-id game-map)
      (let [coastal (local-coastal-cells all-pos country-id game-map)]
        (when (seq coastal)
          (update-coastal-registry! country-id coastal))))))

(defn sovereign-passable?
  [country-id cell]
  (and cell
       (#{:land :city} (:type cell))
       (not= :computer (:city-status cell))
       (or (nil? country-id)
           (= :city (:type cell))
           (nil? (:country-id cell))
           (on-same-continent? country-id (:country-id cell)))))

(defn get-passable-neighbors
  [pos country-id]
  (let [game-map (sa/current-world)]
    (filter (fn [neighbor]
              (sovereign-passable? country-id (get-in game-map neighbor)))
            (core/get-neighbors pos))))

(defn get-empty-passable-neighbors
  [pos country-id]
  (let [game-map (sa/current-world)]
    (filter (fn [neighbor]
              (let [cell (get-in game-map neighbor)]
                (nil? (:contents cell))))
            (get-passable-neighbors pos country-id))))

(defn find-nearest-unclaimed
  [candidates pos]
  (let [unclaimed (remove (or (sa/read-state :claimed-objectives) #{}) candidates)]
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
  [pos target]
  (when (core/move-unit-to pos target)
    (debug/log-computer-event! :army-move pos {:to target})
    (sa/update-world! update-in (conj target :contents :move-history)
                      update-move-history pos)
    (computer-movement/update-cell-visibility! pos :computer)
    (computer-movement/update-cell-visibility! target :computer)
    (register-coastal-cells target
                            (:country-id (get-in (sa/current-world) (conj target :contents))))
    target))

(defn- sovereignty-passability-fn
  "Returns a passability function for A* that respects sovereignty for the given country-id."
  [country-id]
  (fn [cell] (sovereign-passable? country-id cell)))

(defn move-toward-objective
  [pos objective country-id]
  (let [unit (get-in (sa/current-world) (conj pos :contents))
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

(defn in-bounds?
  [pos]
  (let [[c r] pos
        game-map (sa/current-world)]
    (and (>= c 0) (>= r 0)
         (< c (count game-map))
         (< r (count (first game-map))))))
