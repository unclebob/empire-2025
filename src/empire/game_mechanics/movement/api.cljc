(ns empire.game-mechanics.movement.api
  (:require [empire.game-mechanics.movement.movement-execution :as execution]
            [empire.game-mechanics.movement.api-decisions :as decisions]
            [empire.game-mechanics.movement.movement-pathing :as pathing]
            [empire.game-mechanics.movement.movement-resolution :as resolution]
            [empire.game-mechanics.movement.movement-state :as state]))

(defn next-step-pos [pos target]
  (pathing/next-step-pos pos target))

(defn chebyshev-distance [from-pos to-pos]
  (pathing/chebyshev-distance from-pos to-pos))

(defn find-best-sidestep [from-pos target unit-type blocked-dir current-map]
  (pathing/find-best-sidestep from-pos target unit-type blocked-dir current-map))

(defn process-consumables [unit to-cell]
  (execution/process-consumables unit to-cell))

(defn do-move [from-coords final-pos cell final-unit]
  (execution/do-move from-coords final-pos cell final-unit))

(defn move-unit [from-coords target-coords cell current-map]
  (decisions/move-unit-result
   (resolution/move-unit from-coords target-coords cell current-map)))

(defn set-unit-movement
  ([unit-coords target-coords]
   (let [{:keys [unit-coords target-coords]} (decisions/set-unit-movement-args unit-coords target-coords false)]
     (resolution/set-unit-movement unit-coords target-coords)))
  ([unit-coords target-coords extended?]
   (let [{:keys [unit-coords target-coords extended?]} (decisions/set-unit-movement-args unit-coords target-coords extended?)]
     (resolution/set-unit-movement unit-coords target-coords extended?))))

(defn get-active-unit [cell]
  (state/get-active-unit cell))

(defn is-army-aboard-transport? [active-unit]
  (state/is-army-aboard-transport? active-unit))

(defn is-fighter-from-airport? [active-unit]
  (state/is-fighter-from-airport? active-unit))

(defn is-fighter-from-carrier? [active-unit]
  (state/is-fighter-from-carrier? active-unit))

(defn movement-context [cell active-unit]
  (state/movement-context cell active-unit))

(defn set-unit-mode [coords mode]
  (state/set-unit-mode coords mode))

(defn add-unit-at
  ([coords unit-type] (state/add-unit-at coords unit-type))
  ([coords unit-type owner] (state/add-unit-at coords unit-type owner)))

(defn wake-at [coords]
  (state/wake-at coords))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-16T14:24:13.876342-05:00", :module-hash "1751391316", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 6, :hash "345019253"} {:id "defn/next-step-pos", :kind "defn", :line 8, :end-line 9, :hash "-491111284"} {:id "defn/chebyshev-distance", :kind "defn", :line 11, :end-line 12, :hash "-637684582"} {:id "defn/find-best-sidestep", :kind "defn", :line 14, :end-line 15, :hash "-1241092376"} {:id "defn/process-consumables", :kind "defn", :line 17, :end-line 18, :hash "1918709833"} {:id "defn/do-move", :kind "defn", :line 20, :end-line 21, :hash "902977310"} {:id "defn/move-unit", :kind "defn", :line 23, :end-line 25, :hash "1158174084"} {:id "defn/set-unit-movement", :kind "defn", :line 27, :end-line 33, :hash "-943335997"} {:id "defn/get-active-unit", :kind "defn", :line 35, :end-line 36, :hash "1442717894"} {:id "defn/is-army-aboard-transport?", :kind "defn", :line 38, :end-line 39, :hash "-508419173"} {:id "defn/is-fighter-from-airport?", :kind "defn", :line 41, :end-line 42, :hash "-451635816"} {:id "defn/is-fighter-from-carrier?", :kind "defn", :line 44, :end-line 45, :hash "1566329616"} {:id "defn/movement-context", :kind "defn", :line 47, :end-line 48, :hash "223762678"} {:id "defn/set-unit-mode", :kind "defn", :line 50, :end-line 51, :hash "-1314827645"} {:id "defn/add-unit-at", :kind "defn", :line 53, :end-line 55, :hash "-1072003234"} {:id "defn/wake-at", :kind "defn", :line 57, :end-line 58, :hash "1360757252"}]}
;; clj-mutate-manifest-end
