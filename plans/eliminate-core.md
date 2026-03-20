# Eliminate `empire.computer.core`

## Goal

Remove `empire.computer.core` as a compatibility facade without changing behavior, while keeping the `computer` namespace tree readable and testable throughout the transition.

The safe end state is:

- callers depend on the specific provider namespace they actually use
- `shared/*` does not depend on `core`
- top-level facades continue to expose stable entrypoints where that is useful
- `empire.computer.core` can be deleted without leaving hidden architectural gaps

## Why This Needs A Plan

`src/empire/computer/core.cljc` is now mostly a re-export layer over:

- `empire.computer.shared.grid`
- `empire.computer.shared.world-query`
- `empire.computer.shared.transport-query`
- `empire.computer.shared.action-resolution`

That is good progress, but `core` still creates two risks:

1. It hides dependency direction.
   A caller using `core/distance` looks the same as a caller using `core/attempt-conquest-computer`, even though those belong to very different layers.

2. It still allows back-edges.
   `src/empire/computer/shared/army_action_resolution.cljc` currently depends on `empire.computer.core`, which means part of `shared/` points back up into the facade.

The first risk is navigational. The second is architectural and should be fixed first.

## Current `core` Surface

Today `empire.computer.core` exposes these groups:

### Grid / spatial helpers

- `neighbor-offsets`
- `neighbors-in-map`
- `adjacent?`
- `distance`
- `chebyshev-distance`
- `move-toward`

### Visible-world queries

- `get-neighbors`
- `attackable-target?`
- `find-visible-cities`
- `find-visible-player-units`
- `adjacent-to-computer-unexplored?`

### Transport queries

- `find-loading-transport`
- `find-adjacent-loading-transport`

### Mutation / action helpers

- `stamp-territory`
- `move-unit-to`
- `random-away-direction`
- `find-wakeable-sentries`
- `wake-nearby-sentries`
- `board-transport`
- `attempt-conquest-computer`

## Migration Principles

### 1. Eliminate inward dependencies first

No namespace under `shared/` should require `empire.computer.core`.

Allowed direction:

- domain code -> `shared/*`
- facade -> `shared/*`

Not allowed:

- `shared/*` -> facade

### 2. Migrate by capability cluster, not by file count

The lowest-risk order is:

1. direct back-edge removal
2. pure spatial helpers
3. read-only world queries
4. transport queries
5. mutation helpers
6. final facade deletion

This keeps each step conceptually narrow.

### 3. Keep behavior stable while names move

For each slice:

- update production callers first
- update directly-related specs second
- keep the old `core` var as a temporary forwarding alias only if needed to limit blast radius
- delete the alias once all callers are migrated

Do not mix namespace elimination with semantic cleanup unless a dependency boundary forces it.

### 4. Prefer real homes over one new dumping ground

Do not replace `core` with another generic “utils” namespace.

Each function should end up in the namespace implied by its responsibility:

- geometry -> `shared.grid`
- visible map queries -> `shared.world-query`
- transport search -> `shared.transport-query`
- world mutation / resolution -> `shared.action-resolution`
- ship-specific behavior -> `ship.core`
- threat-response behavior -> `threat-response.core`

## Safe Execution Plan

### Phase 1: Remove the `shared -> core` back-edge

Target:

- `src/empire/computer/shared/army_action_resolution.cljc`

Current problem:

- it calls `core/attempt-conquest-computer`
- it calls `core/stamp-territory`

Safe fix:

- require `empire.computer.shared.action-resolution` directly
- call `action-resolution/attempt-conquest-computer`
- call the real provider for `stamp-territory`

Preferred outcome:

- if `stamp-territory` is truly generic action-resolution behavior, move or re-export it from `shared.action-resolution`
- otherwise point directly at its real home

Success criteria:

- no namespace under `src/empire/computer/shared/` requires `empire.computer.core`

This is the most important structural step because it restores the intended layering immediately.

### Phase 2: Migrate pure spatial callers

Move all callers of these functions off `core`:

- `distance`
- `chebyshev-distance`
- `neighbors-in-map`
- `adjacent?`
- `move-toward`

Primary destination:

- `empire.computer.shared.grid`

Exception:

- keep `move-toward` in `core` only temporarily if you want to avoid mixing a local helper move with the first caller migration
- once the first pass is stable, move `move-toward` into `shared.grid` or a very small `shared.targeting` namespace

Likely high-volume callers:

- `army/*`
- `fighter/*`
- `ship/*`
- `transport/*`
- `threat_response/*`
- `land_objectives`
- `early_game/strategy`

Why this phase is safe:

- the functions are mostly pure
- call-site rewrites are straightforward
- tests are localized

### Phase 3: Migrate visible-world query callers

Move callers of:

- `get-neighbors`
- `attackable-target?`
- `find-visible-cities`
- `find-visible-player-units`
- `adjacent-to-computer-unexplored?`

Primary destination:

- `empire.computer.shared.world-query`

Potential cleanup:

- rename `get-neighbors` to something visibility-aware if the current name hides that it reads `computer-map`

Why this phase should be separate:

- these helpers are read-only, but they are state-backed
- separating them from the pure spatial pass makes review easier

### Phase 4: Migrate transport query callers

Move callers of:

- `find-loading-transport`
- `find-adjacent-loading-transport`

Primary destination:

- `empire.computer.shared.transport-query`

Main callers:

- `army/transport`
- transport specs

This phase should be small and should leave no reason for army transport behavior to keep requiring `core`.

### Phase 5: Migrate mutation / action helpers

Move callers of:

- `move-unit-to`
- `stamp-territory`
- `random-away-direction`
- `find-wakeable-sentries`
- `wake-nearby-sentries`
- `board-transport`
- `attempt-conquest-computer`

Primary destination:

- `empire.computer.shared.action-resolution`

Why this phase comes later:

- these calls are higher-risk because they mutate state
- many specs use `with-redefs` against `core/*`
- moving them after the read-only phases keeps failures easier to diagnose

Review point during this phase:

- decide whether `random-away-direction` and wake helpers really belong in `action-resolution`
- if they are stateful but not resolution-oriented, give them a clearer shared home before the last pass

### Phase 6: Update specs away from `core`

A large fraction of current `core` references are spec-level redefs and direct unit tests.

Strategy:

- split the current `core` specs by real provider
- move examples to:
  - `shared.grid` specs
  - `shared.world-query` specs
  - `shared.transport-query` specs
  - `shared.action-resolution` specs

Examples:

- `core_spatial_spec` should stop being a mixed bag and become provider-specific specs
- `random_away_spec`, `move_unit_to_spec`, `board_transport_spec`, `attempt_conquest_spec`, and loading-transport specs should target their real homes directly

This reduces the chance that deleting `core` leaves behind a misleading “legacy facade spec” that still anchors old structure.

### Phase 7: Shrink `core` to zero

At this point:

- remove all remaining production requires of `empire.computer.core`
- remove all remaining spec requires of `empire.computer.core`
- verify `rg -n "\\[empire\\.computer\\.core :as core\\]" src spec` is empty

Then:

- delete `src/empire/computer/core.cljc`

If a temporary deprecation stage is wanted, keep a one-release compatibility facade only after all internal code is migrated. But the cleaner result here is full removal.

## Suggested Slice Order

1. `shared/army_action_resolution.cljc`
2. `army/transport.cljc` and related transport query specs
3. pure spatial helpers in `army/*`, `fighter/*`, `land_objectives`, `early_game/*`
4. ship code that already has `ship.core` and should stop leaning on generic `core`
5. threat-response modules
6. mutation helper specs
7. delete `core.cljc`

Rationale:

- step 1 fixes the architectural bug
- steps 2 and 3 remove the easiest dependencies first
- ship and threat-response have richer internal facades and deserve focused passes

## Special Attention Areas

### Ship code

`ship.cljc` and `ship/*` already have a meaningful domain facade in `empire.computer.ship.core`.

That means ship code should mostly stop depending on generic `core` and instead use:

- `ship.core` for naval behavior
- `shared.grid` for distance math
- `shared.world-query` only when it really needs visible-map queries
- `shared.action-resolution` only for generic mutation helpers

This is a good candidate for a dedicated cleanup slice because it improves discoverability immediately.

### Threat response

`threat-response` already has its own internal `core`.

Avoid replacing `empire.computer.core` with more imports from `threat-response.core` in unrelated namespaces. Keep those boundaries explicit.

### `move-toward`

This helper is small but heavily used.

Decide early whether it lives in:

- `shared.grid`
- or a small `shared.targeting`

Do not leave it stranded as the last reason `core` exists.

## Verification

After each phase:

1. Run the changed specs.
2. Run `clj -M:spec`.
3. Run `clj -M:all-tests` at the end of a meaningful slice.
4. Re-run `clj -M:check-dependencies`.
5. Check for remaining callers with:

```bash
rg -n "\[empire\.computer\.core :as core\]" src spec
```

For the architectural checkpoint after Phase 1, also verify:

```bash
rg -n "\[empire\.computer\.core :as core\]" src/empire/computer/shared
```

and expect no matches.

## Definition Of Done

The plan is complete when all of the following are true:

- `src/empire/computer/core.cljc` is deleted
- no production namespace requires `empire.computer.core`
- no spec namespace requires `empire.computer.core`
- the former `core` behaviors are covered by provider-specific specs
- `shared/*` only depends downward or sideways, never back into a facade
- `clj -M:all-tests` passes
