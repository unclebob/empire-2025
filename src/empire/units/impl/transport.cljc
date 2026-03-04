;; mutation-tested: 2026-02-25
(ns empire.units.impl.transport
  (:require [empire.units.config :as units-config]
            [empire.units.transport :as transport]))

(defmethod transport/initial-state :default
  []
  {:army-count 0
   :awake-armies 0
   :been-to-sea true})

(defmethod transport/can-move-to? :default
  [cell]
  (and cell
       (= (:type cell) :sea)))

(defmethod transport/needs-attention? :default
  [unit]
  (or (= (:mode unit) :awake)
      (pos? (:awake-armies unit 0))))

(defmethod transport/full? :default
  [unit]
  (>= (:army-count unit 0) units-config/transport-capacity))

(defmethod transport/has-armies? :default
  [unit]
  (pos? (:army-count unit 0)))

(defmethod transport/has-awake-armies? :default
  [unit]
  (pos? (:awake-armies unit 0)))

(defmethod transport/add-army :default
  [unit]
  (update unit :army-count (fnil inc 0)))

(defmethod transport/remove-army :default
  [unit]
  (update unit :army-count (fnil dec 0)))

(defmethod transport/wake-armies :default
  [unit]
  (assoc unit :awake-armies (:army-count unit 0)))

(defmethod transport/sleep-armies :default
  [unit]
  (assoc unit :awake-armies 0))

(defmethod transport/remove-awake-army :default
  [unit]
  (-> unit
      (update :army-count (fnil dec 0))
      (update :awake-armies (fnil dec 0))))

(defn load-methods!
  []
  true)
