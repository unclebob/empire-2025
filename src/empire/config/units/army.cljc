(ns empire.config.units.army)

(defn initial-state
  []
  {})

(defn can-move-to?
  [cell]
  (and cell
       (or (= (:type cell) :land)
           (and (= (:type cell) :city)
                (not= (:city-status cell) :player)))))

(defn needs-attention?
  [unit]
  (= (:mode unit) :awake))
