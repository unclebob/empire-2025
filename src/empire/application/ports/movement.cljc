(ns empire.application.ports.movement)

(defprotocol MovementPort
  (movement-move-unit [movement coords target cell current-map] "Resolve one movement step.")
  (movement-get-active-unit [movement cell] "Get active unit in a cell for attention/input.")
  (movement-is-army-aboard-transport? [movement active-unit] "True if active unit is an army aboard transport.")
  (movement-is-fighter-from-airport? [movement active-unit] "True if active unit is airport fighter.")
  (movement-is-fighter-from-carrier? [movement active-unit] "True if active unit is carrier fighter.")
  (movement-context [movement cell active-unit] "Determine movement context for active unit.")
  (movement-set-unit-mode [movement coords mode] "Set unit mode at coords.")
  (movement-add-unit-at [movement coords unit-type owner] "Add a unit at coords for owner.")
  (movement-wake-at [movement coords] "Wake city/unit at coords.")
  (movement-set-unit-movement [movement coords target extended?] "Queue movement target for unit."))
