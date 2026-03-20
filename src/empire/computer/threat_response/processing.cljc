(ns empire.computer.threat-response.processing
  "Threat mission execution helpers extracted from threat-response coordinator."
  (:require [empire.state.api :as sa]
            [empire.computer.fighter.movement :as fm]
            [empire.computer.shared.oscillation :as oscillation]
            [empire.computer.ship.core :as ship-core]
            [empire.computer.threat-response.processing-fighter :as fighter]
            [empire.computer.threat-response.processing-ship :as ship]
            [empire.computer.threat-response.processing-decisions :as decisions]
            [empire.config.core :as config]))

(def ^:private patrol-yield-radius 4)
(def ^:private patrol-max-invasion-distance 10)
;; Keep at least one-turn margin after reaching nearest refueling site.
(def ^:private fighter-refuel-safety-buffer 1)
(def ^:private congestion-random-walk-restore-keys
  [:threat-mission :threat-center :threat-radius :threat-rounds-left
   :major-invasion :major-invasion-target])

(defn- fighter-random-walk-step
  [pos]
  (fighter/fighter-random-walk-step pos))

(defn process-fighter-random-walk-round
  [ctx pos]
  (fighter/process-fighter-random-walk-round ctx pos))

(declare fighter-step-threat)

(defn fighter-step-threat
  [ctx pos unit]
  (fighter/fighter-step-threat ctx
                               pos
                               unit
                               fighter-refuel-safety-buffer
                               congestion-random-walk-restore-keys))

(defn- run-fighter-threat-round
  [ctx pos]
  (fighter/run-fighter-threat-round ctx pos fighter-step-threat))

(defn process-fighter-threat
  "Overrides regular fighter logic while fighter-sweep threat mission is active.
   Returns true when handled."
  [ctx pos unit]
  (fighter/process-fighter-threat ctx pos unit run-fighter-threat-round))

(defn- process-ship-random-walk
  [ctx pos]
  (ship/process-ship-random-walk ctx pos))

(defn process-ship-threat
  "Overrides regular ship logic for sea-scout and major-invasion missions.
   Returns true when handled."
  [ctx pos ship-type unit]
  (ship/process-ship-threat ctx
                            pos
                            ship-type
                            unit
                            congestion-random-walk-restore-keys
                            process-ship-random-walk))
