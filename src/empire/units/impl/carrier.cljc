;; mutation-tested: 2026-02-25
(ns empire.units.impl.carrier
  (:require [empire.units.config :as units-config]
            [empire.units.carrier :as carrier]))

(defmethod carrier/initial-state :default
  []
  {:fighter-count 0
   :awake-fighters 0})

(defmethod carrier/can-move-to? :default
  [cell]
  (and cell
       (= (:type cell) :sea)))

(defmethod carrier/needs-attention? :default
  [unit]
  (or (= (:mode unit) :awake)
      (pos? (:awake-fighters unit 0))))

(defmethod carrier/full? :default
  [unit]
  (>= (:fighter-count unit 0) units-config/carrier-capacity))

(defmethod carrier/has-fighters? :default
  [unit]
  (pos? (:fighter-count unit 0)))

(defmethod carrier/has-awake-fighters? :default
  [unit]
  (pos? (:awake-fighters unit 0)))

(defmethod carrier/add-fighter :default
  [unit]
  (update unit :fighter-count (fnil inc 0)))

(defmethod carrier/remove-fighter :default
  [unit]
  (update unit :fighter-count (fnil dec 0)))

(defmethod carrier/wake-fighters :default
  [unit]
  (assoc unit :awake-fighters (:fighter-count unit 0)))

(defmethod carrier/sleep-fighters :default
  [unit]
  (assoc unit :awake-fighters 0))

(defmethod carrier/remove-awake-fighter :default
  [unit]
  (-> unit
      (update :fighter-count (fnil dec 0))
      (update :awake-fighters (fnil dec 0))))

(defn load-methods!
  []
  true)
