;; mutation-tested: 2026-03-03
(ns empire.computer.production
  "Computer production module - priority-based production."
  (:require [empire.state.api :as sa]
            [empire.computer.production.decisions :as decisions]
            [empire.computer.production.stats :as stats]))


(defn city-is-coastal? [city-pos]
  (stats/city-is-coastal? city-pos))

(defn rebuild-country-stats! []
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
  (decisions/decide-production city-pos))

(defn process-computer-city [pos]
  (decisions/process-computer-city pos))
