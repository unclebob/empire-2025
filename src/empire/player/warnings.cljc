(ns empire.player.warnings
  (:require [empire.sound :as sound]
            [empire.state.api :as sa]))

(defn set-warning-message!
  [msg]
  (sa/write-state! :warning-message msg)
  (sound/play-bonk!))
