# Fix Failing Acceptance Tests

## Overview

14 acceptance tests are failing across 4 areas. All represent unimplemented or incomplete computer AI features.

---

## 1. Computer Production (9 failures)

**Files:** `acceptanceTests/computer-production.txt`, `acceptanceTests/carrier-position.txt`

The computer production decision logic doesn't follow the expected priority chain. The acceptance tests define a three-tier system:

### Per-country priorities (in order):
1. **Transport** — coastal city, country has >= 6 armies but < 1 transport, no sibling city producing transports
2. **Army** — country has < 20 armies, no sibling city producing armies
3. **Patrol-boat** — coastal, country has < 1 patrol-boat
4. **Destroyer** — coastal, country has unadopted transport (no escort destroyer), no sibling producing destroyers
5. **Fighter** — country has < 2 fighters (does NOT require coastal)

### Global priorities (when per-country needs satisfied):
- Carrier, battleship, submarine, satellite — based on global unit counts and city thresholds

### Failing scenarios:

| Line | Scenario | Expected | Got |
|------|----------|----------|-----|
| 53 | Zero armies → army | :army | :fighter |
| 84 | Inland city, transport exists → army | :army | :fighter |
| 114 | Has patrol-boat, unadopted transport → destroyer | :destroyer | :patrol-boat |
| 173 | Per-country satisfied, 12 cities → carrier | :carrier | :patrol-boat |
| 195 | Per-country satisfied, carrier exists → battleship | :battleship | :patrol-boat |
| 215 | Per-country satisfied, carrier+battleship → submarine | :submarine | :patrol-boat |
| 256 | Transport in country (limit 1) → army | :army | :patrol-boat |
| 271 | 19 armies in transport, under cap → army | :army | :patrol-boat |
| carrier-position:17 | Distant cities, per-country satisfied → carrier | :carrier | :patrol-boat |

### Passing scenarios (6 of 14):
- Line 66: 6 armies → transport ✓
- Line 99: Army+transport caps met → patrol-boat ✓
- Line 131: Inland, army cap met → fighter ✓
- Line 145: Existing production not overwritten ✓
- Line 158: Production does not repeat after spawning ✓
- Line 235: Inland, per-country satisfied, 16 cities → satellite ✓

---

## 2. Computer Transport (3 failures)

**File:** `acceptanceTests/computer-transport.txt`

Transport mission state machine (idle → loading → unloading) is incomplete.

| Line | Scenario | Expected | Got |
|------|----------|----------|-----|
| 31 | Loading transport moves toward nearest army | pos [1 0] | [0 0] |
| 55 | Full transport (6 armies) switches to unloading | :unloading | :sailing |
| 69 | Full transport moves toward free city | :unloading | :sailing |

### Passing scenarios (2 of 5):
- Line 6: Idle transport → loading ✓
- Line 40: Loading transport explores when no army ✓

---

## 3. Transport Unloading (2 failures)

**File:** `acceptanceTests/transport.txt` (lines 126, 146)

| Line | Scenario | Expected | Got |
|------|----------|----------|-----|
| 126 | Heads toward other armies, not back to dropped ones | pos [0 1] | [0 3] |
| 146 | Prioritizes player city over free city | target [0 5] | nil |

---

## Status

- [ ] Computer Production — 9 failing
- [ ] Computer Transport — 3 failing
- [ ] Transport Unloading — 2 failing
