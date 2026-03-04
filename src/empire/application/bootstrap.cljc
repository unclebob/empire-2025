;; mutation-tested: no
(ns empire.application.bootstrap
  "Composition root wiring for application boundary implementations."
  (:require [empire.application.impl.runtime]
            [empire.application.impl.state]))

(defn initialize-default-services!
  "Loads app impl namespaces so their defmethod implementations are registered."
  []
  true)
