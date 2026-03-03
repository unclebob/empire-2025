# Repartition Atoms

## Goal
Replace the current many-atom model with a small bounded-context partition that reduces coupling and preserves runtime performance.

## Target Partition
1. `world-state` atom
   - `:game-map`, `:player-map`, `:computer-map`
2. `runtime-state` atom
   - turn flow, input/UI ephemeral state, messages, menu state, counters/ids
3. `ai-state` atom
   - computer planning/caches (claims, coastal/lake intel, invasion state, AI stats)
4. `telemetry-state` atom (optional)
   - debug/action/movement logs and profiling data

## Constraints
- No behavior changes during repartition.
- All existing unit tests and acceptance pipeline must pass after each phase.
- Changes must flow through adapter boundaries (`adapters/state/*`), not direct call-site rewrites first.
- Preserve save/load compatibility across in-flight refactors.

## Phase 0: Baseline + Guardrails
1. Freeze current behavior:
   - run `clj -M:speclj-structure-check`
   - run `clj -M:all-tests`
2. Record current runtime keys and ownership in a migration table.
3. Add/confirm boundary checks that prevent new direct `empire.atoms` dependencies outside adapter files.

Exit criteria:
- Baseline green and key inventory documented.

## Phase 1: Introduce Partitioned Backing Atoms (No Call-Site Change)
1. Add new backing atoms namespace (or adapter-local backing):
   - `world-state*`, `runtime-state*`, `ai-state*`, `telemetry-state*`
2. Initialize them from legacy atom values at startup.
3. Keep legacy atoms as compatibility view/wrappers for now.

Exit criteria:
- No caller changes required yet.
- Tests remain green.

## Phase 2: Move Runtime Adapter to Partitioned Store
1. Update `adapters/state/runtime` read/write paths to target partitioned atoms.
2. Keep key-level API unchanged (`read-runtime-state`, `write-runtime-state!`).
3. Retain compatibility sync for any legacy atom readers still alive.

Exit criteria:
- Runtime adapter no longer depends on per-field legacy atom vars for normal reads/writes.
- Unit + acceptance tests pass.

## Phase 3: Move World Adapter to `world-state`
1. Update world load/save/update adapter to use `world-state` partition.
2. Ensure map updates remain efficient (avoid full-map copies unless required).
3. Verify game-loop and rendering paths through adapter APIs.

Exit criteria:
- World paths run via partitioned store.
- No frame-rate regression in smoke run.

## Phase 4: Move AI Caches/Planning to `ai-state`
1. Route invasion/coastal/claims/country stats and related AI caches through `ai-state`.
2. Remove AI references to legacy standalone atoms.
3. Keep migration shims for any leftover readers until cleanup.

Exit criteria:
- AI state reads/writes are adapter-mediated and partition-backed.
- Acceptance tests covering AI behaviors remain green.

## Phase 5: Save/Load Schema Stabilization
1. Keep persisted key schema stable at first (backward compatible writer/reader).
2. Add migration read path for old saves if internal structure changes.
3. Add round-trip tests for save/load across versions.

Exit criteria:
- Existing saves load correctly.
- New saves reload with no data loss.

## Phase 6: Remove Legacy Atom Surface
1. Remove compatibility sync/shims.
2. Remove obsolete atom defs from `atoms.cljc` (or reduce file to thin facade only if still required by public API).
3. Tighten boundary checks to fail any new direct dependency on removed surface.

Exit criteria:
- Only partitioned atoms remain as storage mechanism.
- `src` direct `empire.atoms` dependencies limited to explicit compatibility entry points (or zero).

## Phase 7: Hardening + Performance
1. Run full validation:
   - `clj -M:speclj-structure-check`
   - `clj -M:spec`
   - `clj -M:all-tests`
2. Run game smoke tests on fixed seeds and compare:
   - turn throughput
   - frame responsiveness
   - memory/GC symptoms
3. Document final state model and adapter contracts.

Exit criteria:
- Functional parity confirmed.
- No material performance regression.

## Suggested Slice Size
- 1 partition concern per slice (small PR/commit).
- Validate all tests each slice.
- Prefer additive changes first, deletions last.

