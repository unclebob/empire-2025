;; mutation-tested: no
(ns empire.application.runtime
  "Runtime wiring for application boundary contexts."
  (:require [empire.application.ports :as ports]))

(def ^:private world-store-fn
  (delay
    (try
      (requiring-resolve 'empire.adapters.state.atoms/world-store)
      (catch #?(:clj Throwable :cljs :default) _
        nil))))

(def ^:private runtime-store-fn
  (delay
    (try
      (requiring-resolve 'empire.adapters.state.runtime/runtime-state-store)
      (catch #?(:clj Throwable :cljs :default) _
        nil))))

(defn default-state-ctx
  "Returns default runtime context for the application state boundary.
   Invariants are warn-only in this phase."
  []
  (let [store (when-let [f @world-store-fn] (f))
        rt-store (when-let [f @runtime-store-fn] (f))]
    {:load-world #(ports/load-world store)
     :save-world! #(ports/save-world! store %)
     :load-major-invasion-state #(ports/load-major-invasion-state rt-store)
     :save-major-invasion-state! #(ports/save-major-invasion-state! rt-store %)
     :read-runtime-state #(ports/read-runtime-state rt-store %)
     :write-runtime-state! #(ports/write-runtime-state! rt-store %1 %2)
     :check-invariants (fn [_world] nil)}))
