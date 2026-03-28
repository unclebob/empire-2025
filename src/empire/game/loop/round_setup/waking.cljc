(ns empire.game.loop.round-setup.waking
  (:require [empire.state.api :as sa]
            [empire.game.loop.round-setup.waking-decisions :as decisions]))

(defn wake-carrier-fighters
  "Wakes all fighters on player carriers at start of round.
   Fighters will be auto-launched if the carrier has a flight-path,
   otherwise they will demand attention."
  []
  (let [world (sa/current-world)]
    (doseq [{:keys [path value]} (decisions/wake-updates [:contents :awake-fighters]
                                                         (decisions/carrier-fighter-wakes world))]
      (sa/update-world! assoc-in path value))))

(defn wake-sentries-seeing-enemy
  "Wakes player sentry units that can see an enemy unit."
  []
  (let [world (sa/current-world)
        wakes (decisions/sentry-enemy-wakes world)]
    (doseq [{:keys [path update-fn]} (decisions/sentry-wake-updates wakes)]
      (sa/update-world! update-in path update-fn))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T02:08:49.546299-05:00", :module-hash "1811054114", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 3, :hash "-2050897319"} {:id "defn/wake-airport-fighters", :kind "defn", :line 5, :end-line 13, :hash "-77071439"} {:id "defn/wake-carrier-fighters", :kind "defn", :line 15, :end-line 23, :hash "-1091345431"} {:id "defn/wake-sentries-seeing-enemy", :kind "defn", :line 25, :end-line 31, :hash "-1905827531"}]}
;; clj-mutate-manifest-end
