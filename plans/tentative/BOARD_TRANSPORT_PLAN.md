# Board-Transport FSM Plan

**STATUS: TENTATIVE**

## Overview

Army mission to move to a transport ship and board it. Used for base establishment when Lieutenant loads 6 armies onto a transport for overseas deployment.

---

## Behavioral Requirements

1. **Move to Coast**: Navigate to a coastal cell adjacent to the transport
2. **Board Transport**: When adjacent to transport, board it
3. **Sidestepping**: Navigate around friendly armies and cities
4. **Handle Moving Transport**: If transport moves, update target location
5. **Report Boarded**: Notify Lieutenant when successfully boarded

**Terminal Conditions:**
- Successfully boarded transport
- Transport destroyed or no longer available
- No path to transport (landlocked)

---

## FSM States

```
:moving-to-coast  →  Pathfinding toward coastal boarding point
       ↓
:boarding         →  Adjacent to transport, boarding
       ↓
[:terminal :boarded]  →  Successfully on transport
```

---

## FSM Transitions

```clojure
(def board-transport-fsm
  [;; Moving to coastal boarding point
   [:moving-to-coast  stuck?                  [:terminal :stuck]    terminal-action]
   [:moving-to-coast  transport-gone?         [:terminal :aborted]  terminal-abort-action]
   [:moving-to-coast  adjacent-to-transport?  :boarding             prepare-board-action]
   [:moving-to-coast  can-move-toward?        :moving-to-coast      move-toward-action]
   [:moving-to-coast  needs-sidestep?         :moving-to-coast      sidestep-action]

   ;; Boarding
   [:boarding  transport-gone?      [:terminal :aborted]  terminal-abort-action]
   [:boarding  transport-has-room?  [:terminal :boarded]  board-action]
   [:boarding  transport-full?      :moving-to-coast      find-alternate-transport-action]])
```

---

## FSM Data Structure

```clojure
{:fsm board-transport-fsm
 :fsm-state :moving-to-coast
 :fsm-data {:mission-type :board-transport
            :position [row col]
            :transport-id id              ; specific transport to board
            :transport-pos [row col]      ; current transport location
            :boarding-point [row col]     ; coastal cell to reach
            :unit-id id                   ; for Lieutenant tracking
            :recent-moves [[r c] ...]}
 :event-queue []}
```

---

## Guards

### `adjacent-to-transport?`
Army is on a coastal cell adjacent to the transport's sea cell.

```clojure
(defn adjacent-to-transport? [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        transport-pos (get-in ctx [:entity :fsm-data :transport-pos])]
    (and (adjacent? pos transport-pos)
         (coastal-cell? ctx pos))))
```

### `transport-has-room?`
Transport has space for another army (max 6).

```clojure
(defn transport-has-room? [ctx]
  (let [transport-pos (get-in ctx [:entity :fsm-data :transport-pos])
        cell (get-in (:game-map ctx) transport-pos)
        army-count (get cell :army-count 0)]
    (< army-count 6)))
```

### `transport-gone?`
Transport no longer at expected position (moved or destroyed).

```clojure
(defn transport-gone? [ctx]
  (let [transport-pos (get-in ctx [:entity :fsm-data :transport-pos])
        transport-id (get-in ctx [:entity :fsm-data :transport-id])
        cell (get-in (:game-map ctx) transport-pos)
        contents (:contents cell)]
    (or (nil? contents)
        (not= :transport (:type contents))
        (not= transport-id (:unit-id contents)))))
```

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

### `terminal-abort-action`
Called when transport is gone. Notifies Lieutenant.

```clojure
(defn- terminal-abort-action [ctx]
  (let [unit-id (get-in ctx [:entity :fsm-data :unit-id])]
    {:events [{:type :mission-ended
               :priority :high
               :data {:unit-id unit-id :reason :aborted}}]}))
```

### `board-action`
Execute boarding - army moves onto transport. Notifies Lieutenant.

```clojure
(defn board-action [ctx]
  (let [unit-id (get-in ctx [:entity :fsm-data :unit-id])
        transport-id (get-in ctx [:entity :fsm-data :transport-id])
        transport-pos (get-in ctx [:entity :fsm-data :transport-pos])]
    {:board-transport transport-pos
     :events [{:type :army-boarded
               :priority :normal
               :data {:transport-id transport-id :army-id unit-id}}
              {:type :mission-ended
               :priority :high
               :data {:unit-id unit-id :reason :boarded}}]}))
```

### `prepare-board-action`
Prepare for boarding, verify transport still present.

```clojure
(defn prepare-board-action [ctx]
  ;; Could update transport position if it has moved nearby
  nil)
```

---

## Creation Function

```clojure
(defn create-board-transport-data
  "Create FSM data for board-transport mission."
  ([pos transport-id transport-pos]
   (create-board-transport-data pos transport-id transport-pos nil))
  ([pos transport-id transport-pos unit-id]
   (let [boarding-point (find-boarding-point pos transport-pos @atoms/game-map)]
     {:fsm board-transport-fsm
      :fsm-state :moving-to-coast
      :fsm-data {:mission-type :board-transport
                 :position pos
                 :transport-id transport-id
                 :transport-pos transport-pos
                 :boarding-point boarding-point
                 :unit-id unit-id
                 :recent-moves [pos]}})))
```

---

## Boarding Point Selection

The boarding point is a coastal land cell adjacent to the transport's sea cell.

```clojure
(defn find-boarding-point [army-pos transport-pos game-map]
  (let [;; Find land cells adjacent to transport
        coastal-cells (get-matching-neighbors
                        transport-pos game-map neighbor-offsets
                        #(= :land (:type %)))
        ;; Sort by distance from army
        sorted (sort-by #(manhattan-distance army-pos %) coastal-cells)]
    (first sorted)))
```

---

## Transport Movement Handling

If transport moves while army is en route:
1. Lieutenant notifies army of new transport position
2. Army recalculates boarding point
3. Army updates path

```clojure
;; Event from Lieutenant
{:type :transport-moved
 :data {:transport-id id
        :new-pos [row col]}}
```

---

## Multi-Round Loading

Loading is a multi-round process coordinated between armies and transport:

### Beach Queuing
- Beach cells (adjacent to transport's sea cell) hold boarding armies
- If beach is full, armies queue one cell back
- As armies board, queued armies advance to beach cells

### Boarding Priority
- Armies on beach cells board first
- One army boards per beach cell per round
- Transport waits until full (6 armies)

### Army State During Loading
```
:at-beach      - On beach cell, ready to board this round
:queued        - One cell back, waiting for beach space
:boarded       - Successfully on transport
```

### Coordination
- Transport sends `:ready-for-boarding` when at beach
- Armies check if transport is adjacent before boarding
- Lieutenant tracks which armies are assigned to which transport

---

## Open Questions (Tentative)

1. ~~How to handle multiple armies trying to board same transport?~~ → Queuing system
2. ~~Priority/ordering for boarding?~~ → Beach cells first, then queued
3. What if transport moves away repeatedly?
4. ~~Should army wait at coast if transport is en route?~~ → Yes, queue up
5. How to coordinate with transport's movement orders?
6. ~~Should Lieutenant assign specific boarding points to avoid congestion?~~ → Yes, beach cell assignment
