# Layer Enforcement Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Split the 145-namespace `:outer-ring` catch-all into three enforced layers: `:application`, `:domain-services`, and `:use-cases`.

**Architecture:** The outer-ring contains three natural layers with no bidirectional dependencies between them. `:domain-services` (movement, combat, containers, debug) never depends on `:use-cases` (player, computer, game-loop). `:application` (ports, state, adapters, atoms) never depends on `:use-cases`, and shouldn't depend on `:domain-services` — but three façade files violate this and must move first.

**Tech Stack:** Clojure, dependency checker (`clj -M:check-dependencies`), architecture boundary script (`scripts/check-architecture-boundaries.sh`), Speclj (`clj -M:spec`)

---

## Prerequisites

Before each task, run `clj -M:spec` to confirm a green baseline. After each task, run all three verification commands:
```bash
clj -M:spec
clj -M:check-dependencies
scripts/check-architecture-boundaries.sh
```

---

## Task 1: Move Movement Façade Files Out of Application

Three files in `application/` are thin wrappers around `movement/` modules. They must move to `movement/` so the `:application` component can forbid `:domain-services` dependencies.

**Files:**
- Move: `src/empire/application/movement_services.cljc` → `src/empire/movement/services.cljc`
- Move: `src/empire/application/player_movement_services.cljc` → `src/empire/movement/player_services.cljc`
- Move: `src/empire/application/waypoint_services.cljc` → `src/empire/movement/waypoint_services.cljc`

**Step 1: Create `src/empire/movement/services.cljc`**

Copy `src/empire/application/movement_services.cljc` verbatim but change the ns:

```clojure
(ns empire.movement.services
  (:require [empire.movement.lakes :as lakes]
            [empire.movement.map-utils :as map-utils]
            [empire.movement.pathfinding :as pathfinding]
            [empire.movement.pathfinding-bfs :as pathfinding-bfs]
            [empire.movement.satellite :as satellite]
            [empire.movement.visibility :as visibility]
            [empire.movement.wake-conditions :as wake]))
```

All function bodies stay identical.

**Step 2: Create `src/empire/movement/player_services.cljc`**

Copy `src/empire/application/player_movement_services.cljc` verbatim but change the ns:

```clojure
(ns empire.movement.player-services
  (:require [empire.movement.coastline :as coastline]
            [empire.movement.explore :as explore]))
```

**Step 3: Create `src/empire/movement/waypoint_services.cljc`**

Copy `src/empire/application/waypoint_services.cljc` verbatim but change the ns:

```clojure
(ns empire.movement.waypoint-services
  (:require [empire.movement.waypoint :as waypoint]))
```

**Step 4: Update all callers**

Replace `empire.application.movement-services` with `empire.movement.services` in these files:

| File | Current alias |
|------|--------------|
| `src/empire/game_loop.cljc` | `movement-services` |
| `src/empire/player/commands/actions.cljc` | `movement-services` |
| `src/empire/player/commands.cljc` | `movement-services` |
| `src/empire/player/production.cljc` | `movement-services` |
| `src/empire/player/attention.cljc` | `movement-services` |
| `src/empire/game_loop/round_setup.cljc` | `movement-services` |
| `src/empire/game_loop/round_setup/waking.cljc` | `movement-services` |
| `src/empire/game_loop/round_setup/fuel.cljc` | `movement-services` |
| `src/empire/game_loop/round_setup/lakes.cljc` | `movement-services` |
| `src/empire/init.cljc` | `movement-services` |

Replace `empire.application.player-movement-services` with `empire.movement.player-services` in:

| File | Current alias |
|------|--------------|
| `src/empire/player/commands/actions.cljc` | `player-movement` |
| `src/empire/game_loop/item_processing.cljc` | `player-movement` |

Replace `empire.application.waypoint-services` with `empire.movement.waypoint-services` in:

| File | Current alias |
|------|--------------|
| `src/empire/player/orders.cljc` | `waypoint-services` |

**Step 5: Delete the 3 old files**

```bash
rm src/empire/application/movement_services.cljc
rm src/empire/application/player_movement_services.cljc
rm src/empire/application/waypoint_services.cljc
```

**Step 6: Verify**

```bash
clj -M:spec
```

**Step 7: Commit**

```bash
git add -A
git commit -m "Move movement façade files from application to movement"
```

---

## Task 2: Add Three New Components to dependency-checker.edn

**Files:**
- Modify: `dependency-checker.edn`

**Step 1: Read the current file**

Read `dependency-checker.edn`.

**Step 2: Add the 3 new components**

Insert them BEFORE the `:outer-ring` catch-all (order matters — first match wins). The full component-rules list becomes:

```edn
:component-rules
[{:component :config
  :kind :concrete
  :match ["^empire\\.config(\\..+)?$"
          "empire.units.config"
          "empire.units.ships"]}

 {:component :inner-ring
  :kind :concrete
  :match ["^empire\\.domain\\.core(\\.(?!impl\\.).*)?$"
          "^empire\\.domain\\.services(\\.(?!impl\\.).*)?$"
          "empire.domain.model.containers"]}

 {:component :quil
  :kind :concrete
  :match "^empire\\.ui\\.quil(\\..*)?$"}

 {:component :ui
  :kind :concrete
  :match "^empire\\.ui\\.util(\\..*)?$"}

 {:component :application
  :kind :concrete
  :match ["^empire\\.application(\\..*)?$"
          "^empire\\.adapters(\\..*)?$"
          "^empire\\.atoms(\\..*)?$"]}

 {:component :domain-services
  :kind :concrete
  :match ["^empire\\.movement(\\..*)?$"
          "^empire\\.combat(\\..*)?$"
          "^empire\\.containers(\\..*)?$"
          "^empire\\.debug(\\..*)?$"]}

 {:component :use-cases
  :kind :concrete
  :match ["^empire\\.player(\\..*)?$"
          "^empire\\.computer(\\..*)?$"
          "^empire\\.game.loop(\\..*)?$"]}

 {:component :outer-ring
  :kind :concrete
  :match "empire.*"}]
```

**Step 3: Update forbidden-dependencies**

```edn
:forbidden-dependencies
[[:inner-ring :outer-ring]
 [:inner-ring :application]
 [:inner-ring :domain-services]
 [:inner-ring :use-cases]
 [:ui :quil]
 [:domain-services :use-cases]
 [:application :use-cases]
 [:application :domain-services]]
```

**Step 4: Run the dependency checker**

```bash
clj -M:check-dependencies
```

Expected: 0 violations. If violations appear, they indicate a dependency that must be fixed before proceeding.

**Likely violations to handle:**

1. **`:application` → `:domain-services`**: `bootstrap.cljc` depends on `empire.movement.*`. This is the composition root — it legitimately wires everything. Move bootstrap to `:outer-ring` by adjusting the `:application` match:

```edn
{:component :application
 :kind :concrete
 :match ["^empire\\.application\\.(?!bootstrap$).*$"
         "empire.application.runtime"
         "^empire\\.adapters(\\..*)?$"
         "^empire\\.atoms(\\..*)?$"]}
```

This excludes `empire.application.bootstrap` from `:application`, letting it fall through to `:outer-ring`.

2. **`:application` → `:use-cases`**: Same file (`bootstrap.cljc`) depends on `empire.computer.*`. Solved by the same exclusion above.

3. **`:application` → `:domain-services`** or `:use-cases`**: `adapters/runtime/acceptance_engine.cljc` depends on `empire.game-loop` and `empire.ui.util.*`. Exclude it similarly:

```edn
{:component :application
 :kind :concrete
 :match ["^empire\\.application\\.(?!bootstrap$).*$"
         "empire.application.runtime"
         "^empire\\.adapters\\.(?!runtime\\.acceptance.engine$).*$"
         "empire.adapters.runtime.acceptance-engine"  ;; NO — remove this, let it fall to outer-ring
         "^empire\\.atoms(\\..*)?$"]}
```

Wait — the simpler approach: match adapters explicitly minus the one file:

```edn
{:component :application
 :kind :concrete
 :match ["^empire\\.application\\.(?!bootstrap$).*$"
         "empire.application.runtime"
         "^empire\\.adapters\\.(?!runtime\\.acceptance.engine$).*$"
         "^empire\\.atoms(\\..*)?$"]}
```

If this regex approach doesn't work with the dependency checker, an alternative is to list the adapters explicitly:

```edn
"empire.adapters.state.atoms"
"empire.adapters.state.runtime"
"empire.adapters.persistence.files"
"empire.adapters.runtime.clock"
"empire.adapters.runtime.rng"
```

**Step 5: Iterate until `clj -M:check-dependencies` reports 0 violations**

Adjust the regex patterns as needed. The key insight: `bootstrap.cljc` and `acceptance_engine.cljc` are composition roots that wire across layers — they belong in `:outer-ring`.

**Step 6: Run full verification**

```bash
clj -M:spec
clj -M:check-dependencies
scripts/check-architecture-boundaries.sh
```

**Step 7: Commit**

```bash
git add dependency-checker.edn
git commit -m "Add application, domain-services, and use-cases components with layer enforcement"
```

---

## Task 3: Add Boundary Guards for New Layer Rules

The dependency checker enforces component-level rules. Add ripgrep guards in the boundary script for finer-grained checks.

**Files:**
- Modify: `scripts/check-architecture-boundaries.sh`

**Step 1: Read the current script**

Read `scripts/check-architecture-boundaries.sh`.

**Step 2: Add domain-services → use-cases guard**

This is the most important new rule. Domain services (movement, combat, containers, debug) must never import player, computer, or game-loop.

```bash
# Domain services must not depend on use-cases
ds_to_uc_hits="$(rg -n 'empire\.(player|computer|game.loop)' src/empire/movement src/empire/combat.cljc src/empire/combat src/empire/containers src/empire/debug || true)"
if [[ -n "$ds_to_uc_hits" ]]; then
  echo "Architecture boundary violation: domain-services must not depend on use-cases (player/computer/game-loop):"
  printf '%s\n' "$ds_to_uc_hits"
  exit 1
fi
```

**Step 3: Add application → use-cases guard (excluding bootstrap and acceptance_engine)**

```bash
# Application must not depend on use-cases (excluding composition roots)
app_to_uc_hits="$(rg -n 'empire\.(player|computer|game.loop)' src/empire/application src/empire/adapters src/empire/atoms --glob '!**/bootstrap.cljc' --glob '!**/acceptance_engine.cljc' || true)"
if [[ -n "$app_to_uc_hits" ]]; then
  echo "Architecture boundary violation: application must not depend on use-cases (player/computer/game-loop):"
  printf '%s\n' "$app_to_uc_hits"
  exit 1
fi
```

**Step 4: Add application → domain-services guard (excluding bootstrap)**

```bash
# Application must not depend on domain-services (excluding composition roots)
app_to_ds_hits="$(rg -n 'empire\.(movement|combat|containers|debug)' src/empire/application src/empire/adapters src/empire/atoms --glob '!**/bootstrap.cljc' --glob '!**/acceptance_engine.cljc' || true)"
if [[ -n "$app_to_ds_hits" ]]; then
  echo "Architecture boundary violation: application must not depend on domain-services (movement/combat/containers/debug):"
  printf '%s\n' "$app_to_ds_hits"
  exit 1
fi
```

**Step 5: Verify**

```bash
clj -M:spec
clj -M:check-dependencies
scripts/check-architecture-boundaries.sh
```

**Step 6: Commit**

```bash
git add scripts/check-architecture-boundaries.sh
git commit -m "Add boundary guards for layer enforcement rules"
```

---

## Task 4: Update Design Document

**Files:**
- Modify: `docs/plans/2026-03-05-architecture-cleanup-design.md`

**Step 1: Append a section documenting the new layer structure**

Add to the end of the design document:

```markdown
## R8 — Layer Enforcement (added 2026-03-05)

Split the 145-namespace `:outer-ring` catch-all into three enforced layers:

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
```

**Step 2: Commit**

```bash
git add docs/plans/2026-03-05-architecture-cleanup-design.md
git commit -m "Document layer enforcement architecture (R8)"
```
