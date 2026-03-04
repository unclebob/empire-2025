;; mutation-tested: no
(ns empire.domain.services.impl.threat-policy
  (:require [empire.domain.services.threat-policy :as threat-policy]))

(defn- player-unit-type
  [game-cell]
  (let [unit (:contents game-cell)]
    (when (= :player (:owner unit))
      (:type unit))))

(defn- player-city?
  [game-cell]
  (and (= :city (:type game-cell))
       (= :player (:city-status game-cell))))

(def ^:private enemy-ship-types
  #{:patrol-boat :destroyer :submarine :transport :carrier :battleship})

(defmethod threat-policy/fighter-response-count :default
  []
  4)

(defmethod threat-policy/ship-response-count :default
  []
  2)

(defmethod threat-policy/fighter-sweep-rounds :default
  []
  10)

(defmethod threat-policy/ship-scout-rounds :default
  []
  10)

(defmethod threat-policy/threat-radius :default
  []
  5)

(defmethod threat-policy/detection-trigger :default
  [game-cell]
  (let [unit-type (player-unit-type game-cell)]
    (cond
      (= :fighter unit-type) :fighter-detected
      (enemy-ship-types unit-type) :ship-detected
      (= :army unit-type) :major-invasion-trigger
      (player-city? game-cell) :major-invasion-trigger
      :else nil)))

(defmethod threat-policy/fighter-sweep-mission :default
  [center]
  {:threat-mission :fighter-sweep
   :threat-center center
   :threat-radius (threat-policy/threat-radius)
   :threat-rounds-left (threat-policy/fighter-sweep-rounds)})

(defmethod threat-policy/sea-scout-mission :default
  [center]
  {:threat-mission :sea-scout
   :threat-center center
   :threat-radius (threat-policy/threat-radius)
   :threat-rounds-left (threat-policy/ship-scout-rounds)})

(defmethod threat-policy/dec-threat-rounds :default
  [unit]
  (if-let [left (:threat-rounds-left unit)]
    (let [next-left (dec left)]
      (if (pos? next-left)
        (assoc unit :threat-rounds-left next-left)
        (dissoc unit :threat-mission :threat-center :threat-radius :threat-rounds-left)))
    unit))
