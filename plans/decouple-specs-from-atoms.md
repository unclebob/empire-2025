# Decouple Specs From Atoms

## Status
- Completed on 2026-03-03.
- Boundary enforcement is active:
  - direct atom manipulation blocked in `spec/**`
  - direct `empire.atoms` requires blocked in `spec/**` except `spec/empire/atoms_spec.clj`
- Validation passed: `speclj-structure-check`, `clj -M:spec`, `clj -M:all-tests`.

## Goal
Remove direct atom manipulation from specs so tests depend on test/runtime boundaries instead of `empire.atoms`.

## Scope Snapshot
- Spec files currently requiring `empire.atoms`: 88
- Largest clusters:
  - `spec/empire/computer`: 39
  - `spec/empire/movement`: 19
  - `spec/empire/ui`: 6
  - `spec/empire/player`: 5

## Phased Plan

### Phase 0: Safety Rails
- Add a spec-only boundary check script that reports direct atom access in `spec/**`:
  - `@atoms/...`
  - `reset! atoms/...`
  - `swap! atoms/...`
- Start in report mode first, then convert to fail mode at the end.
- Use a temporary explicit allowlist for files not yet migrated.

### Phase 1: Test Helper API First
- Expand `empire.test-utils` so specs can read/write state without touching atoms:
  - `read-world`, `write-world!`, `update-world!`
  - `read-player-map`, `write-player-map!`, `update-player-map!`
  - `read-computer-map`, `write-computer-map!`, `update-computer-map!`
  - `set-runtime!`, `update-runtime!`, `read-runtime`
- Migrate easy wins first:
  - Remove stale `empire.atoms` requires in specs that do not use them.

### Phase 2: Small/Local Specs
- Migrate low-risk groups first:
  - `spec/empire/units` (1)
  - small `spec/empire/game_loop` files
  - `spec/empire/player` (5)
  - `spec/empire/ui` (6)
- Keep slices small and behavior-preserving.

### Phase 3: Movement Specs
- Migrate `spec/empire/movement` in sub-batches:
  - pathfinding + bfs
  - movement execution/helpers
  - coastline/explore/visibility
- Add helper API only when repetition proves it is needed.

### Phase 4: Computer Specs (Largest Block)
- Migrate `spec/empire/computer` by feature cluster:
  - fighter/patrol/ship
  - transport/invasion
  - army/coastal/territory
  - production/threat/land objectives
- Keep commits narrow and test-focused.

### Phase 5: Cross-Cutting + Cleanup
- Migrate remaining top-level specs (`combat*`, `map`, `init`, `repair`, `save_load`, etc.).
- Remove remaining `empire.atoms` requires from specs.
- Flip spec boundary check from report mode to fail mode.

### Phase 6: Harden + Docs
- Run full pipeline and confirm all tests pass.
- Update docs/readme guidance: new specs must use test/runtime helpers, not atoms.
- Keep a thin compatibility shim in `test_utils` only if still needed.

## Execution Cadence Per Slice
1. Migrate 3-8 spec files.
2. Run `speclj-structure-check`.
3. Run targeted specs for changed namespaces.
4. Run full specs.
5. Commit.

## Success Criteria
- No direct atom manipulation in `spec/**`.
- No `empire.atoms` require in specs unless explicitly approved as transitional.
- Boundary check enforces the rule in fail mode.
