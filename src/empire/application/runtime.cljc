;; mutation-tested: no
(ns empire.application.runtime
  "Runtime wiring for application boundary contexts."
  (:require [empire.application.ports.runtime-state :as runtime-ports]
            [empire.application.ports.world-store :as world-ports]))

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

(def ^:private movement-port-fn
  (delay
    (try
      (requiring-resolve 'empire.movement.adapter/movement-port)
      (catch #?(:clj Throwable :cljs :default) _
        nil))))

(defn default-state-ctx
  "Returns default runtime context for the application state boundary.
   Invariants are warn-only in this phase."
  []
  (let [store (when-let [f @world-store-fn] (f))
        rt-store (when-let [f @runtime-store-fn] (f))
        movement-port (when-let [f @movement-port-fn] (f))]
    {:world-store store
     :load-world #(world-ports/load-world store)
     :save-world! #(world-ports/save-world! store %)
     :load-major-invasion-state #(runtime-ports/load-major-invasion-state rt-store)
     :save-major-invasion-state! #(runtime-ports/save-major-invasion-state! rt-store %)
     :read-runtime-state #(runtime-ports/read-runtime-state rt-store %)
     :write-runtime-state! #(runtime-ports/write-runtime-state! rt-store %1 %2)
     :on-same-continent? #(runtime-ports/on-same-continent? rt-store %1 %2)
     :merge-continents! #(runtime-ports/merge-continents! rt-store %1 %2)
     :rebuild-refueling-caches! #(runtime-ports/rebuild-refueling-caches! rt-store)
     :movement-port movement-port
     :check-invariants (fn [_world] nil)}))
