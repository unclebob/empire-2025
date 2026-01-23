(ns empire.computer
  "Computer AI coordinator - dispatches to specialized modules for unit processing.
   Gutted for CommandingGeneral refactor - units currently do nothing."
  (:require [empire.atoms :as atoms]
            [empire.computer.army :as army]
            [empire.computer.fighter :as fighter]
            [empire.computer.ship :as ship]
            [empire.computer.transport :as transport]))

;; Main dispatch function

(defn process-computer-unit
  "Processes a single computer unit's turn. Returns nil when done.
   Currently all units do nothing - awaiting CommandingGeneral implementation."
  [pos]
  (let [cell (get-in @atoms/game-map pos)
        unit (:contents cell)]
    (when (and unit (= (:owner unit) :computer))
      (case (:type unit)
        :army (army/process-army pos)
        :fighter (fighter/process-fighter pos unit)
        :transport (transport/process-transport pos)
        (:destroyer :submarine :patrol-boat :carrier :battleship)
        (ship/process-ship pos (:type unit))
        ;; Satellite - no processing needed
        nil))))
