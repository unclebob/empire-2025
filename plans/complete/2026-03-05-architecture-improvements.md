# Architecture Improvements Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Simplify the Empire architecture by unifying state contexts, replacing fake multimethods with data, cleaning bootstrap, and narrowing coupling.

**Architecture:** Eight refactoring slices ordered by dependency — context unification first (enables later slices), then dispatcher simplification, bootstrap cleanup, combat decoupling, atom consolidation, adapter split, domain extraction, and guard migration.

**Tech Stack:** Clojure 1.12, Speclj (TDD), Quil 4.3

---

## Task 1: Unify Triple Context → Single `state-access`

Three separate context singletons (`application/state-access`, `movement/context`, `movement/pathfinding-bfs/context`) provide the same state access with different initialization patterns. Eliminate the two movement contexts.

### Files:
- Modify: `src/empire/application/state_access.cljc`
- Modify: `src/empire/movement/visibility.cljc`
- Modify: `src/empire/movement/satellite.cljc`
- Modify: `src/empire/movement/map_utils.cljc`
- Modify: `src/empire/movement/waypoint.cljc`
- Modify: `src/empire/movement/pathfinding_bfs/coast_targeting.cljc`
- Modify: `src/empire/movement/pathfinding_bfs/transport.cljc`
- Modify: `src/empire/movement/pathfinding_bfs/exploration.cljc`
- Modify: `src/empire/application/bootstrap.cljc`
- Delete: `src/empire/movement/context.cljc`
- Delete: `src/empire/movement/pathfinding_bfs/context.cljc`
- Test: existing specs should still pass

### Step 1: Add missing functions to `state-access`

`state-access` is missing `merge-continents!`, `state-ctx` access for `:handle-detection!`, and `world-store`/`world-atom`. Add them:

```clojure
;; Add to state-access.cljc:
(defn merge-continents! [stamp-id existing-cid]
  ((:merge-continents! @ctx) stamp-id existing-cid))

(defn context-fn [k]
  (get @ctx k))

(defn world-store [] (:world-store @ctx))
```

### Step 2: Run tests to confirm additions don't break anything

Run: `clj -M:spec`
Expected: all green

### Step 3: Migrate `movement/context` consumers

In each of these 4 files, replace:
- `[empire.movement.context :as movement-context]` → `[empire.application.state-access :as sa]`
- `movement-context/current-world` → `sa/current-world`
- `movement-context/update-world!` → `sa/update-world!`
- `movement-context/read-runtime-state` → `sa/read-state`
- `movement-context/write-runtime-state!` → `sa/write-state!`
- `movement-context/merge-continents!` → `sa/merge-continents!`
- `(movement-context/state-ctx)` → `(sa/context-fn :handle-detection!)` (in visibility.cljc only — extract the specific function, not the whole context)

Files: `visibility.cljc`, `satellite.cljc`, `map_utils.cljc`, `waypoint.cljc`

### Step 4: Migrate `pathfinding-bfs/context` consumers

In each of these 3 files, replace:
- `[empire.movement.pathfinding-bfs.context :as bfs-context]` → `[empire.application.state-access :as sa]`
- `bfs-context/current-world` → `sa/current-world`
- `bfs-context/read-runtime-state` → `sa/read-state`

Files: `coast_targeting.cljc`, `transport.cljc`, `exploration.cljc`

### Step 5: Clean up bootstrap initialization

In `bootstrap.cljc`, remove:
- `(movement-context/set-state-ctx! ctx)` (line 59)
- `(bfs-context/set-world-loader! (:load-world ctx))` (line 61)
- `(bfs-context/set-runtime-reader! (:read-runtime-state ctx))` (line 62)
- Remove requires for `movement.context` and `pathfinding-bfs.context`
- Keep `(pathfinding/set-world-loader! (:load-world ctx))` if pathfinding.cljc still needs it (check separately)

### Step 6: Delete context modules

```bash
rm src/empire/movement/context.cljc
rm src/empire/movement/pathfinding_bfs/context.cljc
```

### Step 7: Run all tests

Run: `clj -M:spec`
Run: `clj -M:parse-tests && clj -M:generate-specs && clj -M:spec generated-acceptance-specs/`
Run: `scripts/check-architecture-boundaries.sh`
Expected: all green

### Step 8: Commit

```
Unify triple state context into single state-access
```

---

## Task 2: Replace Fake Multimethods with Data Map

All 14 unit dispatcher multimethods use `(fn [& _] :default)` — they never actually dispatch on type. Replace with a unified data map and plain functions.

### Files:
- Rewrite: `src/empire/units/dispatcher.cljc`
- Delete: `src/empire/units/impl/dispatcher.cljc`
- Modify: `src/empire/application/bootstrap.cljc` (remove `units.impl.dispatcher` require)
- Modify: `scripts/check-architecture-boundaries.sh` (remove `units.impl` guard — no longer needed)
- Test: `spec/empire/units/dispatcher_spec.clj`

### Step 1: Write new dispatcher with data map

Replace `src/empire/units/dispatcher.cljc` entirely. Consolidate all config from `units/config.cljc`, `units/ships.cljc`, and individual unit modules:

```clojure
(ns empire.units.dispatcher
  (:require [empire.domain.core.unit-metrics :as unit-metrics]
            [empire.units.army :as army]
            [empire.units.carrier :as carrier]
            [empire.units.fighter :as fighter]
            [empire.units.satellite :as satellite]
            [empire.units.transport :as transport]
            [empire.units.ships :as ships]
            [empire.units.config :as units-config]))

(def ^:private all-config
  {:army        {:speed units-config/army-speed :cost units-config/army-cost
                 :hits units-config/army-hits :display-char units-config/army-display-char
                 :visibility-radius units-config/army-visibility-radius
                 :strength units-config/army-strength :capacity nil
                 :initial-state-fn army/initial-state
                 :can-move-to-fn army/can-move-to?
                 :needs-attention-fn army/needs-attention?}
   :fighter     {:speed units-config/fighter-speed :cost units-config/fighter-cost
                 :hits units-config/fighter-hits :display-char units-config/fighter-display-char
                 :visibility-radius units-config/fighter-visibility-radius
                 :strength units-config/fighter-strength :capacity nil
                 :initial-state-fn fighter/initial-state
                 :can-move-to-fn fighter/can-move-to?
                 :needs-attention-fn fighter/needs-attention?}
   :satellite   {:speed units-config/satellite-speed :cost units-config/satellite-cost
                 :hits units-config/satellite-hits :display-char units-config/satellite-display-char
                 :visibility-radius units-config/satellite-visibility-radius
                 :strength units-config/satellite-strength :capacity nil
                 :initial-state-fn satellite/initial-state
                 :can-move-to-fn satellite/can-move-to?
                 :needs-attention-fn satellite/needs-attention?}
   :transport   {:speed units-config/transport-speed :cost units-config/transport-cost
                 :hits units-config/transport-hits :display-char units-config/transport-display-char
                 :visibility-radius units-config/transport-visibility-radius
                 :strength units-config/transport-strength
                 :capacity units-config/transport-capacity
                 :initial-state-fn transport/initial-state
                 :can-move-to-fn transport/can-move-to?
                 :needs-attention-fn transport/needs-attention?}
   :carrier     {:speed units-config/carrier-speed :cost units-config/carrier-cost
                 :hits units-config/carrier-hits :display-char units-config/carrier-display-char
                 :visibility-radius units-config/carrier-visibility-radius
                 :strength units-config/carrier-strength
                 :capacity units-config/carrier-capacity
                 :initial-state-fn carrier/initial-state
                 :can-move-to-fn carrier/can-move-to?
                 :needs-attention-fn carrier/needs-attention?}
   :patrol-boat {:speed 4 :cost 15 :hits 1 :display-char "P"
                 :visibility-radius 1 :strength 1 :capacity nil
                 :initial-state-fn ships/initial-state
                 :can-move-to-fn ships/can-move-to?
                 :needs-attention-fn ships/needs-attention?}
   :destroyer   {:speed 2 :cost 20 :hits 3 :display-char "D"
                 :visibility-radius 1 :strength 1 :capacity nil
                 :initial-state-fn ships/initial-state
                 :can-move-to-fn ships/can-move-to?
                 :needs-attention-fn ships/needs-attention?}
   :submarine   {:speed 2 :cost 20 :hits 2 :display-char "S"
                 :visibility-radius 1 :strength 3 :capacity nil
                 :initial-state-fn ships/initial-state
                 :can-move-to-fn ships/can-move-to?
                 :needs-attention-fn ships/needs-attention?}
   :battleship  {:speed 2 :cost 40 :hits 10 :display-char "B"
                 :visibility-radius 1 :strength 2 :capacity nil
                 :initial-state-fn ships/initial-state
                 :can-move-to-fn ships/can-move-to?
                 :needs-attention-fn ships/needs-attention?}})

(defn speed [unit-type] (get-in all-config [unit-type :speed]))
(defn cost [unit-type] (get-in all-config [unit-type :cost]))
(defn hits [unit-type] (get-in all-config [unit-type :hits]))
(defn display-char [unit-type] (get-in all-config [unit-type :display-char]))
(defn visibility-radius [unit-type] (get-in all-config [unit-type :visibility-radius]))
(defn strength [unit-type] (get-in all-config [unit-type :strength]))
(defn capacity [unit-type] (get-in all-config [unit-type :capacity]))

(defn initial-state [unit-type]
  (if-let [f (get-in all-config [unit-type :initial-state-fn])]
    (f)
    {}))

(defn can-move-to? [unit-type cell]
  (if-let [f (get-in all-config [unit-type :can-move-to-fn])]
    (f cell)
    false))

(defn needs-attention? [unit]
  (if-let [f (get-in all-config [(:type unit) :needs-attention-fn])]
    (f unit)
    false))

(defn effective-speed [unit-type current-hits]
  (unit-metrics/effective-speed (speed unit-type) current-hits (hits unit-type)))

(defn effective-capacity [unit-type current-hits]
  (let [max-h (hits unit-type)
        cur-h (or current-hits max-h)]
    (unit-metrics/effective-capacity (capacity unit-type) cur-h max-h)))

(defn naval-unit? [unit-type]
  (unit-metrics/naval-unit? unit-type))

(defn naval-units [unit-type]
  (unit-metrics/naval-unit? unit-type))
```

### Step 2: Update bootstrap — remove `units.impl.dispatcher` require

In `bootstrap.cljc`, delete the line `[empire.units.impl.dispatcher]` from `:require`.

### Step 3: Delete old implementation

```bash
rm src/empire/units/impl/dispatcher.cljc
```

If `src/empire/units/impl/` directory is now empty, delete it too.

### Step 4: Update architecture boundary guard

In `scripts/check-architecture-boundaries.sh`, remove the `units_impl` guard (lines 52-58) since `units.impl` no longer exists.

### Step 5: Update dispatcher spec

Update `spec/empire/units/dispatcher_spec.clj` — tests should call the same public API. The spec should still work since function names/arities are preserved. Run and fix any issues.

### Step 6: Run all tests

Run: `clj -M:spec`
Run: `scripts/check-architecture-boundaries.sh`
Expected: all green

### Step 7: Commit

```
Replace dispatcher multimethods with data map lookup
```

---

## Task 3: Clean Up Bootstrap Lambdas

Three lambdas in `bootstrap.cljc` contain domain logic instead of pure wiring.

### Files:
- Modify: `src/empire/application/bootstrap.cljc`

### Step 1: Simplify wrapper lambdas

Lines 35-40 are wrappers adding no value. Replace:

```clojure
;; Before:
:country-coastal-explored? (fn [country-id]
                              (computer-production/country-coastal-cells-explored? country-id))
:country-city-producing-armies? (fn [city-pos country-id]
                                   (computer-production/country-city-producing-armies?
                                     city-pos country-id))
;; After:
:country-coastal-explored? computer-production/country-coastal-cells-explored?
:country-city-producing-armies? computer-production/country-city-producing-armies?
```

### Step 2: Extract production logic from bootstrap

Lines 41-47 contain real business logic. Extract to a function in the appropriate module — likely `empire.player.production` or `empire.application.city-production` (whichever already has production logic). The lambda becomes a simple function reference.

### Step 3: Run all tests

Run: `clj -M:spec`
Expected: all green

### Step 4: Commit

```
Extract domain logic from bootstrap composition root
```

---

## Task 4: Decouple Combat from `state-access`

`combat.cljc` is classified as a domain-service but directly mutates global state via `state-access`. Refactor combat functions to be pure: accept world as input, return updated world + side effects.

### Files:
- Modify: `src/empire/combat.cljc`
- Modify: callers of combat functions (6+ files)
- Test: `spec/empire/combat_spec.clj`

### Step 1: Refactor `hostile-city?` to accept world

```clojure
;; Before:
(defn hostile-city? [pos owner]
  (let [cell (get-in (sa/current-world) pos)]
    ...))

;; After:
(defn hostile-city? [world pos owner]
  (let [cell (get-in world pos)]
    ...))
```

Update all 5 callers to pass `(sa/current-world)` as first argument.

### Step 2: Refactor remaining combat functions

For each combat function (`attempt-city-conquest`, `attempt-conquest`, `attempt-fighter-overfly`, `attempt-attack`, `conquer-city-contents`, `drown-excess-cargo`):

1. Accept `world` as first argument instead of calling `sa/current-world`
2. Return `{:world updated-world :messages [...] :state-updates {...}}` instead of calling `sa/update-world!`, `sa/write-state!`
3. Create a thin orchestrator function that callers use, which unpacks the result and applies side effects

This is a larger refactor — do it function by function, running tests after each one.

### Step 3: Remove `state-access` require from combat

Once all functions are pure, combat.cljc no longer needs `empire.application.state-access`.

### Step 4: Run all tests

Run: `clj -M:spec`
Run: `scripts/check-architecture-boundaries.sh`
Expected: all green

### Step 5: Commit

```
Decouple combat from global state — pure functions with explicit world arg
```

---

## Task 5: Consolidate Atoms into Aggregates

Reduce 68 atoms to ~6 aggregate atoms, each holding a map.

### Files:
- Modify: `src/empire/atoms.cljc`
- Modify: `src/empire/adapters/state/runtime.cljc`
- Modify: `src/empire/adapters/state/atoms.cljc`
- Modify: `src/empire/test_utils.cljc`
- Modify: `src/empire/save_load.cljc`
- Test: all specs

### Step 1: Define aggregate groups

```clojure
;; World aggregate (persisted)
(def world-state (atom {:game-map nil :player-map nil :computer-map nil
                        :continent-groups nil}))

;; Session aggregate (persisted)
(def session-state (atom {:round-number 0 :destination nil :paused false
                          :production {} :cells-needing-attention nil
                          :player-items nil :computer-items nil
                          :waiting-for-input false :computer-turn false
                          ;; ... all session atoms
                          }))

;; UI aggregate (transient)
(def ui-state (atom {:backtick-pressed false :last-clicked-cell nil
                     :map-to-display :player-map :attention-message nil
                     ;; ... all UI atoms
                     }))

;; Computer strategy aggregate (persisted)
(def computer-strategy (atom {:claimed-objectives #{}
                               :computer-city-positions #{}
                               :fighter-leg-records {}
                               ;; ... all strategy atoms
                               }))

;; ID generators aggregate (persisted)
(def id-generators (atom {:next-transport-id 0 :next-country-id 0
                           ;; ... all ID atoms
                           }))

;; Debug aggregate (transient)
(def debug-state (atom {:debug-message nil :action-log []
                         ;; ... all debug atoms
                         }))
```

### Step 2: Update runtime adapter

Change `runtime-key->atom` in `adapters/state/runtime.cljc` to use cursor-style access:
- Each key maps to `[aggregate-atom path-within-aggregate]`
- `read-runtime-state` does `(get-in @agg path)`
- `write-runtime-state!` does `(swap! agg assoc-in path v)`

### Step 3: Update save/load

`save_load.cljc` currently dereferences ~29 atoms. Update to serialize/deserialize the aggregate atoms.

### Step 4: Update test_utils reset

`reset-all-atoms!` becomes much simpler — reset 6 atoms instead of 68.

### Step 5: Run all tests

Run: `clj -M:spec`
Run: `clj -M:parse-tests && clj -M:generate-specs && clj -M:spec generated-acceptance-specs/`
Expected: all green

### Step 6: Commit

```
Consolidate 68 atoms into 6 aggregate state atoms
```

---

## Task 6: Split MovementAdapter into 3 Records

`MovementAdapter` implements 3 protocols in one `defrecord`, making it impossible to substitute one without the others.

### Files:
- Modify: `src/empire/movement/adapter.cljc`
- Modify: `src/empire/application/bootstrap.cljc`
- Test: any specs that use movement-port

### Step 1: Split into 3 records

```clojure
(defrecord UnitStateAdapter []
  unit-state-ports/UnitStatePort
  ...)

(defrecord MovementExecutionAdapter []
  exec-ports/MovementExecutionPort
  ...)

(defrecord PathfindingAdapter []
  path-ports/PathfindingPort
  ...)

(defn unit-state-port [] (->UnitStateAdapter))
(defn execution-port [] (->MovementExecutionAdapter))
(defn pathfinding-port [] (->PathfindingAdapter))
```

### Step 2: Update bootstrap wiring

In `bootstrap.cljc`, replace single `movement-port` with 3 separate ports in the context map.

### Step 3: Update consumers

Find all callers that use `:movement-port` from the context and update to use the specific port they need.

### Step 4: Run all tests

Run: `clj -M:spec`
Expected: all green

### Step 5: Commit

```
Split MovementAdapter into 3 single-protocol records
```

---

## Task 7: Extract Pure Domain Core

Identify pure functions in combat, containers, and movement and move them to `domain/` if they have no state dependencies.

### Files:
- Create or extend files in `src/empire/domain/`
- Modify: source files that currently host the pure functions
- Test: existing specs should still pass (just re-pointing requires)

### Step 1: Audit pure functions

After Task 4 (combat decoupling), functions like `resolve-combat`, `hostile-city?`, `can-conquer?` will be pure. Similarly check `containers/helpers.cljc` and `containers/ops.cljc`.

### Step 2: Move identified pure functions

Move to `domain/core/` or `domain/model/` as appropriate. Update requires in consumers.

### Step 3: Run all tests

Run: `clj -M:spec`
Run: `scripts/check-architecture-boundaries.sh`
Expected: all green

### Step 4: Commit

```
Extract pure domain functions from domain-services into domain core
```

---

## Task 8: Migrate Architecture Guards to Declarative Rules

Replace brittle regex guards in `scripts/check-architecture-boundaries.sh` with declarative rules in the dependency checker tool.

### Files:
- Modify: `scripts/check-architecture-boundaries.sh`
- Possibly modify: dependency checker config (deps.edn aliases or config file)

### Step 1: Identify which guards can become distance rules

The dependency checker already runs (`clj -M:check-dependencies --max-distance 1`). Determine which regex guards duplicate what distance checking already catches.

### Step 2: Add declarative forbidden-edge rules

If the dependency checker supports it, add rules like:
- `domain-services -> use-cases: forbidden`
- `application -> use-cases: forbidden (except bootstrap)`
- `application -> domain-services: forbidden (except bootstrap)`

### Step 3: Remove redundant regex guards

Keep only guards that can't be expressed declaratively (e.g., "no `swap!` in application/domain").

### Step 4: Run all tests

Run: `scripts/check-architecture-boundaries.sh`
Expected: all green

### Step 5: Commit

```
Migrate architecture guards to declarative dependency rules
```

---

## Execution Order and Dependencies

```
Task 1 (context unification)
   ↓
Task 2 (dispatcher data map)  ←  independent of Task 1
   ↓
Task 3 (bootstrap cleanup)    ←  depends on Task 2 (bootstrap changes)
   ↓
Task 4 (combat decoupling)    ←  depends on Task 1 (state-access is final API)
   ↓
Task 5 (atom consolidation)   ←  depends on Tasks 1, 4 (fewer state-access consumers)
   ↓
Task 6 (adapter split)        ←  depends on Task 1 (context is unified)
   ↓
Task 7 (domain extraction)    ←  depends on Task 4 (combat is pure)
   ↓
Task 8 (guard migration)      ←  depends on Tasks 2, 7 (guards stabilized)
```

Tasks 1 and 2 can run in parallel. Tasks 3 should follow 2. The rest are sequential.
