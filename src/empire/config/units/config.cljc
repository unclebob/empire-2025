;; mutation-tested: no
(ns empire.config.units.config)

(def army-speed 1)
(def army-cost 5)
(def army-hits 1)
(def army-strength 1)
(def army-display-char "A")
(def army-visibility-radius 1)

(def fighter-speed 8)
(def fighter-cost 10)
(def fighter-hits 1)
(def fighter-strength 1)
(def fighter-display-char "F")
(def fighter-fuel 32)
(def fighter-visibility-radius 1)
(def fighter-bingo-threshold (quot fighter-fuel 4))
(def bingo-fuel-divisor 4)
(def explore-steps 50)

(def satellite-speed 10)
(def satellite-cost 50)
(def satellite-hits 1)
(def satellite-strength 1)
(def satellite-display-char "Z")
(def satellite-turns 50)
(def satellite-visibility-radius 2)

(def transport-speed 2)
(def transport-cost 30)
(def transport-hits 1)
(def transport-strength 1)
(def transport-display-char "T")
(def transport-capacity 6)
(def transport-visibility-radius 1)

(def carrier-speed 2)
(def carrier-cost 30)
(def carrier-hits 8)
(def carrier-strength 1)
(def carrier-display-char "C")
(def carrier-capacity 8)
(def carrier-visibility-radius 1)

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T11:59:46.445388-05:00", :module-hash "-484397156", :forms [{:id "form/0/ns", :kind "ns", :line 2, :end-line 2, :hash "974065544"} {:id "def/army-speed", :kind "def", :line 4, :end-line 4, :hash "-605029632"} {:id "def/army-cost", :kind "def", :line 5, :end-line 5, :hash "66542193"} {:id "def/army-hits", :kind "def", :line 6, :end-line 6, :hash "-483541434"} {:id "def/army-strength", :kind "def", :line 7, :end-line 7, :hash "-741888879"} {:id "def/army-display-char", :kind "def", :line 8, :end-line 8, :hash "-509818435"} {:id "def/army-visibility-radius", :kind "def", :line 9, :end-line 9, :hash "1246391928"} {:id "def/fighter-speed", :kind "def", :line 11, :end-line 11, :hash "1924563297"} {:id "def/fighter-cost", :kind "def", :line 12, :end-line 12, :hash "-1494276899"} {:id "def/fighter-hits", :kind "def", :line 13, :end-line 13, :hash "-1881131243"} {:id "def/fighter-strength", :kind "def", :line 14, :end-line 14, :hash "634831173"} {:id "def/fighter-display-char", :kind "def", :line 15, :end-line 15, :hash "1105541830"} {:id "def/fighter-fuel", :kind "def", :line 16, :end-line 16, :hash "-1859143541"} {:id "def/fighter-visibility-radius", :kind "def", :line 17, :end-line 17, :hash "-1181457512"} {:id "def/fighter-bingo-threshold", :kind "def", :line 18, :end-line 18, :hash "-786550539"} {:id "def/bingo-fuel-divisor", :kind "def", :line 19, :end-line 19, :hash "-1625408944"} {:id "def/explore-steps", :kind "def", :line 20, :end-line 20, :hash "-2088706086"} {:id "def/satellite-speed", :kind "def", :line 22, :end-line 22, :hash "411095348"} {:id "def/satellite-cost", :kind "def", :line 23, :end-line 23, :hash "-536459576"} {:id "def/satellite-hits", :kind "def", :line 24, :end-line 24, :hash "-1963861033"} {:id "def/satellite-strength", :kind "def", :line 25, :end-line 25, :hash "-819784268"} {:id "def/satellite-display-char", :kind "def", :line 26, :end-line 26, :hash "616409564"} {:id "def/satellite-turns", :kind "def", :line 27, :end-line 27, :hash "537147559"} {:id "def/satellite-visibility-radius", :kind "def", :line 28, :end-line 28, :hash "1818458351"} {:id "def/transport-speed", :kind "def", :line 30, :end-line 30, :hash "734663707"} {:id "def/transport-cost", :kind "def", :line 31, :end-line 31, :hash "-796422036"} {:id "def/transport-hits", :kind "def", :line 32, :end-line 32, :hash "1988070350"} {:id "def/transport-strength", :kind "def", :line 33, :end-line 33, :hash "-2116217338"} {:id "def/transport-display-char", :kind "def", :line 34, :end-line 34, :hash "-215646796"} {:id "def/transport-capacity", :kind "def", :line 35, :end-line 35, :hash "2103226462"} {:id "def/transport-visibility-radius", :kind "def", :line 36, :end-line 36, :hash "151802466"} {:id "def/carrier-speed", :kind "def", :line 38, :end-line 38, :hash "-1881277788"} {:id "def/carrier-cost", :kind "def", :line 39, :end-line 39, :hash "-1519005283"} {:id "def/carrier-hits", :kind "def", :line 40, :end-line 40, :hash "-1625363794"} {:id "def/carrier-strength", :kind "def", :line 41, :end-line 41, :hash "1779698709"} {:id "def/carrier-display-char", :kind "def", :line 42, :end-line 42, :hash "1863070983"} {:id "def/carrier-capacity", :kind "def", :line 43, :end-line 43, :hash "-2037671803"} {:id "def/carrier-visibility-radius", :kind "def", :line 44, :end-line 44, :hash "810481136"}]}
;; clj-mutate-manifest-end
