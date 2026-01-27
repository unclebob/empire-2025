(ns empire.game-loop.sentry
  "Sentry unit management and wake conditions.

   Handles fuel consumption for sentry fighters and waking units
   when enemies come into view."
  (:require [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.containers.helpers :as uc]
            [empire.movement.wake-conditions :as wake]))

(defn consume-sentry-fighter-fuel
  "Consumes fuel for sentry fighters each round, applying fuel warnings."
  []
  (doseq [i (range (count @atoms/game-map))
          j (range (count (first @atoms/game-map)))
          :let [cell (get-in @atoms/game-map [i j])
                unit (:contents cell)]
          :when (and unit
                     (= :fighter (:type unit))
                     (= :sentry (:mode unit)))]
    (let [current-fuel (:fuel unit config/fighter-fuel)
          new-fuel (dec current-fuel)
          pos [i j]
          bingo-threshold (quot config/fighter-fuel 4)
          low-fuel? (<= new-fuel 1)
          bingo-fuel? (and (<= new-fuel bingo-threshold)
                           (wake/friendly-city-in-range? pos new-fuel atoms/game-map))]
      (cond
        (<= new-fuel 0)
        (swap! atoms/game-map assoc-in [i j :contents :hits] 0)

        low-fuel?
        (swap! atoms/game-map update-in [i j :contents]
               #(assoc % :fuel new-fuel :mode :awake :reason :fighter-out-of-fuel))

        bingo-fuel?
        (swap! atoms/game-map update-in [i j :contents]
               #(assoc % :fuel new-fuel :mode :awake :reason :fighter-bingo))

        :else
        (swap! atoms/game-map assoc-in [i j :contents :fuel] new-fuel)))))

(defn wake-sentries-seeing-enemy
  "Wakes player sentry units that can see an enemy unit."
  []
  (doseq [i (range (count @atoms/game-map))
          j (range (count (first @atoms/game-map)))
          :let [cell (get-in @atoms/game-map [i j])
                unit (:contents cell)]
          :when (and unit
                     (= :player (:owner unit))
                     (= :sentry (:mode unit))
                     (wake/enemy-unit-visible? unit [i j] atoms/game-map))]
    (swap! atoms/game-map update-in [i j :contents]
           #(assoc % :mode :awake :reason :enemy-spotted))))

(defn wake-airport-fighters
  "Wakes all fighters in player city airports at start of round.
   Fighters will be auto-launched if the city has a flight-path,
   otherwise they will demand attention."
  []
  (doseq [i (range (count @atoms/game-map))
          j (range (count (first @atoms/game-map)))
          :let [cell (get-in @atoms/game-map [i j])]
          :when (and (= (:type cell) :city)
                     (= (:city-status cell) :player)
                     (pos? (uc/get-count cell :fighter-count)))]
    (let [total (uc/get-count cell :fighter-count)]
      (swap! atoms/game-map assoc-in [i j :awake-fighters] total))))

(defn wake-carrier-fighters
  "Wakes all fighters on player carriers at start of round.
   Fighters will be auto-launched if the carrier has a flight-path,
   otherwise they will demand attention."
  []
  (doseq [i (range (count @atoms/game-map))
          j (range (count (first @atoms/game-map)))
          :let [cell (get-in @atoms/game-map [i j])
                unit (:contents cell)]
          :when (and unit
                     (= :carrier (:type unit))
                     (= :player (:owner unit))
                     (pos? (uc/get-count unit :fighter-count)))]
    (let [total (uc/get-count unit :fighter-count)]
      (swap! atoms/game-map assoc-in [i j :contents :awake-fighters] total))))
