# Plan: Split Large Files

27 files over 250 lines. Categorized by splittability.

## Not Worth Splitting (cohesive or infrastructure)

| Lines | File | Reason |
|-------|------|--------|
| 471 | debug/dump.cljc | All formatters serve one output. Cohesive. |
| 361 | test/utils.cljc | Test infrastructure, shared helpers. |
| 318 | acceptance/parser/ir_contracts.cljc | 2 functions, mostly data. |
| 307 | acceptance/generator/then.cljc | All then-clause generators. |
| 285 | acceptance/generator.cljc | Pipeline glue. |
| 259 | ui/util/rendering/format.cljc | All formatting functions. |

## Splittable by Mission/Phase

### transport/mission_handlers.cljc (431 lines, 26 defns)
Split by mission type:
- `mission_handlers/loading.cljc` — `process-loading-mission`, `planned-loading-action`, `take-second-unloading-step`
- `mission_handlers/unloading.cljc` — `process-unloading-with-armies`, `prefer-pickup-over-unload?`, `unloading-crawl-loop`, `crawl-step-result`, `clear-hold-and-crawl`
- `mission_handlers/invasion.cljc` — `process-find-armies-for-invasion`, `process-load-for-invasion`, invasion context/action helpers
- `mission_handlers/lake.cljc` — `maybe-handle-lake-transport`, `park-lake-transport-if-empty`, `process-land-locked-mission`
- Keep `mission_handlers.cljc` as thin dispatch.

### transport/sailing_regular.cljc (398 lines, 32 defns)
Split by sailing mission:
- `sailing/follow.cljc` — `sail-follow-path`, `sail-take-second-step`, path following
- `sailing/transitions.cljc` — `enter-sail-to-load!`, `enter-sail-to-unload!`, `transition-to-loading!`, `enter-leave-city!`
- `sailing/missions.cljc` — `process-sail-to-load-mission`, `process-sail-to-unload-mission`, `process-hold-sail-to-load-mission`
- Keep `sailing_regular.cljc` as `process-sailing-mission` dispatch.

### transport/unloading.cljc (359 lines, 19 defns)
Split:
- `unloading/core.cljc` — `unloadable-land-cell?`, `adjacent-empty-land`, `has-nearby-unloadable-land?`, `pickup-exclude-ids`
- `unloading/actions.cljc` — `try-opportunistic-unload`, `unload-armies`, `unloading-crawl-move`, army placement

### transport.cljc (311 lines, 26 defns)
Split:
- `transport/process.cljc` — `process-transport`, `process-active-transport`, random walk
- `transport/transitions.cljc` — `transition-to-loading`, `start-sailing`, invasion transitions

## Splittable by Responsibility

### threat_response/core.cljc (365 lines, 44 defns)
44 functions — too many. Split:
- `threat_response/detection.cljc` — detection handling, point tracking
- `threat_response/refresh.cljc` — map refresh, assignment refresh
- Keep `core.cljc` as `on-round-start!`, `handle-detection!` facade.

### threat_response/major_invasion.cljc (347 lines, 28 defns)
Split:
- `major_invasion/planning.cljc` — target selection, route planning
- `major_invasion/execution.cljc` — transport dispatch, army movement

### threat_response/kamikazee_mission.cljc (334 lines, 28 defns)
Split:
- `kamikazee/targeting.cljc` — target selection, army scoring
- `kamikazee/launch.cljc` — launch logic, airport processing

### visibility.cljc (327 lines, 33 defns)
Split:
- `visibility/core.cljc` — update-combatant-map-state, reveal logic
- `visibility/sync.cljc` — sync-ai-unit-to-computer-map!, detection tracking
- `visibility/territory.cljc` — stamp-exposed-territory!, cell exposure

### fighter/flight_decisions.cljc (321 lines, 27 defns)
Split:
- `flight_decisions/staging.cljc` — staging plans, city hop paths
- `flight_decisions/exploration.cljc` — exploration plans, heading selection

### ship/patrol.cljc (331 lines, 26 defns)
Split:
- `patrol/crawl.cljc` — patrol-crawl-step, coast walking
- `patrol/explore.cljc` — patrol-explore-step, BFS path, random walk
- `patrol/repulsion.cljc` — nearby-patrol-boat-count, prefer-dispersed
- Keep `patrol.cljc` as `process-patrol-boat` dispatch.

### game/loop/core.cljc (283 lines, 20 defns)
Split:
- `loop/round_start.cljc` — `start-new-round`, cache clears, round setup
- `loop/advance.cljc` — `advance-game`, `advance-game-batch`, action dispatch
- Keep `core.cljc` as public API re-exports.

### ship/carrier.cljc (280 lines, 20 defns)
Split:
- `carrier/positioning.cljc` — find-carrier-position, find-position-between-cities, city pairs
- `carrier/movement.cljc` — process-carrier, reposition logic

## Borderline (260-280 lines)

| Lines | File | Action |
|-------|------|--------|
| 279 | pathfinding_bfs/coast_targeting.cljc | Could split scoring from BFS, but tightly coupled. Leave for now. |
| 273 | threat_response/kamikazee_routing.cljc | Graph building + route planning. Could split but small benefit. |
| 268 | game/initialization.cljc | Already grew with coastal index. Could extract coastal index to own file. |
| 260 | army/coastal.cljc | Coast walk + invasion movement. Could split. |
| 256 | shared/action_resolution.cljc | Movement + combat + territory. Could split. |

## UI Files (lower priority)

| Lines | File | Action |
|-------|------|--------|
| 319 | ui/util/rendering/display.cljc | Map display helpers. Cohesive. |
| 306 | ui/quil/core.cljc | Quil setup/draw. Hard to split without framework changes. |
| 296 | game_mechanics/services/combat.cljc | Combat resolution. Cohesive. |

## Implementation Order

1. **transport/mission_handlers** — biggest payoff, 4 clear splits
2. **threat_response/core** — 44 functions is too many
3. **transport/sailing_regular** — 3 clear splits
4. **ship/patrol** — 3 clear splits
5. **visibility** — 3 clear splits
6. **game/loop/core** — 2 clear splits
7. Remaining as needed

## Rules

- Move functions, don't rewrite them
- Update all requires in callers
- Run specs after each file split
- Run acceptance tests after each module
- Run spec-structure-check after each split
