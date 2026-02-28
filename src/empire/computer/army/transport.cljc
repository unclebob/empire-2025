(ns empire.computer.army.transport
  "Army transport boarding behavior."
  (:require [empire.atoms :as atoms]
            [empire.computer.army.movement :as movement]
            [empire.computer.core :as core]
            [empire.debug :as debug]
            [empire.movement.visibility :as visibility]))

(defn find-and-board-transport
  "Look for a loading transport and move toward/board it.
   Excludes transports with matching unload-event-id to prevent
   re-boarding the same transport that unloaded this army."
  [pos country-id]
  (let [army (get-in @atoms/game-map (conj pos :contents))
        army-unload-id (:unload-event-id army)]
    (if-let [transport-pos (core/find-adjacent-loading-transport pos army-unload-id)]
      (do
        (debug/log-computer-event! :army-board pos {:transport transport-pos})
        (core/board-transport pos transport-pos)
        (visibility/update-cell-visibility pos :computer)
        nil)
      (when-let [transport-pos (core/find-loading-transport army-unload-id)]
        (movement/move-toward-objective pos transport-pos country-id)))))
