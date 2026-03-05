(ns empire.game-loop.round-setup.fuel
  (:require [empire.application.state-access :as sa]
            [empire.application.movement-services :as movement-services]
            [empire.config :as config]
            [empire.domain.services.round-setup :as domain-round-setup]))

(defn- world-ref [world] (atom world))

(defn- set-error-message!
  [msg ms]
  (sa/write-state! :error-message msg)
  (sa/write-state! :error-until (+ (System/currentTimeMillis) ms)))

(defn- bingo-fuel? [pos new-fuel]
  (let [world (sa/current-world)]
    (domain-round-setup/bingo-fuel?
     new-fuel
     (movement-services/friendly-city-in-range? pos new-fuel (world-ref world)))))

(defn- fuel-action [new-fuel pos]
  (domain-round-setup/fuel-action new-fuel (bingo-fuel? pos new-fuel)))

(defn- apply-fuel-action [pos action new-fuel]
  (case action
    :crashed (do (set-error-message! (:fighter-crashed config/messages) config/error-message-duration)
                 (sa/update-world! assoc-in (conj pos :contents :hits) 0))
    :out-of-fuel (sa/update-world! update-in (conj pos :contents)
                                   #(assoc % :fuel new-fuel :mode :awake :reason :fighter-out-of-fuel))
    :bingo (sa/update-world! update-in (conj pos :contents)
                             #(assoc % :fuel new-fuel :mode :awake :reason :fighter-bingo))
    :burn (sa/update-world! assoc-in (conj pos :contents :fuel) new-fuel)))

(defn consume-sentry-fighter-fuel
  "Consumes fuel for sentry fighters each round, applying fuel warnings."
  []
  (let [world (sa/current-world)]
    (doseq [i (range (count world))
            j (range (count (first world)))
            :let [cell (get-in world [i j])
                  unit (:contents cell)]
            :when (and unit
                       (= :fighter (:type unit))
                       (= :sentry (:mode unit)))]
      (let [new-fuel (dec (:fuel unit config/fighter-fuel))]
        (apply-fuel-action [i j] (fuel-action new-fuel [i j]) new-fuel)))))
