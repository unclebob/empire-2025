;; mutation-tested: no
(ns empire.application.runtime
  "Runtime wiring contract for application boundary contexts.")

(defonce ^:private impl-loaded?
  (atom false))

(defn- ensure-impl-loaded!
  []
  (when-not @impl-loaded?
    (try
      (require 'empire.application.impl.runtime)
      (reset! impl-loaded? true)
      (catch #?(:clj Throwable :cljs :default) _
        nil)))
  nil)

(defmulti default-state-ctx
  "Returns default runtime context for the application state boundary.
   Invariants are warn-only in this phase."
  (fn [& _]
    (ensure-impl-loaded!)
    :default))
