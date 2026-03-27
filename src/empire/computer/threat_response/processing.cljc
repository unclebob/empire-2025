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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T00:00:35.33468-05:00", :module-hash "1231476612", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 10, :hash "-846188085"} {:id "def/patrol-yield-radius", :kind "def", :line 12, :end-line 12, :hash "-2055166791"} {:id "def/patrol-max-invasion-distance", :kind "def", :line 13, :end-line 13, :hash "-1594233431"} {:id "def/fighter-refuel-safety-buffer", :kind "def", :line 15, :end-line 15, :hash "341204353"} {:id "def/congestion-random-walk-restore-keys", :kind "def", :line 16, :end-line 18, :hash "-695224494"} {:id "defn-/fighter-random-walk-step", :kind "defn-", :line 20, :end-line 22, :hash "-46805628"} {:id "defn/process-fighter-random-walk-round", :kind "defn", :line 24, :end-line 26, :hash "-727811176"} {:id "form/7/declare", :kind "declare", :line 28, :end-line 28, :hash "-165607427"} {:id "defn/fighter-step-threat", :kind "defn", :line 30, :end-line 36, :hash "1821382491"} {:id "defn-/run-fighter-threat-round", :kind "defn-", :line 38, :end-line 40, :hash "1577031249"} {:id "defn/process-fighter-threat", :kind "defn", :line 42, :end-line 46, :hash "1922373362"} {:id "defn-/process-ship-random-walk", :kind "defn-", :line 48, :end-line 50, :hash "-1347167663"} {:id "defn/process-ship-threat", :kind "defn", :line 52, :end-line 61, :hash "-810417206"}]}
;; clj-mutate-manifest-end
