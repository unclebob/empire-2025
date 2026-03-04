;; mutation-tested: 2026-02-25
(ns empire.units.fighter
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

(defmulti consume-fuel
  (fn [& _]
    :default))

(defmulti refuel
  (fn [& _]
    :default))

(defmulti bingo?
  (fn [& _]
    :default))

(defmulti out-of-fuel?
  (fn [& _]
    :default))

(defmulti can-land-at-city?
  (fn [& _]
    :default))

(defmulti can-land-on-carrier?
  (fn [& _]
    :default))

(defmethod initial-state :default
  []
  {:fuel units-config/fighter-fuel})

(defmethod can-move-to? :default
  [_cell]
  true)

(defmethod needs-attention? :default
  [unit]
  (= (:mode unit) :awake))

(defmethod consume-fuel :default
  [unit]
  (let [current-fuel (:fuel unit units-config/fighter-fuel)
        new-fuel (dec current-fuel)]
    (if (<= new-fuel -1)
      nil
      (assoc unit :fuel new-fuel))))

(defmethod refuel :default
  [unit]
  (assoc unit :fuel units-config/fighter-fuel))

(defmethod bingo? :default
  [unit]
  (<= (:fuel unit units-config/fighter-fuel) units-config/fighter-bingo-threshold))

(defmethod out-of-fuel? :default
  [unit]
  (<= (:fuel unit units-config/fighter-fuel) 1))

(defmethod can-land-at-city? :default
  [cell]
  (and (= (:type cell) :city)
       (= (:city-status cell) :player)))

(defmethod can-land-on-carrier? :default
  [cell owner carrier-capacity]
  (let [contents (:contents cell)]
    (and contents
         (= (:type contents) :carrier)
         (= (:owner contents) owner)
         (< (:fighter-count contents 0) carrier-capacity))))
