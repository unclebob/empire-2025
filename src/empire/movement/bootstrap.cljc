(ns empire.movement.bootstrap
  (:require [empire.movement.methods :as methods]
            [empire.movement.service :as service]))

(defn initialize-default-services!
  "Initializes movement services with the default concrete implementation."
  []
  (service/set-movement-services! (methods/default-movement-services)))
