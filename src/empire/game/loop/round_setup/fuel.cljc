(ns empire.game.loop.round-setup.fuel
  (:require [empire.state.api :as sa]
            [empire.game-mechanics.movement.wake-conditions :as wake]
            [empire.config.core :as config]
            [empire.game-mechanics.services.round-setup :as domain-round-setup]
            [empire.game.loop.round-setup.fuel-decisions :as decisions]
            [empire.sound :as sound]))

(defn- world-ref [world] (atom world))

(defn- set-warning-message!
  [msg]
  (sa/write-state! :warning-message msg)
  (sound/play-bonk!))

(defn- bingo-fuel? [pos new-fuel]
  (let [world (sa/current-world)]
    (domain-round-setup/bingo-fuel?
     new-fuel
     (wake/friendly-city-in-range? pos new-fuel (world-ref world)))))

(defn- apply-fuel-action [pos action new-fuel]
  (case action
    :crashed (do (set-warning-message! (:fighter-crashed config/messages))
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
    (doseq [{:keys [pos update]}
            (decisions/sentry-fighter-fuel-actions
             world
             config/fighter-fuel
             (fn [pos new-fuel] (bingo-fuel? pos new-fuel)))]
      (apply-fuel-action pos (:action update) (:fuel update)))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T02:00:50.143255-05:00", :module-hash "1006523654", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 6, :hash "-253992843"} {:id "defn-/world-ref", :kind "defn-", :line 8, :end-line 8, :hash "-1351735972"} {:id "defn-/set-error-message!", :kind "defn-", :line 10, :end-line 13, :hash "-369960802"} {:id "defn-/bingo-fuel?", :kind "defn-", :line 15, :end-line 19, :hash "-1488368327"} {:id "defn-/apply-fuel-action", :kind "defn-", :line 21, :end-line 29, :hash "428394125"} {:id "defn/consume-sentry-fighter-fuel", :kind "defn", :line 31, :end-line 40, :hash "816435690"}]}
;; clj-mutate-manifest-end
