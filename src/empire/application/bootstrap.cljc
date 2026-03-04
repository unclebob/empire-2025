;; mutation-tested: no
(ns empire.application.bootstrap
  "Composition root wiring for application boundary implementations."
  (:require [empire.application.impl.runtime]
            [empire.application.impl.coords]
            [empire.application.impl.production-status]
            [empire.application.impl.city-production]
            [empire.application.impl.state]
            [empire.application.impl.unit-stamping]))

(defn initialize-default-services!
  "Loads app impl namespaces so their defmethod implementations are registered."
  []
  true)
