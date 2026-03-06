(ns empire.ui.util.input.actions.helpers
  (:require [empire.state.api :as sa]
            [empire.game.loop.core :as game-loop]))

(defn set-error-message!
  [msg ms]
  (sa/write-state! :error-message msg)
  (sa/write-state! :error-until (+ (System/currentTimeMillis) ms)))

(defn item-processed!
  []
  (game-loop/item-processed))
