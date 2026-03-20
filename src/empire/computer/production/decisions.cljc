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

(defn- limit-reached?
  [unit-type unit-counts]
  (when-let [limit (get (sa/read-state :computer-production-limits) unit-type)]
    (>= (get unit-counts unit-type 0) limit)))

(defn- apply-production-limit
  [unit-type unit-counts]
  (if (and unit-type
           (not= :army unit-type)
           (limit-reached? unit-type unit-counts))
    :army
    unit-type))

(defn- decide-country-production
  [city-pos country-id coastal? unit-counts]
  (selection/country-production-choice
   {:transport (should-produce-transport? city-pos country-id coastal?)
    :army (when (should-produce-army? country-id) :army)
    :patrol-boat (when (should-produce-patrol-boat? country-id coastal?) :patrol-boat)
    :destroyer (when (should-produce-destroyer? city-pos country-id coastal? unit-counts) :destroyer)
    :fighter (should-produce-fighter?)}))

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

(defn- has-inland-computer-city? []
  (let [game-map (sa/read-state :computer-map)]
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
  (let [city-cell (get-in (sa/read-state :computer-map) city-pos)
        country-id (:country-id city-cell)
        coastal? (stats/city-is-coastal? city-pos)
        unit-counts (stats/count-computer-units)]
    (apply-production-limit
     (selection/production-choice
      {:override (kamikazee/invasion-production-override city-pos)
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
;; {:version 1, :tested-at "2026-03-16T14:22:37.555765-05:00", :module-hash "1952701047", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 9, :hash "-105405622"} {:id "defn/country-city-producing?", :kind "defn", :line 12, :end-line 22, :hash "1067317565"} {:id "defn/country-city-producing-armies?", :kind "defn", :line 24, :end-line 25, :hash "59819800"} {:id "defn-/country-city-producing-destroyers?", :kind "defn-", :line 27, :end-line 28, :hash "-2030098685"} {:id "defn-/should-rotate-transport?", :kind "defn-", :line 30, :end-line 32, :hash "1108334608"} {:id "defn-/should-produce-transport?", :kind "defn-", :line 34, :end-line 42, :hash "493925233"} {:id "defn-/should-produce-army?", :kind "defn-", :line 44, :end-line 46, :hash "-1108987839"} {:id "defn-/should-produce-patrol-boat?", :kind "defn-", :line 48, :end-line 50, :hash "-1013238048"} {:id "defn-/should-produce-destroyer?", :kind "defn-", :line 52, :end-line 56, :hash "1914888961"} {:id "defn-/should-produce-fighter?", :kind "defn-", :line 58, :end-line 60, :hash "-2121852493"} {:id "defn-/decide-country-production", :kind "defn-", :line 62, :end-line 69, :hash "248029671"} {:id "defn-/count-carrier-producers", :kind "defn-", :line 71, :end-line 75, :hash "395669148"} {:id "defn-/carrier-producible?", :kind "defn-", :line 77, :end-line 82, :hash "1054641972"} {:id "defn-/capital-ship-needed?", :kind "defn-", :line 84, :end-line 88, :hash "-477728056"} {:id "defn-/satellite-needed?", :kind "defn-", :line 90, :end-line 92, :hash "1660586830"} {:id "defn-/decide-global-production", :kind "defn-", :line 94, :end-line 98, :hash "-1013587949"} {:id "defn-/has-inland-computer-city?", :kind "defn-", :line 100, :end-line 109, :hash "-806656781"} {:id "defn-/set-opening-role!", :kind "defn-", :line 111, :end-line 113, :hash "-755314735"} {:id "defn-/opening-production", :kind "defn-", :line 115, :end-line 122, :hash "-129578558"} {:id "defn/decide-production", :kind "defn", :line 124, :end-line 136, :hash "1476585220"} {:id "defn/process-computer-city", :kind "defn", :line 138, :end-line 147, :hash "-897245648"}]}
;; clj-mutate-manifest-end
