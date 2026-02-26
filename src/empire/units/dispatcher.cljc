;; mutation-tested: 2026-02-26
(ns empire.units.dispatcher
  "Dispatches to the appropriate unit module based on unit type.
   Provides a unified interface for accessing unit configuration and behavior."
  (:require [empire.units.army :as army]
            [empire.units.fighter :as fighter]
            [empire.units.satellite :as satellite]
            [empire.units.transport :as transport]
            [empire.units.carrier :as carrier]
            [empire.units.ships :as ships]))

(def ^:private ship-types #{:patrol-boat :destroyer :submarine :battleship})

(defn- ship-type? [unit-type]
  (contains? ship-types unit-type))

;; Configuration accessors
(defn speed [unit-type]
  (if (ship-type? unit-type)
    (ships/config unit-type :speed)
    (case unit-type
      :army army/speed
      :fighter fighter/speed
      :satellite satellite/speed
      :transport transport/speed
      :carrier carrier/speed
      nil)))

(defn cost [unit-type]
  (if (ship-type? unit-type)
    (ships/config unit-type :cost)
    (case unit-type
      :army army/cost
      :fighter fighter/cost
      :satellite satellite/cost
      :transport transport/cost
      :carrier carrier/cost
      nil)))

(defn hits [unit-type]
  (if (ship-type? unit-type)
    (ships/config unit-type :hits)
    (case unit-type
      :army army/hits
      :fighter fighter/hits
      :satellite satellite/hits
      :transport transport/hits
      :carrier carrier/hits
      nil)))

(defn display-char [unit-type]
  (if (ship-type? unit-type)
    (ships/config unit-type :display-char)
    (case unit-type
      :army army/display-char
      :fighter fighter/display-char
      :satellite satellite/display-char
      :transport transport/display-char
      :carrier carrier/display-char
      nil)))

(defn visibility-radius [unit-type]
  (if (ship-type? unit-type)
    (ships/config unit-type :visibility-radius)
    (case unit-type
      :army army/visibility-radius
      :fighter fighter/visibility-radius
      :satellite satellite/visibility-radius
      :transport transport/visibility-radius
      :carrier carrier/visibility-radius
      nil)))

(defn strength [unit-type]
  (if (ship-type? unit-type)
    (ships/config unit-type :strength)
    (case unit-type
      :army army/strength
      :fighter fighter/strength
      :satellite satellite/strength
      :transport transport/strength
      :carrier carrier/strength
      nil)))

;; Behavior accessors
(defn initial-state [unit-type]
  (if (ship-type? unit-type)
    (ships/initial-state)
    (case unit-type
      :army (army/initial-state)
      :fighter (fighter/initial-state)
      :satellite (satellite/initial-state)
      :transport (transport/initial-state)
      :carrier (carrier/initial-state)
      {})))

(defn can-move-to? [unit-type cell]
  (if (ship-type? unit-type)
    (ships/can-move-to? cell)
    (case unit-type
      :army (army/can-move-to? cell)
      :fighter (fighter/can-move-to? cell)
      :satellite (satellite/can-move-to? cell)
      :transport (transport/can-move-to? cell)
      :carrier (carrier/can-move-to? cell))))

(defn needs-attention? [unit]
  (let [unit-type (:type unit)]
    (if (ship-type? unit-type)
      (ships/needs-attention? unit)
      (case unit-type
        :army (army/needs-attention? unit)
        :fighter (fighter/needs-attention? unit)
        :satellite (satellite/needs-attention? unit)
        :transport (transport/needs-attention? unit)
        :carrier (carrier/needs-attention? unit)))))

(defn effective-speed
  "Calculates movement speed scaled by remaining hits (VMS ceiling division).
   Units with 1 max hit always return base speed."
  [unit-type current-hits]
  (let [base-speed (speed unit-type)
        max-hits (hits unit-type)]
    (quot (+ (* base-speed current-hits) (dec max-hits)) max-hits)))

(defn capacity
  "Returns the base cargo capacity for container unit types."
  [unit-type]
  (case unit-type
    :transport transport/capacity
    :carrier carrier/capacity
    nil))

(defn effective-capacity
  "Calculates cargo capacity scaled by remaining hits (VMS ceiling division).
   Defaults to max hits if current-hits is nil."
  [unit-type current-hits]
  (let [base-cap (capacity unit-type)
        max-h (hits unit-type)
        cur-h (or current-hits max-h)]
    (quot (+ (* base-cap cur-h) (dec max-h)) max-h)))

;; Naval unit check
(def naval-units #{:transport :patrol-boat :destroyer :submarine :carrier :battleship})

(defn naval-unit? [unit-type]
  (contains? naval-units unit-type))
