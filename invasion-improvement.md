# Invasion Improvement Notes

The current transport and army mechanisms limit the rate at which the computer invades other continents. The main issue is not raw movement speed. The larger issue is coordination: armies, pickup beaches, and transports are only loosely coupled, so transports spend too many rounds searching, waiting, or loading the wrong armies.

## Main Problems

### 1. Generic boarding is too generic

Normal armies board the first or nearest loading transport, not the transport that actually reserved them or the one serving the current invasion lane.

Relevant code:
- `src/empire/computer/shared/transport_query.cljc`
- `src/empire/computer/army/transport.cljc`

Effect:
- Creates cross-traffic between transports
- Pulls armies away from planned pickup cells
- Leaves some transports underfilled while nearby armies board other transports

### 2. Planned loading still prefers opportunistic adjacent armies

Manifest-based pickup exists, but `load-adjacent-armies` currently prioritizes non-manifest adjacent armies ahead of manifest matches.

Relevant code:
- `src/empire/computer/transport/loading.cljc`

Effect:
- Helps ad hoc transport use
- Hurts invasion logistics because invasion transports may load whoever is nearby instead of the staged invasion force

### 3. Returning transport staging is capped below transport capacity

Returning staging assigns at most 5 armies for a transport that can carry 6.

Relevant code:
- `src/empire/computer/army/assignment.cljc`

Effect:
- Leaves throughput on the table immediately
- Makes full loads less likely

### 4. Invasion pickup search is too narrow

`find-armies-for-invasion` only looks for already-coastal armies within a short range.

Relevant code:
- `src/empire/computer/transport/mission_handlers.cljc`

Current limits:
- `invasion-army-search-max-distance = 6`

Effect:
- Inland armies one or two turns from a valid pickup coast are ignored
- Transports revert or sail underloaded instead of pulling from the real invasion pool

### 5. Invasion transports give up too early

Invasion loading uses a fixed short timeout.

Relevant code:
- `src/empire/computer/transport/mission_handlers.cljc`
- `src/empire/computer/threat_response/major_invasion.cljc`

Current limits:
- `invasion-load-timeout-rounds = 5`

Effect:
- Transports can sail underloaded even when more armies are close to arrival
- Waves are smaller and slower than they need to be

### 6. Pickup targeting is too coarse

Return loading picks load targets by 5x5 tile buckets that must already contain at least 4 coastal armies.

Relevant code:
- `src/empire/computer/transport/load_targeting.cljc`

Current limits:
- `tile-size = 5`
- `min-armies-in-target-tile = 4`

Effect:
- Misses good pickup zones where armies are slightly more spread out
- Pushes transports toward coarse tile centers instead of true embark hubs

### 7. Opening coastal staging is too broad once any loading transport exists

As soon as any loading transport exists in the theater, coastal staging becomes broadly enabled.

Relevant code:
- `src/empire/computer/early_game/strategy.cljc`

Effect:
- Helps get armies moving toward water
- But diffuses them instead of concentrating them onto the best embarkation cells

## Best Improvements

### 1. Make boarding reservation-aware

Armies should first prefer:
- a transport whose `:load-manifest` includes them
- or a transport whose `:load-target-cell` matches their staging target

Only if no assignment exists should they fall back to the generic nearest loading transport.

Expected result:
- Less cross-traffic
- Better adherence to pickup plans
- Faster filling of invasion transports

### 2. Split loading policy by mission

Keep opportunistic loading for regular transports, but for `:load-for-invasion` or manifest-based pickup, reverse the priority so reserved armies load first.

Expected result:
- Regular transport flexibility is preserved
- Invasion transports become more deterministic and efficient

### 3. Raise staging and reservation capacity to full transport size

At minimum:
- change returning staging from 5 to 6

Better:
- reserve 6 loaders plus a small overflow queue so one blocked army does not stall departure

Expected result:
- More full loads
- Fewer wasted pickup cycles

### 4. Expand invasion pickup from coastal-only to near-coast routing

Instead of only searching for armies already on coastal cells, allow invasion pickup to recruit armies that are one short land route away from valid pickup coasts.

Expected result:
- Larger usable invasion pool
- Fewer empty or underfilled invasion transports

### 5. Replace the fixed invasion timeout with an adaptive departure rule

Suggested rule:
- wait longer if reserved armies are within a few turns
- sail early only when the source theater is depleted or the target is urgent

Expected result:
- Better load quality without excessive waiting
- More coherent invasion waves

### 6. Replace coarse tile targeting with persistent pickup hubs

Rather than choosing a fresh coarse tile with enough armies, select a specific embark coast for an invasion lane and keep shuttling between that hub and the target continent until the lane is exhausted.

Expected result:
- Better reuse of staging work
- Shorter search and retarget cycles
- Higher sustained invasion throughput

### 7. Tighten staging around actual embark cells

Instead of broadly moving armies toward coast-for-transport, armies assigned to a transport should queue on cells adjacent to that transport’s intended pickup area or sea approach.

Expected result:
- Faster boarding once the transport arrives
- Less random coastal diffusion

## Recommended Order

Implement in this order:

1. Reservation-aware boarding plus manifest-first invasion loading
2. Raise staging and reservation capacity from 5 to 6, and widen invasion pickup search
3. Replace the fixed invasion timeout with an adaptive departure rule
4. Introduce persistent pickup hubs and tighter embark-cell staging

The first two changes should provide the largest increase in invasion rate for the least behavioral risk.
