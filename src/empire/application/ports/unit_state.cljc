(ns empire.application.ports.unit-state)

(defprotocol UnitStatePort
  (movement-get-active-unit [this cell] "Get active unit in a cell for attention/input.")
  (movement-is-army-aboard-transport? [this active-unit] "True if active unit is an army aboard transport.")
  (movement-is-fighter-from-airport? [this active-unit] "True if active unit is airport fighter.")
  (movement-is-fighter-from-carrier? [this active-unit] "True if active unit is carrier fighter.")
  (movement-context [this cell active-unit] "Determine movement context for active unit.")
  (movement-set-unit-mode [this coords mode] "Set unit mode at coords.")
  (movement-add-unit-at [this coords unit-type owner] "Add a unit at coords for owner.")
  (movement-wake-at [this coords] "Wake city/unit at coords."))
