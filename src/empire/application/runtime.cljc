;; mutation-tested: no
(ns empire.application.runtime
  "Runtime wiring contract for application boundary contexts.")

(def ^:private ctx-factory (atom nil))

(defn set-ctx-factory!
  "Called by bootstrap to register the default-state-ctx factory function."
  [f]
  (reset! ctx-factory f))

(defn default-state-ctx
  "Returns default runtime context for the application state boundary."
  []
  (if-let [f @ctx-factory]
    (f)
    (throw (ex-info "Runtime context factory not initialized. Call bootstrap/initialize-default-services! first." {}))))
