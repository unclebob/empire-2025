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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-26T23:34:24.39606-05:00", :module-hash "1485915598", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "-1455212964"} {:id "form/1/defprotocol", :kind "defprotocol", :line 3, :end-line 9, :hash "-1794363128"} {:id "form/2/defrecord", :kind "defrecord", :line 11, :end-line 15, :hash "-1161034417"} {:id "form/3/defonce", :kind "defonce", :line 17, :end-line 17, :hash "1008733038"} {:id "defn/set-threat-response-port!", :kind "defn", :line 19, :end-line 21, :hash "-1674546869"} {:id "defn/threat-response-port", :kind "defn", :line 23, :end-line 25, :hash "-494934277"} {:id "defn/process-fighter-threat", :kind "defn", :line 27, :end-line 29, :hash "-1769073466"} {:id "defn/process-ship-threat", :kind "defn", :line 31, :end-line 33, :hash "1477656477"} {:id "defn/rebuild-kamikazee-routing!", :kind "defn", :line 35, :end-line 37, :hash "-996086086"}]}
;; clj-mutate-manifest-end
