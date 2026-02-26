# Land-Ho Target List for Transports

## Problem

Patrol boats and other units discover free cities as they explore, but this information isn't communicated to transports. Transports independently compute invasion targets via expensive BFS each round. The computer should conquer free cities as quickly as possible.

## Design

### Data

- **`land-ho-targets`** — new atom, ordered list (FIFO queue) of `[col row]` free city positions discovered by computer units.

### Discovery

When the computer-map visibility update reveals a cell for a non-army computer unit, if that cell transitions from unexplored to a free city, append its `[col row]` to `land-ho-targets`.

- Only computer-map transitions trigger this (not player-map).
- Only non-army computer units trigger this — patrol boats, fighters, satellites, and all ship types.
- Each cell is only discovered once (unexplored → revealed is a one-time event on the computer-map), so no deduplication is needed.

### Assignment (once per round start)

1. If `land-ho-targets` is empty, do nothing.
2. Take the first target from the queue.
3. Find the nearest sailing transport with 4+ armies.
4. BFS on the computer-map for a sea path to a sea cell adjacent to the target city's land neighbor.
5. **BFS succeeds** → assign transport to `:invading` mode with `:invasion-target` and `:invasion-path`, remove target from queue.
6. **BFS fails** (no known sea route) → move target to end of queue. Done for this round.
7. **No qualifying transport** → leave target at front of queue. Done for this round.
8. Only one assignment per round.

### Transport Invading Mode

New transport mission state: `:invading`.

**Each round:**
1. Step up to 2 cells along the precomputed `:invasion-path` (transport speed = 2).
2. When the path is exhausted (transport is at the sea cell adjacent to target land), switch to coast-crawl unloading — drop one army per adjacent empty land cell, continue crawling until all armies are unloaded.
3. After fully unloaded, transition to `:loading` mode with normal country-id rules.

**Edge cases:**
- **Path blocked:** reuse existing collision/sidestep handling from current transport movement.
- **Target city already conquered by computer:** keep going — armies serve as reinforcements.
- **Transport destroyed en route:** target is consumed from the queue and lost. Acceptable since the computer-map already has the city revealed, so no re-discovery is possible.

### Fallback

When `land-ho-targets` is empty, transports use the current sailing/BFS behavior unchanged. This keeps the existing approach as the default and makes the land-ho feature purely additive.

## Out of Scope

- **Player city invasion:** Only free cities are targeted. A separate "final invasion force" feature (tracked in future-issues.md) will handle targeting player cities once all free cities are taken.
