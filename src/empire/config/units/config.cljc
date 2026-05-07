;; mutation-tested: no
(ns empire.config.units.config
  (:require [empire.config.domain.core.unit-config :as unit-config]))

(def army-speed unit-config/army-speed)
(def army-cost unit-config/army-cost)
(def army-hits unit-config/army-hits)
(def army-strength unit-config/army-strength)
(def army-display-char unit-config/army-display-char)
(def army-visibility-radius unit-config/army-visibility-radius)

(def fighter-speed unit-config/fighter-speed)
(def fighter-cost unit-config/fighter-cost)
(def fighter-hits unit-config/fighter-hits)
(def fighter-strength unit-config/fighter-strength)
(def fighter-display-char unit-config/fighter-display-char)
(def fighter-fuel unit-config/fighter-fuel)
(def fighter-visibility-radius unit-config/fighter-visibility-radius)
(def fighter-bingo-threshold unit-config/fighter-bingo-threshold)
(def bingo-fuel-divisor unit-config/bingo-fuel-divisor)
(def explore-steps unit-config/explore-steps)

(def satellite-speed unit-config/satellite-speed)
(def satellite-cost unit-config/satellite-cost)
(def satellite-hits unit-config/satellite-hits)
(def satellite-strength unit-config/satellite-strength)
(def satellite-display-char unit-config/satellite-display-char)
(def satellite-turns unit-config/satellite-turns)
(def satellite-visibility-radius unit-config/satellite-visibility-radius)

(def transport-speed unit-config/transport-speed)
(def transport-cost unit-config/transport-cost)
(def transport-hits unit-config/transport-hits)
(def transport-strength unit-config/transport-strength)
(def transport-display-char unit-config/transport-display-char)
(def transport-capacity unit-config/transport-capacity)
(def transport-visibility-radius unit-config/transport-visibility-radius)

(def carrier-speed unit-config/carrier-speed)
(def carrier-cost unit-config/carrier-cost)
(def carrier-hits unit-config/carrier-hits)
(def carrier-strength unit-config/carrier-strength)
(def carrier-display-char unit-config/carrier-display-char)
(def carrier-capacity unit-config/carrier-capacity)
(def carrier-visibility-radius unit-config/carrier-visibility-radius)

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T00:42:36.021458-05:00", :module-hash "-484397156", :forms [{:id "form/0/ns", :kind "ns", :line 2, :end-line 2, :hash "974065544"} {:id "def/army-speed", :kind "def", :line 4, :end-line 4, :hash "-605029632"} {:id "def/army-cost", :kind "def", :line 5, :end-line 5, :hash "66542193"} {:id "def/army-hits", :kind "def", :line 6, :end-line 6, :hash "-483541434"} {:id "def/army-strength", :kind "def", :line 7, :end-line 7, :hash "-741888879"} {:id "def/army-display-char", :kind "def", :line 8, :end-line 8, :hash "-509818435"} {:id "def/army-visibility-radius", :kind "def", :line 9, :end-line 9, :hash "1246391928"} {:id "def/fighter-speed", :kind "def", :line 11, :end-line 11, :hash "1924563297"} {:id "def/fighter-cost", :kind "def", :line 12, :end-line 12, :hash "-1494276899"} {:id "def/fighter-hits", :kind "def", :line 13, :end-line 13, :hash "-1881131243"} {:id "def/fighter-strength", :kind "def", :line 14, :end-line 14, :hash "634831173"} {:id "def/fighter-display-char", :kind "def", :line 15, :end-line 15, :hash "1105541830"} {:id "def/fighter-fuel", :kind "def", :line 16, :end-line 16, :hash "-1859143541"} {:id "def/fighter-visibility-radius", :kind "def", :line 17, :end-line 17, :hash "-1181457512"} {:id "def/fighter-bingo-threshold", :kind "def", :line 18, :end-line 18, :hash "-786550539"} {:id "def/bingo-fuel-divisor", :kind "def", :line 19, :end-line 19, :hash "-1625408944"} {:id "def/explore-steps", :kind "def", :line 20, :end-line 20, :hash "-2088706086"} {:id "def/satellite-speed", :kind "def", :line 22, :end-line 22, :hash "411095348"} {:id "def/satellite-cost", :kind "def", :line 23, :end-line 23, :hash "-536459576"} {:id "def/satellite-hits", :kind "def", :line 24, :end-line 24, :hash "-1963861033"} {:id "def/satellite-strength", :kind "def", :line 25, :end-line 25, :hash "-819784268"} {:id "def/satellite-display-char", :kind "def", :line 26, :end-line 26, :hash "616409564"} {:id "def/satellite-turns", :kind "def", :line 27, :end-line 27, :hash "537147559"} {:id "def/satellite-visibility-radius", :kind "def", :line 28, :end-line 28, :hash "1818458351"} {:id "def/transport-speed", :kind "def", :line 30, :end-line 30, :hash "734663707"} {:id "def/transport-cost", :kind "def", :line 31, :end-line 31, :hash "-796422036"} {:id "def/transport-hits", :kind "def", :line 32, :end-line 32, :hash "1988070350"} {:id "def/transport-strength", :kind "def", :line 33, :end-line 33, :hash "-2116217338"} {:id "def/transport-display-char", :kind "def", :line 34, :end-line 34, :hash "-215646796"} {:id "def/transport-capacity", :kind "def", :line 35, :end-line 35, :hash "2103226462"} {:id "def/transport-visibility-radius", :kind "def", :line 36, :end-line 36, :hash "151802466"} {:id "def/carrier-speed", :kind "def", :line 38, :end-line 38, :hash "-1881277788"} {:id "def/carrier-cost", :kind "def", :line 39, :end-line 39, :hash "-1519005283"} {:id "def/carrier-hits", :kind "def", :line 40, :end-line 40, :hash "-1625363794"} {:id "def/carrier-strength", :kind "def", :line 41, :end-line 41, :hash "1779698709"} {:id "def/carrier-display-char", :kind "def", :line 42, :end-line 42, :hash "1863070983"} {:id "def/carrier-capacity", :kind "def", :line 43, :end-line 43, :hash "-2037671803"} {:id "def/carrier-visibility-radius", :kind "def", :line 44, :end-line 44, :hash "810481136"}]}
;; clj-mutate-manifest-end
