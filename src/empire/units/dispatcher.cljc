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

;; Configuration lookup table for non-ship units
(def ^:private non-ship-config
  {:army {:speed army/speed :cost army/cost :hits army/hits
          :display-char army/display-char :visibility-radius army/visibility-radius
          :strength army/strength}
   :fighter {:speed fighter/speed :cost fighter/cost :hits fighter/hits
             :display-char fighter/display-char :visibility-radius fighter/visibility-radius
             :strength fighter/strength}
   :satellite {:speed satellite/speed :cost satellite/cost :hits satellite/hits
               :display-char satellite/display-char :visibility-radius satellite/visibility-radius
               :strength satellite/strength}
   :transport {:speed transport/speed :cost transport/cost :hits transport/hits
               :display-char transport/display-char :visibility-radius transport/visibility-radius
               :strength transport/strength}
   :carrier {:speed carrier/speed :cost carrier/cost :hits carrier/hits
             :display-char carrier/display-char :visibility-radius carrier/visibility-radius
             :strength carrier/strength}})

(defn- unit-config [unit-type key]
  (if (ship-type? unit-type)
    (ships/config unit-type key)
    (get-in non-ship-config [unit-type key])))

;; Configuration accessors
(defn speed [unit-type] (unit-config unit-type :speed))
(defn cost [unit-type] (unit-config unit-type :cost))
(defn hits [unit-type] (unit-config unit-type :hits))
(defn display-char [unit-type] (unit-config unit-type :display-char))
(defn visibility-radius [unit-type] (unit-config unit-type :visibility-radius))
(defn strength [unit-type] (unit-config unit-type :strength))

;; Behavior accessors
(def ^:private initial-state-fns
  {:army army/initial-state :fighter fighter/initial-state
   :satellite satellite/initial-state :transport transport/initial-state
   :carrier carrier/initial-state})

(defn initial-state [unit-type]
  (if (ship-type? unit-type)
    (ships/initial-state)
    (if-let [f (initial-state-fns unit-type)] (f) {})))

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
