(ns empire.computer.transport
  "Computer transport module - gutted for CommandingGeneral refactor.
   Decision logic removed; units will receive missions from CommandingGeneral."
  (:require [empire.atoms :as atoms]
            [empire.player.combat :as combat]
            [empire.computer.core :as core]))

;; All decision logic removed. Units await missions from CommandingGeneral.

(defn process-transport
  "Processes a transport unit. Currently does nothing - awaits CommandingGeneral."
  [pos]
  nil)
