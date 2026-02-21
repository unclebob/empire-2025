# Computer Ship Repair Acceptance Tests

## Overview

Acceptance tests for the computer ship repair lifecycle: dock, repair, launch.

## Scenarios

### 1. Damaged computer destroyer docks at computer city

A damaged computer destroyer adjacent to a computer city retreats under threat from a player battleship. On arrival at the city, `ship-can-dock?` triggers and the ship enters the shipyard.

Map: `BdX`, destroyer has 2 hits. After round: no `d` on map, shipyard contains destroyer with 2 hits.

Risk: computer movement may not go through the dock check in `movement/move-unit`. If so, this test fails and exposes a real bug.

### 2. Docked ship repairs 1 hit per round

A computer city has a destroyer with 1 hit in its shipyard. After a round, `repair-damaged-ships` increments hits to 2.

### 3. Fully repaired ship launches to adjacent sea

Map: `~X#`, city has destroyer with 2 hits in shipyard. Destroyer max hits = 3; one repair brings it to full. Ship launches to adjacent sea cell. Expected result: `dX#`.

Current `launch-ship-from-shipyard` places the ship on the city cell rather than adjacent sea — this is a bug the test will expose.

### 4. Repaired ship launches even when city is occupied (WILL FAIL)

Same as scenario 3 but city has a computer army on it. Current code checks `(nil? (:contents current-cell))` before launching, blocking the launch. This test documents the desired behavior.

## New parser/generator directives needed

- `GIVEN X has a <ship-type> with N hits in its shipyard.`
- `THEN X has a <ship-type> with N hits in its shipyard.`
- `THEN X has no ships in its shipyard.`
- `THEN the map is <map-string>.`

## Bugs exposed

1. `launch-ship-from-shipyard` places ship on city cell instead of adjacent sea
2. `repair-city-ships` skips launch when city has contents (should launch to adjacent sea instead)
3. Possibly: computer ship movement doesn't go through dock check
