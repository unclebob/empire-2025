;; mutation-tested: no
(ns empire.domain.core.impl.refueling
  (:require [empire.domain.core.refueling :as refueling]))

(defmethod refueling/computer-city-cell? :default
  [cell]
  (and (= :city (:type cell))
       (= :computer (:city-status cell))))

(defmethod refueling/computer-carrier-cell? :default
  [cell]
  (and (= :carrier (get-in cell [:contents :type]))
       (= :computer (get-in cell [:contents :owner]))))

(defmethod refueling/scan-refueling-positions :default
  [game-map]
  (let [cities (transient #{})
        carriers (transient #{})]
    (doseq [i (range (count game-map))
            j (range (count (first game-map)))
            :let [cell (get-in game-map [i j])]]
      (when (refueling/computer-city-cell? cell)
        (conj! cities [i j]))
      (when (refueling/computer-carrier-cell? cell)
        (conj! carriers [i j])))
    {:cities (persistent! cities)
     :carriers (persistent! carriers)}))
