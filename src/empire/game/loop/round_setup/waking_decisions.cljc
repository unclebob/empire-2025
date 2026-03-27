(ns empire.game.loop.round-setup.waking-decisions
  (:require [empire.game-mechanics.containers.helpers :as uc]
            [empire.game-mechanics.movement.wake-conditions :as wake]))

(defn airport-fighter-wakes
  [world]
  (for [i (range (count world))
        j (range (count (first world)))
        :let [cell (get-in world [i j])
              total (uc/get-count cell :fighter-count)]
        :when (and (= (:type cell) :city)
                   (= (:city-status cell) :player)
                   (pos? total))]
    {:pos [i j] :awake-fighters total}))

(defn carrier-fighter-wakes
  [world]
  (for [i (range (count world))
        j (range (count (first world)))
        :let [cell (get-in world [i j])
              unit (:contents cell)
              total (uc/get-count unit :fighter-count)]
        :when (and unit
                   (= :carrier (:type unit))
                   (= :player (:owner unit))
                   (pos? total))]
    {:pos [i j] :awake-fighters total}))

(defn sentry-enemy-wakes
  [world]
  (let [world-atom (atom world)]
    (for [i (range (count world))
          j (range (count (first world)))
          :let [cell (get-in world [i j])
                unit (:contents cell)]
          :when (and unit
                     (= :player (:owner unit))
                     (= :sentry (:mode unit))
                     (wake/enemy-unit-visible? unit [i j] world-atom))]
      {:pos [i j]
       :reason :enemy-spotted})))

(defn wake-updates
  [wake-path wakes]
  (mapv (fn [{:keys [pos awake-fighters]}]
          {:path (into pos (if (sequential? wake-path) wake-path [wake-path]))
           :value awake-fighters})
        wakes))

(defn sentry-wake-updates
  [wakes]
  (mapv (fn [{:keys [pos reason]}]
          {:path (conj pos :contents)
           :update-fn #(assoc % :mode :awake :reason reason)})
        wakes))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T02:07:33.540482-05:00", :module-hash "-1214682990", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 3, :hash "-1441247639"} {:id "defn/airport-fighter-wakes", :kind "defn", :line 5, :end-line 14, :hash "-100017077"} {:id "defn/carrier-fighter-wakes", :kind "defn", :line 16, :end-line 27, :hash "1381565261"} {:id "defn/sentry-enemy-wakes", :kind "defn", :line 29, :end-line 41, :hash "1659630517"} {:id "defn/wake-updates", :kind "defn", :line 43, :end-line 48, :hash "-1198401980"} {:id "defn/sentry-wake-updates", :kind "defn", :line 50, :end-line 55, :hash "-1989402569"}]}
;; clj-mutate-manifest-end
