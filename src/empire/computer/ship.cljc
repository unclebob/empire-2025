(ns empire.computer.ship
  "Computer ship module - gutted for CommandingGeneral refactor.
   Decision logic removed; units will receive missions from CommandingGeneral."
  (:require [empire.atoms :as atoms]
            [empire.player.combat :as combat]
            [empire.computer.core :as core]
            [empire.computer.threat :as threat]))

;; All decision logic removed. Units await missions from CommandingGeneral.

(defn process-ship
  "Processes a computer ship's turn. Currently does nothing - awaits CommandingGeneral."
  [pos ship-type]
  nil)
