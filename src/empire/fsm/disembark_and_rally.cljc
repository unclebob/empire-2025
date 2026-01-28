(ns empire.fsm.disembark-and-rally
  "Disembark-and-Rally FSM - army exits transport and rallies to inland position.
   Phase 0: Disembarked on beach, request rally point if none assigned.
   Phase 1: Move inland toward rally point.
   Terminal: Report to new Lieutenant, or get stuck."
  (:require [empire.atoms :as atoms]
            [empire.movement.pathfinding :as pathfinding]
            [empire.fsm.explorer-utils :as utils]))


;; --- Guards ---

(defn- stuck?
  "Guard: Returns true if there are no valid moves available."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])]
    (utils/stuck? pos @atoms/game-map)))

(defn- has-rally-point?
  "Guard: Returns true if rally-point has been assigned."
  [ctx]
  (some? (get-in ctx [:entity :fsm-data :rally-point])))

(defn- no-rally-point?
  "Guard: Returns true if no rally-point is assigned."
  [ctx]
  (nil? (get-in ctx [:entity :fsm-data :rally-point])))

(defn- at-rally-point?
  "Guard: Returns true if current position equals rally-point."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        rally-point (get-in ctx [:entity :fsm-data :rally-point])]
    (= pos rally-point)))

(defn- can-move-toward?
  "Guard: Returns true if pathfinding can find next step toward rally-point."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        rally-point (get-in ctx [:entity :fsm-data :rally-point])
        next-pos (when rally-point (pathfinding/next-step-toward pos rally-point))]
    (boolean next-pos)))

(defn- needs-sidestep?
  "Guard: Returns true if direct path is blocked but sidestep move exists."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        rally-point (get-in ctx [:entity :fsm-data :rally-point])
        recent-moves (or (get-in ctx [:entity :fsm-data :recent-moves]) [])]
    (boolean (utils/find-sidestep-move pos rally-point recent-moves @atoms/game-map))))

;; --- Actions ---

(defn- terminal-action
  "Action: Called when stuck with no valid moves. Notifies Lieutenant."
  [ctx]
  (let [unit-id (get-in ctx [:entity :fsm-data :unit-id])]
    {:events [(utils/make-mission-ended-event unit-id :stuck)]}))

(defn- begin-inland-move-action
  "Action: Start moving toward rally point, clear beach for next army."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        rally-point (get-in ctx [:entity :fsm-data :rally-point])
        recent-moves (or (get-in ctx [:entity :fsm-data :recent-moves]) [])
        next-pos (pathfinding/next-step-toward pos rally-point)]
    (when next-pos
      {:move-to next-pos
       :recent-moves (utils/update-recent-moves recent-moves next-pos)})))

(defn- report-to-lieutenant-action
  "Action: Register with new Lieutenant, request mission assignment. Ends current mission."
  [ctx]
  (let [unit-id (get-in ctx [:entity :fsm-data :unit-id])
        lt-id (get-in ctx [:entity :fsm-data :new-lieutenant-id])
        pos (get-in ctx [:entity :fsm-data :position])]
    {:events [{:type :unit-needs-orders
               :priority :normal
               :to lt-id
               :data {:unit-id unit-id :coords pos}}
              (utils/make-mission-ended-event unit-id :reported)]}))

(defn- request-orders-action
  "Action: No rally point assigned, request orders from Lieutenant."
  [ctx]
  (let [unit-id (get-in ctx [:entity :fsm-data :unit-id])
        pos (get-in ctx [:entity :fsm-data :position])]
    {:events [{:type :unit-needs-rally-point
               :priority :high
               :data {:unit-id unit-id
                      :coords pos}}]}))

(defn- move-toward-action
  "Action: Move toward rally-point using pathfinding."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        rally-point (get-in ctx [:entity :fsm-data :rally-point])
        recent-moves (or (get-in ctx [:entity :fsm-data :recent-moves]) [])
        next-pos (pathfinding/next-step-toward pos rally-point)]
    (when next-pos
      {:move-to next-pos
       :recent-moves (utils/update-recent-moves recent-moves next-pos)})))

(defn- sidestep-action
  "Action: Sidestep around obstacle while moving toward rally-point."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        rally-point (get-in ctx [:entity :fsm-data :rally-point])
        recent-moves (or (get-in ctx [:entity :fsm-data :recent-moves]) [])
        sidestep-pos (utils/find-sidestep-move pos rally-point recent-moves @atoms/game-map)]
    (when sidestep-pos
      {:move-to sidestep-pos
       :recent-moves (utils/update-recent-moves recent-moves sidestep-pos)})))

;; --- FSM Definition ---

(def disembark-and-rally-fsm
  "FSM transitions for disembark-and-rally mission.
   Format: [current-state guard-fn new-state action-fn]

   States:
   - :disembarked           - Just landed on beach, orienting
   - :moving-inland         - Pathfinding toward rally point
   - [:terminal :reported]  - At rally point, reported to Lieutenant
   - [:terminal :stuck]     - No valid moves available"
  [;; Disembarked transitions
   [:disembarked  has-rally-point?  :moving-inland  begin-inland-move-action]
   [:disembarked  no-rally-point?   :disembarked    request-orders-action]

   ;; Moving inland transitions (order matters - first matching wins)
   [:moving-inland  stuck?              [:terminal :stuck]     terminal-action]
   [:moving-inland  at-rally-point?     [:terminal :reported]  report-to-lieutenant-action]
   [:moving-inland  can-move-toward?    :moving-inland         move-toward-action]
   [:moving-inland  needs-sidestep?     :moving-inland         sidestep-action]])

;; --- Create Mission ---

(defn create-disembark-and-rally-data
  "Create FSM data for disembark-and-rally mission.
   pos - beach cell where disembarked
   rally-point - inland assembly point (may be nil initially)
   new-lieutenant-id - Lieutenant for new territory
   transport-id - transport that delivered us
   unit-id - for tracking (optional)"
  ([pos rally-point new-lieutenant-id transport-id]
   (create-disembark-and-rally-data pos rally-point new-lieutenant-id transport-id nil))
  ([pos rally-point new-lieutenant-id transport-id unit-id]
   {:fsm disembark-and-rally-fsm
    :fsm-state :disembarked
    :fsm-data {:mission-type :disembark-and-rally
               :position pos
               :rally-point rally-point
               :new-lieutenant-id new-lieutenant-id
               :transport-id transport-id
               :unit-id unit-id
               :recent-moves [pos]}}))
