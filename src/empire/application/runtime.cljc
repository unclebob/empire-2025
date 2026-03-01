;; mutation-tested: no
(ns empire.application.runtime
  "Runtime wiring for application boundary contexts."
  (:require [empire.adapters.state.atoms :as atom-store]
            [empire.adapters.state.runtime :as runtime-store]
            [empire.application.ports :as ports]))

(defn default-state-ctx
  "Returns default runtime context for the application state boundary.
   Invariants are warn-only in this phase."
  []
  (let [store (atom-store/world-store)
        rt-store (runtime-store/runtime-state-store)]
    {:load-world #(ports/load-world store)
     :save-world! #(ports/save-world! store %)
     :load-major-invasion-state #(ports/load-major-invasion-state rt-store)
     :save-major-invasion-state! #(ports/save-major-invasion-state! rt-store %)
     :read-runtime-state #(ports/read-runtime-state rt-store %)
     :write-runtime-state! #(ports/write-runtime-state! rt-store %1 %2)
     :check-invariants (fn [_world] nil)}))
