(ns empire.computer.coordinator
  "Computer AI coordinator - dispatches to specialized modules for unit processing."
  (:require [empire.state.api :as sa]
            [empire.computer.army :as army]
            [empire.computer.fighter :as fighter]
            [empire.computer.ship :as ship]
            [empire.computer.transport :as transport]))

(defn- computer-unit? [unit]
  (and unit (= (:owner unit) :computer)))

(defn- dispatch-unit [pos unit]
  (case (:type unit)
    :army (army/process-army pos)
    :fighter (fighter/process-fighter pos unit)
    :transport (transport/process-transport pos)
    (:destroyer :submarine :patrol-boat :carrier :battleship)
    (ship/process-ship pos (:type unit))
    nil))

(defn process-computer-unit
  "Processes a single computer unit's turn."
  [pos]
  (let [unit (:contents (get-in (sa/current-world) pos))]
    (when (computer-unit? unit)
      (dispatch-unit pos unit))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T11:57:27.899889-05:00", :module-hash "1021597229", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 7, :hash "-942997730"} {:id "defn-/computer-unit?", :kind "defn-", :line 9, :end-line 10, :hash "-799138969"} {:id "defn-/dispatch-unit", :kind "defn-", :line 12, :end-line 19, :hash "1650727784"} {:id "defn/process-computer-unit", :kind "defn", :line 21, :end-line 26, :hash "938671659"}]}
;; clj-mutate-manifest-end
