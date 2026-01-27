# Interior Explorer FSM Plan

## Overview

The interior explorer is an army mission FSM that systematically explores landlocked territory. Unlike the coastline explorer which follows shorelines, the interior explorer sweeps across landmasses in a raster pattern, maximizing coverage of unexplored cells.

---

## Behavioral Requirements

1. **Initial Movement**: Move to Lieutenant-specified target position
2. **Diagonal Preference**: Prefer diagonal moves that expose the most unexplored territory
3. **Direction Persistence**: Continue in same/similar direction until blocked
4. **Raster Pattern**: When coast is reached, reverse perpendicular direction and continue
5. **Free City Reporting**: Report discovered free cities via events
6. **City Sidestepping**: Avoid entering free cities, navigate around them
7. **Army Sidestepping**: Avoid cells containing friendly (computer) armies, route around
8. **Backtrack Avoidance**: Remember last 10 moves, avoid revisiting
9. **Cell Reporting**: Report discovered cells to Lieutenant (as in coastline explorer)
10. **Away from Home**: Prefer directions away from initial city when choosing raster axis

---

## FSM States

```
:moving-to-target  →  Initial state when Lieutenant specifies a destination
        ↓
   :exploring      →  Diagonal sweep away from initial city
        ↓
    :rastering     →  Back-and-forth between coasts (2-step advancement)
        ↓
[:terminal :no-unexplored]  →  Mission complete
```

Both `:exploring` and `:rastering` can use `route-around-action` when blocked.

### State Descriptions

| State | Description |
|-------|-------------|
| `:moving-to-target` | Pathfind toward Lieutenant-directed starting position |
| `:exploring` | Move diagonally away from initial city, toward unexplored territory |
| `:rastering` | Systematic back-and-forth sweep between coasts with two-step advancement |
| `[:terminal :no-unexplored]` | Mission complete - no reachable unexplored cells |

Note: There is no "stuck" terminal state. When blocked, the FSM routes around obstacles using BFS.

---

## FSM Transitions

```clojure
(def interior-explorer-fsm
  [;; Moving to target
   [:moving-to-target  stuck?               [:terminal :stuck]         terminal-action]
   [:moving-to-target  at-target?           :exploring                 arrive-at-target-action]
   [:moving-to-target  not-at-target?       :moving-to-target          move-toward-target-action]

   ;; Exploring (initial diagonal sweep)
   [:exploring         stuck?                     [:terminal :stuck]         terminal-action]
   [:exploring         no-reachable-unexplored?  [:terminal :no-unexplored]  terminal-complete-action]
   [:exploring         reached-coast?            :rastering                  start-raster-action]
   [:exploring         can-continue?             :exploring                  explore-diagonal-action]
   [:exploring         needs-routing?            :exploring                  route-around-action]

   ;; Rastering (back-and-forth between coasts)
   [:rastering         stuck?                     [:terminal :stuck]         terminal-action]
   [:rastering         no-reachable-unexplored?  [:terminal :no-unexplored]  terminal-complete-action]
   [:rastering         can-continue?             :rastering                  raster-action]
   [:rastering         needs-routing?            :rastering                  route-around-action]])
```

Note: `needs-routing?` triggers when normal movement is blocked but BFS can find a path. The `route-around-action` uses BFS to navigate toward unexplored territory. Terminal state `[:terminal :no-unexplored]` occurs when BFS confirms no unexplored cells are reachable. Terminal state `[:terminal :stuck]` occurs when no valid moves exist at all. Both terminal states emit `:mission-ended` events for Lieutenant tracking.

---

## FSM Data Structure

```clojure
{:fsm interior-explorer-fsm
 :fsm-state :moving-to-target  ; or :exploring, :rastering, [:terminal reason]
 :fsm-data {:mission-type :explore-interior
            :position [row col]           ; current position
            :destination [row col]        ; Lieutenant-specified target (or nil)
            :initial-city [row col]       ; home city position (for "away from" preference)
            :explore-direction [dr dc]    ; primary diagonal direction e.g. [1 1]
            :raster-axis :vertical        ; :vertical or :horizontal - which axis to sweep
            :raster-direction 1           ; +1 or -1 for current sweep direction
            :recent-moves [[r c] ...]     ; backtrack prevention (last 10 moves)
            :lieutenant-id id             ; for event reporting
            :unit-id id}                  ; for Lieutenant tracking
 :event-queue []}
```

### Backtrack Prevention

Maintain a list of the last 10 positions visited. When selecting moves, penalize or exclude positions in `recent-moves` to avoid oscillation and wasted movement.

```clojure
(def backtrack-limit 10)

(defn update-recent-moves [recent-moves new-pos]
  (let [updated (conj (vec recent-moves) new-pos)]
    (if (> (count updated) backtrack-limit)
      (vec (drop (- (count updated) backtrack-limit) updated))
      updated)))
```

### Direction Representation

The `explore-direction` is a diagonal: `[1 1]`, `[1 -1]`, `[-1 1]`, or `[-1 -1]`.

When rastering:
- `raster-axis` indicates whether we sweep north-south (`:vertical`) or east-west (`:horizontal`)
- `raster-direction` flips between `+1` and `-1` when hitting a coast

---

## Guards

### `stuck?`
Returns true if no valid moves exist at all. This is checked first in every state.

```clojure
(defn stuck? [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        valid-moves (get-valid-interior-moves ctx pos)]
    (empty? valid-moves)))
```

### `at-target?`
Returns true if `position` equals `destination`, or if no destination was set.

### `not-at-target?`
Inverse of `at-target?`.

### `reached-coast?`
Returns true if current position is adjacent to sea (`map-utils/adjacent-to-sea?`).

### `can-continue?`
Returns true if there's a valid diagonal move toward unexplored territory.

### `no-reachable-unexplored?`
Returns true if no unexplored cells are reachable from current position.
Uses BFS/flood-fill on `computer-map` to check reachability. This is the **only terminal condition**.

```clojure
(defn no-reachable-unexplored? [ctx]
  (nil? (find-nearest-unexplored ctx)))
```

### `needs-routing?`
Returns true when:
- Normal diagonal/raster move is blocked (by coast, city, or army)
- But BFS can find a path to unexplored territory

This triggers `route-around-action` instead of going terminal.

```clojure
(defn needs-routing? [ctx]
  (and (not (can-continue? ctx))
       (some? (find-nearest-unexplored ctx))))
```

### `always`
Always returns true (fallback guard - should not be reached if guards are complete).

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

### `terminal-complete-action`
Called when exploration is complete (no unexplored cells reachable). Notifies Lieutenant.

```clojure
(defn- terminal-complete-action [ctx]
  (let [unit-id (get-in ctx [:entity :fsm-data :unit-id])]
    {:events [{:type :mission-ended
               :priority :high
               :data {:unit-id unit-id :reason :no-unexplored}}]}))
```

### `move-toward-target-action`
Pathfind one step toward destination.

```clojure
;; Returns:
{:move-to [row col]
 :recent-moves updated-list}
```

### `arrive-at-target-action`
Initialize exploration from target position. Pick initial diagonal direction **away from initial city** and toward unexplored territory.

```clojure
;; Returns:
{:destination nil
 :explore-direction [dr dc]  ; diagonal away from initial-city
 :recent-moves [position]
 :events [...]}              ; cells-discovered event

(defn pick-initial-direction [pos initial-city]
  "Choose diagonal direction that moves away from initial city."
  (let [[pr pc] pos
        [ir ic] initial-city
        ;; Direction away from city
        dr (if (>= pr ir) 1 -1)
        dc (if (>= pc ic) 1 -1)]
    [dr dc]))
```

### `explore-diagonal-action`
Move diagonally, preferring direction that exposes most unexplored cells. Report any adjacent free cities. Sidestep cities and friendly armies.

```clojure
;; Returns:
{:move-to [row col]
 :recent-moves updated-list
 :events [...]}  ; free-city-found, cells-discovered
```

### `start-raster-action`
Transition from exploring to rastering. Determine raster axis based on coastline orientation. Flip perpendicular direction.

```clojure
;; Returns:
{:move-to [row col]
 :raster-axis :vertical|:horizontal
 :raster-direction +1|-1
 :recent-moves updated-list
 :events [...]}
```

### `raster-action`
Continue raster sweep. When hitting coast, advance two diagonal steps and flip `raster-direction`. Move in current sweep direction, sidestepping obstacles.

```clojure
;; Returns:
{:move-to [row col]
 :raster-direction new-direction  ; may flip
 :recent-moves updated-list
 :events [...]}
```

### `route-around-action`
Used when normal movement is blocked but unexplored territory is reachable. Uses BFS to find path to nearest unexplored cell, takes first step on that path.

```clojure
;; Returns:
{:move-to [row col]           ; first step on BFS path
 :recent-moves updated-list
 :events [...]}               ; cells-discovered

(defn route-around-action [ctx]
  (let [fsm-data (get-in ctx [:entity :fsm-data])
        pos (:position fsm-data)
        recent-moves (or (:recent-moves fsm-data) [])
        ;; BFS to find nearest unexplored
        path (find-path-to-unexplored ctx pos)
        next-pos (second path)  ; first element is current pos
        ;; Events
        free-city (find-adjacent-free-city pos)
        events (cond-> [(make-cells-discovered-event pos)]
                 free-city (conj (make-free-city-event free-city)))]
    (when next-pos
      {:move-to next-pos
       :recent-moves (update-recent-moves recent-moves next-pos)
       :events events})))
```

---

## Movement Logic

### Diagonal Scoring

Score each potential move by:
1. **Unexplored exposure**: Count unexplored neighbors of destination cell
2. **Direction alignment**: How well move aligns with `explore-direction`
3. **Diagonal bonus**: Prefer diagonal moves (expose 5 new cells vs 3 for cardinal)

```clojure
(defn score-move [pos target-pos explore-direction computer-map]
  (let [unexplored-count (count-unexplored-neighbors target-pos computer-map)
        direction-score (direction-alignment pos target-pos explore-direction)
        diagonal? (diagonal-move? pos target-pos)]
    (+ (* 3 unexplored-count)
       (* 2 direction-score)
       (if diagonal? 1 0))))
```

### Obstacle Sidestepping

When preferred move is blocked (city or friendly army), **route around** the obstacle:

1. Try perpendicular moves that maintain progress in the primary direction
2. If both perpendicular blocked, try opposite diagonal
3. If still blocked, use BFS to find path around obstacle toward unexplored territory
4. As last resort, any valid move toward unexplored

```clojure
(defn sidestep-obstacle [pos blocked-pos explore-direction valid-moves recent-moves]
  ;; blocked-pos is where we wanted to go
  ;; Find best alternative that keeps us moving, avoiding recent moves
  (let [non-backtrack (remove (set recent-moves) valid-moves)
        moves-to-consider (if (seq non-backtrack) non-backtrack valid-moves)
        [dr dc] explore-direction
        ;; Perpendicular options maintain one component of direction
        perpendicular-1 [dr 0]
        perpendicular-2 [0 dc]
        ;; Score moves by alignment with perpendicular directions
        scored (map (fn [m]
                      [m (+ (direction-alignment pos m perpendicular-1)
                            (direction-alignment pos m perpendicular-2))])
                    moves-to-consider)
        best (first (sort-by second > scored))]
    (first best)))

(defn route-around-obstacle [pos target-direction valid-moves game-map computer-map]
  "Use BFS to find a path around obstacles toward unexplored territory."
  ;; Returns next step on path, or nil if completely stuck
  ...)
```

### Stuck Recovery

When immediately stuck (no valid moves in preferred direction), attempt to route around:

1. Use BFS to find nearest unexplored cell reachable via valid moves
2. Take first step on that path
3. Only transition to `[:terminal :no-unexplored]` if BFS finds no path to any unexplored cell

### Raster Pattern

```
Coast A                    Coast B
  |  ↘                        |
  |    ↘                      |
  |      ↘                    |
  |        ↘ (hit coast)      |
  |          ↓ (2 diagonal    |
  |          ↓  steps down)   |
  |        ↙                  |
  |      ↙                    |
  |    ↙                      |
  |  ↙ (hit coast)            |
  |  ↓ (2 diagonal            |
  |  ↓  steps down)           |
  |  ↘                        |
  |    ↘                      |
  ...
```

When hitting a coast:
1. Determine which coast (left/right or top/bottom based on `raster-axis`)
2. **Advance two diagonal steps** along the raster axis (perpendicular to flip direction)
3. Flip `raster-direction` (e.g., `[1 1]` becomes `[1 -1]`)
4. Continue diagonal sweep in new direction

The two-step advancement ensures adequate coverage overlap between raster passes while avoiding excessive redundancy.

### Raster Axis Selection

When transitioning from `:exploring` to `:rastering` (first coast hit), determine axis by preferring the direction **away from the initial city**:

```clojure
(defn determine-raster-axis [pos initial-city]
  (let [[pr pc] pos
        [ir ic] initial-city
        dr (- pr ir)  ; positive = south of city
        dc (- pc ic)] ; positive = east of city
    ;; Choose axis that moves away from city
    (if (> (Math/abs dr) (Math/abs dc))
      :vertical    ; continue north-south movement (away from city vertically)
      :horizontal))) ; continue east-west movement (away from city horizontally)
```

---

## Event Generation

### `:cells-discovered`
Generated every move with terrain type (`:coastal` or `:landlocked`).

```clojure
{:type :cells-discovered
 :priority :low
 :data {:cells [{:pos [r c] :terrain :landlocked}]}}
```

### `:free-city-found`
Generated when adjacent to a free city.

```clojure
{:type :free-city-found
 :priority :high
 :data {:coords [r c]}}
```

---

## Helper Functions

### New Functions Needed

| Function | Purpose |
|----------|---------|
| `pick-initial-direction` | Choose initial diagonal away from initial city |
| `score-interior-move` | Score move by unexplored exposure + direction alignment |
| `pick-interior-move` | Select best move considering obstacles |
| `determine-raster-axis` | Detect coastline orientation based on distance from initial city |
| `flip-raster-direction` | Reverse perpendicular component |
| `advance-raster` | Take two diagonal steps when hitting coast |
| `has-friendly-army?` | Check if cell contains computer army |
| `get-valid-interior-moves` | Get valid moves excluding friendly armies |
| `find-nearest-unexplored` | BFS to find nearest unexplored cell position |
| `find-path-to-unexplored` | BFS to find path to nearest unexplored cell |
| `direction-alignment` | Score how well a move aligns with a direction |
| `diagonal-move?` | Returns true if move is diagonal |

### Reusable from Coastline Explorer

| Function | Source |
|----------|--------|
| `update-recent-moves` | `coastline_explorer.cljc:182` |
| `count-unexplored-neighbors` | `coastline_explorer.cljc:82` |
| `pick-best-by-unexplored` | `coastline_explorer.cljc:92` |
| `find-adjacent-free-city` | `coastline_explorer.cljc:17` |
| `adjacent-to?` | `coastline_explorer.cljc:144` |

Consider extracting shared functions to a common module (e.g., `explorer_utils.cljc`).

---

## Creation Function

```clojure
(defn create-interior-explorer
  "Create an army unit with interior exploration mission.

   Parameters:
   - lieutenant-id: ID of commanding Lieutenant (for event reporting)
   - start-pos: Current position [row col]
   - initial-city: Position of home city (for 'away from' direction preference)
   - target: Optional destination to move to before exploring
   - unit-id: Optional unit ID for Lieutenant tracking"
  ([lieutenant-id start-pos initial-city]
   (create-interior-explorer lieutenant-id start-pos initial-city nil nil))

  ([lieutenant-id start-pos initial-city target]
   (create-interior-explorer lieutenant-id start-pos initial-city target nil))

  ([lieutenant-id start-pos initial-city target unit-id]
   (let [has-target? (and target (not= start-pos target))
         explore-dir (pick-initial-direction start-pos initial-city)]
     {:fsm interior-explorer-fsm
      :fsm-state (if has-target? :moving-to-target :exploring)
      :fsm-data {:mission-type :explore-interior
                 :position start-pos
                 :destination (when has-target? target)
                 :initial-city initial-city
                 :explore-direction explore-dir
                 :raster-axis nil           ; set when entering :rastering
                 :raster-direction nil      ; set when entering :rastering
                 :recent-moves [start-pos]
                 :lieutenant-id lieutenant-id
                 :unit-id unit-id}
      :event-queue []})))
```

---

## Valid Move Filtering

Modify or wrap `context/get-valid-army-moves` to also exclude:

1. **Cells with friendly armies**: `(:contents cell)` with `:owner :computer`
2. **Free cities**: Already handled by existing logic (cities aren't `:land`)

```clojure
(defn get-valid-interior-moves
  "Returns valid moves for interior exploration, excluding friendly armies."
  [ctx pos]
  (let [all-moves (context/get-valid-army-moves ctx pos)
        game-map (:game-map ctx)]
    (remove #(friendly-army-at? game-map %) all-moves)))

(defn- friendly-army-at?
  "Returns true if there's a computer-owned army at pos."
  [game-map pos]
  (let [cell (get-in game-map pos)
        contents (:contents cell)]
    (and contents
         (= :army (:type contents))
         (= :computer (:owner contents)))))
```

---

## Terminal Conditions

### `:stuck`
Triggered when `stuck?` guard returns true (no valid moves exist). Emits `:mission-ended` event with reason `:stuck`.

### `:no-unexplored`
Triggered when `find-nearest-unexplored` returns nil. Emits `:mission-ended` event with reason `:no-unexplored`.

Implementation: BFS from current position over valid army moves (excluding friendly armies). If no cell in `computer-map` with `:type :unexplored` is reachable, mission is complete.

```clojure
(defn find-nearest-unexplored [ctx]
  "BFS to find nearest unexplored cell reachable from current position.
   Returns [row col] of nearest unexplored, or nil if none reachable."
  (let [pos (get-in ctx [:entity :fsm-data :position])
        computer-map (:computer-map ctx)
        game-map (:game-map ctx)]
    (loop [queue (conj clojure.lang.PersistentQueue/EMPTY pos)
           visited #{pos}]
      (when-let [current (peek queue)]
        (let [cell (get-in computer-map current)]
          (if (= :unexplored (:type cell))
            current
            (let [neighbors (get-valid-interior-moves-from game-map current)
                  unvisited (remove visited neighbors)
                  new-visited (into visited unvisited)
                  new-queue (into (pop queue) unvisited)]
              (recur new-queue new-visited))))))))
```

Both terminal states emit `:mission-ended` events for Lieutenant tracking. If normal movement is blocked but unexplored cells exist, the `route-around-action` uses BFS to navigate around obstacles.

---

## Test Cases

### Unit Tests (TDD)

1. **Creation**
   - `create-interior-explorer` produces correct initial state
   - With destination → `:moving-to-target`
   - Without destination → `:exploring`
   - Stores `initial-city` in fsm-data

2. **Guards**
   - `at-target?` true when position = destination
   - `at-target?` true when no destination set
   - `reached-coast?` true when adjacent to sea
   - `no-reachable-unexplored?` true when BFS finds no unexplored
   - `needs-routing?` true when blocked but unexplored exists

3. **Direction Selection**
   - `pick-initial-direction` chooses diagonal away from initial city
   - Diagonal moves scored higher than cardinal
   - Moves toward unexplored preferred
   - Recent moves (last 10) penalized to avoid backtracking

4. **Move Filtering**
   - Friendly armies filtered from valid moves
   - `get-valid-interior-moves` excludes cells with computer armies

5. **Raster Behavior**
   - `determine-raster-axis` based on distance from initial city
   - Correctly detects coast arrival
   - Flips direction at coast
   - Advances two diagonal steps when flipping

6. **Routing**
   - `find-nearest-unexplored` returns nearest via BFS
   - `find-path-to-unexplored` returns full path
   - `route-around-action` navigates around obstacles

7. **Event Generation**
   - Free city found → `:free-city-found` event queued
   - Cells discovered each move → `:cells-discovered` event queued

8. **Terminal State**
   - Reaches `[:terminal :no-unexplored]` when BFS finds nothing
   - Does NOT go terminal when blocked but unexplored exists

### Integration Tests

1. **Full Exploration**
   - Explorer covers entire small landmass
   - Raster pattern visibly correct
   - Two-step advancement at coasts

2. **Obstacle Handling**
   - Navigates around free city (sidesteps)
   - Navigates around friendly army cluster (routes around)
   - Reports free cities while sidestepping

3. **Direction Preference**
   - Initial direction moves away from initial city
   - Raster axis oriented away from initial city

4. **Backtrack Avoidance**
   - Does not revisit cells in last 10 moves
   - Still makes progress when constrained

5. **Reporting**
   - All free cities reported to Lieutenant
   - All cells reported via `:cells-discovered`

---

## Implementation Phases

### Phase 1: Core Infrastructure
1. Write tests for `friendly-army-at?` and `get-valid-interior-moves`
2. Implement move filtering
3. Write tests for diagonal scoring
4. Implement `score-interior-move` and `pick-interior-move`

### Phase 2: Basic FSM
1. Write tests for guards
2. Implement guards: `at-target?`, `reached-coast?`, `can-continue?`
3. Write tests for `move-toward-target-action`
4. Implement pathfinding action

### Phase 3: Exploration State
1. Write tests for `explore-diagonal-action`
2. Implement diagonal exploration with sidestepping
3. Write tests for event generation
4. Verify free city reporting

### Phase 4: Raster State
1. Write tests for `determine-raster-axis`
2. Implement raster axis detection
3. Write tests for `raster-action` including direction flip
4. Implement full raster behavior

### Phase 5: Routing & Terminal Conditions
1. Write tests for `find-nearest-unexplored` and `find-path-to-unexplored`
2. Implement BFS reachability and pathfinding
3. Write tests for `no-reachable-unexplored?` and `needs-routing?` guards
4. Implement `route-around-action`
5. Verify terminal state transitions

### Phase 6: Integration
1. Update `create-interior-explorer` in `missions/army.cljc`
2. Add interior explorer to Lieutenant's mission assignment logic
3. Full integration test with real game loop

---

## File Changes

| File | Changes |
|------|---------|
| `src/empire/fsm/interior_explorer.cljc` | **NEW** - Main FSM implementation |
| `src/empire/fsm/explorer_utils.cljc` | **NEW** - Shared explorer utilities |
| `src/empire/fsm/missions/army.cljc` | Update `create-interior-explorer` |
| `src/empire/fsm/context.cljc` | Add `get-valid-interior-moves` |
| `spec/empire/fsm/interior_explorer_spec.clj` | **NEW** - Unit tests |
| `spec/empire/fsm/explorer_utils_spec.clj` | **NEW** - Utility tests |

---

## Design Decisions (Resolved)

1. **Raster axis selection**: Prefer directions **away from initial city**. The axis is chosen based on which direction (vertical or horizontal) moves the explorer further from where it started.

2. **Raster advancement**: **Two diagonal steps** when flipping at coast. This provides adequate coverage overlap while avoiding excessive redundancy.

3. **Stuck recovery**: **Route around** stuck positions using BFS to find path to nearest unexplored cell. Only terminate if no path exists.

4. **Friendly army collision**: **Route around**. Treat friendly armies as temporary obstacles and find alternate path.

5. **Cell reporting**: Report neighbor cells to Lieutenant using `:cells-discovered` events, same pattern as coastline explorer.

6. **Backtrack avoidance**: Remember **last 10 moves** and penalize/exclude them from move selection.
