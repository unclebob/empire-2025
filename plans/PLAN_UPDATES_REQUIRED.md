# Plan Updates Required

Assessment of changes needed based on recent implementation commits.

---

## Key Implementation Patterns Discovered

### 1. Terminal State Format
All FSMs use `[:terminal :reason]` format:
```clojure
[:terminal :stuck]
```

### 2. Terminal Action Pattern
When entering terminal state, emit `:mission-ended` event:
```clojure
(defn- terminal-action [ctx]
  (let [unit-id (get-in ctx [:entity :fsm-data :unit-id])]
    {:events [{:type :mission-ended
               :priority :high
               :data {:unit-id unit-id :reason :stuck}}]}))
```

### 3. Unit ID Tracking
FSM data must include `:unit-id` for Lieutenant tracking:
```clojure
{:fsm some-fsm
 :fsm-state :initial
 :fsm-data {:position pos
            :unit-id unit-id  ; <-- Required
            ...}}
```

### 4. Lieutenant Direct Report Structure
```clojure
{:unit-id id
 :coords [r c]
 :mission-type :explore-coastline
 :fsm-state :exploring
 :status :active}  ; or :ended with :end-reason
```

### 5. Lieutenant Event Handling
- `:mission-ended` → marks unit `:status :ended`, decrements count
- Counts tracked: `:coastline-explorer-count`, `:interior-explorer-count`, `:waiting-army-count`

### 6. Stuck Guard Pattern
Check every state for stuck condition first:
```clojure
[[:some-state  stuck?   [:terminal :stuck]  terminal-action]
 [:some-state  guard?   :next-state         action]
 ...]
```

---

## Ready Plans Status

### INTERIOR_EXPLORER_PLAN.md
**Status: IMPLEMENTED (differently)**

Implementation in `src/empire/fsm/interior_explorer.cljc`:
- States: `:moving-to-start` → `:exploring-interior` → `[:terminal :stuck]`
- Simpler than plan (no raster pattern, no diagonal preference)
- Plan's raster/diagonal features deferred

**Action**: Mark as partially implemented. Raster pattern could be Phase 2.

### HURRY_UP_AND_WAIT_PLAN.md
**Status: IMPLEMENTED (as waiting-reserve)**

Implementation in `src/empire/fsm/waiting_reserve.cljc`:
- Named `waiting-reserve` not `hurry-up-and-wait`
- States: `:moving-to-station` → `:holding` → `[:terminal :stuck]`
- Uses `:station` instead of `:destination`
- Simpler: no sidestepping-destination logic (holds at station, doesn't enter sentry)

**Action**: Mark as implemented with different name. Plan's sidestepping logic deferred.

---

## Tentative Plans - Required Updates

All tentative plans need these changes:

### 1. Add `:unit-id` to FSM Data
```clojure
:fsm-data {:mission-type :rally-to-squad
           :position [row col]
           :unit-id unit-id        ; <-- ADD THIS
           ...}
```

### 2. Add `stuck?` Guard + Terminal State
Every state needs stuck check as first transition:
```clojure
[[:moving  stuck?  [:terminal :stuck]  terminal-action]
 [:moving  ...     ...                 ...]]
```

### 3. Add `terminal-action`
```clojure
(defn- terminal-action [ctx]
  (let [unit-id (get-in ctx [:entity :fsm-data :unit-id])]
    {:events [{:type :mission-ended
               :priority :high
               :data {:unit-id unit-id :reason :stuck}}]}))
```

### 4. Creation Function Signature
Add unit-id parameter:
```clojure
(defn create-xxx-data
  ([pos] (create-xxx-data pos nil nil))
  ([pos target] (create-xxx-data pos target nil))
  ([pos target unit-id]
   ...))
```

---

## Specific Plan Updates

### RALLY_TO_SQUAD_PLAN.md
- Add `:unit-id` to fsm-data
- Add `stuck?` guard to `:moving` state
- Add terminal action with `:mission-ended` event
- Consider: Should also emit `:unit-arrived` on join?

### ATTACK_CITY_PLAN.md
- Add `:unit-id` to fsm-data
- Add `stuck?` guard to `:approaching` and `:attacking` states
- Terminal reasons: `:stuck`, `:conquered`, `:failed`
- Consider: Multiple terminal reasons need different events?

### DEFEND_CITY_PLAN.md
- Add `:unit-id` to fsm-data
- Add `stuck?` guard to `:moving` state
- Terminal state `[:terminal :defending]` should emit `:mission-ended` with reason `:defending`?
- Or: `:holding` state like waiting-reserve (never terminal while defending)

### MOVE_WITH_SQUAD_PLAN.md
- Add `:unit-id` to fsm-data
- Add `stuck?` guard to `:executing-move` state
- Order-driven FSM may not have natural terminal (waits for orders)
- Consider: Terminal when squad disbanded?

### BOARD_TRANSPORT_PLAN.md
- Add `:unit-id` to fsm-data
- Add `stuck?` guard to `:moving-to-coast` and `:boarding` states
- Terminal reasons: `:stuck`, `:boarded`, `:aborted`

### DISEMBARK_AND_RALLY_PLAN.md
- Add `:unit-id` to fsm-data
- Add `stuck?` guard to `:moving-inland` state
- Terminal reason `:reported` should emit what event?
- Consider: Should register with new Lieutenant via `:unit-needs-orders`

---

## New Event Types to Consider

Based on mission lifecycle:

| Event | From | To | Purpose |
|-------|------|----|---------|
| `:mission-ended` | Army FSM | Lieutenant | Mission complete/stuck |
| `:unit-arrived` | Rally FSM | Squad | Unit joined squad |
| `:city-conquered` | Attack FSM | Squad/Lt | City captured |
| `:attack-failed` | Attack FSM | Squad | Attack unsuccessful |
| `:unit-boarded` | Board FSM | Lieutenant | Army on transport |
| `:unit-disembarked` | Transport | Army | Trigger disembark mission |

---

## Naming Conventions

Implementation uses:
- `create-xxx-data` (not `create-xxx`)
- `:mission-ended` event (not `:mission-complete`)
- `[:terminal :reason]` state format
- `:station` for waiting-reserve (not `:destination`)
