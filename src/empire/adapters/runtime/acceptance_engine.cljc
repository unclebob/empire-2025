;; mutation-tested: no
(ns empire.adapters.runtime.acceptance-engine
  "Runtime adapter functions used by the application acceptance harness."
  (:require [empire.game-loop.core :as game-loop]
            [empire.test-utils :as test-utils]
            [empire.ui.util.input.dispatch :as input-dispatch]))

(defn reset-runtime!
  []
  (test-utils/reset-all-atoms!))

(defn handle-input!
  [in]
  (input-dispatch/handle-key in))

(defn start-round!
  []
  (game-loop/start-new-round))
