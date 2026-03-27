(ns empire.computer.army.transport
  "Army transport boarding behavior."
  (:require [empire.state.api :as sa]
            [empire.computer.army.movement :as movement]
            [empire.computer.shared.action-resolution :as action-resolution]
            [empire.game-mechanics.debug.logging :as debug]
            [empire.computer.shared.transport-query :as transport-query]
            [empire.computer.shared.movement :as computer-movement]))

(def ^:private nearby-loading-transport-radius 5)

(defn find-and-board-transport
  "Look for a loading transport and move toward/board it.
   Excludes transports with matching unload-event-id to prevent
   re-boarding the same transport that unloaded this army."
  [pos country-id]
  (let [army (get-in (sa/read-state :computer-map) (conj pos :contents))
        army-unload-id (:unload-event-id army)]
    (if-let [transport-pos (transport-query/find-adjacent-loading-transport pos army-unload-id)]
      (do
        (debug/log-computer-event! :army-board pos {:transport transport-pos})
        (action-resolution/board-transport pos transport-pos)
        (computer-movement/update-cell-visibility! pos :computer)
        nil)
      (when-let [transport-pos (transport-query/find-loading-transport army-unload-id)]
        (movement/move-toward-objective pos transport-pos country-id)))))

(defn find-and-board-nearby-loading-transport
  [pos country-id]
  (let [army (get-in (sa/read-state :computer-map) (conj pos :contents))
        army-unload-id (:unload-event-id army)]
    (if-let [transport-pos (transport-query/find-adjacent-loading-transport pos army-unload-id)]
      (do
        (debug/log-computer-event! :army-board pos {:transport transport-pos})
        (action-resolution/board-transport pos transport-pos)
        (computer-movement/update-cell-visibility! pos :computer)
        :boarded)
      (when-let [transport-pos (transport-query/find-nearby-loading-transport
                                pos
                                nearby-loading-transport-radius
                                army-unload-id)]
        (movement/move-toward-objective pos transport-pos country-id)))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-26T22:33:56.764206-05:00", :module-hash "-403407838", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 8, :hash "-323365915"} {:id "def/nearby-loading-transport-radius", :kind "def", :line 10, :end-line 10, :hash "1557696767"} {:id "defn/find-and-board-transport", :kind "defn", :line 12, :end-line 26, :hash "398630335"} {:id "defn/find-and-board-nearby-loading-transport", :kind "defn", :line 28, :end-line 42, :hash "-518958576"}]}
;; clj-mutate-manifest-end
