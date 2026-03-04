# Zero-Distance Execution Plan

## Goal
Drive architectural distance down by separating abstract and concrete components, removing direct adapter coupling, and moving implementation details behind explicit boundaries while keeping tests green.
Constraint: no artificial/fake dependencies to manipulate metrics.

## Phase 1: Repartition Metrics (Config-First)
1. Split `:domain-model` into:
   - `:domain-model-abstract` (interfaces/dispatch-only modules)
   - `:domain-model-concrete` (concrete logic and data modules)
2. Split `:state-adapters` into:
   - `:state-adapters-port-impl` (adapter implementations)
   - `:state-adapters-legacy` (legacy atom namespaces)
3. Update dependency rules so current architecture constraints remain valid.

## Phase 2: Remove Remaining Direct Legacy Couplings
1. Replace static dependencies on legacy state adapters/atoms from non-adapter modules with app/runtime/world ports.
2. Keep temporary compatibility paths via runtime indirection where necessary.

## Phase 3: Move Concrete Implementations Out of Abstract Modules
1. Keep `defmulti`/protocol declarations in abstract namespaces.
2. Move concrete `defmethod` implementations into `*.impl*` namespaces.
3. Ensure implementation namespaces are loaded through initialization wiring.

## Phase 4: Normalize Test Support Boundary
1. Keep `test-support` dependent on stable public boundaries only.
2. Eliminate static coupling to legacy state implementation details.

## Execution Loop
For each slice:
1. Make one coherent change.
2. Run `clj -M:all-tests-fast`.
3. Run `clj -M:check-dependencies dependency-tool.edn --max-distance 0.0`.
4. Record metric deltas and continue.
