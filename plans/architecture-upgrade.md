# Architecture + State Management Upgrade Plan

## Goal

Restructure the system around dependency-inverted boundaries so core game policy is independent of atoms, UI, filesystem, and runtime details, then execute state-management consolidation inside those boundaries.

## Architectural Principles

- Inner layers define contracts; outer layers implement them.
- Domain logic is pure and deterministic where possible.
- Application orchestrates use-cases and coordinates side effects through ports.
- Adapters translate external concerns (UI, persistence, time, randomness, runtime state) into port implementations.
- State mutation is centralized behind a single application boundary.

## Plan Order

1. Establish architecture boundaries and ports first.
2. Execute state-management refactors within that architecture.
3. Enforce boundary rules and consolidate remaining legacy atoms.

## Hard Constraints

1. Do not modify acceptance scenarios in `acceptanceTests/*.txt`.
2. Do not modify parser namespaces under `src/empire/acceptance/parser/*`.
3. Preserve generator compatibility with existing IR and scenario corpus.
4. Every phase must pass all unit and acceptance tests before advancing.
5. Request play test review after each phase gate.

## Target Component Model

1. `domain.world` (pure core rules)
   - Movement resolution, combat resolution, production rules, container rules, victory checks.
   - Input: immutable world + command.
   - Output: updated world + domain events.

2. `domain.ai` (pure policy and strategy)
   - Threat detection policy, assignment policy, transport invasion policy, refuel policy.
   - Input: world snapshot.
   - Output: intents/commands.

3. `application.turn-engine` (use-case orchestration)
   - `start-round`, `process-player-item`, `process-computer-item`, `advance-game`.
   - Calls domain modules and applies resulting commands/events via ports.

4. `application.state` (single mutation boundary)
   - `apply-command!`, `apply-events!`, `with-invariants!`.
   - Owns all writes to runtime state storage.

5. `ports` (owned by inner layers)
   - `WorldStorePort`: `load-world`, `save-world!`.
   - `RngPort`: `roll`, `shuffle`.
   - `ClockPort`: `now-ms`.
   - `PersistencePort`: `save-game!`, `load-game`.
   - `RenderPort`: render view model.
   - `InputPort`: input stream/intents.
   - `TelemetryPort`: log events.

6. `adapters.state.atoms`
   - Implements `WorldStorePort` using existing atoms.
   - Transitional adapter while legacy atoms are still present.

7. `adapters.ui.quil`
   - Implements `RenderPort` + `InputPort`.
   - Converts keystrokes/clicks to app commands.

8. `adapters.persistence.files`
   - Implements `PersistencePort` using existing save/load file formats.

9. `adapters.runtime`
   - Implements `ClockPort`, `RngPort`, telemetry adapters.

10. `acceptance.pipeline` (separate bounded context)
   - Parser IR contracts, parser, and generator remain separate from runtime engine.
   - No direct dependency from runtime domain on parser/generator code.

## Current Inventory (State Baseline)

- 67 atoms are currently defined across:
  - `src/empire/atoms.cljc`
  - `src/empire/atoms/runtime.cljc`
  - `src/empire/computer/land_objectives.cljc`
- In runtime code (excluding test helpers and acceptance generator), `atoms/game-map` is the dominant hotspot with 225 write sites.
- Next write hotspots are `player-items`, `computer-carrier-positions`, `production`, `paused`, `attention-message`, and `waiting-for-input`.

## High-Risk Files First

- `src/empire/combat.cljc` (15 `game-map` writes plus cache writes)
- `src/empire/containers/ops.cljc` (20 `game-map` writes)
- `src/empire/game_loop/round_setup.cljc` (13 `game-map` writes)
- `src/empire/computer/threat_response.cljc` (8 `game-map` writes, 2 `major-invasion-state` writes)
- `src/empire/computer/transport_unloading.cljc` (13 `game-map` writes)
- `src/empire/game_loop.cljc` (orchestration with many state transitions)

## Split Candidates (Over 250 Lines)

- `src/empire/computer/threat_response.cljc` (333 lines)
- `src/empire/player/commands.cljc` (285 lines)
- `src/empire/combat.cljc` (262 lines)
- `src/empire/containers/ops.cljc` (255 lines)

## Dependency Direction

- Allowed: `adapters -> application -> domain`
- Allowed: `application -> ports` and `domain -> domain`
- Forbidden: `domain/application -> atoms/ui/save-load/quil`
- Acceptance boundary rule: `acceptance pipeline -> acceptance harness interfaces -> application`
- Forbidden: generated acceptance specs depending on domain internals, atoms, UI adapters, or parser/generator internals.

## Contract Style in Clojure

- Use function-map ports for most boundaries:
  - Example: `{:load-world ..., :save-world! ..., :roll ..., :now-ms ...}`
- Use protocols where stronger named contracts improve readability/stability.
- Use multimethods for domain variation by data tag (unit type, terrain), not for infrastructure inversion.

## Acceptance Testing Boundary

- Treat generated acceptance specs as an external adapter.
- Generated specs must depend only on a stable acceptance harness contract.
- The acceptance harness contract is defined by interfaces owned at the boundary, implemented by the application.
- This is strong dependency inversion: acceptance artifacts target abstractions; application provides implementations.
- Keep dependency one-way:
  - `acceptance parser/generator/specs -> acceptance harness interfaces -> application use-cases`
  - never `application/domain -> acceptance parser/generator`.

### Acceptance Harness Contract (Stable API)

- `given-world` (initialize scenario state through application boundary)
- `when-input` (apply player/computer inputs as application commands)
- `advance-rounds` (advance simulation through turn engine)
- `query-world` (read model for assertions without exposing internals)
- `then-assert` helpers (assertions over stable query model)

### Contract Governance

- Version the acceptance IR-to-harness contract.
- Evolve parser/generator independently as long as harness contract compatibility is maintained.
- Add contract tests to ensure generated specs call only approved harness interfaces.

## Proposed Namespace Layout

- `src/empire/domain/world/*`
- `src/empire/domain/ai/*`
- `src/empire/application/turn_engine.cljc`
- `src/empire/application/state.cljc`
- `src/empire/application/ports.cljc`
- `src/empire/adapters/state/atoms.cljc`
- `src/empire/adapters/ui/quil/*.cljc`
- `src/empire/adapters/persistence/files.cljc`
- `src/empire/adapters/runtime/{clock,rng,telemetry}.cljc`

## Mapping from Current Hotspots

- `src/empire/combat.cljc` -> `domain.world.combat` (+ app command wrappers)
- `src/empire/containers/ops.cljc` -> `domain.world.containers`
- `src/empire/game_loop.cljc` and `src/empire/game_loop/*` -> `application.turn-engine`
- `src/empire/computer/threat_response.cljc` -> `domain.ai.threat` + app coordination
- `src/empire/player/commands.cljc` and `src/empire/player/orders.cljc` -> input adapters + app commands
- `src/empire/save_load.cljc` -> `adapters.persistence.files`
- `src/empire/atoms*.cljc` -> `adapters.state.atoms` (transitional), eventually slimmed to UI/runtime only

## Migration Plan (Architecture First, State Execution Inside)

1. Introduce ports + composition root
   - Add `application/ports` and bootstrap wiring in `init`/main.
   - No behavioral change.

2. Add application state boundary
   - Introduce `application/state` with `apply-command!`.
   - Wrap existing mutation paths without changing logic.

3. Extract first vertical slice: combat
   - Move pure combat resolution to `domain.world.combat`.
   - Keep adapter layer writing state via `apply-command!`.
   - Return explicit effects (messages, visibility, cache updates).

4. Extract containers slice
   - Move load/disembark/launch to pure transforms in domain.
   - Route visibility/message updates through effects handled by application.

5. Extract round setup slice
   - Convert round-start mutating routines into pure transforms.
   - Apply via single state boundary.

6. Extract threat-response slice
   - Split detection/policy from mutation.
   - Centralize major invasion state transitions.

7. Input/UI inversion
   - Convert player command handlers to emit app commands only.
   - UI becomes adapter with no direct domain mutation.

8. Persistence inversion
   - Refactor save/load calls behind `PersistencePort`.
   - App requests save/load; adapter executes IO.

9. Add acceptance harness boundary
   - Introduce acceptance harness interfaces owned by the boundary.
   - Implement harness interfaces in application layer.
   - Update generator output target to harness API only.

10. Tighten boundaries
   - Add static checks/lint for forbidden requires.
   - Remove remaining direct atom mutation from domain/application.
   - Enforce acceptance boundary (generated specs may only depend on harness API).

11. Consolidate state
    - Move domain state to one world-state atom via adapter.
    - Keep only UI/runtime service atoms outside world-state.

## Execution Phases (Locked Acceptance Inputs)

### Phase 0: Baseline Lock

1. Add characterization specs for current acceptance harness behavior.
2. Add CI guard that fails if `acceptanceTests/*.txt` or `src/empire/acceptance/parser/*` change.
3. Run phase gate tests (see Phase Gate).
4. Request play test review.

### Phase 1: Ports + Composition Root (No Behavior Change)

1. Introduce `application/ports` and composition wiring.
2. Keep existing implementations and behavior unchanged.
3. Run phase gate tests.
4. Request play test review.

### Phase 2: Application State Boundary

1. Introduce `application/state` with `apply-command!`.
2. Route existing mutation entry points through boundary wrappers.
3. Add invariants in warn-only mode initially.
4. Run phase gate tests.
5. Request play test review.

### Phase 3: Combat Vertical Slice

1. Extract pure combat transitions/effects into `domain.world.combat`.
2. Apply effects through `application/state`.
3. Keep acceptance harness API stable.
4. Run phase gate tests.
5. Request play test review.

### Phase 4: Containers + Round Setup Vertical Slice

1. Extract pure transforms for container operations.
2. Extract pure transforms for round setup.
3. Keep orchestration in application layer.
4. Run phase gate tests.
5. Request play test review.

### Phase 5: Threat Response + AI Assignment Slice

1. Split detection/policy from mutation in threat response.
2. Keep behavior equivalent against existing acceptance scenarios.
3. Run phase gate tests.
4. Request play test review.

### Phase 6: Acceptance Harness Inversion

1. Define stable acceptance harness interfaces.
2. Make generated acceptance specs depend only on harness interfaces.
3. Implement harness interfaces in application.
4. Keep scenarios and parser unchanged.
5. Run phase gate tests.
6. Request play test review.

### Phase 7: Boundary Enforcement + Consolidation

1. Enforce forbidden dependency rules in CI/lint.
2. Remove remaining direct domain mutation outside `application/state`.
3. Consolidate domain state into world-state adapter.
4. Keep parser/scenario inputs unchanged.
5. Run phase gate tests.
6. Request final play test review.

## Phase Gate

1. `clj -M:spec`
2. `clj -M:parse-tests && clj -M:generate-specs && clj -M:spec generated-acceptance-specs/`
3. `clj -M:spec-structure-check`
4. Play test review requested and logged before moving to next phase.

## File-by-File State Execution Track

1. `src/empire/combat.cljc`
   - Replace direct mutation with pure transitions + effects.
2. `src/empire/containers/ops.cljc`
   - Convert load/disembark/launch to transform commands.
3. `src/empire/game_loop/round_setup.cljc`
   - Convert round-start routines to pure transforms.
4. `src/empire/computer/threat_response.cljc`
   - Split `detection`, `assignment`, `invasion-state`.
5. `src/empire/game_loop.cljc` and `src/empire/game_loop/item_processing.cljc`
   - Keep orchestration only, no direct domain writes.
6. `src/empire/player/commands.cljc`, `src/empire/player/orders.cljc`, `src/empire/ui/util/input/actions.cljc`
   - Emit commands into application boundary.

## Invariants and Safety Nets

- Run invariant checks after each command application:
  - container capacity/awake counts valid
  - escort references valid
  - city ownership/content consistency
  - no double occupancy
  - fuel/hits constraints
- Keep acceptance pipeline as primary behavioral regression net.

## Testing Strategy

- Domain unit specs: pure function tests for transitions and AI policy.
- Application specs: orchestration tests with fake ports.
- Adapter specs: atoms/UI/persistence translation tests.
- Acceptance tests: end-to-end behavior unchanged.
- Acceptance contract specs: generated code must compile/run against harness interfaces only.
- Harness conformance specs: application implementation satisfies harness contract semantics.

## Validation Protocol

- After each spec change, run `clj -M:spec-structure-check`.
- Before mutation checks, inspect file size and recommend splitting if file length is greater than 250 lines.
- For acceptance behavior after each migration slice:
  - `clj -M:parse-tests && clj -M:generate-specs && clj -M:spec generated-acceptance-specs/`
- For phase completion, use the full Phase Gate suite.

## Done Criteria

- Domain and application namespaces have no direct dependency on atoms/UI/filesystem.
- All domain mutation paths go through `application/state`.
- Generated acceptance specs depend only on acceptance harness interfaces.
- Application implements the acceptance harness interfaces used by generated specs.
- `acceptanceTests/*.txt` and `src/empire/acceptance/parser/*` remain unchanged throughout migration.
- Acceptance suite passes with no behavior regressions.
- Boundary checks are automated in CI/spec workflow.
