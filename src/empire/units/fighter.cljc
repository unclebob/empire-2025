(ns empire.units.fighter
  (:require [empire.units.dispatcher :as dispatcher]))

;; Configuration constants
(def fuel 32)
(def bingo-threshold (quot fuel 4))

(defn can-move-to?
  "Fighters can fly anywhere (they're in the air)."
  [_cell]
  true)

(defn needs-attention?
  "Fighters need attention when awake."
  [unit]
  (= (:mode unit) :awake))

(defn consume-fuel
  "Decrements fighter fuel by 1. Returns nil if fighter runs out of fuel."
  [unit]
  (let [current-fuel (:fuel unit fuel)
        new-fuel (dec current-fuel)]
    (if (<= new-fuel -1)
      nil
      (assoc unit :fuel new-fuel))))

(defn refuel
  "Refuels fighter to full capacity."
  [unit]
  (assoc unit :fuel fuel))

(defn bingo?
  "Returns true if fighter fuel is at or below bingo threshold (1/4 of max fuel)."
  [unit]
  (<= (:fuel unit fuel) bingo-threshold))

(defn out-of-fuel?
  "Returns true if fighter has 1 or less fuel remaining."
  [unit]
  (<= (:fuel unit fuel) 1))

(defn can-land-at-city?
  "Returns true if fighter can land at the given cell (must be a player city)."
  [cell]
  (and (= (:type cell) :city)
       (= (:city-status cell) :player)))

(defn can-land-on-carrier?
  "Returns true if fighter can land on carrier in the given cell.
   Requires a friendly carrier with space available."
  [cell owner carrier-capacity]
  (let [contents (:contents cell)]
    (and contents
         (= (:type contents) :carrier)
         (= (:owner contents) owner)
         (< (:fighter-count contents 0) carrier-capacity))))

(dispatcher/defunit :fighter
  {:speed 8, :cost 10, :hits 1, :strength 1,
   :display-char "F", :visibility-radius 1}
  (fn [] {:fuel fuel})
  can-move-to?
  needs-attention?)
