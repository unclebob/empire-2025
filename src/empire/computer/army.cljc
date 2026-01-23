(ns empire.computer.army
  "Computer army module - gutted for CommandingGeneral refactor.
   Decision logic removed; units will receive missions from CommandingGeneral."
  (:require [empire.atoms :as atoms]
            [empire.player.combat :as combat]
            [empire.computer.core :as core]))

;; All decision logic removed. Units await missions from CommandingGeneral.

(defn process-army
  "Processes a computer army's turn. Currently does nothing - awaits CommandingGeneral."
  [pos]
  nil)
