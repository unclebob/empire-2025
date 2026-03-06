(ns empire.config.units.carrier
  (:require [empire.config.units.config :as units-config]))

(defn initial-state
  []
  {:fighter-count 0
   :awake-fighters 0})

(defn can-move-to?
  [cell]
  (and cell
       (= (:type cell) :sea)))

(defn needs-attention?
  [unit]
  (or (= (:mode unit) :awake)
      (pos? (:awake-fighters unit 0))))

(defn full?
  [unit]
  (>= (:fighter-count unit 0) units-config/carrier-capacity))

(defn has-fighters?
  [unit]
  (pos? (:fighter-count unit 0)))

(defn has-awake-fighters?
  [unit]
  (pos? (:awake-fighters unit 0)))

(defn add-fighter
  [unit]
  (update unit :fighter-count (fnil inc 0)))

(defn remove-fighter
  [unit]
  (update unit :fighter-count (fnil dec 0)))

(defn wake-fighters
  [unit]
  (assoc unit :awake-fighters (:fighter-count unit 0)))

(defn sleep-fighters
  [unit]
  (assoc unit :awake-fighters 0))

(defn remove-awake-fighter
  [unit]
  (-> unit
      (update :fighter-count (fnil dec 0))
      (update :awake-fighters (fnil dec 0))))
