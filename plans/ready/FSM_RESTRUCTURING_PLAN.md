# FSM Restructuring Plan

**STATUS: READY**

## Overview

The current FSM engine needs to support **super-states** (hierarchical state machines) to handle the Lieutenant's mode-based operation where the General can change the Lieutenant's overall directive.

---

## Current FSM Structure

The existing FSM engine uses a table-driven approach:

```clojure
(def some-fsm
  [[current-state  guard-fn  new-state  action-fn]
   [current-state  guard-fn  new-state  action-fn]
   ...])
```

**Engine behavior:**
1. Find first transition where `current-state` matches and `guard-fn` returns true
2. Execute `action-fn`
3. Transition to `new-state`

**Limitations:**
- Flat state space - no hierarchy
- No shared transitions across multiple states
- No concept of "mode" that affects behavior across states

---

## Requirements for Super-States

### 1. Hierarchical State Representation

States need to represent both super-state (mode) and sub-state:

```clojure
;; Option A: Composite state vector
[:normal-operations :processing-events]
[:attack-preparation :loading-transport]

;; Option B: Nested map
{:mode :normal-operations
 :sub-state :processing-events}

;; Option C: Dot notation keyword
:normal-operations.processing-events
```

### 2. Transitions at Multiple Levels

```
Super-state transitions:
  :normal-operations → :attack-preparation  (on :prepare-attack from General)

Sub-state transitions (within a super-state):
  :processing-events → :forming-squad  (on unassigned-free-city?)
```

### 3. Inherited Transitions

Transitions defined at super-state level apply to all sub-states:

```clojure
;; Any sub-state within :normal-operations can transition to :attack-preparation
[:normal-operations/*  received-prepare-attack?  :attack-preparation/init  begin-attack-prep]
```

---

## Discussion Points

### Q1: How should state be represented?

Options:
- A: Vector `[super sub]`
- B: Map `{:mode :sub-state}`
- C: Single keyword with convention `:mode.substate`
- D: Other?

### Q2: How should transitions be specified?

Options for matching "any sub-state within a super-state":
- A: Wildcard `:normal-operations/*`
- B: Just the super-state `:normal-operations` matches any sub-state
- C: Separate transition tables per super-state
- D: Other?

### Q3: Should super-states have entry/exit actions?

When transitioning from `:normal-operations` to `:attack-preparation`:
- Exit action for `:normal-operations`?
- Entry action for `:attack-preparation`?

### Q4: How deep should hierarchy go?

- Just two levels (super-state + sub-state)?
- Arbitrary depth?
- Recommendation: Two levels is likely sufficient for our needs

---

## Agreed Structure

### FSM Table Format: State-Grouped with 3-Tuples

**Current format (4-tuples, flat):**
```clojure
[[:moving-to-start  stuck?                        [:terminal :stuck] terminal-action]
 [:moving-to-start  at-destination-on-coast?      :following-coast   arrive-at-start-action]
 [:moving-to-start  at-destination-not-on-coast?  :seeking-coast     arrive-at-start-action]
 [:seeking-coast    stuck?         [:terminal :stuck] terminal-action]
 [:seeking-coast    on-coast?      :following-coast   follow-coast-action]
 ...]
```

**New format (state-grouped, 3-tuples):**
```clojure
[[:moving-to-start
   [stuck?                        [:terminal :stuck] terminal-action]
   [at-destination-on-coast?      :following-coast   arrive-at-start-action]
   [at-destination-not-on-coast?  :seeking-coast     arrive-at-start-action]]
 [:seeking-coast
   [stuck?         [:terminal :stuck] terminal-action]
   [on-coast?      :following-coast   follow-coast-action]
   [not-on-coast?  :seeking-coast     seek-coast-action]]
 ...]
```

**Benefits:**
- No repetition of state names
- Visual grouping of transitions by state
- Cleaner, more readable structure
- Natural foundation for super-states

### Super-State Declaration: 2-Tuple State Header

If the state header is a 2-tuple `[sub-state super-state]`, it declares super-state membership:

```clojure
[[:seeking-coast :exploring]
   [stuck?         [:terminal :stuck] terminal-action]
   [on-coast?      :following-coast   follow-coast-action]
   [not-on-coast?  :seeking-coast     seek-coast-action]]

[[:following-coast :exploring]
   [stuck?         [:terminal :stuck] terminal-action]
   [at-port-city?  :skirting-city     skirt-city-action]
   [always         :following-coast   follow-coast-action]]

[[:moving-to-target :traversing]
   [stuck?          [:terminal :stuck] terminal-action]
   [at-target?      [:terminal :arrived] arrival-action]
   [always          :moving-to-target  move-toward-action]]
```

**Semantics:**
- Single keyword header (e.g., `:seeking-coast`) = no super-state
- 2-tuple header (e.g., `[:seeking-coast :exploring]`) = sub-state within super-state
- Entity's `:fsm-state` stores sub-state; super-state derived from FSM structure
- Super-state transitions can be defined separately (shared across sub-states)

---

## Detailed Implementation Plan

### Phase 1: Engine Changes

**File:** `src/empire/fsm/engine.cljc`

#### 1.1 New Functions Required

| Function | Purpose |
|----------|---------|
| `parse-state-group` | Extract state, super-state, and transitions from a state group |
| `build-transition-index` | Build lookup map from state → {super-state, transitions} |
| `get-super-state` | Query super-state for a given sub-state |

#### 1.2 Functions to Modify

| Function | Changes |
|----------|---------|
| `find-matching-transition` | Use new indexed lookup; transitions are now 3-tuples |
| `step` | Destructure 3-tuples instead of 4-tuples |

#### 1.3 Tests to Write

- Parse state group with keyword header → returns `[state nil transitions]`
- Parse state group with 2-tuple header → returns `[sub-state super-state transitions]`
- Build transition index creates correct map structure
- Find matching transition works with new grouped format
- Step function works with 3-tuple transitions
- Get super-state returns correct value or nil

---

### Phase 2: Convert Existing FSMs

**Order:** Simplest first to validate approach.

| FSM | File | States | Transitions | Priority |
|-----|------|--------|-------------|----------|
| waiting-reserve-fsm | waiting_reserve.cljc | 2 | 4 | 1 (simplest) |
| interior-explorer-fsm | interior_explorer.cljc | 2 | 5 | 2 |
| lieutenant-fsm | lieutenant.cljc | 4 | 8 | 3 |
| coastline-explorer-fsm | coastline_explorer.cljc | 4 | 13 | 4 |
| squad-fsm | squad.cljc | TBD | TBD | 5 |
| general-fsm | general.cljc | TBD | TBD | 6 |

**Also:** Remove duplicate FSM definitions in `missions/army.cljc`

---

### Phase 3: Update Plan Documents

#### 3.1 Ready Plans

| File | Status |
|------|--------|
| `INTERIOR_EXPLORER_PLAN.md` | Update FSM examples to new format |
| `HURRY_UP_AND_WAIT_PLAN.md` | Update FSM examples to new format |

#### 3.2 Tentative Plans

| File | Status |
|------|--------|
| `RALLY_TO_SQUAD_PLAN.md` | Update FSM format |
| `ATTACK_CITY_PLAN.md` | Update FSM format |
| `MOVE_WITH_SQUAD_PLAN.md` | Update FSM format |
| `DISEMBARK_AND_RALLY_PLAN.md` | Update FSM format |
| `BOARD_TRANSPORT_PLAN.md` | Update FSM format |
| `DEFEND_CITY_PLAN.md` | Update FSM format |
| `SQUAD_FSM_PLAN.md` | Update FSM format |
| `TRANSPORT_FSM_PLAN.md` | Update FSM format |
| `FIGHTER_PATROL_FSM_PLAN.md` | Update FSM format |
| `PATROL_BOAT_FSM_PLAN.md` | Update FSM format |
| `LIEUTENANT_FSM_DESIGN.md` | Update FSM format, add super-state examples |
| `LIEUTENANT_PLAN.md` | Reference new FSM structure |

---

### Phase 4: Super-State Support (Future)

Deferred until basic restructuring validated. Will add:

1. Super-state transitions (apply to all sub-states within a super-state)
2. Entry/exit actions (hooks when changing super-states)
3. Tick actions (shared behavior that runs every step within a super-state)

---

### Execution Order

1. Write engine tests for new format (TDD)
2. Implement engine changes to pass tests
3. Convert waiting-reserve-fsm (simplest, validates approach)
4. Run full test suite
5. Convert remaining FSMs one at a time, testing after each
6. Update plan documents to reflect new format

---

### Risk Mitigation

- Engine could support both formats during transition for backward compatibility
- Convert one FSM at a time with full test verification between each
- Update plan documents only after code changes are validated

---

## Impact on Existing FSMs

### Army FSMs

**Strong case for super-states:** Some army states should check for free cities and report new territory (exploring), while others traverse known territory and should not (movement missions).

**Current situation:**
- Exploration FSMs manually include discovery reporting in every action
- Movement FSMs don't include it
- The distinction is implicit in the code, duplicated across actions

**Proposed super-states for armies:**

```
┌─────────────────────────────────────────────┐
│  :exploring                                 │
│    - Check for free cities every move       │
│    - Report :cells-discovered               │
│    - Report :beach-found                    │
│                                             │
│   [:exploring :seeking-coast]               │
│   [:exploring :following-coast]             │
│   [:exploring :skirting-city]               │
│   [:exploring :rastering]                   │
│   [:exploring :moving-to-start]             │
└─────────────────────────────────────────────┘

┌─────────────────────────────────────────────┐
│  :traversing                                │
│    - No discovery reporting                 │
│    - Just move to destination               │
│                                             │
│   [:traversing :moving-to-target]           │
│   [:traversing :rallying]                   │
│   [:traversing :boarding]                   │
│   [:traversing :sidestepping]               │
│   [:traversing :queuing-at-beach]           │
└─────────────────────────────────────────────┘
```

**Benefits:**
1. **DRY** - Discovery logic defined once at super-state level
2. **Explicit** - Clear which modes do discovery vs not
3. **Transitions** - Explorer finishes → transitions to `:traversing` mode
4. **Entry/exit actions** - Super-state entry could initialize discovery tracking

**Implication for FSM engine:**
The super-state could have its own action that runs *before* or *after* the sub-state action:
- Discovery check at super-state level (shared)
- Specific movement logic at sub-state level (unique)

### Lieutenant FSM
- Primary driver for this change
- Will use super-states for General's directives

### Squad FSM
- Probably stays flat (assembling → moving → attacking → disbanded)

### Transport FSM
- Could use super-states: `:outbound` (loading/sailing/unloading) vs `:inbound` (returning)

---

## Notes

*[Recording discussion points as we go]*

