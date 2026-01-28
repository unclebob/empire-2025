(ns empire.fsm.squad
  "Squad FSM - tactical unit group with shared mission.
   Coordinates 2-6 units for attack and defense operations.
   Squad owns its armies and steps them each turn."
  (:require [empire.fsm.engine :as engine]
            [empire.fsm.context :as context]
            [empire.atoms :as atoms]))

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

;; --- Expanded Squad Functionality ---

(defn create-squad-for-city
  "Create a squad to conquer a free city.
   squad-id - unique identifier for this squad
   target-city - [r c] coordinates of city to conquer
   rally-point - [r c] assembly point near target
   lieutenant-id - owning Lieutenant for reporting
   target-size - desired number of armies (3-5)
   current-round - current game round for deadline calculation"
  [squad-id target-city rally-point lieutenant-id target-size current-round]
  {:fsm squad-fsm
   :fsm-state :assembling
   :fsm-data {:squad-id squad-id
              :target target-city
              :target-city target-city
              :rally-point rally-point
              :mission-type :attack-city
              :expected-units []
              :target-size target-size
              :assembly-deadline (+ current-round 10)
              :armies []
              :armies-present-count 0}
   :event-queue []
   :units []
   :lieutenant-id lieutenant-id})

(defn add-army
  "Add an army to the squad with :rallying status."
  [squad army-id]
  (update-in squad [:fsm-data :armies] conj
             {:unit-id army-id :status :rallying :coords nil}))

(defn- update-army
  "Update an army's fields by unit-id."
  [squad army-id updates]
  (update-in squad [:fsm-data :armies]
             (fn [armies]
               (mapv (fn [a]
                       (if (= army-id (:unit-id a))
                         (merge a updates)
                         a))
                     armies))))

(defn mark-army-arrived
  "Mark army as :present and record its coordinates."
  [squad army-id coords]
  (-> squad
      (update-army army-id {:status :present :coords coords})
      (update-in [:fsm-data :armies-present-count] inc)))

(defn mark-army-lost
  "Mark army as :lost (destroyed)."
  [squad army-id]
  (let [current-army (first (filter #(= army-id (:unit-id %))
                                    (get-in squad [:fsm-data :armies])))
        was-present? (= :present (:status current-army))]
    (cond-> (update-army squad army-id {:status :lost})
      was-present? (update-in [:fsm-data :armies-present-count] dec))))

(defn get-surviving-armies
  "Get all armies not marked as :lost."
  [squad]
  (filter #(not= :lost (:status %))
          (get-in squad [:fsm-data :armies])))

;; --- Guards for Expanded FSM ---

(defn assembly-complete?
  "Guard: All target-size armies have arrived."
  [ctx]
  (let [target-size (get-in ctx [:entity :fsm-data :target-size])
        present-count (get-in ctx [:entity :fsm-data :armies-present-count])]
    (>= present-count target-size)))

(defn assembly-timeout?
  "Guard: Assembly deadline reached with at least 3 armies present."
  [ctx]
  (let [deadline (get-in ctx [:entity :fsm-data :assembly-deadline])
        current-round (:round-number ctx)
        present-count (get-in ctx [:entity :fsm-data :armies-present-count])]
    (and (>= current-round deadline)
         (>= present-count 3))))

(defn squad-destroyed?
  "Guard: All armies have been lost."
  [ctx]
  (let [armies (get-in ctx [:entity :fsm-data :armies])
        active (filter #(not= :lost (:status %)) armies)]
    (and (seq armies) (empty? active))))

(defn city-conquered?
  "Guard: Target city now belongs to computer."
  [ctx]
  (let [target (get-in ctx [:entity :fsm-data :target-city])
        cell (get-in (:game-map ctx) target)]
    (= :computer (:city-status cell))))

;; --- Actions for Expanded FSM ---

(defn disband-success-action
  "Action: City conquered. Notify Lieutenant, release armies to staging."
  [ctx]
  (let [squad-id (get-in ctx [:entity :fsm-data :squad-id])
        lt-id (:lieutenant-id (:entity ctx))
        target (get-in ctx [:entity :fsm-data :target-city])
        armies (get-in ctx [:entity :fsm-data :armies])
        survivors (filter #(not= :lost (:status %)) armies)]
    {:events [{:type :squad-mission-complete
               :priority :high
               :to lt-id
               :data {:squad-id squad-id
                      :result :success
                      :city-conquered target
                      :surviving-armies (mapv :unit-id survivors)}}]}))

(defn disband-failure-action
  "Action: Squad failed. Notify Lieutenant to create new squad."
  [ctx]
  (let [squad-id (get-in ctx [:entity :fsm-data :squad-id])
        lt-id (:lieutenant-id (:entity ctx))
        target (get-in ctx [:entity :fsm-data :target-city])]
    {:events [{:type :squad-mission-complete
               :priority :high
               :to lt-id
               :data {:squad-id squad-id
                      :result :failed
                      :target-city target}}]}))
