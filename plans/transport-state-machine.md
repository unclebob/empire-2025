# Transport State Machine

## Goal

Replace the current transport mission logic with a smaller state machine that is driven by load state and adjacent coast classification.

This state machine replaces the normal transport missions only.
The invasion-specific missions and flow remain in place as they are.

## States

- `loading`
- `sail-to-unload`
- `unloading`
- `sail-to-load`

## Transition Table

Schema:

`current-state | event | next-state | action`

```text
loading | not-loaded | loading | crawl-pickup
loading | loaded | sail-to-unload | set-unload-target

sail-to-unload | claimed-land | sail-to-unload | set-unload-target
sail-to-unload | unclaimed-land | unloading | unload
sail-to-unload | unexplored-target | sail-to-unload | set-unload-target

unloading | not-unloaded | unloading | crawl-unload
unloading | unloaded | sail-to-load | set-load-target

sail-to-load | claimed-land | loading | crawl-pickup
```

## Events

- `not-loaded`: fewer than 4 armies aboard
- `loaded`: 4 or more armies aboard
- `claimed-land`: transport is adjacent to claimed land
- `unclaimed-land`: transport is adjacent to unclaimed land
- `unexplored-target`: transport reached its target and there is no adjacent land
- `not-unloaded`: more than 0 armies aboard
- `unloaded`: no armies aboard

## Actions

- `crawl-pickup`
  - Crawl the coast without backtracking.
  - Pick up adjacent armies while crawling.

- `set-unload-target`
  - Set BFS path to the nearest reachable unclaimed land.
  - If none exists, set BFS path to the nearest reachable unexplored cell.

- `unload`
  - Unload one army into each adjacent unclaimed land cell.

- `crawl-unload`
  - Crawl the coast without backtracking.
  - Unload armies on every adjacent land cell while crawling.
  - If no crawl move exists, clear backtrack memory and try the crawl again.

- `set-load-target`
  - Set BFS path to the nearest claimed land.

## Land Classification

- Claimed land:
  - land with a `country-id`

- Unclaimed land:
  - land without a `country-id`
  - free cities
  - computer cities

- Player cities:
  - neither claimed nor unclaimed land for this state machine

## Clarifications

- In `sail-to-unload`, adjacent claimed land is not a stop condition.
  - The transport stays in `sail-to-unload` and retargets.

- In `sail-to-load`, adjacency to claimed land is enough to switch back to `loading`.

- The random fallback from `crawl-unload` is required to avoid transports stalling when they still have armies but cannot continue the unload crawl.

## Sanity Check

The proposed state machine is sound, but several boundary conditions need to be explicit so the rewrite does not silently reintroduce the current transport bugs.

- The four-state split is appropriate for normal transport behavior.
  - It cleanly replaces the current normal mission mix of `:loading`, `:sailing`, and `:unloading`.
  - It should not absorb `:invading`, `:find-armies-for-invasion`, `:load-for-invasion`, or `:land-locked`.

- `sail-to-load` needs one additional fallback rule.
  - The current draft only specifies `claimed-land -> loading`.
  - If `set-load-target` cannot find reachable claimed land, that should be treated as impossible and surfaced as a hard failure in code/tests.
  - Empty sailing transports with no load target should not silently drift.

- `sail-to-unload` already has a practical no-target rule.
  - If no unclaimed land is reachable, it should target the nearest reachable unexplored cell.
  - If neither exists, it should remain in `sail-to-unload` with an empty path rather than crash.

- `crawl-unload` must preserve non-backtracking state.
  - The current crawl logic depends on `:crawl-history`.
  - The rewrite must keep that state and sync it into `computer-map`.

- `loading` still needs the current pickup safeguards.
  - `crawl-pickup` must continue to auto-load adjacent armies.
  - The current stale-loading escape hatch and `:loading-since` timestamp should be removed.
  - A transport that never receives the `loaded` event remains in `loading`.

- Land classification is intentionally asymmetric.
  - Claimed land is only land with a `country-id`.
  - Computer cities are unload targets, but not reload targets.
  - Player cities are excluded from both categories for this state machine.

- Arrival in `sail-to-load` is adjacency-based.
  - The transport does not need to reach a specific marked coast cell.
  - As soon as it is adjacent to claimed land, it switches to `loading`.

- The new state names should either replace stored mission values or map onto them explicitly.
  - If mission values remain persisted, the rename/migration needs to be handled deliberately.

## Implementation Notes

- Map the existing normal transport missions onto these four states and remove no-longer-needed normal mission variants.
- Do not change the invasion-specific missions or invasion flow as part of this work.
- Keep AI reads on `computer-map`.
- Any transport self-state needed by the AI must be synced into `computer-map`.
- Update acceptance and unit specs to reflect the new state transitions without changing scenario intent unless explicitly approved.

## Implementation Plan

1. Freeze the state vocabulary.
   - Introduce the four normal mission values: `:loading`, `:sail-to-unload`, `:unloading`, `:sail-to-load`.
   - Keep invasion and lake-handling missions untouched.
   - Add one normalization function that maps old normal missions to the new ones during the transition.

2. Centralize normal mission dispatch.
   - Replace the normal branch in [transport_process_decisions.cljc](/Users/unclebob/projects/clojure/empire/empire-2025/src/empire/computer/transport_process_decisions.cljc) and [transport.cljc](/Users/unclebob/projects/clojure/empire/empire-2025/src/empire/computer/transport.cljc) so only the four normal states dispatch into the new handlers.
   - Leave invasion dispatch as-is.

3. Extract shared coast classification helpers.
   - Add helpers for `claimed-land`, `unclaimed-land`, and `unexplored-target` using `computer-map`.
   - Put them near the current sailing/unloading support code so all normal missions classify coast the same way.
   - Make the `player city is neither` rule explicit in code and tests.

4. Rebuild `loading` around `crawl-pickup`.
   - Keep `load-adjacent-armies` and `:crawl-history`.
   - Remove `:loading-since`, stale-loading logic, and timeout-based escape behavior.
   - Keep the existing pickup-continent return logic only if it still serves the new `set-load-target` behavior.
   - Transition to `:sail-to-unload` when the load threshold is reached.

5. Replace current sailing logic with two explicit sail states.
   - Move loaded transport pathing into `:sail-to-unload`.
   - Move empty transport return pathing into `:sail-to-load`.
   - Split current `compute-sail-path` into two path builders:
     - nearest reachable unclaimed land, else nearest reachable unexplored cell
     - nearest reachable claimed land
   - Keep launch-from-city behavior, path following, and computer-map sync.

6. Rebuild `unloading` around adjacent unclaimed coast.
   - Unload only onto adjacent unclaimed land.
   - If armies remain, continue `crawl-unload`.
   - If crawl cannot continue, clear backtrack memory and retry `crawl-unload`.
   - When armies reach zero, transition to `:sail-to-load` and immediately compute the load target.

7. Remove obsolete normal-transport concepts.
   - Delete normal-city targeting for transports.
   - Remove or simplify any normal-flow code that depends on `:pickup-country-id`, `:unload-target-city`, or old `:sailing` semantics if no longer needed.
   - Keep any fields still needed by invasion logic.

8. Update tests in layers.
   - Add unit specs for the pure state transitions first.
   - Add module specs for each action family: load crawl, unload crawl, unload targeting, load targeting.
   - Then run the full acceptance pipeline and adjust harness/spec setup as needed without changing `.txt` scenarios unless explicitly approved.

9. Verify persistence and debug output.
   - Confirm saves and debug dumps remain readable with the new mission names.
   - If old saves matter, add mission normalization on load.
