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
            [empire.movement.context :as movement-context]
            [empire.movement.pathfinding :as pathfinding]
            [empire.movement.pathfinding-bfs.context :as bfs-context]
            [empire.units.impl.dispatcher]
            [empire.computer.core.impl]
            [empire.units.impl.satellite]))

(defn initialize-default-services!
  "Loads app impl namespaces so their defmethod implementations are registered."
  []
  (let [ctx (app-runtime/default-state-ctx)]
    (movement-context/set-state-ctx! ctx)
    (pathfinding/set-world-loader! (:load-world ctx))
    (bfs-context/set-world-loader! (:load-world ctx))
    (bfs-context/set-runtime-reader! (:read-runtime-state ctx)))
  true)
