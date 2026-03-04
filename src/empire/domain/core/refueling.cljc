;; mutation-tested: no
(ns empire.domain.core.refueling)

(defmulti computer-city-cell? (fn [& _] :default))

(defmulti computer-carrier-cell? (fn [& _] :default))

(defmulti scan-refueling-positions
  "Scans a map and returns {:cities #{[x y]} :carriers #{[x y]}}."
  (fn [& _] :default))

(defmethod computer-city-cell? :default
  [cell]
  (and (= :city (:type cell))
       (= :computer (:city-status cell))))

(defmethod computer-carrier-cell? :default
  [cell]
  (and (= :carrier (get-in cell [:contents :type]))
       (= :computer (get-in cell [:contents :owner]))))

(defmethod scan-refueling-positions :default
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
