(ns empire.computer.threat-response-port)

(defprotocol ThreatResponsePort
  (rebuild-kamikazee-routing! [this]
    "Rebuild threat-response kamikazee routing after a strategic state change."))

(defrecord NoopThreatResponsePort []
  ThreatResponsePort
  (rebuild-kamikazee-routing! [_] nil))

(defonce ^:private active-port (atom (->NoopThreatResponsePort)))

(defn set-threat-response-port!
  [port]
  (reset! active-port port))

(defn threat-response-port
  []
  @active-port)
