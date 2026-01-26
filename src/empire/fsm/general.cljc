(ns empire.fsm.general
  "General FSM - top-level strategic commander for computer player.
   Creates and commands Lieutenants, processes strategic events."
  (:require [empire.atoms :as atoms]
            [empire.fsm.engine :as engine]
            [empire.fsm.context :as context]
            [empire.fsm.lieutenant :as lieutenant]))

(def lieutenant-names
  "NATO phonetic alphabet for naming lieutenants."
  ["Alpha" "Bravo" "Charlie" "Delta" "Echo" "Foxtrot" "Golf" "Hotel"
   "India" "Juliet" "Kilo" "Lima" "Mike" "November" "Oscar" "Papa"
   "Quebec" "Romeo" "Sierra" "Tango" "Uniform" "Victor" "Whiskey"
   "X-ray" "Yankee" "Zulu"])

(defn- next-lieutenant-name
  "Get the next lieutenant name based on current count."
  [lieutenants]
  (let [idx (count lieutenants)]
    (if (< idx (count lieutenant-names))
      (nth lieutenant-names idx)
      (str "Lieutenant-" (inc idx)))))

(defn- has-city-needs-orders?
  "Guard: check if there's a city-needs-orders event in queue."
  [ctx]
  (context/has-event? ctx :city-needs-orders))

(defn- handle-city-needs-orders
  "Action: create lieutenant for the city. Returns fsm-data updates."
  [_ctx]
  ;; FSM data updates - actual lieutenant creation happens in process-general
  nil)

(def general-fsm
  "FSM transitions for the General.
   Format: [current-state guard-fn new-state action-fn]"
  [[:awaiting-city has-city-needs-orders? :operational handle-city-needs-orders]
   [:operational (constantly false) :operational identity]])

(defn create-general
  "Create a new General in initial state."
  []
  {:fsm general-fsm
   :fsm-state :awaiting-city
   :fsm-data {}
   :event-queue []
   :lieutenants []})

(defn- process-city-needs-orders
  "Process a city-needs-orders event: create lieutenant and assign city.
   Also stores the lieutenant's name on the city cell for unit spawning.
   Initializes lieutenant with knowledge of visible cells around the city."
  [general event]
  (let [city-coords (get-in event [:data :coords])
        lt-name (next-lieutenant-name (:lieutenants general))
        new-lt (-> (lieutenant/create-lieutenant lt-name city-coords)
                   (lieutenant/initialize-with-visible-cells))]
    ;; Store lieutenant name on the city cell
    (swap! atoms/game-map assoc-in (conj city-coords :lieutenant) lt-name)
    (update general :lieutenants conj new-lt)))

(defn- process-base-established
  "Process a base-established event: create lieutenant for the new base.
   Also stores the lieutenant's name on the beach cell.
   Initializes lieutenant with knowledge of visible cells around the beach."
  [general event]
  (let [beach-coords (get-in event [:data :beach-coords])
        lt-name (next-lieutenant-name (:lieutenants general))
        new-lt (-> (lieutenant/create-lieutenant lt-name beach-coords)
                   (lieutenant/initialize-with-visible-cells))]
    ;; Store lieutenant name on the beach cell
    (swap! atoms/game-map assoc-in (conj beach-coords :lieutenant) lt-name)
    (update general :lieutenants conj new-lt)))

(defn- process-event
  "Process a single event from the queue."
  [general event]
  (case (:type event)
    :city-needs-orders (process-city-needs-orders general event)
    :base-established (process-base-established general event)
    general))

(defn process-general
  "Process one step of the General FSM.
   Handles state transitions and event processing."
  [general]
  (let [ctx (context/build-context general)
        ;; Step the FSM (handles state transitions)
        general-after-step (engine/step general ctx)
        ;; Pop and process one event if any
        [event general-after-pop] (engine/pop-event general-after-step)]
    (if event
      (process-event general-after-pop event)
      general-after-pop)))
