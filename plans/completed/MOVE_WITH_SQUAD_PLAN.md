# Move-with-Squad FSM Plan

**STATUS: READY FOR IMPLEMENTATION**

## Overview

Army mission to move as part of a squad toward a target. Used during squad `:moving` state. The squad coordinates movement; individual armies execute move orders while staying cohesive with the group.

---

## Behavioral Requirements

1. **Follow Squad Orders**: Execute movement orders issued by squad
2. **Stay Cohesive**: Don't get too far from squad center/other units
3. **Sidestepping**: Navigate around obstacles while maintaining formation
4. **Report Position**: Keep squad informed of current position
5. **Transition Ready**: Be ready to transition to attack when squad orders

**Terminal Conditions:**
- Squad transitions to `:attacking` state (mission changes to attack-city)
- Squad disbanded or army reassigned

---

## FSM States

```
:awaiting-orders  →  Waiting for movement order from squad
       ↓
:executing-move   →  Moving to ordered position
       ↓
:awaiting-orders  →  Move complete, wait for next order
```

This is an **order-driven FSM** - it cycles between waiting and executing rather than having a fixed goal.

---

## FSM Transitions

```clojure
(def move-with-squad-fsm
  [[:awaiting-orders
     [squad-disbanded?     [:terminal :disbanded]    terminal-disbanded-action]
     [squad-attacking?     [:terminal :attack-mode]  terminal-attack-action]
     [has-move-order?      :executing-move           accept-order-action]
     [always               :awaiting-orders          nil]]
   [:executing-move
     [stuck?               [:terminal :stuck]        terminal-action]
     [at-ordered-position? :awaiting-orders          report-position-action]
     [can-move-toward?     :executing-move           move-toward-action]
     [needs-sidestep?      :executing-move           sidestep-action]
     [move-blocked?        :awaiting-orders          report-blocked-action]]])
```

---

## FSM Data Structure

```clojure
{:fsm move-with-squad-fsm
 :fsm-state :awaiting-orders
 :fsm-data {:mission-type :move-with-squad
            :position [row col]
            :ordered-position nil         ; set when order received
            :squad-id id
            :unit-id id                   ; for Lieutenant tracking
            :recent-moves [[r c] ...]}
 :event-queue []}
```

---

## Squad Coordination Model

### Option A: Squad Issues Individual Orders
Squad calculates path to target, issues move orders to each army:
```clojure
;; Squad posts to army's event queue
{:type :move-order
 :data {:target [row col]}}
```

### Option B: Squad Broadcasts Next Waypoint
Squad announces next waypoint, armies independently pathfind:
```clojure
;; Squad updates shared waypoint
{:type :squad-waypoint
 :data {:waypoint [row col]}}
```

### Option C: Follow-the-Leader
Armies follow the squad leader (first army), maintaining relative positions.

**Tentative Choice**: Option A - explicit move orders for predictable behavior.

---

## Guards

### `has-move-order?`
```clojure
(defn has-move-order? [ctx]
  (context/has-event? ctx :move-order))
```

### `squad-attacking?`
```clojure
(defn squad-attacking? [ctx]
  (context/has-event? ctx :squad-attacking))
```

### `at-ordered-position?`
```clojure
(defn at-ordered-position? [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        ordered (get-in ctx [:entity :fsm-data :ordered-position])]
    (= pos ordered)))
```

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

### `terminal-disbanded-action`
Called when squad is disbanded. Notifies Lieutenant.

```clojure
(defn- terminal-disbanded-action [ctx]
  (let [unit-id (get-in ctx [:entity :fsm-data :unit-id])]
    {:events [{:type :mission-ended
               :priority :high
               :data {:unit-id unit-id :reason :disbanded}}]}))
```

### `terminal-attack-action`
Called when squad transitions to attack. Notifies Lieutenant of mission change.

```clojure
(defn- terminal-attack-action [ctx]
  (let [unit-id (get-in ctx [:entity :fsm-data :unit-id])]
    {:events [{:type :mission-ended
               :priority :high
               :data {:unit-id unit-id :reason :attack-mode}}]}))
```

### `accept-order-action`
Pop move order from queue, set as current target.

```clojure
(defn accept-order-action [ctx]
  (let [[event _] (engine/pop-event (:entity ctx))
        target (get-in event [:data :target])]
    {:ordered-position target}))
```

### `report-position-action`
Notify squad of arrival at ordered position.

```clojure
(defn report-position-action [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])]
    {:ordered-position nil  ; clear order
     :events [{:type :unit-position-report
               :priority :normal
               :data {:unit-id (get-in ctx [:entity :unit-id])
                      :coords pos}}]}))
```

### `report-blocked-action`
Notify squad that movement is blocked.

```clojure
(defn report-blocked-action [ctx]
  {:ordered-position nil
   :events [{:type :unit-blocked
             :priority :high
             :data {:unit-id (get-in ctx [:entity :unit-id])
                    :coords (get-in ctx [:entity :fsm-data :position])}}]})
```

---

## Creation Function

```clojure
(defn create-move-with-squad-data
  "Create FSM data for move-with-squad mission."
  ([pos squad-id]
   (create-move-with-squad-data pos squad-id nil))
  ([pos squad-id unit-id]
   {:fsm move-with-squad-fsm
    :fsm-state :awaiting-orders
    :fsm-data {:mission-type :move-with-squad
               :position pos
               :ordered-position nil
               :squad-id squad-id
               :unit-id unit-id
               :recent-moves [pos]}}))
```

---

## Cohesion Considerations

To keep the squad together:
1. Squad waits for all units before issuing next move order
2. Or: Squad issues orders based on slowest unit's progress
3. Or: Armies have max-distance-from-squad-center constraint

---

## Resolved Questions

1. **How does squad decide movement path?**
   - Squad calculates path to target city
   - Issues individual move orders to each army (Option A - explicit orders)
   - See SQUAD_FSM_PLAN.md `issue-move-orders-action`

2. **Should armies move simultaneously or sequentially?**
   - Simultaneously - each army receives move order, executes independently
   - Squad waits for slowest army before issuing next move order
   - See SQUAD_FSM_PLAN.md "Stragglers" resolution

3. **How to handle one army getting blocked while others advance?**
   - Army reports `:unit-blocked` to squad
   - Squad may issue alternative order or wait
   - Army continues attempting movement or sidestepping

4. **What's the formation?**
   - No rigid formation - armies cluster around rally point then target
   - Squad issues waypoint-based orders; armies find paths independently
   - Natural clustering from converging on same destination

5. **Should armies auto-attack enemies encountered en route?**
   - No - armies follow move orders to maintain cohesion
   - Combat only occurs if enemy blocks the ordered position
   - Squad coordinates attack phase separately

6. **How to handle army destruction during movement?**
   - Squad tracks army status; marks destroyed army as `:lost`
   - Squad continues with remaining armies
   - If all armies lost, squad transitions to `[:terminal :failed]`
