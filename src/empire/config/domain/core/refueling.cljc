;; mutation-tested: no
(ns empire.config.domain.core.refueling)

(defn computer-city-cell?
  [cell]
  (and (= :city (:type cell))
       (= :computer (:city-status cell))))

(defn computer-carrier-cell?
  [cell]
  (and (= :carrier (get-in cell [:contents :type]))
       (= :computer (get-in cell [:contents :owner]))))

(defn scan-refueling-positions
  "Scans a map and returns {:cities #{[x y]} :carriers #{[x y]}}."
  [game-map]
  (let [cities (transient #{})
        carriers (transient #{})]
    (doseq [i (range (count game-map))
            j (range (count (first game-map)))
            :let [cell (get-in game-map [i j])]]
      (when (computer-city-cell? cell)
        (conj! cities [i j]))
      (when (computer-carrier-cell? cell)
        (conj! carriers [i j])))
    {:cities (persistent! cities)
     :carriers (persistent! carriers)}))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T00:29:22.432697-05:00", :module-hash "32320224", :forms [{:id "form/0/ns", :kind "ns", :line 2, :end-line 2, :hash "-1736060099"} {:id "defn/computer-city-cell?", :kind "defn", :line 4, :end-line 7, :hash "263806765"} {:id "defn/computer-carrier-cell?", :kind "defn", :line 9, :end-line 12, :hash "1245641492"} {:id "defn/scan-refueling-positions", :kind "defn", :line 14, :end-line 27, :hash "-780216883"}]}
;; clj-mutate-manifest-end
