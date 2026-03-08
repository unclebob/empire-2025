(ns empire.config.units.dispatcher
  "Unified unit property/behavior lookup via data map + plain functions."
  (:require [empire.config.units.army :as army]
            [empire.config.units.carrier :as carrier]
            [empire.config.units.config :as cfg]
            [empire.config.units.fighter :as fighter]
            [empire.config.units.satellite :as satellite]
            [empire.config.units.ships :as ships]
            [empire.config.units.unit-metrics :as unit-metrics]
            [empire.config.units.transport :as transport]))

(def ^:private all-config
  {:army        {:speed cfg/army-speed :cost cfg/army-cost :hits cfg/army-hits
                 :display-char cfg/army-display-char :visibility-radius cfg/army-visibility-radius
                 :strength cfg/army-strength :capacity nil
                 :initial-state-fn army/initial-state
                 :can-move-to-fn army/can-move-to?
                 :needs-attention-fn army/needs-attention?}
   :fighter     {:speed cfg/fighter-speed :cost cfg/fighter-cost :hits cfg/fighter-hits
                 :display-char cfg/fighter-display-char :visibility-radius cfg/fighter-visibility-radius
                 :strength cfg/fighter-strength :capacity nil
                 :initial-state-fn fighter/initial-state
                 :can-move-to-fn fighter/can-move-to?
                 :needs-attention-fn fighter/needs-attention?}
   :satellite   {:speed cfg/satellite-speed :cost cfg/satellite-cost :hits cfg/satellite-hits
                 :display-char cfg/satellite-display-char :visibility-radius cfg/satellite-visibility-radius
                 :strength cfg/satellite-strength :capacity nil
                 :initial-state-fn satellite/initial-state
                 :can-move-to-fn satellite/can-move-to?
                 :needs-attention-fn satellite/needs-attention?}
   :transport   {:speed cfg/transport-speed :cost cfg/transport-cost :hits cfg/transport-hits
                 :display-char cfg/transport-display-char :visibility-radius cfg/transport-visibility-radius
                 :strength cfg/transport-strength :capacity cfg/transport-capacity
                 :initial-state-fn transport/initial-state
                 :can-move-to-fn transport/can-move-to?
                 :needs-attention-fn transport/needs-attention?}
   :carrier     {:speed cfg/carrier-speed :cost cfg/carrier-cost :hits cfg/carrier-hits
                 :display-char cfg/carrier-display-char :visibility-radius cfg/carrier-visibility-radius
                 :strength cfg/carrier-strength :capacity cfg/carrier-capacity
                 :initial-state-fn carrier/initial-state
                 :can-move-to-fn carrier/can-move-to?
                 :needs-attention-fn carrier/needs-attention?}
   :patrol-boat {:speed 4 :cost 15 :hits 1 :strength 1 :display-char "P" :visibility-radius 1
                 :capacity nil
                 :initial-state-fn ships/initial-state
                 :can-move-to-fn ships/can-move-to?
                 :needs-attention-fn ships/needs-attention?}
   :destroyer   {:speed 2 :cost 20 :hits 3 :strength 1 :display-char "D" :visibility-radius 1
                 :capacity nil
                 :initial-state-fn ships/initial-state
                 :can-move-to-fn ships/can-move-to?
                 :needs-attention-fn ships/needs-attention?}
   :submarine   {:speed 2 :cost 20 :hits 2 :strength 3 :display-char "S" :visibility-radius 1
                 :capacity nil
                 :initial-state-fn ships/initial-state
                 :can-move-to-fn ships/can-move-to?
                 :needs-attention-fn ships/needs-attention?}
   :battleship  {:speed 2 :cost 40 :hits 10 :strength 2 :display-char "B" :visibility-radius 1
                 :capacity nil
                 :initial-state-fn ships/initial-state
                 :can-move-to-fn ships/can-move-to?
                 :needs-attention-fn ships/needs-attention?}})

(defn speed [unit-type]
  (get-in all-config [unit-type :speed]))

(defn cost [unit-type]
  (get-in all-config [unit-type :cost]))

(defn hits [unit-type]
  (get-in all-config [unit-type :hits]))

(defn display-char [unit-type]
  (get-in all-config [unit-type :display-char]))

(defn visibility-radius [unit-type]
  (get-in all-config [unit-type :visibility-radius]))

(defn strength [unit-type]
  (get-in all-config [unit-type :strength]))

(defn capacity [unit-type]
  (get-in all-config [unit-type :capacity]))

(defn initial-state [unit-type]
  (if-let [f (get-in all-config [unit-type :initial-state-fn])]
    (f)
    {}))

(defn can-move-to? [unit-type cell]
  (let [f (get-in all-config [unit-type :can-move-to-fn])]
    (f cell)))

(defn needs-attention? [unit]
  (let [f (get-in all-config [(:type unit) :needs-attention-fn])]
    (f unit)))

(defn effective-speed [unit-type current-hits]
  (unit-metrics/effective-speed (speed unit-type) current-hits (hits unit-type)))

(defn effective-capacity [unit-type current-hits]
  (let [max-h (hits unit-type)
        cur-h (or current-hits max-h)]
    (unit-metrics/effective-capacity (capacity unit-type) cur-h max-h)))

(defn naval-unit? [unit-type]
  (unit-metrics/naval-unit? unit-type))

(defn naval-units [unit-type]
  (unit-metrics/naval-unit? unit-type))
