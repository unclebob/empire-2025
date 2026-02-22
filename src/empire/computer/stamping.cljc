(ns empire.computer.stamping
  (:require [empire.atoms :as atoms]))

(defn- apply-computer-satellite-direction
  "Assigns a random direction to computer satellites."
  [unit]
  (if (and (= :satellite (:type unit)) (= :computer (:owner unit)))
    (assoc unit :direction (rand-nth [[-1 -1] [-1 0] [-1 1] [0 -1] [0 1] [1 -1] [1 0] [1 1]]))
    unit))

(defn- apply-computer-transport-fields
  "Stamps transport-mission, stuck-since-round, and transport-id on computer transports."
  [unit]
  (if (and (= :transport (:type unit)) (= :computer (:owner unit)))
    (let [id @atoms/next-transport-id]
      (swap! atoms/next-transport-id inc)
      (assoc unit :transport-mission :idle
                  :stuck-since-round @atoms/round-number
                  :transport-id id
                  :army-count 0))
    unit))

(defn- apply-country-id
  "Assigns city's country-id to computer armies, transports, and fighters."
  [unit cell]
  (if (and (#{:army :transport :fighter} (:type unit)) (:country-id cell))
    (assoc unit :country-id (:country-id cell))
    unit))

(defn- apply-patrol-fields
  "Stamps patrol boat fields on computer patrol boats spawned from a country city.
   Tracks patrol-number per country (1st coastline, 2nd+ heading-based)."
  [unit cell]
  (if (and (= :patrol-boat (:type unit))
           (= :computer (:city-status cell))
           (:country-id cell))
    (let [country-id (:country-id cell)
          produced (get @atoms/patrol-boats-produced country-id 0)
          patrol-num (inc produced)]
      (swap! atoms/patrol-boats-produced update country-id (fnil inc 0))
      (assoc unit :patrol-country-id country-id
                  :patrol-direction :clockwise
                  :patrol-mode :homing
                  :patrol-number patrol-num))
    unit))

(defn- apply-carrier-fields
  "Stamps carrier fields on computer carriers: mode, id, group slots."
  [unit]
  (if (and (= :carrier (:type unit)) (= :computer (:owner unit)))
    (let [id @atoms/next-carrier-id]
      (swap! atoms/next-carrier-id inc)
      (assoc unit :carrier-mode :positioning
                  :carrier-id id
                  :group-battleship-id nil
                  :group-submarine-ids []))
    unit))

(defn- apply-escort-fields
  "Stamps escort fields on computer battleships and submarines."
  [unit]
  (if (and (#{:battleship :submarine} (:type unit)) (= :computer (:owner unit)))
    (let [id @atoms/next-escort-id]
      (swap! atoms/next-escort-id inc)
      (assoc unit :escort-id id :escort-mode :seeking))
    unit))

(defn- apply-destroyer-fields
  "Stamps destroyer-id and escort-mode on computer destroyers."
  [unit]
  (if (and (= :destroyer (:type unit)) (= :computer (:owner unit)))
    (let [id @atoms/next-destroyer-id]
      (swap! atoms/next-destroyer-id inc)
      (assoc unit :destroyer-id id :escort-mode :seeking))
    unit))

(defn stamp-computer-fields
  "Applies all computer-specific fields to a unit.
   Called by stamp-unit-fields in player/production after shared attributes."
  [unit cell]
  (-> unit
      (apply-computer-satellite-direction)
      (apply-computer-transport-fields)
      (apply-destroyer-fields)
      (apply-carrier-fields)
      (apply-escort-fields)
      (apply-country-id cell)
      (apply-patrol-fields cell)))

(defn apply-coast-walk-fields
  "Stamps coast-walk mode on first 2 computer armies per country.
   Alternates clockwise/counter-clockwise."
  [unit item cell coords]
  (if (and (= item :army)
           (= (:city-status cell) :computer)
           (:country-id cell)
           (< (get @atoms/coast-walkers-produced (:country-id cell) 0) 2)
           (not ((requiring-resolve 'empire.computer.production/country-coastal-cells-explored?)
                 (:country-id cell))))
    (let [country-id (:country-id cell)
          produced (get @atoms/coast-walkers-produced country-id 0)
          direction (if (even? produced) :clockwise :counter-clockwise)]
      (swap! atoms/coast-walkers-produced update country-id (fnil inc 0))
      (assoc unit :mode :coast-walk
                  :coast-direction direction
                  :coast-start coords
                  :coast-visited [coords]))
    unit))

(defn apply-random-explore-fields
  "Stamps random-explore mode on 1/3 of non-coast-walk computer armies."
  [unit item cell]
  (if (and (= item :army)
           (= :computer (:owner unit))
           (not= :coast-walk (:mode unit))
           (:country-id cell)
           (< (rand) 1/3))
    (assoc unit :mode :random-explore
                :random-explore-direction
                (rand-nth [[-1 -1] [-1 0] [-1 1] [0 -1] [0 1] [1 -1] [1 0] [1 1]]))
    unit))
