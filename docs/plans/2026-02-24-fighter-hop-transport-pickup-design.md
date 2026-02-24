# Design: Computer Fighter Hop-Over & Transport Post-Unload Pickup

Date: 2026-02-24

## Feature 1: Computer Fighter Hop-Over

### Summary

Computer fighters skip over friendly (computer) units on their flight path instead of sidestepping around them. Each skipped cell costs one movement point. Only computer fighters get this behavior — armies and ships retain sidestep.

### Behavior

When a computer fighter's best neighbor toward its target is occupied by a computer unit, scan forward along the direction of travel, skipping all consecutive computer-occupied cells. Land on the first empty passable cell.

- Each skipped cell costs 1 movement point (deducted from step loop in `process-fighter`).
- Each skipped cell costs 1 fuel via `consume-fighter-fuel`.
- A fighter with 3 steps remaining that hops over 2 friendly units and lands on the 3rd cell uses all 3 steps.
- If remaining steps are exhausted before finding an empty cell, the fighter stops and burns stuck fuel.
- If the path runs off the map before finding an empty cell, fighter doesn't move (burns stuck fuel).
- If an enemy unit is in the hop path, the fighter attacks it rather than hopping over it.

### Changes

- **`computer/fighter.cljc`**: Replace call to `move-toward-with-sidestep` with new `hop-over-friendly-units` function.
- **`movement/sidestep.cljc`**: Untouched — armies/ships still use sidestep.
- **Tests**: Fighter-specific sidestep tests become hop-over tests. Army/ship sidestep tests unchanged.

### Tests Affected

- `spec/empire/computer/fighter_spec.clj` — 2 sidestep tests (lines 294-332) become hop-over tests
- `spec/empire/movement/sidestep_spec.clj` — fighter-specific tests (lines 103-124, 256-271, 273-308, 310-325) updated for hop behavior; army/ship tests stay
- `spec/empire/movement/movement_spec.clj` — fighter sidestep tests (lines 726-758) updated
- `acceptanceTests/fighter.txt` — new scenarios for hop-over

---

## Feature 2: Transport Post-Unload Pickup

### Summary

After a transport finishes unloading, it BFS-searches for the nearest sentry army on a coastal cell whose country-id differs from the transport's unload-country-id, then navigates there in loading mode. If no army found, it waits 10 rounds and searches again.

### Behavior

When army-count reaches 0 after unloading:

1. BFS the game map for the nearest sentry army on a coastal land cell (adjacent to sea) with `:country-id` != transport's `:unload-country-id`.
2. If found: store position as `:pickup-target`, enter `:loading` mode, navigate there via existing loading-phase movement (coastal crawl + `move-toward-position`). No sail-path BFS needed.
3. If not found: enter waiting state. Store current round in `:waiting-since`. Stay put. After 10 rounds, re-run BFS. If still nothing, reset wait counter.
4. On arrival at pickup target, if no loadable armies nearby (target moved/killed), re-run BFS.
5. When loading begins (first army picked up), mint new `:unload-country-id`.

### Fields

- `:pickup-target` — replaces `:pickup-continent-pos` (position of target sentry army)
- `:waiting-since` — round number when waiting began, nil when not waiting
- Remove `:pickup-country-id` and pickup-continent flood-fill logic

### Existing Behavior Preserved

- `recently-unloaded-country?` 10-round exclusion still prevents reloading just-unloaded armies.
- Loading-phase coastal crawl and army auto-loading unchanged.
- Mint of `:unload-country-id` moves from sail-start to load-start.

### Changes

- **`computer/transport.cljc`**: Replace `find-next-pickup-continent-pos` with `find-nearest-pickup-army`. Update `transition-to-loading`. Add waiting logic. Move country-id minting to load-start.
- **`atoms.cljc`**: No new atoms needed (state on transport unit).

### Tests Affected

- `spec/empire/computer/transport_spec.clj` — pickup-continent tests (lines 207-271) rewritten for BFS-to-sentry-army; transition-to-loading tests updated; cycle-breaking test still relevant
- `acceptanceTests/computer-transport.txt` and `acceptanceTests/sailing-transport.txt` — post-unload transition scenarios updated
- New acceptance tests: transport finds sentry army on different-country coast, transport waits when no army found, transport re-searches after 10 rounds
