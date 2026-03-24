# Performance Improvements

Note:
- Record every optimization made during this performance investigation in this file.
- The profiling and instrumentation added for the investigation is expected to be backed out later.
- The actual performance improvements are expected to remain, so this file is the durable record of what should be kept.

## 2026-03-24

### Fighter arrival sortie replanning

Change:
- Cached nearest-unexplored distance per site during fighter sortie planning.
- Reused a ranked city list across `choose-exploration-plan`, `max-sortie-plan`, and landing-site selection.

Files:
- `src/empire/computer/fighter/flight_decisions.cljc`

Measurement:
- Command: `clj -M:run --headless=220 --slow-round-analysis=500:20`
- Before optimization, seed `1774361452683` showed:
  - `process-computer/fighter-arrival-launch-exploration` avg `211.2 ms`
  - `process-computer/fighter-arrival-max-sortie-plan` avg `210.8 ms`
- After optimization, seed `1774361658123` showed:
  - `process-computer/fighter-arrival-launch-exploration` avg `107.0 ms`

Result:
- Fighter arrival replanning cost was roughly cut in half.
- The dominant hotspot shifted away from fighter arrival planning and toward city processing.

### City production count caching

Change:
- Added finer city production profiling phases.
- Cached computer unit, city, and fighter counts against the current `computer-map` snapshot.
- Reused those cached counts during city production instead of rescanning the whole map for every city decision.
- Cache bypasses itself automatically when `computer-map` changes.

Files:
- `src/empire/computer/production/decisions.cljc`
- `src/empire/computer/production/stats.cljc`
- `src/empire/state/computer.cljc`

Measurement:
- Command: `clj -M:run --headless=220 --slow-round-analysis=500:20 --seed=1774361658123`

Before caching:
- Round `151` analysis window:
  - `process-computer/city` avg `316.8 ms`
  - `process-computer/city-process-production` avg `270.4 ms`
- Round `172` analysis window:
  - `process-computer/city` avg `460.5 ms`
  - `process-computer/city-process-production` avg `379.2 ms`
- Round `193` analysis window:
  - `process-computer/city` avg `666.0 ms`
  - `process-computer/city-process-production` avg `541.5 ms`

After caching:
- Round `151` analysis window:
  - `process-computer/city` avg `250.0 ms`
  - `process-computer/city-process-production` avg `212.4 ms`
- Round `172` analysis window:
  - `process-computer/city` avg `368.7 ms`
  - `process-computer/city-process-production` avg `284.5 ms`
- Round `193` analysis window:
  - `process-computer/city` avg `360.8 ms`
  - `process-computer/city-process-production` avg `292.3 ms`

Result:
- City processing dropped by about `21%` at round `151`, `20%` at round `172`, and `46%` at round `193`.
- City production remains expensive in late rounds, but its share of `process-computer` is materially lower than before this cache.
- In the late-game windows, army processing is now often the largest cost center.

### Army transport staging cheap-step

Change:
- Armies moving in `:move-to-coast-for-transport` now try a cheap greedy step toward their stable staging target before falling back to full objective pathing.
- This reuses the existing coastal cheap-step logic and keeps the old pathfinding as the fallback when a greedy move is not available.

Files:
- `src/empire/computer/army/coastal.cljc`

Measurement:
- Command: `clj -M:run --headless=220 --slow-round-analysis=500:20 --seed=1774361658123`
- Before optimization, late windows showed:
  - `process-computer/army-move-to-coast-for-transport` avg `331.3 ms`
  - later `560.3 ms`
  - later `863.7 ms`
- After optimization, comparable later windows showed:
  - `process-computer/army-move-to-coast-for-transport` avg `227.0 ms`
  - later `449.5 ms`
  - later `407.4 ms`

Result:
- Transport-staging army movement dropped materially in every measured late window.
- The late-game hotspot is no longer just transport staging; invasion staging and threat-response now compete with it.

### Invasion coast-target recompute avoidance

Change:
- Armies moving in `:move-to-coast-for-invasion` no longer recompute a coast target when they already have a cached `:coast-target`.
- Coast-target selection now only calls `find-coast-target-once` when the cached target is missing.

Files:
- `src/empire/computer/army/coastal_invasion.cljc`

Result:
- This removes redundant invasion target BFS work on rounds where armies are already following a valid cached coast target.
- The change is behavior-preserving for cached targets and keeps the old computation path as the fallback when no cached target exists.

### Army transport staging local sidestep fallback

Change:
- Added a second cheap fallback for `:move-to-coast-for-transport` armies.
- After the direct distance-reducing greedy step, transport staging now tries the same local empty-neighbor choice that army objective movement uses before falling back to full pathfinding.

Files:
- `src/empire/computer/army/movement.cljc`
- `src/empire/computer/army/coastal.cljc`

Measurement:
- Command: `clj -M:run --headless=220 --slow-round-analysis=500:20 --seed=1774361658123`
- Before this change, transport-staging windows showed:
  - `process-computer/army-move-to-coast-for-transport` avg `279.8 ms`
  - later `440.3 ms`
- After this change, comparable windows showed:
  - `process-computer/army-move-to-coast-for-transport` avg `149.9 ms`
  - later `169.6 ms`

Result:
- Transport-staging pathing dropped substantially again.
- In the latest late-game windows, the main hotspots shifted to broader army work and `start-new-round/threat-response` rather than transport-staging pathing alone.

### Threat-response duplicate army-target refresh removal

Change:
- Removed a redundant `kamikazee/refresh-army-targets!` call from active major-invasion round-start processing.
- Active threat-response refresh now updates kamikazee army targets only once, inside `refresh-major-invasion-assignments!`, instead of once before assignments and again during assignments.

Files:
- `src/empire/computer/threat_response/major_invasion_manager.cljc`

Verification:
- Added a regression proving active round start calls `refresh-army-targets!` exactly once.

Result:
- This removes one guaranteed full kamikazee target refresh from every active major-invasion round.
- Follow-up profiling shows the remaining threat-response hotspot is no longer army-target refresh itself. The dominant subphase is now transport assignment inside `refresh-active-assignments`, for example:
  - `start-new-round/threat-response-refresh-active-assignments-transports` avg `340.7 ms` in the final analyzed window of `clj -M:run --headless=220 --slow-round-analysis=500:20 --seed=1774361658123`

### Threat-response transport assignment no-op write elimination

Change:
- Stopped rewriting transport major-invasion state when the transport already has the current `:major-invasion-target`.
- Stopped re-marking transports as `:find-armies-for-invasion` when they are already in that mission.
- Stopped syncing AI visibility for those unchanged transport assignments.

Files:
- `src/empire/computer/threat_response/major_invasion.cljc`

Verification:
- Added regressions proving `prepare-transport-major-invasion!` does not write or sync for:
  - an already-current invasion target
  - an already-marked `:find-armies-for-invasion` transport

Measurement:
- Command: `clj -M:run --headless=220 --slow-round-analysis=500:20 --seed=1774361658123`
- Before optimization, a late active-invasion window showed:
  - `start-new-round/threat-response-refresh-active-assignments-transports` avg `340.7 ms`
- After optimization, a comparable active-invasion window showed:
  - `start-new-round/threat-response-refresh-active-assignments-transports` avg `170.8 ms`

Result:
- Transport assignment inside threat-response was roughly cut in half.
- The remaining late-game hotspots are broader army processing and the remaining non-transport threat-response assignment work.

### Army transport staging no-op assignment elimination

Change:
- Stopped rewriting army `:mode` and `:transport-staging-target` during transport staging when both values are already current.
- Stopped syncing AI visibility for those unchanged staging assignments.
- Applied the same no-op skip to both producer staging and returning `:sail-to-load` staging assignment.

Files:
- `src/empire/computer/army/assignment.cljc`

Verification:
- Added regressions proving no writes occur when:
  - a producer staging army already has the assigned staging mode and target
  - a returning-transport staging army already has the assigned staging mode and target

Measurement:
- Command: `clj -M:run --headless=220 --slow-round-analysis=500:20 --seed=1774361658123`
- Before optimization, an earlier late window showed:
  - `process-computer/army-move-to-coast-for-transport` avg `347.6 ms`
- After optimization, a comparable post-change window showed:
  - `process-computer/army-move-to-coast-for-transport` avg `91.0 ms`

Result:
- Transport staging army work dropped substantially on this seeded path.
- In the next measured active-invasion window, the dominant army cost shifted to `process-computer/army-move-to-coast-for-invasion`.
