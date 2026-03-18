## AI Map Access Audit Follow-Up

Context:

- Audit command: `./scripts/check-ai-map-access.sh`
- Transport AI decision paths were just moved back to `computer-map` reads and are green.
- Full validation at this point:
  - `clj -M:spec` passes
  - acceptance pipeline passes
- Audit still reports widespread `sa/current-world` reads across non-transport AI.

Hard rule for this cleanup:

- No code involved with computer AI decisions is allowed to read from the game map.
- Fog of war must be preserved.
- If the AI needs fresh self-state after same-round mutations, the fix is not to read `game-map` in decision code.
- The fix is to perform the necessary visibility or state sync updates so the needed data is present in `computer-map`.

Important boundary:

- Not every `current-world` reference is a defect.
- The transport sync helper in [src/empire/computer/transport_core.cljc](/Users/unclebob/projects/clojure/empire/empire-2025/src/empire/computer/transport_core.cljc#L9) is intentional.
- That helper reads authoritative state only to mirror the acting AI-owned transport cell into `computer-map`.
- The cleanup target is: AI decision logic should not read `current-world` directly.

## Current audit hotspots

Highest concentration and likely highest leverage:

- [src/empire/computer/core.cljc](/Users/unclebob/projects/clojure/empire/empire-2025/src/empire/computer/core.cljc)
  - foundational movement and helper functions
  - many downstream AI modules inherit authoritative-map coupling from here
- [src/empire/computer/threat_response.cljc](/Users/unclebob/projects/clojure/empire/empire-2025/src/empire/computer/threat_response.cljc)
  - broad orchestration layer
  - currently injects and rereads `current-world` in multiple decision paths
- [src/empire/computer/ship_patrol.cljc](/Users/unclebob/projects/clojure/empire/empire-2025/src/empire/computer/ship_patrol.cljc)
  - multiple self-state and random-walk reads
  - closest analogue to the transport bug pattern
- [src/empire/computer/ship_escort.cljc](/Users/unclebob/projects/clojure/empire/empire-2025/src/empire/computer/ship_escort.cljc)
  - escort/transport coordination still reads authoritative unit state directly
- [src/empire/computer/ship_carrier_group.cljc](/Users/unclebob/projects/clojure/empire/empire-2025/src/empire/computer/ship_carrier_group.cljc)
  - carrier/escort coordination reads authoritative world for paired-unit decisions

Second tier:

- [src/empire/computer/fighter.cljc](/Users/unclebob/projects/clojure/empire/empire-2025/src/empire/computer/fighter.cljc)
- [src/empire/computer/fighter_movement_impl.cljc](/Users/unclebob/projects/clojure/empire/empire-2025/src/empire/computer/fighter_movement_impl.cljc)
- [src/empire/computer/army.cljc](/Users/unclebob/projects/clojure/empire/empire-2025/src/empire/computer/army.cljc)
- [src/empire/computer/army/movement.cljc](/Users/unclebob/projects/clojure/empire/empire-2025/src/empire/computer/army/movement.cljc)
- [src/empire/computer/army/coastal.cljc](/Users/unclebob/projects/clojure/empire/empire-2025/src/empire/computer/army/coastal.cljc)
- [src/empire/computer/army/exploration.cljc](/Users/unclebob/projects/clojure/empire/empire-2025/src/empire/computer/army/exploration.cljc)

Lower priority or likely dependency-driven:

- [src/empire/computer/coordinator.cljc](/Users/unclebob/projects/clojure/empire/empire-2025/src/empire/computer/coordinator.cljc)
- [src/empire/computer/production.cljc](/Users/unclebob/projects/clojure/empire/empire-2025/src/empire/computer/production.cljc)
- [src/empire/computer/land_ho.cljc](/Users/unclebob/projects/clojure/empire/empire-2025/src/empire/computer/land_ho.cljc)
- [src/empire/computer/early_game/strategy.cljc](/Users/unclebob/projects/clojure/empire/empire-2025/src/empire/computer/early_game/strategy.cljc)

## Priority order

1. `src/empire/computer/core.cljc`
2. `src/empire/computer/ship_patrol.cljc`
3. `src/empire/computer/threat_response.cljc`
4. `src/empire/computer/ship_escort.cljc`
5. `src/empire/computer/ship_carrier_group.cljc`
6. `src/empire/computer/fighter_movement_impl.cljc`
7. `src/empire/computer/fighter.cljc`
8. `src/empire/computer/army/movement.cljc`
9. `src/empire/computer/army/coastal.cljc`
10. `src/empire/computer/army/exploration.cljc`

Rationale:

- `core.cljc` is the multiplier. If movement helpers keep assuming `current-world`, every AI module above them stays leaky.
- `ship_patrol.cljc` looks like the nearest repeat risk of the transport stale-self-state bug: mission/self-state plus oscillation logic mixed with authoritative rereads.
- `threat_response.cljc` is large and central. It will likely need staged cleanup after helper boundaries are fixed.
- fighter and army modules should come after the shared helper and naval orchestration layers are stable.

## Cleanup strategy

For each target module:

1. Identify whether each `current-world` read is:
   - AI world knowledge
   - acting unit self-state
   - post-mutation authoritative sync support
2. Convert AI world knowledge reads to `sa/read-state :computer-map`.
3. Convert acting-unit self-state reads to `computer-map` as well.
4. If same-round writes make self-state stale, add explicit write-through sync into the relevant visible AI map.
5. If fog-preserving AI behavior needs newly-learned information, add the appropriate visibility update rather than a decision-time `game-map` read.
6. Keep any remaining authoritative reads isolated to sync helpers or engine-adjacent mutation helpers, not decision code.

Non-negotiable constraint:

- `computer-map` is the only map AI decision code may consult.
- `game-map` may only be used by mutation/sync infrastructure whose job is to project authoritative state into AI-visible state.
- Any ambiguity should be resolved in favor of more explicit visibility updates, not fallback reads from `game-map`.

## Expected supporting work

- Add more explicit sync helpers for non-transport AI-owned units if needed.
- Likely candidates:
  - patrol boats / destroyers
  - carriers and escorts
  - fighters after fuel/path/order mutations
- Add or expand visibility updates where AI units should legitimately know the result of their own moves, mission transitions, cargo changes, fuel changes, or nearby reveals without breaking fog-of-war constraints.
- Some `core.cljc` helpers may need split responsibilities:
  - pure decision helpers that consume `computer-map`
  - mutation helpers that update world then sync AI-visible maps

## Risk notes

- `threat_response.cljc` and `core.cljc` may be too large for safe direct conversion in one pass.
- If a module mixes fog-limited targeting with same-round self-state mutation, the right answer is not to restore `current-world` reads inside decision code.
- The transport work established the preferred pattern:
  - mutate authoritative world
  - sync acting AI-owned unit state into `computer-map`
  - continue decisions from `computer-map`
- Some fixes will require special visibility updates so the AI has the information it is entitled to have while still preserving fog of war.

## Recommended execution sequence

Phase 1:

- clean `core.cljc`
- then re-audit

Phase 2:

- clean `ship_patrol.cljc`
- clean `ship_escort.cljc`
- clean `ship_carrier_group.cljc`
- then re-audit

Phase 3:

- clean `threat_response.cljc`
- then re-audit

Phase 4:

- clean fighter modules
- clean army modules
- then re-audit

## Success criteria

- audit output no longer reports AI decision-path reads from `sa/current-world`
- any remaining hits are documented sync helpers only
- full `clj -M:spec` passes
- acceptance pipeline passes
- changed modules satisfy CRAP and differential mutation workflow
