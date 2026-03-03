;; Provides default concrete implementations for movement protocols.
(ns empire.movement.methods
  (:require [empire.movement.movement :as movement]
            [empire.movement.movement-execution :as execution]
            [empire.movement.movement-pathing :as pathing]
            [empire.movement.movement-resolution :as resolution]
            [empire.movement.movement-state :as state]))

(defrecord DefaultMovementServices []
  movement/MovementPathingPort
  (path-next-step-pos [_ pos target]
    (pathing/next-step-pos pos target))
  (path-chebyshev-distance [_ from-pos to-pos]
    (pathing/chebyshev-distance from-pos to-pos))
  (path-find-best-sidestep [_ from-pos target unit-type blocked-dir current-map]
    (pathing/find-best-sidestep from-pos target unit-type blocked-dir current-map))

  movement/MovementExecutionPort
  (exec-process-consumables [_ unit to-cell]
    (execution/process-consumables unit to-cell))
  (exec-do-move [_ from-coords final-pos cell final-unit]
    (execution/do-move from-coords final-pos cell final-unit))

  movement/MovementResolutionPort
  (resolve-move-unit [_ from-coords target-coords cell current-map]
    (resolution/move-unit from-coords target-coords cell current-map))
  (resolve-set-unit-movement [_ unit-coords target-coords extended?]
    (resolution/set-unit-movement unit-coords target-coords extended?))

  movement/MovementStatePort
  (state-get-active-unit [_ cell]
    (state/get-active-unit cell))
  (state-is-army-aboard-transport? [_ active-unit]
    (state/is-army-aboard-transport? active-unit))
  (state-is-fighter-from-airport? [_ active-unit]
    (state/is-fighter-from-airport? active-unit))
  (state-is-fighter-from-carrier? [_ active-unit]
    (state/is-fighter-from-carrier? active-unit))
  (state-movement-context [_ cell active-unit]
    (state/movement-context cell active-unit))
  (state-set-unit-mode [_ coords mode]
    (state/set-unit-mode coords mode))
  (state-add-unit-at [_ coords unit-type owner]
    (state/add-unit-at coords unit-type owner))
  (state-wake-at [_ coords]
    (state/wake-at coords)))

(defn default-movement-services []
  (->DefaultMovementServices))
