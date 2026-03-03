;; mutation-tested: no
(ns empire.application.ports
  "Application-owned boundary contracts.
   Adapters implement these contracts so domain/application code stays decoupled
   from atoms, UI, filesystem, and runtime details.")

(defprotocol WorldStorePort
  (load-world [store] "Load current world state.")
  (save-world! [store world] "Persist world state and return it."))

(defprotocol ClockPort
  (now-ms [clock] "Current wall-clock time in milliseconds."))

(defprotocol RandomPort
  (roll [rng] "Random double in [0.0, 1.0).")
  (roll-int [rng n] "Random integer in [0, n)."))

(defprotocol PersistencePort
  (list-saves [persistence dir-path] "List save files from dir-path newest-first.")
  (save-state! [persistence dir-path data] "Save data to dir-path and return filename.")
  (load-state [persistence dir-path filename] "Load and return state data map."))

(defprotocol MajorInvasionStorePort
  (load-major-invasion-state [store] "Load major invasion coordinator state.")
  (save-major-invasion-state! [store state] "Persist major invasion state and return it."))

(defprotocol RuntimeStatePort
  (read-runtime-state [store k] "Read runtime state by key.")
  (write-runtime-state! [store k v] "Write runtime state by key and return value."))

(defprotocol AcceptanceHarnessPort
  (given-world! [harness world] "Initialize world for acceptance execution.")
  (when-input! [harness input] "Apply an acceptance input action.")
  (advance-rounds! [harness rounds] "Advance simulation by rounds.")
  (query-world [harness query] "Read world/query data for assertions."))

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
