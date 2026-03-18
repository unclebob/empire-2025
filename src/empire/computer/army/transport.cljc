(ns empire.computer.army.transport
  "Army transport boarding behavior."
  (:require [empire.state.api :as sa]
            [empire.computer.army.movement :as movement]
            [empire.computer.core :as core]
            [empire.game-mechanics.debug.logging :as debug]
            [empire.computer.movement :as computer-movement]))

(defn find-and-board-transport
  "Look for a loading transport and move toward/board it.
   Excludes transports with matching unload-event-id to prevent
   re-boarding the same transport that unloaded this army."
  [pos country-id]
  (let [army (get-in (sa/read-state :computer-map) (conj pos :contents))
        army-unload-id (:unload-event-id army)]
    (if-let [transport-pos (core/find-adjacent-loading-transport pos army-unload-id)]
      (do
        (debug/log-computer-event! :army-board pos {:transport transport-pos})
        (core/board-transport pos transport-pos)
        (computer-movement/update-cell-visibility! pos :computer)
        nil)
      (when-let [transport-pos (core/find-loading-transport army-unload-id)]
        (movement/move-toward-objective pos transport-pos country-id)))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T11:57:25.063379-05:00", :module-hash "1491705333", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 7, :hash "1350358818"} {:id "defn/find-and-board-transport", :kind "defn", :line 9, :end-line 23, :hash "-1877571689"}]}
;; clj-mutate-manifest-end
