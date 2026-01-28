# Rally-to-Squad FSM Plan

**STATUS: READY FOR IMPLEMENTATION**

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
  [[:moving
     [stuck?                  [:terminal :stuck]     terminal-action]
     [at-rally-point?         [:terminal :joined]    join-squad-action]
     [rally-blocked?          :sidestepping-rally    begin-sidestep-action]
     [can-move-toward?        :moving                move-toward-action]
     [needs-sidestep?         :moving                sidestep-action]]
   [:sidestepping-rally
     [stuck?          [:terminal :stuck]     terminal-action]
     [on-empty-land?  [:terminal :joined]    join-squad-action]
     [always          :sidestepping-rally    sidestep-to-land-action]]])
```

---

## Guards

### `at-rally-point?`
Army has reached the designated rally point.

```clojure
(defn at-rally-point? [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        rally (get-in ctx [:entity :fsm-data :rally-point])]
    (= pos rally)))
```

### `rally-blocked?`
Rally point is occupied by another unit.

```clojure
(defn rally-blocked? [ctx]
  (let [rally (get-in ctx [:entity :fsm-data :rally-point])
        cell (get-in (:game-map ctx) rally)]
    (and (not (at-rally-point? ctx))
         (adjacent-to-rally? ctx)
         (occupied? cell))))
```

### `on-empty-land?`
Army is on an empty land cell (used after sidestepping).

```clojure
(defn on-empty-land? [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        rally (get-in ctx [:entity :fsm-data :rally-point])]
    (and (adjacent? pos rally)
         (land-cell? ctx pos))))
```

### `stuck?`
No valid moves available.

```clojure
(defn stuck? [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        recent (get-in ctx [:entity :fsm-data :recent-moves])
        valid-moves (get-valid-army-moves ctx pos)]
    (or (empty? valid-moves)
        (stuck-in-loop? recent))))
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

### `move-toward-action`
Move one step toward rally point.

```clojure
(defn move-toward-action [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        rally (get-in ctx [:entity :fsm-data :rally-point])
        next-step (pathfind-next-step pos rally ctx)]
    {:move-to next-step
     :recent-moves (conj-limited (get-in ctx [:entity :fsm-data :recent-moves]) pos 10)}))
```

### `sidestep-action`
Sidestep around obstacle while still moving toward rally point.

```clojure
(defn sidestep-action [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        rally (get-in ctx [:entity :fsm-data :rally-point])
        valid-moves (get-valid-army-moves ctx pos)
        best-sidestep (best-sidestep-toward rally valid-moves)]
    {:move-to best-sidestep
     :recent-moves (conj-limited (get-in ctx [:entity :fsm-data :recent-moves]) pos 10)}))
```

### `sidestep-to-land-action`
Find any adjacent empty land cell near rally point.

```clojure
(defn sidestep-to-land-action [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        rally (get-in ctx [:entity :fsm-data :rally-point])
        adjacent-land (filter #(and (land-cell? ctx %)
                                    (not (occupied? ctx %))
                                    (adjacent? % rally))
                              (neighbors pos))]
    (when (seq adjacent-land)
      {:move-to (first adjacent-land)})))
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
- `:unit-arrived` event to squad (not just `:mission-ended`)

**Implementation**: Can share movement/sidestep logic with hurry-up-and-wait. The key difference is the terminal action notifying the squad.

---

---

## Squad Integration

When army arrives, squad updates its tracking:

```clojure
;; Squad receives :unit-arrived event
(defmethod handle-event :unit-arrived [squad event ctx]
  (let [unit-id (get-in event [:data :unit-id])
        coords (get-in event [:data :coords])]
    (-> squad
        (update-army-status unit-id :present)
        (update-army-position unit-id coords)
        (update :armies-present-count inc))))
```

**Army Status Flow**:
```
:rallying  →  Army assigned to squad, moving to rally point
:present   →  Army arrived at/near rally point
:moving    →  Squad in :moving state, army executing move orders
:attacking →  Squad in :attacking state, army attacking city
:lost      →  Army destroyed
```

---

## Resolved Questions

1. **Rally point = target city or separate location?**
   - Separate location. Lieutenant selects rally point 3-5 cells from target city, minimizing total army travel distance. This allows armies to assemble before the final approach.

2. **Congestion avoidance for multiple armies?**
   - Armies sidestep if rally point is occupied. An army counts as "joined" if it reaches the rally point OR an adjacent empty land cell. No specific slots assigned.

3. **Specific positions or just "get close"?**
   - Just "get close". Army is considered assembled when at rally point or adjacent. Squad tracks army status (`:rallying` → `:present`) but doesn't assign specific cells.
