(ns empire.fsm.engine
  "FSM execution engine with priority event queue support.
   Provides event queue operations and FSM stepping.")

(def priority-order
  "Priority ordering for events. Lower number = higher priority."
  {:high 0 :normal 1 :low 2})

(defn- event-priority
  "Returns numeric priority for an event (default :normal if not specified)."
  [event]
  (get priority-order (:priority event :normal) 1))

(defn- insert-by-priority
  "Insert event into queue maintaining priority order with FIFO within same priority."
  [queue event]
  (let [event-pri (event-priority event)
        [before after] (split-with #(<= (event-priority %) event-pri) queue)]
    (vec (concat before [event] after))))

(defn post-event
  "Add event to entity's queue, maintaining priority order.
   Events with same priority maintain FIFO order."
  [entity event]
  (update entity :event-queue insert-by-priority event))

(defn pop-event
  "Return [event updated-entity] with highest priority event removed.
   Returns [nil entity] if queue is empty."
  [entity]
  (let [queue (:event-queue entity)]
    (if (empty? queue)
      [nil entity]
      [(first queue) (update entity :event-queue #(vec (rest %)))])))

(defn peek-events
  "View queue without consuming. Returns the event queue vector."
  [entity]
  (:event-queue entity))

;; --- FSM Execution ---

(defn terminal?
  "Returns true if entity's FSM is in a terminal state.
   Terminal states are vectors starting with :terminal, e.g., [:terminal :complete]."
  [entity]
  (let [state (:fsm-state entity)]
    (and (vector? state)
         (= :terminal (first state)))))

(defn find-matching-transition
  "Find first transition in FSM that matches current state and whose guard returns true.
   FSM is a vector of [current-state guard-fn new-state action-fn] tuples.
   Returns the matching transition or nil if none found."
  [fsm current-state context]
  (first (filter (fn [[state guard-fn _ _]]
                   (and (= state current-state)
                        (guard-fn context)))
                 fsm)))

(defn step
  "Execute one FSM step for entity. If a matching transition is found,
   executes its action and transitions to the new state.
   Action function receives context and returns fsm-data updates (or nil).
   Returns updated entity."
  [entity context]
  (if (terminal? entity)
    entity
    (let [fsm (:fsm entity)
          current-state (:fsm-state entity)
          transition (find-matching-transition fsm current-state context)]
      (if-not transition
        entity
        (let [[_ _ new-state action-fn] transition
              data-updates (action-fn context)]
          (-> entity
              (assoc :fsm-state new-state)
              (update :fsm-data merge data-updates)))))))
