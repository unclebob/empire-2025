# Unified Threat Response Plan

## 1. Objectives

1. Add three automatic threat responses that run from computer-side detections:
2. Enemy fighter detection: dispatch 4 fighters for local sweep.
3. Enemy ship detection: dispatch 2 patrol boats + 2 battleships for sea scouting.
4. Enemy city/army detection: trigger full major invasion with global mobilization.

## 2. Core Architecture

1. Create a new coordinator namespace, e.g. `empire.computer.threat_response`.
2. Coordinator owns threat events, active missions, theater state, and per-round updates.
3. Keep existing unit movement/combat code as execution engines; coordinator sets mission state only.
4. Hook detection ingestion where computer visibility updates occur.
5. Hook mission progression in round-start and/or unit-processing flow.

## 3. Shared State Additions

1. Add atoms (or structured maps under one atom) for:
2. `:fighter-threat-events` (recent enemy fighter detections).
3. `:ship-threat-events` (recent enemy ship detections).
4. `:major-invasion` map with:
5. `:active?`
6. `:detection-points` (set of coordinates)
7. `:theater` (derived area/continent set)
8. `:started-round`
9. Add unit mission fields (as needed) on unit maps:
10. `:mission-type`
11. `:mission-center`
12. `:mission-radius`
13. `:mission-rounds-left`
14. route/refuel fields for fighters/transports.

## 4. Detection Pipeline

1. On computer-map reveal, detect newly visible player entities by type.
2. Classify detections into three trigger types:
3. player fighter
4. player ship
5. player city or player army
6. Deduplicate same-cell detections per round.
7. Record event time/round for mission refresh logic.

## 5. Trigger 1: Enemy Fighter Response

1. On enemy fighter detection at `D`, pick up to 4 closest available computer fighters.
2. Availability excludes dead/non-computer/incompatible locked states.
3. Assign mission `:threat-fighter-sweep` with:
4. center `D`
5. radius `5`
6. duration `10` rounds
7. Fighters must support multi-hop refuel to reach zone.
8. Refuel graph nodes: friendly cities + carriers.
9. If low fuel during sweep, refuel and return to sweep while duration remains.
10. End mission when rounds expire or fighter unavailable.

## 6. Trigger 2: Enemy Ship Response

1. On enemy ship detection at `D`, select:
2. up to 2 closest patrol boats
3. up to 2 closest battleships
4. Assign mission `:threat-sea-scout` with center `D`, radius `5`.
5. Units move/scout inside radius and engage attackable targets opportunistically.
6. If damaged/blocked, existing safety/repair behavior can temporarily take precedence, then resume if mission still active.
7. Mission duration: define explicit constant (recommend `10` rounds for parity unless changed).

## 7. Trigger 3: Enemy City/Army Major Invasion

1. On any enemy city/army detection, activate major invasion immediately.
2. No dependency on free-city count; always triggers.
3. Add detection point to persistent invasion set.
4. Broaden theater by unioning all active detection points into one attack area.
5. Mobilize all relevant units:
6. all transports (empty -> urgent loading; loaded -> invade movement)
7. all carriers
8. all fighters
9. all battleships, destroyers, submarines
10. Combat rule: any mobilized unit attacks any valid attackable target when possible.
11. Transport unload rule: may unload on any eligible land connected to any detection point (union of target continents), not a single target only.

## 8. Transport Changes for Major Invasion

1. Add major-invasion-aware target selection path.
2. Compute union of continents connected to detection points.
3. Compute sea approach/unload positions adjacent to that union.
4. Loaded transports prioritize entering unloading/invading near union coastline.
5. Empty transports prioritize fastest loading then same routing.
6. Keep existing non-invasion transport logic as fallback when invasion inactive.

## 9. Fighter/Carrier Coordination

1. Reuse/extend fighter refuel pathing to allow chained city/carrier hops.
2. Carriers move with invasion fleet to keep forward refuel options alive.
3. Fighter mission phases:
4. route-to-theater
5. sweep/engage
6. refuel-and-return
7. Mission persists until timer expires or major invasion deactivates.

## 10. Integration Sequence

1. Add data model and coordinator skeleton.
2. Implement detection classification and event recording.
3. Implement fighter-trigger mission assignment + progression.
4. Implement ship-trigger mission assignment + progression.
5. Implement major invasion state, theater broadening, and global mobilization.
6. Integrate transport unload-on-union-continent rule.
7. Integrate opportunistic attack checks under mobilized missions.
8. Wire into game loop/round start.

## 11. Test Plan (Test First)

1. Detection classification tests:
2. fighter/ship/city-army routes to correct trigger.
3. Fighter trigger tests:
4. selects 4 closest fighters
5. supports multi-hop refuel
6. patrol radius 5
7. lasts 10 rounds
8. Ship trigger tests:
9. selects 2 patrol boats + 2 battleships
10. nearest-selection correctness
11. radius-bound scouting behavior
12. Major invasion tests:
13. triggers every city/army detection
14. detection points accumulate and broaden theater
15. all required unit classes mobilized (including carriers)
16. transports load if empty
17. transports unload on land connected to any detection point
18. opportunistic attack behavior while mobilized
19. Regression tests:
20. normal behavior unchanged when no trigger active.

## 12. Validation and Quality Gates

1. Run targeted specs after each slice.
2. After any spec changes, run `clj -M:spec-structure-check`.
3. Run full spec suite before closing issue.
4. If mutate is run, check file line count first; recommend splitting files over 250 lines.

## 13. Delivery Strategy

1. Implement in small vertical slices with passing tests per slice.
2. Keep commits focused:
3. coordinator + state
4. fighter response
5. ship response
6. major invasion + transport rule
7. final cleanup/docs/future-issues update.
8. Remove the final item from `plans/future-issues.md` once all tests pass.
