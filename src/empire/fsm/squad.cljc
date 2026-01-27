(ns empire.fsm.squad
  "Squad FSM - tactical unit group with shared mission.
   Coordinates 2-6 units for attack and defense operations."
  (:require [empire.fsm.engine :as engine]
            [empire.fsm.context :as context]))

(defn adjacent?
  "Returns true if two [row col] positions are adjacent (including diagonals)."
  [[r1 c1] [r2 c2]]
  (and (not (and (= r1 r2) (= c1 c2)))
       (<= (abs (- r1 r2)) 1)
       (<= (abs (- c1 c2)) 1)))

(defn- all-units-present?
  "Guard: check if all expected units have joined the squad."
  [ctx]
  (let [entity (:entity ctx)
        expected (set (get-in entity [:fsm-data :expected-units]))
        present (set (map :unit-id (:units entity)))]
    (= expected present)))

(defn- any-unit-adjacent-to-target?
  "Guard: check if any unit is adjacent to the target."
  [ctx]
  (let [entity (:entity ctx)
        target (get-in entity [:fsm-data :target])
        units (:units entity)]
    (boolean (some #(adjacent? (:coords %) target) units))))

(defn- has-city-conquered?
  "Guard: check if city-conquered event is in queue."
  [ctx]
  (context/has-event? ctx :city-conquered))

(def squad-fsm
  "FSM transitions for the Squad.
   Format: state-grouped with 3-tuples [guard-fn new-state action-fn]"
  [[:assembling
    [all-units-present? :moving (constantly nil)]]
   [:moving
    [any-unit-adjacent-to-target? :attacking (constantly nil)]]
   [:attacking
    [has-city-conquered? :defending (constantly nil)]]
   [:defending
    [(constantly false) :defending identity]]])

(defn create-squad
  "Create a new Squad with the given target and expected units."
  [lieutenant-id target-coords expected-unit-ids]
  {:fsm squad-fsm
   :fsm-state :assembling
   :fsm-data {:target target-coords
              :mission-type :attack-city
              :expected-units expected-unit-ids}
   :event-queue []
   :units []
   :lieutenant-id lieutenant-id})

(defn join-squad
  "Add a unit to the squad."
  [squad unit]
  (update squad :units conj unit))

(defn- handle-city-conquered
  "Handle city-conquered event."
  [squad _event]
  ;; Transition to defending is handled by FSM
  ;; Could post event to lieutenant here
  squad)

(defn- process-event
  "Process a single event from the queue."
  [squad event]
  (case (:type event)
    :city-conquered (handle-city-conquered squad event)
    squad))

(defn process-squad
  "Process one step of the Squad FSM.
   Handles state transitions and event processing."
  [squad]
  (let [ctx (context/build-context squad)
        ;; Step the FSM (handles state transitions)
        sq-after-step (engine/step squad ctx)
        ;; Pop and process one event if any
        [event sq-after-pop] (engine/pop-event sq-after-step)]
    (if event
      (process-event sq-after-pop event)
      sq-after-pop)))
