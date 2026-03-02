# Load Transports for Invasion

## Goal
Ensure major invasion behavior prioritizes loading armies into transports before sailing, while keeping test discipline strict and incremental.

## Non-Negotiable TDD Rules
1. Follow the three laws of TDD at all times.
2. Write acceptance tests first.
3. New invasion acceptance tests are specification tests and may remain failing until the described behavior is fully implemented.
4. Do not add minimal/shortcut production code solely to force early acceptance-test green.

## Test Execution Policy During Phases
1. Add new invasion acceptance tests first and confirm they fail (RED).
2. During each implementation phase:
- Keep full regression green excluding the new invasion acceptance tests (temporarily pending/ignored or excluded).
- Verify expected failures are only the new invasion acceptance tests.
3. Final phase:
- Re-enable all invasion acceptance tests.
- Complete implementation until all invasion acceptance tests pass.
- Final gate: all unit tests and all acceptance tests pass with zero ignored invasion tests.

## Precondition
1. Remove existing profiling instrumentation/switches before invasion-state implementation.
2. Preserve gameplay behavior while removing profiling (add/adjust tests as needed).

## Behavior Plan
1. Add transport invasion states:
- `:find-armies-for-invasion`
- `:load-for-invasion`
- `:invasion-sail`

2. Add army embarkation state:
- `:move-to-coast-for-invasion`

3. Major invasion entry behavior:
- Empty transports at sea -> `:find-armies-for-invasion`
- Transports with armies -> `:invasion-sail`
- Non-coastal unloaded armies -> `:move-to-coast-for-invasion`

4. Empty transport behavior:
- In `:find-armies-for-invasion`, sail toward nearest coastal loading point with armies.
- Transition to `:load-for-invasion` only when transport reaches coastal loading point and can load.

5. Loading behavior and timeout:
- Start 5-round timer when transport enters `:load-for-invasion`.
- Attempt loading throughout timer window.
- At timeout:
- If `army-count >= 1`, transition to `:invasion-sail`.
- If `army-count = 0`, do not join invasion; return to normal transport behavior.

6. Army coast-seeking behavior:
- On entry to `:move-to-coast-for-invasion`, run one land-only BFS and cache `:coast-target`.
- Move toward cached target without full BFS recomputation each round.
- If army reaches coastal target, wait to be picked up; no further movement.
- If not yet coastal and blocked, run only local land-only BFS radius 2 for nearby empty coastal cell.
- If no local coastal opening exists, hold staging position and retry local search later.

7. Path constraints:
- Army routing BFS is land-only.
- Army targets are coastal land cells (land adjacent to sea).
- Transports remain sea-routed.

## Acceptance Tests to Write First
1. Major invasion assigns inland armies to coast-embark state.
2. Inland armies move to coast and wait on arrival.
3. Empty transport at sea enters `find-armies-for-invasion` and sails to loading coast.
4. Transport enters `load-for-invasion` only when at valid coastal loading point.
5. `load-for-invasion` timeout starts on entry and expires after 5 rounds.
6. Timeout with zero armies: transport does not join invasion.
7. Timeout with one or more armies: transport joins invasion.
8. Coast congestion: armies stage nearby and advance as coast cells open.
9. Blocked army retarget uses local radius-2 search behavior.
10. Land-only army routing while heading to coast (never traverses sea).

## Unit Test Focus
1. State transitions and mission flags for new transport/army states.
2. 5-round timeout accounting from entry round.
3. Cached army target persistence and non-recomputation across rounds.
4. Radius-2 fallback target update when blocked.
5. Empty-transport timeout exit to normal behavior.
6. Partial-load timeout transition to invasion sailing.
