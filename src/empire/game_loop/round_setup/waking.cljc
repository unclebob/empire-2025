(ns empire.game-loop.round-setup.waking
  (:require [empire.application.state-access :as sa]
            [empire.movement.services :as movement-services]
            [empire.containers.helpers :as uc]))

(defn- world-ref [world] (atom world))

(defn wake-airport-fighters
  "Wakes all fighters in player city airports at start of round.
   Fighters will be auto-launched if the city has a flight-path,
   otherwise they will demand attention."
  []
  (let [world (sa/current-world)]
    (doseq [i (range (count world))
            j (range (count (first world)))
            :let [cell (get-in world [i j])]
            :when (and (= (:type cell) :city)
                       (= (:city-status cell) :player)
                       (pos? (uc/get-count cell :fighter-count)))]
      (let [total (uc/get-count cell :fighter-count)]
        (sa/update-world! assoc-in [i j :awake-fighters] total)))))

(defn wake-carrier-fighters
  "Wakes all fighters on player carriers at start of round.
   Fighters will be auto-launched if the carrier has a flight-path,
   otherwise they will demand attention."
  []
  (let [world (sa/current-world)]
    (doseq [i (range (count world))
            j (range (count (first world)))
            :let [cell (get-in world [i j])
                  unit (:contents cell)]
            :when (and unit
                       (= :carrier (:type unit))
                       (= :player (:owner unit))
                       (pos? (uc/get-count unit :fighter-count)))]
      (let [total (uc/get-count unit :fighter-count)]
        (sa/update-world! assoc-in [i j :contents :awake-fighters] total)))))

(defn wake-sentries-seeing-enemy
  "Wakes player sentry units that can see an enemy unit."
  []
  (let [world (sa/current-world)
        world-atom (world-ref world)]
    (doseq [i (range (count world))
            j (range (count (first world)))
            :let [cell (get-in world [i j])
                  unit (:contents cell)]
            :when (and unit
                       (= :player (:owner unit))
                       (= :sentry (:mode unit))
                       (movement-services/enemy-unit-visible? unit [i j] world-atom))]
      (sa/update-world! update-in [i j :contents]
                        #(assoc % :mode :awake :reason :enemy-spotted)))))
