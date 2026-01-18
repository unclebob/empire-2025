(ns empire.units.patrol-boat)

;; Configuration
(def speed 4)
(def cost 15)
(def hits 1)
(def strength 1)
(def display-char "P")
(def visibility-radius 1)

(defn initial-state
  "Returns initial state fields for a new patrol boat."
  []
  {})

(defn can-move-to?
  "Patrol boats can only move on sea."
  [cell]
  (and cell
       (= (:type cell) :sea)))

(defn needs-attention?
  "Patrol boats need attention when awake."
  [unit]
  (= (:mode unit) :awake))
