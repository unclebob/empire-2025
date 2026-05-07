(ns empire.computer.army.movement
  "Shared movement and passability helpers for computer armies."
  (:require [empire.state.api :as sa]
            [empire.computer.shared.action-resolution :as action-resolution]
            [empire.computer.shared.grid :as grid]
            [empire.game-mechanics.debug.logging :as debug]
            [empire.computer.shared.world-query :as world-query]
            [empire.computer.shared.movement :as computer-movement]))

(defn on-same-continent?
  [country-a country-b]
  (sa/on-same-continent? country-a country-b))

(defn merge-continents!
  [stamp-id existing-cid]
  (sa/merge-continents! stamp-id existing-cid))

(defn adjacent-to-sea?
  [pos]
  (some (fn [neighbor]
          (= :sea (:type (get-in (sa/read-state :computer-map) neighbor))))
        (world-query/get-neighbors pos)))

(defn- seed-coastal-registry
  "Populate coastal cell registry for country-id using coastal index when available."
  [country-id]
  (let [gm (sa/read-state :computer-map)
        coastal-index (sa/read-state :coastal-index)
        coastal (if coastal-index
                  (filter (fn [pos]
                            (let [cell (get-in gm pos)]
                              (and cell
                                   (= :land (:type cell))
                                   (or (nil? (:country-id cell))
                                       (= country-id (:country-id cell))))))
                          (:coastal-land-cells coastal-index))
                  (for [i (range (count gm))
                        j (range (count (first gm)))
                        :let [cell (get-in gm [i j])]
                        :when (and (= :land (:type cell))
                                   (or (nil? (:country-id cell))
                                       (= country-id (:country-id cell)))
                                   (adjacent-to-sea? [i j]))]
                    [i j]))]
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
    (let [game-map (sa/read-state :computer-map)
          all-pos (cons pos (world-query/get-neighbors pos))]
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
  (let [game-map (sa/read-state :computer-map)]
    (filter (fn [neighbor]
              (sovereign-passable? country-id (get-in game-map neighbor)))
            (world-query/get-neighbors pos))))

(defn get-empty-passable-neighbors
  [pos country-id]
  (let [game-map (sa/read-state :computer-map)]
    (filter (fn [neighbor]
              (let [cell (get-in game-map neighbor)]
                (nil? (:contents cell))))
            (get-passable-neighbors pos country-id))))

(defn find-nearest-unclaimed
  [candidates pos]
  (let [unclaimed (remove (or (sa/read-state :claimed-objectives) #{}) candidates)]
    (when (seq unclaimed)
      (apply min-key #(grid/distance pos %) unclaimed))))

(defn- update-move-history
  "Adds pos to move-history vector, keeping at most 4 entries."
  [history pos]
  (grid/bounded-conj history pos 4))

(defn try-move
  [pos target]
  (when (action-resolution/move-unit-to pos target)
    (debug/log-computer-event! :army-move pos {:to target})
    (sa/update-world! update-in (conj target :contents :move-history)
                      update-move-history pos)
    (computer-movement/update-cell-visibility! pos :computer)
    (computer-movement/update-cell-visibility! target :computer)
    (register-coastal-cells target
                            (:country-id (get-in (sa/read-state :computer-map) (conj target :contents))))
    target))

(defn step-toward-target-cheap
  [pos target country-id]
  (let [current-dist (grid/distance pos target)
        candidates (->> (get-empty-passable-neighbors pos country-id)
                        (filter #(> current-dist (grid/distance % target)))
                        (sort-by #(grid/distance % target)))]
    (when-let [best (first candidates)]
      (try-move pos best))))

(defn- sovereignty-passability-fn
  "Returns a passability function for A* that respects sovereignty for the given country-id."
  [country-id]
  (fn [cell] (sovereign-passable? country-id cell)))

(defn move-toward-objective
  [pos objective country-id]
  (let [unit (get-in (sa/read-state :computer-map) (conj pos :contents))
        history (set (:move-history unit))
        pass-fn (when country-id (sovereignty-passability-fn country-id))
        preferred (computer-movement/next-step pos objective :army pass-fn country-id)]
    (or (when (and preferred (not (history preferred)))
          (try-move pos preferred))
        (let [empty-neighbors (get-empty-passable-neighbors pos country-id)
              filtered (remove history empty-neighbors)]
          (when (seq filtered)
            (let [sorted (sort-by #(grid/distance % objective) filtered)]
              (try-move pos (first sorted))))))))

(defn local-step-toward-objective
  [pos objective country-id]
  (let [unit (get-in (sa/read-state :computer-map) (conj pos :contents))
        history (set (:move-history unit))
        empty-neighbors (get-empty-passable-neighbors pos country-id)
        filtered (remove history empty-neighbors)
        pool (if (seq filtered) filtered empty-neighbors)]
    (when (seq pool)
      (let [sorted (sort-by #(grid/distance % objective) pool)]
        (try-move pos (first sorted))))))

(defn in-bounds?
  [pos]
  (let [[c r] pos
        game-map (sa/read-state :computer-map)]
    (and (>= c 0) (>= r 0)
         (< c (count game-map))
         (< r (count (first game-map))))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-26T22:31:29.529284-05:00", :module-hash "-1168517995", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 8, :hash "1600116539"} {:id "defn/on-same-continent?", :kind "defn", :line 10, :end-line 12, :hash "1156216714"} {:id "defn/merge-continents!", :kind "defn", :line 14, :end-line 16, :hash "-454703343"} {:id "defn/adjacent-to-sea?", :kind "defn", :line 18, :end-line 22, :hash "-934074242"} {:id "defn-/seed-coastal-registry", :kind "defn-", :line 24, :end-line 39, :hash "-1128333285"} {:id "defn/ensure-coastal-registry", :kind "defn", :line 41, :end-line 44, :hash "-675360738"} {:id "defn-/merge-neighbor-continents!", :kind "defn-", :line 46, :end-line 51, :hash "908131821"} {:id "defn-/local-coastal-cells", :kind "defn-", :line 53, :end-line 62, :hash "-1307796171"} {:id "defn-/update-coastal-registry!", :kind "defn-", :line 64, :end-line 69, :hash "-1830690924"} {:id "defn/register-coastal-cells", :kind "defn", :line 71, :end-line 79, :hash "-220333648"} {:id "defn/sovereign-passable?", :kind "defn", :line 81, :end-line 89, :hash "-2145061485"} {:id "defn/get-passable-neighbors", :kind "defn", :line 91, :end-line 96, :hash "1908859268"} {:id "defn/get-empty-passable-neighbors", :kind "defn", :line 98, :end-line 104, :hash "1363261546"} {:id "defn/find-nearest-unclaimed", :kind "defn", :line 106, :end-line 110, :hash "891914422"} {:id "defn-/update-move-history", :kind "defn-", :line 112, :end-line 118, :hash "1842406334"} {:id "defn/try-move", :kind "defn", :line 120, :end-line 130, :hash "1054696794"} {:id "defn-/sovereignty-passability-fn", :kind "defn-", :line 132, :end-line 135, :hash "-2003031201"} {:id "defn/move-toward-objective", :kind "defn", :line 137, :end-line 149, :hash "2011508017"} {:id "defn/local-step-toward-objective", :kind "defn", :line 151, :end-line 160, :hash "-810494014"} {:id "defn/in-bounds?", :kind "defn", :line 162, :end-line 168, :hash "992981382"}]}
;; clj-mutate-manifest-end
