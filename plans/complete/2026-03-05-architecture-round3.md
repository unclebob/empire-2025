# Architecture Round 3: Break Cycle, Consolidate Top-Level, Application Abstractness

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Eliminate the 5-component dependency cycle, reduce top-level namespace sprawl from 10 to 5 files, and add a StateAccessPort protocol to increase application-layer abstractness.

**Architecture:** The cycle is caused by misclassified components in `dependency-checker.edn` — `units.dispatcher`, `domain.model.combat`, `atoms-runtime`, and `save-load` all fall into the `:outer-ring` catch-all but belong in inner layers. Fixing classifications breaks ALL inward-to-outer-ring back-edges, collapsing the 5-node SCC to zero cycles. Top-level consolidation moves 5 facade/coordinator files into their natural subdirectories. The StateAccessPort formalizes the state-access contract as a protocol.

**Tech Stack:** Clojure 1.12, Speclj (TDD)

---

## Task 0: Fix component classifications to break the dependency cycle

The dependency checker reports one cycle: `:use-cases -> :ui -> :domain-services -> :outer-ring -> :application`. The root cause: 5 namespaces are misclassified into `:outer-ring` (the catch-all) when they belong in inner layers. Every inward-to-outer-ring back-edge passes through one of these misclassified namespaces.

**Files:**
- Modify: `dependency-checker.edn`

**Step 1: Read the current config**

Read `dependency-checker.edn`.

**Step 2: Reclassify `units.dispatcher` into `:config`**

`units.dispatcher` is a pure data map of unit configuration. It only requires `:config`-level modules (`units.config`, `units.ships`, `units.army`, etc.). Add it to the `:config` component.

Change the `:config` rule from:
```clojure
{:component :config
 :kind :concrete
 :match ["^empire\\.config(\\..+)?$"
         "empire.units.config"
         "empire.units.ships"]}
```
to:
```clojure
{:component :config
 :kind :concrete
 :match ["^empire\\.config(\\..+)?$"
         "empire.units.config"
         "empire.units.ships"
         "empire.units.dispatcher"]}
```

This eliminates 15 back-edges: 8 from domain-services, 4 from use-cases, and 3 from ui — all via `units.dispatcher`.

**Step 3: Reclassify `domain.model.combat` into `:inner-ring`**

`domain.model.combat` contains pure domain functions and only requires `:config`-level modules. The `:inner-ring` pattern already includes `empire.domain.model.containers` — broaden to all `domain.model.*`:

Change the `:inner-ring` match from:
```clojure
:match ["^empire\\.domain\\.core(\\.(?!impl\\.).*)?$"
        "^empire\\.domain\\.services(\\.(?!impl\\.).*)?$"
        "empire.domain.model.containers"]
```
to:
```clojure
:match ["^empire\\.domain\\.core(\\.(?!impl\\.).*)?$"
        "^empire\\.domain\\.services(\\.(?!impl\\.).*)?$"
        "^empire\\.domain\\.model(\\..*)?$"]
```

This eliminates the `combat.cljc -> domain.model.combat` back-edge.

**Step 4: Reclassify `atoms-runtime` into `:application`**

`atoms-runtime` is runtime state used by the application layer. The `:application` pattern `^empire\\.atoms(\\..*)?$` matches `empire.atoms` and `empire.atoms.anything` but NOT `empire.atoms-runtime` (hyphen vs dot). Add it explicitly:

Change the `:application` match to include:
```clojure
"empire.atoms-runtime"
```

Add it after the existing `"^empire\\.atoms(\\..*)?$"` line.

This eliminates the `atoms -> atoms-runtime` back-edge.

**Step 5: Reclassify `save-load` into `:application`**

`save-load` only requires `:application`-level and `:inner-ring`-level modules (`adapters.persistence.files`, `application.ports.persistence`, `application.state-access`, `domain.core.messages`). Add it:

```clojure
"empire.save-load"
```

Add it to the `:application` match list. This eliminates the `ui -> outer-ring` back-edge via `dispatch -> save-load`.

**Step 6: Exclude `acceptance-harness` from `:application`**

`acceptance-harness` is test infrastructure that wires across all layers (like `bootstrap`). Exclude it so it falls to `:outer-ring`:

Change the `:application` match from:
```clojure
"^empire\\.application\\.(?!bootstrap$).*$"
```
to:
```clojure
"^empire\\.application\\.(?!bootstrap$|acceptance.harness$).*$"
```

This eliminates the last `application -> outer-ring` back-edge.

**Step 7: Run the dependency checker**

```bash
clj -M:check-dependencies
```

Expected: `Cycles: 0`. All 5 inward-to-outer-ring back-edges are eliminated. The `:outer-ring` component becomes a pure leaf — it depends on inner layers but nothing depends on it.

**Step 8: Run all tests**

```bash
clj -M:spec
```

**Step 9: Commit**

```
Fix component classifications: break 5-node dependency cycle

Reclassify units.dispatcher (config), domain.model.combat (inner-ring),
atoms-runtime and save-load (application), acceptance-harness (outer-ring).
Eliminates all inward-to-outer-ring back-edges → 0 cycles.
```

---

## Task 1: Merge `computer.cljc` into `computer/core.cljc`

The top-level `computer.cljc` (32 lines) is a thin coordinator that dispatches to `computer/army`, `computer/fighter`, etc. `computer/core.cljc` (233 lines) already exists. Merge the coordinator into core (combined: 265 lines). Only 1 caller.

**Files:**
- Delete: `src/empire/computer.cljc`
- Modify: `src/empire/computer/core.cljc`
- Modify: `src/empire/game_loop/item_processing.cljc` (the 1 caller)
- Modify: `spec/empire/computer_spec.clj` (spec for computer.cljc)

**Step 1: Read all 4 files**

Read `src/empire/computer.cljc`, `src/empire/computer/core.cljc`, `src/empire/game_loop/item_processing.cljc`, and `spec/empire/computer_spec.clj`.

**Step 2: Copy functions from `computer.cljc` into `computer/core.cljc`**

Add the three functions (`computer-unit?`, `dispatch-unit`, `process-computer-unit`) to the end of `computer/core.cljc`. Add any requires that `computer.cljc` has that `core.cljc` lacks (e.g., `empire.computer.army`, `empire.computer.fighter`, `empire.computer.ship`, `empire.computer.transport`). Also add `empire.application.state-access` if not already present.

**Step 3: Update the caller**

In `game_loop/item_processing.cljc`, change:
- `[empire.computer :as computer]` → `[empire.computer.core :as computer]`

All `computer/` calls stay the same (alias unchanged).

**Step 4: Update the spec**

In `spec/empire/computer_spec.clj`, change:
- `(ns empire.computer-spec` → `(ns empire.computer.core-spec` (or keep the original name if it tests both)
- Update the require from `empire.computer` to `empire.computer.core`

**Step 5: Delete `src/empire/computer.cljc`**

```bash
rm src/empire/computer.cljc
```

**Step 6: Run tests**

```bash
clj -M:spec
```

**Step 7: Commit**

```
Merge computer.cljc coordinator into computer/core
```

---

## Task 2: Move `game_loop.cljc` to `game_loop/core.cljc`

The top-level `game_loop.cljc` (191 lines) is the round orchestrator. It already has 7 sub-modules in `game_loop/`. Moving it into the subdirectory as `core.cljc` is natural. 5 callers in src.

**Files:**
- Move: `src/empire/game_loop.cljc` → `src/empire/game_loop/core.cljc`
- Modify: 5 callers (update namespace require)
- Move: `spec/empire/game_loop_spec.clj` → `spec/empire/game_loop/core_spec.clj`
- Modify: `spec/empire/game_loop_rounds_spec.clj`, `spec/empire/game_loop_movement_spec.clj` (if they require `empire.game-loop`)

**Step 1: Move the source file**

```bash
mv src/empire/game_loop.cljc src/empire/game_loop/core.cljc
```

**Step 2: Update the ns declaration**

In `src/empire/game_loop/core.cljc`, change:
```clojure
(ns empire.game-loop ...)
```
to:
```clojure
(ns empire.game-loop.core ...)
```

**Step 3: Find and update all callers**

```bash
rg 'empire\.game-loop\b' src spec --glob '!**/game_loop/core.cljc'
```

For each caller, replace `empire.game-loop` with `empire.game-loop.core` in the require. Keep the `:as game-loop` alias so call sites don't change.

Known src callers:
- `src/empire/acceptance/harness.cljc`
- `src/empire/ui/quil/core.cljc`
- `src/empire/ui/util/input/actions/helpers.cljc`
- `src/empire/adapters/runtime/acceptance_engine.cljc`
- `src/empire/ui/util/input/dispatch.cljc`

**Step 4: Move and update spec files**

```bash
mv spec/empire/game_loop_spec.clj spec/empire/game_loop/core_spec.clj
```

Update the ns declaration and requires in the moved spec. Also update `game_loop_rounds_spec.clj` and `game_loop_movement_spec.clj` if they require `empire.game-loop`.

**Step 5: Run tests**

```bash
clj -M:spec
```

**Step 6: Commit**

```
Move game_loop.cljc into game_loop/core
```

---

## Task 3: Inline `debug.cljc` facade

The top-level `debug.cljc` (46 lines) is a pure delegation facade — every function just calls through to `debug/logging` or `debug/dump`. 13 callers. After inlining, callers require the target modules directly.

**Files:**
- Delete: `src/empire/debug.cljc`
- Modify: 13 caller files (update requires)
- Modify: `spec/empire/debug_spec.clj` (update requires)

**Step 1: Map functions to targets**

| Facade function | Target |
|----------------|--------|
| `log-player-movement!` | `debug.logging/log-player-movement!` |
| `log-computer-event!` | `debug.logging/log-computer-event!` |
| `log-action!` | `debug.logging/log-action!` |
| `dump-region` | `debug.dump/dump-region` |
| `format-cell` | `debug.dump/format-cell` |
| `format-dump` | `debug.dump/format-dump` |
| `generate-dump-filename` | `debug.dump/generate-dump-filename` |
| `write-dump!` | `debug.dump/write-dump!` |
| `screen-coords-to-cell-range` | `debug.dump/screen-coords-to-cell-range` |

**Step 2: Update callers one at a time**

For each caller:
- Replace `[empire.debug :as debug]` with `[empire.debug.logging :as debug-log]` (and `[empire.debug.dump :as debug-dump]` if needed)
- Replace `debug/log-*` calls with `debug-log/log-*`
- Replace `debug/write-dump!` etc. with `debug-dump/write-dump!`

**Logging-only callers (11 files)** — need only `debug.logging`:
- `src/empire/movement/coastline.cljc`
- `src/empire/computer/core.cljc`
- `src/empire/computer/army.cljc`
- `src/empire/computer/army/coastal.cljc`
- `src/empire/computer/army/combat.cljc`
- `src/empire/computer/army/movement.cljc`
- `src/empire/computer/army/transport.cljc`
- `src/empire/computer/transport.cljc`
- `src/empire/computer/transport_loading.cljc`
- `src/empire/computer/transport_unloading.cljc`

**Both logging and dump (1 file)**:
- `src/empire/ui/util/input/dispatch.cljc`

Note: `coastline.cljc` uses both `log-action!` and `log-player-movement!`.

Run tests after every 3-4 callers.

**Step 3: Update the spec**

In `spec/empire/debug_spec.clj`, replace `empire.debug` require with `empire.debug.logging` and `empire.debug.dump`. Update function references.

**Step 4: Delete the facade**

```bash
rm src/empire/debug.cljc
```

**Step 5: Run tests**

```bash
clj -M:spec
```

**Step 6: Commit**

```
Inline debug facade: callers require debug/logging and debug/dump directly
```

---

## Task 4: Move `save_load.cljc` to `application/save_load.cljc`

`save_load.cljc` (158 lines) only depends on `:application` and `:inner-ring` modules. Moving it into `application/` reflects its true layer. 2 callers.

**Files:**
- Move: `src/empire/save_load.cljc` → `src/empire/application/save_load.cljc`
- Modify: 2 callers
- Move: `spec/empire/save_load_spec.clj` → `spec/empire/application/save_load_spec.clj`

**Step 1: Move the source file**

```bash
mv src/empire/save_load.cljc src/empire/application/save_load.cljc
```

**Step 2: Update the ns declaration**

Change `(ns empire.save-load ...)` to `(ns empire.application.save-load ...)`.

**Step 3: Update callers**

```bash
rg 'empire\.save-load' src spec
```

Known callers:
- `src/empire/ui/quil/rendering/overlay.cljc` — change require
- `src/empire/ui/util/input/dispatch.cljc` — change require

Replace `empire.save-load` with `empire.application.save-load` in requires. Keep `:as save-load` alias.

**Step 4: Move and update spec**

```bash
mkdir -p spec/empire/application
mv spec/empire/save_load_spec.clj spec/empire/application/save_load_spec.clj
```

Update ns declaration and requires.

**Step 5: Run tests**

```bash
clj -M:spec
```

**Step 6: Commit**

```
Move save_load into application layer where it belongs
```

---

## Task 5: Define `StateAccessPort` protocol

The application layer has distance 0.545 (abstractness 0.08). Adding a `StateAccessPort` protocol formalizes the state-access contract and increases abstractness. Per architecture policy, migrate at least one consumer and add a guard.

**Files:**
- Create: `src/empire/application/ports/state_access.cljc`
- Modify: `src/empire/application/state_access.cljc` (implement protocol)
- Modify: `src/empire/combat.cljc` (migrate `apply-combat-result!`)
- Create: `spec/empire/application/ports/state_access_spec.clj`

**Step 1: Define the protocol**

Create `src/empire/application/ports/state_access.cljc`:

```clojure
(ns empire.application.ports.state-access)

(defprotocol StateAccessPort
  (current-world [this])
  (update-world! [this f])
  (read-state [this k])
  (write-state! [this k v])
  (update-state! [this k f]))
```

These are the 5 most-used functions from `state-access.cljc`.

**Step 2: Implement the protocol in `state-access.cljc`**

Add a `defrecord` or `reify` that implements `StateAccessPort` by delegating to the existing singleton functions. Add a public `state-access-port` function that returns the implementation:

```clojure
(defn state-access-port []
  (reify StateAccessPort
    (current-world [_] (current-world))
    (update-world! [_ f] (update-world! f))
    (read-state [_ k] (read-state k))
    (write-state! [_ k v] (write-state! k v))
    (update-state! [_ k f] (update-state! k f))))
```

Import the protocol namespace in state-access.cljc's requires.

**Step 3: Migrate `combat.cljc` as first consumer**

Change `apply-combat-result!` to accept an optional state-access port:

```clojure
(defn apply-combat-result!
  ([result] (apply-combat-result! result (sa/state-access-port)))
  ([{:keys [world messages state-updates visibility]} port]
   (when world
     (ports/update-world! port (constantly world)))
   (doseq [[k v] messages]
     (ports/write-state! port k v))
   (doseq [[k v] state-updates]
     (if (fn? v)
       (ports/update-state! port k v)
       (ports/write-state! port k v)))
   (doseq [{:keys [pos owner]} visibility]
     (visibility/update-cell-visibility pos owner))))
```

The 0-arity version preserves backward compatibility. The 2-arity version enables testing with a mock port.

**Step 4: Write a spec for the protocol**

Create `spec/empire/application/ports/state_access_spec.clj`:

```clojure
(ns empire.application.ports.state-access-spec
  (:require [speclj.core :refer :all]
            [empire.application.ports.state-access :as ports]
            [empire.application.state-access :as sa]
            [empire.test-utils :as tu]))

(describe "StateAccessPort"
  (tu/reset-atoms-fixture)

  (it "state-access-port implements the protocol"
    (let [port (sa/state-access-port)]
      (should-not-be-nil port)
      (should (satisfies? ports/StateAccessPort port))))

  (it "delegates current-world to singleton"
    (let [port (sa/state-access-port)]
      (should= (sa/current-world) (ports/current-world port))))

  (it "delegates read-state to singleton"
    (let [port (sa/state-access-port)]
      (should= (sa/read-state :round-number) (ports/read-state port :round-number)))))
```

**Step 5: Run tests**

```bash
clj -M:spec
```

**Step 6: Commit**

```
Add StateAccessPort protocol; migrate combat apply-combat-result!
```

---

## Task 6: Update boundary guards and dependency-checker.edn

Add guards for all removed/moved namespaces and verify the full pipeline.

**Files:**
- Modify: `scripts/check-architecture-boundaries.sh`
- Modify: `dependency-checker.edn` (if not already done in Task 0)

**Step 1: Add guards for moved namespaces**

```bash
# Removed top-level namespaces — use new locations
toplevel_moved_hits="$(rg -n 'empire\.(computer|game-loop|save-load|debug)\b' src/empire --glob '!**/computer/**' --glob '!**/game_loop/**' --glob '!**/application/**' --glob '!**/debug/**' | rg -v '^src/empire/(computer|game_loop|debug|application)/' || true)"
```

Actually, these guards are tricky because the old namespace names are substrings of the new ones. Instead, add specific guards:

```bash
# empire.computer was merged into empire.computer.core
computer_toplevel_hits="$(rg -n 'empire\.computer\b' src/empire | rg -v 'empire\.computer\.' || true)"
if [[ -n "$computer_toplevel_hits" ]]; then
  echo "Architecture boundary violation: empire.computer was merged into computer.core:"
  printf '%s\n' "$computer_toplevel_hits"
  exit 1
fi

# empire.debug facade was removed; use debug.logging or debug.dump
debug_facade_hits="$(rg -n 'empire\.debug\b' src/empire | rg -v 'empire\.debug\.' || true)"
if [[ -n "$debug_facade_hits" ]]; then
  echo "Architecture boundary violation: empire.debug facade was removed; use debug.logging or debug.dump:"
  printf '%s\n' "$debug_facade_hits"
  exit 1
fi
```

**Step 2: Add guard for StateAccessPort consumer**

```bash
# combat.cljc must not import concrete state-access (uses port instead)
combat_sa_hits="$(rg -n 'empire\.application\.state-access' src/empire/combat.cljc || true)"
if [[ -n "$combat_sa_hits" ]]; then
  echo "Architecture boundary violation: combat.cljc should use StateAccessPort, not concrete state-access:"
  printf '%s\n' "$combat_sa_hits"
  exit 1
fi
```

**Step 3: Run the full boundary check and tests**

```bash
scripts/check-architecture-boundaries.sh
clj -M:spec
```

**Step 4: Commit**

```
Add boundary guards for round 3 namespace moves
```

---

## Execution Order

```
Task 0 (fix classifications)     — do first; breaks the cycle with zero code changes
Task 1 (merge computer)          — independent
Task 2 (move game_loop)          — independent
Task 3 (inline debug)            — independent but largest (13 callers)
Task 4 (move save_load)          — independent
Task 5 (StateAccessPort)         — depends on Task 0 (config stable)
Task 6 (boundary guards)         — depends on Tasks 0-5
```

Task 0 is config-only — do it first to validate the cycle breaks. Tasks 1-4 are independent namespace moves. Task 5 is the deepest refactor. Task 6 is cleanup.

**Expected results:**
- Dependency cycles: 1 → 0
- Top-level files: 10 → 5 (`atoms.cljc`, `atoms_runtime.cljc`, `config.cljc`, `combat.cljc`, `test_utils.cljc`, `init.cljc`)
- Application abstractness: increased (new StateAccessPort protocol)
- ~5 namespaces moved/removed, ~20 files modified
