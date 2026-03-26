# Transport Improvement

This note tracks the current baseline and the planned transport changes aimed at reducing the gap between first foreign landing and sustained conquest.

## Baseline

Seed: `1774446200001`

Run:

```bash
clj -M:run --headless=250 --log --seed=1774446200001
```

Log:
- `empire-units2026-03-25-091614.log`

Aggregate transport baseline:
- Transports seen: `12`
- Cargo departures: `12`
- Completed deliveries: `8`
- Armies loaded: `78`
- Armies unloaded: `53`
- Average departure load: `5.08 / 6`
- Average load factor: `84.7%`
- First foreign landing: round `92`
- Foreign landing events by round `250`: `7`

Army baseline:
- Army conquest events: `24`

Foreign landing to conquest conversion:
- Continent `[78 47]`: first landing `92`, no conquest by `250`
- Continent `[66 33]`: first landing `162`, first conquest `165`, gap `3`, conquests `2`
- Continent `[52 57]`: first landing `179`, no conquest by `250`
- Continent `[87 28]`: first landing `198`, no conquest by `250`
- Continent `[60 34]`: first landing `201`, no conquest by `250`
- Continent `[47 22]`: first landing `228`, no conquest by `250`
- Continent `[43 36]`: first landing `244`, first conquest `245`, gap `1`, conquests `1`

Key baseline read:
- First foreign landing occurs at round `92`
- Only `2` of `7` foreign-landed continents convert into conquest by round `250`
- The transport system reaches continents more often than it converts them into beachheads

## Strategy

The first variable to improve is conversion from foreign landing to conquest. The main implementation lever is to concentrate transport waves on active beachheads instead of spreading landings across many continents.

Planned steps:

1. Make invasion loading manifest-first rather than opportunistic-first.
2. Raise returning transport staging capacity from `5` to `6`.
3. Prefer reinforcing an active foreign beachhead continent before opening a new one.

## Measurement

After each step, rerun:

```bash
clj -M:run --headless=250 --log --seed=1774446200001
```

Track:
- cargo departures
- completed deliveries
- average departure load
- first foreign landing round
- number of foreign-landed continents
- number of foreign-landed continents that produce conquest by round `250`
- first conquest round per invaded continent
- conquest totals per invaded continent

## Step Results

### Step 1: Manifest-first invasion loading

Change:
- In `:load-for-invasion`, load manifest armies before opportunistic adjacent armies.
- Regular transport loading remains opportunistic-first.

Run:
- `empire-units2026-03-25-092639.log`

Result:
- Cargo departures: `14` (baseline `12`)
- Completed deliveries: `11` (baseline `8`)
- Armies loaded: `88` (baseline `78`)
- Armies unloaded: `60` (baseline `53`)
- Average departure load: `6.0 / 6` (baseline `5.08 / 6`)
- First foreign landing: `89` (baseline `92`)
- Foreign-landed continents: `7` (same as baseline)
- Converted continents: `4 / 7` (baseline `2 / 7`)
- Army conquests: `33` (baseline `24`)

Read:
- This improved both throughput and conversion.
- It is a keeper.

### Step 2: Raise returning staging capacity to 6

Change:
- Raise returning transport staging from `5` armies to `6`.

Run:
- `empire-units2026-03-25-092950.log`

Result:
- Cargo departures: `16` (Step 1 `14`, baseline `12`)
- Completed deliveries: `9` (Step 1 `11`, baseline `8`)
- Armies loaded: `113` (Step 1 `88`, baseline `78`)
- Armies unloaded: `62` (Step 1 `60`, baseline `53`)
- Average departure load: `5.0 / 6` (Step 1 `6.0 / 6`, baseline `5.08 / 6`)
- Average load factor: `83.3%` (Step 1 `100%`, baseline `84.7%`)
- First foreign landing: `98` (Step 1 `89`, baseline `92`)
- Foreign-landed continents: `6`
- Converted continents: `4 / 6` (Step 1 `4 / 7`, baseline `2 / 7`)
- Army conquests: `29` (Step 1 `33`, baseline `24`)

Read:
- This increased transport activity but weakened departure quality.
- It did not improve conversion over Step 1, and it delayed first foreign landing.
- On this seed it also pushed the game into major invasion by round `250`, so the regime changed.

### Step 3: Prefer reinforcing active beachheads

Change:
- When planning transport major invasion targets, prefer a foreign continent that already has computer land units but no computer city yet.
- Fall back to the old major-invasion target picker only when no such beachhead exists.

Run:
- `empire-units2026-03-25-093804.log`

Result:
- Cargo departures: `9` (Step 2 `16`, Step 1 `14`, baseline `12`)
- Completed deliveries: `7` (Step 2 `9`, Step 1 `11`, baseline `8`)
- Armies loaded: `50` (Step 2 `113`, Step 1 `88`, baseline `78`)
- Armies unloaded: `31` (Step 2 `62`, Step 1 `60`, baseline `53`)
- Average departure load: `5.11 / 6` (Step 2 `5.0 / 6`, Step 1 `6.0 / 6`, baseline `5.08 / 6`)
- Average load factor: `85.2%` (Step 2 `83.3%`, Step 1 `100%`, baseline `84.7%`)
- First foreign landing: `118` (Step 2 `98`, Step 1 `89`, baseline `92`)
- Foreign-landed continents: `5`
- Converted continents: `3 / 5` (Step 2 `4 / 6`, Step 1 `4 / 7`, baseline `2 / 7`)
- Army conquests: `12` (Step 2 `29`, Step 1 `33`, baseline `24`)

Read:
- This concentrated landings more narrowly, but it slowed the whole transport pipeline too much.
- Conversion fraction improved over baseline, but total throughput and total conquests dropped sharply.
- On this seed, the beachhead preference is too strong as implemented.

## Conclusion

Best result on this seed:
- Step 1 only.

Why:
- It improved both transport throughput and beachhead conversion.
- It produced the earliest foreign landing and the highest total conquest count.

Current recommendation:
1. Keep Step 1: manifest-first invasion loading.
2. Do not keep Step 2 as a blanket staging-capacity change without more targeting work.
3. Rework Step 3 before keeping it. The idea is still strategically sound, but the current implementation over-concentrates and suppresses total transport activity.

Next likely refinement:
- make beachhead preference conditional rather than absolute
- only stick to a beachhead while it has recent unloads and fewer than a target reinforcement count
- then reopen new continent targeting once that threshold is met or the beachhead stalls
