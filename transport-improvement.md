# Transport Improvement

This note tracks the baseline, the first transport changes, and the follow-up tuning of conditional beachhead reinforcement.

## Baseline And First Pass

Source note:
- See `tranport-improvement.md` for the original baseline and the first three step results.

Key results from that run series on seed `1774446200001`:
- Baseline: `12` cargo departures, `8` completed deliveries, first foreign landing `92`, converted continents `2/7`, army conquests `24`
- Step 1, manifest-first invasion loading: `14` departures, `11` deliveries, first foreign landing `89`, converted continents `4/7`, army conquests `33`
- Step 2, staging capacity `6`: `16` departures, `9` deliveries, first foreign landing `98`, converted continents `4/6`, army conquests `29`
- Step 3, absolute beachhead preference: `9` departures, `7` deliveries, first foreign landing `118`, converted continents `3/5`, army conquests `12`

Read:
- Step 1 was the clear win.
- Absolute beachhead preference was too sticky and suppressed total transport activity.

## Conditional Beachhead Tuning

Seed:
- `1774446200001`

Run command after each tuning step:

```bash
clj -M:run --headless=250 --log --seed=1774446200001
```

Planned tuning steps:
1. Release a beachhead once enough force is already committed there.
2. Stop reinforcing a beachhead after it stalls for too many rounds.
3. Cap how many transports can stay assigned to one beachhead.

## Results

### Reference: Absolute beachhead preference

Log:
- `empire-units2026-03-25-093804.log`

Result:
- Cargo departures: `9`
- Completed deliveries: `7`
- Armies loaded: `50`
- Armies unloaded: `31`
- Average departure load: `5.11 / 6`
- First foreign landing: `118`
- Foreign-landed continents: `5`
- Converted continents: `3 / 5`
- Army conquests: `12`

### Step A: Release once enough force is committed

Change:
- Only keep reinforcing a beachhead while the total committed force is below a threshold.
- Count both armies already on the continent and armies already loaded in transports assigned there.

Log:
- `empire-units2026-03-25-094647.log`

Result:
- Cargo departures: `11`
- Completed deliveries: `10`
- Armies loaded: `64`
- Armies unloaded: `42`
- Average departure load: `4.82 / 6`
- Average load factor: `80.3%`
- First foreign landing: `84`
- Foreign-landed continents: `5`
- Converted continents: `2 / 5`
- Army conquests: `21`

Read:
- This recovers most of the throughput lost by the absolute beachhead rule.
- First foreign landing is earlier than baseline and much earlier than the absolute beachhead version.
- It still does not match Step 1, but it is materially better than the untuned beachhead preference.

### Step B: Release stalled beachheads

Change:
- Track the last foreign unload round per beachhead continent.
- Stop preferring a beachhead after it stalls beyond the configured round window.

Log:
- `empire-units2026-03-25-094935.log`

Result:
- Cargo departures: `17`
- Completed deliveries: `15`
- Armies loaded: `123`
- Armies unloaded: `83`
- Average departure load: `5.76 / 6`
- Average load factor: `96.1%`
- First foreign landing: `120`
- Foreign-landed continents: `7`
- Converted continents: `3 / 7`
- Army conquests: `41`
- Round 250 status: `major invasion yes`

Read:
- This is the strongest raw throughput result so far.
- It also produced the highest conquest total, but it is no longer directly comparable to the earlier no-major-invasion baseline because the run crossed into major invasion by round `250`.
- Even so, the stall timer looks directionally right: it restores aggressive transport turnover instead of letting one beachhead monopolize the fleet.

### Step C: Cap transports per beachhead

Change:
- Limit how many transports can stay assigned to one beachhead at a time.
- Preserve the force gate and the stall timer, but stop piling additional transports onto the same target once the cap is reached.

Log:
- `empire-units2026-03-25-095318.log`

Result:
- Cargo departures: `10`
- Completed deliveries: `8`
- Armies loaded: `73`
- Armies unloaded: `43`
- Average departure load: `6.0 / 6`
- Average load factor: `100%`
- First foreign landing: `97`
- Foreign-landed continents: `5`
- Converted continents: `3 / 5`
- Army conquests: `15`
- Round 250 status: `major invasion no`

Read:
- The transport cap restores discipline and keeps departure quality perfect.
- It prevents overcommitment, but it also reduces overall conquest volume compared with Step A and especially Step B.
- This is safer than the raw stall-timer version, but not the strongest conquest result.

## Conclusion

Best raw result:
- Step B, the stall timer.

Why:
- Highest departures, deliveries, unloads, and total army conquests.
- It appears to fix the core Step 3 problem: transports stop getting stuck on dead beachheads.

Important caveat:
- Step B is not apples-to-apples with the original baseline because it entered major invasion by round `250`.

Best like-for-like conditional beachhead result while still ending `invasion no`:
- Step A, the force gate.

Why:
- It moves first foreign landing up to round `84`.
- It recovers most of the throughput lost by the absolute beachhead preference.
- It keeps the logic simple and avoids the heavy suppression seen in the untuned Step 3.

Current recommendation:
1. Keep Step 1 from the first pass: manifest-first invasion loading.
2. If you want a conservative beachhead refinement, keep Step A.
3. If you want the most aggressive version, Step B is the best candidate, but it should be evaluated over more seeds because it changes the strategic regime.
4. Step C is useful as a safety brake, but by itself it looks too restrictive.

## Clean Step 1 + Step 3 Trials

These reruns back out Step 2 and compare only Step 1 plus the Step 3 refinements.

### Trial: 1 + 3A

Configuration:
- Step 1 enabled
- Step 2 backed out
- Step 3A force gate enabled
- stall timer disabled
- transport cap disabled

Log:
- `empire-units2026-03-25-100528.log`

Result:
- Cargo departures: `10`
- Completed deliveries: `9`
- Armies loaded: `66`
- Armies unloaded: `40`
- Average departure load: `5.6 / 6`
- Average load factor: `93.3%`
- First foreign landing: `98`
- Foreign-landed continents: `4`
- Converted continents: `1 / 4`
- Army conquests: `18`
- Round 250 status: `major invasion yes`

### Trial: 1 + 3B

Configuration:
- Step 1 enabled
- Step 2 backed out
- Step 3A force gate enabled
- Step 3B stall timer enabled
- transport cap disabled

Log:
- `empire-units2026-03-25-100705.log`

Result:
- Cargo departures: `11`
- Completed deliveries: `9`
- Armies loaded: `79`
- Armies unloaded: `43`
- Average departure load: `6.0 / 6`
- Average load factor: `100%`
- First foreign landing: `88`
- Foreign-landed continents: `7`
- Converted continents: `2 / 7`
- Army conquests: `22`
- Round 250 status: `major invasion no`

### Trial: 1 + 3C

Configuration:
- Step 1 enabled
- Step 2 backed out
- Step 3A force gate enabled
- Step 3B stall timer enabled
- Step 3C transport cap enabled

Log:
- `empire-units2026-03-25-100833.log`

Result:
- Cargo departures: `3`
- Completed deliveries: `3`
- Armies loaded: `28`
- Armies unloaded: `18`
- Average departure load: `6.0 / 6`
- Average load factor: `100%`
- First foreign landing: `89`
- Foreign-landed continents: `3`
- Converted continents: `0 / 3`
- Army conquests: `2`
- Round 250 status: `major invasion no`

## Clean Comparison To Baseline

Baseline:
- Cargo departures: `12`
- Completed deliveries: `8`
- Armies loaded: `78`
- Armies unloaded: `53`
- Average departure load: `5.08 / 6`
- Average load factor: `84.7%`
- First foreign landing: `92`
- Foreign-landed continents: `7`
- Converted continents: `2 / 7`
- Army conquests: `24`

Compared to baseline:

- `1 + 3A`
  - better: deliveries, departure quality
  - worse: departures, unload volume, converted continents, total conquests
  - later first foreign landing than baseline
  - not an improvement overall

- `1 + 3B`
  - better: first foreign landing, deliveries, departure quality
  - equal: converted continents `2 / 7`
  - worse: departures slightly, unload volume, total conquests slightly
  - mixed result, but the strongest of the clean Step 1 + Step 3 variants

- `1 + 3C`
  - much worse across almost every meaningful metric except departure quality
  - clearly too restrictive

Read:
- Of the clean variants, `1 + 3B` is the only one worth further investigation.
- It improves timing and load discipline, but on this seed it still does not beat baseline on conquest volume.
- `1 + 3C` should be rejected.

## Four-Run Geography Baseline

Chosen geography seed:
- `1774446200110`

Reason:
- among 10 sampled seeds, it had the greatest initial player/computer city separation
- player city `[85 26]`
- computer city `[9 49]`
- Chebyshev distance `76`

Four 250-round baseline logs:
- `empire-units2026-03-25-104924.log`
- `empire-units2026-03-25-105831.log`
- `empire-units2026-03-25-110149.log`
- `empire-units2026-03-25-110610.log`

Average across the four runs:
- Transports seen: `20.25`
- Cargo departures: `24.25`
- Completed deliveries: `19.25`
- Armies loaded: `137.75`
- Armies unloaded: `82.5`
- Average departure load: `5.67 / 6`
- Average load factor: `94.6%`
- Underfilled departures: `2.0`
- Departures below 4 armies: `1.75`
- First foreign landing: round `91.0`
- First foreign conquest: round `103.75`
- Gap from first landing to first conquest: `12.75`
- Foreign-landed continents: `9.25`
- Converted continents: `4.75`
- Army-discovered cells: `387.0`
- Army conquests: `36.25`

Per-run conversion:
- run 1: `5 / 7` landed continents converted, `34` army conquests
- run 2: `3 / 9` landed continents converted, `38` army conquests
- run 3: `5 / 12` landed continents converted, `37` army conquests
- run 4: `6 / 9` landed continents converted, `36` army conquests

Interpretation:
- this seed supports consistently strong transport throughput
- beachhead conversion still varies materially from run to run, even on the same geography
- future transport changes should be compared against this four-run average baseline rather than a single run
