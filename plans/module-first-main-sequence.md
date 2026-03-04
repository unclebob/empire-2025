# Module-First Main-Sequence Plan

## Objective

Drive architecture quality bottom-up:

1. Break analysis down to individual namespaces (modules).
2. Push each module toward main-sequence behavior (`D = |1 - (A + I)|`), splitting into abstract/concrete pairs only when real indirection exists.
3. Recompose modules into higher-level cohesive components.
4. Reconcile and simplify abstractions inside components after recomposition.

## Guardrails

- No fake abstractions.
- Abstractness only from real indirection (`defprotocol`, `defmulti`, injected behavior).
- Keep composition-root wiring in concrete bootstrap/adapters.
- Keep tests green while slicing; if temporary regressions appear, fix within the same slice.

## Phase 0: Baseline and Ranking

1. Export dependency report:
   - `clj -M:check-dependencies dependency-tool.edn --format edn`
2. Rank modules by module-level `D` (derived from namespace fan-in/fan-out and module abstractness).
3. Exclude architecture-tooling and acceptance parser/generator modules from early slices.

Deliverable:
- Ranked module list in working notes for execution ordering.
- Initial ranked sample (non-tooling): `empire.config`, `empire.containers.helpers`,
  `empire.movement.map-utils`, `empire.computer.movement`, `empire.movement.visibility`,
  `empire.adapters.state.atoms`.

## Phase 1: Remove Abstract->Concrete Leakage at Module Level

1. Identify abstract namespaces that load concrete implementations directly.
2. Move that loading to explicit composition roots (`bootstrap` namespaces).
3. Keep abstract modules dispatch-only.

First slice executed:
- Added `empire.application.bootstrap`.
- Removed concrete loading from:
  - `empire.application.runtime`
  - `empire.application.state`
- Wired bootstrap through `empire.movement.bootstrap`.
- Result: dependency check now reports `Kind violations: 0`, `Warnings: 0`.

## Phase 2: Split Mixed Modules into Abstract/Concrete Pairs

For each high-distance mixed module:

1. Extract interface/dispatch contract into abstract module.
2. Move logic/stateful behavior into concrete module.
3. Wire implementation in bootstrap/composition root.
4. Re-run dependency checker and relevant specs.

Execution order: highest `D` first, constrained by dependency cycles.

## Phase 3: Reassemble Into Cohesive Components

1. Group stabilized modules into cohesive components in `dependency-tool.edn`.
2. Remove temporary split artifacts that no longer add value.
3. Unify abstractions inside components where one contract is enough.

## Phase 4: Tighten Gates

1. Run:
   - `clj -M:check-dependencies dependency-tool.edn`
   - `clj -M:all-tests-fast`
2. Then full:
   - `clj -M:all-tests`
3. Keep warnings at zero and reduce kind/cycle/distance violations slice-by-slice.

## Success Criteria

- Dynamic lookup warnings: `0`.
- Abstract modules no longer load concrete impls.
- Kind violations and cycles trend downward each slice.
- Component map reflects real cohesive boundaries after module-level cleanup.
