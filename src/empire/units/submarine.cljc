(ns empire.units.submarine
  (:require [empire.units.dispatcher :as dispatcher]))

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

;; Register with dispatcher
(defmethod dispatcher/speed :submarine [_] speed)
(defmethod dispatcher/cost :submarine [_] cost)
(defmethod dispatcher/hits :submarine [_] hits)
(defmethod dispatcher/strength :submarine [_] strength)
(defmethod dispatcher/display-char :submarine [_] display-char)
(defmethod dispatcher/visibility-radius :submarine [_] visibility-radius)
(defmethod dispatcher/initial-state :submarine [_] (initial-state))
(defmethod dispatcher/can-move-to? :submarine [_ cell] (can-move-to? cell))
(defmethod dispatcher/needs-attention? :submarine [unit] (needs-attention? unit))
