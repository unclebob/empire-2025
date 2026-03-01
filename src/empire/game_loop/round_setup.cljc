;; mutation-tested: 2026-02-26
(ns empire.game-loop.round-setup
  "Round initialization: satellite moves, fuel consumption, sentry waking,
   dead unit removal, repair, step resets."
  (:require [empire.application.runtime :as app-runtime]
            [empire.application.state :as app-state]
            [empire.config :as config]
            [empire.containers.ops :as container-ops]
            [empire.containers.helpers :as uc]
            [empire.domain.world.round-setup :as domain-round-setup]
            [empire.game-loop.round-setup.satellites :as satellites]
            [empire.movement.map-utils :as map-utils]
            [empire.movement.satellite :as satellite]
            [empire.movement.visibility :as visibility]
            [empire.movement.wake-conditions :as wake]
            [empire.units.dispatcher :as dispatcher]))

(def ^:private state-ctx
  (delay (app-runtime/default-state-ctx)))

(defn- current-world
  []
  ((:load-world @state-ctx)))

(defn- update-game-map!
  [f & args]
  (apply app-state/update-world! @state-ctx f args))

(defn- read-runtime-state
  [k]
  ((:read-runtime-state @state-ctx) k))

(defn- write-runtime-state!
  [k v]
  ((:write-runtime-state! @state-ctx) k v))

(defn- update-runtime-state!
  [k f & args]
  (let [current (read-runtime-state k)
        next-state (apply f current args)]
    (write-runtime-state! k next-state)))

(defn- set-error-message!
  [msg ms]
  (write-runtime-state! :error-message msg)
  (write-runtime-state! :error-until (+ (System/currentTimeMillis) ms)))

(defn- world-ref
  [world]
  (atom world))

(defn dead-unit? [contents]
  (domain-round-setup/dead-unit? contents))

(defn computer-carrier? [contents]
  (domain-round-setup/computer-carrier? contents))

(defn remove-dead-units
  "Removes units with hits at or below zero."
  []
  (let [world (current-world)]
    (doseq [i (range (count world))
            j (range (count (first world)))
            :let [cell (get-in world [i j])
                contents (:contents cell)]
            :when (dead-unit? contents)]
      (when (computer-carrier? contents)
        (update-runtime-state! :computer-carrier-positions disj [i j]))
      (update-game-map! assoc-in [i j] (dissoc cell :contents))
      (visibility/update-cell-visibility [i j] (:owner contents)))))

(defn reset-steps-remaining
  "Resets steps-remaining for all player units at start of round."
  []
  (let [world (current-world)]
    (doseq [i (range (count world))
            j (range (count (first world)))
            :let [cell (get-in world [i j])
                unit (:contents cell)]
            :when (and unit (= (:owner unit) :player))]
      (let [steps (or (dispatcher/effective-speed (:type unit) (:hits unit)) 1)]
        (update-game-map! assoc-in [i j :contents :steps-remaining] steps)))))

(defn wake-airport-fighters
  "Wakes all fighters in player city airports at start of round.
   Fighters will be auto-launched if the city has a flight-path,
   otherwise they will demand attention."
  []
  (let [world (current-world)]
    (doseq [i (range (count world))
            j (range (count (first world)))
            :let [cell (get-in world [i j])]
            :when (and (= (:type cell) :city)
                       (= (:city-status cell) :player)
                       (pos? (uc/get-count cell :fighter-count)))]
      (let [total (uc/get-count cell :fighter-count)]
        (update-game-map! assoc-in [i j :awake-fighters] total)))))

(defn wake-carrier-fighters
  "Wakes all fighters on player carriers at start of round.
   Fighters will be auto-launched if the carrier has a flight-path,
   otherwise they will demand attention."
  []
  (let [world (current-world)]
    (doseq [i (range (count world))
            j (range (count (first world)))
            :let [cell (get-in world [i j])
                  unit (:contents cell)]
            :when (and unit
                       (= :carrier (:type unit))
                       (= :player (:owner unit))
                       (pos? (uc/get-count unit :fighter-count)))]
      (let [total (uc/get-count unit :fighter-count)]
        (update-game-map! assoc-in [i j :contents :awake-fighters] total)))))

(defn- bingo-fuel? [pos new-fuel]
  (let [world (current-world)]
    (domain-round-setup/bingo-fuel?
     new-fuel
     (wake/friendly-city-in-range? pos new-fuel (world-ref world)))))

(defn- fuel-action [new-fuel pos]
  (domain-round-setup/fuel-action new-fuel (bingo-fuel? pos new-fuel)))

(defn- apply-fuel-action [pos action new-fuel]
  (case action
    :crashed (do (set-error-message! (:fighter-crashed config/messages) config/error-message-duration)
                 (update-game-map! assoc-in (conj pos :contents :hits) 0))
    :out-of-fuel (update-game-map! update-in (conj pos :contents)
                                   #(assoc % :fuel new-fuel :mode :awake :reason :fighter-out-of-fuel))
    :bingo (update-game-map! update-in (conj pos :contents)
                             #(assoc % :fuel new-fuel :mode :awake :reason :fighter-bingo))
    :burn (update-game-map! assoc-in (conj pos :contents :fuel) new-fuel)))

(defn consume-sentry-fighter-fuel
  "Consumes fuel for sentry fighters each round, applying fuel warnings."
  []
  (let [world (current-world)]
    (doseq [i (range (count world))
            j (range (count (first world)))
            :let [cell (get-in world [i j])
                  unit (:contents cell)]
            :when (and unit
                       (= :fighter (:type unit))
                       (= :sentry (:mode unit)))]
      (let [new-fuel (dec (:fuel unit config/fighter-fuel))]
        (apply-fuel-action [i j] (fuel-action new-fuel [i j]) new-fuel)))))

(defn wake-sentries-seeing-enemy
  "Wakes player sentry units that can see an enemy unit."
  []
  (let [world (current-world)
        world-atom (world-ref world)]
    (doseq [i (range (count world))
            j (range (count (first world)))
            :let [cell (get-in world [i j])
                  unit (:contents cell)]
            :when (and unit
                       (= :player (:owner unit))
                       (= :sentry (:mode unit))
                       (wake/enemy-unit-visible? unit [i j] world-atom))]
      (update-game-map! update-in [i j :contents]
                        #(assoc % :mode :awake :reason :enemy-spotted)))))

(defn move-satellites
  "Moves all satellites according to their speed.
   Removes satellites with turns-remaining at or below zero."
  []
  (satellites/move-satellites!
   {:current-world current-world
    :update-game-map! update-game-map!
    :update-visibility! visibility/update-cell-visibility
    :move-satellite satellite/move-satellite
    :satellite-speed (config/unit-speed :satellite)}))

(defn- find-adjacent-empty-sea
  "Returns the first adjacent empty sea cell, or nil if none."
  [pos]
  (first (map-utils/get-matching-neighbors
          pos (current-world) map-utils/neighbor-offsets
          #(and (= :sea (:type %)) (nil? (:contents %))))))

(defn- repair-city-ships
  "Repairs all ships in a city's shipyard by 1 hit each.
   Launches fully repaired ships to city cell or adjacent sea."
  [city-coords]
  (let [cell (get-in (current-world) city-coords)
        shipyard (uc/get-shipyard-ships cell)]
    (when (seq shipyard)
      ;; First, repair all ships
      (let [repaired-ships (mapv uc/repair-ship shipyard)]
        (update-game-map! assoc-in (conj city-coords :shipyard) repaired-ships))
      ;; Then, launch fully repaired ships
      ;; Process from end to avoid index shifting issues
      (let [updated-cell (get-in (current-world) city-coords)
            updated-shipyard (uc/get-shipyard-ships updated-cell)]
        (doseq [i (reverse (range (count updated-shipyard)))]
          (let [current-cell (get-in (current-world) city-coords)
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
  (let [world (current-world)]
    (doseq [i (range (count world))
            j (range (count (first world)))
            :let [cell (get-in world [i j])]
            :when (and (= (:type cell) :city)
                       (#{:player :computer} (:city-status cell))
                       (seq (uc/get-shipyard-ships cell)))]
      (repair-city-ships [i j]))))
