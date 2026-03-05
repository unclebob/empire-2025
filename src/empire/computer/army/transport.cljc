;; mutation-tested: 2026-03-02
(ns empire.computer.army.transport
  "Army transport boarding behavior."
  (:require [empire.application.state-access :as sa]
            [empire.computer.army.movement :as movement]
            [empire.computer.core :as core]
            [empire.debug :as debug]
            [empire.computer.movement :as computer-movement]))

(defn find-and-board-transport
  "Look for a loading transport and move toward/board it.
   Excludes transports with matching unload-event-id to prevent
   re-boarding the same transport that unloaded this army."
  [pos country-id]
  (let [army (get-in (sa/current-world) (conj pos :contents))
        army-unload-id (:unload-event-id army)]
    (if-let [transport-pos (core/find-adjacent-loading-transport pos army-unload-id)]
      (do
        (debug/log-computer-event! :army-board pos {:transport transport-pos})
        (core/board-transport pos transport-pos)
        (computer-movement/update-cell-visibility! pos :computer)
        nil)
      (when-let [transport-pos (core/find-loading-transport army-unload-id)]
        (movement/move-toward-objective pos transport-pos country-id)))))
