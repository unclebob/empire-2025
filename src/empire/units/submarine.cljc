(ns empire.units.submarine)

;; Configuration
(def speed 2)
(def cost 20)
(def hits 2)
(def strength 3)
(def display-char "S")
(def visibility-radius 1)

(defn initial-state
  "Returns initial state fields for a new submarine."
  []
  {})

(defn can-move-to?
  "Submarines can only move on sea."
  [cell]
  (and cell
       (= (:type cell) :sea)))

(defn needs-attention?
  "Submarines need attention when awake."
  [unit]
  (= (:mode unit) :awake))
