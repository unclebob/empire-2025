;; mutation-tested: 2026-02-25
(ns empire.units.impl.army
  (:require [empire.units.army :as army]))

(defmethod army/initial-state :default
  []
  {})

(defmethod army/can-move-to? :default
  [cell]
  (and cell
       (or (= (:type cell) :land)
           (and (= (:type cell) :city)
                (not= (:city-status cell) :player)))))

(defmethod army/needs-attention? :default
  [unit]
  (= (:mode unit) :awake))

(defn load-methods!
  []
  true)
