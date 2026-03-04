;; mutation-tested: no
(ns empire.application.runtime
  "Runtime wiring contract for application boundary contexts.")

(def ^:private impl-loaded?
  (delay
    (try
      (some? (requiring-resolve (symbol (str "empire.application.impl.runtime")
                                        (str "default-state-ctx"))))
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
