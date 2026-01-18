# Computer Opponent Strategy Plan

## Overview

The computer opponent must play fairly - with the same constraints as the player in terms of units and fog-of-war visibility.

---

## Prerequisite: Combat Mechanics

Before implementing the computer AI, we must first implement attack, damage, and repair mechanics. This prerequisite is documented in detail below.

### Combat Rules (from DOC.md)

**Combat Resolution:**
> "Hits are traded off at 50% probability of a hit landing on one or the other units until one unit is totally destroyed. There is only 1 possible winner."

- Combat is **probabilistic rounds** until one unit dies
- Each round: 50% chance attacker hits, 50% chance defender hits
- **Strength** determines damage per hit:
  - Submarine: 3 damage per hit
  - Battleship: 2 damage per hit
  - All others: 1 damage per hit

**Repair:**
> "Ships can also dock in a user-owned city. Docked ships have damage repaired at the rate of 1 hit per turn."

**Unit Character Convention:**
- **Uppercase = Player/Friendly**: A, F, P, D, S, T, C, B, Z
- **Lowercase = Enemy/Computer**: a, f, p, d, s, t, c, b, z

### Combat Implementation Phases

#### Phase 1: Enemy Units in Test Infrastructure
- Add lowercase enemy unit characters to `test_utils.cljc`
- Add `get-test-enemy-unit` function

#### Phase 2: Fix Backtick Commands
- Uppercase = player units (`` `A ``, `` `D ``, etc.)
- Lowercase = enemy units (`` `a ``, `` `d ``, etc.)
- Modify `add-unit-at` to accept owner parameter

#### Phase 3: Unit-vs-Unit Combat
- Add `strength` to each unit module (submarine=3, battleship=2, others=1)
- Add to `combat.cljc`: `hostile-unit?`, `fight-round`, `resolve-combat`, `attempt-attack`

#### Phase 4: Integrate Combat into Movement
- Trigger combat when moving into enemy unit
- Winner occupies cell if terrain allows

#### Phase 5: Repair Mechanics
- Ships repair 1 hit/turn when docked in friendly city
- The :shipyard of the city will contain a list of the docked ships
- Add `repair-damaged-ships` to game loop
- fully repaired ships should wake up.  Damaged ships should remain asleep.

### Combat Implementation Status

- [ ] Phase 1: Enemy units in test infrastructure
- [ ] Phase 2: Fix backtick commands (uppercase=player, lowercase=enemy)
- [ ] Phase 3: Unit-vs-unit combat
- [ ] Phase 4: Integrate combat into movement
- [ ] Phase 5: Repair mechanics

---

## Codebase Analysis

### Current State: No Computer AI Exists

The game currently has **no computer opponent logic**. Computer entities are passive - they exist on the map but never move or act.

### Existing Infrastructure

| Component | File | Lines | Status |
|-----------|------|-------|--------|
| Game loop | `game_loop.cljc` | 302-324 | Player-only processing |
| State atoms | `atoms.cljc` | all | Has `computer-map` (line 83-85) but unused for AI |
| Fog-of-war | `movement/visibility.cljc` | 16-62 | Works for both sides |
| Round progression | `game_loop.cljc` | 209-223 | No computer turn phase |
| Unit movement | `movement.cljc` | all | Reusable for computer units |
| Combat | `combat.cljc` | all | Reusable for computer attacks |
| Production | `production.cljc` | all | Needs computer decision layer |

### Current Round Flow

```
advance-game (called every frame @ 30 FPS)
├─ if paused: do nothing
├─ if player-items empty:
│  └─ start-new-round
│     ├─ increment round-number
│     ├─ move-satellites
│     ├─ consume-sentry-fighter-fuel
│     ├─ remove-dead-units
│     ├─ update-production (player cities only)
│     ├─ reset-steps-remaining
│     ├─ wake-airport-fighters
│     └─ build player-items list
└─ if waiting-for-input: wait for user
└─ else: process-one-item (player only, up to 100 per frame)
```

### Key Functions to Leverage

- `movement/move-unit` - Execute unit movement
- `combat/attack` - Execute combat
- `production/set-production` - Set city production
- `map-utils/neighbors` - Get adjacent cells
- `map-utils/distance` - Calculate distances
- `visibility/update-combatant-map` - Already updates computer-map

---

## Strategic Concepts

### Production Strategy

- **Early game**: Prioritize armies for city capture and exploration
- **Mid game**: Build naval units once coastal cities are secured, fighters for reconnaissance
- **Late game**: Battleships and carriers for decisive force projection
- **Adaptive production**: Track enemy unit composition and counter (e.g., if player builds many transports, build destroyers/subs)

### Exploration

- **Fog-of-war management**: Send scouts (fighters, patrol boats) to reveal map
- **Frontier tracking**: Maintain list of unexplored edges, prioritize exploring toward likely enemy positions
- **Memory**: Remember last-known enemy positions even after fog covers them again

### Unit Movement Priorities

**Armies:**
- Capture nearest free cities first (visible on computer-map)
- Once free cities exhausted, advance toward enemy territory
- Protect critical cities with garrison

**Naval:**
- Transports: Load armies, deliver to enemy coastlines
- Combat ships: Escort transports, blockade enemy ports, hunt enemy transports

**Fighters:**
- Scout ahead of advancing forces
- Attack vulnerable units (transports, damaged ships)

### Tactical Algorithms

| Algorithm | Purpose |
|-----------|---------|
| **A\* pathfinding** | Route units efficiently avoiding threats |
| **Influence maps** | Track areas of control, identify contested zones |
| **Threat assessment** | Evaluate danger to each unit, retreat when outmatched |
| **Target prioritization** | Attack high-value targets (transports > battleships) |

---

## AI Implementation Plan

### Phase 1: Basic Infrastructure (Foundation)

**Goal**: Computer units move toward objectives each round.

#### 1.1 Add Computer Turn State Management

**File: `atoms.cljc`**
```clojure
(def computer-items (atom []))        ; List of computer units/cities to process
(def computer-turn (atom false))      ; Flag: true when processing computer turn
```

#### 1.2 Build Computer Items List

**File: `game_loop.cljc`** - New function
```clojure
(defn build-computer-items []
  "Build list of computer-owned cities and units to process this round."
  ;; Similar to build-player-items but filters for :computer owner
  )
```

#### 1.3 Integrate Computer Turn into Round Progression

**File: `game_loop.cljc`** - Modify `advance-game`

New flow:
```
advance-game
├─ if paused: do nothing
├─ if player-items empty AND computer-items empty:
│  └─ start-new-round (builds both player-items and computer-items)
├─ if player-items not empty:
│  └─ process player items (existing logic)
├─ else if computer-items not empty:
│  └─ process-computer-item
```

#### 1.4 Create Computer Decision Module

**New file: `src/empire/computer.cljc`**

```clojure
(ns empire.computer
  (:require [empire.atoms :as atoms]
            [empire.map-utils :as map-utils]
            [empire.movement :as movement]))

(defn decide-army-move [game-map computer-map position]
  "Decide where an army should move. Returns target cell or nil.")

(defn decide-ship-move [game-map computer-map position unit-type]
  "Decide where a ship should move. Returns target cell or nil.")

(defn decide-fighter-move [game-map computer-map position]
  "Decide where a fighter should move. Returns target cell or nil.")

(defn decide-production [game-map computer-map city-position]
  "Decide what a city should produce. Returns unit type keyword.")

(defn process-computer-unit [game-map computer-map position]
  "Process one computer unit's turn. Returns updated game-map.")
```

#### 1.5 Simple Movement Heuristics (Phase 1 Only)

**Armies:**
1. If adjacent to attackable target (player unit or city), attack
2. If free city visible on computer-map, move toward nearest
3. If player city visible, move toward nearest
4. Else move toward unexplored area

**Ships:**
1. If adjacent to attackable target, attack
2. Move toward nearest enemy unit/city visible
3. Patrol randomly if nothing visible

**Fighters:**
1. If adjacent to attackable target, attack
2. If fuel allows, move toward unexplored areas
3. Return to nearest city/carrier when fuel low

### Phase 2: Pathfinding and Threat Avoidance

**Goal**: Units navigate intelligently and avoid death.

#### 2.1 A* Pathfinding

**New file: `src/empire/pathfinding.cljc`**
- Implement A* that respects terrain (land/sea for unit types)
- Cache paths to avoid recalculation every frame
- Consider movement costs (all 1 for now, extensible)

#### 2.2 Threat Assessment

**Add to `computer.cljc`:**
```clojure
(defn threat-level [computer-map position]
  "Calculate threat level at position based on nearby enemy units.")

(defn safe-moves [computer-map position possible-moves]
  "Filter moves to avoid high-threat areas when unit is weak.")
```

#### 2.3 Retreat Logic

- Units with low hits retreat toward friendly cities
- Transports with armies avoid combat ships
- Fighters return to base when fuel < distance to nearest base

### Phase 3: Strategic Planning

**Goal**: Coordinate multiple units for complex operations.

#### 3.1 Invasion Planning

**New file: `src/empire/computer/strategy.cljc`**
```clojure
(defn plan-invasion [computer-map target-city]
  "Create invasion plan: which transports, which armies, which escorts.")

(defn assign-units-to-plan [game-map plan]
  "Assign specific units to roles in the plan.")
```

#### 3.2 Production Coordination

- Track what units are needed vs. what's being built
- Balance army production across cities
- Prioritize naval production at coastal cities

#### 3.3 Multi-Unit Tactics

- Escorts stay near transports
- Armies mass before attacking fortified positions
- Fighters provide cover for invasions

### Phase 4: Adaptive Responses

**Goal**: React to player strategy.

#### 4.1 Player Strategy Detection

```clojure
(defn detect-player-strategy [computer-map history]
  "Analyze player moves to detect patterns: naval rush, army spam, etc.")
```

#### 4.2 Counter-Strategy Selection

- If player building many transports → prioritize destroyers/subs
- If player army rushing → defend cities, build fighters
- If player turtling → build up overwhelming force

#### 4.3 Memory and Learning

- Remember where player units were last seen
- Track which cities player controls
- Anticipate player movement based on last known positions

---

## File Structure

```
src/empire/
├── computer/
│   ├── ai.cljc           ; Main AI coordination
│   ├── decisions.cljc    ; Unit decision logic
│   ├── pathfinding.cljc  ; A* and path caching
│   ├── strategy.cljc     ; High-level planning
│   └── threats.cljc      ; Threat assessment
```

Or simpler for Phase 1:
```
src/empire/
├── computer.cljc         ; All AI logic in one file initially
```

---

## Testing Strategy

Each phase needs comprehensive tests:

**Phase 1 Tests (`spec/empire/computer_spec.clj`):**
- `decide-army-move` returns valid adjacent cell
- `decide-army-move` prioritizes free cities over exploration
- `decide-army-move` attacks adjacent enemies
- `process-computer-unit` updates game-map correctly
- Computer turn processes all computer units
- Round progression includes computer turn

**Phase 2 Tests:**
- A* finds shortest valid path
- A* respects terrain (armies on land, ships on sea)
- Threat assessment identifies dangerous cells
- Units retreat when damaged

---

## Implementation Status

- [ ] **Phase 1: Basic Infrastructure**
  - [ ] Add `computer-items` and `computer-turn` atoms
  - [ ] Implement `build-computer-items`
  - [ ] Modify `advance-game` for computer turn
  - [ ] Create `computer.cljc` with basic decision functions
  - [ ] Implement simple army movement heuristics
  - [ ] Implement simple ship movement heuristics
  - [ ] Implement simple fighter movement heuristics
  - [ ] Implement basic production decisions
  - [ ] Write tests for all new functions

- [ ] **Phase 2: Pathfinding and Threat Avoidance**
  - [ ] Implement A* pathfinding
  - [ ] Add threat assessment
  - [ ] Add retreat logic
  - [ ] Write tests

- [ ] **Phase 3: Strategic Planning**
  - [ ] Implement invasion planning
  - [ ] Add production coordination
  - [ ] Add multi-unit tactics
  - [ ] Write tests

- [ ] **Phase 4: Adaptive Responses**
  - [ ] Implement player strategy detection
  - [ ] Add counter-strategy selection
  - [ ] Add memory system
  - [ ] Write tests
