(ns empire.config.units.transport
  (:require [empire.config.units.config :as units-config]))

(defn initial-state
  []
  {:army-count 0
   :awake-armies 0
   :been-to-sea true})

(defn can-move-to?
  [cell]
  (and cell
       (= (:type cell) :sea)))

(defn needs-attention?
  [unit]
  (or (= (:mode unit) :awake)
      (pos? (:awake-armies unit 0))))

(defn full?
  [unit]
  (>= (:army-count unit 0) units-config/transport-capacity))

(defn has-armies?
  [unit]
  (pos? (:army-count unit 0)))

(defn has-awake-armies?
  [unit]
  (pos? (:awake-armies unit 0)))

(defn add-army
  [unit]
  (update unit :army-count (fnil inc 0)))

(defn remove-army
  [unit]
  (update unit :army-count (fnil dec 0)))

(defn wake-armies
  [unit]
  (assoc unit :awake-armies (:army-count unit 0)))

(defn sleep-armies
  [unit]
  (assoc unit :awake-armies 0))

(defn remove-awake-army
  [unit]
  (-> unit
      (update :army-count (fnil dec 0))
      (update :awake-armies (fnil dec 0))))
