(ns empire.ui.util.input.actions.helpers
  (:require [empire.application.state-access :as sa]
            [empire.game-loop :as game-loop]))

(defn unit-state-port []
  (or (:unit-state-port (sa/state-ctx))
      (throw (ex-info "Unit state port not configured in runtime state context" {}))))

(defn execution-port []
  (or (:execution-port (sa/state-ctx))
      (throw (ex-info "Execution port not configured in runtime state context" {}))))

(defn set-error-message!
  [msg ms]
  (sa/write-state! :error-message msg)
  (sa/write-state! :error-until (+ (System/currentTimeMillis) ms)))

(defn item-processed!
  []
  (game-loop/item-processed))
