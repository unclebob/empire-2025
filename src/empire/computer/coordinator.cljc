(ns empire.computer.coordinator
  "Computer AI coordinator - dispatches to specialized modules for unit processing."
  (:require [empire.state.api :as sa]
            [empire.computer.army :as army]
            [empire.computer.coordinator-decisions :as decisions]
            [empire.computer.fighter :as fighter]
            [empire.computer.ship :as ship]
            [empire.computer.transport :as transport]))

(defn- dispatch-unit [pos unit]
  (case (:action (decisions/dispatch-plan unit))
    :army (army/process-army pos)
    :fighter (fighter/process-fighter pos unit)
    :transport (transport/process-transport pos)
    :ship (ship/process-ship pos (:type unit))
    nil))

(defn process-computer-unit
  "Processes a single computer unit's turn."
  [pos]
  (let [unit (:contents (get-in (sa/read-state :computer-map) pos))]
    (when (decisions/dispatch-plan unit)
      (dispatch-unit pos unit))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-26T22:36:45.598684-05:00", :module-hash "-1496519182", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 8, :hash "747665866"} {:id "defn-/dispatch-unit", :kind "defn-", :line 10, :end-line 16, :hash "728412540"} {:id "defn/process-computer-unit", :kind "defn", :line 18, :end-line 23, :hash "778807333"}]}
;; clj-mutate-manifest-end
