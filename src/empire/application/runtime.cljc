;; mutation-tested: no
(ns empire.application.runtime
  "Runtime wiring contract for application boundary contexts.")

(defmulti default-state-ctx
  "Returns default runtime context for the application state boundary.
   Invariants are warn-only in this phase."
  (fn [& _] :default))
