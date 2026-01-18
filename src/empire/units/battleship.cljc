(ns empire.units.battleship)

;; Configuration
(def speed 2)
(def cost 40)
(def hits 10)
(def strength 2)
(def display-char "B")
(def visibility-radius 1)

(defn initial-state
  "Returns initial state fields for a new battleship."
  []
  {})

(defn can-move-to?
  "Battleships can only move on sea."
  [cell]
  (and cell
       (= (:type cell) :sea)))

(defn needs-attention?
  "Battleships need attention when awake."
  [unit]
  (= (:mode unit) :awake))
