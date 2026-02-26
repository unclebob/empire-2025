# Early Satellites Design

## Goal

Produce satellites and patrol boats earlier in the game to improve computer visibility and scouting.

## Trigger

When the first computer transport reaches full army load (6 armies), a one-time global flag fires. This enables two one-shot production overrides: one early patrol boat, then one early satellite.

## Design

### 1. Three boolean atoms

- `transport-fully-loaded?` — set true when any computer transport reaches full load. Never reset.
- `early-patrol-boat-produced?` — set true when the early patrol boat enters production. Never reset.
- `early-satellite-produced?` — set true when the early satellite enters production. Never reset.

All added to `atoms.cljc`, `test_utils.cljc`, and `save_load.cljc`.

### 2. Trigger detection

In the transport loading logic (`computer/transport.cljc`), when a computer transport reaches full army load, set `transport-fully-loaded?` to true. Skip if already true.

### 3. Early production rules

In `decide-production`, before normal rules: if `transport-fully-loaded?` is true:

- **Patrol boat first:** If `early-patrol-boat-produced?` is false and city is coastal, produce `:patrol-boat` and set the flag.
- **Satellite second:** If `early-satellite-produced?` is false and patrol boat flag is true, produce `:satellite`. Prefer inland city (skip if coastal and let an inland city claim it). If no inland city claims it within a round, a coastal city takes it.

After each flag is set, normal rules resume.

### 4. Army limit reduction

Change `country-army-limit-reached?` from 100% to 2/3 of coastal cells. This frees cities earlier for other production.

### 5. Random explore timeout

Add `:random-explore-rounds` counter to armies entering `:random-explore` mode. Increment each round in `process-random-explore`. At 10 rounds, switch to `:awake` and clear random-explore fields — army falls through to `fill-coastal-cell`.
