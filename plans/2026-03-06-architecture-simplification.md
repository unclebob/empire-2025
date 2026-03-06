# Architecture Simplification Design

## Goal

Reduce the codebase to 7 components across 5 layers with strictly downward dependencies.
Eliminate the port/adapter/ctx-map infrastructure (17 files) that adds ceremony without
providing real polymorphic boundaries or dependency inversion.

## 5-Layer / 7-Component Target

```
L5  UI              ui.quil.*, ui.util.*
L4  Round Mechanics  game-loop.*, application.*
L3  Player           player.*
L3  Computer         computer.*
L2  Shared Mechanics movement.*, containers.*, domain.services.*
L1  State            state.api, state.atoms, state.runtime
L1  Config           config.*, units.*, domain.core.*, domain.model.*
```

Forbidden: upward dependencies. Player and Computer are L3 peers -- neither depends on the other.

## Changes

### 1. Move combat to L2

Move `application/combat.cljc` to `domain/services/combat.cljc`.

- Change ns: `empire.application.combat` -> `empire.domain.services.combat`
- Update 8 callers (find-replace on require)
- Update dependency-checker.edn

Files touched: 9

### 2. Create State component (L1)

Consolidate state into `src/empire/state/`:

| New file | Source | Role |
|---|---|---|
| `state/api.cljc` | `application/state_access.cljc` | Public API (78 callers) |
| `state/atoms.cljc` | `application/state/atoms.cljc` | Atom definitions |
| `state/runtime.cljc` | `application/state/runtime.cljc` | Runtime atom definitions |

`state/api.cljc` is the only public face. It reads/writes atoms directly -- no ctx map,
no protocol dispatch, no key->atom lookup table. The atom namespaces are private
implementation details.

The API preserves the current function signatures so 78 callers need only a require rename:
- `empire.application.state-access` -> `empire.state.api`

This API boundary lets us consolidate atoms later without affecting callers.

Files touched: 81 (78 callers + 3 new files)

### 3. Delete port/adapter infrastructure

Remove 17 files that exist only for mocking and provide no real dependency inversion:

**Ports (9 files) -- `application/ports/`:**
- acceptance_harness.cljc
- clock.cljc
- movement_execution.cljc
- pathfinding.cljc
- persistence.cljc
- random.cljc
- runtime_state.cljc
- unit_state.cljc
- world_store.cljc

**Adapters (6 files) -- `adapters/`:**
- state/atoms.cljc (AtomWorldStore)
- state/runtime.cljc (AtomRuntimeStateStore)
- persistence/files.cljc (FilesPersistenceAdapter)
- runtime/clock.cljc (SystemClock)
- runtime/rng.cljc (CoreRandom)
- runtime/acceptance_engine.cljc

**Adapter/wiring (2 files):**
- movement/adapter.cljc
- application/bootstrap.cljc

**Boundary/factory (2 files):**
- application/state.cljc (command boundary)
- application/runtime.cljc (ctx-factory)

Total: 19 files deleted.

Callers that used ports/adapters indirectly through the ctx map switch to direct calls:
- `(ports/movement-next-step (pathfinding-port) ...)` -> `(pathfinding/next-step ...)`
- `(exec-ports/movement-set-unit-movement (execution-port) ...)` -> `(api/set-unit-movement ...)`
- `(movement-port/movement-update-cell-visibility (execution-port) ...)` -> `(visibility/update-cell-visibility ...)`

For test mocking: use `with-redefs` on the concrete functions.

Persistence functions from `adapters/persistence/files.cljc` move into `application/save_load.cljc`
as plain functions (timestamp, list-saves, save-state!, load-state).

### 4. Event-based detection (L2 -> L3 inversion)

`movement/visibility.cljc` currently calls up to `threat-response/handle-detection!` (L3)
via the ctx map. This is the one real upward dependency.

Fix: visibility returns detection events as data. The caller (L3 or L4) dispatches them.

Before:
```clojure
(when-let [f (sa/context-fn :handle-detection!)]
  (f pos unit owner))
```

After:
```clojure
;; visibility returns {:detections [{:pos pos :unit unit :owner owner}]}
;; caller dispatches:
(doseq [d detections]
  (threat-response/handle-detection! d))
```

### 5. Computer -> city-production (L3 -> L4)

Computer currently calls `:set-city-production!` from the ctx map, which delegates to
`application/city_production.cljc` (L4). But set-city-production just writes to the
production atom.

Fix: computer calls `state.api` directly to write production decisions. No inversion needed.
If city-production has validation logic, extract the pure validation to L2 (domain.services)
and let computer call that + state.api.

## Execution Order

Steps must be done in this order due to dependencies:

1. **Move combat** -- standalone, no prereqs
2. **Create State component** -- standalone, no prereqs (can parallel with 1)
3. **Delete ports/adapters/ctx** -- depends on State component existing (step 2)
4. **Event-based detection** -- depends on ctx map being gone (step 3)
5. **Computer direct production** -- depends on ctx map being gone (step 3)

Steps 1 and 2 are independent. Steps 4 and 5 are independent.

## Agent Recommendations

**3 concurrent agents**, organized as follows:

**Agent A -- Move combat (step 1)**
- Worktree branch: `arch/move-combat`
- Move file, update ns, update 8 callers, update dependency-checker.edn
- Run tests
- Small, self-contained, fast

**Agent B -- Create State component (step 2)**
- Worktree branch: `arch/state-component`
- Create `state/api.cljc`, move atom files
- Rename requires in 78 callers
- Run tests
- Large blast radius but mechanical (find-replace)

**Agent C -- Delete infrastructure + events (steps 3-5)**
- Worktree branch: `arch/remove-ports`
- Depends on A and B merging first
- Delete 19 files
- Replace ctx-map usage with direct calls in all callers
- Convert visibility to return detection events
- Convert computer to call state.api for production
- Move persistence functions to save_load.cljc
- Run tests
- Largest scope but cannot start until steps 1+2 are merged

**Merge order:** A merges first (smallest), B merges second, C starts after both are in master.

## Verification

After each step:
1. `clj -M:spec` (all unit tests)
2. `clj -M:parse-tests && clj -M:generate-specs && clj -M:spec generated-acceptance-specs/`
3. No upward dependency violations in dependency-checker.edn
