(ns empire.application.ports.movement-execution)

(defprotocol MovementExecutionPort
  (movement-move-unit [this coords target cell current-map] "Resolve one movement step.")
  (movement-set-unit-movement [this coords target extended?] "Queue movement target for unit.")
  (movement-update-cell-visibility [this pos owner] "Recompute visibility around pos for owner.")
  (movement-update-cell-visibility-with-unit [this pos owner unit] "Recompute visibility at pos for owner using explicit unit."))
