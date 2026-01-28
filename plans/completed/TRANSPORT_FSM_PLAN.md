# Transport FSM Plan

**STATUS: READY FOR IMPLEMENTATION**

## Overview

The Transport is a naval unit that carries up to 6 armies for invasion. Managed by the Lieutenant, it cycles through loading, sailing, unloading, and returning. When exploring, it searches for new continents; when directed, it sails to a known target.

## Terminology

- **Transport-landing**: Sea cell where transport docks (3-7 adjacent land cells)
- **Beach**: The land cells adjacent to a transport-landing (where armies stage/board)
- Transports use transport-landings; armies use beaches

---

## Lifecycle

```
Lieutenant produces transport at coastal city
        ↓
Transport moves to transport-landing for loading
        ↓
Armies board from beach (multi-round, up to 6)
        ↓
Transport sails to target (exploring or directed)
        ↓
Transport finds transport-landing on new continent
        ↓
Armies disembark to beach (new Lieutenant spawned)
        ↓
Transport returns to home transport-landing
        ↓
(cycle repeats)
```

---

## FSM States

```
:moving-to-landing  →  Newly produced, sailing to home transport-landing
       ↓
:loading            →  At transport-landing, armies boarding from beach
       ↓
:sailing            →  En route to target (directed invasion)
       OR
:exploring          →  Searching for new continent
       ↓
:scouting           →  Found land, searching for suitable transport-landing
       ↓
:unloading          →  At foreign transport-landing, armies disembarking to beach
       ↓
:returning          →  Sailing back to home transport-landing
       ↓
:loading            →  (cycle repeats)
```

---

## FSM Transitions

```clojure
(def transport-fsm
  [[:moving-to-landing
     [at-home-landing?     :loading            arrive-landing-action]
     [always               :moving-to-landing  sail-to-landing-action]]
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
     [landing-found?       :unloading     begin-unload-action]
     [coastline-complete?  :exploring     resume-explore-action]
     [always               :scouting      scout-coast-action]]
   [:unloading
     [fully-unloaded?      :returning     depart-home-action]
     [always               :unloading     unload-army-action]]
   [:returning
     [at-home-landing?     :loading       arrive-home-action]
     [always               :returning     sail-home-action]]])

;; Transports wait for full load (6 armies) before departing
```

---

## FSM Data Structure

```clojure
{:fsm transport-fsm
 :fsm-state :moving-to-landing
 :fsm-data {:transport-id id
            :position [r c]
            :lieutenant-id id           ; owning Lieutenant
            :home-landing {:transport-landing [r c]    ; sea cell
                           :beach [[r c] ...]}         ; adjacent land cells
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

### `at-home-landing?`
Transport is at its designated home transport-landing (sea cell).

```clojure
(defn at-home-landing? [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        home-landing (get-in ctx [:entity :fsm-data :home-landing :transport-landing])]
    (= pos home-landing)))
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
        beach (get-in ctx [:entity :fsm-data :home-landing :beach])
        game-map (:game-map ctx)]
    (some (fn [beach-cell]
            (let [cell (get-in game-map beach-cell)]
              (and (= :army (get-in cell [:contents :type]))
                   (= :computer (get-in cell [:contents :owner]))
                   ;; Army has board-transport mission for this transport
                   )))
          beach)))
```

### `land-found?`
While exploring, transport has detected land.

```clojure
(defn land-found? [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])]
    (some #(land-cell? ctx %) (neighbors pos))))
```

### `landing-found?`
While scouting, transport found a suitable transport-landing (3-7 adjacent land cells).

```clojure
(defn landing-found? [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        adjacent-land (count-adjacent-land-cells ctx pos)]
    (and (>= adjacent-land 3)
         (<= adjacent-land 7))))
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
4. **Scout coastline** - Follow coast looking for transport-landing (3-7 adjacent land cells)
5. **Found transport-landing** - Transition to :unloading (armies disembark to beach)

```clojure
(defn explore-action [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        home-landing (get-in ctx [:entity :fsm-data :home-landing :transport-landing])
        direction (away-from home-landing pos)]
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
  [transport-id production-city-pos home-landing lieutenant-id]
  {:fsm transport-fsm
   :fsm-state :moving-to-landing
   :fsm-data {:transport-id transport-id
              :position production-city-pos
              :lieutenant-id lieutenant-id
              :home-landing home-landing   ; {:transport-landing [r c] :beach [[r c] ...]}
              :target {:type :exploring :coords nil}
              :cargo []
              :spawned-lieutenant-id nil
              :scouting-data nil}
   :event-queue []})
```

---

## Resolved Questions

1. **Wait for full load vs. partial?** - Transports wait for full load (6 armies)
2. **Return to same beachhead?** - Yes, transport returns to same spawned Lieutenant's transport-landing
3. **Transport destroyed?** - Armies lost, Lieutenant notified, may produce replacement
4. **Report all land or only landings?** - Report transport-landings found (suitable for unloading)
