(ns empire.computer.ship
  "Computer ship module - facade delegating to sub-modules."
  (:require [empire.atoms :as atoms]
            [empire.computer.core :as core]
            [empire.computer.ship-carrier :as carrier]
            [empire.computer.ship-carrier-group :as carrier-group]
            [empire.computer.ship-core :as ship-core]
            [empire.computer.ship-escort :as escort]
            [empire.computer.ship-patrol :as patrol]
            [empire.movement.visibility :as visibility]))

;; --- Core utility re-exports ---

(def get-passable-sea-neighbors ship-core/get-passable-sea-neighbors)
(def attack-enemy ship-core/attack-enemy)
(def move-toward ship-core/move-toward)

;; --- Patrol re-exports ---

(def patrol-crawl-step patrol/patrol-crawl-step)
(def patrol-explore-step patrol/patrol-explore-step)

;; --- Carrier re-exports ---

(def compute-distant-city-pairs carrier/compute-distant-city-pairs)
(def update-distant-city-pairs! carrier/update-distant-city-pairs!)
(def find-reserved-pairs carrier/find-reserved-pairs)
(def find-unreserved-pair carrier/find-unreserved-pair)
(def find-position-between-cities carrier/find-position-between-cities)
(def find-refueling-sites carrier/find-refueling-sites)
(def find-carrier-position carrier/find-carrier-position)
(def orbit-ring carrier-group/orbit-ring)

;; --- Dispatch helpers ---

(defn- try-dock [pos unit]
  (when-let [city (ship-core/find-adjacent-dock-city pos unit)]
    (ship-core/dock-computer-ship pos city)))

(defn- try-retreat [pos unit]
  (when-let [rp (ship-core/retreat-if-damaged pos unit)]
    (core/move-unit-to pos rp)
    (visibility/update-cell-visibility pos :computer)
    (visibility/update-cell-visibility rp :computer)))

(defn- try-attack-adjacent [pos]
  (when-let [ep (ship-core/find-adjacent-enemy-ship pos)]
    (ship-core/attack-enemy pos ep)))

(defn- try-escort [pos ship-type unit]
  (when (:escort-mode unit)
    (cond
      (= :destroyer ship-type)
      (or (escort/process-escort-destroyer pos)
          (ship-core/explore-sea pos ship-type))

      (#{:battleship :submarine} ship-type)
      (or (carrier-group/process-carrier-group-escort pos ship-type)
          (ship-core/explore-sea pos ship-type)))))

(defn- try-escort-transport [pos ship-type]
  (when (= :destroyer ship-type)
    (when-let [transport-pos (ship-core/find-nearest-transport pos)]
      (if (> (core/distance pos transport-pos) 2)
        (ship-core/move-toward pos transport-pos)
        (ship-core/explore-sea pos ship-type)))))

(defn- try-hunt-player-ship [pos]
  (when-let [sighting (ship-core/find-player-ship-sighting pos)]
    (ship-core/move-toward pos sighting)))

(defn- dispatch-ship-action [pos ship-type unit]
  (cond
    (= :patrol-boat ship-type)
    (patrol/process-patrol-boat pos)

    (and (= :carrier ship-type) (:carrier-mode unit))
    (carrier/process-carrier pos)

    :else
    (or (try-dock pos unit)
        (try-retreat pos unit)
        (try-attack-adjacent pos)
        (try-escort pos ship-type unit)
        (try-escort-transport pos ship-type)
        (try-hunt-player-ship pos)
        (ship-core/explore-sea pos ship-type))))

(defn process-ship
  "Processes a computer ship using VMS Empire style logic.
   Returns nil after processing."
  [pos ship-type]
  (let [unit (:contents (get-in @atoms/game-map pos))]
    (when (and unit
               (= :computer (:owner unit))
               (= ship-type (:type unit)))
      (dispatch-ship-action pos ship-type unit)))
  nil)
