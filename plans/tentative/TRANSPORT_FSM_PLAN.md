# Transport FSM Plan

**STATUS: TENTATIVE**

## Overview

The Transport is a naval unit that carries up to 6 armies for invasion. Managed by the Lieutenant, it cycles through loading, sailing, unloading, and returning. When exploring, it searches for new continents; when directed, it sails to a known target.

---

## Lifecycle

```
Lieutenant produces transport at coastal city
        ↓
Transport moves to beach for loading
        ↓
Armies board (multi-round, up to 6)
        ↓
Transport sails to target (exploring or directed)
        ↓
Transport finds beach on new continent
        ↓
Armies disembark (new Lieutenant spawned)
        ↓
Transport returns to home beach
        ↓
(cycle repeats)
```

---

## FSM States

```
:moving-to-beach  →  Newly produced, sailing to home beach
       ↓
:loading          →  At beach, armies boarding
       ↓
:sailing          →  En route to target (directed invasion)
       OR
:exploring        →  Searching for new continent
       ↓
:scouting         →  Found land, searching for suitable beach
       ↓
:unloading        →  At beachhead, armies disembarking
       ↓
:returning        →  Sailing back to home base
       ↓
:loading          →  (cycle repeats)
```

---

## FSM Transitions

```clojure
(def transport-fsm
  [[:moving-to-beach
     [at-home-beach?       :loading          arrive-beach-action]
     [always               :moving-to-beach  sail-to-beach-action]]
   [:loading
     [fully-loaded?        :sailing|:exploring  depart-action]
     [army-adjacent?       :loading             load-army-action]
     [always               :loading             wait-for-army-action]]
   [:sailing
     [at-destination?      :unloading     begin-unload-action]
     [always               :sailing       sail-toward-target-action]]
   [:exploring
     [land-found?          :scouting      begin-scout-action]
     [always               :exploring     explore-action]]
   [:scouting
     [beach-found?         :unloading     begin-unload-action]
     [coastline-complete?  :exploring     resume-explore-action]
     [always               :scouting      scout-coast-action]]
   [:unloading
     [fully-unloaded?      :returning     depart-home-action]
     [always               :unloading     unload-army-action]]
   [:returning
     [at-home-beach?       :loading       arrive-home-action]
     [always               :returning     sail-home-action]]])
```

---

## FSM Data Structure

```clojure
{:fsm transport-fsm
 :fsm-state :moving-to-beach
 :fsm-data {:transport-id id
            :position [r c]
            :lieutenant-id id           ; owning Lieutenant
            :home-beach {:sea-cell [r c]
                         :beach-cells [[r c] ...]}
            :target {:type :directed|:exploring
                     :continent-id id|nil
                     :coords [r c]|nil}
            :cargo []                   ; army unit-ids, up to 6
            :spawned-lieutenant-id nil  ; set on first unload
            :scouting-data {:land-found [r c]
                            :coastline-visited #{}}
            }
 :event-queue []}
```

---

## Guards

### `at-home-beach?`
Transport is at its designated home beach sea cell.

```clojure
(defn at-home-beach? [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        home-sea (get-in ctx [:entity :fsm-data :home-beach :sea-cell])]
    (= pos home-sea)))
```

### `fully-loaded?`
Transport has 6 armies aboard.

```clojure
(defn fully-loaded? [ctx]
  (let [cargo (get-in ctx [:entity :fsm-data :cargo])]
    (= 6 (count cargo))))
```

### `army-adjacent?`
An army waiting to board is on an adjacent beach cell.

```clojure
(defn army-adjacent? [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        beach-cells (get-in ctx [:entity :fsm-data :home-beach :beach-cells])
        game-map (:game-map ctx)]
    (some (fn [bc]
            (let [cell (get-in game-map bc)]
              (and (= :army (get-in cell [:contents :type]))
                   (= :computer (get-in cell [:contents :owner]))
                   ;; Army has board-transport mission for this transport
                   )))
          beach-cells)))
```

### `land-found?`
While exploring, transport has detected land.

```clojure
(defn land-found? [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])]
    (some #(land-cell? ctx %) (neighbors pos))))
```

### `beach-found?`
While scouting, transport found a suitable beach (3+ adjacent land cells).

```clojure
(defn beach-found? [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        adjacent-land (count-adjacent-land-cells ctx pos)]
    (>= adjacent-land 3)))
```

### `fully-unloaded?`
All armies have disembarked.

```clojure
(defn fully-unloaded? [ctx]
  (let [cargo (get-in ctx [:entity :fsm-data :cargo])]
    (empty? cargo)))
```

---

## Actions

### `depart-action`
Leave beach for target. Determine if directed or exploring.

```clojure
(defn depart-action [ctx]
  (let [target (get-in ctx [:entity :fsm-data :target])
        lt-id (get-in ctx [:entity :fsm-data :lieutenant-id])]
    {:events [{:type :transport-departed
               :priority :normal
               :to lt-id
               :data {:transport-id (get-in ctx [:entity :fsm-data :transport-id])
                      :cargo-count (count (get-in ctx [:entity :fsm-data :cargo]))
                      :target target}}]
     ;; FSM engine will transition to :sailing or :exploring based on target type
     :next-state (if (= :directed (:type target)) :sailing :exploring)}))
```

### `load-army-action`
Load one adjacent army onto transport.

```clojure
(defn load-army-action [ctx]
  (let [army-id (find-adjacent-boarding-army ctx)]
    {:cargo (conj (get-in ctx [:entity :fsm-data :cargo]) army-id)
     :load-from-cell (get-army-cell ctx army-id)}))
```

### `begin-unload-action`
Arrived at beachhead. Spawn new Lieutenant if first time.

```clojure
(defn begin-unload-action [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        spawned-lt (get-in ctx [:entity :fsm-data :spawned-lieutenant-id])
        lt-id (get-in ctx [:entity :fsm-data :lieutenant-id])]
    (if spawned-lt
      ;; Already spawned Lieutenant, just unload
      {:beachhead-pos pos}
      ;; First time: spawn new Lieutenant
      (let [new-lt-id (generate-lieutenant-id)]
        {:spawned-lieutenant-id new-lt-id
         :events [{:type :lieutenant-spawned
                   :priority :high
                   :to lt-id
                   :data {:new-lieutenant-id new-lt-id
                          :beachhead-coords pos
                          :parent-lieutenant-id lt-id}}]}))))
```

### `unload-army-action`
Unload one army onto beachhead.

```clojure
(defn unload-army-action [ctx]
  (let [cargo (get-in ctx [:entity :fsm-data :cargo])
        army-id (first cargo)
        beach-cell (find-empty-beach-cell ctx)
        new-lt-id (get-in ctx [:entity :fsm-data :spawned-lieutenant-id])]
    {:cargo (vec (rest cargo))
     :unload-to-cell beach-cell
     :events [{:type :assign-mission
               :priority :high
               :data {:unit-id army-id
                      :mission-type :disembark-and-rally
                      :new-lieutenant-id new-lt-id}}]}))
```

### `depart-home-action`
All armies unloaded. Begin return journey.

```clojure
(defn depart-home-action [ctx]
  (let [lt-id (get-in ctx [:entity :fsm-data :lieutenant-id])
        pos (get-in ctx [:entity :fsm-data :position])]
    {:events [{:type :transport-unloaded
               :priority :normal
               :to lt-id
               :data {:transport-id (get-in ctx [:entity :fsm-data :transport-id])
                      :beachhead-coords pos}}]}))
```

### `arrive-home-action`
Returned to home beach. Ready for next load.

```clojure
(defn arrive-home-action [ctx]
  (let [lt-id (get-in ctx [:entity :fsm-data :lieutenant-id])]
    {:events [{:type :transport-returned
               :priority :normal
               :to lt-id
               :data {:transport-id (get-in ctx [:entity :fsm-data :transport-id])}}]
     :target nil}))  ; Clear target for next cycle
```

---

## Exploration Behavior

When no directive from General, transport explores:

1. **Sail away from home** - Pick direction away from home continent
2. **Scan for land** - Check adjacent cells for land while moving
3. **Upon finding land** - Transition to :scouting
4. **Scout coastline** - Follow coast looking for beach (3+ land cells)
5. **Found beach** - Transition to :unloading

```clojure
(defn explore-action [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        home (get-in ctx [:entity :fsm-data :home-beach :sea-cell])
        direction (away-from home pos)]
    {:move-to (add-coords pos direction)}))
```

---

## Communication with Lieutenant

### Events Transport Sends
- `:transport-departed` - Left beach with cargo
- `:transport-arrived` - Reached destination
- `:transport-unloaded` - All armies disembarked
- `:transport-returned` - Back at home beach
- `:continent-found` - Discovered new land while exploring
- `:lieutenant-spawned` - New Lieutenant created at beachhead

### Events Transport Receives
- `:set-target` - Lieutenant directs to specific destination
- `:army-ready-to-board` - Army waiting at beach cell

---

## Creation Function

```clojure
(defn create-transport
  "Create a transport for invasion operations."
  [transport-id production-city-pos home-beach lieutenant-id]
  {:fsm transport-fsm
   :fsm-state :moving-to-beach
   :fsm-data {:transport-id transport-id
              :position production-city-pos
              :lieutenant-id lieutenant-id
              :home-beach home-beach
              :target {:type :exploring :coords nil}
              :cargo []
              :spawned-lieutenant-id nil
              :scouting-data nil}
   :event-queue []})
```

---

## Open Questions

1. How long should transport wait for full load vs. departing with partial?
2. Should transport prefer returning to same beachhead on subsequent trips?
3. How to handle transport destroyed during voyage?
4. Should exploring transport report all land found, or only suitable beaches?
