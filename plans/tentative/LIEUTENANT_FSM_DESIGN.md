# Lieutenant FSM Design

**STATUS: TENTATIVE - PRELIMINARY**

## Overview

The Lieutenant manages concurrent concerns that don't fit a simple linear state machine. This document proposes an event-driven architecture with condition flags, and assesses all subordinate FSMs implied by the Lieutenant's responsibilities.

---

## Why Not a Linear FSM?

The current implementation is strictly sequential:
```
:start-exploring-coastline → :start-exploring-interior → :recruiting-for-transport → :waiting-for-transport
```

Problems with this approach:
1. **Conquest is reactive** - Triggered by `:free-city-found` events, not by reaching a state
2. **Multiple squads can operate simultaneously** - Not a single "conquest" phase
3. **Staging overlaps with conquest** - Armies go to beaches after conquest, not in a separate phase
4. **Defense is ongoing** - Sentries, fighters, patrol boats run continuously
5. **Transport pipeline is cyclic** - Load → sail → unload → return → load...

---

## Proposed Architecture: Event-Driven with Condition Flags

### Core Loop

```clojure
(defn process-lieutenant [lieutenant]
  (-> lieutenant
      (process-pending-events)      ; Handle all queued events
      (check-exploration-status)    ; Update exploration-complete? flag
      (check-production-needs)      ; Decide what cities should produce
      (update-transport-states)     ; Advance transport FSMs
      (update-squad-states)))       ; Advance squad FSMs
```

### Condition Flags

```clojure
{:exploration-complete? false      ; All land discovered
 :beaches-known? false             ; At least one beach found
 :conquest-active? false           ; At least one squad operating
 :transport-fleet-ready? false     ; 3 transports exist
 :defense-deployed? false          ; Sentries in position
 :fighter-patrol-active? false     ; Fighters patrolling
 :patrol-boats-active? false}      ; Patrol boats exploring
```

### Production Decision Logic

Each turn, Lieutenant decides what each city produces based on priorities:

```clojure
(defn production-priority [lieutenant city]
  (cond
    ;; Priority 1: Exploration (until 2+2 explorers)
    (need-explorers? lieutenant)
    :army

    ;; Priority 2: Conquest squads (when free cities known and squad needs armies)
    (squad-needs-armies? lieutenant)
    :army

    ;; Priority 3: Transport staging (armies for transport)
    (need-transport-armies? lieutenant)
    :army

    ;; Priority 4: Transport production (when 3+ armies waiting, <3 transports)
    (and (should-produce-transport? lieutenant)
         (coastal-city? city))
    :transport

    ;; Priority 5: Fighter patrol (when 3+ cities, <3 fighters)
    (and (should-produce-fighters? lieutenant)
         (has-airport? city))
    :fighter

    ;; Priority 6: Patrol boats (when transports ready, <2 patrol boats)
    (and (should-produce-patrol-boats? lieutenant)
         (coastal-city? city))
    :patrol-boat

    ;; Default: Keep producing armies (for coastal sentries, future needs)
    :else :army))
```

---

## Event Handlers

### Unit Events

| Event | Handler |
|-------|---------|
| `:unit-needs-orders` | Assign mission based on current needs |
| `:mission-ended` | Update tracking, possibly reassign |
| `:unit-arrived` | Squad member arrived at rally point |

### Discovery Events

| Event | Handler |
|-------|---------|
| `:free-city-found` | Create squad to conquer it |
| `:beach-found` | Add to beaches list, possibly redirect staging |
| `:cells-discovered` | Update terrain knowledge |
| `:continent-found` | Report to General, update transport target |

### Squad Events

| Event | Handler |
|-------|---------|
| `:squad-assembled` | Transition squad to moving state |
| `:squad-arrived` | Transition squad to attacking state |
| `:city-conquered` | Add city, disband squad, armies to staging |
| `:squad-failed` | Form new squad, try again |

### Transport Events

| Event | Handler |
|-------|---------|
| `:transport-loaded` | Send transport to target |
| `:transport-arrived` | Begin unloading |
| `:transport-unloaded` | Spawn Lieutenant, return transport |
| `:transport-returned` | Begin loading next wave |

### General Events

| Event | Handler |
|-------|---------|
| `:invade-continent` | Direct next transport to specified target |

---

## Lieutenant's Subordinate Entities

The Lieutenant manages several types of entities, each with their own FSM:

### 1. Squads
- Created when `:free-city-found` received
- Contains list of assigned armies
- Has own FSM: assembling → moving → attacking → disbanded

### 2. Transports
- Produced when 3+ armies waiting
- Has own FSM: loading → sailing → unloading → returning
- Lieutenant tracks up to 3

### 3. Direct Reports (Armies)
- Track mission type, status, location
- Armies run their own FSMs
- Report events back to Lieutenant

### 4. Fighters
- Patrol FSM: launching → flying-out → returning → landing
- Lieutenant tracks up to 3

### 5. Patrol Boats
- Exploration FSM: exploring world
- Lieutenant tracks up to 2

---

## Implied FSMs Assessment

### Already Defined (Ready)

| FSM | Status | Updates Needed |
|-----|--------|----------------|
| Coastline Explorer | Implemented | Add `:beach-found` event generation (see below) |
| Interior Explorer | Ready | None identified |
| Hurry-Up-And-Wait | Ready | None identified |

#### Coastline Explorer Update Required

The coastline explorer currently reports `:cells-discovered` and `:free-city-found` events. It needs to also detect and report beaches.

**Beach Detection Logic to Add:**

```clojure
(defn- find-beach-at-sea-cell
  "Check if sea-cell has 3+ adjacent land cells without cities.
   Returns beach data or nil."
  [sea-cell]
  (let [adjacent-land (map-utils/get-matching-neighbors
                        sea-cell @atoms/game-map map-utils/neighbor-offsets
                        #(and (= :land (:type %))
                              (not= :city (:type %))))
        beach-cells (vec adjacent-land)]
    (when (>= (count beach-cells) 3)
      {:sea-cell sea-cell
       :beach-cells beach-cells
       :capacity (count beach-cells)})))

(defn- find-adjacent-beach
  "Find a beach (sea cell with 3+ land neighbors) adjacent to pos."
  [pos]
  (let [adjacent-sea (map-utils/get-matching-neighbors
                       pos @atoms/game-map map-utils/neighbor-offsets
                       #(= :sea (:type %)))]
    (first (keep find-beach-at-sea-cell adjacent-sea))))

(defn- make-beach-found-event
  "Create a :beach-found event."
  [beach-data]
  {:type :beach-found
   :priority :normal
   :data beach-data})
```

**Integration:** Add beach checking to `follow-coast-action` and `skirt-city-action`:
```clojure
;; In events building:
beach (find-adjacent-beach pos)
events (cond-> [(make-cells-discovered-event pos)]
         free-city (conj (make-free-city-event free-city))
         beach (conj (make-beach-found-event beach)))
```

### Already Defined (Tentative)

| FSM | Status | Updates Needed |
|-----|--------|----------------|
| Rally-to-Squad | Tentative | Clarify rally point assignment |
| Attack-City | Tentative | None identified |
| Defend-City | Tentative | May not be needed if armies go to staging |
| Move-With-Squad | Tentative | Squad coordination details |
| Board-Transport | Tentative | Multi-round loading |
| Disembark-And-Rally | Tentative | New Lieutenant reporting |

### NEW FSMs Needed

| FSM | Entity | Purpose |
|-----|--------|---------|
| **Squad FSM** | Squad | Manage squad lifecycle |
| **Transport FSM** | Transport | Manage transport cycle |
| **Fighter Patrol FSM** | Fighter | Random patrol pattern |
| **Patrol Boat FSM** | Patrol Boat | World exploration |
| **Coast Sentry FSM** | Army | Sentry duty on coast (or reuse Hurry-Up-And-Wait?) |

---

## Squad FSM (NEW)

### Overview
Squad coordinates multiple armies to conquer a free city.

### States
```
:assembling  →  Waiting for armies to rally
     ↓
:moving      →  All armies present, moving toward city
     ↓
:attacking   →  Adjacent to city, armies attacking
     ↓
:disbanded   →  City conquered, armies released to staging
```

### FSM Transitions
```clojure
(def squad-fsm
  [[:assembling  assembly-complete?   :moving      begin-movement-action]
   [:assembling  assembly-timeout?    :moving      begin-with-partial-action]
   [:assembling  always               :assembling  nil]

   [:moving      at-target-city?      :attacking   begin-attack-action]
   [:moving      always               :moving      advance-squad-action]

   [:attacking   city-conquered?      :disbanded   disband-success-action]
   [:attacking   squad-destroyed?     :disbanded   disband-failure-action]
   [:attacking   always               :attacking   continue-attack-action]])
```

### Data Structure
```clojure
{:fsm squad-fsm
 :fsm-state :assembling
 :squad-id id
 :target-city [r c]
 :rally-point [r c]
 :armies [{:unit-id id :status :rallying|:present|:attacking}]
 :assembly-deadline round-number}
```

---

## Transport FSM (NEW)

### Overview
Transport manages the invasion cycle: load armies, sail, unload, return.

### States
```
:loading     →  At home beach, armies boarding
     ↓
:sailing     →  En route to target (exploring or directed)
     ↓
:unloading   →  At beachhead, armies disembarking
     ↓
:returning   →  Sailing back to home base
     ↓
:loading     →  (cycle repeats)
```

### FSM Transitions
```clojure
(def transport-fsm
  [[:loading    fully-loaded?         :sailing     depart-action]
   [:loading    always                :loading     load-army-action]

   [:sailing    at-destination?       :unloading   begin-unload-action]
   [:sailing    exploring-found-land? :scouting    scout-coast-action]
   [:sailing    always                :sailing     sail-action]

   [:scouting   found-beach?          :unloading   begin-unload-action]
   [:scouting   always                :scouting    continue-scout-action]

   [:unloading  fully-unloaded?       :returning   depart-home-action]
   [:unloading  always                :unloading   unload-army-action]

   [:returning  at-home-beach?        :loading     arrive-home-action]
   [:returning  always                :returning   sail-action]])
```

### Data Structure
```clojure
{:fsm transport-fsm
 :fsm-state :loading
 :transport-id id
 :home-beach {:sea-cell [r c] :beach-cells [...]}
 :target {:type :directed|:exploring :coords [r c]|nil}
 :cargo [{:unit-id id}]  ; up to 6 armies
 :spawned-lieutenant-id nil}  ; set after first unload
```

---

## Fighter Patrol FSM (NEW)

### Overview
Fighter patrols in random directions, reporting sightings.

### States
```
:launching    →  Taking off from city
     ↓
:flying-out   →  Flying away from base in random direction
     ↓
:returning    →  Flying back to origin city
     ↓
:landing      →  At city, brief pause
     ↓
:launching    →  (cycle repeats)
```

### FSM Transitions
```clojure
(def fighter-patrol-fsm
  [[:launching   clear-of-city?       :flying-out   pick-direction-action]
   [:launching   always               :launching    takeoff-action]

   [:flying-out  fuel-half?           :returning    turn-back-action]
   [:flying-out  enemy-spotted?       :flying-out   report-and-sidestep-action]
   [:flying-out  always               :flying-out   fly-action]

   [:returning   at-base?             :landing      land-action]
   [:returning   enemy-spotted?       :returning    report-and-sidestep-action]
   [:returning   always               :returning    fly-toward-base-action]

   [:landing     refueled?            :launching    nil]
   [:landing     always               :landing      nil]])
```

---

## Patrol Boat FSM (NEW)

### Overview
Patrol boat explores the entire world, reporting findings.

### States
```
:exploring    →  Moving through unexplored sea
     ↓
:fleeing      →  Enemy detected, moving away
     ↓
:exploring    →  (continues)
```

### FSM Transitions
```clojure
(def patrol-boat-fsm
  [[:exploring  enemy-adjacent?       :fleeing     flee-and-report-action]
   [:exploring  unexplored-nearby?    :exploring   explore-action]
   [:exploring  always                :exploring   circumnavigate-action]

   [:fleeing    safe-distance?        :exploring   resume-explore-action]
   [:fleeing    always                :fleeing     flee-action]])
```

---

## Coast Sentry FSM (NEW or Reuse?)

### Option A: Reuse Hurry-Up-And-Wait
- Army uses hurry-up-and-wait to reach coast position
- Upon arrival, enters sentry mode with wake condition: enemy adjacent
- When woken, reports to Lieutenant and fights

### Option B: Dedicated Coast Sentry FSM
```
:moving       →  Moving to assigned coast position
     ↓
:on-station   →  In sentry mode at coast
     ↓
:engaging     →  Enemy detected, fighting
```

**Recommendation:** Option A (reuse) with sentry wake conditions is simpler.

---

## Updates to Existing Tentative Plans

### 1. Defend-City (Reassess)
Per the Lieutenant plan, armies after conquest go to **transport staging**, not city defense. The defend-city FSM may not be needed, OR it could be repurposed for:
- Armies assigned as coastal sentries
- Future: defending against enemy siege

### 2. Board-Transport (Update)
Add multi-round loading details:
- Armies on beach cells board first
- Queued armies (one cell back) advance as space opens
- Transport waits until full (6 armies) or timeout

### 3. Coastline Explorer (Update - Ready Plan)
Add beach detection and reporting:
- Check each sea cell for 3+ adjacent land cells without cities
- Report `:beach-found` with sea cell, beach cells, capacity

### 4. Rally-to-Squad (Clarify)
Answer open questions:
- Rally point is near target city, chosen by Lieutenant for army convergence
- Squad assigns general area, armies fill in available cells
- Use queuing if rally point congested

---

## Summary of All FSMs

### Army FSMs
| FSM | Purpose | Status |
|-----|---------|--------|
| Coastline Explorer | Map coastline, find beaches/cities | Ready (needs update) |
| Interior Explorer | Map interior, find cities | Ready |
| Hurry-Up-And-Wait | Move to position, sentry mode | Ready |
| Rally-to-Squad | Join squad at rally point | Tentative |
| Move-With-Squad | Move with squad toward target | Tentative |
| Attack-City | Attack city with squad | Tentative |
| Defend-City | Defend position | Tentative (reassess) |
| Board-Transport | Board transport at beach | Tentative (update) |
| Disembark-And-Rally | Disembark, join new Lieutenant | Tentative |
| Coast Sentry | Sentry duty on coast | Use Hurry-Up-And-Wait |

### Other Unit FSMs
| FSM | Unit Type | Status |
|-----|-----------|--------|
| Fighter Patrol | Fighter | NEW |
| Patrol Boat Explore | Patrol Boat | NEW |

### Command Entity FSMs
| FSM | Entity | Status |
|-----|--------|--------|
| Lieutenant | Lieutenant | Event-driven (this doc) |
| Squad | Squad | NEW |
| Transport | Transport | NEW |

---

## Next Steps

1. Create SQUAD_FSM_PLAN.md
2. Create TRANSPORT_FSM_PLAN.md
3. Create FIGHTER_PATROL_FSM_PLAN.md
4. Create PATROL_BOAT_FSM_PLAN.md
5. Update COASTLINE_EXPLORER with beach detection
6. Update BOARD_TRANSPORT with multi-round loading
7. Reassess DEFEND_CITY purpose
