(ns empire.units.fighter)

;; Configuration
(def speed 8)
(def cost 10)
(def hits 1)
(def strength 1)
(def display-char "F")
(def fuel 32)
(def visibility-radius 1)
(def bingo-threshold (quot fuel 4))

(defn initial-state
  "Returns initial state fields for a new fighter."
  []
  {:fuel fuel})

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
