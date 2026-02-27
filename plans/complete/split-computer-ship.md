# Split computer/ship.cljc

## Goal
Split 890-line `computer/ship.cljc` into four focused modules under 250 lines each, enabling mutation testing.

## New Modules

### `computer/ship_patrol.cljc` (~140 lines)
Move from ship.cljc:
- `find-adjacent-player-transport` (private)
- `find-adjacent-non-transport-enemy` (private)
- `adjacent-to-land?` (private)
- `flee-from` (private)
- `patrol-crawl-step` (public)
- `arrived-at-unseen-coast?` (private)
- `run-bfs-and-store-path` (private)
- `switch-to-crawling` (private)
- `follow-explore-path` (private)
- `patrol-explore-step` (public)
- `patrol-boat-step` (private)
- `process-patrol-boat` (private → public, called by ship.cljc)

Requires: `ship/get-passable-sea-neighbors`, `ship/attack-enemy`, `ship/explore-sea`, `ship/move-toward`

### `computer/ship_escort.cljc` (~140 lines)
Move from ship.cljc:
- `find-unadopted-transport` (private)
- `adopt-transport` (private)
- `find-transport-by-id` (private → public, used by ship_carrier)
- `find-enemy-near-positions` (private → public, used by ship_carrier)
- `find-enemy-near-destroyer-group` (private)
- `begin-pursuit` (private → public, used by ship_carrier)
- `group-positions` (private)
- `visible-to-group?` (private)
- `end-pursuit` (private)
- `process-pursuit` (private → public, used by ship_carrier)
- `revert-destroyer-to-seeking` (private)
- `process-destroyer-seeking/intercepting/escorting` (private)
- `process-escort-destroyer` (private → public, called by ship.cljc)

Requires: `ship/move-toward`, `ship/explore-sea`
Also needs `find-carrier-by-id` from ship_carrier (circular) — resolve by passing as parameter or declaring.

### `computer/ship_carrier.cljc` (~200 lines)
Move from ship.cljc:
- `find-computer-cities` (private)
- `compute-distant-city-pairs` (public)
- `update-distant-city-pairs!` (public)
- `find-reserved-pairs` (public)
- `find-unreserved-pair` (public)
- `find-position-between-cities` (public)
- `find-refueling-sites` (public)
- `find-carrier-position` (public)
- `find-carrier-by-id` (private → public, used by ship_escort)
- `orbit-ring` (public)
- `find-carrier-with-open-slot` (private)
- `initial-orbit-angle` (private)
- `adopt-carrier-escort` (private)
- `orbit-target-pos` (private)
- `valid-orbit-pos?` (private)
- `find-next-orbit-angle` (private)
- `revert-escort-to-seeking` (private)
- `process-escort-seeking/intercepting/orbiting` (private)
- `find-enemy-near-carrier-group` (private)
- `process-carrier-group-escort` (private → public, called by ship.cljc)
- Carrier positioning functions (private → process-carrier public)
- `target-still-valid?`, `position-carrier-with-target`, `position-carrier-without-target`
- `reposition-carrier`, `pair-still-valid?`, `process-carrier` (public)

Requires: `ship/move-toward`, `ship/explore-sea`
Uses: `ship_escort/find-transport-by-id`, `ship_escort/find-enemy-near-positions`, `ship_escort/begin-pursuit`, `ship_escort/process-pursuit`

### `computer/ship.cljc` (facade, ~100 lines)
Keep:
- `get-passable-sea-neighbors` (public)
- `find-adjacent-enemy-ship` (public)
- `attack-enemy` (public)
- `find-computer-transports` (private)
- `find-nearest-transport` (private)
- `move-toward` (public)
- `explore-sea` (public)
- `find-player-ship-sighting` (private)
- `retreat-if-damaged` (private)
- `find-adjacent-dock-city` (private)
- `dock-computer-ship` (private)
- `try-dock/retreat/attack-adjacent/escort/escort-transport/hunt-player-ship` (private)
- `dispatch-ship-action` (private)
- `process-ship` (public)

Re-export from sub-modules:
- `patrol-crawl-step`, `patrol-explore-step` from ship_patrol
- `compute-distant-city-pairs`, `update-distant-city-pairs!`, `find-reserved-pairs`, `find-unreserved-pair`, `find-carrier-position`, `find-refueling-sites`, `find-position-between-cities`, `orbit-ring`, `process-carrier` from ship_carrier

## Circular Dependency Resolution
ship_escort needs `find-carrier-by-id` from ship_carrier.
ship_carrier needs `find-transport-by-id`, `find-enemy-near-positions`, `begin-pursuit`, `process-pursuit` from ship_escort.

Resolution: Move shared pursuit/group logic into ship_escort. ship_carrier requires ship_escort. ship_escort does NOT require ship_carrier — instead, `find-carrier-by-id` is passed as a parameter or ship_escort declares it and ship_carrier provides it. Simplest: put `find-carrier-by-id` in ship_escort (it's used by group-positions there anyway).

## Steps
1. Create `ship_patrol.cljc` with patrol boat functions
2. Create `ship_escort.cljc` with destroyer escort + pursuit functions + `find-carrier-by-id`
3. Create `ship_carrier.cljc` with carrier positioning + carrier-group escort
4. Slim `ship.cljc` to facade: core utilities + dispatch + re-exports
5. Run full test suite
6. Commit
