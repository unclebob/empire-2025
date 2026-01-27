(ns empire.fsm.engine
  "FSM execution engine with priority event queue support.
   Provides event queue operations and FSM stepping."
  (:require [empire.debug :as debug]))

(def priority-order
  "Priority ordering for events. Lower number = higher priority."
  {:high 0 :normal 1 :low 2})

(defn- entity-type
  "Detect entity type from its structure for logging purposes."
  [entity]
  (cond
    (:lieutenants entity) :general
    (:cities entity) :lieutenant
    (:units entity) :squad
    (get-in entity [:fsm-data :mission-type]) (get-in entity [:fsm-data :mission-type])
    :else :unknown))

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
  (debug/log-action! [:fsm-event-posted (entity-type entity) (:type event) (:priority event)])
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

;; --- State-Grouped FSM Format ---

(defn parse-state-group
  "Parse a state group from the new grouped FSM format.
   Returns [state super-state config transitions] where:
   - state: the sub-state keyword (or super-state name if defining a super-state)
   - super-state: nil or the super-state keyword (if header is 2-tuple)
   - config: map with :entry/:exit actions (or nil)
   - transitions: vector of 3-tuples [guard-fn new-state action-fn]

   Formats supported:
   - [:state [trans1] [trans2]]                    - simple state
   - [[:sub :super] [trans1] [trans2]]            - sub-state with super-state
   - [:super {:entry fn :exit fn} [trans1]]       - super-state with hooks"
  [group]
  (let [header (first group)
        rest-items (rest group)
        ;; Check if second item is a config map
        has-config? (and (seq rest-items) (map? (first rest-items)))
        config (when has-config? (first rest-items))
        transitions (vec (if has-config? (rest rest-items) rest-items))]
    (if (vector? header)
      [(first header) (second header) config transitions]
      [header nil config transitions])))

(defn build-transition-index
  "Build a lookup index from a state-grouped FSM.
   Returns map of state -> {:super-state _ :config _ :transitions [...]}
   Super-states (states with no super-state but with sub-states pointing to them)
   are also included in the index."
  [fsm]
  (reduce (fn [index group]
            (let [[state super-state config transitions] (parse-state-group group)]
              (assoc index state {:super-state super-state
                                  :config config
                                  :transitions transitions})))
          {}
          fsm))

(defn get-super-state
  "Get the super-state for a given sub-state from a transition index.
   Returns nil if state has no super-state."
  [index state]
  (:super-state (get index state)))

(defn- find-matching-in-transitions
  "Find first matching transition in a transitions vector."
  [transitions context]
  (first (filter (fn [[guard-fn _ _]]
                   (guard-fn context))
                 transitions)))

(defn find-matching-transition-grouped
  "Find first matching transition in a grouped FSM index.
   First checks sub-state transitions, then falls through to super-state transitions.
   Returns the matching 3-tuple [guard-fn new-state action-fn] or nil."
  [index current-state context]
  (when-let [state-entry (get index current-state)]
    (or
     ;; First try sub-state transitions
     (find-matching-in-transitions (:transitions state-entry) context)
     ;; Fall through to super-state transitions if sub-state guards fail
     (when-let [super-state (:super-state state-entry)]
       (when-let [super-entry (get index super-state)]
         (find-matching-in-transitions (:transitions super-entry) context))))))

(defn- grouped-fsm?
  "Returns true if fsm is in the new grouped format.
   Grouped format: first element's second item is a vector (transition) or map (config).
   Flat format: first element's second item is a function (guard)."
  [fsm]
  (when (seq fsm)
    (let [first-elem (first fsm)]
      (and (vector? first-elem)
           (>= (count first-elem) 2)
           (let [second-item (second first-elem)]
             (or (vector? second-item)  ;; transition
                 (map? second-item)))))))

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
   Supports both flat format (4-tuples) and grouped format.
   Returns the matching transition (4-tuple for flat, 3-tuple for grouped) or nil."
  [fsm current-state context]
  (if (grouped-fsm? fsm)
    (let [index (build-transition-index fsm)]
      (find-matching-transition-grouped index current-state context))
    ;; Flat format: [current-state guard-fn new-state action-fn]
    (first (filter (fn [[state guard-fn _ _]]
                     (and (= state current-state)
                          (guard-fn context)))
                   fsm))))

(defn- merge-action-events
  "Merge events from action result into entity's event-queue."
  [entity events]
  (if (seq events)
    (reduce post-event entity events)
    entity))

(defn- extract-transition-parts
  "Extract new-state and action-fn from a transition.
   Handles both 3-tuple (grouped) and 4-tuple (flat) formats."
  [transition fsm]
  (if (grouped-fsm? fsm)
    ;; Grouped: [guard-fn new-state action-fn]
    [(second transition) (nth transition 2)]
    ;; Flat: [state guard-fn new-state action-fn]
    [(nth transition 2) (nth transition 3)]))

(defn- get-state-super-state
  "Get the super-state for a given state from the FSM index."
  [index state]
  (when-let [entry (get index state)]
    (:super-state entry)))

(defn- get-super-state-config
  "Get the config (entry/exit actions) for a super-state."
  [index super-state]
  (when-let [entry (get index super-state)]
    (:config entry)))

(defn- execute-exit-action
  "Execute exit action for a super-state if defined. Returns fsm-data updates."
  [index super-state context]
  (when-let [config (get-super-state-config index super-state)]
    (when-let [exit-fn (:exit config)]
      (exit-fn context))))

(defn- execute-entry-action
  "Execute entry action for a super-state if defined. Returns fsm-data updates."
  [index super-state context]
  (when-let [config (get-super-state-config index super-state)]
    (when-let [entry-fn (:entry config)]
      (entry-fn context))))

(defn step
  "Execute one FSM step for entity. If a matching transition is found,
   executes its action and transitions to the new state.
   Action function receives context and returns fsm-data updates (or nil).
   If action returns :events, they are merged into the entity's event-queue.
   Supports both flat format (4-tuples) and grouped format (3-tuples).
   For grouped FSMs with super-states, executes entry/exit actions when
   transitioning between super-states.
   Returns updated entity."
  [entity context]
  (if (terminal? entity)
    entity
    (let [fsm (:fsm entity)
          current-state (:fsm-state entity)
          transition (find-matching-transition fsm current-state context)]
      (if-not transition
        entity
        (let [[new-state action-fn] (extract-transition-parts transition fsm)
              ;; Execute the transition action
              data-updates (action-fn context)
              events (:events data-updates)
              fsm-data-updates (dissoc data-updates :events)
              ;; For grouped FSMs, check for super-state changes
              grouped? (grouped-fsm? fsm)
              index (when grouped? (build-transition-index fsm))
              old-super (when index (get-state-super-state index current-state))
              new-super (when index (get-state-super-state index new-state))
              super-state-changed? (and index (not= old-super new-super))
              ;; Execute exit action if leaving a super-state
              exit-updates (when (and super-state-changed? old-super)
                             (execute-exit-action index old-super context))
              ;; Execute entry action if entering a super-state
              entry-updates (when (and super-state-changed? new-super)
                              (execute-entry-action index new-super context))]
          (when (not= current-state new-state)
            (debug/log-action! [:fsm-transition (entity-type entity) current-state new-state]))
          (-> entity
              (assoc :fsm-state new-state)
              (update :fsm-data merge fsm-data-updates)
              (update :fsm-data merge exit-updates)
              (update :fsm-data merge entry-updates)
              (merge-action-events events)))))))
