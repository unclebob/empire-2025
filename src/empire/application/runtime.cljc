;; mutation-tested: no
(ns empire.application.runtime
  "Runtime wiring for application boundary contexts."
  (:require [empire.adapters.state.atoms :as atom-store]
            [empire.application.ports :as ports]))

(defn default-state-ctx
  "Returns default runtime context for the application state boundary.
   Invariants are warn-only in this phase."
  []
  (let [store (atom-store/world-store)]
    {:load-world #(ports/load-world store)
     :save-world! #(ports/save-world! store %)
     :check-invariants (fn [_world] nil)}))
