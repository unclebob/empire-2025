# Design: Remove Sea Lane Network

## Goal

Clean removal of the sea lane network system. The sea lanes were a performance optimization for long-distance naval A* pathfinding, but only carriers actually use `pathfinding/next-step`. After removal, carriers use plain A* with the existing per-round path cache. Add carrier movement optimization to future issues for later consideration.

## Changes

### Delete files
- `src/empire/movement/sea_lanes.cljc` — entire module (~346 lines)
- `spec/empire/movement/sea_lanes_spec.clj` — entire spec (~483 lines)

### Simplify `src/empire/movement/pathfinding.cljc`
- Remove `naval-types`, `try-network-route`, `compute-network-step`
- Remove `sea-lanes/record-path!` call from `compute-a-star-step`
- Remove `bounded-a-star` (only used by sea lane routing)
- Simplify `next-step`: check cache -> A* -> cache sub-paths -> return
- Remove `require` of `sea-lanes` namespace and related config constants

### Strip references from other files
- `atoms.cljc` — remove `sea-lane-network` atom
- `test_utils.cljc` — remove sea-lane reset line
- `save_load.cljc` — remove `:sea-lane-network` from `saveable-atoms`
- `debug.cljc` — remove `format-sea-lane-section` and its call from `format-dump`
- `config.cljc` — remove 6 `sea-lane-*` constants

### Clean specs
- `spec/empire/movement/pathfinding_bfs_spec.clj` — remove sea-lane recording/routing tests
- `spec/empire/debug_spec.clj` — remove sea-lane debug formatting tests

### Update future issues
- Remove "Remove sea lanes" entry
- Add "Carrier movement optimization" entry
