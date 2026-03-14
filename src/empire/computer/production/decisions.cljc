(ns empire.computer.production.decisions
  (:require [empire.game-mechanics.services.city-production :as city-production]
            [empire.state.api :as sa]
            [empire.config.core :as config]
            [empire.computer.early-game.strategy :as opening]
            [empire.computer.production.stats :as stats]
            [empire.computer.ship :as ship]
            [empire.computer.threat-response.kamikazee :as kamikazee]))


(defn country-city-producing?
  [city-pos country-id unit-type]
  (some (fn [[coords prod]]
          (and (map? prod)
               (= unit-type (:item prod))
               (not= coords city-pos)
               (let [cell (get-in (sa/current-world) coords)]
                 (and (= :city (:type cell))
                      (= :computer (:city-status cell))
                      (= country-id (:country-id cell))))))
        (sa/read-state :production)))

(defn country-city-producing-armies? [city-pos country-id]
  (country-city-producing? city-pos country-id :army))

(defn- country-city-producing-destroyers? [city-pos country-id]
  (country-city-producing? city-pos country-id :destroyer))

(defn- should-rotate-transport? [city-pos country-id]
  (and (= city-pos (get (sa/read-state :last-transport-city) country-id))
       (stats/country-has-other-coastal-city? city-pos country-id)))

(defn- should-produce-transport? [city-pos country-id coastal?]
  (when (and coastal?
             (>= (stats/count-country-armies country-id) config/armies-before-transport)
             (stats/country-has-waiting-armies? country-id)
             (not (should-rotate-transport? city-pos country-id)))
    (sa/write-state! :last-transport-city
                          (assoc (or (sa/read-state :last-transport-city) {})
                                 country-id city-pos))
    :transport))

(defn- should-produce-army? [country-id]
  (and (stats/has-unoccupied-coastal-cells? country-id)
       (not (stats/country-army-limit-reached? country-id))))

(defn- should-produce-patrol-boat? [country-id coastal?]
  (and coastal?
       (< (stats/count-country-patrol-boats country-id) config/max-patrol-boats-per-country)))

(defn- should-produce-destroyer? [city-pos country-id coastal? unit-counts]
  (and coastal?
       (< (get unit-counts :destroyer 0) (get unit-counts :transport 0))
       (stats/country-has-unadopted-transport? country-id)
       (not (country-city-producing-destroyers? city-pos country-id))))

(defn- should-produce-fighter? []
  (when (< (stats/count-all-computer-fighters) (stats/count-computer-cities))
    :fighter))

(defn- decide-country-production
  [city-pos country-id coastal? unit-counts]
  (or (should-produce-transport? city-pos country-id coastal?)
      (when (should-produce-army? country-id) :army)
      (when (should-produce-patrol-boat? country-id coastal?) :patrol-boat)
      (when (should-produce-destroyer? city-pos country-id coastal? unit-counts) :destroyer)
      (should-produce-fighter?)))

(defn- count-carrier-producers []
  (count (filter (fn [[_coords prod]]
                   (and (map? prod)
                        (= :carrier (:item prod))))
                 (sa/read-state :production))))

(defn- carrier-producible? [coastal? unit-counts]
  (and coastal?
       (> (stats/count-computer-cities) config/carrier-city-threshold)
       (< (get unit-counts :carrier 0) config/max-live-carriers)
       (< (count-carrier-producers) config/max-carrier-producers)
       (ship/find-carrier-position)))

(defn- capital-ship-needed? [coastal? unit-counts]
  (when coastal?
    (cond
      (< (get unit-counts :battleship 0) (get unit-counts :carrier 0)) :battleship
      (< (get unit-counts :submarine 0) (* 2 (get unit-counts :carrier 0))) :submarine)))

(defn- satellite-needed? [unit-counts]
  (and (> (stats/count-computer-cities) config/satellite-city-threshold)
       (< (get unit-counts :satellite 0) config/max-satellites)))

(defn- decide-global-production [coastal? unit-counts]
  (or (when (carrier-producible? coastal? unit-counts) :carrier)
      (capital-ship-needed? coastal? unit-counts)
      (when (satellite-needed? unit-counts) :satellite)))

(defn- has-inland-computer-city? []
  (let [game-map (sa/current-world)]
    (some (fn [i]
            (some (fn [j]
                    (let [cell (get-in game-map [i j])]
                      (and (= :city (:type cell))
                           (= :computer (:city-status cell))
                           (not (stats/city-is-coastal? [i j])))))
                  (range (count (first game-map)))))
          (range (count game-map)))))

(defn- set-opening-role!
  [city-pos role]
  (sa/update-world! assoc-in (conj city-pos :opening-role) role))

(defn- opening-production
  [city-pos]
  (when-let [item (opening/opening-production city-pos)]
    (let [role (opening/assigned-role city-pos)]
      (set-opening-role! city-pos role)
      (when (= :satellite item)
        (sa/write-state! :opening-satellite-produced? true))
      item)))

(defn decide-production [city-pos]
  (let [city-cell (get-in (sa/current-world) city-pos)
        country-id (:country-id city-cell)
        coastal? (stats/city-is-coastal? city-pos)
        unit-counts (stats/count-computer-units)]
    (or (kamikazee/invasion-production-override city-pos)
        (opening-production city-pos)
        (when country-id
          (or (decide-country-production city-pos country-id coastal? unit-counts)
              (decide-global-production coastal? unit-counts)))
        (when-not (and country-id (stats/country-army-limit-reached? country-id))
          :army))))

(defn process-computer-city [pos]
  (when (opening/should-reset-lake-production? pos)
    (sa/update-state! :production dissoc pos)
    (sa/update-world! update-in pos dissoc :opening-role))
  (let [current-production (get (sa/read-state :production) pos)]
    (when (nil? current-production)
      (when-let [unit-type (decide-production pos)]
        (city-production/set-city-production pos unit-type)))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-13T15:21:43.333833-05:00", :module-hash "-1945125333", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 7, :hash "800683599"} {:id "defn/country-city-producing?", :kind "defn", :line 10, :end-line 20, :hash "1067317565"} {:id "defn/country-city-producing-armies?", :kind "defn", :line 22, :end-line 23, :hash "59819800"} {:id "defn-/country-city-producing-destroyers?", :kind "defn-", :line 25, :end-line 26, :hash "-2030098685"} {:id "defn-/should-rotate-transport?", :kind "defn-", :line 28, :end-line 30, :hash "1108334608"} {:id "defn-/should-produce-transport?", :kind "defn-", :line 32, :end-line 40, :hash "493925233"} {:id "defn-/should-produce-army?", :kind "defn-", :line 42, :end-line 44, :hash "-1108987839"} {:id "defn-/should-produce-patrol-boat?", :kind "defn-", :line 46, :end-line 48, :hash "-1013238048"} {:id "defn-/should-produce-destroyer?", :kind "defn-", :line 50, :end-line 54, :hash "1914888961"} {:id "defn-/should-produce-fighter?", :kind "defn-", :line 56, :end-line 58, :hash "-2121852493"} {:id "defn-/decide-country-production", :kind "defn-", :line 60, :end-line 66, :hash "-1582427974"} {:id "defn-/count-carrier-producers", :kind "defn-", :line 68, :end-line 72, :hash "395669148"} {:id "defn-/carrier-producible?", :kind "defn-", :line 74, :end-line 79, :hash "1054641972"} {:id "defn-/capital-ship-needed?", :kind "defn-", :line 81, :end-line 85, :hash "-477728056"} {:id "defn-/satellite-needed?", :kind "defn-", :line 87, :end-line 89, :hash "1660586830"} {:id "defn-/decide-global-production", :kind "defn-", :line 91, :end-line 94, :hash "2073812679"} {:id "defn-/has-inland-computer-city?", :kind "defn-", :line 96, :end-line 105, :hash "-806656781"} {:id "defn-/set-opening-role!", :kind "defn-", :line 107, :end-line 109, :hash "-755314735"} {:id "defn-/opening-production", :kind "defn-", :line 111, :end-line 118, :hash "-129578558"} {:id "defn/decide-production", :kind "defn", :line 120, :end-line 130, :hash "-240695301"} {:id "defn/process-computer-city", :kind "defn", :line 132, :end-line 139, :hash "-643461719"}]}
;; clj-mutate-manifest-end
