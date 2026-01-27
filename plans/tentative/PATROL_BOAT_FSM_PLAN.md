# Patrol Boat FSM Plan

**STATUS: TENTATIVE**

## Overview

Patrol boats provide long-range naval reconnaissance by exploring the world's oceans. They map coastlines, discover continents, and report enemy positions. When encountering enemies, they flee and report rather than engage.

---

## Behavioral Requirements

1. **Explore oceans** - Visit unexplored sea cells
2. **Map coastlines** - Follow newly discovered land
3. **Report discoveries** - Continents, enemy positions
4. **Flee from enemies** - Do not engage, retreat and report
5. **Circumnavigate** - Eventually explore entire world

---

## FSM States

```
:exploring     →  Moving through sea, seeking unexplored areas
     ↓
:following-coast  →  Discovered land, mapping coastline
     ↓
:fleeing       →  Enemy detected, moving away
     ↓
:exploring     →  (resumes after safe)
```

---

## FSM Transitions

```clojure
(def patrol-boat-fsm
  [[:exploring
     [enemy-adjacent?         :fleeing           flee-and-report-action]
     [land-adjacent?          :following-coast   begin-coast-follow-action]
     [unexplored-sea-nearby?  :exploring         explore-sea-action]
     [always                  :exploring         seek-unexplored-action]]
   [:following-coast
     [enemy-adjacent?         :fleeing           flee-and-report-action]
     [coast-complete?         :exploring         resume-explore-action]
     [always                  :following-coast   follow-coast-action]]
   [:fleeing
     [safe-distance?          :exploring         resume-explore-action]
     [always                  :fleeing           flee-action]]])
```

---

## FSM Data Structure

```clojure
{:fsm patrol-boat-fsm
 :fsm-state :exploring
 :fsm-data {:patrol-boat-id id
            :position [r c]
            :lieutenant-id id
            :explore-direction [dr dc]     ; current heading
            :coastline-data {:land-found [r c]
                             :coast-visited #{}
                             :start-pos [r c]}
            :enemies-reported #{}          ; avoid duplicate reports
            :continents-reported #{}}      ; avoid duplicate reports
 :event-queue []}
```

---

## Guards

### `enemy-adjacent?`
Enemy ship in adjacent cell.

```clojure
(defn enemy-adjacent? [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])]
    (some #(enemy-ship-at? ctx %) (neighbors pos))))
```

### `land-adjacent?`
Land cell adjacent (discovered new continent).

```clojure
(defn land-adjacent? [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])]
    (some #(land-at? ctx %) (neighbors pos))))
```

### `unexplored-sea-nearby?`
Unexplored sea cell within range.

```clojure
(defn unexplored-sea-nearby? [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])]
    (some #(unexplored-sea-at? ctx %) (neighbors pos))))
```

### `coast-complete?`
Returned to starting position of coast follow (circumnavigated land).

```clojure
(defn coast-complete? [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        start (get-in ctx [:entity :fsm-data :coastline-data :start-pos])
        visited (get-in ctx [:entity :fsm-data :coastline-data :coast-visited])]
    (and (= pos start)
         (> (count visited) 4))))  ; Must have actually traveled
```

### `safe-distance?`
No enemies within 3 cells.

```clojure
(defn safe-distance? [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])]
    (not-any? #(enemy-within-distance? ctx % 3) (all-enemies ctx))))
```

---

## Actions

### `explore-sea-action`
Move to adjacent unexplored sea cell.

```clojure
(defn explore-sea-action [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        unexplored (filter #(unexplored-sea-at? ctx %) (neighbors pos))
        target (first unexplored)]
    {:move-to target}))
```

### `seek-unexplored-action`
No adjacent unexplored - head toward distant unexplored area.

```clojure
(defn seek-unexplored-action [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        target (find-nearest-unexplored-sea ctx pos)]
    (if target
      {:move-to (step-toward pos target)}
      ;; World fully explored - just patrol
      {:move-to (random-adjacent-sea ctx pos)})))
```

### `begin-coast-follow-action`
Found land - start following coastline, report discovery.

```clojure
(defn begin-coast-follow-action [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        lt-id (get-in ctx [:entity :fsm-data :lieutenant-id])
        land-pos (first (filter #(land-at? ctx %) (neighbors pos)))
        reported (get-in ctx [:entity :fsm-data :continents-reported])]
    (if (contains? reported land-pos)
      ;; Already reported this continent
      {:coastline-data {:land-found land-pos
                        :coast-visited #{pos}
                        :start-pos pos}}
      ;; New continent discovery
      {:coastline-data {:land-found land-pos
                        :coast-visited #{pos}
                        :start-pos pos}
       :continents-reported (conj reported land-pos)
       :events [{:type :continent-found
                 :priority :high
                 :to lt-id
                 :data {:discovery-coords land-pos
                        :discovered-by :patrol-boat}}]})))
```

### `follow-coast-action`
Continue following coastline, keeping land on one side.

```clojure
(defn follow-coast-action [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        visited (get-in ctx [:entity :fsm-data :coastline-data :coast-visited])
        next-pos (find-coast-follow-move ctx pos visited)]
    {:move-to next-pos
     :coastline-data (update (get-in ctx [:entity :fsm-data :coastline-data])
                             :coast-visited conj next-pos)}))
```

### `flee-and-report-action`
Enemy detected - report and flee.

```clojure
(defn flee-and-report-action [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        lt-id (get-in ctx [:entity :fsm-data :lieutenant-id])
        enemies (find-adjacent-enemies ctx pos)
        reported (get-in ctx [:entity :fsm-data :enemies-reported])
        new-enemies (remove #(contains? reported (:id %)) enemies)
        flee-pos (find-flee-direction ctx pos enemies)]
    {:move-to flee-pos
     :enemies-reported (into reported (map :id new-enemies))
     :events (mapv (fn [e]
                     {:type :enemy-spotted
                      :priority :high
                      :to lt-id
                      :data {:enemy-type (:type e)
                             :enemy-coords (:coords e)
                             :spotted-by :patrol-boat}})
                   new-enemies)}))
```

### `flee-action`
Continue fleeing from enemy.

```clojure
(defn flee-action [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        enemies (find-nearby-enemies ctx pos 3)
        flee-pos (find-flee-direction ctx pos enemies)]
    {:move-to flee-pos}))
```

### `resume-explore-action`
Return to exploration after coast follow or fleeing.

```clojure
(defn resume-explore-action [ctx]
  {:coastline-data nil})
```

---

## Communication with Lieutenant

### Events Patrol Boat Sends
- `:continent-found` - Discovered new land
- `:enemy-spotted` - Detected enemy ship

### Events Patrol Boat Receives
- None (autonomous exploration)

---

## Creation Function

```clojure
(defn create-patrol-boat
  "Create a patrol boat for world exploration."
  [patrol-boat-id start-pos lieutenant-id]
  {:fsm patrol-boat-fsm
   :fsm-state :exploring
   :fsm-data {:patrol-boat-id patrol-boat-id
              :position start-pos
              :lieutenant-id lieutenant-id
              :explore-direction [0 1]  ; initial heading
              :coastline-data nil
              :enemies-reported #{}
              :continents-reported #{}}
   :event-queue []})
```

---

## World Exploration Strategy

To ensure complete coverage:

1. **Spiral outward** - Start from home, expand search radius
2. **Prefer unexplored** - Always move toward unexplored sea when possible
3. **Coast follow** - When finding land, circumnavigate to map it
4. **Resume heading** - After coast follow, continue in original direction

---

## Open Questions

1. Should two patrol boats coordinate to cover different areas?
2. How to handle patrol boat destroyed?
3. Should patrol boat return to base periodically, or patrol indefinitely?
4. Report enemy cities as well as units?
