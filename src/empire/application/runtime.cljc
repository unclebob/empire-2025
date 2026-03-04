;; mutation-tested: no
(ns empire.application.runtime
  "Runtime wiring contract for application boundary contexts.")

(def ^:private impl-loaded?
  (delay
    (try
      (require 'empire.application.impl.runtime)
      true
      (catch #?(:clj Throwable :cljs :default) _
        false))))

(defn- ensure-impl-loaded!
  []
  (force impl-loaded?)
  nil)

(defmulti default-state-ctx
  "Returns default runtime context for the application state boundary.
   Invariants are warn-only in this phase."
  (fn [& _]
    (ensure-impl-loaded!)
    :default))
