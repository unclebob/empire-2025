# Patrol Boat Crawl/Explore Redesign

## Problem
Patrol boats get stuck in corners and narrow channels. The heading-based reflection logic creates deterministic ping-pong patterns that waste movement steps.

## Design
Replace all patrol boat movement (both 1st and 2nd+ boats) with a two-mode state machine: **crawl** and **explore**.

### Shared State
- New global atom `seen-coast` — a set of `[col row]` coordinates shared by all computer patrol boats. Accumulates for the entire game. Added to `atoms.cljc` and `reset-all-atoms!`.

### Per-Unit State
- `:patrol-mode` — `:crawling` or `:exploring` (start as `:crawling`)

### Mode: `:crawling`
1. Move to an adjacent sea cell that is adjacent to land/city (same as current `coastline-move`).
2. Add each visited coastal cell to global `seen-coast`.
3. **Exit to `:exploring` when:**
   - Next coastal cell is already in `seen-coast` (covers loop-back and overlap)
   - At map edge with no unseen coastal neighbor

### Mode: `:exploring`
1. BFS from current position over passable sea cells. Minimum 4 levels, expand further until a target is found.
2. **Priority:** unseen coast first (sea cell adjacent to land/city not in `seen-coast`), unexplored cells second.
3. Move toward target using greedy neighbor selection (like transport `move-toward-position`), up to 4 steps/round.
4. **Exit to `:crawling` when:** arrive at a coastal cell not in `seen-coast`.

### Combat (unchanged)
Before each step, check:
1. Attack adjacent player transport → attack
2. Adjacent non-transport enemy → flee
3. Otherwise → crawl or explore

### What Gets Removed
- `patrol-heading`, `patrol-number`, `patrol-direction`, `patrol-history` fields
- `reflect-heading`, `detect-reflection-surface`, `patrol-sail-one-step`, `process-sailing-patrol-boat` functions
- Navigation module (`navigation.cljc`) if only used by patrol boats
- `patrol-country-id` distinction between 1st/2nd+ boats

### What Gets Reused
- `get-passable-sea-neighbors`, `adjacent-to-land?`, `flee-from`, `find-adjacent-player-transport`, `find-adjacent-non-transport-enemy` — kept as-is
- `coastline-move` — adapted: add `seen-coast` recording, check `seen-coast` for transition
- Transport BFS pattern from `pathfinding/bfs-to-coast-target` — adapted for patrol boat explore mode with different target criteria (unseen coast vs unexplored)

## Implementation Steps

### Step 1: Add `seen-coast` atom
- Add `(def seen-coast (atom #{}))` to `atoms.cljc`
- Add to `reset-all-atoms!` in `test_utils.cljc`

### Step 2: Write patrol boat BFS function
- New function in `pathfinding.cljc`: `bfs-to-unseen-coast`
- BFS over passable sea, min 4 levels, finds nearest sea cell adjacent to coast not in `seen-coast` (priority) or adjacent to unexplored territory
- Returns path (vector of positions) from start to target

### Step 3: Rewrite `coastline-move` → `patrol-crawl-step`
- Move to adjacent sea cell adjacent to land (prefer unseen coast)
- Add current cell to `@seen-coast`
- If next coastal cell is in `seen-coast` or at map edge → switch to `:exploring`
- Returns new position or nil

### Step 4: Write `patrol-explore-step`
- If no path cached, run `bfs-to-unseen-coast` to get path
- Move one step along path (greedy toward next waypoint)
- If arrive at unseen coast → switch to `:crawling`
- Returns new position or nil

### Step 5: Rewrite `patrol-boat-step` and `process-patrol-boat`
- Remove patrol-number branching
- Each step: combat check → crawl or explore based on `:patrol-mode`
- Loop up to 4 steps per round

### Step 6: Clean up dead code
- Remove heading/reflection/navigation code
- Remove `patrol-heading`, `patrol-number`, `patrol-direction`, `patrol-history` references
- Remove `navigation.cljc` if no other users

### Step 7: Update tests
- Rewrite patrol boat specs to test crawl/explore behavior
- Test `seen-coast` shared state
- Test mode transitions
- Test map edge handling
- Mock `rand` for deterministic tests

### Step 8: Update acceptance tests (with permission)
- Update patrol-boat.txt if existing scenarios conflict with new behavior
