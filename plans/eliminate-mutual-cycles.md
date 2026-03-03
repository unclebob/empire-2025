# Eliminate Mutual Cycles

## Status (2026-03-03)
- Completed.
- Removed mutual/component back-edges:
  - `movement -> computer`
  - `computer -> player`
  - `player -> game-loop`
  - `game-loop -> ui`
  - `debug -> ui`
  - `combat -> movement`
  - `containers -> movement`
  - `containers -> player`
  - `domain -> containers`
  - `application -> adapters`
- Current dependency-check result:
  - `Cycles: 0`

## Baseline Mutual Pairs (raw analyzer)
- `:adapters <-> :application`
- `:adapters <-> :game-loop`
- `:adapters <-> :test-utils`
- `:combat <-> :movement`
- `:computer <-> :movement`
- `:computer <-> :player`
- `:containers <-> :domain`
- `:containers <-> :movement`
- `:containers <-> :player`
- `:debug <-> :ui`
- `:game-loop <-> :player`
- `:game-loop <-> :ui`

## Remaining Mutual Pairs
- None

## Goal
Break architectural cycles by removing all mutual dependency pairs first, then clean up remaining indirect back-edges.

## Scope
- Component-level dependencies from `clj -M:check-dependencies dependency-tool.edn`
- Priority focus:
  1. `computer <-> game-loop`
  2. movement-related back-edges
  3. ui/core boundary back-edges
  4. init/save-load orchestration back-edges

## Constraints
- No behavior changes.
- Keep acceptance scenarios/parser unchanged.
- All unit + acceptance tests must pass after each phase.
- Introduce boundary rules only after code is compliant (avoid red baseline).

## Phase 0: Baseline + Pair Inventory
1. Run:
   - `clj -M:speclj-structure-check`
   - `clj -M:all-tests`
   - `clj -M:check-dependencies dependency-tool.edn`
2. Enumerate mutual pairs (`A -> B` and `B -> A`) from current report.
3. Record expected target direction for each pair.

Exit criteria:
- Baseline green.
- Mutual-pair inventory committed in this plan.

## Phase 1: `computer <-> game-loop`
Note:
- Current analyzer data shows `:game-loop -> :computer` but not `:computer -> :game-loop`.
- Treat this phase as verification + guardrail, not refactor.
1. Introduce a small orchestration port (protocol):
   - turn callbacks/services computer needs from game-loop.
2. Move game-loop-dependent behavior behind port implementation in orchestration layer.
3. Refactor computer code to depend on injected port/context only.
4. Remove direct `computer -> game-loop` dependency.

Validation:
- unit specs
- acceptance pipeline
- dependency check confirms no `computer -> game-loop`.

## Phase 2: Movement Mutual Pairs
1. Resolve mutual pairs involving `movement` with:
   - `computer`
   - `player`
   - `combat`
2. Extract shared decision primitives into neutral domain modules where needed.
3. Inject services via ports/context instead of cross-calls.

Validation:
- targeted movement/computer/player/combat specs
- full `all-tests`
- dependency check shows removed mutual edges.

## Phase 3: UI/Core Mutual Pairs
1. Enforce one-way dependency:
   - core does not depend on `ui`.
2. Move UI-triggered effects to adapters or callbacks.
3. Keep core output as data/events consumed by UI layer.

Validation:
- UI + game-loop + player specs
- full `all-tests`
- dependency check confirms no core->ui back-edges.

## Phase 4: Init/Save-Load Orchestration Pairs
1. Constrain `init` and `save-load` to orchestration/adapters.
2. Remove core dependencies that point back into orchestration.
3. Keep save/load schema compatibility and migration behavior intact.

Validation:
- save/load and init specs
- full `all-tests`
- dependency check confirms broken orchestration back-edges.

## Phase 5: Tighten Dependency Rules
1. Add forbidden dependency rules for each pair removed in phases 1-4.
2. Keep exceptions minimal and documented.
3. Re-run dependency analysis and ensure zero violations.

Validation:
- `clj -M:check-dependencies dependency-tool.edn`
- `clj -M:all-tests`

## Phase 6: Remaining Indirect Cycles
1. Analyze any remaining SCCs after mutual pair elimination.
2. Break residual long loops by moving shared logic inward (`domain`/`application`) and keeping adapters outward.
3. Add final forbidden rules for new boundaries.

Exit criteria:
- No large SCC across core gameplay components.
- Dependency graph materially closer to layered direction.
- Tests and acceptance pipeline green.

## Execution Slice Template
1. Implement one pair-direction change.
2. Run targeted specs.
3. Run `clj -M:all-tests`.
4. Run `clj -M:check-dependencies dependency-tool.edn`.
5. Commit.
