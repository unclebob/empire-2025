;; mutation-tested: no
(ns empire.application.acceptance-harness
  "Transitional acceptance harness.
   Generated acceptance specs can target this boundary API while application
   internals evolve."
  (:require [empire.application.ports :as ports]
            [empire.application.runtime :as app-runtime]
            [empire.application.state :as app-state]
            [empire.game-loop :as game-loop]
            [empire.ui.util.input.dispatch :as input]
            [empire.test-utils :as test-utils]))

(defonce ^:private state-ctx (delay (app-runtime/default-state-ctx)))

(defn- current-world []
  ((:load-world @state-ctx)))

(defrecord AppAcceptanceHarness []
  ports/AcceptanceHarnessPort
  (given-world! [_ world]
    (test-utils/reset-all-atoms!)
    (app-state/set-world! @state-ctx world)
    world)
  (when-input! [_ in]
    (input/handle-key in))
  (advance-rounds! [_ rounds]
    (dotimes [_ rounds]
      (game-loop/start-new-round))
    (current-world))
  (query-world [_ query]
    (if (fn? query)
      (query (current-world))
      (current-world))))

(defn make-harness
  []
  (->AppAcceptanceHarness))
