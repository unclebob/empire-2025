;; mutation-tested: 2026-03-02
(ns empire.computer.army
  "Computer army orchestrator.
   Priority: Attack adjacent enemies > Find land objective > Board transport > Explore"
  (:require [empire.adapters.state.runtime :as runtime-state]
            [empire.application.ports :as ports]
            [empire.application.runtime :as app-runtime]
            [empire.computer.army.assignment :as assignment]
            [empire.computer.army.coastal :as coastal]
            [empire.computer.army.combat :as army-combat]
            [empire.computer.army.exploration :as exploration]
            [empire.computer.army.movement :as movement]
            [empire.computer.army.transport :as transport]
            [empire.computer.core :as core]
            [empire.computer.land-objectives :as land-objectives]
            [empire.debug :as debug]))

(def ^:private state-ctx
  (delay (app-runtime/default-state-ctx)))

(defn- current-world
  []
  ((:load-world @state-ctx)))

(defn- read-runtime-state
  [k]
  (let [store (runtime-state/runtime-state-store)]
    (ports/read-runtime-state store k)))

(defn- write-runtime-state!
  [k v]
  (let [store (runtime-state/runtime-state-store)]
    (ports/write-runtime-state! store k v)))

(defn- find-city-objective
  "Find a city objective not already claimed by another army.
   Targets player and free cities only (not unexplored territory)."
  [pos]
  (let [cont-positions (land-objectives/flood-fill-continent pos)
        all-objectives (land-objectives/find-all-objectives-on-continent cont-positions)
        comp-map (read-runtime-state :computer-map)
        player-cities (filter #(= :player (:city-status (get-in comp-map %))) all-objectives)
        free-cities (filter #(= :free (:city-status (get-in comp-map %))) all-objectives)
        cities (concat player-cities free-cities)
        target (or (movement/find-nearest-unclaimed player-cities pos)
                   (movement/find-nearest-unclaimed free-cities pos)
                   (when (seq cities)
                     (apply min-key #(core/distance pos %) cities)))]
    (when target
      (write-runtime-state! :claimed-objectives
                            (conj (or (read-runtime-state :claimed-objectives) #{}) target))
      target)))

(defn- process-sentry-in-city [pos country-id cell]
  (when (= :city (:type cell))
    (coastal/fill-coastal-cell pos country-id)))

(defn- process-unowned-army [pos]
  (or (when-let [obj (find-city-objective pos)]
        (movement/move-toward-objective pos obj nil))
      (exploration/explore-randomly pos nil)))

(defn- should-sentry-on-coast? [pos country-id]
  (coastal/should-sentry-on-coast? pos country-id))

(defn- can-settle-here? [pos country-id]
  (coastal/can-settle-here? pos country-id))

(defn- start-interior-exploration [pos country-id]
  (exploration/start-interior-exploration pos country-id))

(defn- find-and-execute-land-action [pos country-id]
  (or (when-let [objective (find-city-objective pos)]
        (movement/move-toward-objective pos objective country-id))
      (when (and country-id (< (rand) 1/3))
        (start-interior-exploration pos country-id))
      (coastal/fill-coastal-cell pos country-id)
      (transport/find-and-board-transport pos country-id)
      (exploration/explore-randomly pos country-id)))

(defn- exit-city
  "If army is in a city, move to an empty passable neighbor.
   Returns new position, or original pos if unable to exit."
  [pos country-id]
  (let [cell (get-in (current-world) pos)]
    (if (= :city (:type cell))
      (if-let [exit (first (movement/get-empty-passable-neighbors pos country-id))]
        (or (movement/try-move pos exit) pos)
        pos)
      pos)))

(defn- build-army-actions [pos country-id mode unit cell enemy-pos]
  [[enemy-pos                               #(army-combat/attack-enemy pos enemy-pos)]
   [(:attack-target unit)                   #(army-combat/process-attack-target pos country-id)]
   [(= :coast-walk mode)                    #(coastal/process-coast-walk pos country-id)]
   [(= :move-to-coast-for-invasion mode)    #(coastal/process-move-to-coast-for-invasion pos country-id)]
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
  (let [game-map (current-world)
        cell (get-in game-map pos)
        unit (:contents cell)]
    (when (and unit (= :computer (:owner unit)) (= :army (:type unit)))
      (let [pos (exit-city pos (:country-id unit))
            cell (get-in (current-world) pos)
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
