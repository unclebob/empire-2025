(ns empire.units.destroyer
  (:require [empire.units.dispatcher :as dispatcher]))

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

;; Register with dispatcher
(defmethod dispatcher/speed :destroyer [_] speed)
(defmethod dispatcher/cost :destroyer [_] cost)
(defmethod dispatcher/hits :destroyer [_] hits)
(defmethod dispatcher/strength :destroyer [_] strength)
(defmethod dispatcher/display-char :destroyer [_] display-char)
(defmethod dispatcher/visibility-radius :destroyer [_] visibility-radius)
(defmethod dispatcher/initial-state :destroyer [_] (initial-state))
(defmethod dispatcher/can-move-to? :destroyer [_ cell] (can-move-to? cell))
(defmethod dispatcher/needs-attention? :destroyer [unit] (needs-attention? unit))
