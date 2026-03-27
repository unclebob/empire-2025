# Performance Enhancement: Precomputed Coastal Index

## Problem

Multiple BFS searches across the codebase re-discover fixed geography every round. Coastline — which cells are land-adjacent-to-sea and sea-adjacent-to-land — is determined at map generation and never changes. Yet patrol boats, transports, and army coastal logic all run BFS from scratch to find coastal cells, scanning hundreds or thousands of cells each time.

## Approach: Permanent Coastal Cell Index

At map generation, precompute and store two permanent sets:

1. **Coastal land cells** — land/city cells that have at least one sea neighbor
2. **Coastal sea cells** — sea cells that have at least one land/city neighbor

These sets are immutable for the life of the game. All subsequent "find nearest coast" queries become filtered lookups over these sets rather than BFS expansion.

## Where This Replaces BFS

### Patrol boat exploration (`bfs-to-unseen-coast`)
- **Current**: BFS from boat position over passable sea, up to 1500 cells, looking for sea cells adjacent to land not in `seen-coast`
- **With index**: Filter `coastal-sea-cells` by: not in `seen-coast`, visible on computer-map, not claimed by another patrol boat. Sort by distance. Pick nearest. Then pathfind to it (still needed, but target is known).
- **Savings**: Eliminates the 1500-cell BFS discovery. Only the pathfinding-to-known-target remains.

### Transport unload targeting (`bfs-to-unload-target`, `has-nearby-unloadable-land?`)
- **Current**: BFS from transport over sea cells to find first cell adjacent to unloadable land
- **With index**: Filter `coastal-sea-cells` by: adjacent to unoccupied unclaimed land on computer-map. Sort by distance. Pick nearest reachable.
- **Savings**: Eliminates discovery BFS. Candidate set is typically 50-200 cells vs 1000+ BFS expansion.

### Transport load targeting (`choose-load-target-cell`, tile scanning)
- **Current**: Scans all 5x5 tiles looking for coastal land cells with armies nearby
- **With index**: Filter `coastal-land-cells` by: has country-id, has nearby armies. Already a filtered lookup — the tile system is a spatial index approximation. Could replace tiles with distance-filtered coastal-land-cells.

### Army coastal positioning (`find-nearest-unoccupied-coastal-cell`, `seed-coastal-registry`)
- **Current**: `seed-coastal-registry` scans entire map to find coastal land cells per country. Called once per country per round.
- **With index**: Filter `coastal-land-cells` by country-id on computer-map. No scan needed.

### Fighter refueling (`find-nearest-refueling-site`)
- **Already cached per round** — less urgent, but the scan could use the coastal index to find carrier positions faster.

## Data Structure

```clojure
;; Computed once at map generation, stored in game state
{:coastal-land-cells #{[r c] ...}   ;; land/city cells adjacent to sea
 :coastal-sea-cells  #{[r c] ...}}  ;; sea cells adjacent to land/city
```

Size: On a 100x60 map, typically 500-1500 coastal cells of each type. Small enough to filter and sort every query.

## Implementation Steps

### Step 0: Eliminate diagonal land pinches at map generation
After terrain smoothing, scan for the diagonal pinch pattern: two land cells touching at corners with two sea cells at the other corners. Fill one of the sea corners with land to eliminate the ambiguity. Repeat until no pinches remain (typically 1-2 passes). This guarantees every coastline is a single unambiguous walk with no bifurcations.

### Step 1: Compute and store the index
- After `make-initial-map`, scan once and store both sets in game state
- Add to save/load serialization

### Step 2: Replace patrol boat BFS target discovery
- `bfs-to-unseen-coast` becomes: filter coastal-sea-cells, pick nearest unseen, pathfind to it
- Keep the existing BFS pathfinding for the route, just eliminate the target discovery

### Step 3: Replace transport unload target discovery
- `bfs-to-unload-target` becomes: filter coastal-sea-cells adjacent to unloadable land, pick nearest
- `has-nearby-unloadable-land?` becomes: check if any coastal-sea-cell within radius has unloadable neighbor

### Step 4: Replace army coastal registry seeding
- `seed-coastal-registry` becomes: filter coastal-land-cells by country-id
- Eliminates the per-country full map scan

### Step 5: Replace tile-based load targeting (optional)
- `choose-load-target-cell` could use coastal-land-cells filtered by army proximity
- May not be worth changing if the tile system works well enough

## Unit Processing Order: Transports Before Armies

### Problem

Currently `build-computer-items` returns all computer units in arbitrary map-scan order. Cities, transports, armies, fighters, and ships are interleaved by position. This means:

1. **Tile scanning is per-transport**: Each transport calling `choose-load-target-cell` independently scans all 5x5 tiles to find coastal areas with armies. If done once at round start, all transports could share the result.

2. **Armies move before transports load**: An army might walk away from the coast before a transport arrives to pick it up. If transports choose targets and move first, then armies move toward loading transports, loading happens in the same round.

### Solution

Sort `build-computer-items` to process units in this order:
1. **Cities** — production decisions first
2. **Transports** — choose targets and move into loading position
3. **Patrol boats, destroyers, submarines, battleships, carriers** — naval movement
4. **Fighters** — air movement
5. **Armies** — land movement, including walking toward loading transports

### Benefits

- **Shared tile scan**: Compute the tile-based coastal army summary once at the start of transport processing. All transports use the same snapshot. Eliminates redundant tile scans.
- **Same-round loading**: Transport moves to coast, then army walks to transport and boards in the same round. Currently this takes two rounds.
- **Simpler staging**: `assign-transport-staging` already runs at round start. With transports moving first, the staging assignments are fresh when armies process.

### Risk

- Changing processing order could affect game behavior. Transport-first means transports see armies at their round-start positions. Armies see transports at their post-move positions. This is actually more logical — transports go where armies are, armies go where transports are.
- Need to verify no existing logic assumes arbitrary ordering.

### Step 6: Ray+crawl sea pathfinding

Replace BFS sea pathfinding with a geometric approach using the coastal index.

**Data structure**: A neighbor map `{[r c] #{[r1 c1] ...}}` where each coastal-sea-cell maps to its adjacent coastal-sea-cells. Built once at map generation alongside the coastal sets. Diagonal pinch elimination (Step 0) guarantees unambiguous coastline walks.

**Algorithm**:
1. Fire a ray (Bresenham line) from ship to target. Check each cell along the ray — if all are sea, the path is the ray. Done.
2. If the ray hits land, find the nearest coastal-sea-cell to the hit point using the neighbor map.
3. Walk the neighbor map in both directions simultaneously along the coastline. At each step, fire a ray from the current coastal-sea-cell to the target.
4. First direction that finds a clear ray wins. The path is: first ray segment + coast-walk segment + second ray segment.
5. If the second ray also hits land, repeat from step 2 (up to 4 total rays).
6. If 4 rays exhausted without reaching the target, fall back to BFS.

**Ray clearance check**: Walk the ray cell by cell, checking `(= :sea (:type cell))` on the game map. O(distance) lookups per ray. A ray across 30 cells is 30 map lookups.

**Coast walk**: Pure iteration over the precomputed neighbor map. No map scanning, no BFS. Each step is an O(1) lookup.

**Expected coverage**: Single ray handles open water. One coast-walk handles single islands/peninsulas (~80% of blocked paths). Two coast-walks handle archipelagos. BFS fallback covers complex geography (rare).

**Performance**: Ray is O(distance). Coast walk is O(coastline-segment-length). Total path planning is O(distance + coastline) vs BFS O(reachable-sea-cells). For typical paths, 10-100x faster.

## Risks

- The index is game-map based but queries filter by computer-map visibility. Must ensure queries don't leak information the AI hasn't discovered.
- Save/load must include the index, or recompute on load.
- Cities can change ownership (conquest), which changes whether a coastal-land-cell is "claimed". The index stores positions, not ownership — ownership is checked at query time from computer-map. This is correct.

## Expected Impact

Based on profiling data from this session:
- Patrol boat: 20ms/unit → most is BFS discovery → could drop to 5-8ms
- Transport unload transition: 50-200ms spikes → eliminate discovery BFS
- Army coastal registry: already cached per round, but initial scan eliminated
- Overall: should push the 500ms threshold trigger point significantly later
