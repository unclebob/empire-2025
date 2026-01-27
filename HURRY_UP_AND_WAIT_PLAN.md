# Hurry-Up-And-Wait FSM Plan

## Overview

The hurry-up-and-wait FSM is a simple army mission that moves to a Lieutenant-specified position and then enters sentry mode. Used for positioning armies at defensive locations, staging areas, or rally points.

---

## Behavioral Requirements

1. **Move to Target**: Pathfind toward Lieutenant-specified destination
2. **Sidestepping**: Navigate around friendly armies and cities blocking the path
3. **Destination Blocked**: If destination itself is occupied, sidestep to adjacent empty land
4. **Sentry Mode**: Enter sentry (sleep) mode upon arrival

**Terminal Conditions:**
- Reach assigned destination, OR
- Reach any empty land cell after being forced to sidestep a blocked destination

---

## FSM States

```
:moving  →  Pathfinding toward destination, sidestepping obstacles
    ↓
    ├── [:terminal :arrived]  →  Reached destination, enter sentry mode
    │
    └── :sidestepping-destination  →  Destination blocked, finding nearby empty land
            ↓
        [:terminal :arrived]  →  Reached empty land near destination, enter sentry mode
```

### State Descriptions

| State | Description |
|-------|-------------|
| `:moving` | Pathfind toward destination, sidestep obstacles along the way |
| `:sidestepping-destination` | Adjacent to blocked destination, seeking nearby empty land |
| `[:terminal :arrived]` | Mission complete - at destination or nearby empty land, enter sentry mode |

---

## FSM Transitions

```clojure
(def hurry-up-and-wait-fsm
  [;; Moving toward destination
   [:moving  at-destination?              [:terminal :arrived]        arrive-action]
   [:moving  destination-blocked?         :sidestepping-destination   begin-sidestep-action]
   [:moving  can-move-toward?             :moving                     move-toward-action]
   [:moving  needs-sidestep?              :moving                     sidestep-action]

   ;; Sidestepping blocked destination - find nearby empty land
   [:sidestepping-destination  on-empty-land?  [:terminal :arrived]        arrive-action]
   [:sidestepping-destination  always          :sidestepping-destination   sidestep-to-land-action]])
```

**Transition Logic:**
1. If at destination → done
2. If adjacent to destination but destination is blocked → enter sidestepping state
3. If path clear → move toward destination
4. If path blocked but can sidestep → sidestep and continue
5. Once sidestepping destination, any empty land cell → done

---

## FSM Data Structure

```clojure
{:fsm hurry-up-and-wait-fsm
 :fsm-state :moving  ; or :sidestepping-destination, [:terminal :arrived]
 :fsm-data {:mission-type :hurry-up-and-wait
            :position [row col]           ; current position
            :destination [row col]        ; Lieutenant-specified target
            :recent-moves [[r c] ...]     ; backtrack prevention (last 10 moves)
            :lieutenant-id id}            ; for event reporting
 :event-queue []}
```

---

## Guards

### `at-destination?`
Returns true if `position` equals `destination`.

```clojure
(defn at-destination? [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        dest (get-in ctx [:entity :fsm-data :destination])]
    (= pos dest)))
```

### `destination-blocked?`
Returns true if adjacent to destination but destination is blocked by army or city.

```clojure
(defn destination-blocked? [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        dest (get-in ctx [:entity :fsm-data :destination])]
    (and (adjacent? pos dest)
         (blocked-by-army-or-city? ctx dest))))
```

### `can-move-toward?`
Returns true if pathfinding can take a direct step toward destination (no obstacle in the way).

```clojure
(defn can-move-toward? [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        dest (get-in ctx [:entity :fsm-data :destination])
        next-step (pathfinding/next-step-toward pos dest)]
    (and next-step
         (not (blocked-by-army-or-city? ctx next-step)))))
```

### `needs-sidestep?`
Returns true if direct path is blocked by army or city but alternate route exists.

```clojure
(defn needs-sidestep? [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        dest (get-in ctx [:entity :fsm-data :destination])
        next-step (pathfinding/next-step-toward pos dest)]
    (and next-step
         (blocked-by-army-or-city? ctx next-step)
         (some? (find-sidestep-move ctx pos dest)))))
```

### `on-empty-land?`
Returns true if current position is empty land (used in `:sidestepping-destination` state).

```clojure
(defn on-empty-land? [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        cell (get-in (:game-map ctx) pos)]
    (and (= :land (:type cell))
         (nil? (:contents cell)))))
```

### `always`
Always returns true (fallback guard).

---

## Actions

### `move-toward-action`
Take one step toward destination using pathfinding.

```clojure
(defn move-toward-action [ctx]
  (let [fsm-data (get-in ctx [:entity :fsm-data])
        pos (:position fsm-data)
        dest (:destination fsm-data)
        recent-moves (or (:recent-moves fsm-data) [])
        next-pos (pathfinding/next-step-toward pos dest)]
    {:move-to next-pos
     :recent-moves (update-recent-moves recent-moves next-pos)}))
```

### `sidestep-action`
Find alternate move that makes progress toward destination while avoiding obstacle.

```clojure
(defn sidestep-action [ctx]
  (let [fsm-data (get-in ctx [:entity :fsm-data])
        pos (:position fsm-data)
        dest (:destination fsm-data)
        recent-moves (or (:recent-moves fsm-data) [])
        next-pos (find-sidestep-move ctx pos dest)]
    {:move-to next-pos
     :recent-moves (update-recent-moves recent-moves next-pos)}))
```

### `begin-sidestep-action`
Triggered when adjacent to blocked destination. Finds nearby empty land cell.

```clojure
(defn begin-sidestep-action [ctx]
  (let [fsm-data (get-in ctx [:entity :fsm-data])
        pos (:position fsm-data)
        recent-moves (or (:recent-moves fsm-data) [])
        next-pos (find-empty-land-nearby ctx pos)]
    {:move-to next-pos
     :recent-moves (update-recent-moves recent-moves next-pos)}))
```

### `sidestep-to-land-action`
Continue moving to find empty land after destination was blocked.

```clojure
(defn sidestep-to-land-action [ctx]
  (let [fsm-data (get-in ctx [:entity :fsm-data])
        pos (:position fsm-data)
        recent-moves (or (:recent-moves fsm-data) [])
        next-pos (find-empty-land-nearby ctx pos)]
    {:move-to next-pos
     :recent-moves (update-recent-moves recent-moves next-pos)}))
```

### `arrive-action`
Signal arrival and transition unit to sentry mode.

```clojure
(defn arrive-action [ctx]
  ;; Returns data that orchestration layer uses to set unit to sentry mode
  {:enter-sentry-mode true})
```

---

## Helper Functions

### `blocked-by-army-or-city?`
Check if position contains a friendly army or any city.

```clojure
(defn blocked-by-army-or-city? [ctx pos]
  (let [cell (get-in (:game-map ctx) pos)
        contents (:contents cell)]
    (or (= :city (:type cell))
        (and contents
             (= :army (:type contents))
             (= :computer (:owner contents))))))
```

### `adjacent?`
Check if two positions are adjacent (including diagonals).

```clojure
(defn adjacent? [[r1 c1] [r2 c2]]
  (and (<= (Math/abs (- r1 r2)) 1)
       (<= (Math/abs (- c1 c2)) 1)
       (not (and (= r1 r2) (= c1 c2)))))
```

### `find-sidestep-move`
Find a valid move that:
1. Avoids the blocked cell
2. Makes progress toward destination (reduces distance)
3. Avoids backtracking (not in recent-moves)

```clojure
(defn find-sidestep-move [ctx pos dest]
  (let [recent-moves (get-in ctx [:entity :fsm-data :recent-moves] [])
        all-moves (get-valid-army-moves ctx pos)
        non-backtrack (remove (set recent-moves) all-moves)
        current-dist (manhattan-distance pos dest)
        ;; Filter moves that make progress
        progress-moves (filter #(< (manhattan-distance % dest) current-dist)
                               non-backtrack)
        ;; Also consider lateral moves if no progress moves
        lateral-moves (filter #(= (manhattan-distance % dest) current-dist)
                              non-backtrack)]
    (or (first progress-moves)
        (first lateral-moves)
        (first non-backtrack))))

(defn manhattan-distance [[r1 c1] [r2 c2]]
  (+ (Math/abs (- r1 r2))
     (Math/abs (- c1 c2))))
```

### `find-empty-land-nearby`
Find an empty land cell adjacent to current position. Used when destination is blocked.

```clojure
(defn find-empty-land-nearby [ctx pos]
  (let [recent-moves (get-in ctx [:entity :fsm-data :recent-moves] [])
        game-map (:game-map ctx)
        neighbors (map-utils/get-matching-neighbors
                    pos game-map map-utils/neighbor-offsets
                    (fn [cell]
                      (and (= :land (:type cell))
                           (nil? (:contents cell)))))
        non-backtrack (remove (set recent-moves) neighbors)]
    (or (first non-backtrack)
        (first neighbors))))
```

### `update-recent-moves`
Reuse from coastline/interior explorer - maintains last 10 moves.

---

## Creation Function

```clojure
(defn create-hurry-up-and-wait
  "Create an army unit with hurry-up-and-wait mission.

   Parameters:
   - lieutenant-id: ID of commanding Lieutenant
   - start-pos: Current position [row col]
   - destination: Target position to move to"
  [lieutenant-id start-pos destination]
  {:fsm hurry-up-and-wait-fsm
   :fsm-state :moving
   :fsm-data {:mission-type :hurry-up-and-wait
              :position start-pos
              :destination destination
              :recent-moves [start-pos]
              :lieutenant-id lieutenant-id}
   :event-queue []})
```

---

## Orchestration Layer Integration

When the FSM returns `{:enter-sentry-mode true}`, the orchestration layer should:

1. Set unit's `:mode` to `:sentry`
2. Remove unit from active processing queue
3. Unit remains at position until woken by external event

---

## Test Cases

### Unit Tests (TDD)

1. **Creation**
   - `create-hurry-up-and-wait` produces correct initial state
   - Starts in `:moving` state
   - Destination stored in fsm-data

2. **Guards**
   - `at-destination?` true when position = destination
   - `destination-blocked?` true when adjacent to blocked destination
   - `destination-blocked?` false when not adjacent
   - `destination-blocked?` false when destination is clear
   - `can-move-toward?` true when path clear
   - `can-move-toward?` false when blocked by army
   - `can-move-toward?` false when blocked by city
   - `needs-sidestep?` true when blocked but sidestep available
   - `on-empty-land?` true on empty land cell
   - `on-empty-land?` false on city or occupied cell

3. **Movement**
   - `move-toward-action` returns next step on path
   - `sidestep-action` finds alternate route around obstacle
   - `begin-sidestep-action` finds empty land near blocked destination
   - `sidestep-to-land-action` continues to empty land
   - Recent moves updated correctly

4. **Arrival**
   - `arrive-action` returns enter-sentry-mode flag
   - FSM reaches `[:terminal :arrived]` at destination
   - FSM reaches `[:terminal :arrived]` on empty land after sidestepping destination

5. **Backtrack Avoidance**
   - Recent moves (last 10) avoided in sidestep selection

### Integration Tests

1. **Direct Path**
   - Army moves straight to destination when unobstructed

2. **Sidestep En Route**
   - Army navigates around friendly army blocking path
   - Army navigates around city blocking path
   - Continues to destination after sidestepping

3. **Blocked Destination**
   - Army stops on empty land when destination has friendly army
   - Army stops on empty land when destination is a city
   - Transitions to `:sidestepping-destination` then terminal

4. **Sentry Mode**
   - Unit enters sentry mode upon arrival at destination
   - Unit enters sentry mode upon arrival at alternate location

---

## Implementation Phases

### Phase 1: Core Infrastructure
1. Write tests for `blocked-by-army-or-city?`
2. Implement blocking detection
3. Write tests for `find-sidestep-move`
4. Implement sidestep logic

### Phase 2: FSM Implementation
1. Write tests for guards
2. Implement guards: `at-destination?`, `destination-blocked?`, `can-move-toward?`, `needs-sidestep?`, `on-empty-land?`
3. Write tests for actions
4. Implement actions: `move-toward-action`, `sidestep-action`, `begin-sidestep-action`, `sidestep-to-land-action`, `arrive-action`

### Phase 3: Integration
1. Write tests for `create-hurry-up-and-wait`
2. Implement creation function
3. Integrate with orchestration layer for sentry mode transition
4. Full integration test

---

## File Changes

| File | Changes |
|------|---------|
| `src/empire/fsm/hurry_up_and_wait.cljc` | **NEW** - FSM implementation |
| `src/empire/fsm/missions/army.cljc` | Add `create-hurry-up-and-wait` |
| `spec/empire/fsm/hurry_up_and_wait_spec.clj` | **NEW** - Unit tests |

---

## Design Decisions

1. **Single terminal state**: Only `[:terminal :arrived]` - mission always succeeds
   - Reached assigned destination, OR
   - Reached empty land after destination was blocked

2. **Destination blocked handling**: When adjacent to blocked destination, transition to `:sidestepping-destination` state and find nearby empty land

3. **Sentry mode**: Signaled via return value, orchestration layer handles mode change

4. **No event reporting**: Unlike explorers, this mission doesn't report cells or cities (it's just moving to a known location)

5. **Backtrack avoidance**: Same 10-move limit as other explorers to prevent oscillation
