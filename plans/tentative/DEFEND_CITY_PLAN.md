# Defend-City FSM Plan

**STATUS: TENTATIVE**

## Overview

Army mission to take a defensive position around a conquered city. Used during squad `:defending` state. Key constraint: **do not block city exits** so units can still be produced and leave.

---

## Behavioral Requirements

1. **Move to Defensive Position**: Move to assigned position around city
2. **Don't Block Exits**: Position must not prevent units from leaving city
3. **Sidestepping**: Navigate around other defenders and obstacles
4. **Sentry Mode**: Enter sentry mode upon reaching position
5. **Wake on Enemy**: Should wake if enemy approaches (handled by wake conditions)

**Terminal Conditions:**
- Reach defensive position and enter sentry mode

---

## FSM States

```
:moving  →  Moving to assigned defensive position
    ↓
[:terminal :defending]  →  At position, in sentry mode
```

---

## FSM Transitions

```clojure
(def defend-city-fsm
  [;; Moving to defensive position
   [:moving  stuck?              [:terminal :stuck]      terminal-action]
   [:moving  at-position?        [:terminal :defending]  enter-defense-action]
   [:moving  position-blocked?   :moving                 reassign-position-action]
   [:moving  can-move-toward?    :moving                 move-toward-action]
   [:moving  needs-sidestep?     :moving                 sidestep-action]])
```

---

## FSM Data Structure

```clojure
{:fsm defend-city-fsm
 :fsm-state :moving
 :fsm-data {:mission-type :defend-city
            :position [row col]
            :defensive-position [row col]  ; assigned spot around city
            :city-coords [row col]         ; city being defended
            :squad-id id
            :unit-id id                    ; for Lieutenant tracking
            :recent-moves [[r c] ...]}
 :event-queue []}
```

---

## Defensive Position Assignment

The Lieutenant or Squad assigns defensive positions around the city. Positions must satisfy:

1. **Adjacent to city** (one of 8 neighboring cells)
2. **Not blocking exits**: At least one exit must remain clear for production
3. **Land cell**: Must be traversable land
4. **Not occupied**: By another friendly unit

### Exit Blocking Rules

A city has up to 8 adjacent cells. To avoid blocking:
- If city has only 1-2 land exits, leave at least 1 clear
- Prefer diagonal positions (less likely to block direct exits)
- Maximum defenders = (land exits - 1)

```clojure
(defn valid-defensive-positions [city-pos game-map current-defenders]
  (let [all-adjacent (get-land-neighbors city-pos game-map)
        occupied (set (map :defensive-position current-defenders))
        available (remove occupied all-adjacent)
        ;; Must leave at least one exit
        max-defenders (max 0 (dec (count all-adjacent)))]
    (when (< (count current-defenders) max-defenders)
      available)))
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

### `enter-defense-action`
Enter sentry mode at defensive position. Notifies Lieutenant mission complete.

```clojure
(defn enter-defense-action [ctx]
  (let [unit-id (get-in ctx [:entity :fsm-data :unit-id])]
    {:enter-sentry-mode true
     :wake-conditions [:enemy-adjacent :attacked]
     :events [{:type :mission-ended
               :priority :high
               :data {:unit-id unit-id :reason :defending}}]}))
```

### `reassign-position-action`
If assigned position is now blocked, request new position from squad.

```clojure
(defn reassign-position-action [ctx]
  (let [city (get-in ctx [:entity :fsm-data :city-coords])
        current-pos (get-in ctx [:entity :fsm-data :position])
        new-position (find-alternate-defensive-position ctx city current-pos)]
    {:defensive-position new-position}))
```

---

## Creation Function

```clojure
(defn create-defend-city-data
  "Create FSM data for defend-city mission."
  ([pos defensive-position city-coords squad-id]
   (create-defend-city-data pos defensive-position city-coords squad-id nil))
  ([pos defensive-position city-coords squad-id unit-id]
   {:fsm defend-city-fsm
    :fsm-state :moving
    :fsm-data {:mission-type :defend-city
               :position pos
               :defensive-position defensive-position
               :city-coords city-coords
               :squad-id squad-id
               :unit-id unit-id
               :recent-moves [pos]}}))
```

---

## Relationship to Hurry-Up-And-Wait

Very similar, with additions:
- Defensive position assignment logic
- Position reassignment if blocked
- Wake conditions for defense

**Consider**: Implementing as variant of hurry-up-and-wait with defensive position validation.

---

## Open Questions (Tentative)

1. Who assigns defensive positions - Lieutenant or Squad?
2. How to handle more armies than defensive positions?
3. Should defenders rotate or maintain fixed positions?
4. What wake conditions apply? (enemy adjacent, under attack, ordered to move)
5. Should defenders pursue enemies that approach then retreat?
