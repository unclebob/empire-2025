;; mutation-tested: no
(ns empire.application.acceptance-harness
  "Transitional acceptance harness.
   Generated acceptance specs can target this boundary API while application
   internals evolve."
  (:require [empire.adapters.runtime.acceptance-engine :as acceptance-engine]
            [empire.application.ports :as ports]
            [empire.application.runtime :as app-runtime]
            [empire.application.state :as app-state]))

(defonce ^:private state-ctx
  (delay
    (merge (app-runtime/default-state-ctx)
           {:reset-runtime! acceptance-engine/reset-runtime!
            :handle-input! acceptance-engine/handle-input!
            :start-round! acceptance-engine/start-round!})))

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
