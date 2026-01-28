# Lieutenant FSM Plan

**STATUS: READY FOR IMPLEMENTATION**

## Overview

The Lieutenant is the operational commander for a single landmass/base. Its mission is to:
1. Explore its base (coastline and interior)
2. Conquer free cities on the base
3. Prepare and execute invasions of other continents
4. Report discoveries and status to the General

The Lieutenant retains control of its transports and executes invasions. When armies land on a new continent, a new Lieutenant is spawned to command that beachhead.

---

## FSM Architecture

### Super-State Model

The Lieutenant uses a **single `:operational` super-state** with sub-handlers for concurrent concerns:

```clojure
(def lieutenant-fsm
  {:initial-state :initializing

   :states
   {:initializing
    [[has-city?  :operational  initialize-action]]

    :operational
    {:entry operational-entry-action
     :exit  operational-exit-action

     :sub-handlers
     {:exploration  handle-exploration
      :conquest     handle-conquest
      :staging      handle-staging
      :invasion     handle-invasion
      :defense      handle-defense}

     :transitions
     [[all-cities-lost?  [:terminal :defeated]  defeat-action]]}}})
```

### Sub-Handler Dispatch

Each turn, the Lieutenant:
1. Processes all incoming events from its queue (drain completely)
2. Invokes each sub-handler to manage its concern
3. Steps all owned squads (squads step their armies)
4. Steps all owned transports
5. Steps direct-reports (explorers, unassigned armies)
6. Steps fighters and patrol boats

```clojure
(defn process-lieutenant-turn [lieutenant ctx]
  (-> lieutenant
      (process-all-events ctx)       ; drain event queue
      (handle-exploration ctx)
      (handle-conquest ctx)
      (handle-staging ctx)
      (handle-invasion ctx)
      (handle-defense ctx)
      (step-squads ctx)              ; squads step their armies
      (step-transports ctx)
      (step-direct-reports ctx)      ; explorers, unassigned armies
      (step-fighters ctx)
      (step-patrol-boats ctx)))
```

---

## Terminology: Transport-Landings and Beaches

### Transport-Landing (Sea Cell)
A **transport-landing** is a sea cell where transports dock for loading/unloading:
- Must have 3-7 adjacent land cells
- Adjacent land cells must NOT contain cities
- Higher adjacent land count = better transport-landing
- Transports use transport-landings

### Beach (Land Cells)
A **beach** is the collection of land cells adjacent to a transport-landing:
- 3-7 land cells that armies use for staging and boarding
- Armies use beaches
- Each transport-landing has exactly one associated beach

### Data Structure
```clojure
{:transport-landing [r c]        ; the sea cell
 :beach [[r1 c1] [r2 c2] ...]    ; 3-7 adjacent land cells
 :capacity (count beach)}         ; number of land cells
```

### Reporting
Coastline explorer identifies and reports:
```clojure
{:type :transport-landing-found
 :data {:transport-landing [r c]
        :beach [[r1 c1] [r2 c2] ...]
        :capacity (count beach)}}
```

---

## Ownership and Stepping Hierarchy

### Entity Ownership
```
Lieutenant
  ├── Squads (owned, stepped by Lieutenant)
  │     └── Armies (owned by squad, stepped by squad)
  ├── Transports (owned, stepped by Lieutenant)
  ├── Explorers (direct-reports, stepped by Lieutenant)
  └── Unassigned Armies (direct-reports, stepped by Lieutenant)
```

### Stepping Rules
1. **Lieutenant steps squads** - each squad's FSM is advanced
2. **Squads step their armies** - squad advances each contained army's FSM
3. **Lieutenant does NOT step squad's armies directly**
4. **When squad dissolves** - Lieutenant takes possession of surviving armies

### Squad Dissolution
When a squad reaches a terminal state:
```clojure
;; Squad reports completion
{:type :squad-mission-complete
 :data {:squad-id id
        :result :success|:failed
        :surviving-armies [army-id-1 army-id-2 ...]}}

;; Lieutenant receives armies back
(defn handle-squad-dissolved [lieutenant squad-id surviving-armies]
  (-> lieutenant
      (update :squads #(remove-squad % squad-id))
      (update :direct-reports #(into % surviving-armies))))
```

---

## Exploration

### Coastline Exploration
- Commission 2 coastline explorers
- Explorers report:
  - `:cells-discovered` - terrain classification
  - `:free-city-found` - triggers conquest
  - `:transport-landing-found` - suitable transport docking sites

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

When exploration is complete, explorers are released to beach staging.

---

## Conquest

### Triggering Conquest
When a `:free-city-found` event is received, the Lieutenant:
1. Creates a squad targeting that city
2. Populates the squad from available armies (direct-reports)

### Squad Composition
- **Target size**: 3-5 armies
- **Assembly deadline**: 10 rounds from squad creation
- **Minimum to proceed**: 3 armies
- **If fewer than 3 at deadline**: Extend deadline (do not cancel, do not proceed with 2)
- **Sources**:
  - Newly produced armies (`:unit-needs-orders`)
  - Terminated mission armies (`:mission-ended`)
  - Armies whose missions ended (explorers that got stuck, etc.)

### Squad Assembly Logic
Lieutenant calculates:
1. Rally point selection (based on army positions and target city)
2. How many armies can reach rally point within 10 rounds:
   - Currently available (idle, in direct-reports)
   - In transit (can be redirected)
   - In production (estimate completion time)
3. If 3+ armies can assemble → form squad and transfer army ownership to squad

### Squad Creation
```clojure
(defn create-conquest-squad [lieutenant target-city armies]
  (let [squad (squad/create {:target-city target-city
                             :rally-point (select-rally-point target-city armies)
                             :armies armies
                             :assembly-deadline (+ current-round 10)})]
    (-> lieutenant
        (update :squads conj squad)
        (update :direct-reports #(remove-all % armies)))))  ; transfer ownership
```

### Parallelism
- One squad per discovered free city
- Multiple squads can operate simultaneously
- No need to attack sequentially

### Post-Conquest
- **Success**: Lieutenant receives surviving armies back, assigns to beach staging
- **Failure**: Lieutenant receives surviving armies, forms new squad, tries again
- Conquered city added to Lieutenant's cities, starts producing

---

## Beach Staging

### Staging Progression

**Phase 1: No transport-landings known yet**
- Armies waiting for transport → generic coastal staging area
- Use `get-waiting-area` to find empty coastal cell

**Phase 2: Transport-landings discovered**
- Armies → stationed at specific beach cells
- Prefer transport-landings with higher capacity

### Armies That Go to Beach Staging
1. Conquest survivors (squad dissolves successfully)
2. Finished explorers (continent fully mapped)
3. Armies from terminated missions

### Transport-Landing Selection Logic

Best transport-landing is a logistics optimization based on **convergence time**:

```
For each transport-landing candidate:
  army_time = max travel time from army-producing cities to beach cells
  transport_time = travel time from transport-producing city to transport-landing
  convergence_time = max(army_time, transport_time)

Best transport-landing = minimize convergence_time
```

**Factors considered:**
1. Which cities are producing armies
2. Which city is producing the transport
3. Land pathfinding time for armies to beach cells
4. Sea pathfinding time for transport to transport-landing

### Beach Assignment
- Lieutenant selects optimal transport-landing based on convergence calculation
- Tracks which beach cells are occupied
- Assigns incoming armies to selected beach's cells

**Concentration strategy:**
- Fill one beach completely before using another
- When beach is full AND transport loading → start filling next beach
- Supports pipeline: Beach A loading transport → Beach B staging next wave

**Queuing when beach is full:**
- Armies fill beach cells first (up to beach capacity: 3-7)
- Overflow armies queue one cell back from beach
- As armies board transport, queued armies move up to beach cells

**Loading is multi-round:**
- Transport sits at transport-landing (sea cell)
- Each round, adjacent armies can board (up to beach capacity per round)
- Queued armies advance to beach cells as space opens
- Full load of 6 armies may take 2+ rounds depending on beach capacity

---

## Transport Production and Invasion

### Transport Production

**Trigger:** 3+ armies waiting for transport (on beaches or queued)

**Location:** Nearest coastal city to the selected transport-landing

**Fleet Management:**
- Maximum 3 transports at any time
- Lieutenant retains control of transports (does not hand off to General)
- Pipeline model:
  - Transport 1: Loading at transport-landing
  - Transport 2: Sailing to target continent
  - Transport 3: Invading/unloading at destination
- This allows continuous invasion operations

**Production Decision:**
```
When waiting_army_count >= 3 AND transport_count < 3:
  Select best transport-landing (logistics optimization)
  Find nearest coastal city to that transport-landing
  Start transport production at that city
```

### Transport Loading

**Transports wait for full load (6 armies)** before departing.

If fewer than 6 armies available and no more expected:
- Transport continues waiting
- Lieutenant may redirect armies from other duties if strategically important

### Invasion Cycle

**Full transport cycle:**
```
1. Transport moves to transport-landing
2. Load 6 armies from beach (multi-round)
3. Sail to target continent
4. Find suitable transport-landing on new continent
5. Armies disembark onto beach
6. NEW Lieutenant created for beachhead continent
7. Disembarked armies report to NEW Lieutenant (disembark-and-rally mission)
8. Transport returns to original Lieutenant's transport-landing
9. Reload with next batch of armies
10. Repeat
```

**Transport mission modes:**
```
:moving-to-landing  - Sailing to home transport-landing
:loading            - At transport-landing, armies boarding from beach
:directed-invasion  - Sailing to known continent (General's orders)
:exploring          - Searching for new continent (default when no directive)
:scouting           - Found land, searching for suitable transport-landing
:unloading          - At foreign transport-landing, armies disembarking to beach
:returning          - Sailing back to home transport-landing
```

**Exploration mode (default):**
- No directive from General → transport explores
- Sail away from home base
- Scan for new landmass
- Upon finding land → scout coastline for suitable transport-landing
- Find transport-landing (sea cell with 3+ adjacent land cells) → unload armies to beach

**Directed invasion mode:**
- General commands: "Invade continent X at transport-landing Y"
- Lieutenant sends loaded transport to specified location
- Unload at directed transport-landing

**New Lieutenant Creation:**
- When first army disembarks, new Lieutenant is spawned for that continent
- Subsequent armies from same transport report to that Lieutenant
- New Lieutenant begins its own explore/conquer/prepare cycle

---

## Coast Defense

Defense has three layers providing early warning and interception:

### Layer 1: Sentry Armies on Coast

**Trigger:** Transport-landings have been discovered

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
- Beach staging armies take priority
- Fill sentry gaps with surplus armies after transport needs met

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

**Enemy encounter response:**
- Flee (disengage, move away)
- Report enemy position to Lieutenant
- Do not engage in combat

---

## General Interaction

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
           :target-transport-landing [r c]}}
   ```

---

## Events Handled

### From Units (Incoming)
- `:unit-needs-orders` - Assign mission to army
- `:mission-ended` - Update unit status, reassign if needed
- `:free-city-found` - Track discovered cities, trigger conquest
- `:city-conquered` - Add to cities list
- `:cells-discovered` - Update terrain knowledge
- `:transport-landing-found` - Track transport-landing candidates (replaces `:coastline-mapped`)
- `:enemy-spotted` - From fighters/patrol boats, track threats

### From Squads (Incoming)
- `:squad-mission-complete` - Squad reached terminal state, receive armies back

### From Transports (Incoming)
- `:transport-arrived` - Transport reached destination
- `:transport-loaded` - Transport finished loading (6 armies aboard)
- `:transport-unloaded` - All armies disembarked
- `:continent-found` - Transport discovered new landmass

### From General (Incoming)
- `:invade-continent` - Directive to invade specific continent

### To General (Outgoing)
- `:continent-discovered` - Report new continent found
- `:lieutenant-spawned` - Report new Lieutenant created
- `:continent-status` - Periodic status update

---

## Data Structure

```clojure
{;; Identity
 :lieutenant-id id
 :general-id id                     ; parent General for reporting

 ;; Territory
 :cities [...]                      ; controlled cities [coords ...]
 :known-coastal-cells #{}
 :known-landlocked-cells #{}
 :exploration-complete? false

 ;; Discovery tracking
 :free-cities-known [...]           ; discovered but not yet conquered
 :transport-landings []             ; discovered transport-landings with beaches
                                    ; each: {:transport-landing [r c]
                                    ;        :beach [[r c] ...]
                                    ;        :capacity n}

 ;; Owned entities (Lieutenant steps these)
 :squads []                         ; active conquest squads (own their armies)
 :transports []                     ; fleet of up to 3 transports
 :direct-reports []                 ; explorers and unassigned armies
                                    ; each: {:unit-id id :position [r c] :fsm-state ...}

 ;; Exploration management
 :explorer-requests []              ; pending requests: [{:type :coastline|:interior :count n}]
 :active-explorers #{}              ; unit-ids currently exploring

 ;; Staging management
 :beach-assignments {}              ; beach-cell [r c] -> army-id
 :queue-assignments []              ; [{:army-id id :queue-cell [r c]} ...]
 :waiting-armies []                 ; army-ids staged for transport

 ;; Production management
 :city-production {}                ; city-coords -> :army|:transport|:fighter|:patrol-boat
 :transport-production nil          ; {:city coords :target-landing landing} or nil
 :designated-fighter-city nil       ; coords of city producing fighters

 ;; Invasion tracking
 :spawned-lieutenants []            ; Lieutenants created from this one's invasions
 :pending-invasion-directive nil    ; directive from General, if any

 ;; Defense tracking
 :sentry-positions {}               ; coastal-cell [r c] -> army-id
 :fighters []                       ; fighter entities (stepped by Lieutenant)
 :patrol-boats []                   ; patrol boat entities (stepped by Lieutenant)

 ;; FSM state
 :fsm-state :operational
 :event-queue []}
```

---

---

## Sub-Handler Details

Each sub-handler is invoked every turn, regardless of events. Sub-handlers manage their concern proactively.

### handle-exploration

**Purpose**: Maintain explorer quota, track exploration progress, detect completion.

**Algorithm**:
```clojure
(defn handle-exploration [lieutenant ctx]
  (let [coastline-quota 2
        interior-quota 2
        current-coastline (count-explorers-by-type lieutenant :coastline)
        current-interior (count-explorers-by-type lieutenant :interior)]
    (cond-> lieutenant
      ;; Commission coastline explorers if under quota
      (< current-coastline coastline-quota)
      (request-explorer :coastline (- coastline-quota current-coastline))

      ;; Commission interior explorers if under quota
      (< current-interior interior-quota)
      (request-explorer :interior (- interior-quota current-interior))

      ;; Check if exploration is complete
      (exploration-complete? lieutenant ctx)
      (mark-exploration-complete))))
```

**Explorer Quota Policy**:
- **Fixed quotas**: 2 coastline, 2 interior explorers
- **Replacement**: If explorer terminates (stuck, destroyed), commission replacement
- **Completion**: When exploration complete, release explorers to beach staging
- **No adaptive scaling**: Quotas do not change based on continent size

**Requesting Explorers**:
```clojure
(defn request-explorer [lieutenant explorer-type count]
  ;; Mark that we need explorers; fulfilled when :unit-needs-orders arrives
  (update lieutenant :explorer-requests conj
          {:type explorer-type :count count}))
```

**Exploration Complete Detection**:
```clojure
(defn exploration-complete? [lieutenant ctx]
  (let [known-land (set/union (:known-coastal-cells lieutenant)
                              (:known-landlocked-cells lieutenant))
        game-map (:game-map ctx)]
    ;; Check if any unexplored cell is adjacent to known land
    (not-any? (fn [land-cell]
                (some #(unexplored? game-map %)
                      (land-neighbors land-cell game-map)))
              known-land)))
```

---

### handle-conquest

**Purpose**: Create squads for discovered free cities, manage squad lifecycle.

**Algorithm**:
```clojure
(defn handle-conquest [lieutenant ctx]
  (let [free-cities (:free-cities-known lieutenant)
        active-squads (:squads lieutenant)
        targeted-cities (set (map :target-city active-squads))
        untargeted-cities (remove targeted-cities free-cities)]
    (reduce (fn [lt city]
              (if (can-form-squad? lt city ctx)
                (create-squad-for-city lt city ctx)
                lt))
            lieutenant
            untargeted-cities)))
```

**Squad Formation Decision**:
```clojure
(defn can-form-squad? [lieutenant target-city ctx]
  (let [available-armies (available-for-squad lieutenant)
        rally-point (select-rally-point target-city available-armies ctx)
        reachable (armies-reachable-within lieutenant available-armies rally-point 10 ctx)]
    (>= (count reachable) 3)))  ; minimum 3 armies
```

**Available for Squad**:
Armies in `:direct-reports` that are:
- Not currently exploring (unless exploration complete)
- Not assigned to beach staging
- Not in another squad

```clojure
(defn available-for-squad [lieutenant]
  (let [exploring-ids (set (map :unit-id (active-explorers lieutenant)))
        staging-ids (set (:waiting-armies lieutenant))]
    (remove #(or (exploring-ids (:unit-id %))
                 (staging-ids (:unit-id %)))
            (:direct-reports lieutenant))))
```

**Rally Point Selection**:
```clojure
(defn select-rally-point [target-city armies ctx]
  ;; Find land cell 3-5 cells from target that minimizes total army travel
  (let [candidates (land-cells-within-range target-city 3 5 ctx)
        scored (map (fn [cell]
                      {:cell cell
                       :total-distance (reduce + (map #(distance (:position %) cell) armies))})
                    candidates)]
    (:cell (first (sort-by :total-distance scored)))))
```

**Squad Creation**:
```clojure
(defn create-squad-for-city [lieutenant target-city ctx]
  (let [available (available-for-squad lieutenant)
        rally-point (select-rally-point target-city available ctx)
        reachable (armies-reachable-within lieutenant available rally-point 10 ctx)
        selected (take 5 reachable)  ; max 5 armies per squad
        squad (squad/create {:squad-id (generate-id)
                             :target-city target-city
                             :rally-point rally-point
                             :armies selected
                             :assembly-deadline (+ (:round ctx) 10)
                             :lieutenant-id (:lieutenant-id lieutenant)})]
    (-> lieutenant
        (update :squads conj squad)
        (update :direct-reports #(remove-all % selected)))))
```

---

### handle-staging

**Purpose**: Assign armies to beaches, manage beach cell allocation, handle queuing.

**Algorithm**:
```clojure
(defn handle-staging [lieutenant ctx]
  (let [waiting (:waiting-armies lieutenant)
        unassigned (remove #(beach-assigned? lieutenant %) waiting)]
    (reduce assign-to-beach lieutenant unassigned)))
```

**Beach Assignment**:
```clojure
(defn assign-to-beach [lieutenant army-id]
  (let [landings (:transport-landings lieutenant)
        assignments (:beach-assignments lieutenant)]
    (if (empty? landings)
      ;; No landings known yet - use generic waiting area
      (assign-to-waiting-area lieutenant army-id)
      ;; Find best available beach cell
      (let [best-cell (find-best-beach-cell landings assignments)]
        (if best-cell
          (-> lieutenant
              (assoc-in [:beach-assignments best-cell] army-id)
              (issue-mission army-id :board-transport
                             {:assigned-beach-cell best-cell
                              :transport-landing (landing-for-cell best-cell landings)}))
          ;; All beach cells full - queue behind beach
          (assign-to-queue lieutenant army-id))))))
```

**Beach Cell Priority**:
```clojure
(defn find-best-beach-cell [landings assignments]
  ;; 1. Prefer landing with transport present
  ;; 2. Prefer landing with most armies already staged (concentration)
  ;; 3. Within landing, prefer unoccupied cell closest to center
  (let [occupied (set (keys assignments))
        by-landing (group-by :transport-landing landings)]
    (->> landings
         (sort-by #(landing-priority % assignments) >)
         (mapcat :beach)
         (remove occupied)
         first)))
```

**Queue Management**:
```clojure
(defn assign-to-queue [lieutenant army-id]
  ;; Find cell one step back from beach
  (let [primary-landing (primary-transport-landing lieutenant)
        beach (:beach primary-landing)
        queue-cells (cells-adjacent-to-but-not-in beach)]
    (-> lieutenant
        (update :queue-assignments conj {:army-id army-id
                                         :queue-cell (first-available queue-cells)})
        (issue-mission army-id :hurry-up-and-wait
                       {:destination (first-available queue-cells)}))))
```

**Advancing Queue**:
When an army boards transport, advance queued armies:
```clojure
(defn advance-queue [lieutenant vacated-beach-cell]
  (if-let [queued (first (:queue-assignments lieutenant))]
    (-> lieutenant
        (update :queue-assignments rest)
        (assoc-in [:beach-assignments vacated-beach-cell] (:army-id queued))
        (issue-mission (:army-id queued) :board-transport
                       {:assigned-beach-cell vacated-beach-cell}))
    lieutenant))
```

---

### handle-invasion

**Purpose**: Manage transport fleet, trigger transport production, assign transport targets.

**Algorithm**:
```clojure
(defn handle-invasion [lieutenant ctx]
  (-> lieutenant
      (maybe-produce-transport ctx)
      (assign-transport-targets ctx)))
```

**Transport Production Decision**:
```clojure
(defn maybe-produce-transport [lieutenant ctx]
  (let [waiting-count (count (:waiting-armies lieutenant))
        transport-count (count (:transports lieutenant))
        producing? (transport-in-production? lieutenant)]
    (if (and (>= waiting-count 3)
             (< transport-count 3)
             (not producing?))
      (start-transport-production lieutenant ctx)
      lieutenant)))

(defn start-transport-production [lieutenant ctx]
  (let [best-landing (select-best-transport-landing lieutenant ctx)
        production-city (nearest-coastal-city lieutenant best-landing ctx)]
    (-> lieutenant
        (assoc :transport-production {:city production-city
                                      :target-landing best-landing})
        (set-city-production production-city :transport))))
```

**Transport Target Assignment**:
```clojure
(defn assign-transport-targets [lieutenant ctx]
  (let [transports (:transports lieutenant)
        idle (filter #(= :loading (:fsm-state %)) transports)
        loaded (filter fully-loaded? idle)]
    (reduce (fn [lt transport]
              (let [target (get-invasion-target lt ctx)]
                (assign-target lt (:transport-id transport) target)))
            lieutenant
            loaded)))

(defn get-invasion-target [lieutenant ctx]
  ;; Check for General directive first
  (if-let [directive (pending-invasion-directive lieutenant)]
    {:type :directed
     :continent-id (:target-continent-id directive)
     :coords (:target-transport-landing directive)}
    ;; Otherwise, explore
    {:type :exploring
     :coords nil}))
```

---

### handle-defense

**Purpose**: Manage sentry placement, fighter patrols, patrol boats.

**Algorithm**:
```clojure
(defn handle-defense [lieutenant ctx]
  (-> lieutenant
      (manage-coastal-sentries ctx)
      (manage-fighter-patrol ctx)
      (manage-patrol-boats ctx)))
```

**Coastal Sentry Management**:
```clojure
(defn manage-coastal-sentries [lieutenant ctx]
  (if (empty? (:transport-landings lieutenant))
    lieutenant  ; no landings known yet
    (let [coastline (:known-coastal-cells lieutenant)
          beaches (set (mapcat :beach (:transport-landings lieutenant)))
          gaps (find-sentry-gaps coastline beaches 5)  ; max 5 cells between sentries
          current-sentries (count-sentries lieutenant)
          surplus-armies (surplus-for-defense lieutenant)]
      (if (and (seq gaps) (seq surplus-armies))
        (assign-sentry lieutenant (first surplus-armies) (first gaps))
        lieutenant))))

(defn surplus-for-defense [lieutenant]
  ;; Armies available only after transport staging needs are met
  (let [waiting (:waiting-armies lieutenant)
        needed-for-transport 6
        surplus (- (count waiting) needed-for-transport)]
    (when (pos? surplus)
      (take surplus (:direct-reports lieutenant)))))
```

**Fighter Patrol Management**:
```clojure
(defn manage-fighter-patrol [lieutenant ctx]
  (let [city-count (count (:cities lieutenant))
        fighter-count (count-fighters lieutenant)
        max-fighters 3]
    (if (and (>= city-count 3)
             (< fighter-count max-fighters)
             (not (fighter-in-production? lieutenant)))
      (start-fighter-production lieutenant ctx)
      lieutenant)))

(defn start-fighter-production [lieutenant ctx]
  (let [fighter-city (or (:designated-fighter-city lieutenant)
                         (designate-fighter-city lieutenant))]
    (-> lieutenant
        (assoc :designated-fighter-city fighter-city)
        (set-city-production fighter-city :fighter))))
```

**Patrol Boat Management**:
```clojure
(defn manage-patrol-boats [lieutenant ctx]
  (let [transport-count (count (:transports lieutenant))
        patrol-boat-count (count-patrol-boats lieutenant)
        max-patrol-boats 2]
    (if (and (>= transport-count 3)
             (< patrol-boat-count max-patrol-boats)
             (not (patrol-boat-in-production? lieutenant)))
      (start-patrol-boat-production lieutenant ctx)
      lieutenant)))
```

---

## Event Processing

### Processing Order

Events are processed **before** sub-handlers and entity stepping:

```clojure
(defn process-lieutenant-turn [lieutenant ctx]
  (-> lieutenant
      (process-all-events ctx)      ; 1. Drain event queue first
      (handle-exploration ctx)       ; 2. Then sub-handlers
      (handle-conquest ctx)
      (handle-staging ctx)
      (handle-invasion ctx)
      (handle-defense ctx)
      (step-squads ctx)              ; 3. Then step owned entities
      (step-transports ctx)
      (step-direct-reports ctx)))
```

### Event Queue Draining

Process all events in priority order before proceeding:

```clojure
(defn process-all-events [lieutenant ctx]
  (loop [lt lieutenant]
    (if-let [event (peek-event lt)]
      (recur (-> lt
                 pop-event
                 (handle-event event ctx)))
      lt)))
```

### Event Handlers

```clojure
(defmulti handle-event (fn [lt event ctx] (:type event)))

(defmethod handle-event :unit-needs-orders [lt event ctx]
  (let [unit-id (get-in event [:data :unit-id])
        coords (get-in event [:data :coords])]
    (assign-mission-to-new-unit lt unit-id coords ctx)))

(defmethod handle-event :mission-ended [lt event ctx]
  (let [unit-id (get-in event [:data :unit-id])
        reason (get-in event [:data :reason])]
    (handle-mission-ended lt unit-id reason ctx)))

(defmethod handle-event :free-city-found [lt event ctx]
  (let [coords (get-in event [:data :coords])]
    (update lt :free-cities-known conj coords)))

(defmethod handle-event :city-conquered [lt event ctx]
  (let [coords (get-in event [:data :coords])]
    (-> lt
        (update :cities conj coords)
        (update :free-cities-known #(remove #{coords} %)))))

(defmethod handle-event :transport-landing-found [lt event ctx]
  (let [landing-data (:data event)]
    (update lt :transport-landings conj landing-data)))

(defmethod handle-event :squad-mission-complete [lt event ctx]
  (let [squad-id (get-in event [:data :squad-id])
        result (get-in event [:data :result])
        survivors (get-in event [:data :surviving-armies])]
    (-> lt
        (remove-squad squad-id)
        (receive-armies survivors)
        (if (= result :success)
          identity
          #(mark-city-for-retry % (get-in event [:data :target-city]))))))

(defmethod handle-event :cells-discovered [lt event ctx]
  (let [{:keys [coastal-cells landlocked-cells]} (:data event)]
    (-> lt
        (update :known-coastal-cells into coastal-cells)
        (update :known-landlocked-cells into landlocked-cells))))
```

---

## Production Allocation

### City Production Priorities

Each city can produce one unit type at a time. Lieutenant allocates production:

**Priority Order** (highest to lowest):
1. **Transport** - If ≥3 armies waiting and <3 transports
2. **Army** - Default production for most cities
3. **Fighter** - If ≥3 cities controlled and <3 fighters
4. **Patrol Boat** - If 3 transports exist and <2 patrol boats

### Production Assignment Rules

```clojure
(defn allocate-production [lieutenant ctx]
  (let [cities (:cities lieutenant)]
    (reduce (fn [lt city]
              (let [current (get-city-production lt city)]
                (if (or (nil? current) (production-complete? lt city))
                  (assign-production lt city ctx)
                  lt)))
            lieutenant
            cities)))

(defn assign-production [lieutenant city ctx]
  (cond
    ;; Transport needed?
    (and (needs-transport? lieutenant)
         (= city (transport-production-city lieutenant)))
    (set-city-production lieutenant city :transport)

    ;; Fighter needed?
    (and (needs-fighter? lieutenant)
         (= city (:designated-fighter-city lieutenant)))
    (set-city-production lieutenant city :fighter)

    ;; Patrol boat needed?
    (and (needs-patrol-boat? lieutenant)
         (coastal-city? city ctx))
    (set-city-production lieutenant city :patrol-boat)

    ;; Default: produce armies
    :else
    (set-city-production lieutenant city :army)))
```

### Army Distribution

Armies are the default production. When a new army reports (`:unit-needs-orders`):

```clojure
(defn assign-mission-to-new-unit [lieutenant unit-id coords ctx]
  (cond
    ;; Exploration needs fill first
    (needs-explorers? lieutenant)
    (assign-explorer-mission lieutenant unit-id coords ctx)

    ;; Conquest squad needs army
    (squad-needs-army? lieutenant)
    (assign-to-squad lieutenant unit-id ctx)

    ;; Otherwise, go to beach staging
    :else
    (assign-to-staging lieutenant unit-id)))
```

---

## Implementation Notes

- Lieutenant scope is ONE landmass/continent (its "base")
- Lieutenant retains control of its transports throughout invasion cycle
- Lieutenant spawns new Lieutenants when armies land on new continents
- General coordinates between Lieutenants and may direct invasions to known continents
- Fleet of up to 3 transports allows continuous pipeline: loading → sailing → invading
- Sub-handlers are invoked every turn regardless of events (allows proactive behavior)
- Squads are first-class entities with their own FSMs, owned and stepped by Lieutenant
- Events are fully drained before sub-handlers run (no interleaving)
- Explorer quotas are fixed (2 coastline, 2 interior), not adaptive
