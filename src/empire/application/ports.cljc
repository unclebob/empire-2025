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

(defprotocol AcceptanceHarnessPort
  (given-world! [harness world] "Initialize world for acceptance execution.")
  (when-input! [harness input] "Apply an acceptance input action.")
  (advance-rounds! [harness rounds] "Advance simulation by rounds.")
  (query-world [harness query] "Read world/query data for assertions."))
