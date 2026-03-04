(ns empire.movement.bootstrap
  (:require [empire.application.bootstrap :as app-bootstrap]
            [empire.movement.methods :as methods]
            [empire.movement.service :as service]))

(defn initialize-default-services!
  "Initializes movement services with the default concrete implementation."
  []
  (app-bootstrap/initialize-default-services!)
  (service/set-movement-services! (methods/default-movement-services)))
