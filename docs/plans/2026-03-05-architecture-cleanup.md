# Architecture Cleanup Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Eliminate ~150 fake-polymorphic defmultis, add boundary guards, separate Quil/UI components, and split MovementPort.

**Architecture:** Mechanical defmulti→defn conversions within each ring, plus new component boundaries for Quil/UI. No cross-ring dependency changes.

**Tech Stack:** Clojure, Speclj (`clj -M:spec`), architecture checker (`scripts/check-architecture-boundaries.sh`), dependency checker (`clj -M:check-dependencies`)

---

## Conventions Used Throughout This Plan

### Same-File Collapse Pattern
When `defmulti` and `defmethod :default` are in the **same file**, replace both with a plain `defn`:

Before:
```clojure
(defmulti foo (fn [& _] :default))
(defmethod foo :default [x] (+ x 1))
```

After:
```clojure
(defn foo [x] (+ x 1))
```

### Contract/Impl Merge Pattern
When `defmulti` is in a **contract file** and `defmethod :default` is in a **separate impl file**:

1. Copy all `:require` clauses from impl ns into contract ns (deduplicate)
2. Copy all `defn-` private helpers from impl into contract
3. Replace each `defmulti` with the body of its corresponding `defmethod :default` as a `defn`
4. Delete the impl file
5. Remove the impl require from `application/bootstrap.cljc`

### Verification Commands
After every task:
```bash
clj -M:spec                              # all tests pass
clj -M:check-dependencies                # dependency rules pass
scripts/check-architecture-boundaries.sh  # boundary guards pass
```

---

## Task 1: R3 — Add units/impl Boundary Guard

**Files:**
- Modify: `scripts/check-architecture-boundaries.sh`

**Step 1: Check for existing violations**

Run: `rg -n 'empire\.units\.impl' src/empire --glob '!src/empire/units/impl/*' --glob '!src/empire/application/bootstrap.cljc'`

Expected: No output (no violations). If there are hits, fix them first.

**Step 2: Add guard to check-architecture-boundaries.sh**

Insert before the `echo "Architecture boundary check passed"` line:

```bash
units_impl_hits="$(rg -n 'empire\.units\.impl' src/empire || true)"
units_impl_violations="$(printf '%s\n' "$units_impl_hits" | rg -v '^src/empire/units/impl/|^src/empire/application/bootstrap\.cljc:' || true)"
if [[ -n "$units_impl_violations" ]]; then
  echo "Architecture boundary violation: units.impl must only be referenced from itself and application/bootstrap:"
  printf '%s\n' "$units_impl_violations"
  exit 1
fi
```

**Step 3: Verify**

Run: `scripts/check-architecture-boundaries.sh`
Expected: "Architecture boundary check passed"

**Step 4: Commit**

```bash
git add scripts/check-architecture-boundaries.sh
git commit -m "Add boundary guard for units/impl namespace"
```

---

## Task 2: R7 — Separate Quil and UI Components

**Files:**
- Modify: `src/empire/ui/util/input/dispatch.cljc` (add key-down function)
- Modify: `src/empire/ui/quil/input.cljc` (delegate to dispatch/key-down)
- Modify: `src/empire/acceptance/harness.cljc` (remove quil dependency)
- Modify: `dependency-checker.edn` (add :quil and :ui components)
- Modify: `scripts/check-architecture-boundaries.sh` (add quil boundary guard)

**Step 1: Add key-down to dispatch.cljc**

In `src/empire/ui/util/input/dispatch.cljc`, add this public function (it already has `dispatch-key`):

```clojure
(defn key-down
  "Process a key press with explicit mouse coordinates.
   Entry point for both Quil (live) and harness (test) key input."
  [k mouse-x mouse-y]
  (dispatch-key k mouse-x mouse-y))
```

**Step 2: Update quil/input.cljc to delegate**

Replace the body of `key-down` in `src/empire/ui/quil/input.cljc`:

Before:
```clojure
(defn key-down [k]
  (dispatch/dispatch-key k (q/mouse-x) (q/mouse-y)))
```

After:
```clojure
(defn key-down [k]
  (dispatch/key-down k (q/mouse-x) (q/mouse-y)))
```

**Step 3: Update acceptance/harness.cljc**

Remove these requires from the ns declaration:
```clojure
[empire.ui.quil.input :as quil-input]
[quil.core :as q]
```

Replace `key-down!`:
```clojure
(defn key-down!
  [k]
  (set-last-key! nil)
  (input-dispatch/key-down k 0 0))
```

Replace `key-down-at!`:
```clojure
(defn key-down-at!
  [k mouse-x mouse-y]
  (set-last-key! nil)
  (input-dispatch/key-down k mouse-x mouse-y))
```

**Step 4: Run tests**

Run: `clj -M:spec`
Expected: All tests pass.

Also run acceptance pipeline:
```bash
clj -M:parse-tests && clj -M:generate-specs && clj -M:spec generated-acceptance-specs/
```

**Step 5: Add components to dependency-checker.edn**

Add two new component rules BEFORE the `:outer-ring` catch-all:

```edn
{:component :quil
 :kind :concrete
 :match "^empire\\.ui\\.quil(\\..*)?$"}

{:component :ui
 :kind :concrete
 :match "^empire\\.ui\\.util(\\..*)?$"}
```

Add to `:forbidden-dependencies`:
```edn
[:ui :quil]
```

**Step 6: Run dependency checker**

Run: `clj -M:check-dependencies`
Expected: Pass (UI does not depend on Quil).

**Step 7: Add quil boundary guard**

Add to `scripts/check-architecture-boundaries.sh` before the success echo:

```bash
quil_outside_hits="$(rg -n 'empire\.ui\.quil' src/empire --glob '!src/empire/ui/quil/*' || true)"
if [[ -n "$quil_outside_hits" ]]; then
  echo "Architecture boundary violation: empire.ui.quil must only be referenced from within ui/quil/:"
  printf '%s\n' "$quil_outside_hits"
  exit 1
fi
```

**Step 8: Verify all guards**

Run: `scripts/check-architecture-boundaries.sh`
Expected: "Architecture boundary check passed"

**Step 9: Commit**

```bash
git add -A
git commit -m "Separate Quil and UI into distinct components with boundary guard"
```

---

## Task 3: R1 — Collapse Domain Inner-Ring Defmultis

**Overview:** 7 contract/impl pairs to merge using the Contract/Impl Merge Pattern. Each merge: copy requires + helpers from impl into contract, convert defmulti→defn, delete impl.

**Files to merge (contract ← impl):**

| # | Contract | Impl | Defmultis | Impl Helpers |
|---|----------|------|-----------|--------------|
| 1 | `domain/core/unit_metrics.cljc` | `domain/core/impl/unit_metrics.cljc` | 4 | none |
| 2 | `domain/core/continents.cljc` | `domain/core/impl/continents.cljc` | 2 | none |
| 3 | `domain/core/refueling.cljc` | `domain/core/impl/refueling.cljc` | 3 | none |
| 4 | `domain/core/messages.cljc` | `domain/core/impl/messages.cljc` | 1 | none |
| 5 | `domain/services/threat_policy.cljc` | `domain/services/impl/threat_policy.cljc` | 9 | player-unit-type, player-city? |
| 6 | `domain/services/round_setup.cljc` | `domain/services/impl/round_setup.cljc` | 4 | none |
| 7 | `domain/model/containers.cljc` | `domain/model/impl/containers.cljc` | 10 | wake-all, sleep-all, remove-awake-unit, has-awake? |

**Step 1: Read each contract and its impl**

Read all 14 files before editing. Note the requires in each impl — most have no additional requires beyond the contract itself. Exceptions:
- `impl/round_setup.cljc` adds `empire.config`
- `impl/containers.cljc` adds `empire.config`

**Step 2: Merge each pair**

For each pair, apply the Contract/Impl Merge Pattern:
1. Add any new requires from impl to contract ns
2. Move private helpers from impl into contract (as `defn-`)
3. Replace each `defmulti`+`defmethod` with plain `defn`
4. Delete the impl file

Note: `threat_policy.cljc` already has `player-unit-type` and `player-city?` helper defns in both contract AND impl. After merge, keep one copy.

Note: `containers.cljc` already has `wake-all`, `sleep-all`, `remove-awake-unit`, `has-awake?` helpers in both contract AND impl. After merge, keep one copy.

**Step 3: Delete impl directories if empty**

```bash
rm src/empire/domain/core/impl/unit_metrics.cljc
rm src/empire/domain/core/impl/continents.cljc
rm src/empire/domain/core/impl/refueling.cljc
rm src/empire/domain/core/impl/messages.cljc
rm src/empire/domain/services/impl/threat_policy.cljc
rm src/empire/domain/services/impl/round_setup.cljc
rm src/empire/domain/model/impl/containers.cljc
```

Check if any impl directories are now empty and remove them:
```bash
rmdir src/empire/domain/core/impl/ 2>/dev/null || true
rmdir src/empire/domain/services/impl/ 2>/dev/null || true
```

Note: `domain/model/impl/` still has `combat.cljc` and `combat_runtime.cljc` — do NOT delete this directory.

**Step 4: Update bootstrap.cljc**

Remove these requires from `src/empire/application/bootstrap.cljc`:
- `empire.domain.core.impl.unit-metrics` (if present)
- `empire.domain.core.impl.continents` (if present)
- `empire.domain.core.impl.refueling` (if present)
- `empire.domain.core.impl.messages` (if present)
- `empire.domain.services.impl.threat-policy` (if present)
- `empire.domain.services.impl.round-setup` (if present)
- `empire.domain.model.impl.containers` (if present)

Note: Some of these may be wired differently (loaded by other impl files rather than bootstrap). Check each one — only remove requires that actually exist in bootstrap.

**Step 5: Verify**

```bash
clj -M:spec
clj -M:check-dependencies
scripts/check-architecture-boundaries.sh
```

**Step 6: Commit**

```bash
git add -A
git commit -m "Collapse domain inner-ring fake defmultis to plain functions"
```

---

## Task 4: R4 — Collapse Application Defmultis

**Overview:** 6 contract/impl pairs in `application/`.

**Files to merge:**

| # | Contract | Impl | Defmultis | Impl Helpers |
|---|----------|------|-----------|--------------|
| 1 | `application/runtime.cljc` | `application/impl/runtime.cljc` | 1 | none |
| 2 | `application/state.cljc` | `application/impl/state.cljc` | 5 | require-fn |
| 3 | `application/coords.cljc` | `application/impl/coords.cljc` | 1 | none |
| 4 | `application/production_status.cljc` | `application/impl/production_status.cljc` | 1 | none |
| 5 | `application/city_production.cljc` | `application/impl/city_production.cljc` | 1 | item-cost |
| 6 | `application/unit_stamping.cljc` | `application/impl/unit_stamping.cljc` | 3 | next-id!, apply-computer-satellite-direction, apply-computer-transport-fields, apply-country-id, apply-patrol-fields, apply-carrier-fields, apply-escort-fields, apply-destroyer-fields, country-coastal-cells-explored? |

**IMPORTANT for runtime.cljc:** The impl requires adapter namespaces (`adapters.state.atoms`, `adapters.state.runtime`, `movement.adapter`, `computer.threat-response`, `computer.production`). After merge, `application/runtime.cljc` will require these. This is fine — `application/runtime` is `:outer-ring`.

**Step 1: Read all 12 files**

**Step 2: Merge each pair using Contract/Impl Merge Pattern**

**Step 3: Delete impl files**

```bash
rm src/empire/application/impl/runtime.cljc
rm src/empire/application/impl/state.cljc
rm src/empire/application/impl/coords.cljc
rm src/empire/application/impl/production_status.cljc
rm src/empire/application/impl/city_production.cljc
rm src/empire/application/impl/unit_stamping.cljc
rmdir src/empire/application/impl/ 2>/dev/null || true
```

**Step 4: Update bootstrap.cljc**

Remove these requires:
```clojure
[empire.application.impl.runtime]
[empire.application.impl.coords]
[empire.application.impl.production-status]
[empire.application.impl.city-production]
[empire.application.impl.state]
[empire.application.impl.unit-stamping]
```

**Step 5: Verify**

```bash
clj -M:spec
clj -M:check-dependencies
scripts/check-architecture-boundaries.sh
```

**Step 6: Commit**

```bash
git add -A
git commit -m "Collapse application fake defmultis to plain functions"
```

---

## Task 5: R2a — Collapse Computer AI Same-File Defmultis

**Overview:** 5 files where defmulti+defmethod are already co-located. Apply Same-File Collapse Pattern.

**Files:**

| File | Defmultis | Lines |
|------|-----------|-------|
| `computer/land_objectives.cljc` | 11 | 189 |
| `computer/fighter_movement.cljc` | 16 | 243 |
| `computer/army/movement.cljc` | 12 | 180 |
| `computer/transport_core.cljc` | 8 | 84 |
| `computer/ship_core.cljc` | 11 | 164 |

**Step 1: For each file, replace every defmulti+defmethod pair with defn**

The transformation is mechanical. For each occurrence:

Before:
```clojure
(defmulti get-passable-neighbors (fn [& _] :default))
(defmethod get-passable-neighbors :default
  [pos]
  ...)
```

After:
```clojure
(defn get-passable-neighbors
  [pos]
  ...)
```

Ensure internal self-references don't use the multimethod var syntax. E.g., if a defmethod calls another function in the same file via the multimethod, it should still work as a plain function call.

**Step 2: Verify**

```bash
clj -M:spec
```

**Step 3: Commit**

```bash
git add -A
git commit -m "Collapse computer AI same-file fake defmultis to plain functions"
```

---

## Task 6: R2b — Collapse Containers/Ops Same-File Defmultis

**Files:**
- Modify: `src/empire/containers/ops.cljc` (229 lines, 13 defmultis)

**Step 1: Apply Same-File Collapse Pattern to all 13 defmulti/defmethod pairs**

**Step 2: Verify**

```bash
clj -M:spec
```

**Step 3: Commit**

```bash
git add -A
git commit -m "Collapse containers/ops fake defmultis to plain functions"
```

---

## Task 7: R2c — Collapse Combat and Debug Contract/Impl Pairs

**Files to merge:**

| Contract | Impl | Defmultis |
|----------|------|-----------|
| `combat.cljc` | `domain/model/impl/combat_runtime.cljc` | 14 |
| `domain/model/combat.cljc` | `domain/model/impl/combat.cljc` | 4 |
| `debug.cljc` | `debug/impl/facade_methods.cljc` | 9 |

**IMPORTANT:** `combat.cljc` (top-level) has 14 defmultis. Its impl is `domain/model/impl/combat_runtime.cljc` (189 lines) which requires `sa`, `combat.escorts`, `config`, `domain.model.combat`, `movement.visibility`, `units.dispatcher`. After merge, `combat.cljc` will have these requires.

`domain/model/combat.cljc` has 4 defmultis. Its impl is `domain/model/impl/combat.cljc` (69 lines) which requires `units.dispatcher`. After merge, `domain.model.combat` gains this require. Both are `:outer-ring`, so this is safe.

**Step 1: Read all 6 files**

**Step 2: Merge each pair using Contract/Impl Merge Pattern**

**Step 3: Delete impl files**

```bash
rm src/empire/domain/model/impl/combat_runtime.cljc
rm src/empire/domain/model/impl/combat.cljc
rm src/empire/debug/impl/facade_methods.cljc
rmdir src/empire/domain/model/impl/ 2>/dev/null || true
rmdir src/empire/debug/impl/ 2>/dev/null || true
```

**Step 4: Update bootstrap.cljc**

Remove:
```clojure
[empire.domain.model.impl.combat-runtime]
[empire.debug.impl.facade-methods]
```

(Check if `domain.model.impl.combat` is in bootstrap too.)

**Step 5: Verify**

```bash
clj -M:spec
clj -M:check-dependencies
scripts/check-architecture-boundaries.sh
```

**Step 6: Commit**

```bash
git add -A
git commit -m "Collapse combat and debug fake defmultis to plain functions"
```

---

## Task 8: R2d — Collapse Units Contract/Impl Pairs

**Overview:** 6 unit-type files have defmulti contracts with impl in `units/impl/`. The `units/dispatcher.cljc` is EXCLUDED — it has genuine multi-value dispatch on unit type.

**Files to merge:**

| Contract | Impl | Defmultis |
|----------|------|-----------|
| `units/carrier.cljc` | `units/impl/carrier.cljc` | 11 |
| `units/fighter.cljc` | `units/impl/fighter.cljc` | 9 |
| `units/transport.cljc` | `units/impl/transport.cljc` | 11 |
| `units/satellite.cljc` | `units/impl/satellite.cljc` | 6 |

**Already co-located (Same-File Collapse Pattern):**

| File | Defmultis |
|------|-----------|
| `units/ships.cljc` | 4 |
| `units/army.cljc` | 3 |

**DO NOT TOUCH:**
- `units/dispatcher.cljc` — genuine polymorphism (dispatches on unit-type keyword)
- `units/impl/dispatcher.cljc` — real multi-value implementations

**Step 1: Merge the 4 contract/impl pairs**

For carrier, fighter, transport, satellite: apply Contract/Impl Merge Pattern.

Note: Some contract files (carrier, fighter, transport) have both defmulti declarations AND some defmethod :default implementations in the same file. The impl file has the remaining defmethod :default implementations. After merge, ALL become plain defn in the contract file.

**Step 2: Collapse ships.cljc and army.cljc in place**

Apply Same-File Collapse Pattern.

**Step 3: Delete impl files (but NOT dispatcher)**

```bash
rm src/empire/units/impl/carrier.cljc
rm src/empire/units/impl/fighter.cljc
rm src/empire/units/impl/transport.cljc
rm src/empire/units/impl/satellite.cljc
```

Do NOT delete `units/impl/dispatcher.cljc` or `units/impl/ships.cljc` or `units/impl/army.cljc`.

Wait — check if `units/impl/ships.cljc` and `units/impl/army.cljc` have any content beyond what's already in the contract. If they only duplicate the contract, delete them. If they have additional dispatcher-level defmethods, keep them.

**Step 4: Update boundary guard (Task 1)**

After deleting impl files, the units/impl guard from Task 1 still applies — `units/impl/dispatcher.cljc` remains, plus possibly `ships.cljc` and `army.cljc`.

**Step 5: Update bootstrap.cljc if needed**

Remove any requires for deleted impl files:
```clojure
[empire.units.impl.satellite]
```
(Check which unit impl files are in bootstrap.)

**Step 6: Verify**

```bash
clj -M:spec
clj -M:check-dependencies
scripts/check-architecture-boundaries.sh
```

**Step 7: Commit**

```bash
git add -A
git commit -m "Collapse unit-type fake defmultis to plain functions"
```

---

## Task 9: R5 — Split MovementPort into 3 Protocols

**Files:**
- Modify: `src/empire/application/ports/movement.cljc` (split into 3 protocols)
- Modify: `src/empire/movement/adapter.cljc` (implement 3 protocols instead of 1)
- Modify: consumers (update requires to use specific port)

**Step 1: Read the current MovementPort**

Read `src/empire/application/ports/movement.cljc` for all 21 method signatures.

**Step 2: Define 3 new protocols**

Create 3 new files (or keep in one file with 3 protocols — user preference):

**`application/ports/unit_state.cljc`** — UnitStatePort:
```clojure
(ns empire.application.ports.unit-state)

(defprotocol UnitStatePort
  (movement-get-active-unit [this cell])
  (movement-is-army-aboard-transport? [this active-unit])
  (movement-is-fighter-from-airport? [this active-unit])
  (movement-is-fighter-from-carrier? [this active-unit])
  (movement-context [this cell active-unit])
  (movement-set-unit-mode [this coords mode])
  (movement-add-unit-at [this coords unit-type owner])
  (movement-wake-at [this coords]))
```

**`application/ports/movement_execution.cljc`** — MovementExecutionPort:
```clojure
(ns empire.application.ports.movement-execution)

(defprotocol MovementExecutionPort
  (movement-move-unit [this coords target cell current-map])
  (movement-set-unit-movement [this coords target extended?])
  (movement-update-cell-visibility [this pos owner])
  (movement-update-cell-visibility-with-unit [this pos owner unit]))
```

**`application/ports/pathfinding.cljc`** — PathfindingPort:
```clojure
(ns empire.application.ports.pathfinding)

(defprotocol PathfindingPort
  (movement-find-nearest-unexplored [this pos unit-type])
  (movement-bfs-to-unseen-coast [this pos computer-map claimed-targets])
  (movement-bfs-to-land-ho-target [this from target computer-map])
  (movement-bfs-to-coast-target [this from computer-map])
  (movement-next-step [this from target unit-type passability-fn cache-key-extra])
  (movement-lake-cells [this world lake-max-cells]))
```

**Step 3: Update the old movement.cljc port**

Make `application/ports/movement.cljc` a re-export facade that requires all 3 and defines MovementPort as extending all 3 (or just delete it and update consumers). Recommend: keep as facade initially for backwards compatibility, migrate consumers in a follow-up.

**Step 4: Update adapter.cljc**

`MovementAdapter` record implements all 3 new protocols.

**Step 5: Update consumers**

Each consumer should require only the protocol(s) it uses. This can be done incrementally — the facade keeps everything working during transition.

**Step 6: Verify**

```bash
clj -M:spec
clj -M:check-dependencies
scripts/check-architecture-boundaries.sh
```

**Step 7: Commit**

```bash
git add -A
git commit -m "Split MovementPort into UnitStatePort, MovementExecutionPort, PathfindingPort"
```

---

## Task 10: R6 — Collapse Movement Internal Protocols

**Prerequisite:** Task 9 (R5) must be complete.

**Overview:** Remove the 4 internal protocols, the service registry, and the API facade. The adapter calls implementation modules directly.

**Files to delete:**
- `src/empire/movement/movement.cljc` (4 protocol definitions)
- `src/empire/movement/methods.cljc` (DefaultMovementServices record)
- `src/empire/movement/service.cljc` (atom-based registry)
- `src/empire/movement/api.cljc` (facade)

**Files to modify:**
- `src/empire/movement/adapter.cljc` — call impl modules directly instead of api
- `src/empire/movement/bootstrap.cljc` — remove service registration; keep context initialization
- Any file that requires `movement.api` — replace with direct module calls

**Step 1: Identify all callers of movement/api.cljc**

Run: `rg 'empire\.movement\.api' src/empire`

These need to either call the implementation modules directly or go through the adapter.

**Step 2: Update adapter.cljc**

Replace `api/` calls with direct calls to:
- `empire.movement.movement-pathing` (for next-step-pos, chebyshev-distance, find-best-sidestep)
- `empire.movement.movement-execution` (for process-consumables, do-move)
- `empire.movement.movement-resolution` (for move-unit, set-unit-movement)
- `empire.movement.movement-state` (for get-active-unit, is-army-aboard-transport?, etc.)
- `empire.movement.visibility` (for update-cell-visibility)
- `empire.movement.pathfinding-bfs` (for find-nearest-unexplored, bfs-to-*)
- `empire.movement.pathfinding` (for next-step)
- `empire.movement.lakes` (for lake-cells)

**Step 3: Update bootstrap.cljc**

Remove service registration. Keep context initialization (`movement-context/set-state-ctx!`, `pathfinding/set-world-loader!`, etc.).

**Step 4: Delete the 4 files**

```bash
rm src/empire/movement/movement.cljc
rm src/empire/movement/methods.cljc
rm src/empire/movement/service.cljc
rm src/empire/movement/api.cljc
```

**Step 5: Remove movement architecture guards that reference deleted files**

In `scripts/check-architecture-boundaries.sh`, remove guards for:
- `empire.movement.methods`
- `empire.movement.service`
- `empire.movement.api`

Keep the `empire.movement.adapter` guard if one exists.

**Step 6: Verify**

```bash
clj -M:spec
clj -M:check-dependencies
scripts/check-architecture-boundaries.sh
```

**Step 7: Commit**

```bash
git add -A
git commit -m "Collapse movement internal protocols to plain functions"
```
