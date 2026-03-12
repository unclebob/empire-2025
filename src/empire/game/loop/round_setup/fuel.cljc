(ns empire.game.loop.round-setup.fuel
  (:require [empire.state.api :as sa]
            [empire.game-mechanics.movement.wake-conditions :as wake]
            [empire.config.core :as config]
            [empire.game-mechanics.services.round-setup :as domain-round-setup]))

(defn- world-ref [world] (atom world))

(defn- set-error-message!
  [msg ms]
  (sa/write-state! :error-message msg)
  (sa/write-state! :error-until (+ (System/currentTimeMillis) ms)))

(defn- bingo-fuel? [pos new-fuel]
  (let [world (sa/current-world)]
    (domain-round-setup/bingo-fuel?
     new-fuel
     (wake/friendly-city-in-range? pos new-fuel (world-ref world)))))

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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T12:00:10.34683-05:00", :module-hash "2071655665", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 5, :hash "-1067048952"} {:id "defn-/world-ref", :kind "defn-", :line 7, :end-line 7, :hash "-1351735972"} {:id "defn-/set-error-message!", :kind "defn-", :line 9, :end-line 12, :hash "-369960802"} {:id "defn-/bingo-fuel?", :kind "defn-", :line 14, :end-line 18, :hash "-1488368327"} {:id "defn-/fuel-action", :kind "defn-", :line 20, :end-line 21, :hash "-1364834926"} {:id "defn-/apply-fuel-action", :kind "defn-", :line 23, :end-line 31, :hash "1684615316"} {:id "defn/consume-sentry-fighter-fuel", :kind "defn", :line 33, :end-line 45, :hash "-1721693425"}]}
;; clj-mutate-manifest-end
