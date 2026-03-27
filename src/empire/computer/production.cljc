(ns empire.computer.production
  "Computer production module - priority-based production."
  (:require [empire.state.api :as sa]
            [empire.game-mechanics.visibility :as visibility]
            [empire.computer.production.decisions :as decisions]
            [empire.computer.production.stats :as stats]))

(defn- refresh-computer-map!
  []
  (visibility/refresh-visible-map! :computer))

(defn city-is-coastal? [city-pos]
  (stats/city-is-coastal? city-pos))

(defn rebuild-country-stats! []
  (refresh-computer-map!)
  (stats/rebuild-country-stats!))

(defn count-computer-units []
  (stats/count-computer-units))

(defn count-computer-cities []
  (stats/count-computer-cities))

(defn count-country-armies [country-id]
  (stats/count-country-armies country-id))

(defn count-country-coastal-cells [country-id]
  (stats/count-country-coastal-cells country-id))

(defn country-has-waiting-armies? [country-id]
  (stats/country-has-waiting-armies? country-id))

(defn country-city-producing? [city-pos country-id unit-type]
  (decisions/country-city-producing? city-pos country-id unit-type))

(defn country-city-producing-armies? [city-pos country-id]
  (decisions/country-city-producing-armies? city-pos country-id))

(defn has-unoccupied-coastal-cells? [country-id]
  (stats/has-unoccupied-coastal-cells? country-id))

(def ^:private country-army-limit-reached? stats/country-army-limit-reached?)
(def ^:private country-has-other-coastal-city? stats/country-has-other-coastal-city?)
(defn- count-carrier-producers []
  (count (filter (fn [[_coords prod]]
                   (and (map? prod)
                        (= :carrier (:item prod))))
                 (sa/read-state :production))))

(defn decide-production [city-pos]
  (refresh-computer-map!)
  (decisions/decide-production city-pos))

(defn process-computer-city [pos]
  (refresh-computer-map!)
  (decisions/process-computer-city pos))

(defn process-computer-city-with-current-visibility [pos]
  (decisions/process-computer-city pos))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-26T23:05:44.574951-05:00", :module-hash "2042880514", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 6, :hash "501769800"} {:id "defn-/refresh-computer-map!", :kind "defn-", :line 8, :end-line 10, :hash "-1538139572"} {:id "defn/city-is-coastal?", :kind "defn", :line 12, :end-line 13, :hash "-195109412"} {:id "defn/rebuild-country-stats!", :kind "defn", :line 15, :end-line 17, :hash "-914797650"} {:id "defn/count-computer-units", :kind "defn", :line 19, :end-line 20, :hash "-1151065690"} {:id "defn/count-computer-cities", :kind "defn", :line 22, :end-line 23, :hash "1313514888"} {:id "defn/count-country-armies", :kind "defn", :line 25, :end-line 26, :hash "-149046381"} {:id "defn/count-country-coastal-cells", :kind "defn", :line 28, :end-line 29, :hash "1276220846"} {:id "defn/country-has-waiting-armies?", :kind "defn", :line 31, :end-line 32, :hash "-1340406242"} {:id "defn/country-city-producing?", :kind "defn", :line 34, :end-line 35, :hash "-1532318696"} {:id "defn/country-city-producing-armies?", :kind "defn", :line 37, :end-line 38, :hash "-1297410548"} {:id "defn/has-unoccupied-coastal-cells?", :kind "defn", :line 40, :end-line 41, :hash "562197599"} {:id "def/country-army-limit-reached?", :kind "def", :line 43, :end-line 43, :hash "-1997178089"} {:id "def/country-has-other-coastal-city?", :kind "def", :line 44, :end-line 44, :hash "1676080305"} {:id "defn-/count-carrier-producers", :kind "defn-", :line 45, :end-line 49, :hash "395669148"} {:id "defn/decide-production", :kind "defn", :line 51, :end-line 53, :hash "836542091"} {:id "defn/process-computer-city", :kind "defn", :line 55, :end-line 57, :hash "-1077018842"} {:id "defn/process-computer-city-with-current-visibility", :kind "defn", :line 59, :end-line 60, :hash "1475454061"}]}
;; clj-mutate-manifest-end
