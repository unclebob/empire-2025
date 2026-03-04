;; mutation-tested: 2026-02-25
(ns empire.units.impl.fighter
  (:require [empire.units.config :as units-config]
            [empire.units.fighter :as fighter]))

(defmethod fighter/initial-state :default
  []
  {:fuel units-config/fighter-fuel})

(defmethod fighter/can-move-to? :default
  [_cell]
  true)

(defmethod fighter/needs-attention? :default
  [unit]
  (= (:mode unit) :awake))

(defmethod fighter/consume-fuel :default
  [unit]
  (let [current-fuel (:fuel unit units-config/fighter-fuel)
        new-fuel (dec current-fuel)]
    (if (<= new-fuel -1)
      nil
      (assoc unit :fuel new-fuel))))

(defmethod fighter/refuel :default
  [unit]
  (assoc unit :fuel units-config/fighter-fuel))

(defmethod fighter/bingo? :default
  [unit]
  (<= (:fuel unit units-config/fighter-fuel) units-config/fighter-bingo-threshold))

(defmethod fighter/out-of-fuel? :default
  [unit]
  (<= (:fuel unit units-config/fighter-fuel) 1))

(defmethod fighter/can-land-at-city? :default
  [cell]
  (and (= (:type cell) :city)
       (= (:city-status cell) :player)))

(defmethod fighter/can-land-on-carrier? :default
  [cell owner carrier-capacity]
  (let [contents (:contents cell)]
    (and contents
         (= (:type contents) :carrier)
         (= (:owner contents) owner)
         (< (:fighter-count contents 0) carrier-capacity))))

(defn load-methods!
  []
  true)
