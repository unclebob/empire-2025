(ns empire.ui.util.input.actions
  (:require [empire.movement.movement-state :as movement-state]
            [empire.state.api :as sa]
            [empire.ui.util.input.actions.modes :as modes]
            [empire.ui.util.input.actions.movement :as movement]
            [empire.ui.util.input.actions.production :as production]))

(def army-aboard-action movement/army-aboard-action)

(defn handle-key [k]
  (when-let [coords (first (sa/read-state :cells-needing-attention))]
    (let [cell (get-in (sa/current-world) coords)
          active-unit (movement-state/get-active-unit cell)]
      (if active-unit
        (case k
          :space (modes/handle-space-key coords)
          :u (modes/handle-unload-key coords cell)
          :s (modes/handle-sentry-key coords cell active-unit)
          :l (modes/handle-look-around-key coords cell active-unit)
          (movement/handle-unit-movement-key k coords cell))
        (production/handle-city-production-key k coords cell)))))
