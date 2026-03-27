(ns empire.computer.threat-response.kamikazee
  "Facade for kamikazee fighter support."
  (:require [empire.computer.threat-response.kamikazee-mission :as mission]
            [empire.computer.threat-response.kamikazee-routing :as routing]
            [empire.computer.threat-response.kamikazee-targets :as targets]))

(def current-round targets/current-round)
(def ordered-army-target-positions targets/ordered-army-target-positions)
(def refresh-army-targets! targets/refresh-army-targets!)
(def record-army-target! targets/record-army-target!)
(def choose-army-target targets/choose-army-target)

(def site-distance routing/site-distance)
(def at-site? routing/at-site?)
(def plan-route routing/plan-route)
(def rebuild-routing-graph! routing/rebuild-routing-graph!)
(def fixed-carrier? routing/fixed-carrier?)
(def carrier-support-target routing/carrier-support-target)
(def invasion-production-override routing/invasion-production-override)

(def process-kamikazee-fighter mission/process-kamikazee-fighter)
(def launch-kamikazee-from-airport! mission/launch-kamikazee-from-airport!)

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-26T23:46:50.026816-05:00", :module-hash "1772324525", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 5, :hash "-649825508"} {:id "def/current-round", :kind "def", :line 7, :end-line 7, :hash "-1918896019"} {:id "def/ordered-army-target-positions", :kind "def", :line 8, :end-line 8, :hash "-950051864"} {:id "def/refresh-army-targets!", :kind "def", :line 9, :end-line 9, :hash "-2037843684"} {:id "def/record-army-target!", :kind "def", :line 10, :end-line 10, :hash "2100930905"} {:id "def/choose-army-target", :kind "def", :line 11, :end-line 11, :hash "1324018857"} {:id "def/site-distance", :kind "def", :line 13, :end-line 13, :hash "1617687813"} {:id "def/at-site?", :kind "def", :line 14, :end-line 14, :hash "1040031201"} {:id "def/plan-route", :kind "def", :line 15, :end-line 15, :hash "2028635226"} {:id "def/rebuild-routing-graph!", :kind "def", :line 16, :end-line 16, :hash "1520195119"} {:id "def/fixed-carrier?", :kind "def", :line 17, :end-line 17, :hash "-480624991"} {:id "def/carrier-support-target", :kind "def", :line 18, :end-line 18, :hash "1718641932"} {:id "def/invasion-production-override", :kind "def", :line 19, :end-line 19, :hash "1876443703"} {:id "def/process-kamikazee-fighter", :kind "def", :line 21, :end-line 21, :hash "293990572"} {:id "def/launch-kamikazee-from-airport!", :kind "def", :line 22, :end-line 22, :hash "1251576913"}]}
;; clj-mutate-manifest-end
