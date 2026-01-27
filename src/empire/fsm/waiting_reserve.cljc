(ns empire.fsm.waiting-reserve
  "Waiting Reserve FSM - army holds at an assigned station, ready for orders.
   Phase 0: Move to assigned station (if not already there).
   Phase 1: Hold at station, awaiting further orders."
  (:require [empire.atoms :as atoms]
            [empire.movement.pathfinding :as pathfinding]
            [empire.fsm.context :as context]))

;; --- Guards ---

(defn- at-station?
  "Guard: Returns true if current position equals station."
  [ctx]
  (let [fsm-data (get-in ctx [:entity :fsm-data])
        pos (:position fsm-data)
        station (:station fsm-data)]
    (or (nil? station) (= pos station))))

(defn- not-at-station?
  "Guard: Returns true if not at station."
  [ctx]
  (not (at-station? ctx)))

(defn- always [_ctx] true)

(defn- stuck?
  "Guard: Returns true if there are no valid moves to reach station.
   Only triggers when NOT at station but pathfinding returns nil."
  [ctx]
  (let [fsm-data (get-in ctx [:entity :fsm-data])
        pos (:position fsm-data)
        station (:station fsm-data)
        at-station (or (nil? station) (= pos station))
        next-pos (when (and station (not at-station))
                   (pathfinding/next-step-toward pos station))]
    (and (not at-station) (nil? next-pos))))

;; --- Actions ---

(defn- hold-action
  "Action: Hold at current position. Returns nil for :move-to.
   The army stays put, ready for future orders."
  [ctx]
  (let [fsm-data (get-in ctx [:entity :fsm-data])
        pos (:position fsm-data)]
    {:move-to nil
     :position pos}))

;; Public version for testing
(def hold-action-public hold-action)

(defn- move-to-station-action
  "Action: Move toward station using pathfinding.
   Returns {:move-to [row col]}"
  [ctx]
  (let [fsm-data (get-in ctx [:entity :fsm-data])
        pos (:position fsm-data)
        station (:station fsm-data)
        next-pos (pathfinding/next-step-toward pos station)]
    (when next-pos
      {:move-to next-pos
       :station station})))

;; Public version for testing
(def move-to-station-action-public move-to-station-action)

(defn- arrive-at-station-action
  "Action: Called when arriving at station. Sets up for holding."
  [ctx]
  (let [fsm-data (get-in ctx [:entity :fsm-data])
        pos (:position fsm-data)]
    {:move-to nil
     :position pos}))

(defn- terminal-action
  "Action: Called when the reserve is stuck with no path to station.
   Emits a :mission-ended event to notify the Lieutenant."
  [ctx]
  (let [unit-id (get-in ctx [:entity :fsm-data :unit-id])]
    {:events [{:type :mission-ended
               :priority :high
               :data {:unit-id unit-id :reason :stuck}}]}))

;; --- FSM Definition ---

(def waiting-reserve-fsm
  "FSM transitions for waiting reserve.
   Format: state-grouped with 3-tuples [guard-fn new-state action-fn]

   States:
   - :moving-to-station - Moving to assigned station
   - :holding           - Holding at station, ready for orders
   - [:terminal :stuck] - No path to station, mission ended"
  [[:moving-to-station
    [stuck?          [:terminal :stuck]  terminal-action]
    [at-station?     :holding            arrive-at-station-action]
    [not-at-station? :moving-to-station  move-to-station-action]]
   [:holding
    [always          :holding            hold-action]]])

;; --- Create Waiting Reserve ---

(defn create-waiting-reserve-data
  "Create FSM data for a waiting reserve mission at given position.
   Optional station parameter sets the destination where the army should hold.
   Optional unit-id parameter identifies the unit for mission tracking."
  ([pos]
   (create-waiting-reserve-data pos nil nil))
  ([pos station]
   (create-waiting-reserve-data pos station nil))
  ([pos station unit-id]
   (let [has-station? (and station (not= pos station))]
     {:fsm waiting-reserve-fsm
      :fsm-state (if has-station? :moving-to-station :holding)
      :fsm-data {:position pos
                 :station (when has-station? station)
                 :unit-id unit-id}})))
