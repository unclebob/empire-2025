# Architecture Cohesion Improvements Design

## Goal

Five coordinated refactorings to reduce coupling, eliminate boilerplate, and bring all production files under 250 lines.

## Execution Order

State-access extraction first (subsequent splits use it). Then four independent splits.

```
1. State-access extraction (58 files)
   |
   v
2. config split    3. round_setup split    4. actions split    5. core split
   (independent)      (independent)           (independent)      (independent)
```

## 1. State-Access Module

**Problem:** 58 files duplicate `(delay (app-runtime/default-state-ctx))` + `current-world` + `read-runtime-state` + `write-runtime-state!` + `update-game-map!`. Two different `update-game-map!` implementations exist.

**New file:** `src/empire/application/state_access.cljc`

```clojure
(ns empire.application.state-access
  (:require [empire.application.runtime :as app-runtime]
            [empire.application.state :as app-state]))

(def ^:private ctx (delay (app-runtime/default-state-ctx)))
(defn state-ctx [] @ctx)
(defn current-world [] ((:load-world @ctx)))
(defn update-world! [f & args] (apply app-state/update-world! @ctx f args))
(defn read-state [k] ((:read-runtime-state @ctx) k))
(defn write-state! [k v] ((:write-runtime-state! @ctx) k v))
(defn update-state! [k f & args] (write-state! k (apply f (read-state k) args)))
```

**Migration:** Each of 58 files replaces private boilerplate with `(:require [empire.application.state-access :as sa])`. Module-specific helpers (e.g., `movement-port`, `set-error-message!`, `world-ref`) stay local.

**Boundary guard:** Add to `check-architecture-boundaries.sh`: no file outside `application/` should contain `(delay (app-runtime/default-state-ctx))` after migration.

## 2. Config Split

**Problem:** `config.cljc` (298 lines, 37 dependents) mixes rendering, generation, AI, keys, and messages.

**Target:** 5 sub-modules + re-export facade.

| New file | Contents | ~Lines |
|---|---|---|
| `config/rendering.cljc` | cell-size, fonts, pixel offsets, text-area layout, colors, `color-of`, `mode->color`, `unit->color`, `city-color-key`, `country-land-color` | 95 |
| `config/generation.cljc` | `default-map-size`, `smooth-count`, `land-fraction`, `number-of-cities`, `min-city-distance`, `max-placement-attempts`, `min-surrounding-land`, `compute-size-constants` | 25 |
| `config/ai.cljc` | `armies-before-transport`, `max-patrol-boats-per-country`, `carrier-city-threshold`, `max-live-carriers`, `max-carrier-producers`, `satellite-city-threshold`, `max-satellites`, `advances-per-frame` | 20 |
| `config/keys.cljc` | `key->direction`, `key->extended-direction`, `key->production-item` | 35 |
| `config/messages.cljc` | `messages` map, `error-message-duration` | 50 |
| `config.cljc` (facade) | Re-exports from all sub-modules + remaining domain constants (`hostile-city?`, `fighter-fuel`, `transport-capacity`, etc.) + unit-stat delegation fns (`item-cost`, `item-hits`, etc.) | 75 |

**Migration:** Existing 37 call sites keep working via facade. Modules can switch to direct sub-module requires over time.

## 3. Round Setup Split

**Problem:** `game_loop/round_setup.cljc` (424 lines) mixes 8 concerns. Over the 250-line mutation threshold.

**Target:** 4 sub-modules + orchestrator.

| New file | Functions | ~Lines |
|---|---|---|
| `round_setup/fuel.cljc` | `bingo-fuel?`, `fuel-action`, `apply-fuel-action`, `consume-sentry-fighter-fuel` | 35 |
| `round_setup/waking.cljc` | `wake-airport-fighters`, `wake-carrier-fighters`, `wake-sentries-seeing-enemy` | 50 |
| `round_setup/lakes.cljc` | All lake/evacuation/lock functions (lines 174-387) | 215 |
| `round_setup/repair.cljc` | `repair-city-ships`, `repair-damaged-ships` | 35 |
| `round_setup.cljc` (orchestrator) | `dead-unit?`, `computer-carrier?`, `remove-dead-units`, `reset-steps-remaining`, `move-satellites` + sequencing calls | 90 |

All sub-modules use `state-access` instead of duplicating boilerplate.

## 4. Actions Split

**Problem:** `ui/util/input/actions.cljc` (293 lines, 14 deps). Highest fan-out in codebase.

**Target:** 3 sub-modules + dispatcher.

| New file | Handlers | Key deps |
|---|---|---|
| `actions/movement.cljc` | `calculate-extended-target`, `launch-fighter-and-update`, `army-aboard-action`, movement chain | combat, container-ops, ports.movement, dispatcher |
| `actions/modes.cljc` | `handle-space-key`, `handle-unload-key`, `handle-sentry-key`, `handle-look-around-key` | container-ops, ports.movement, explore, coastline |
| `actions/production.cljc` | `try-set-production`, `handle-city-production-key` | dispatcher, ports.movement, player-production |
| `actions.cljc` (dispatcher) | `item-processed!` (shared), `handle-key` | state-access + 3 sub-modules |

Each sub-module: 3-9 deps instead of 14.

## 5. Computer Core Contract/Impl Split

**Problem:** `computer/core.cljc` (282 lines) defines 17 `defmulti` + implements all 17 `:default` in same file. Violates architecture policy.

**Target:**

| File | Contents | ~Lines |
|---|---|---|
| `computer/core.cljc` (contracts) | 17 `defmulti` declarations + pure helpers (`neighbors-in-map`, `distance`, `chebyshev-distance`, `adjacent?`, `neighbor-offsets`) | 70 |
| `computer/core/impl.cljc` | 17 `defmethod :default` + private stateful helpers | 210 |

**Wiring:** `application/bootstrap.cljc` requires `empire.computer.core.impl`.

**Boundary guard:** `computer.core.impl` only referenced from itself and `application.bootstrap`.

## Validation Protocol

After each split:
1. `clj -M:spec-structure-check` (if spec files changed)
2. `clj -M:spec` (full unit tests)
3. `clj -M:all-tests-fast` (includes boundary checks)
4. `clj -M:check-dependencies dependency-tool.edn`

## Success Criteria

- All production files under 250 lines
- Zero `(delay (app-runtime/default-state-ctx))` outside `application/state_access.cljc`
- `config.cljc` dependents can require only the sub-module they need
- `computer/core.cljc` contracts separated from implementations
- No regressions: all 3393+ specs pass, acceptance pipeline green
- Boundary guards added for new splits
