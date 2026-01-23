(ns empire.computer.fighter
  "Computer fighter module - gutted for CommandingGeneral refactor.
   Decision logic removed; units will receive missions from CommandingGeneral."
  (:require [empire.atoms :as atoms]
            [empire.player.combat :as combat]
            [empire.computer.core :as core]))

;; All decision logic removed. Units await missions from CommandingGeneral.

(defn process-fighter
  "Processes a computer fighter's turn. Currently does nothing - awaits CommandingGeneral."
  [pos unit]
  nil)
