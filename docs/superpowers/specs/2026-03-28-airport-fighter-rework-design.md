# Airport Fighter Rework

## Problem

Fighters in city airports currently lose all identity on landing (destroyed, become a count). At round start all airport fighters are woken, and all auto-launch on the flight path. The player has no per-fighter control once they enter the airport.

## Goal

Rework airport processing so that:
- Only one fighter launches on the flight path per round.
- One fighter always gets attention if fighters are present.
- The player can use `u` and `s` to control batch attention, mirroring the transport/army pattern.
- Fighters never appear in city `:contents`.

## Data Model

### City Cell Fields

- **`:fighter-count`** (integer) — total fighters stored in the airport. Incremented on landing or production. Decremented on launch or attention-driven departure.
- **`:awake-fighters`** (integer) — fighters queued for individual attention. Only non-zero after the player presses `u`. Decremented each time a queued fighter is handled.
- **`:flight-path`** (coords or nil) — persistent launch destination for auto-launched fighters.

### No Round-Start Waking

Remove the `wake-airport-fighters` call from `start-new-round`. Airport fighters stay sleeping by default. The processing logic itself decides when to present one for attention.

Carrier fighters already follow this pattern (comment on line 129 of `round_start.cljc`: "Carrier fighters stay asleep until 'u' is pressed").

## City Processing Flow

When a player city with `fighter-count > 0` is processed in `item_processing`:

1. **Flight-path launch**: If `:flight-path` is set and `fighter-count > 0`, launch one fighter toward the flight path. Decrement `fighter-count`.
2. **Attention**: If `fighter-count > 0` (after possible launch), present a synthetic airport fighter for attention. The player gives it orders (destination, sentry, etc.).
3. **Requeue on `u`**: If `awake-fighters > 0` after the current fighter is handled, requeue the city so the next fighter gets attention. Decrement `awake-fighters`.

### Synthetic Fighter

The synthetic fighter presented for attention is unchanged:

```clojure
{:type :fighter :mode :awake :owner :player :fuel config/fighter-fuel :from-airport true}
```

It is never stored in `:contents`. It exists only as a return value from `get-active-unit`.

## Keyboard Commands

### `u` (unload) while airport fighter has attention

Set `awake-fighters = fighter-count - 1` (all others besides the current one are queued for attention). The current fighter is handled normally. After it departs, the city requeues and the next fighter gets attention.

### `u` on a city at any other time (no fighter currently asking)

Set `awake-fighters = fighter-count` (all fighters will get attention when the city is next processed in the round).

### `s` (sentry) while airport fighter has attention

Set `awake-fighters = 0` (clear the attention queue). The current fighter goes sentry (stays in airport). Mark item processed.

### Other keys while airport fighter has attention

Standard behavior: player gives the fighter a destination, it launches from the airport. `fighter-count` decrements. If `awake-fighters > 0`, city requeues for the next fighter.

## Production

When a city produces a fighter:
- Increment `fighter-count` on the city cell.
- Do NOT place the fighter in `:contents`.
- Production runs before item processing (`update-production` at line 124 of `round_start.cljc`, player-items built at line 134), so the fighter will be in the airport when the city is processed.

## Attention Detection

Change `player-map-cell-needs-attention?` in `attention_decisions.cljc`:
- A city needs attention if `fighter-count > 0` (not `awake-fighters > 0` as before).
- This ensures one fighter always gets attention during normal processing.

## Landing

Unchanged: when a fighter lands at a player-owned city, the fighter unit is removed from the map and `fighter-count` is incremented. No changes to landing mechanics.

## Files Affected

| File | Change |
|------|--------|
| `game/loop/round_start.cljc` | Remove `wake-airport-fighters` call |
| `game/loop/round_setup/waking.cljc` | Remove or gut `wake-airport-fighters` |
| `game/loop/item_processing.cljc` | Rework auto-launch to launch only one; present one for attention; requeue on `awake-fighters > 0` |
| `player/commands_actions.cljc` | Add `:wake-fighters-on-airport` and `:sleep-fighters-on-airport` handlers |
| `player/commands_action_decisions.cljc` | Add airport cases for `u` and `s` decisions |
| `player/attention_decisions.cljc` | Change attention check from `awake-fighters > 0` to `fighter-count > 0` |
| `player/production.cljc` | Fighter spawn increments `fighter-count` instead of placing in `:contents` |
| `player/production_decisions.cljc` | Fighter build returns nil (no unit for `:contents`); flight-path no longer applied at spawn |
| `game_mechanics/containers/ops.cljc` | Add `wake-fighters-on-airport` and `sleep-fighters-on-airport` operations |
| `game_mechanics/movement/movement_state.cljc` | Update `active-airport-fighter` to check `fighter-count > 0` (not `awake-fighters`) for the default single-attention case |

## Unchanged

- Carrier fighter mechanics (already follow the `u`-to-wake pattern).
- Fighter landing mechanics.
- Flight-path setting via `f` key.
- Computer airport/kamikazee fighter handling.
