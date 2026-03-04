;; mutation-tested: no
(ns empire.application.acceptance-harness
  "Transitional acceptance harness.
   Generated acceptance specs can target this boundary API while application
   internals evolve."
  (:require [empire.application.ports.acceptance-harness :as ports]
            [empire.application.runtime :as app-runtime]
            [empire.application.state :as app-state]))

(defn- resolve-acceptance-fn
  [sym]
  (or (try
        (requiring-resolve (symbol "empire.adapters.runtime.acceptance-engine" (name sym)))
        (catch #?(:clj Throwable :cljs :default) _
          nil))
      (throw (ex-info (str "Unable to resolve acceptance runtime function: " (name sym))
                      {:symbol sym}))))

(defonce ^:private state-ctx
  (delay
    (merge (app-runtime/default-state-ctx)
           {:reset-runtime! (resolve-acceptance-fn 'reset-runtime!)
            :handle-input! (resolve-acceptance-fn 'handle-input!)
            :start-round! (resolve-acceptance-fn 'start-round!)})))

(defn- current-world []
  ((:load-world @state-ctx)))

(defn- require-ctx-fn
  [k]
  (let [f (get @state-ctx k)]
    (when-not (fn? f)
      (throw (ex-info (str "Missing runtime function in acceptance harness context: " (name k))
                      {:missing k
                       :provided (->> (keys @state-ctx) sort vec)})))
    f))

(defrecord AppAcceptanceHarness []
  ports/AcceptanceHarnessPort
  (given-world! [_ world]
    ((require-ctx-fn :reset-runtime!))
    (app-state/set-world! @state-ctx world)
    world)
  (when-input! [_ in]
    ((require-ctx-fn :handle-input!) in))
  (advance-rounds! [_ rounds]
    (dotimes [_ rounds]
      ((require-ctx-fn :start-round!)))
    (current-world))
  (query-world [_ query]
    (if (fn? query)
      (query (current-world))
      (current-world))))

(defn make-harness
  []
  (->AppAcceptanceHarness))
