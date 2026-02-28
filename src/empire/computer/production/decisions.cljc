;; mutation-tested: 2026-02-28
(ns empire.computer.production.decisions
  (:require [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.computer.production.stats :as stats]
            [empire.player.production :as player-production]
            [empire.computer.ship :as ship]))

(defn country-city-producing?
  [city-pos country-id unit-type]
  (some (fn [[coords prod]]
          (and (map? prod)
               (= unit-type (:item prod))
               (not= coords city-pos)
               (let [cell (get-in @atoms/game-map coords)]
                 (and (= :city (:type cell))
                      (= :computer (:city-status cell))
                      (= country-id (:country-id cell))))))
        @atoms/production))

(defn country-city-producing-armies? [city-pos country-id]
  (country-city-producing? city-pos country-id :army))

(defn- country-city-producing-destroyers? [city-pos country-id]
  (country-city-producing? city-pos country-id :destroyer))

(defn- should-rotate-transport? [city-pos country-id]
  (and (= city-pos (get @atoms/last-transport-city country-id))
       (stats/country-has-other-coastal-city? city-pos country-id)))

(defn- should-produce-transport? [city-pos country-id coastal?]
  (when (and coastal?
             (>= (stats/count-country-armies country-id) config/armies-before-transport)
             (stats/country-has-waiting-armies? country-id)
             (not (should-rotate-transport? city-pos country-id)))
    (swap! atoms/last-transport-city assoc country-id city-pos)
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

(defn decide-country-production
  [city-pos country-id coastal? unit-counts]
  (or (should-produce-transport? city-pos country-id coastal?)
      (when (should-produce-army? country-id) :army)
      (when (should-produce-patrol-boat? country-id coastal?) :patrol-boat)
      (when (should-produce-destroyer? city-pos country-id coastal? unit-counts) :destroyer)
      (should-produce-fighter?)))

(defn count-carrier-producers []
  (count (filter (fn [[_coords prod]]
                   (and (map? prod)
                        (= :carrier (:item prod))))
                 @atoms/production)))

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

(defn decide-global-production [coastal? unit-counts]
  (or (when (carrier-producible? coastal? unit-counts) :carrier)
      (capital-ship-needed? coastal? unit-counts)
      (when (satellite-needed? unit-counts) :satellite)))

(defn- has-inland-computer-city? []
  (some (fn [i]
          (some (fn [j]
                  (let [cell (get-in @atoms/game-map [i j])]
                    (and (= :city (:type cell))
                         (= :computer (:city-status cell))
                         (not (stats/city-is-coastal? [i j])))))
                (range (count (first @atoms/game-map)))))
        (range (count @atoms/game-map))))

(defn- early-patrol-boat-needed? [coastal?]
  (and coastal? (not @atoms/early-patrol-boat-produced?)))

(defn- early-satellite-needed? [coastal?]
  (and @atoms/early-patrol-boat-produced?
       (not @atoms/early-satellite-produced?)
       (or (not coastal?) (not (has-inland-computer-city?)))))

(defn decide-early-production [city-pos coastal?]
  (when @atoms/transport-fully-loaded?
    (cond
      (early-patrol-boat-needed? coastal?)
      (do (reset! atoms/early-patrol-boat-produced? true)
          :patrol-boat)

      (early-satellite-needed? coastal?)
      (do (reset! atoms/early-satellite-produced? true)
          :satellite))))

(defn decide-production [city-pos]
  (let [city-cell (get-in @atoms/game-map city-pos)
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
  (let [current-production (get @atoms/production pos)]
    (when (nil? current-production)
      (when-let [unit-type (decide-production pos)]
        (player-production/set-city-production pos unit-type)))))
