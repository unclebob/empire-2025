(ns empire.computer.production.decisions
  (:require [empire.game-mechanics.services.city-production :as city-production]
            [empire.state.api :as sa]
            [empire.config.core :as config]
            [empire.computer.production.stats :as stats]
            [empire.computer.ship :as ship]))


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

(defn- early-patrol-boat-needed? [coastal?]
  (and coastal? (not (sa/read-state :early-patrol-boat-produced?))))

(defn- has-distinct-army-and-transport-producers?
  []
  (let [production (sa/read-state :production)
        army-cities (set (for [[coords prod] production
                               :when (and (map? prod) (= :army (:item prod)))]
                           coords))
        transport-cities (set (for [[coords prod] production
                                    :when (and (map? prod) (= :transport (:item prod)))]
                                coords))]
    (boolean (some #(contains? transport-cities %)
                   (for [a army-cities
                         t transport-cities
                         :when (not= a t)]
                     t)))))

(defn- early-satellite-needed? [coastal?]
  (and (sa/read-state :early-patrol-boat-produced?)
       (not (sa/read-state :early-satellite-produced?))
       (has-distinct-army-and-transport-producers?)
       (or (not coastal?) (not (has-inland-computer-city?)))))

(defn- decide-early-production [city-pos coastal?]
  (when (sa/read-state :transport-fully-loaded?)
    (cond
      (early-patrol-boat-needed? coastal?)
      (do (sa/write-state! :early-patrol-boat-produced? true)
          :patrol-boat)

      (early-satellite-needed? coastal?)
      (do (sa/write-state! :early-satellite-produced? true)
          :satellite))))

(defn decide-production [city-pos]
  (let [city-cell (get-in (sa/current-world) city-pos)
        country-id (:country-id city-cell)
        coastal? (stats/city-is-coastal? city-pos)
        unit-counts (stats/count-computer-units)]
    (or (decide-early-production city-pos coastal?)
        (when country-id
          (or (decide-country-production city-pos country-id coastal? unit-counts)
              (decide-global-production coastal? unit-counts)))
        (when-not (and country-id (stats/country-army-limit-reached? country-id))
          :army))))

(defn process-computer-city [pos]
  (let [current-production (get (sa/read-state :production) pos)]
    (when (nil? current-production)
      (when-let [unit-type (decide-production pos)]
        (city-production/set-city-production pos unit-type)))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T11:58:08.180206-05:00", :module-hash "186252075", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 6, :hash "916608097"} {:id "defn/country-city-producing?", :kind "defn", :line 9, :end-line 19, :hash "1067317565"} {:id "defn/country-city-producing-armies?", :kind "defn", :line 21, :end-line 22, :hash "59819800"} {:id "defn-/country-city-producing-destroyers?", :kind "defn-", :line 24, :end-line 25, :hash "-2030098685"} {:id "defn-/should-rotate-transport?", :kind "defn-", :line 27, :end-line 29, :hash "1108334608"} {:id "defn-/should-produce-transport?", :kind "defn-", :line 31, :end-line 39, :hash "493925233"} {:id "defn-/should-produce-army?", :kind "defn-", :line 41, :end-line 43, :hash "-1108987839"} {:id "defn-/should-produce-patrol-boat?", :kind "defn-", :line 45, :end-line 47, :hash "-1013238048"} {:id "defn-/should-produce-destroyer?", :kind "defn-", :line 49, :end-line 53, :hash "1914888961"} {:id "defn-/should-produce-fighter?", :kind "defn-", :line 55, :end-line 57, :hash "-2121852493"} {:id "defn-/decide-country-production", :kind "defn-", :line 59, :end-line 65, :hash "-1582427974"} {:id "defn-/count-carrier-producers", :kind "defn-", :line 67, :end-line 71, :hash "395669148"} {:id "defn-/carrier-producible?", :kind "defn-", :line 73, :end-line 78, :hash "1054641972"} {:id "defn-/capital-ship-needed?", :kind "defn-", :line 80, :end-line 84, :hash "-477728056"} {:id "defn-/satellite-needed?", :kind "defn-", :line 86, :end-line 88, :hash "1660586830"} {:id "defn-/decide-global-production", :kind "defn-", :line 90, :end-line 93, :hash "2073812679"} {:id "defn-/has-inland-computer-city?", :kind "defn-", :line 95, :end-line 104, :hash "-806656781"} {:id "defn-/early-patrol-boat-needed?", :kind "defn-", :line 106, :end-line 107, :hash "-659487597"} {:id "defn-/has-distinct-army-and-transport-producers?", :kind "defn-", :line 109, :end-line 122, :hash "-1504933432"} {:id "defn-/early-satellite-needed?", :kind "defn-", :line 124, :end-line 128, :hash "664799790"} {:id "defn-/decide-early-production", :kind "defn-", :line 130, :end-line 139, :hash "246567094"} {:id "defn/decide-production", :kind "defn", :line 141, :end-line 151, :hash "-1603521951"} {:id "defn/process-computer-city", :kind "defn", :line 153, :end-line 157, :hash "438810461"}]}
;; clj-mutate-manifest-end
