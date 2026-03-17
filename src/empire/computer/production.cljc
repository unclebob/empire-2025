(ns empire.computer.production
  "Computer production module - priority-based production."
  (:require [empire.state.api :as sa]
            [empire.game-mechanics.movement.visibility :as visibility]
            [empire.computer.production.decisions :as decisions]
            [empire.computer.production.stats :as stats]))

(defn- make-empty-visible-map
  [game-map]
  (vec (repeat (count game-map)
               (vec (repeat (count (first game-map)) nil)))))

(defn- refresh-computer-map!
  []
  (let [game-map (sa/current-world)
        current-map (sa/read-state :computer-map)
        visible-map (if (and (vector? current-map)
                             (= (count current-map) (count game-map))
                             (= (count (first current-map))
                                (count (first game-map))))
                      current-map
                      (make-empty-visible-map game-map))]
    (when-let [updated (visibility/update-combatant-map-state
                        visible-map
                        :computer
                        game-map)]
      (sa/write-state! :computer-map updated))))

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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T11:58:05.597194-05:00", :module-hash "1487137274", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 5, :hash "1421105289"} {:id "defn/city-is-coastal?", :kind "defn", :line 8, :end-line 9, :hash "-195109412"} {:id "defn/rebuild-country-stats!", :kind "defn", :line 11, :end-line 12, :hash "1880435806"} {:id "defn/count-computer-units", :kind "defn", :line 14, :end-line 15, :hash "-1151065690"} {:id "defn/count-computer-cities", :kind "defn", :line 17, :end-line 18, :hash "1313514888"} {:id "defn/count-country-armies", :kind "defn", :line 20, :end-line 21, :hash "-149046381"} {:id "defn/count-country-coastal-cells", :kind "defn", :line 23, :end-line 24, :hash "1276220846"} {:id "defn/country-has-waiting-armies?", :kind "defn", :line 26, :end-line 27, :hash "-1340406242"} {:id "defn/country-city-producing?", :kind "defn", :line 29, :end-line 30, :hash "-1532318696"} {:id "defn/country-city-producing-armies?", :kind "defn", :line 32, :end-line 33, :hash "-1297410548"} {:id "defn/has-unoccupied-coastal-cells?", :kind "defn", :line 35, :end-line 36, :hash "562197599"} {:id "def/country-army-limit-reached?", :kind "def", :line 38, :end-line 38, :hash "-1997178089"} {:id "def/country-has-other-coastal-city?", :kind "def", :line 39, :end-line 39, :hash "1676080305"} {:id "defn-/count-carrier-producers", :kind "defn-", :line 40, :end-line 44, :hash "395669148"} {:id "defn/decide-production", :kind "defn", :line 46, :end-line 47, :hash "-1702676889"} {:id "defn/process-computer-city", :kind "defn", :line 49, :end-line 50, :hash "768696797"}]}
;; clj-mutate-manifest-end
