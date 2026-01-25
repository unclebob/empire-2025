(ns empire.fsm.lieutenant
  "Lieutenant FSM - operational commander for a base/territory.
   Controls cities, assigns missions to units, forms squads."
  (:require [empire.fsm.engine :as engine]
            [empire.fsm.context :as context]))

(defn- has-unit-needs-orders?
  "Guard: check if there's a unit-needs-orders event in queue."
  [ctx]
  (context/has-event? ctx :unit-needs-orders))

(defn- has-any-event?
  "Guard: check if there's any event in queue."
  [ctx]
  (seq (:event-queue ctx)))

(def lieutenant-fsm
  "FSM transitions for the Lieutenant.
   Format: [current-state guard-fn new-state action-fn]"
  [[:initializing has-unit-needs-orders? :exploring (constantly nil)]
   [:exploring (constantly false) :exploring identity]
   [:established (constantly false) :established identity]])

(defn create-lieutenant
  "Create a new Lieutenant with the given name and assigned city."
  [name city-coords]
  {:fsm lieutenant-fsm
   :fsm-state :initializing
   :fsm-data {:mission-type :explore-conquer}
   :event-queue []
   :name name
   :cities [city-coords]
   :direct-reports []
   :free-cities-known []
   :beach-candidates []})

(defn city-name
  "Generate city name for the nth city (0-indexed) under this lieutenant."
  [lieutenant idx]
  (str (:name lieutenant) "-" (inc idx)))

(defn- create-explorer
  "Create an explorer unit record for a direct report."
  [unit-id coords]
  {:unit-id unit-id
   :coords coords
   :mission-type :explore-coastline  ; Default to coastline exploration
   :fsm-state :exploring})

(defn- handle-unit-needs-orders
  "Handle unit-needs-orders event: assign explorer mission."
  [lieutenant event]
  (let [unit-id (get-in event [:data :unit-id])
        coords (get-in event [:data :coords])
        explorer (create-explorer unit-id coords)]
    (update lieutenant :direct-reports conj explorer)))

(defn- handle-free-city-found
  "Handle free-city-found event: add to known list if not duplicate."
  [lieutenant event]
  (let [coords (get-in event [:data :coords])
        known (:free-cities-known lieutenant)]
    (if (some #(= % coords) known)
      lieutenant
      (update lieutenant :free-cities-known conj coords))))

(defn- handle-city-conquered
  "Handle city-conquered event: add to cities, remove from free-cities-known."
  [lieutenant event]
  (let [coords (get-in event [:data :coords])]
    (-> lieutenant
        (update :cities conj coords)
        (update :free-cities-known #(vec (remove #{coords} %))))))

(defn- handle-coastline-mapped
  "Handle coastline-mapped event: add beach candidate if not duplicate."
  [lieutenant event]
  (let [coords (get-in event [:data :coords])
        known (:beach-candidates lieutenant)]
    (if (some #(= % coords) known)
      lieutenant
      (update lieutenant :beach-candidates conj coords))))

(defn- process-event
  "Process a single event from the queue."
  [lieutenant event]
  (case (:type event)
    :unit-needs-orders (handle-unit-needs-orders lieutenant event)
    :free-city-found (handle-free-city-found lieutenant event)
    :city-conquered (handle-city-conquered lieutenant event)
    :coastline-mapped (handle-coastline-mapped lieutenant event)
    lieutenant))

(defn process-lieutenant
  "Process one step of the Lieutenant FSM.
   Handles state transitions and event processing."
  [lieutenant]
  (let [ctx (context/build-context lieutenant)
        ;; Step the FSM (handles state transitions)
        lt-after-step (engine/step lieutenant ctx)
        ;; Pop and process one event if any
        [event lt-after-pop] (engine/pop-event lt-after-step)]
    (if event
      (process-event lt-after-pop event)
      lt-after-pop)))
