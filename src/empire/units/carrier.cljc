;; mutation-tested: 2026-02-25
(ns empire.units.carrier
  (:require [empire.units.config :as units-config]))

(defmulti initial-state
  (fn [& _]
    :default))

(defmulti can-move-to?
  (fn [& _]
    :default))

(defmulti needs-attention?
  (fn [& _]
    :default))

(defmulti full?
  (fn [& _]
    :default))

(defmulti has-fighters?
  (fn [& _]
    :default))

(defmulti has-awake-fighters?
  (fn [& _]
    :default))

(defmulti add-fighter
  (fn [& _]
    :default))

(defmulti remove-fighter
  (fn [& _]
    :default))

(defmulti wake-fighters
  (fn [& _]
    :default))

(defmulti sleep-fighters
  (fn [& _]
    :default))

(defmulti remove-awake-fighter
  (fn [& _]
    :default))

(defmethod initial-state :default
  []
  {:fighter-count 0
   :awake-fighters 0})

(defmethod can-move-to? :default
  [cell]
  (and cell
       (= (:type cell) :sea)))

(defmethod needs-attention? :default
  [unit]
  (or (= (:mode unit) :awake)
      (pos? (:awake-fighters unit 0))))

(defmethod full? :default
  [unit]
  (>= (:fighter-count unit 0) units-config/carrier-capacity))

(defmethod has-fighters? :default
  [unit]
  (pos? (:fighter-count unit 0)))

(defmethod has-awake-fighters? :default
  [unit]
  (pos? (:awake-fighters unit 0)))

(defmethod add-fighter :default
  [unit]
  (update unit :fighter-count (fnil inc 0)))

(defmethod remove-fighter :default
  [unit]
  (update unit :fighter-count (fnil dec 0)))

(defmethod wake-fighters :default
  [unit]
  (assoc unit :awake-fighters (:fighter-count unit 0)))

(defmethod sleep-fighters :default
  [unit]
  (assoc unit :awake-fighters 0))

(defmethod remove-awake-fighter :default
  [unit]
  (-> unit
      (update :fighter-count (fnil dec 0))
      (update :awake-fighters (fnil dec 0))))
