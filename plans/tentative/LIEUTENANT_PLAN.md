# Lieutenant FSM Plan

**STATUS: TENTATIVE - IN DISCUSSION**

## Overview

The Lieutenant is the operational commander for a single landmass/base. Its mission is to:
1. Explore its base (coastline and interior)
2. Conquer free cities on the base
3. Prepare and execute invasions of other continents
4. Report discoveries and status to the General

The Lieutenant retains control of its transports and executes invasions. When armies land on a new continent, a new Lieutenant is spawned to command that beachhead.

---

## Mission Phases (Concurrent, Not Sequential)

Unlike the current implementation which is strictly sequential, the Lieutenant operates multiple concerns concurrently:

1. **Exploration** - Map coastline and interior, find beaches and free cities
2. **Conquest** - Capture discovered free cities with squads
3. **Staging** - Assemble armies at beaches for transport loading
4. **Invasion** - Load transports, sail to new continents, establish beachheads
5. **Defense** - Protect beaches and territory from enemy invasion (TBD)

---

## Exploration

### Coastline Exploration
- Commission 2 coastline explorers
- Explorers report:
  - `:cells-discovered` - terrain classification
  - `:free-city-found` - triggers conquest
  - `:beach-found` - suitable transport landing sites

### Interior Exploration
- Commission 2 interior explorers
- Explorers report:
  - `:cells-discovered` - terrain classification
  - `:free-city-found` - triggers conquest

### Exploration Complete Criteria
The continent is fully explored when:
- All unexplored cells adjacent to known territory are sea cells
- No unexplored cell is orthogonal to any land cell
- This means all land on the continent has been discovered

When exploration is complete, explorers are released to transport waiting.

---

## Conquest

### Triggering Conquest
When a `:free-city-found` event is received, the Lieutenant:
1. Creates a squad targeting that city
2. Populates the squad from available armies

### Squad Composition
- **Target size**: 3-5 armies
- **Time threshold**: ~10 rounds to assemble
- **Sources**:
  - Newly produced armies (`:unit-needs-orders`)
  - Terminated mission armies (`:mission-ended`)
  - Armies whose missions ended (explorers that got stuck, etc.)

### Squad Assembly Logic
Lieutenant calculates:
1. Rally point selection (based on army positions and target city)
2. How many armies can reach rally point within 10 rounds:
   - Currently available (idle, terminated)
   - In transit (can be redirected)
   - In production (estimate completion time)
3. If 3+ armies can assemble → form squad

### Parallelism
- One squad per discovered free city
- Multiple squads can operate simultaneously
- No need to attack sequentially

### Post-Conquest
- **Success**: Surviving armies → transport waiting site
- **Failure**: Lieutenant forms new squad, tries again
- Conquered city added to Lieutenant's cities, starts producing

---

## Beach Discovery

### What is a Beach?
A beach is defined by a **sea cell** where:
- Adjacent to 3-6 land cells (beach cells)
- Beach cells do NOT have cities on them
- More beach cells = higher quality beach (up to 6)

### Why This Definition?
- Transport sits at the sea cell
- Armies stage on adjacent land cells (beach cells)
- 6 beach cells = all 6 armies can stage for boarding
- Transport capacity is 6 armies

### Beach Reporting
Coastline explorer identifies and reports:
```clojure
{:type :beach-found
 :data {:sea-cell [r c]
        :beach-cells [[r1 c1] [r2 c2] ...]  ; 3-6 adjacent land cells
        :capacity (count beach-cells)}}
```

### Lieutenant Beach Management
- Maintains list of beach candidates
- Ranks beaches by capacity (more beach cells = better)
- Assigns waiting armies to best beaches

---

## Transport Staging

### Staging Progression

**Phase 1: No beaches known yet**
- Armies waiting for transport → generic coastal staging area
- Use `get-waiting-area` to find empty coastal cell

**Phase 2: Beaches discovered**
- Armies → stationed at specific beach cells
- Prefer beaches with higher capacity

### Armies That Become "Waiting for Transport"
1. Conquest survivors (squad completes mission successfully)
2. Finished explorers (continent fully mapped)
3. Armies from terminated missions

### Beach Selection Logic

Best beach is a logistics optimization based on **convergence time**:

```
For each beach candidate:
  army_time = max travel time from army-producing cities to beach cells
  transport_time = travel time from transport-producing city to beach sea cell
  convergence_time = max(army_time, transport_time)

Best beach = minimize convergence_time
```

**Factors considered:**
1. Which cities are producing armies
2. Which city is producing the transport
3. Land pathfinding time for armies to beach cells
4. Sea pathfinding time for transport to beach sea cell

### Beach Assignment
- Lieutenant selects optimal beach based on convergence calculation
- Tracks which beach cells are occupied
- Assigns incoming armies to selected beach's cells

**Concentration strategy:**
- Fill one beach completely before using another
- When beach is full AND waiting for transport → start filling next beach
- Supports pipeline: Beach A loading transport → Beach B staging next wave

**Queuing when beach is full:**
- Armies fill beach cells first (up to beach capacity: 3-6)
- Overflow armies queue one cell back from beach
- As armies board transport, queued armies move up to beach cells

**Loading is multi-round:**
- Transport sits at sea cell
- Each round, adjacent armies can board (up to beach capacity per round)
- Queued armies advance to beach cells as space opens
- Full load of 6 armies may take 2+ rounds depending on beach capacity

---

## Coast Defense

Defense has three layers providing early warning and interception:

### Layer 1: Sentry Armies on Coast

**Trigger:** Beaches have been discovered

**Deployment:**
- Place sentry armies in gaps between beaches along the coastline
- Maximum gap size: 5 cells between sentries
- Sentries in sentry mode with wake condition: enemy adjacent

**Purpose:**
- Detect enemy landings
- Intercept enemy armies on beaches
- Provide ground-level early warning

**Wake response:**
- Report enemy presence to Lieutenant
- Engage enemy (fight)

**Production priority:**
- Coastal sentries are a **convenience, not necessity**
- Transport staging armies take priority
- Fill sentry gaps with surplus armies after transport needs met

**Sentry placement algorithm:**
```
For coastline between beaches:
  If gap > 5 cells:
    Place sentries every 5 cells to fill gap
```

### Layer 2: Fighter Patrol

**Trigger:** More than 2 conquered cities (3+ cities under control)

**Deployment:**
- Designate one city for fighter production
- Maintain fleet of no more than 3 fighters
- Patrol pattern: fly out in random direction → return to origin → repeat

**Purpose:**
- Aerial reconnaissance
- Early warning of approaching enemy fleets
- Can spot enemy transports far from coast

**Enemy encounter response:**
- Report sighting to Lieutenant
- Continue patrol (do not engage)
- Sidestep if necessary to avoid combat

**Fighter patrol FSM:**
```
:launching     - Taking off from city
:flying-out    - Flying random direction away from base
:returning     - Flying back to origin city
:landing       - At city, brief pause then repeat
```

### Layer 3: Patrol Boats

**Trigger:** Transport fleet has been produced (3 transports exist)

**Deployment:**
- Coastal city produces patrol boats
- Maintain fleet of no more than 2 patrol boats
- Mission: explore the whole world

**Purpose:**
- Long-range naval reconnaissance
- Map enemy coastlines and positions
- Discover all continents for General's strategic planning

**Patrol boat behavior:**
- Circumnavigate the world
- Report discovered continents and enemy positions
- Stay mobile, avoid engagement when possible

**Enemy encounter response:**
- Flee (disengage, move away)
- Report enemy position to Lieutenant
- Do not engage in combat

---

## Transport Production

**Trigger:** 3+ armies waiting for transport

**Location:** Nearest coastal city to the selected beach

**Fleet Management:**
- Maximum 3 transports at any time
- Lieutenant retains control of transports (does not hand off to General)
- Pipeline model:
  - Transport 1: Loading at beach
  - Transport 2: Sailing to target continent
  - Transport 3: Invading/unloading at destination
- This allows continuous invasion operations

**Production Decision:**
```
When waiting_army_count >= 3 AND transport_count < 3:
  Select best beach (logistics optimization)
  Find nearest coastal city to that beach
  Start transport production at that city
```

### Invasion Cycle

**Full transport cycle:**
```
1. Load 6 armies at home beach
2. Sail to target continent
3. Armies disembark onto beachhead
4. NEW Lieutenant created for beachhead continent
5. Disembarked armies report to NEW Lieutenant (disembark-and-rally mission)
6. Transport returns to original Lieutenant's base beach
7. Reload with next batch of armies
8. Repeat
```

**Transport mission modes:**
```
:loading            - At home beach, armies boarding
:directed-invasion  - Sailing to known continent (General's orders)
:exploring          - Searching for new continent (default when no directive)
:unloading          - At beachhead, armies disembarking
:returning          - Sailing back to home base
```

**Exploration mode (default):**
- No directive from General → transport explores
- Sail away from home base
- Scan for new landmass
- Upon finding land → scout coastline for suitable beach
- Find beach (sea cell with 3+ adjacent land cells) → unload armies

**Directed invasion mode:**
- General commands: "Invade continent X at beach Y"
- Lieutenant sends loaded transport to specified location
- Unload at directed beach

**New Lieutenant Creation:**
- When first army disembarks, new Lieutenant is spawned for that continent
- Subsequent armies from same transport report to that Lieutenant
- New Lieutenant begins its own explore/conquer/prepare cycle

### General Interaction

**General may direct invasions:**
- General knows about discovered continents (from all Lieutenants)
- General can command: "Invade continent X"
- Lieutenant sends next available transport to directed target

**Without General directive:**
- Lieutenant sends transports to explore
- Transports find new continents independently
- Lieutenant reports discovered continents to General

**New Lieutenant Creation:**
- Original Lieutenant spawns new Lieutenant when armies disembark
- New Lieutenant takes command of beachhead continent
- Reports to General as a new operational commander

### Reporting to General

**Events Lieutenant sends to General:**

1. **`:continent-discovered`** - Transport found new landmass
   ```clojure
   {:type :continent-discovered
    :data {:continent-id id
           :discovery-coords [r c]
           :discovered-by :transport-id}}
   ```

2. **`:lieutenant-spawned`** - New Lieutenant created at beachhead
   ```clojure
   {:type :lieutenant-spawned
    :data {:new-lieutenant-id id
           :continent-id id
           :parent-lieutenant-id id
           :beachhead-coords [r c]}}
   ```

3. **`:continent-status`** - Periodic status update
   ```clojure
   {:type :continent-status
    :data {:lieutenant-id id
           :exploration-complete? bool
           :cities-controlled count
           :free-cities-known count
           :armies-available count
           :transports count}}
   ```

**Events Lieutenant receives from General:**

1. **`:invade-continent`** - Directed invasion order
   ```clojure
   {:type :invade-continent
    :data {:target-continent-id id
           :target-beach [r c]}}
   ```

---

## Events Handled

### Already Implemented
- `:unit-needs-orders` - Assign mission to army
- `:mission-ended` - Update unit status, reassign if needed
- `:free-city-found` - Track discovered cities, trigger conquest
- `:city-conquered` - Add to cities list
- `:cells-discovered` - Update terrain knowledge
- `:coastline-mapped` - Track beach candidates

### Needed (Incoming)
- `:beach-found` - Detailed beach info with capacity (from coastline explorer)
- `:squad-mission-complete` - Squad completed conquest attempt
- `:transport-arrived` - Transport reached destination
- `:transport-loaded` - Transport finished loading armies
- `:continent-found` - Transport discovered new landmass
- `:invade-continent` - Directive from General

### Needed (Outgoing to General)
- `:continent-discovered` - Report new continent found
- `:lieutenant-spawned` - Report new Lieutenant created
- `:continent-status` - Periodic status update

---

## Data Structure Additions

Current Lieutenant already has:
```clojure
{:cities [...]
 :direct-reports [...]
 :free-cities-known [...]
 :beach-candidates [...]
 :known-coastal-cells #{}
 :known-landlocked-cells #{}}
```

Needed additions:
```clojure
{:squads []                    ; active conquest squads
 :beaches []                   ; detailed beach info with capacity
 :beach-assignments {}         ; beach-cell -> army-id mapping
 :waiting-armies []            ; armies staged for transport
 :exploration-complete? false  ; flag when continent mapped
 :transports []                ; fleet of up to 3 transports
                               ; each: {:id :state :location :cargo}
 :general-id id                ; parent General for reporting
 :spawned-lieutenants []       ; Lieutenants created from this one's invasions
 }
```

---

## FSM State Considerations

Current FSM is too linear:
```
:start-exploring-coastline → :start-exploring-interior → :recruiting-for-transport → :waiting-for-transport
```

Need a more flexible model that handles concurrent concerns:
- Exploration ongoing
- Conquest squads forming/operating
- Staging progressing
- Possibly event-driven rather than strict state machine?

---

## Notes

- Lieutenant scope is ONE landmass/continent (its "base")
- Lieutenant retains control of its transports throughout invasion cycle
- Lieutenant spawns new Lieutenants when armies land on new continents
- General coordinates between Lieutenants and may direct invasions to known continents
- Fleet of up to 3 transports allows continuous pipeline: loading → sailing → invading
