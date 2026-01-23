(ns empire.units.battleship
  (:require [empire.units.dispatcher :as dispatcher]))

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

;; Register with dispatcher
(defmethod dispatcher/speed :battleship [_] speed)
(defmethod dispatcher/cost :battleship [_] cost)
(defmethod dispatcher/hits :battleship [_] hits)
(defmethod dispatcher/strength :battleship [_] strength)
(defmethod dispatcher/display-char :battleship [_] display-char)
(defmethod dispatcher/visibility-radius :battleship [_] visibility-radius)
(defmethod dispatcher/initial-state :battleship [_] (initial-state))
(defmethod dispatcher/can-move-to? :battleship [_ cell] (can-move-to? cell))
(defmethod dispatcher/needs-attention? :battleship [unit] (needs-attention? unit))
