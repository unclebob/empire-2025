(ns empire.application.ports.acceptance-harness)

(defprotocol AcceptanceHarnessPort
  (given-world! [harness world] "Initialize world for acceptance execution.")
  (when-input! [harness input] "Apply an acceptance input action.")
  (advance-rounds! [harness rounds] "Advance simulation by rounds.")
  (query-world [harness query] "Read world/query data for assertions."))
