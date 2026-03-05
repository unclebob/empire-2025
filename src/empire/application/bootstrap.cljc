;; mutation-tested: no
(ns empire.application.bootstrap
  "Composition root wiring for application boundary implementations."
  (:require [empire.application.runtime :as app-runtime]
            [empire.application.impl.runtime]
            [empire.application.impl.coords]
            [empire.application.impl.production-status]
            [empire.application.impl.city-production]
            [empire.application.impl.state]
            [empire.application.impl.unit-stamping]
            [empire.debug.impl.facade-methods]
            [empire.domain.model.impl.combat-runtime]
            [empire.movement.pathfinding :as pathfinding]
            [empire.units.impl.dispatcher]
            [empire.units.impl.satellite]))

(defn initialize-default-services!
  "Loads app impl namespaces so their defmethod implementations are registered."
  []
  (pathfinding/set-world-loader! (:load-world (app-runtime/default-state-ctx)))
  true)
