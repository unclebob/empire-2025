# Architecture Cleanup Design

**Date:** 2026-03-05
**Status:** Draft — awaiting approval

## Overview

Seven coordinated refactorings to eliminate fake polymorphism, tighten component boundaries, and separate Quil from UI logic. None violate the ring architecture — all operate within a single ring or preserve existing dependency directions.

---

## R1. Collapse Domain Inner-Ring Fake Defmultis

**Problem:** 43 defmultis in `domain/` dispatch `(fn [& _] :default)` with exactly one `:default` implementation. Six directories of contract/impl ceremony for no actual polymorphism.

**Namespaces affected:**

| Contract NS | Impl NS | Count |
|-------------|---------|-------|
| `domain.core.unit-metrics` | `domain.core.impl.unit-metrics` | 4 |
| `domain.core.continents` | `domain.core.impl.continents` | 2 |
| `domain.core.refueling` | `domain.core.impl.refueling` | 3 |
| `domain.core.messages` | `domain.core.impl.messages` | 1 |
| `domain.services.threat-policy` | `domain.services.impl.threat-policy` | 9 |
| `domain.services.round-setup` | `domain.services.impl.round-setup` | 4 |
| `domain.model.containers` | `domain.model.impl.containers` | 10 |
| `domain.model.combat` | `domain.model.impl.combat` | 4 |
| **Total** | | **37** |

Note: 6 additional domain defmultis may exist in sub-namespaces not yet cataloged. Final count confirmed during implementation.

**Change:** For each pair, merge the `:default` implementation into the contract namespace as a plain `defn`. Delete the impl namespace. Remove the bootstrap require.

**Ring impact:** None. Both contract and impl are `:inner-ring`. Functions stay in `domain/`.

**Dependency-checker impact:** The `:inner-ring` match patterns `^empire\.domain\.core(\.(?!impl\.).*)?$` and `^empire\.domain\.services(\.(?!impl\.).*)?$` explicitly exclude `impl` sub-namespaces (they fall to `:outer-ring`). After collapse, the impl namespaces are deleted, so the exclusion becomes moot. No rule change needed.

Wait — the impl namespaces are currently matched by the `empire.*` catch-all as `:outer-ring`. But they contain defmethod implementations for inner-ring defmultis. This means the dependency checker currently sees `domain.core.impl.X` as outer-ring depending on inner-ring `domain.core.X` — which is the allowed direction. After collapse, these namespaces simply cease to exist. **No rule change needed.**

**Bootstrap impact:** Remove requires for all `domain.*.impl.*` namespaces from `bootstrap.cljc`.

---

## R2. Collapse Computer AI Fake Defmultis

**Problem:** 47 defmultis across 4 computer modules dispatch `(fn [& _] :default)` with single implementations in the same file.

**Namespaces affected:**

| Namespace | Count |
|-----------|-------|
| `computer.land-objectives` | 11 |
| `computer.fighter-movement` | 16 |
| `computer.army.movement` | 12 |
| `computer.transport-core` | 8 |
| `computer.ship-core` | 11 |
| **Total** | **~58** |

Note: Some of these (like `computer.army.movement`) have defmulti + defmethod in the same file — they're already co-located but still use the multimethod ceremony. Others may have separate impl files.

**Change:** Convert each `defmulti`/`defmethod :default` pair to a plain `defn` in the same file. Where impl is in a separate namespace, merge and delete.

**Ring impact:** None. All `:outer-ring`.

---

## R3. Add units/impl Boundary Guard

**Problem:** `empire.units.impl.*` contains 57+ defmethod implementations wired via bootstrap, but no architecture guard prevents direct requires from feature modules. Every other impl directory has a guard.

**Change:** Add to `scripts/check-architecture-boundaries.sh`:

```bash
units_impl_hits="$(rg -n 'empire\.units\.impl' src/empire || true)"
units_impl_violations="$(printf '%s\n' "$units_impl_hits" | rg -v '^src/empire/units/impl/|^src/empire/application/bootstrap\.cljc:' || true)"
if [[ -n "$units_impl_violations" ]]; then
  echo "Architecture boundary violation: units.impl must only be referenced from itself and application/bootstrap:"
  printf '%s\n' "$units_impl_violations"
  exit 1
fi
```

**Ring impact:** None. Enforcement only.

**Risk:** May discover existing violations that need fixing first.

---

## R4. Collapse application/runtime Defmulti

**Problem:** `application/runtime.cljc` defines one defmulti `default-state-ctx` with one `:default` implementation in `application/impl/runtime.cljc`. `state-access.cljc` wraps it in `(delay ...)`. This is the root of the entire state initialization chain.

**Change:** Move the implementation from `impl/runtime.cljc` directly into `application/runtime.cljc` as a plain `defn`. Delete `impl/runtime.cljc`. Remove its require from `bootstrap.cljc`.

The `(delay ...)` in `state-access.cljc` remains — it's needed for lazy initialization regardless of multimethod vs plain function.

**Also collapse:** The other `application/` defmultis in the same pattern:
- `application/state.cljc` (5 defmultis) ← `application/impl/state.cljc`
- `application/coords.cljc` (1) ← `application/impl/coords.cljc`
- `application/production-status.cljc` (1) ← `application/impl/production_status.cljc`
- `application/city-production.cljc` (1) ← `application/impl/city_production.cljc`
- `application/unit-stamping.cljc` (3) ← `application/impl/unit_stamping.cljc`

Total: 12 defmultis across 6 pairs.

**Ring impact:** None. All `:outer-ring`.

---

## R5. Split MovementPort (21 methods → 3 protocols)

**Problem:** `MovementPort` has 21 methods spanning visibility, pathfinding, unit state queries, BFS exploration, and lake classification. Consumers that need one capability must mock all 21.

**Cohesion analysis** (from caller data):

| Proposed Protocol | Methods | Primary Consumers |
|-------------------|---------|-------------------|
| `UnitStatePort` | get-active-unit, is-army-aboard-transport?, is-fighter-from-airport?, is-fighter-from-carrier?, context, set-unit-mode, add-unit-at, wake-at | player/commands, ui/input/actions |
| `MovementExecutionPort` | move-unit, set-unit-movement, update-cell-visibility, update-cell-visibility-with-unit | game_loop, player/commands, computer/* |
| `PathfindingPort` | find-nearest-unexplored, bfs-to-unseen-coast, bfs-to-land-ho-target, bfs-to-coast-target, next-step, lake-cells | computer/movement, computer/ship_core |

**Change:** Split `application/ports/movement.cljc` into 3 protocol files under `application/ports/`. Update `movement/adapter.cljc` to implement all 3. Update consumers to require only the protocol(s) they use.

**Ring impact:** None. Protocols stay in `application/ports/` (matched by `:inner-ring` or `:outer-ring` depending on pattern — verify). Adapter stays in `movement/`.

**Note:** The existing movement architecture guards remain valid — they protect `movement.methods`, `movement.service`, and `movement.api`, not the port protocols.

---

## R6. Collapse Movement Internal Protocols

**Problem:** 4 internal protocols (`MovementPathingPort`, `MovementExecutionPort`, `MovementResolutionPort`, `MovementStatePort`) in `movement/movement.cljc` have exactly one implementation (`DefaultMovementServices` in `movement/methods.cljc`). Plus an atom-based service registry (`service.cljc`) and an API facade (`api.cljc`) — three layers of indirection for no polymorphism.

**Change:** Collapse the 4 internal protocols and their single implementation into plain functions. The stack `api.cljc → service.cljc → methods.cljc → movement.cljc` collapses to direct function calls in the implementation modules (`movement_pathing.cljc`, `movement_execution.cljc`, `movement_resolution.cljc`, `movement_state.cljc`).

The `MovementPort` protocol at the application boundary (R5) **remains** — that's a real port with test double potential. The adapter (`adapter.cljc`) would call the implementation modules directly instead of going through the service registry.

**Ring impact:** None. All `movement/` is `:outer-ring`.

**Architecture guard impact:** The 3 existing movement guards (`methods`, `service`, `api`) would be **removed** since those namespaces cease to exist. The `adapter.cljc` guard remains.

**Risk:** Medium — this is the most complex refactoring. The service registry pattern may have been chosen for initialization ordering. Needs careful analysis of `movement/bootstrap.cljc` and `movement/context.cljc` to ensure the collapse doesn't break initialization.

**Recommendation:** Do this last, after R1-R5 are stable.

---

## R7. Separate Quil and UI Components

**Problem:** Quil (rendering framework) and UI logic (input dispatch, display formatting, action handling) are not declared as separate components in `dependency-checker.edn`. The acceptance harness (`acceptance/harness.cljc`) directly requires `quil.core` and `ui.quil.input`, coupling tests to the rendering framework.

### Current dependency direction

```
ui/quil/core.cljc      → ui/util/core.cljc
ui/quil/core.cljc      → ui/util/input/dispatch.cljc
ui/quil/core.cljc      → ui/util/rendering/display.cljc
ui/quil/input.cljc     → ui/util/input/dispatch.cljc
ui/quil/rendering/*.cljc → ui/util/rendering/display.cljc
```

**This is already correct:** Quil depends on UI, not vice versa. No UI util file imports `quil.core`.

### The harness leak

`acceptance/harness.cljc` imports:
- `empire.ui.quil.input` — to call `quil-input/key-down`
- `quil.core` — to mock `q/mouse-x`, `q/mouse-y`

`quil/input.cljc` does:
```clojure
(defn key-down [k]
  (dispatch/dispatch-key k (q/mouse-x) (q/mouse-y)))
```

The harness calls `key-down` and mocks the Quil mouse functions. This is the only reason the harness depends on Quil.

### Fix: Extract key-down with explicit coordinates

Move the mouse-coordinate-aware key dispatch into the UI layer:

**In `ui/util/input/dispatch.cljc`** — add:
```clojure
(defn key-down [k mouse-x mouse-y]
  (dispatch-key k mouse-x mouse-y))
```

**In `ui/quil/input.cljc`** — change to:
```clojure
(defn key-down [k]
  (dispatch/key-down k (q/mouse-x) (q/mouse-y)))
```

**In `acceptance/harness.cljc`** — change to:
```clojure
(defn key-down!
  [k]
  (set-last-key! nil)
  (input-dispatch/key-down k 0 0))

(defn key-down-at!
  [k mouse-x mouse-y]
  (set-last-key! nil)
  (input-dispatch/key-down k mouse-x mouse-y))
```

This removes the harness's dependency on `quil.core` and `ui.quil.input`.

### New component rules

Add to `dependency-checker.edn`:

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

This enforces: UI logic must never depend on the Quil rendering framework.

### Boundary guard

Add to `scripts/check-architecture-boundaries.sh`:

```bash
quil_outside_hits="$(rg -n 'empire\.ui\.quil' src/empire --glob '!src/empire/ui/quil/*' || true)"
if [[ -n "$quil_outside_hits" ]]; then
  echo "Architecture boundary violation: empire.ui.quil must only be referenced from within ui/quil/:"
  printf '%s\n' "$quil_outside_hits"
  exit 1
fi
```

**Ring impact:** None. Both `:quil` and `:ui` are concrete components within `:outer-ring`. The new forbidden dependency `[:ui :quil]` is orthogonal to the ring rules.

---

## Execution Order

Dependencies between recommendations:

```
R3 (units guard)     — independent, do first (quick win)
R7 (Quil/UI split)   — independent, do early (removes harness coupling)
R1 (domain collapse)  — independent
R2 (computer collapse) — independent
R4 (app collapse)     — independent
R5 (split MovementPort) — independent of R1-R4, do before R6
R6 (movement internals) — depends on R5, do last
```

Recommended batches:
1. **R3 + R7** — boundary guards and component separation (small, high value)
2. **R1 + R4** — inner-ring + application defmulti collapse
3. **R2** — computer AI defmulti collapse (largest batch)
4. **R5** — split MovementPort
5. **R6** — collapse movement internals (most complex, benefits from R5)
6. **R8** — layer enforcement (depends on all above being clean)

---

## R8 — Layer Enforcement (added 2026-03-05)

Split the 145-namespace `:outer-ring` catch-all into three enforced layers.

### Components (dependency order, top to bottom)

| Component | Namespaces | Role |
|-----------|-----------|------|
| `:quil` | `empire.ui.quil.*` | Quil framework binding |
| `:ui` | `empire.ui.util.*` | UI logic, input dispatch |
| `:use-cases` | `empire.player.*`, `empire.computer.*`, `empire.game_loop.*` | Domain orchestration |
| `:domain-services` | `empire.movement.*`, `empire.combat.*`, `empire.containers.*`, `empire.debug.*` | Shared domain services |
| `:application` | `empire.application.*`, `empire.adapters.*`, `empire.atoms.*` | Ports, state, adapters |
| `:inner-ring` | `empire.domain.core.*`, `empire.domain.services.*`, `empire.domain.model.containers` | Pure domain logic |
| `:config` | `empire.config.*`, `empire.units.config`, `empire.units.ships` | Constants |
| `:outer-ring` | `empire.*` (catch-all) | Composition roots, init, test utils |

### Enforced rules

- `[:domain-services :use-cases]` — services never depend on orchestrators
- `[:application :use-cases]` — app layer never depends on orchestrators
- `[:application :domain-services]` — app layer never depends on services
- `[:inner-ring :application]`, `[:inner-ring :domain-services]`, `[:inner-ring :use-cases]` — inner ring depends on nothing above config

### Composition roots

`bootstrap.cljc` and `acceptance_engine.cljc` are excluded from `:application` and fall into `:outer-ring`. As composition roots, they legitimately wire across all layers.

### Prerequisite

Three movement façade files (`movement_services`, `player_movement_services`, `waypoint_services`) moved from `application/` to `movement/` so `:application` has no `:domain-services` dependencies.
