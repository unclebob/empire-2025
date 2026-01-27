# Fighter Patrol FSM Plan

**STATUS: TENTATIVE**

## Overview

Fighter patrols provide aerial reconnaissance for early warning of enemy approach. Launched from a city, the fighter flies out in a random direction, then returns to base. Cycle repeats. If enemy spotted, reports to Lieutenant and sidesteps to avoid engagement.

---

## Behavioral Requirements

1. **Launch from city** - Take off from airport
2. **Fly outbound** - Random direction away from base
3. **Turn back at half fuel** - Ensure enough fuel to return
4. **Report sightings** - Enemy spotted → report to Lieutenant
5. **Avoid combat** - Sidestep enemies, do not engage
6. **Return to base** - Land at origin city
7. **Repeat** - Brief refuel pause, then launch again

---

## FSM States

```
:launching    →  Taking off from city
     ↓
:flying-out   →  Flying away from base in chosen direction
     ↓
:returning    →  Flying back toward base
     ↓
:landing      →  At base, refueling
     ↓
:launching    →  (cycle repeats)
```

---

## FSM Transitions

```clojure
(def fighter-patrol-fsm
  [[:launching
     [clear-of-city?           :flying-out    pick-direction-action]
     [always                   :launching     takeoff-action]]
   [:flying-out
     [fuel-at-half?           :returning     turn-back-action]
     [enemy-adjacent?         :flying-out    report-and-sidestep-action]
     [at-map-edge?            :returning     turn-back-action]
     [always                  :flying-out    fly-outbound-action]]
   [:returning
     [at-base?                :landing       land-action]
     [enemy-adjacent?         :returning     report-and-sidestep-action]
     [always                  :returning     fly-toward-base-action]]
   [:landing
     [refueled?               :launching     nil]
     [always                  :landing       refuel-action]]])
```

---

## FSM Data Structure

```clojure
{:fsm fighter-patrol-fsm
 :fsm-state :launching
 :fsm-data {:fighter-id id
            :position [r c]
            :base-city [r c]           ; origin city for return
            :lieutenant-id id
            :fuel-remaining n          ; turns of flight left
            :max-fuel n                ; full tank
            :patrol-direction [dr dc]  ; chosen outbound direction
            :enemies-reported #{}}     ; avoid duplicate reports
 :event-queue []}
```

---

## Guards

### `clear-of-city?`
Fighter has moved off the city cell.

```clojure
(defn clear-of-city? [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        base (get-in ctx [:entity :fsm-data :base-city])]
    (not= pos base)))
```

### `fuel-at-half?`
Fuel is at or below 50% - time to turn back.

```clojure
(defn fuel-at-half? [ctx]
  (let [fuel (get-in ctx [:entity :fsm-data :fuel-remaining])
        max-fuel (get-in ctx [:entity :fsm-data :max-fuel])]
    (<= fuel (/ max-fuel 2))))
```

### `enemy-adjacent?`
Enemy unit in adjacent cell.

```clojure
(defn enemy-adjacent? [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])]
    (some #(enemy-at? ctx %) (neighbors pos))))
```

### `at-base?`
Fighter is at base city.

```clojure
(defn at-base? [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        base (get-in ctx [:entity :fsm-data :base-city])]
    (= pos base)))
```

### `at-map-edge?`
Fighter has reached edge of map.

```clojure
(defn at-map-edge? [ctx]
  (let [[r c] (get-in ctx [:entity :fsm-data :position])
        [dr dc] (get-in ctx [:entity :fsm-data :patrol-direction])
        height (count (:game-map ctx))
        width (count (first (:game-map ctx)))]
    (or (and (neg? dr) (zero? r))
        (and (pos? dr) (= r (dec height)))
        (and (neg? dc) (zero? c))
        (and (pos? dc) (= c (dec width))))))
```

### `refueled?`
Fuel is back to max.

```clojure
(defn refueled? [ctx]
  (let [fuel (get-in ctx [:entity :fsm-data :fuel-remaining])
        max-fuel (get-in ctx [:entity :fsm-data :max-fuel])]
    (= fuel max-fuel)))
```

---

## Actions

### `pick-direction-action`
Choose random outbound direction.

```clojure
(defn pick-direction-action [ctx]
  (let [directions [[0 1] [0 -1] [1 0] [-1 0]
                    [1 1] [1 -1] [-1 1] [-1 -1]]
        dir (rand-nth directions)]
    {:patrol-direction dir}))
```

### `takeoff-action`
Move off city cell in patrol direction.

```clojure
(defn takeoff-action [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        dir (get-in ctx [:entity :fsm-data :patrol-direction])
        new-pos (add-coords pos dir)]
    {:move-to new-pos
     :fuel-remaining (dec (get-in ctx [:entity :fsm-data :fuel-remaining]))}))
```

### `fly-outbound-action`
Continue flying in patrol direction.

```clojure
(defn fly-outbound-action [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        dir (get-in ctx [:entity :fsm-data :patrol-direction])
        new-pos (add-coords pos dir)]
    {:move-to new-pos
     :fuel-remaining (dec (get-in ctx [:entity :fsm-data :fuel-remaining]))}))
```

### `turn-back-action`
Reverse direction to head home.

```clojure
(defn turn-back-action [ctx]
  nil)  ; State transition handles the change; direction now toward base
```

### `fly-toward-base-action`
Fly toward base city.

```clojure
(defn fly-toward-base-action [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        base (get-in ctx [:entity :fsm-data :base-city])
        next-pos (step-toward pos base)]
    {:move-to next-pos
     :fuel-remaining (dec (get-in ctx [:entity :fsm-data :fuel-remaining]))}))
```

### `report-and-sidestep-action`
Report enemy, sidestep to avoid.

```clojure
(defn report-and-sidestep-action [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        lt-id (get-in ctx [:entity :fsm-data :lieutenant-id])
        enemies (find-adjacent-enemies ctx pos)
        reported (get-in ctx [:entity :fsm-data :enemies-reported])
        new-enemies (remove reported enemies)
        sidestep-pos (find-sidestep-away-from-enemies ctx pos enemies)]
    {:move-to sidestep-pos
     :fuel-remaining (dec (get-in ctx [:entity :fsm-data :fuel-remaining]))
     :enemies-reported (into reported new-enemies)
     :events (mapv (fn [e]
                     {:type :enemy-spotted
                      :priority :high
                      :to lt-id
                      :data {:enemy-type (:type e)
                             :enemy-coords (:coords e)
                             :spotted-by :fighter}})
                   new-enemies)}))
```

### `land-action`
Land at base city.

```clojure
(defn land-action [ctx]
  {:enemies-reported #{}})  ; Clear for next patrol
```

### `refuel-action`
Refuel at base.

```clojure
(defn refuel-action [ctx]
  (let [fuel (get-in ctx [:entity :fsm-data :fuel-remaining])
        max-fuel (get-in ctx [:entity :fsm-data :max-fuel])]
    {:fuel-remaining (min max-fuel (+ fuel 2))}))  ; Refuel 2 per turn
```

---

## Communication with Lieutenant

### Events Fighter Sends
- `:enemy-spotted` - Detected enemy unit

### Events Fighter Receives
- None (autonomous patrol)

---

## Creation Function

```clojure
(defn create-fighter-patrol
  "Create a fighter for patrol duty."
  [fighter-id base-city lieutenant-id max-fuel]
  {:fsm fighter-patrol-fsm
   :fsm-state :launching
   :fsm-data {:fighter-id fighter-id
              :position base-city
              :base-city base-city
              :lieutenant-id lieutenant-id
              :fuel-remaining max-fuel
              :max-fuel max-fuel
              :patrol-direction nil
              :enemies-reported #{}}
   :event-queue []})
```

---

## Open Questions

1. Should patrol direction be truly random, or biased away from explored areas?
2. Should fighter engage lone enemy fighters, or always avoid?
3. How to handle fighter shot down during patrol?
