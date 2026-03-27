(ns empire.computer.army-effects
  (:require [empire.computer.threat-response-port :as threat-response-port]))

(defn handle-attack-outcome!
  [{:keys [conquered-city?]}]
  (when conquered-city?
    (threat-response-port/rebuild-kamikazee-routing!)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-26T22:13:53.162051-05:00", :module-hash "2046465038", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 2, :hash "116750140"} {:id "defn/handle-attack-outcome!", :kind "defn", :line 4, :end-line 7, :hash "-1331798196"}]}
;; clj-mutate-manifest-end
