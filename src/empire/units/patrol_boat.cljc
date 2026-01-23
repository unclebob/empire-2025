(ns empire.units.patrol-boat
  (:require [empire.units.dispatcher :as dispatcher]))

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

;; Register with dispatcher
(defmethod dispatcher/speed :patrol-boat [_] speed)
(defmethod dispatcher/cost :patrol-boat [_] cost)
(defmethod dispatcher/hits :patrol-boat [_] hits)
(defmethod dispatcher/strength :patrol-boat [_] strength)
(defmethod dispatcher/display-char :patrol-boat [_] display-char)
(defmethod dispatcher/visibility-radius :patrol-boat [_] visibility-radius)
(defmethod dispatcher/initial-state :patrol-boat [_] (initial-state))
(defmethod dispatcher/can-move-to? :patrol-boat [_ cell] (can-move-to? cell))
(defmethod dispatcher/needs-attention? :patrol-boat [unit] (needs-attention? unit))
