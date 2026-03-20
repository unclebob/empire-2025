(ns empire.computer.threat-response-impl
  (:require [empire.computer.threat-response-port :as threat-response-port]
            [empire.computer.threat-response.core :as core]))

(def major-invasion-target-land? core/major-invasion-target-land?)
(def major-invasion-target-revision core/major-invasion-target-revision)
(def handle-detection! core/handle-detection!)
(def refresh-major-invasion-assignments! core/refresh-major-invasion-assignments!)
(def rebuild-kamikazee-routing! core/rebuild-kamikazee-routing!)
(def launch-kamikazee-from-airport! core/launch-kamikazee-from-airport!)
(def on-round-start! core/on-round-start!)
(def prepare-transport! core/prepare-transport!)
(def process-fighter-threat core/process-fighter-threat)
(def process-ship-threat core/process-ship-threat)

(defrecord ActiveThreatResponsePort []
  threat-response-port/ThreatResponsePort
  (process-fighter-threat* [_ pos unit]
    (core/process-fighter-threat pos unit))
  (process-ship-threat* [_ pos ship-type unit]
    (core/process-ship-threat pos ship-type unit))
  (rebuild-kamikazee-routing!* [_]
    (core/rebuild-kamikazee-routing!)))

(defn install-threat-response-port!
  []
  (threat-response-port/set-threat-response-port! (->ActiveThreatResponsePort)))

(install-threat-response-port!)
