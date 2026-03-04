;; mutation-tested: 2026-02-26
(ns empire.units.dispatcher
  "Dispatches to the appropriate unit module based on unit type.
   Provides a unified interface for accessing unit configuration and behavior."
  (:require [empire.domain.core.unit-metrics :as unit-metrics]
            [empire.units.config :as units-config]
            [empire.units.army :as army]
            [empire.units.carrier :as carrier]
            [empire.units.fighter :as fighter]
            [empire.units.ships :as ships]
            [empire.units.satellite :as satellite]
            [empire.units.transport :as transport]))

(defmulti speed
  (fn [& _]
    :default))

(defmulti cost
  (fn [& _]
    :default))

(defmulti hits
  (fn [& _]
    :default))

(defmulti display-char
  (fn [& _]
    :default))

(defmulti visibility-radius
  (fn [& _]
    :default))

(defmulti strength
  (fn [& _]
    :default))

(defmulti initial-state
  (fn [& _]
    :default))

(defmulti can-move-to?
  (fn [& _]
    :default))

(defmulti needs-attention?
  (fn [& _]
    :default))

(defmulti effective-speed
  (fn [& _]
    :default))

(defmulti capacity
  (fn [& _]
    :default))

(defmulti effective-capacity
  (fn [& _]
    :default))

(defmulti naval-units
  (fn [& _]
    :default))

(defmulti naval-unit?
  (fn [& _]
    :default))

(def ^:private ship-types #{:patrol-boat :destroyer :submarine :battleship})

(defn- ship-type?
  [unit-type]
  (contains? ship-types unit-type))

(def ^:private non-ship-config
  {:army {:speed units-config/army-speed :cost units-config/army-cost :hits units-config/army-hits
          :display-char units-config/army-display-char :visibility-radius units-config/army-visibility-radius
          :strength units-config/army-strength}
   :fighter {:speed units-config/fighter-speed :cost units-config/fighter-cost :hits units-config/fighter-hits
             :display-char units-config/fighter-display-char :visibility-radius units-config/fighter-visibility-radius
             :strength units-config/fighter-strength}
   :satellite {:speed units-config/satellite-speed :cost units-config/satellite-cost :hits units-config/satellite-hits
               :display-char units-config/satellite-display-char :visibility-radius units-config/satellite-visibility-radius
               :strength units-config/satellite-strength}
   :transport {:speed units-config/transport-speed :cost units-config/transport-cost :hits units-config/transport-hits
               :display-char units-config/transport-display-char :visibility-radius units-config/transport-visibility-radius
               :strength units-config/transport-strength}
   :carrier {:speed units-config/carrier-speed :cost units-config/carrier-cost :hits units-config/carrier-hits
             :display-char units-config/carrier-display-char :visibility-radius units-config/carrier-visibility-radius
             :strength units-config/carrier-strength}})

(defn- unit-config
  [unit-type key]
  (if (ship-type? unit-type)
    (ships/config unit-type key)
    (get-in non-ship-config [unit-type key])))

(defmethod speed :default [unit-type]
  (unit-config unit-type :speed))

(defmethod cost :default [unit-type]
  (unit-config unit-type :cost))

(defmethod hits :default [unit-type]
  (unit-config unit-type :hits))

(defmethod display-char :default [unit-type]
  (unit-config unit-type :display-char))

(defmethod visibility-radius :default [unit-type]
  (unit-config unit-type :visibility-radius))

(defmethod strength :default [unit-type]
  (unit-config unit-type :strength))

(def ^:private initial-state-fns
  {:army army/initial-state
   :fighter fighter/initial-state
   :satellite satellite/initial-state
   :transport transport/initial-state
   :carrier carrier/initial-state})

(defmethod initial-state :default [unit-type]
  (if (ship-type? unit-type)
    (ships/initial-state)
    (if-let [f (initial-state-fns unit-type)] (f) {})))

(defmethod can-move-to? :default [unit-type cell]
  (if (ship-type? unit-type)
    (ships/can-move-to? cell)
    (case unit-type
      :army (army/can-move-to? cell)
      :fighter (fighter/can-move-to? cell)
      :satellite (satellite/can-move-to? cell)
      :transport (transport/can-move-to? cell)
      :carrier (carrier/can-move-to? cell))))

(defmethod needs-attention? :default [unit]
  (let [unit-type (:type unit)]
    (if (ship-type? unit-type)
      (ships/needs-attention? unit)
      (case unit-type
        :army (army/needs-attention? unit)
        :fighter (fighter/needs-attention? unit)
        :satellite (satellite/needs-attention? unit)
        :transport (transport/needs-attention? unit)
        :carrier (carrier/needs-attention? unit)))))

(defmethod effective-speed :default [unit-type current-hits]
  (let [base-speed (speed unit-type)
        max-hits (hits unit-type)]
    (unit-metrics/effective-speed base-speed current-hits max-hits)))

(defmethod capacity :default [unit-type]
  (case unit-type
    :transport units-config/transport-capacity
    :carrier units-config/carrier-capacity
    nil))

(defmethod effective-capacity :default [unit-type current-hits]
  (let [base-cap (capacity unit-type)
        max-h (hits unit-type)
        cur-h (or current-hits max-h)]
    (unit-metrics/effective-capacity base-cap cur-h max-h)))

(defmethod naval-units :default [unit-type]
  (unit-metrics/naval-unit? unit-type))

(defmethod naval-unit? :default [unit-type]
  (unit-metrics/naval-unit? unit-type))
