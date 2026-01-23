(ns empire.units.carrier
  (:require [empire.units.dispatcher :as dispatcher]))

;; Configuration
(def speed 2)
(def cost 30)
(def hits 8)
(def strength 1)
(def display-char "C")
(def capacity 8)
(def visibility-radius 1)

(defn initial-state
  "Returns initial state fields for a new carrier."
  []
  {:fighter-count 0
   :awake-fighters 0})

(defn can-move-to?
  "Carriers can only move on sea."
  [cell]
  (and cell
       (= (:type cell) :sea)))

(defn needs-attention?
  "Carriers need attention when awake or when they have awake fighters aboard."
  [unit]
  (or (= (:mode unit) :awake)
      (pos? (:awake-fighters unit 0))))

(defn full?
  "Returns true if carrier is at fighter capacity."
  [unit]
  (>= (:fighter-count unit 0) capacity))

(defn has-fighters?
  "Returns true if carrier has any fighters aboard."
  [unit]
  (pos? (:fighter-count unit 0)))

(defn has-awake-fighters?
  "Returns true if carrier has awake fighters aboard."
  [unit]
  (pos? (:awake-fighters unit 0)))

(defn add-fighter
  "Adds a fighter to the carrier. Returns updated carrier."
  [unit]
  (update unit :fighter-count (fnil inc 0)))

(defn remove-fighter
  "Removes a fighter from the carrier. Returns updated carrier."
  [unit]
  (update unit :fighter-count (fnil dec 0)))

(defn wake-fighters
  "Wakes all sleeping fighters aboard the carrier."
  [unit]
  (assoc unit :awake-fighters (:fighter-count unit 0)))

(defn sleep-fighters
  "Puts all fighters aboard back to sleep."
  [unit]
  (assoc unit :awake-fighters 0))

(defn remove-awake-fighter
  "Removes one awake fighter from the carrier. Returns updated carrier."
  [unit]
  (-> unit
      (update :fighter-count (fnil dec 0))
      (update :awake-fighters (fnil dec 0))))

;; Register with dispatcher
(defmethod dispatcher/speed :carrier [_] speed)
(defmethod dispatcher/cost :carrier [_] cost)
(defmethod dispatcher/hits :carrier [_] hits)
(defmethod dispatcher/strength :carrier [_] strength)
(defmethod dispatcher/display-char :carrier [_] display-char)
(defmethod dispatcher/visibility-radius :carrier [_] visibility-radius)
(defmethod dispatcher/initial-state :carrier [_] (initial-state))
(defmethod dispatcher/can-move-to? :carrier [_ cell] (can-move-to? cell))
(defmethod dispatcher/needs-attention? :carrier [unit] (needs-attention? unit))
