(ns empire.config.units.fighter
  (:require [empire.config.units.config :as units-config]))

(defn initial-state
  []
  {:fuel units-config/fighter-fuel})

(defn can-move-to?
  [_cell]
  true)

(defn needs-attention?
  [unit]
  (= (:mode unit) :awake))

(defn consume-fuel
  [unit]
  (let [current-fuel (:fuel unit units-config/fighter-fuel)
        new-fuel (dec current-fuel)]
    (if (<= new-fuel -1)
      nil
      (assoc unit :fuel new-fuel))))

(defn refuel
  [unit]
  (assoc unit :fuel units-config/fighter-fuel))

(defn bingo?
  [unit]
  (<= (:fuel unit units-config/fighter-fuel) units-config/fighter-bingo-threshold))

(defn out-of-fuel?
  [unit]
  (<= (:fuel unit units-config/fighter-fuel) 1))

(defn can-land-at-city?
  [cell]
  (and (= (:type cell) :city)
       (= (:city-status cell) :player)))

(defn can-land-on-carrier?
  [cell owner carrier-capacity]
  (let [contents (:contents cell)]
    (and contents
         (= (:type contents) :carrier)
         (= (:owner contents) owner)
         (< (:fighter-count contents 0) carrier-capacity))))
