# Architecture Round 2: Namespace Collapse & Combat Purity

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Flatten directory structure, reduce namespace count by collapsing thin delegation facades, remove dead code, make combat fully pure, and deprecate the composite MovementAdapter.

**Architecture:** Four themes — (0) flatten single-file subdirectories, (1) collapse thin delegator namespaces into their targets, (2) complete combat's transition to pure functions returning result maps, (3) clean up leftover backward-compat shims.

**Tech Stack:** Clojure 1.12, Speclj (TDD)

---

## Task 0: Flatten single-file subdirectories

11 directories contain exactly one `.cljc` file. A directory holding one file isn't organizing anything. Fix by either merging the child into its parent (if combined < 300 lines) or promoting the child to a sibling file (eliminating the directory).

Also delete 5 empty directories.

### Phase A: Merge into parent (combined < 300 lines)

For each, move the child's content into the parent file and update all callers.

| Child file | Lines | Parent file | Lines | Combined |
|---|---|---|---|---|
| `computer/core/transport_search.cljc` | 41 | `computer/core.cljc` | 233 | 274 |
| `computer/transport_unloading/filtering.cljc` | 45 | `computer/transport_unloading.cljc` | 208 | 253 |
| `combat/escorts.cljc` | 87 | `combat.cljc` | 139 | 226 |
| `debug/dump/output.cljc` | 68 | `debug/dump.cljc` | 227 | 295 |

For each merge:

**Step 1:** Read the child file and its parent file.

**Step 2:** Copy all functions from the child into the parent. Add any requires the child has that the parent lacks.

**Step 3:** Find callers of the child namespace:
```bash
rg 'empire\.computer\.core\.transport-search' src spec
```

**Step 4:** Update each caller: replace the child namespace require with the parent namespace, update all qualified calls (e.g., `transport-search/foo` → inline or use parent alias).

**Step 5:** Delete the child file and its now-empty directory:
```bash
rm src/empire/computer/core/transport_search.cljc
rmdir src/empire/computer/core
```

**Step 6:** Run tests: `clj -M:spec`

Do all 4 merges, testing after each. Commit:
```
Merge single-file subdirs into parents: core, transport_unloading, combat, debug
```

### Phase B: Promote to sibling (too large to merge)

For each, move the child up one level with a flattened name. This changes the namespace.

| Current path | New path | Callers |
|---|---|---|
| `computer/transport_sailing/path.cljc` | `computer/transport_sailing_path.cljc` | 1 |
| `computer/fighter/flight_plan.cljc` | `computer/fighter_flight_plan.cljc` | 1 |
| `computer/transport/mission_handlers.cljc` | `computer/transport_mission_handlers.cljc` | 2 |
| `computer/army/coastal/invasion.cljc` | `computer/army/coastal_invasion.cljc` | 3 |
| `player/commands/actions.cljc` | `player/commands_actions.cljc` | 2 |
| `atoms/runtime.cljc` | `atoms_runtime.cljc` | 1 |

For each promotion:

**Step 1:** Read the file. Note its `(ns ...)` declaration.

**Step 2:** Move the file:
```bash
mv src/empire/computer/transport_sailing/path.cljc src/empire/computer/transport_sailing_path.cljc
rmdir src/empire/computer/transport_sailing 2>/dev/null  # only if empty after move
```

**Step 3:** Update the `(ns ...)` declaration in the moved file:
```clojure
;; Before:
(ns empire.computer.transport-sailing.path ...)
;; After:
(ns empire.computer.transport-sailing-path ...)
```

**Step 4:** Find and update all callers:
```bash
rg 'empire\.computer\.transport-sailing\.path' src spec
```
Replace old namespace with new in every require and any qualified calls.

**Step 5:** If there's a spec file for the child, move and rename it too:
```bash
# e.g., spec/empire/computer/transport_sailing/path_spec.clj → spec/empire/computer/transport_sailing_path_spec.clj
```
Update its `(ns ...)` and any requires.

**Step 6:** Run tests: `clj -M:spec`

Do all 6 promotions, testing after each. Commit:
```
Flatten single-file subdirs: promote to sibling namespaces
```

### Phase C: Delete empty directories

```bash
rmdir src/empire/adapters/movement 2>/dev/null
rmdir src/empire/movement/impl 2>/dev/null
rmdir src/empire/test 2>/dev/null
rmdir src/empire/domain/ai 2>/dev/null
rmdir src/empire/architecture/dependency_checker 2>/dev/null
rmdir src/empire/architecture 2>/dev/null
```

Run tests, commit:
```
Delete empty directories
```

---

## Task 1: Delete `movement/bootstrap.cljc` (no-op stub)

This file is a 6-line no-op retained for "API compatibility." Its two callers (`test_utils.cljc`, `ui/quil/core.cljc`) call `initialize-default-services!` which returns `true` and does nothing.

**Files:**
- Delete: `src/empire/movement/bootstrap.cljc`
- Modify: `src/empire/test_utils.cljc` — remove require and call
- Modify: `src/empire/ui/quil/core.cljc` — remove require and call

**Step 1: Read both callers to find the exact require and call sites**

Read `src/empire/test_utils.cljc` and `src/empire/ui/quil/core.cljc`.

**Step 2: Remove the require and call from each caller**

In `test_utils.cljc`: remove `[empire.movement.bootstrap :as movement-bootstrap]` from require and any `(movement-bootstrap/initialize-default-services!)` call.

In `ui/quil/core.cljc`: same removal.

**Step 3: Delete the file**

```bash
rm src/empire/movement/bootstrap.cljc
```

**Step 4: Run tests**

```bash
clj -M:spec
```

**Step 5: Commit**

```
Delete no-op movement/bootstrap stub
```

---

## Task 2: Inline `movement/waypoint_services.cljc`

This 14-line file delegates 3 functions to `waypoint.cljc`. Its single caller (`player/orders.cljc`) should require `waypoint` directly.

**Files:**
- Delete: `src/empire/movement/waypoint_services.cljc`
- Modify: `src/empire/player/orders.cljc` — replace require, update calls

**Step 1: Read `src/empire/player/orders.cljc`**

Find every `waypoint-services/` call and the require.

**Step 2: Replace require and calls**

- `[empire.movement.waypoint-services :as waypoint-services]` → `[empire.movement.waypoint :as waypoint]`
- `waypoint-services/create-waypoint` → `waypoint/create-waypoint`
- `waypoint-services/set-waypoint-orders` → `waypoint/set-waypoint-orders`
- `waypoint-services/set-waypoint-orders-by-direction` → `waypoint/set-waypoint-orders-by-direction`

**Step 3: Delete the file**

```bash
rm src/empire/movement/waypoint_services.cljc
```

**Step 4: Run tests**

```bash
clj -M:spec
```

**Step 5: Commit**

```
Inline waypoint-services into direct waypoint calls
```

---

## Task 3: Inline `movement/player_services.cljc`

This 27-line file delegates 6 functions to `explore` and `coastline` modules. Its 2 callers should require those modules directly.

**Files:**
- Delete: `src/empire/movement/player_services.cljc`
- Modify: `src/empire/player/commands/actions.cljc`
- Modify: `src/empire/game_loop/item_processing.cljc`

**Step 1: Read `src/empire/movement/player_services.cljc`**

Map each function to its delegation target.

**Step 2: Read both callers**

Find every `player-movement/` call.

**Step 3: Replace requires and calls in each caller**

Replace `[empire.movement.player-services :as player-movement]` with direct requires for the target modules (e.g., `empire.movement.explore`, `empire.movement.coastline`). Update each call to point to the target function.

**Step 4: Delete the file**

```bash
rm src/empire/movement/player_services.cljc
```

**Step 5: Run tests**

```bash
clj -M:spec
```

**Step 6: Commit**

```
Inline player-services into direct explore/coastline calls
```

---

## Task 4: Inline `movement/services.cljc`

This 58-line facade delegates 11 functions to 6 movement modules. It has 10 callers — the largest collapse. Work carefully.

**Files:**
- Delete: `src/empire/movement/services.cljc`
- Modify: 10 caller files (listed in research above)

**Step 1: Read `src/empire/movement/services.cljc`**

Map every function to its delegation target module.

**Step 2: Read each of the 10 callers**

For each, list which `movement-services/` functions they call.

**Step 3: Replace requires and calls in each caller**

For each caller:
- Remove `[empire.movement.services :as movement-services]`
- Add direct requires for the target modules used by that caller
- Replace each `movement-services/xxx` call with the target module's function

Do this **one caller at a time**, running tests after each.

**Step 4: Delete the file**

```bash
rm src/empire/movement/services.cljc
```

**Step 5: Run tests**

```bash
clj -M:spec
```

**Step 6: Commit**

```
Inline movement/services facade into direct module calls
```

---

## Task 5: Inline `application/coords.cljc`

This 11-line file has a single function `screen->cell` with a single caller (`ui/util/core.cljc`). Move the function into the caller.

**Files:**
- Delete: `src/empire/application/coords.cljc`
- Modify: `src/empire/ui/util/core.cljc`

**Step 1: Read both files**

**Step 2: Move `screen->cell` into `ui/util/core.cljc`**

Add the function directly. Remove the require for `application.coords`.

**Step 3: Delete the file**

```bash
rm src/empire/application/coords.cljc
```

**Step 4: Run tests**

```bash
clj -M:spec
```

**Step 5: Commit**

```
Inline coords/screen->cell into ui/util/core
```

---

## Task 6: Deprecate composite MovementAdapter

The old `MovementAdapter` defrecord (3 protocols) was retained for backward compat. Now that the 3 single-protocol adapters exist, migrate consumers off the composite and delete it.

**Files:**
- Modify: `src/empire/movement/adapter.cljc` — remove `MovementAdapter`, remove `movement-port` fn
- Modify: `src/empire/application/bootstrap.cljc` — remove `:movement-port` from context
- Modify: any files that use `:movement-port` from the context

**Step 1: Find all consumers of `:movement-port`**

```bash
rg ':movement-port' src/empire
```

**Step 2: Migrate each consumer to the specific port it needs**

Each consumer should use `:unit-state-port`, `:execution-port`, or `:pathfinding-port` instead.

**Step 3: Remove `:movement-port` from bootstrap context and `movement-port` fn from adapter**

**Step 4: Remove `MovementAdapter` defrecord from adapter.cljc**

**Step 5: Run tests**

```bash
clj -M:spec
```

**Step 6: Commit**

```
Remove composite MovementAdapter; consumers use single-protocol ports
```

---

## Task 7: Make combat functions return result maps

Currently combat functions accept `world` but still write side effects via `sa/update-world!` and `sa/write-state!`. Refactor them to return `{:world updated-world :messages {...} :state-updates {...}}` and let callers apply the effects.

**Files:**
- Modify: `src/empire/combat.cljc`
- Modify: callers of combat functions (6+ files)
- Test: `spec/empire/combat_spec.clj`

**Step 1: Define the result map shape**

```clojure
{:world    updated-world          ;; the new world state
 :messages {:turn-message "..."   ;; optional
            :error-message "..."  ;; optional
            :error-until ts}      ;; optional
 :state-updates {:key val ...}}   ;; runtime state changes
```

**Step 2: Create `apply-combat-result!` helper**

A thin function in combat.cljc (or a new `combat/effects.cljc`) that unpacks the result and applies side effects:

```clojure
(defn apply-combat-result! [result]
  (when-let [w (:world result)]
    (sa/update-world! (constantly w)))
  (doseq [[k v] (:messages result)]
    (sa/write-state! k v))
  (doseq [[k v] (:state-updates result)]
    (sa/write-state! k v)))
```

**Step 3: Refactor one function at a time**

Start with `attempt-attack` (simplest — one caller). Change it to return a result map. Update its caller to call `apply-combat-result!`. Run tests.

Then `attempt-conquest`, `attempt-city-conquest`, `attempt-fighter-overfly` — each one function at a time, testing after each.

**Step 4: Remove sa/ calls from combat core functions**

Once all functions return result maps, the only `sa/` calls remaining in combat.cljc should be in `apply-combat-result!`. Consider moving that helper to the caller layer if desired.

**Step 5: Run all tests**

```bash
clj -M:spec
scripts/check-architecture-boundaries.sh
```

**Step 6: Commit**

```
Combat functions return pure result maps; callers apply effects
```

---

## Task 8: Add boundary guards for removed namespaces

Add architecture guards preventing reintroduction of deleted facades.

**Files:**
- Modify: `scripts/check-architecture-boundaries.sh`

**Step 1: Add guards**

```bash
# Removed facade namespaces — use target modules directly
facade_hits="$(rg -n 'empire\.movement\.(services|player.services|waypoint.services|bootstrap)\b' src/empire || true)"
if [[ -n "$facade_hits" ]]; then
  echo "Architecture boundary violation: movement facade namespaces were removed; require target modules directly:"
  printf '%s\n' "$facade_hits"
  exit 1
fi
```

**Step 2: Run the guard**

```bash
scripts/check-architecture-boundaries.sh
```

**Step 3: Commit**

```
Add guards for removed facade namespaces
```

---

## Execution Order

```
Task 0 (flatten single-file subdirs)    — do first; biggest structural change
Task 1 (delete bootstrap stub)          — independent
Task 2 (inline waypoint-services)       — independent
Task 3 (inline player-services)         — independent
Task 4 (inline services.cljc)           — independent but largest; do after 1-3 for momentum
Task 5 (inline coords)                  — independent
Task 6 (deprecate composite adapter)    — independent
Task 7 (combat pure results)            — independent but most complex
Task 8 (boundary guards)                — depends on Tasks 0-5
```

Task 0 is the structural foundation — do it first so later tasks work with the flattened layout.
Tasks 1-5 are simple namespace collapses and can be done rapidly (parallel-safe).
Task 6 is medium complexity.
Task 7 is the deepest refactor.
Task 8 is cleanup.

**Expected net reduction:** ~17 namespaces deleted/flattened, ~11 directories removed, ~170 lines of delegation code removed.
