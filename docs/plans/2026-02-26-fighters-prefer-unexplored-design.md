# Fighters Prefer Unexplored Cells — Design

## Goal

Computer fighters should prefer unexplored cells during regular leg navigation, not just during exploration sorties.

## Current Behavior

`navigate-toward-target` in `computer/fighter.cljc` detours through unexplored cells only when they are within 1 step of the direct path to the target. This is too conservative — fighters mostly fly direct routes over already-explored territory.

## Change

Modify `navigate-toward-target` to score all passable, unoccupied neighbors by `count-unexplored-neighbors` (existing function) when fuel margin allows. No distance cap on detours — fuel is the only constraint.

### Logic

1. If `fuel > direct-dist + 2`: score all passable unoccupied neighbors by `count-unexplored-neighbors`
2. Pick the highest-scoring neighbor; break ties by proximity to target
3. If no neighbor has unexplored cells nearby (all score 0), fall back to hop-over-friendly direct navigation

### What doesn't change

- Leg selection (`choose-leg`)
- Exploration sorties and drones
- Fuel management and low-fuel return
- Attack priority
- Arrival/refueling logic

## Testing

- Fighter picks neighbor with highest unexplored count when fuel allows
- Fighter takes direct path when fuel is tight
- Fighter falls back to direct navigation when no neighbors border unexplored cells
- Reuses existing `count-unexplored-neighbors` — no new helpers needed
