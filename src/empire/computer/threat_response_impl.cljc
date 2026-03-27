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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-26T23:33:01.612994-05:00", :module-hash "2129555255", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 3, :hash "-407735459"} {:id "def/major-invasion-target-land?", :kind "def", :line 5, :end-line 5, :hash "-1301129731"} {:id "def/major-invasion-target-revision", :kind "def", :line 6, :end-line 6, :hash "-1708728303"} {:id "def/handle-detection!", :kind "def", :line 7, :end-line 7, :hash "941206491"} {:id "def/refresh-major-invasion-assignments!", :kind "def", :line 8, :end-line 8, :hash "-1820770552"} {:id "def/rebuild-kamikazee-routing!", :kind "def", :line 9, :end-line 9, :hash "892030327"} {:id "def/launch-kamikazee-from-airport!", :kind "def", :line 10, :end-line 10, :hash "-871288850"} {:id "def/on-round-start!", :kind "def", :line 11, :end-line 11, :hash "-1249823841"} {:id "def/prepare-transport!", :kind "def", :line 12, :end-line 12, :hash "2078845634"} {:id "def/process-fighter-threat", :kind "def", :line 13, :end-line 13, :hash "469495979"} {:id "def/process-ship-threat", :kind "def", :line 14, :end-line 14, :hash "-1957746910"} {:id "form/11/defrecord", :kind "defrecord", :line 16, :end-line 23, :hash "355996845"} {:id "defn/install-threat-response-port!", :kind "defn", :line 25, :end-line 27, :hash "2113943843"} {:id "form/13/install-threat-response-port!", :kind "install-threat-response-port!", :line 29, :end-line 29, :hash "-1767224390"}]}
;; clj-mutate-manifest-end
