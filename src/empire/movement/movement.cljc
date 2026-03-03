;; mutation-tested: 2026-02-28
(ns empire.movement.movement)

(defprotocol MovementPathingPort
  (path-next-step-pos [this pos target])
  (path-chebyshev-distance [this from-pos to-pos])
  (path-find-best-sidestep [this from-pos target unit-type blocked-dir current-map]))

(defprotocol MovementExecutionPort
  (exec-process-consumables [this unit to-cell])
  (exec-do-move [this from-coords final-pos cell final-unit]))

(defprotocol MovementResolutionPort
  (resolve-move-unit [this from-coords target-coords cell current-map])
  (resolve-set-unit-movement [this unit-coords target-coords extended?]))

(defprotocol MovementStatePort
  (state-get-active-unit [this cell])
  (state-is-army-aboard-transport? [this active-unit])
  (state-is-fighter-from-airport? [this active-unit])
  (state-is-fighter-from-carrier? [this active-unit])
  (state-movement-context [this cell active-unit])
  (state-set-unit-mode [this coords mode])
  (state-add-unit-at [this coords unit-type owner])
  (state-wake-at [this coords]))
