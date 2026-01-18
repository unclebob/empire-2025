(ns empire.units.dispatcher
  "Dispatches to the appropriate unit module based on unit type.
   Provides a unified interface for accessing unit configuration and behavior."
  (:require [empire.units.army :as army]
            [empire.units.fighter :as fighter]
            [empire.units.satellite :as satellite]
            [empire.units.transport :as transport]
            [empire.units.carrier :as carrier]
            [empire.units.patrol-boat :as patrol-boat]
            [empire.units.destroyer :as destroyer]
            [empire.units.submarine :as submarine]
            [empire.units.battleship :as battleship]))

;; Unit type to module mapping
(def unit-modules
  {:army army/initial-state
   :fighter fighter/initial-state
   :satellite satellite/initial-state
   :transport transport/initial-state
   :carrier carrier/initial-state
   :patrol-boat patrol-boat/initial-state
   :destroyer destroyer/initial-state
   :submarine submarine/initial-state
   :battleship battleship/initial-state})

;; Configuration accessors
(defn speed [unit-type]
  (case unit-type
    :army army/speed
    :fighter fighter/speed
    :satellite satellite/speed
    :transport transport/speed
    :carrier carrier/speed
    :patrol-boat patrol-boat/speed
    :destroyer destroyer/speed
    :submarine submarine/speed
    :battleship battleship/speed
    nil))

(defn cost [unit-type]
  (case unit-type
    :army army/cost
    :fighter fighter/cost
    :satellite satellite/cost
    :transport transport/cost
    :carrier carrier/cost
    :patrol-boat patrol-boat/cost
    :destroyer destroyer/cost
    :submarine submarine/cost
    :battleship battleship/cost
    nil))

(defn hits [unit-type]
  (case unit-type
    :army army/hits
    :fighter fighter/hits
    :satellite satellite/hits
    :transport transport/hits
    :carrier carrier/hits
    :patrol-boat patrol-boat/hits
    :destroyer destroyer/hits
    :submarine submarine/hits
    :battleship battleship/hits
    nil))

(defn display-char [unit-type]
  (case unit-type
    :army army/display-char
    :fighter fighter/display-char
    :satellite satellite/display-char
    :transport transport/display-char
    :carrier carrier/display-char
    :patrol-boat patrol-boat/display-char
    :destroyer destroyer/display-char
    :submarine submarine/display-char
    :battleship battleship/display-char
    nil))

(defn visibility-radius [unit-type]
  (case unit-type
    :army army/visibility-radius
    :fighter fighter/visibility-radius
    :satellite satellite/visibility-radius
    :transport transport/visibility-radius
    :carrier carrier/visibility-radius
    :patrol-boat patrol-boat/visibility-radius
    :destroyer destroyer/visibility-radius
    :submarine submarine/visibility-radius
    :battleship battleship/visibility-radius
    nil))

(defn strength [unit-type]
  (case unit-type
    :army army/strength
    :fighter fighter/strength
    :satellite satellite/strength
    :transport transport/strength
    :carrier carrier/strength
    :patrol-boat patrol-boat/strength
    :destroyer destroyer/strength
    :submarine submarine/strength
    :battleship battleship/strength
    nil))

;; Behavior accessors
(defn initial-state [unit-type]
  (case unit-type
    :army (army/initial-state)
    :fighter (fighter/initial-state)
    :satellite (satellite/initial-state)
    :transport (transport/initial-state)
    :carrier (carrier/initial-state)
    :patrol-boat (patrol-boat/initial-state)
    :destroyer (destroyer/initial-state)
    :submarine (submarine/initial-state)
    :battleship (battleship/initial-state)
    {}))

(defn can-move-to? [unit-type cell]
  (case unit-type
    :army (army/can-move-to? cell)
    :fighter (fighter/can-move-to? cell)
    :satellite (satellite/can-move-to? cell)
    :transport (transport/can-move-to? cell)
    :carrier (carrier/can-move-to? cell)
    :patrol-boat (patrol-boat/can-move-to? cell)
    :destroyer (destroyer/can-move-to? cell)
    :submarine (submarine/can-move-to? cell)
    :battleship (battleship/can-move-to? cell)
    false))

(defn needs-attention? [unit]
  (let [unit-type (:type unit)]
    (case unit-type
      :army (army/needs-attention? unit)
      :fighter (fighter/needs-attention? unit)
      :satellite (satellite/needs-attention? unit)
      :transport (transport/needs-attention? unit)
      :carrier (carrier/needs-attention? unit)
      :patrol-boat (patrol-boat/needs-attention? unit)
      :destroyer (destroyer/needs-attention? unit)
      :submarine (submarine/needs-attention? unit)
      :battleship (battleship/needs-attention? unit)
      false)))

;; Naval unit check
(def naval-units #{:transport :patrol-boat :destroyer :submarine :carrier :battleship})

(defn naval-unit? [unit-type]
  (contains? naval-units unit-type))
