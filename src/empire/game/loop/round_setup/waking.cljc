(ns empire.game.loop.round-setup.waking
  (:require [empire.state.api :as sa]
            [empire.game.loop.round-setup.waking-decisions :as decisions]))

(defn wake-airport-fighters
  "Wakes all fighters in player city airports at start of round.
   Fighters will be auto-launched if the city has a flight-path,
   otherwise they will demand attention."
  []
  (let [world (sa/current-world)]
    (doseq [{:keys [pos awake-fighters]} (decisions/airport-fighter-wakes world)]
      (sa/update-world! assoc-in (conj pos :awake-fighters) awake-fighters))))

(defn wake-carrier-fighters
  "Wakes all fighters on player carriers at start of round.
   Fighters will be auto-launched if the carrier has a flight-path,
   otherwise they will demand attention."
  []
  (let [world (sa/current-world)]
    (doseq [{:keys [pos awake-fighters]} (decisions/carrier-fighter-wakes world)]
      (sa/update-world! assoc-in (conj pos :contents :awake-fighters) awake-fighters))))

(defn wake-sentries-seeing-enemy
  "Wakes player sentry units that can see an enemy unit."
  []
  (let [world (sa/current-world)
        wakes (decisions/sentry-enemy-wakes world)]
    (doseq [{:keys [pos reason]} wakes]
      (sa/update-world! update-in (conj pos :contents)
                        #(assoc % :mode :awake :reason :enemy-spotted)))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-16T08:25:05.06585-05:00", :module-hash "551265528", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 3, :hash "-2050897319"} {:id "defn/wake-airport-fighters", :kind "defn", :line 5, :end-line 12, :hash "1224243406"} {:id "defn/wake-carrier-fighters", :kind "defn", :line 14, :end-line 21, :hash "413504483"} {:id "defn/wake-sentries-seeing-enemy", :kind "defn", :line 23, :end-line 30, :hash "174546867"}]}
;; clj-mutate-manifest-end
