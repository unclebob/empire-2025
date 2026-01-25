# Commanding General Implementation Plan

## Overview

Replace gutted computer AI with a table-driven FSM system using a hierarchical command structure: General → Lieutenants → Squads/Units. Subordinates report to commanders via prioritized event queues. The General drives processing top-down each turn.

---

## Command Hierarchy

```
General FSM (strategic)
├── Creates and commands Lieutenants
├── Processes own event queue first
│
└── Lieutenant FSMs (operational)
    ├── One per base/territory
    ├── Controls cities (naming, production, defense)
    ├── Contains direct-reports (polymorphic: units or squads)
    │
    └── direct-reports
        ├── Individual units (explorers)
        └── Squad FSMs (2-6 units with shared mission)
            └── Unit FSMs (tactical)
```

All entities share common FSM fields: `:fsm`, `:fsm-state`, `:fsm-data`, `:event-queue`. The mission FSM distinguishes units from squads.

---

## Early Game Flow

```
Game Start: 1 computer city
    ↓
City posts {:type :city-needs-orders :priority :high} to General
    ↓
General creates Lieutenant (explore/conquer mission), assigns city
    ↓
Lieutenant commands city: produce armies
    ↓
New army posts {:type :unit-needs-orders} to Lieutenant
    ↓
Lieutenant assigns explorer mission (coastline or interior)
    ↓
Explorer finds free city → posts {:type :free-city-found :data {:location [x y]}} to Lieutenant
    ↓
Lieutenant creates Squad (2-6 units) with attack-city mission
    ↓
Squad conquers → posts {:type :city-conquered} to Lieutenant
    ↓
Lieutenant names city, directs squad to defend (position around city, sentry mode)
    ↓
Lieutenant discovers appropriate beach + loads transport (6 armies)
    ↓
Base established → Lieutenant posts {:type :base-established} to General
    ↓
General creates new Lieutenant, assigns transport to new Lieutenant
    ↓
New Lieutenant lands → claims beachhead, begins explore/conquer
```

**Appropriate beach:** Cells adjacent to a sea cell from which a transport can unload at least three armies without moving.

**Base established:** Lieutenant has discovered appropriate beach AND built and loaded a full transport (6 armies).

---

## Event Queue System

### Event Structure

```clojure
{:type :keyword            ; :city-needs-orders, :unit-needs-orders, :free-city-found, etc.
 :priority :high           ; :high, :normal, :low - processed in priority order
 :data {...}               ; type-specific payload
 :from entity-id}          ; reporting entity
```

### Queue Behavior

Priority queue: high before normal before low. Within same priority, FIFO.

### Engine Functions (`fsm/engine.cljc`)

```clojure
(def priority-order {:high 0 :normal 1 :low 2})

(defn post-event [entity event]
  ;; Add event to entity's queue, maintaining priority order

(defn pop-event [entity]
  ;; Return [event updated-entity] with highest priority event removed

(defn peek-events [entity]
  ;; View queue without consuming
```

---

## Processing Order

Each computer turn, the General drives hierarchical processing:

```clojure
(defn process-computer-turn []
  (process-general general)
  (doseq [lieutenant (:lieutenants general)]
    (process-lieutenant lieutenant)
    (doseq [direct-report (:direct-reports lieutenant)]
      (process-direct-report direct-report)    ; polymorphic
      (when (squad? direct-report)             ; determined by mission FSM
        (doseq [unit (:units direct-report)]
          (step-unit unit))))))
```

---

## FSM Structure

Each state machine is a list of transitions:
```clojure
[current-state guard-fn new-state action-fn]
```
- `guard-fn`: `(context -> boolean)` - guards the transition
- `action-fn`: `(context -> fsm-data-updates or nil)` - executes on transition
- First matching transition wins (order matters)

All FSM-bearing entities store:
- `:fsm` - the transition table
- `:fsm-state` - current state (keyword or `[:terminal :reason]`)
- `:fsm-data` - mission-specific context (targets, mission-type, etc.)
- `:event-queue` - prioritized event queue

---

## Entity Structures

### General

```clojure
{:fsm general-fsm
 :fsm-state :awaiting-city       ; → :operational
 :fsm-data {}
 :event-queue []
 :lieutenants []}                ; refs to Lieutenant entities
```

**Events handled:**
- `:city-needs-orders` → create Lieutenant, assign city
- `:base-established` → create new Lieutenant with transport

### Lieutenant

```clojure
{:fsm lieutenant-fsm
 :fsm-state :initializing        ; → :exploring → :established
 :fsm-data {:mission-type :explore-conquer}
 :event-queue []
 :name "Alpha"                   ; for city naming: "Alpha-1", "Alpha-2"
 :cities []                      ; assigned city coords
 :direct-reports []              ; polymorphic: units or squads
 :free-cities-known []           ; reported but not yet targeted
 :beach-candidates []}           ; appropriate beaches discovered
```

**Events handled:**
- `:unit-needs-orders` → assign explorer mission or form squad
- `:free-city-found` → add to known list, spawn squad when ready
- `:coastline-mapped` → evaluate beach candidates
- `:city-conquered` → name city, reassign squad to defend

### Squad

```clojure
{:fsm squad-fsm
 :fsm-state :assembling          ; → :moving → :attacking → :defending
 :fsm-data {:target [x y]
            :mission-type :attack-city}
 :event-queue []
 :units []                       ; 2-6 units (armies, fighters, etc.)
 :lieutenant-id id}              ; for reporting back
```

**Transitions:**
- `:assembling` + all units present → `:moving`
- `:moving` + adjacent to target → `:attacking`
- `:attacking` + city conquered → `:defending`, post event to Lieutenant
- `:defending` → position units around city (no blocking exits), sentry mode

### Unit (Army Example)

```clojure
{:fsm explore-coastline-fsm
 :fsm-state :exploring
 :fsm-data {:mission-type :explore-coastline
            :start-pos [x y]
            :lieutenant-id id}
 :event-queue []
 ;; ... standard unit fields: :type, :owner, :hits, etc.
}
```

---

## Explorer Missions

### Coastline Explorer FSM

- Follow coastline (keep sea on one side)
- Post `:coastline-mapped` events for beach candidates
- Post `:free-city-found` when city discovered
- Continue mission after reporting
- Terminal when circuit complete or blocked

### Interior Explorer FSM

- Expand into unexplored territory
- Post `:free-city-found` when city discovered
- Continue mission after reporting
- Terminal when no more unexplored reachable

---

## New Files

### Core Infrastructure
| File | Purpose |
|------|---------|
| `src/empire/fsm/engine.cljc` | FSM execution: `step`, `terminal?`, event queue operations |
| `src/empire/fsm/context.cljc` | Build context maps for predicates/actions |

### Command Layer
| File | Purpose |
|------|---------|
| `src/empire/fsm/general.cljc` | General FSM and processing |
| `src/empire/fsm/lieutenant.cljc` | Lieutenant FSM and processing |
| `src/empire/fsm/squad.cljc` | Squad FSM |

### Mission Definitions
| File | Purpose |
|------|---------|
| `src/empire/fsm/missions/army.cljc` | Army FSMs: explore-coastline, explore-interior |
| `src/empire/fsm/missions/fighter.cljc` | Fighter FSMs: patrol, intercept |
| `src/empire/fsm/missions/ship.cljc` | Ship FSMs: patrol, hunt, escort |
| `src/empire/fsm/missions/transport.cljc` | Transport FSMs: ferry-armies |

### Tests (TDD - write before implementation)
| File | Purpose |
|------|---------|
| `spec/empire/fsm/engine_spec.clj` | Engine and event queue tests |
| `spec/empire/fsm/general_spec.clj` | General FSM tests |
| `spec/empire/fsm/lieutenant_spec.clj` | Lieutenant FSM tests |
| `spec/empire/fsm/squad_spec.clj` | Squad FSM tests |
| `spec/empire/fsm/missions/army_spec.clj` | Army mission tests |

---

## Implementation Phases

### Phase 0: Event Queue Infrastructure (TDD)
1. Write `spec/empire/fsm/engine_spec.clj` - event queue tests
2. Implement priority queue: `post-event`, `pop-event`, `peek-events`
3. Implement FSM stepping with event queue integration

### Phase 1: FSM Engine (TDD)
1. Extend `spec/empire/fsm/engine_spec.clj` - FSM execution tests
2. Implement `find-matching-transition`, `step`, `terminal?`
3. Implement `src/empire/fsm/context.cljc`

### Phase 2: General FSM (TDD)
1. Write `spec/empire/fsm/general_spec.clj`
2. Implement General: `:awaiting-city` → `:operational`
3. Handle `:city-needs-orders` → create Lieutenant

### Phase 3: Lieutenant FSM (TDD)
1. Write `spec/empire/fsm/lieutenant_spec.clj`
2. Implement Lieutenant states: `:initializing` → `:exploring` → `:established`
3. Handle `:unit-needs-orders`, `:free-city-found`, `:city-conquered`
4. Implement city naming and production control

### Phase 4: Squad FSM (TDD)
1. Write `spec/empire/fsm/squad_spec.clj`
2. Implement Squad states: `:assembling` → `:moving` → `:attacking` → `:defending`
3. Implement defense positioning (around city, no blocking exits, sentry mode)

### Phase 5: Army Explorer Missions (TDD)
1. Write `spec/empire/fsm/missions/army_spec.clj`
2. Implement `explore-coastline-fsm`
3. Implement `explore-interior-fsm`
4. Implement event posting to Lieutenant

### Phase 6: Integration
1. Modify `game_loop.cljc` - call General processing each computer turn
2. Modify `computer.cljc` - hierarchical processing
3. Modify unit creation - add FSM fields for computer units
4. Run full test suite

### Phase 7: Base Establishment
1. Implement beach candidate evaluation
2. Implement transport loading detection
3. Handle `:base-established` in General
4. New Lieutenant creation with transport assignment

### Future Phases
- Fighter, ship, transport missions
- Patrol boat coastline mapping (replaces army coastline explorers)
- Strategic map abstraction for General
- Lieutenant resource requests

---

## Integration Points

### Modify: `src/empire/game_loop.cljc`
```clojure
(defn start-computer-phase []
  (general/process-turn)  ;; Drives entire hierarchy
  ;; ...existing code for animation, etc.
```

### Modify: `src/empire/player/production.cljc`
```clojure
(defn create-base-unit [item owner]
  ;; For computer units, add FSM fields and post to Lieutenant
  ;; :fsm nil :fsm-state nil :fsm-data {} :event-queue []
```

---

## Verification

1. **Unit tests**: `clj -M:spec spec/empire/fsm/`
2. **Full suite**: `clj -M:spec`
3. **Manual testing**: `clj -M:run` - observe computer behavior

---

## Strategic Architecture (Future)

### Geographic Lieutenants

Lieutenants are tied to **bases/landmasses**:
- First Lieutenant controls home base
- New Lieutenants created when bases are established (transport loaded)
- Lieutenants claim territory upon landing

### Reconnaissance Doctrine

| Unit | Role |
|------|------|
| **Armies** | Early game: coastline and interior exploration |
| **Patrol boats** | Later: trace coastlines (replaces army coastline explorers) |
| **Satellites** | Broad sweeps, reveal large areas quickly |
| **Fighters** | Fast area coverage, fuel-limited |

### Strategic Map (Future)

The General will eventually need a high-level map for planning:
- Theater status at a glance
- Resource summaries
- Connectivity for planning invasions
- Chokepoints for defensive planning
