(ns empire.computer.army-effects
  (:require [empire.computer.threat-response-port :as threat-response-port]))

(defn handle-attack-outcome!
  [{:keys [conquered-city?]}]
  (when conquered-city?
    (threat-response-port/rebuild-kamikazee-routing!)))
