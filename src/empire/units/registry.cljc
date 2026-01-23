(ns empire.units.registry
  "Loads all unit modules to register their multimethod implementations.
   Require this namespace to ensure all unit types are available."
  (:require [empire.units.dispatcher]
            [empire.units.army]
            [empire.units.fighter]
            [empire.units.satellite]
            [empire.units.transport]
            [empire.units.carrier]
            [empire.units.patrol-boat]
            [empire.units.destroyer]
            [empire.units.submarine]
            [empire.units.battleship]))

;; All unit modules loaded - their defmethod registrations are now active.
