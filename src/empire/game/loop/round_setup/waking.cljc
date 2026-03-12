(ns empire.game.loop.round-setup.waking
  (:require [empire.state.api :as sa]
            [empire.game-mechanics.movement.wake-conditions :as wake]
            [empire.game-mechanics.containers.helpers :as uc]))

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
                       (wake/enemy-unit-visible? unit [i j] world-atom))]
      (sa/update-world! update-in [i j :contents]
                        #(assoc % :mode :awake :reason :enemy-spotted)))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T12:00:21.418149-05:00", :module-hash "-1066347908", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 4, :hash "1719980751"} {:id "defn-/world-ref", :kind "defn-", :line 6, :end-line 6, :hash "-1351735972"} {:id "defn/wake-airport-fighters", :kind "defn", :line 8, :end-line 21, :hash "116418176"} {:id "defn/wake-carrier-fighters", :kind "defn", :line 23, :end-line 38, :hash "825275198"} {:id "defn/wake-sentries-seeing-enemy", :kind "defn", :line 40, :end-line 54, :hash "241745160"}]}
;; clj-mutate-manifest-end
