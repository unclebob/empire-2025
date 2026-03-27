(ns empire.computer.army
  "Computer army orchestrator.
   Priority: Attack adjacent enemies > Find land objective > Board transport > Explore"
  (:require [empire.state.api :as sa]
            [empire.computer.army-effects :as army-effects]
            [empire.computer.army.assignment :as assignment]
            [empire.computer.army.coastal :as coastal]
            [empire.computer.army.combat :as army-combat]
            [empire.computer.early-game.strategy :as opening]
            [empire.computer.army.exploration :as exploration]
            [empire.computer.army.movement :as movement]
            [empire.computer.army.transport :as transport]
            [empire.game-mechanics.debug.logging :as debug]))

(defn- process-sentry-in-city [pos country-id cell]
  (when (= :city (:type cell))
    (coastal/fill-coastal-cell pos country-id)))

(defn- process-unowned-army [pos]
  (exploration/explore-randomly pos nil))

(defn- should-sentry-on-coast? [pos country-id]
  (coastal/should-sentry-on-coast? pos country-id))

(defn- can-settle-here? [pos country-id]
  (coastal/can-settle-here? pos country-id))

(defn- start-interior-exploration [pos country-id]
  (exploration/start-interior-exploration pos country-id))

(defn- should-stage-for-opening-transport?
  [pos]
  (opening/allow-coastal-staging? pos))

(defn- find-and-execute-land-action [pos country-id]
  (or (transport/find-and-board-nearby-loading-transport pos country-id)
      (when (and country-id (< (rand) 1/3))
        (start-interior-exploration pos country-id))
      (when (should-stage-for-opening-transport? pos)
        (or (coastal/fill-coastal-cell pos country-id)
            (transport/find-and-board-transport pos country-id)))
      (exploration/explore-randomly pos country-id)))

(defn- exit-city
  "If army is in a city, move to an empty passable neighbor.
   Returns new position, or original pos if unable to exit."
  [pos country-id]
  (let [cell (get-in (sa/read-state :computer-map) pos)]
    (if (= :city (:type cell))
      (if-let [exit (first (movement/get-empty-passable-neighbors pos country-id))]
        (or (movement/try-move pos exit) pos)
        pos)
      pos)))

(defn- attack-and-handle-effects [pos enemy-pos]
  (let [outcome (army-combat/attack-enemy-result pos enemy-pos)]
    (army-effects/handle-attack-outcome! outcome)
    (:position outcome)))

(defn- build-army-actions [pos country-id mode unit cell enemy-pos]
  [[enemy-pos                               #(attack-and-handle-effects pos enemy-pos)]
   [(= :country-defense (:threat-mission unit))
    #(movement/move-toward-objective pos (:threat-center unit) country-id)]
   [(:attack-target unit)                   #(army-combat/process-attack-target pos country-id)]
   [(= :coast-walk mode)                    #(coastal/process-coast-walk pos country-id)]
   [(= :move-to-coast-for-invasion mode)    #(coastal/process-move-to-coast-for-invasion pos country-id)]
   [(= :move-to-coast-for-transport mode)   #(coastal/process-move-to-coast-for-transport pos country-id)]
   [(= :move-inland mode)                   #(exploration/process-move-inland pos country-id)]
   [(= :random-explore mode)                #(exploration/process-random-explore pos country-id)]
   [(= :sentry mode)                        #(process-sentry-in-city pos country-id cell)]
   [(:interior-explore-direction unit)      #(exploration/process-interior-explore pos country-id)]
   [(nil? country-id)                       #(process-unowned-army pos)]
   [true                                    #(find-and-execute-land-action pos country-id)]])

(defn process-army
  "Processes a computer army's turn.
   Priority: Exit city > Attack > Attack-target > Coast-walk > Random-explore > Coastal fill
   Returns nil after processing - armies only move once per round."
  [pos]
  (let [game-map (sa/read-state :computer-map)
        cell (get-in game-map pos)
        unit (:contents cell)]
    (when (and unit (= :computer (:owner unit)) (= :army (:type unit)))
      (let [pos (exit-city pos (:country-id unit))
            cell (get-in (sa/read-state :computer-map) pos)
            unit (:contents cell)
            enemy-pos (army-combat/find-adjacent-enemy pos)
            country-id (:country-id unit)
            mode (:mode unit)
            eid (:unload-event-id unit)]
        (debug/log-computer-event! :army-process pos
                                   (cond-> {:mode mode}
                                     country-id (assoc :cid country-id)
                                     eid (assoc :eid eid)))
        (let [actions (build-army-actions pos country-id mode unit cell enemy-pos)]
          (reduce (fn [_ [pred action]] (when pred (reduced (action)))) nil actions))))
    nil))

(defn assign-city-attacks
  "Scans computer-map for visible free/player cities and assigns up to 6 closest armies each."
  []
  (assignment/assign-city-attacks))

(defn assign-transport-staging
  []
  (assignment/assign-transport-staging!))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-26T22:15:23.213155-05:00", :module-hash "-900803497", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 13, :hash "1361596923"} {:id "defn-/process-sentry-in-city", :kind "defn-", :line 15, :end-line 17, :hash "1801065927"} {:id "defn-/process-unowned-army", :kind "defn-", :line 19, :end-line 20, :hash "1920550239"} {:id "defn-/should-sentry-on-coast?", :kind "defn-", :line 22, :end-line 23, :hash "-370775186"} {:id "defn-/can-settle-here?", :kind "defn-", :line 25, :end-line 26, :hash "1670601850"} {:id "defn-/start-interior-exploration", :kind "defn-", :line 28, :end-line 29, :hash "-1722277386"} {:id "defn-/should-stage-for-opening-transport?", :kind "defn-", :line 31, :end-line 33, :hash "1763417169"} {:id "defn-/find-and-execute-land-action", :kind "defn-", :line 35, :end-line 42, :hash "-797759959"} {:id "defn-/exit-city", :kind "defn-", :line 44, :end-line 53, :hash "671770124"} {:id "defn-/attack-and-handle-effects", :kind "defn-", :line 55, :end-line 58, :hash "273871187"} {:id "defn-/build-army-actions", :kind "defn-", :line 60, :end-line 73, :hash "-1778736280"} {:id "defn/process-army", :kind "defn", :line 75, :end-line 97, :hash "-1265385791"} {:id "defn/assign-city-attacks", :kind "defn", :line 99, :end-line 102, :hash "-176952326"} {:id "defn/assign-transport-staging", :kind "defn", :line 104, :end-line 106, :hash "-1866386775"}]}
;; clj-mutate-manifest-end
