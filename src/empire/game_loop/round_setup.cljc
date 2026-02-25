;; mutation-tested: 2026-02-24
(ns empire.game-loop.round-setup
  "Round initialization: satellite moves, fuel consumption, sentry waking,
   dead unit removal, repair, step resets."
  (:require [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.containers.ops :as container-ops]
            [empire.containers.helpers :as uc]
            [empire.movement.map-utils :as map-utils]
            [empire.movement.satellite :as satellite]
            [empire.movement.visibility :as visibility]
            [empire.movement.wake-conditions :as wake]
            [empire.units.dispatcher :as dispatcher]))

(defn remove-dead-units
  "Removes units with hits at or below zero."
  []
  (doseq [i (range (count @atoms/game-map))
          j (range (count (first @atoms/game-map)))
          :let [cell (get-in @atoms/game-map [i j])
                contents (:contents cell)]
          :when (and contents (<= (:hits contents 1) 0))]
    (swap! atoms/game-map assoc-in [i j] (dissoc cell :contents))
    (visibility/update-cell-visibility [i j] (:owner contents))))

(defn reset-steps-remaining
  "Resets steps-remaining for all player units at start of round."
  []
  (doseq [i (range (count @atoms/game-map))
          j (range (count (first @atoms/game-map)))
          :let [cell (get-in @atoms/game-map [i j])
                unit (:contents cell)]
          :when (and unit (= (:owner unit) :player))]
    (let [steps (or (dispatcher/effective-speed (:type unit) (:hits unit)) 1)]
      (swap! atoms/game-map assoc-in [i j :contents :steps-remaining] steps))))

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

(defn- bingo-fuel? [pos new-fuel]
  (let [threshold (quot config/fighter-fuel config/bingo-fuel-divisor)]
    (and (<= new-fuel threshold)
         (wake/friendly-city-in-range? pos new-fuel atoms/game-map))))

(defn- fuel-action [new-fuel pos]
  (cond
    (<= new-fuel 0) :crashed
    (<= new-fuel 1) :out-of-fuel
    (bingo-fuel? pos new-fuel) :bingo
    :else :burn))

(defn- apply-fuel-action [pos action new-fuel]
  (case action
    :crashed (do (atoms/set-error-message (:fighter-crashed config/messages) config/error-message-duration)
                 (swap! atoms/game-map assoc-in (conj pos :contents :hits) 0))
    :out-of-fuel (swap! atoms/game-map update-in (conj pos :contents)
                        #(assoc % :fuel new-fuel :mode :awake :reason :fighter-out-of-fuel))
    :bingo (swap! atoms/game-map update-in (conj pos :contents)
                  #(assoc % :fuel new-fuel :mode :awake :reason :fighter-bingo))
    :burn (swap! atoms/game-map assoc-in (conj pos :contents :fuel) new-fuel)))

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
    (let [new-fuel (dec (:fuel unit config/fighter-fuel))]
      (apply-fuel-action [i j] (fuel-action new-fuel [i j]) new-fuel))))

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

(defn- find-satellite-coords
  "Returns coordinates of all satellites on the map.
   Returns a vector to avoid lazy evaluation issues during map modification."
  []
  (vec (for [i (range (count @atoms/game-map))
             j (range (count (first @atoms/game-map)))
             :let [cell (get-in @atoms/game-map [i j])
                   contents (:contents cell)]
             :when (= (:type contents) :satellite)]
         [i j])))

(defn- move-satellite-steps
  "Moves a satellite the number of steps based on its speed.
   Decrements turns-remaining once per round.
   Returns final position or nil if satellite expired."
  [start-coords]
  (loop [coords start-coords
         steps-left (config/unit-speed :satellite)]
    (let [cell (get-in @atoms/game-map coords)
          satellite (:contents cell)]
      (cond
        ;; No satellite here (already removed or error)
        (not satellite)
        nil

        ;; Satellite expired
        (<= (:turns-remaining satellite 0) 0)
        (do (swap! atoms/game-map update-in coords dissoc :contents)
            (visibility/update-cell-visibility coords (:owner satellite))
            nil)

        ;; No more steps this round - decrement turns-remaining once per round
        (zero? steps-left)
        (let [new-turns (dec (:turns-remaining satellite 1))]
          (if (<= new-turns 0)
            (do (swap! atoms/game-map update-in coords dissoc :contents)
                (visibility/update-cell-visibility coords (:owner satellite))
                nil)
            (do (swap! atoms/game-map assoc-in (conj coords :contents :turns-remaining) new-turns)
                coords)))

        ;; Move one step
        :else
        (let [new-coords (satellite/move-satellite coords)]
          (recur new-coords (dec steps-left)))))))

(defn move-satellites
  "Moves all satellites according to their speed.
   Removes satellites with turns-remaining at or below zero."
  []
  (doseq [coords (find-satellite-coords)]
    (move-satellite-steps coords)))

(defn- find-adjacent-empty-sea
  "Returns the first adjacent empty sea cell, or nil if none."
  [pos]
  (first (map-utils/get-matching-neighbors
          pos @atoms/game-map map-utils/neighbor-offsets
          #(and (= :sea (:type %)) (nil? (:contents %))))))

(defn- repair-city-ships
  "Repairs all ships in a city's shipyard by 1 hit each.
   Launches fully repaired ships to city cell or adjacent sea."
  [city-coords]
  (let [cell (get-in @atoms/game-map city-coords)
        shipyard (uc/get-shipyard-ships cell)]
    (when (seq shipyard)
      ;; First, repair all ships
      (let [repaired-ships (mapv uc/repair-ship shipyard)]
        (swap! atoms/game-map assoc-in (conj city-coords :shipyard) repaired-ships))
      ;; Then, launch fully repaired ships
      ;; Process from end to avoid index shifting issues
      (let [updated-cell (get-in @atoms/game-map city-coords)
            updated-shipyard (uc/get-shipyard-ships updated-cell)]
        (doseq [i (reverse (range (count updated-shipyard)))]
          (let [current-cell (get-in @atoms/game-map city-coords)
                ship (get-in current-cell [:shipyard i])]
            (when (uc/ship-fully-repaired? ship)
              (let [launch-pos (if (nil? (:contents current-cell))
                                 city-coords
                                 (find-adjacent-empty-sea city-coords))]
                (when launch-pos
                  (container-ops/launch-ship-from-shipyard city-coords i launch-pos))))))))))

(defn repair-damaged-ships
  "Repairs ships in all friendly city shipyards by 1 hit per round.
   Launches fully repaired ships onto the map if the city cell is empty."
  []
  (doseq [i (range (count @atoms/game-map))
          j (range (count (first @atoms/game-map)))
          :let [cell (get-in @atoms/game-map [i j])]
          :when (and (= (:type cell) :city)
                     (#{:player :computer} (:city-status cell))
                     (seq (uc/get-shipyard-ships cell)))]
    (repair-city-ships [i j])))
