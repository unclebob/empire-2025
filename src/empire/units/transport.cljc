;; mutation-tested: 2026-02-25
(ns empire.units.transport
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

(defmulti has-armies?
  (fn [& _]
    :default))

(defmulti has-awake-armies?
  (fn [& _]
    :default))

(defmulti add-army
  (fn [& _]
    :default))

(defmulti remove-army
  (fn [& _]
    :default))

(defmulti wake-armies
  (fn [& _]
    :default))

(defmulti sleep-armies
  (fn [& _]
    :default))

(defmulti remove-awake-army
  (fn [& _]
    :default))

(defmethod initial-state :default
  []
  {:army-count 0
   :awake-armies 0
   :been-to-sea true})

(defmethod can-move-to? :default
  [cell]
  (and cell
       (= (:type cell) :sea)))

(defmethod needs-attention? :default
  [unit]
  (or (= (:mode unit) :awake)
      (pos? (:awake-armies unit 0))))

(defmethod full? :default
  [unit]
  (>= (:army-count unit 0) units-config/transport-capacity))

(defmethod has-armies? :default
  [unit]
  (pos? (:army-count unit 0)))

(defmethod has-awake-armies? :default
  [unit]
  (pos? (:awake-armies unit 0)))

(defmethod add-army :default
  [unit]
  (update unit :army-count (fnil inc 0)))

(defmethod remove-army :default
  [unit]
  (update unit :army-count (fnil dec 0)))

(defmethod wake-armies :default
  [unit]
  (assoc unit :awake-armies (:army-count unit 0)))

(defmethod sleep-armies :default
  [unit]
  (assoc unit :awake-armies 0))

(defmethod remove-awake-army :default
  [unit]
  (-> unit
      (update :army-count (fnil dec 0))
      (update :awake-armies (fnil dec 0))))
