;; mutation-tested: 2026-02-25
(ns empire.units.army)

(defmulti initial-state
  (fn [& _]
    :default))

(defmulti can-move-to?
  (fn [& _]
    :default))

(defmulti needs-attention?
  (fn [& _]
    :default))

(defmethod initial-state :default
  []
  {})

(defmethod can-move-to? :default
  [cell]
  (and cell
       (or (= (:type cell) :land)
           (and (= (:type cell) :city)
                (not= (:city-status cell) :player)))))

(defmethod needs-attention? :default
  [unit]
  (= (:mode unit) :awake))
