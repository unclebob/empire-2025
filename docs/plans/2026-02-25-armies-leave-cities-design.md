# Armies Leave Cities

## Problem

Computer armies can sit indefinitely in cities after spawning. No mechanism forces them out. VMS Empire treats cities as pass-through points.

## Design

Add a city-exit check at the top of `process-army`, before mode dispatch:

1. If the army's cell is `:city`, find an empty passable land neighbor.
2. Move there and return. Normal mode processing resumes next round.
3. If all neighbors are blocked, fall through to normal processing (retry next round).

The army keeps its stamped mode (coast-walk, random-explore, etc.). No garrison.

## Files

- `src/empire/computer/army.cljc` — city-exit logic at top of `process-army`
- `spec/empire/computer/army_spec.clj` — tests for exit and blocked cases
