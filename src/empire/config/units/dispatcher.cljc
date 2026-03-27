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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T00:43:54.78993-05:00", :module-hash "139854211", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 10, :hash "-1418690521"} {:id "def/all-config", :kind "def", :line 12, :end-line 62, :hash "1381769171"} {:id "defn/speed", :kind "defn", :line 64, :end-line 65, :hash "-798565689"} {:id "defn/cost", :kind "defn", :line 67, :end-line 68, :hash "1191851411"} {:id "defn/hits", :kind "defn", :line 70, :end-line 71, :hash "-1864399518"} {:id "defn/display-char", :kind "defn", :line 73, :end-line 74, :hash "1878064206"} {:id "defn/visibility-radius", :kind "defn", :line 76, :end-line 77, :hash "1180808113"} {:id "defn/strength", :kind "defn", :line 79, :end-line 80, :hash "1223272414"} {:id "defn/capacity", :kind "defn", :line 82, :end-line 83, :hash "2096295950"} {:id "defn/initial-state", :kind "defn", :line 85, :end-line 88, :hash "-1859744669"} {:id "defn/can-move-to?", :kind "defn", :line 90, :end-line 92, :hash "400843408"} {:id "defn/needs-attention?", :kind "defn", :line 94, :end-line 96, :hash "162848047"} {:id "defn/effective-speed", :kind "defn", :line 98, :end-line 99, :hash "786106594"} {:id "defn/effective-capacity", :kind "defn", :line 101, :end-line 104, :hash "-830718604"} {:id "defn/naval-unit?", :kind "defn", :line 106, :end-line 107, :hash "-1836024285"} {:id "defn/naval-units", :kind "defn", :line 109, :end-line 110, :hash "-1796906852"}]}
;; clj-mutate-manifest-end
