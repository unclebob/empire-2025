(ns empire.computer.threat-response-port)

(defprotocol ThreatResponsePort
  (process-fighter-threat* [this pos unit]
    "Handle fighter threat-response work for a unit. Returns truthy when handled.")
  (process-ship-threat* [this pos ship-type unit]
    "Handle ship threat-response work for a unit. Returns truthy when handled.")
  (rebuild-kamikazee-routing!* [this]
    "Rebuild threat-response kamikazee routing after a strategic state change."))

(defrecord NoopThreatResponsePort []
  ThreatResponsePort
  (process-fighter-threat* [_ _ _] nil)
  (process-ship-threat* [_ _ _ _] nil)
  (rebuild-kamikazee-routing!* [_] nil))

(defonce ^:private active-port (atom (->NoopThreatResponsePort)))

(defn set-threat-response-port!
  [port]
  (reset! active-port port))

(defn threat-response-port
  []
  @active-port)

(defn process-fighter-threat
  [pos unit]
  (process-fighter-threat* (threat-response-port) pos unit))

(defn process-ship-threat
  [pos ship-type unit]
  (process-ship-threat* (threat-response-port) pos ship-type unit))

(defn rebuild-kamikazee-routing!
  []
  (rebuild-kamikazee-routing!* (threat-response-port)))
