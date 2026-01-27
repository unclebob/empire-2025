# Attack-City FSM Plan

**STATUS: TENTATIVE**

## Overview

Army mission to capture a target city. Used during squad `:attacking` state. Army moves adjacent to city and attempts conquest. Reports success or failure to squad.

---

## Behavioral Requirements

1. **Move Adjacent**: If not adjacent to target, move toward it
2. **Attack**: When adjacent, attempt to capture city
3. **Report Outcome**: Post `:city-conquered` or `:attack-failed` event
4. **Handle Failure**: If attack fails (army destroyed), mission terminates

**Terminal Conditions:**
- City conquered → report success
- Army destroyed during attack → mission ends (army gone)
- City captured by another unit → report and terminate

---

## FSM States

```
:approaching  →  Moving to get adjacent to target city
      ↓
:attacking    →  Adjacent to city, attempting capture
      ↓
      ├── [:terminal :conquered]  →  City captured successfully
      └── [:terminal :failed]     →  Attack failed (army destroyed or city already taken)
```

---

## FSM Transitions

```clojure
(def attack-city-fsm
  [;; Approaching target city
   [:approaching  adjacent-to-target?   :attacking    prepare-attack-action]
   [:approaching  can-move-toward?      :approaching  move-toward-action]
   [:approaching  needs-sidestep?       :approaching  sidestep-action]
   [:approaching  city-already-ours?    [:terminal :conquered]  report-already-captured-action]

   ;; Attacking
   [:attacking  city-captured?          [:terminal :conquered]  report-conquest-action]
   [:attacking  city-already-ours?      [:terminal :conquered]  report-already-captured-action]
   [:attacking  always                  :attacking              attempt-capture-action]])
```

**Note**: Army destruction during combat is handled by the combat system, not the FSM. If army is destroyed, the entity ceases to exist.

---

## FSM Data Structure

```clojure
{:fsm attack-city-fsm
 :fsm-state :approaching
 :fsm-data {:mission-type :attack-city
            :position [row col]
            :target-city [row col]
            :squad-id id                  ; parent squad
            :lieutenant-id id
            :recent-moves [[r c] ...]}
 :event-queue []}
```

---

## Guards

### `adjacent-to-target?`
```clojure
(defn adjacent-to-target? [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        target (get-in ctx [:entity :fsm-data :target-city])]
    (adjacent? pos target)))
```

### `city-captured?`
```clojure
(defn city-captured? [ctx]
  (let [target (get-in ctx [:entity :fsm-data :target-city])
        cell (get-in (:game-map ctx) target)]
    (= :computer (:city-status cell))))
```

### `city-already-ours?`
Same as `city-captured?` - city already belongs to computer.

---

## Actions

### `attempt-capture-action`
Trigger movement into city cell, which initiates combat/conquest.

```clojure
(defn attempt-capture-action [ctx]
  (let [target (get-in ctx [:entity :fsm-data :target-city])]
    {:move-to target}))  ; Movement into city triggers conquest attempt
```

### `report-conquest-action`
Report successful capture to squad.

```clojure
(defn report-conquest-action [ctx]
  (let [target (get-in ctx [:entity :fsm-data :target-city])]
    {:events [{:type :city-conquered
               :priority :high
               :data {:coords target}}]}))
```

---

## Combat Integration

The actual combat/conquest mechanics are handled by the existing combat system:
- Army moves into city cell
- Combat resolves (army may be destroyed)
- If army survives, city is captured

The FSM orchestrates the approach and triggers the move; combat resolution is external.

---

## Open Questions (Tentative)

1. Should multiple armies attack simultaneously, or take turns?
2. How does squad coordinate attack order?
3. If first army fails, does next army automatically attack?
4. Should army retreat if heavily damaged? (hits remaining)
5. How to handle city defended by enemy units?
