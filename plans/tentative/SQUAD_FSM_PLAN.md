# Squad FSM Plan

**STATUS: TENTATIVE**

## Overview

The Squad is a command entity that coordinates multiple armies to conquer a free city. Created by the Lieutenant when a free city is discovered. The squad manages army assembly, movement to target, and attack coordination.

---

## Lifecycle

```
Lieutenant discovers free city
        ↓
Lieutenant creates Squad
        ↓
Squad recruits armies (rally-to-squad missions)
        ↓
Squad moves toward city (move-with-squad missions)
        ↓
Squad attacks city (attack-city missions)
        ↓
City conquered → Squad disbanded → Armies to staging
        OR
Squad failed → Lieutenant creates new Squad
```

---

## FSM States

```
:assembling  →  Recruiting armies, waiting at rally point
     ↓
:moving      →  Squad moving toward target city
     ↓
:attacking   →  Adjacent to city, armies attacking
     ↓
[:terminal :success]  →  City conquered
[:terminal :failed]   →  Squad destroyed or unable to conquer
```

---

## FSM Transitions

```clojure
(def squad-fsm
  [[:assembling
     [assembly-complete?   :moving              begin-movement-action]
     [assembly-timeout?    :moving              begin-with-available-action]
     [squad-cancelled?     [:terminal :failed]  disband-cancelled-action]
     [always               :assembling          wait-for-armies-action]]
   [:moving
     [squad-destroyed?     [:terminal :failed]  disband-failure-action]
     [at-target-city?      :attacking           begin-attack-action]
     [always               :moving              issue-move-orders-action]]
   [:attacking
     [city-conquered?      [:terminal :success] disband-success-action]
     [squad-destroyed?     [:terminal :failed]  disband-failure-action]
     [always               :attacking           continue-attack-action]]])
```

---

## FSM Data Structure

```clojure
{:fsm squad-fsm
 :fsm-state :assembling
 :fsm-data {:squad-id id
            :target-city [r c]
            :rally-point [r c]
            :lieutenant-id id
            :target-size 3-5            ; desired army count
            :assembly-deadline round    ; 10 rounds from creation
            :armies [{:unit-id id
                      :status :rallying|:present|:moving|:attacking|:lost}
                     ...]
            :armies-present-count 0}
 :event-queue []}
```

---

## Guards

### `assembly-complete?`
All recruited armies have arrived at rally point.

```clojure
(defn assembly-complete? [ctx]
  (let [armies (get-in ctx [:entity :fsm-data :armies])
        target-size (get-in ctx [:entity :fsm-data :target-size])]
    (>= (count (filter #(= :present (:status %)) armies))
        target-size)))
```

### `assembly-timeout?`
Assembly deadline reached with at least 3 armies present.

```clojure
(defn assembly-timeout? [ctx]
  (let [deadline (get-in ctx [:entity :fsm-data :assembly-deadline])
        current-round (:round ctx)
        present-count (get-in ctx [:entity :fsm-data :armies-present-count])]
    (and (>= current-round deadline)
         (>= present-count 3))))
```

### `at-target-city?`
At least one army is adjacent to target city.

```clojure
(defn at-target-city? [ctx]
  (let [target (get-in ctx [:entity :fsm-data :target-city])
        armies (get-in ctx [:entity :fsm-data :armies])]
    (some #(adjacent? (:coords %) target)
          (filter #(#{:moving :present} (:status %)) armies))))
```

### `city-conquered?`
Target city now belongs to computer.

```clojure
(defn city-conquered? [ctx]
  (let [target (get-in ctx [:entity :fsm-data :target-city])
        cell (get-in (:game-map ctx) target)]
    (= :computer (:city-status cell))))
```

### `squad-destroyed?`
No armies remaining (all lost).

```clojure
(defn squad-destroyed? [ctx]
  (let [armies (get-in ctx [:entity :fsm-data :armies])
        active (filter #(not= :lost (:status %)) armies)]
    (empty? active)))
```

---

## Actions

### `begin-movement-action`
Assembly complete. Issue move-with-squad missions to all present armies.

```clojure
(defn begin-movement-action [ctx]
  (let [armies (get-in ctx [:entity :fsm-data :armies])
        present (filter #(= :present (:status %)) armies)]
    {:armies (mapv #(assoc % :status :moving) armies)
     :events (mapv (fn [a]
                     {:type :assign-mission
                      :priority :high
                      :data {:unit-id (:unit-id a)
                             :mission-type :move-with-squad
                             :squad-id (get-in ctx [:entity :fsm-data :squad-id])}})
                   present)}))
```

### `begin-attack-action`
Adjacent to city. Issue attack-city missions to all armies.

```clojure
(defn begin-attack-action [ctx]
  (let [armies (get-in ctx [:entity :fsm-data :armies])
        target (get-in ctx [:entity :fsm-data :target-city])
        active (filter #(#{:moving :present} (:status %)) armies)]
    {:armies (mapv #(if (#{:moving :present} (:status %))
                      (assoc % :status :attacking)
                      %)
                   armies)
     :events (mapv (fn [a]
                     {:type :assign-mission
                      :priority :high
                      :data {:unit-id (:unit-id a)
                             :mission-type :attack-city
                             :target-city target}})
                   active)}))
```

### `disband-success-action`
City conquered. Notify Lieutenant, release armies to staging.

```clojure
(defn disband-success-action [ctx]
  (let [squad-id (get-in ctx [:entity :fsm-data :squad-id])
        lt-id (get-in ctx [:entity :fsm-data :lieutenant-id])
        target (get-in ctx [:entity :fsm-data :target-city])
        armies (get-in ctx [:entity :fsm-data :armies])
        survivors (filter #(not= :lost (:status %)) armies)]
    {:events (concat
               [{:type :squad-mission-complete
                 :priority :high
                 :to lt-id
                 :data {:squad-id squad-id
                        :result :success
                        :city-conquered target}}]
               ;; Release survivors to staging
               (mapv (fn [a]
                       {:type :assign-mission
                        :priority :normal
                        :data {:unit-id (:unit-id a)
                               :mission-type :hurry-up-and-wait
                               :destination :beach}})
                     survivors))}))
```

### `disband-failure-action`
Squad failed. Notify Lieutenant to create new squad.

```clojure
(defn disband-failure-action [ctx]
  (let [squad-id (get-in ctx [:entity :fsm-data :squad-id])
        lt-id (get-in ctx [:entity :fsm-data :lieutenant-id])
        target (get-in ctx [:entity :fsm-data :target-city])]
    {:events [{:type :squad-mission-complete
               :priority :high
               :to lt-id
               :data {:squad-id squad-id
                      :result :failed
                      :target-city target}}]}))
```

---

## Army Recruitment

When Lieutenant creates a squad, it assigns armies from:
1. Newly produced armies (`:unit-needs-orders`)
2. Terminated mission armies (`:mission-ended`)
3. Redirected idle armies

Each assigned army gets `rally-to-squad` mission.

---

## Rally Point Selection

Lieutenant chooses rally point based on:
1. Near target city (minimize travel after assembly)
2. Accessible to assigned armies
3. Open land cells for multiple armies

```clojure
(defn select-rally-point [target-city army-positions]
  ;; Find land cell ~3-5 cells from target
  ;; That minimizes total army travel distance
  ...)
```

---

## Communication with Lieutenant

### Events Squad Sends
- `:squad-mission-complete` - Success or failure
- `:army-lost` - When an army is destroyed

### Events Squad Receives
- `:army-assigned` - New army joining squad
- `:unit-arrived` - Army reached rally point
- `:mission-ended` - Army's individual mission ended

---

## Creation Function

```clojure
(defn create-squad
  "Create a squad to conquer a free city."
  [squad-id target-city rally-point lieutenant-id target-size current-round]
  {:fsm squad-fsm
   :fsm-state :assembling
   :fsm-data {:squad-id squad-id
              :target-city target-city
              :rally-point rally-point
              :lieutenant-id lieutenant-id
              :target-size target-size
              :assembly-deadline (+ current-round 10)
              :armies []
              :armies-present-count 0}
   :event-queue []})
```

---

## Open Questions

1. How does squad track army positions during movement?
2. Should squad issue individual move orders, or just waypoints?
3. How to handle stragglers who fall behind during movement?
4. Should squad wait for all armies before attacking, or attack with first arrivals?
