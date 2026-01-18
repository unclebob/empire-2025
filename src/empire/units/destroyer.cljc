(ns empire.units.destroyer)

;; Configuration
(def speed 2)
(def cost 20)
(def hits 3)
(def strength 1)
(def display-char "D")
(def visibility-radius 1)

(defn initial-state
  "Returns initial state fields for a new destroyer."
  []
  {})

(defn can-move-to?
  "Destroyers can only move on sea."
  [cell]
  (and cell
       (= (:type cell) :sea)))

(defn needs-attention?
  "Destroyers need attention when awake."
  [unit]
  (= (:mode unit) :awake))
