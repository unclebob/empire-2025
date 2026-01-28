# Attack-City FSM Plan

**STATUS: READY FOR IMPLEMENTATION**

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
  [[:approaching
     [stuck?                [:terminal :stuck]      terminal-action]
     [city-already-ours?    [:terminal :conquered]  report-already-captured-action]
     [adjacent-to-target?   :attacking              prepare-attack-action]
     [can-move-toward?      :approaching            move-toward-action]
     [needs-sidestep?       :approaching            sidestep-action]]
   [:attacking
     [city-captured?          [:terminal :conquered]  report-conquest-action]
     [city-already-ours?      [:terminal :conquered]  report-already-captured-action]
     [always                  :attacking              attempt-capture-action]]])
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
            :unit-id id                   ; for Lieutenant tracking
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

### `terminal-action`
Called when stuck with no valid moves. Notifies Lieutenant.

```clojure
(defn- terminal-action [ctx]
  (let [unit-id (get-in ctx [:entity :fsm-data :unit-id])]
    {:events [{:type :mission-ended
               :priority :high
               :data {:unit-id unit-id :reason :stuck}}]}))
```

### `attempt-capture-action`
Trigger movement into city cell, which initiates combat/conquest.

```clojure
(defn attempt-capture-action [ctx]
  (let [target (get-in ctx [:entity :fsm-data :target-city])]
    {:move-to target}))  ; Movement into city triggers conquest attempt
```

### `report-conquest-action`
Report successful capture to squad and Lieutenant.

```clojure
(defn report-conquest-action [ctx]
  (let [unit-id (get-in ctx [:entity :fsm-data :unit-id])
        target (get-in ctx [:entity :fsm-data :target-city])]
    {:events [{:type :city-conquered
               :priority :high
               :data {:coords target :unit-id unit-id}}
              {:type :mission-ended
               :priority :high
               :data {:unit-id unit-id :reason :conquered}}]}))
```

### `report-already-captured-action`
City already ours - report mission complete.

```clojure
(defn report-already-captured-action [ctx]
  (let [unit-id (get-in ctx [:entity :fsm-data :unit-id])]
    {:events [{:type :mission-ended
               :priority :high
               :data {:unit-id unit-id :reason :conquered}}]}))
```

---

## Creation Function

```clojure
(defn create-attack-city-data
  "Create FSM data for attack-city mission."
  ([pos target-city squad-id]
   (create-attack-city-data pos target-city squad-id nil))
  ([pos target-city squad-id unit-id]
   {:fsm attack-city-fsm
    :fsm-state :approaching
    :fsm-data {:mission-type :attack-city
               :position pos
               :target-city target-city
               :squad-id squad-id
               :unit-id unit-id
               :recent-moves [pos]}}))
```

---

## Combat Integration

The actual combat/conquest mechanics are handled by the existing combat system:
- Army moves into city cell
- Combat resolves (army may be destroyed)
- If army survives, city is captured

The FSM orchestrates the approach and triggers the move; combat resolution is external.

---

## Resolved Questions

1. **Should multiple armies attack simultaneously, or take turns?**
   - Armies attack independently once squad enters `:attacking` state
   - Each army executes its own attack-city FSM
   - First army to successfully capture the city triggers squad success

2. **How does squad coordinate attack order?**
   - Squad issues `:attack-city` missions to all armies when `at-target-city?` is true
   - Armies then act independently - no turn-taking coordination needed
   - See SQUAD_FSM_PLAN.md `begin-attack-action`

3. **If first army fails, does next army automatically attack?**
   - Yes - remaining armies continue their attack-city FSM
   - Army destruction during combat is handled by combat system, not FSM
   - Squad tracks army losses via `:lost` status

4. **Should army retreat if heavily damaged?**
   - No - armies attack until city is captured or army is destroyed
   - Simplifies FSM; retreat logic could be added later if needed

5. **How to handle city defended by enemy units?**
   - Combat system handles this - army moves into city cell
   - Combat resolves between army and defender
   - If army survives, it continues; if destroyed, squad notes loss
