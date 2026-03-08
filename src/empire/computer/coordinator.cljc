(ns empire.computer.coordinator
  "Computer AI coordinator - dispatches to specialized modules for unit processing."
  (:require [empire.state.api :as sa]
            [empire.computer.army :as army]
            [empire.computer.fighter :as fighter]
            [empire.computer.ship :as ship]
            [empire.computer.transport :as transport]))

(defn- computer-unit? [unit]
  (and unit (= (:owner unit) :computer)))

(defn- dispatch-unit [pos unit]
  (case (:type unit)
    :army (army/process-army pos)
    :fighter (fighter/process-fighter pos unit)
    :transport (transport/process-transport pos)
    (:destroyer :submarine :patrol-boat :carrier :battleship)
    (ship/process-ship pos (:type unit))
    nil))

(defn process-computer-unit
  "Processes a single computer unit's turn."
  [pos]
  (let [unit (:contents (get-in (sa/current-world) pos))]
    (when (computer-unit? unit)
      (dispatch-unit pos unit))))
