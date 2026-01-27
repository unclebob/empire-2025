# Rally-to-Squad FSM Plan

**STATUS: TENTATIVE**

## Overview

Army mission to move to a designated rally point and join a squad. Used during squad `:assembling` state when the Lieutenant forms a squad to attack a free city.

---

## Behavioral Requirements

1. **Move to Rally Point**: Pathfind toward squad's rally position
2. **Sidestepping**: Navigate around friendly armies and cities
3. **Join Squad**: Report arrival to squad upon reaching rally point
4. **Handle Blocked Rally**: If rally point blocked, sidestep to adjacent empty land

**Terminal Conditions:**
- Reach rally point and join squad, OR
- Reach adjacent empty land after sidestepping blocked rally point

---

## FSM States

```
:moving  →  Pathfinding toward rally point
    ↓
    ├── [:terminal :joined]  →  At rally point, joined squad
    │
    └── :sidestepping-rally  →  Rally point blocked, finding nearby land
            ↓
        [:terminal :joined]  →  At nearby land, joined squad
```

---

## FSM Transitions

```clojure
(def rally-to-squad-fsm
  [;; Moving toward rally point
   [:moving  stuck?                  [:terminal :stuck]     terminal-action]
   [:moving  at-rally-point?         [:terminal :joined]    join-squad-action]
   [:moving  rally-blocked?          :sidestepping-rally    begin-sidestep-action]
   [:moving  can-move-toward?        :moving                move-toward-action]
   [:moving  needs-sidestep?         :moving                sidestep-action]

   ;; Sidestepping blocked rally point
   [:sidestepping-rally  stuck?          [:terminal :stuck]     terminal-action]
   [:sidestepping-rally  on-empty-land?  [:terminal :joined]    join-squad-action]
   [:sidestepping-rally  always          :sidestepping-rally    sidestep-to-land-action]])
```

---

## FSM Data Structure

```clojure
{:fsm rally-to-squad-fsm
 :fsm-state :moving
 :fsm-data {:mission-type :rally-to-squad
            :position [row col]
            :rally-point [row col]        ; designated assembly location
            :squad-id id                  ; squad to join
            :unit-id id                   ; for Lieutenant tracking
            :recent-moves [[r c] ...]}
 :event-queue []}
```

---

## Key Actions

### `terminal-action`
Called when stuck with no valid moves. Notifies Lieutenant.

```clojure
(defn- terminal-action [ctx]
  (let [unit-id (get-in ctx [:entity :fsm-data :unit-id])]
    {:events [{:type :mission-ended
               :priority :high
               :data {:unit-id unit-id :reason :stuck}}]}))
```

### `join-squad-action`
Notify squad that army has arrived and Lieutenant that mission ended.

```clojure
(defn join-squad-action [ctx]
  (let [unit-id (get-in ctx [:entity :fsm-data :unit-id])
        squad-id (get-in ctx [:entity :fsm-data :squad-id])
        pos (get-in ctx [:entity :fsm-data :position])]
    {:events [{:type :unit-arrived
               :priority :high
               :data {:unit-id unit-id :squad-id squad-id :coords pos}}
              {:type :mission-ended
               :priority :high
               :data {:unit-id unit-id :reason :joined}}]}))
```

---

## Creation Function

```clojure
(defn create-rally-to-squad-data
  "Create FSM data for rally-to-squad mission."
  ([pos rally-point squad-id]
   (create-rally-to-squad-data pos rally-point squad-id nil))
  ([pos rally-point squad-id unit-id]
   {:fsm rally-to-squad-fsm
    :fsm-state :moving
    :fsm-data {:mission-type :rally-to-squad
               :position pos
               :rally-point rally-point
               :squad-id squad-id
               :unit-id unit-id
               :recent-moves [pos]}}))
```

---

## Relationship to Hurry-Up-And-Wait

This mission is nearly identical to hurry-up-and-wait, with the addition of:
- Squad notification on arrival (`join-squad-action`)
- Squad ID tracking in fsm-data

**Consider**: Implementing as a variant or extension of hurry-up-and-wait.

---

## Open Questions (Tentative)

1. Should rally point be the squad's target city, or a separate assembly location?
2. If multiple armies rally, how to avoid congestion at rally point?
3. Does the squad assign specific positions, or just "get close"?
