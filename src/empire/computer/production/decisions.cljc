(ns empire.computer.production.decisions
  (:require [empire.game-mechanics.services.city-production :as city-production]
            [empire.state.api :as sa]
            [empire.config.core :as config]
            [empire.computer.early-game.strategy :as opening]
            [empire.computer.production.selection-decisions :as selection]
            [empire.computer.production.stats :as stats]
            [empire.computer.ship :as ship]
            [empire.computer.threat-response.kamikazee :as kamikazee]))


(defn country-city-producing?
  [city-pos country-id unit-type]
  (some (fn [[coords prod]]
          (and (map? prod)
               (= unit-type (:item prod))
               (not= coords city-pos)
               (let [cell (get-in (sa/read-state :computer-map) coords)]
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

(def ^:private produced-transport-cache (atom nil))

(defn clear-produced-transport-cache! [] (reset! produced-transport-cache nil))

(defn- scan-produced-transports [computer-map]
  (reduce (fn [acc column]
            (reduce (fn [inner-acc cell]
                      (let [unit (:contents cell)
                            produced-at (:produced-at unit)]
                        (if (and (= :transport (:type unit))
                                 (= :computer (:owner unit))
                                 produced-at)
                          (update inner-acc produced-at
                                  (fn [existing]
                                    (if (or (nil? existing)
                                            (> (:transport-id unit 0)
                                               (:transport-id existing 0)))
                                      unit
                                      existing)))
                          inner-acc)))
                    acc
                    column))
          {}
          computer-map))

(defn- produced-transport-at
  [city-pos]
  (let [by-city (or @produced-transport-cache
                    (let [result (scan-produced-transports (sa/read-state :computer-map))]
                      (reset! produced-transport-cache result)
                      result))]
    (get by-city city-pos)))

(defn- next-produced-transport-cycle-item
  [city-pos]
  (when-let [transport (produced-transport-at city-pos)]
    (when (#{:leave-city :sail-to-load :loading} (:transport-mission transport))
      :army)))

(defn- should-produce-transport? [city-pos country-id coastal? unit-counts]
  (when (and coastal?
             (< (get unit-counts :transport 0) config/max-transports)
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

(defn- should-produce-patrol-boat? [country-id coastal? unit-counts]
  (and coastal?
       (< (get unit-counts :patrol-boat 0) config/max-patrol-boats)
       (< (stats/count-country-patrol-boats country-id) config/max-patrol-boats-per-country)))

(defn- should-produce-destroyer? [city-pos country-id coastal? unit-counts]
  (and coastal?
       (< (get unit-counts :destroyer 0) (get unit-counts :transport 0))
       (stats/country-has-unadopted-transport? country-id)
       (not (country-city-producing-destroyers? city-pos country-id))))

(defn- should-produce-fighter? []
  (when (< (stats/count-all-computer-fighters) (stats/count-computer-cities))
    :fighter))

(defn- limit-reached?
  [unit-type unit-counts]
  (when-let [limit (get (sa/read-state :computer-production-limits) unit-type)]
    (>= (get unit-counts unit-type 0) limit)))

(defn- global-limit-reached?
  [unit-type unit-counts]
  (case unit-type
    :patrol-boat (>= (get unit-counts :patrol-boat 0) config/max-patrol-boats)
    :transport (>= (get unit-counts :transport 0) config/max-transports)
    false))

(defn- apply-production-limit
  [unit-type unit-counts]
  (if (and unit-type
           (not= :army unit-type)
           (or (limit-reached? unit-type unit-counts)
               (global-limit-reached? unit-type unit-counts)))
    :army
    unit-type))

(defn- decide-country-production
  [city-pos country-id coastal? unit-counts]
  (or (next-produced-transport-cycle-item city-pos)
      (selection/country-production-choice
       {:transport (should-produce-transport? city-pos country-id coastal? unit-counts)
        :army (when (should-produce-army? country-id) :army)
        :patrol-boat (when (should-produce-patrol-boat? country-id coastal? unit-counts) :patrol-boat)
        :destroyer (when (should-produce-destroyer? city-pos country-id coastal? unit-counts) :destroyer)
        :fighter (should-produce-fighter?)})))

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
  (selection/global-production-choice
   {:carrier (when (carrier-producible? coastal? unit-counts) :carrier)
    :capital-ship (capital-ship-needed? coastal? unit-counts)
    :satellite (when (satellite-needed? unit-counts) :satellite)}))

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
  (let [city-cell (get-in (sa/read-state :computer-map) city-pos)
        country-id (:country-id city-cell)
        coastal? (stats/city-is-coastal? city-pos)
        unit-counts (stats/count-computer-units)]
    (apply-production-limit
     (selection/production-choice
      {:override (or (kamikazee/invasion-production-override city-pos)
                     (next-produced-transport-cycle-item city-pos))
       :opening (opening-production city-pos)
       :country-choice (when country-id
                         (or (decide-country-production city-pos country-id coastal? unit-counts)
                             (decide-global-production coastal? unit-counts)))
       :global-choice nil
       :fallback-army? (not (and country-id (stats/country-army-limit-reached? country-id)))})
     unit-counts)))

(defn process-computer-city [pos]
  (let [action (selection/process-city-action
                {:reset-lake-production? (opening/should-reset-lake-production? pos)
                 :current-production (get (sa/read-state :production) pos)
                 :unit-type (decide-production pos)})]
    (when (:reset-lake-production? action)
      (sa/update-state! :production dissoc pos)
      (sa/update-world! update-in pos dissoc :opening-role))
    (when (:set-production? action)
      (city-production/set-city-production pos (:unit-type action)))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-26T20:32:27.498426-05:00", :module-hash "-785226876", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 9, :hash "-1438636642"} {:id "defn/country-city-producing?", :kind "defn", :line 12, :end-line 22, :hash "241276917"} {:id "defn/country-city-producing-armies?", :kind "defn", :line 24, :end-line 25, :hash "59819800"} {:id "defn-/country-city-producing-destroyers?", :kind "defn-", :line 27, :end-line 28, :hash "-2030098685"} {:id "defn-/should-rotate-transport?", :kind "defn-", :line 30, :end-line 32, :hash "1108334608"} {:id "def/produced-transport-cache", :kind "def", :line 34, :end-line 34, :hash "847509926"} {:id "defn/clear-produced-transport-cache!", :kind "defn", :line 36, :end-line 36, :hash "-1027610030"} {:id "defn-/scan-produced-transports", :kind "defn-", :line 38, :end-line 57, :hash "-899471304"} {:id "defn-/produced-transport-at", :kind "defn-", :line 59, :end-line 65, :hash "-1483292564"} {:id "defn-/next-produced-transport-cycle-item", :kind "defn-", :line 67, :end-line 71, :hash "-1379001009"} {:id "defn-/should-produce-transport?", :kind "defn-", :line 73, :end-line 82, :hash "1949922264"} {:id "defn-/should-produce-army?", :kind "defn-", :line 84, :end-line 86, :hash "-1108987839"} {:id "defn-/should-produce-patrol-boat?", :kind "defn-", :line 88, :end-line 91, :hash "-135241729"} {:id "defn-/should-produce-destroyer?", :kind "defn-", :line 93, :end-line 97, :hash "1914888961"} {:id "defn-/should-produce-fighter?", :kind "defn-", :line 99, :end-line 101, :hash "-2121852493"} {:id "defn-/limit-reached?", :kind "defn-", :line 103, :end-line 106, :hash "1299923427"} {:id "defn-/global-limit-reached?", :kind "defn-", :line 108, :end-line 113, :hash "322663030"} {:id "defn-/apply-production-limit", :kind "defn-", :line 115, :end-line 122, :hash "2072706306"} {:id "defn-/decide-country-production", :kind "defn-", :line 124, :end-line 132, :hash "64160051"} {:id "defn-/count-carrier-producers", :kind "defn-", :line 134, :end-line 138, :hash "395669148"} {:id "defn-/carrier-producible?", :kind "defn-", :line 140, :end-line 145, :hash "1054641972"} {:id "defn-/capital-ship-needed?", :kind "defn-", :line 147, :end-line 151, :hash "-477728056"} {:id "defn-/satellite-needed?", :kind "defn-", :line 153, :end-line 155, :hash "1660586830"} {:id "defn-/decide-global-production", :kind "defn-", :line 157, :end-line 161, :hash "-1013587949"} {:id "defn-/set-opening-role!", :kind "defn-", :line 163, :end-line 165, :hash "-755314735"} {:id "defn-/opening-production", :kind "defn-", :line 167, :end-line 174, :hash "-129578558"} {:id "defn/decide-production", :kind "defn", :line 176, :end-line 191, :hash "1154925278"} {:id "defn/process-computer-city", :kind "defn", :line 193, :end-line 202, :hash "-897245648"}]}
;; clj-mutate-manifest-end
