(ns empire.game-mechanics.movement.api
  (:require [empire.game-mechanics.movement.movement-execution :as execution]
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
  (resolution/move-unit from-coords target-coords cell current-map))

(defn set-unit-movement
  ([unit-coords target-coords]
   (resolution/set-unit-movement unit-coords target-coords))
  ([unit-coords target-coords extended?]
   (resolution/set-unit-movement unit-coords target-coords extended?)))

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
