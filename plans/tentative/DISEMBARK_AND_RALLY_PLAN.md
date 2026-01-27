# Disembark-and-Rally FSM Plan

**STATUS: TENTATIVE**

## Overview

Army mission after being unloaded from a transport onto a beachhead. Army moves inland to a rally point and reports to the new Lieutenant controlling the territory. Used when establishing a new base overseas.

---

## Behavioral Requirements

1. **Disembark**: Exit transport onto beach (handled by transport)
2. **Move to Rally Point**: Navigate inland to designated rally position
3. **Sidestepping**: Navigate around other disembarked armies
4. **Report to Lieutenant**: Register with new territory's Lieutenant
5. **Await Orders**: Enter state ready for new mission assignment

**Terminal Conditions:**
- Reach rally point and report to Lieutenant
- Rally point unreachable (report failure)

---

## FSM States

```
:disembarked     →  Just landed on beach, orienting
      ↓
:moving-inland   →  Pathfinding toward rally point
      ↓
[:terminal :reported]  →  At rally point, reported to Lieutenant
```

---

## FSM Transitions

```clojure
(def disembark-and-rally-fsm
  [;; Just disembarked
   [:disembarked  has-rally-point?  :moving-inland  begin-inland-move-action]
   [:disembarked  no-rally-point?   :disembarked    request-orders-action]

   ;; Moving inland to rally point
   [:moving-inland  stuck?               [:terminal :stuck]     terminal-action]
   [:moving-inland  at-rally-point?      [:terminal :reported]  report-to-lieutenant-action]
   [:moving-inland  rally-blocked?       :moving-inland         find-alternate-rally-action]
   [:moving-inland  can-move-toward?     :moving-inland         move-toward-action]
   [:moving-inland  needs-sidestep?      :moving-inland         sidestep-action]])
```

---

## FSM Data Structure

```clojure
{:fsm disembark-and-rally-fsm
 :fsm-state :disembarked
 :fsm-data {:mission-type :disembark-and-rally
            :position [row col]           ; beach landing position
            :rally-point [row col]        ; inland assembly point
            :new-lieutenant-id id         ; Lieutenant for new territory
            :transport-id id              ; transport that delivered us
            :unit-id id                   ; for Lieutenant tracking
            :recent-moves [[r c] ...]}
 :event-queue []}
```

---

## Guards

### `has-rally-point?`
Rally point has been assigned.

```clojure
(defn has-rally-point? [ctx]
  (some? (get-in ctx [:entity :fsm-data :rally-point])))
```

### `at-rally-point?`
```clojure
(defn at-rally-point? [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        rally (get-in ctx [:entity :fsm-data :rally-point])]
    (= pos rally)))
```

### `no-path?`
Cannot find any path to rally point (completely blocked).

---

## Actions

### `terminal-action`
Called when stuck with no valid moves. Notifies Lieutenant.

```clojure
(defn- terminal-action [ctx]
  (let [unit-id (get-in ctx [:entity :fsm-data :unit-id])]
    {:events [{:type :mission-ended
               :priority :high
               :data {:unit-id unit-id :reason :stuck}}]}))
```

### `begin-inland-move-action`
Start moving toward rally point, clear beach for next army.

```clojure
(defn begin-inland-move-action [ctx]
  (let [rally (get-in ctx [:entity :fsm-data :rally-point])
        pos (get-in ctx [:entity :fsm-data :position])]
    {:move-to (pathfinding/next-step-toward pos rally)}))
```

### `report-to-lieutenant-action`
Register with new Lieutenant, request mission assignment. Ends current mission.

```clojure
(defn report-to-lieutenant-action [ctx]
  (let [unit-id (get-in ctx [:entity :fsm-data :unit-id])
        lt-id (get-in ctx [:entity :fsm-data :new-lieutenant-id])
        pos (get-in ctx [:entity :fsm-data :position])]
    {:events [{:type :unit-needs-orders
               :priority :normal
               :to lt-id
               :data {:unit-id unit-id :coords pos}}
              {:type :mission-ended
               :priority :high
               :data {:unit-id unit-id :reason :reported}}]}))
```

### `request-orders-action`
No rally point assigned, request orders from Lieutenant.

```clojure
(defn request-orders-action [ctx]
  (let [unit-id (get-in ctx [:entity :fsm-data :unit-id])]
    {:events [{:type :unit-needs-rally-point
               :priority :high
               :data {:unit-id unit-id
                      :coords (get-in ctx [:entity :fsm-data :position])}}]}))
```

---

## Creation Function

```clojure
(defn create-disembark-and-rally-data
  "Create FSM data for disembark-and-rally mission."
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
```

---

## Beach Landing Coordination

When transport unloads multiple armies:

1. **Staggered Unloading**: Transport unloads one army at a time
2. **Rally Point Spread**: Different rally points to avoid congestion
3. **Beach Clearing**: First priority is to move off beach for next army

```
Transport → Beach1 → Rally1
                  → Beach2 → Rally2
                  → Beach3 → Rally3
```

---

## Lieutenant Handoff

The army transitions from the original Lieutenant (who built/loaded it) to the new Lieutenant (who controls the beachhead territory):

1. Transport belongs to original Lieutenant
2. Upon landing, army assigned to new Lieutenant
3. New Lieutenant assigns rally point and subsequent missions

---

## Relationship to Hurry-Up-And-Wait

Similar structure, with additions:
- Initial disembark state
- Lieutenant handoff/registration
- Beach-specific logic (clear beach quickly)

---

## Open Questions (Tentative)

1. Who assigns rally points - transport, original Lieutenant, or new Lieutenant?
2. How to coordinate unloading order with rally point assignment?
3. What if beach is contested (enemies nearby)?
4. Should army defend beach while others unload?
5. Priority: clear beach vs. move to rally point vs. explore?
6. What happens if new Lieutenant doesn't exist yet? (first army creates it?)
