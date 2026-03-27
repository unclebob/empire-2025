(ns empire.game.loop.round-setup.fuel-decisions
  (:require [empire.game-mechanics.services.round-setup :as domain-round-setup]))

(defn bingo-fuel?
  [new-fuel friendly-city-in-range?]
  (domain-round-setup/bingo-fuel? new-fuel friendly-city-in-range?))

(defn fuel-action
  [new-fuel friendly-city-in-range?]
  (domain-round-setup/fuel-action new-fuel (bingo-fuel? new-fuel friendly-city-in-range?)))

(defn fuel-update-action
  [new-fuel friendly-city-in-range?]
  {:action (fuel-action new-fuel friendly-city-in-range?)
   :fuel new-fuel})

(defn sentry-fighter-fuel-actions
  [world fighter-fuel friendly-city-in-range?]
  (vec
   (for [i (range (count world))
         j (range (count (first world)))
         :let [cell (get-in world [i j])
               unit (:contents cell)]
         :when (and unit
                    (= :fighter (:type unit))
                    (= :sentry (:mode unit)))
         :let [new-fuel (dec (:fuel unit fighter-fuel))]]
     {:pos [i j]
      :update (fuel-update-action new-fuel (friendly-city-in-range? [i j] new-fuel))})))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T01:59:36.106553-05:00", :module-hash "-1167960370", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 2, :hash "-2066105122"} {:id "defn/bingo-fuel?", :kind "defn", :line 4, :end-line 6, :hash "246805794"} {:id "defn/fuel-action", :kind "defn", :line 8, :end-line 10, :hash "-1964260053"} {:id "defn/fuel-update-action", :kind "defn", :line 12, :end-line 15, :hash "968851622"} {:id "defn/sentry-fighter-fuel-actions", :kind "defn", :line 17, :end-line 29, :hash "1144855070"}]}
;; clj-mutate-manifest-end
