(ns empire.computer.army.movement
  "Shared movement and passability helpers for computer armies."
  (:require [empire.state.api :as sa]
            [empire.computer.core :as core]
            [empire.game-mechanics.debug.logging :as debug]
            [empire.computer.movement :as computer-movement]))

(defn on-same-continent?
  [country-a country-b]
  (sa/on-same-continent? country-a country-b))

(defn merge-continents!
  [stamp-id existing-cid]
  (sa/merge-continents! stamp-id existing-cid))

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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T11:57:22.42638-05:00", :module-hash "280881474", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 6, :hash "1820433582"} {:id "defn/on-same-continent?", :kind "defn", :line 8, :end-line 10, :hash "1156216714"} {:id "defn/merge-continents!", :kind "defn", :line 12, :end-line 14, :hash "-454703343"} {:id "defn/adjacent-to-sea?", :kind "defn", :line 16, :end-line 20, :hash "-142666661"} {:id "defn-/seed-coastal-registry", :kind "defn-", :line 22, :end-line 37, :hash "1436125686"} {:id "defn/ensure-coastal-registry", :kind "defn", :line 39, :end-line 42, :hash "-675360738"} {:id "defn-/merge-neighbor-continents!", :kind "defn-", :line 44, :end-line 49, :hash "908131821"} {:id "defn-/local-coastal-cells", :kind "defn-", :line 51, :end-line 60, :hash "-1307796171"} {:id "defn-/update-coastal-registry!", :kind "defn-", :line 62, :end-line 67, :hash "-1830690924"} {:id "defn/register-coastal-cells", :kind "defn", :line 69, :end-line 77, :hash "-1929613630"} {:id "defn/sovereign-passable?", :kind "defn", :line 79, :end-line 87, :hash "-2145061485"} {:id "defn/get-passable-neighbors", :kind "defn", :line 89, :end-line 94, :hash "451113115"} {:id "defn/get-empty-passable-neighbors", :kind "defn", :line 96, :end-line 102, :hash "103104840"} {:id "defn/find-nearest-unclaimed", :kind "defn", :line 104, :end-line 108, :hash "-1699464324"} {:id "defn-/update-move-history", :kind "defn-", :line 110, :end-line 116, :hash "1842406334"} {:id "defn/try-move", :kind "defn", :line 118, :end-line 128, :hash "1946626754"} {:id "defn-/sovereignty-passability-fn", :kind "defn-", :line 130, :end-line 133, :hash "-2003031201"} {:id "defn/move-toward-objective", :kind "defn", :line 135, :end-line 147, :hash "1322877056"} {:id "defn/in-bounds?", :kind "defn", :line 149, :end-line 155, :hash "-615119798"}]}
;; clj-mutate-manifest-end
