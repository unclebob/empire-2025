# Carrier Positioning Simplification

## Goal

Replace the expensive O(M + S³) carrier positioning algorithm with a simple city-pair reservation scheme.

## Current Problems

1. Two full map scans to find refueling sites
2. O(S²) pairs generated from all refueling sites (cities + carriers)
3. O(S³) spacing checks against all carriers
4. Fallback scans entire map

## New Scheme

1. **City pairs only** — Position carriers between pairs of computer cities
2. **Distance threshold** — Only pairs where distance > `fighter-fuel` (32) need a carrier
3. **One carrier per pair** — Each distant pair gets exactly one carrier
4. **Reservation** — Carrier stores its assigned city pair; no spacing checks needed

## Data Structure Changes

### Carrier Contents

Replace:
```clojure
:carrier-mode :positioning
:carrier-target [r c]
```

With:
```clojure
:carrier-mode :positioning
:carrier-pair [[r1 c1] [r2 c2]]  ; assigned city pair
:carrier-target [r c]            ; computed position between pair
```

## New Functions

### `find-distant-city-pairs`
```clojure
(defn find-distant-city-pairs
  "Returns all pairs of computer cities where distance > fighter-fuel."
  []
  ...)
```
- Complexity: O(C²) where C = number of computer cities
- Returns: seq of `[[r1 c1] [r2 c2]]` pairs, normalized (sorted) for deduplication

### `find-reserved-pairs`
```clojure
(defn find-reserved-pairs
  "Returns city pairs already assigned to carriers."
  []
  ...)
```
- Scan carriers for `:carrier-pair` field
- Include carriers in `:positioning` and `:holding` modes
- Complexity: O(K) where K = number of carriers (max 8)

### `find-unreserved-pair`
```clojure
(defn find-unreserved-pair
  "Returns a distant city pair not yet assigned to any carrier, or nil."
  []
  ...)
```
- Complexity: O(C² + K)

### `find-position-between-cities`
```clojure
(defn find-position-between-cities
  "Finds valid sea position near midpoint of two cities.
   Returns [r c] or nil."
  [[city1 city2]]
  ...)
```
- Compute midpoint
- Search radius-8 box around midpoint for empty sea
- Prefer position closest to midpoint
- Validate within fuel range of both cities
- Complexity: O(289) = O(1)

### `find-carrier-position` (replacement)
```clojure
(defn find-carrier-position
  "Finds a valid carrier position for an unreserved city pair.
   Returns {:pair [[r1 c1] [r2 c2]] :position [r c]} or nil."
  []
  ...)
```
- Call `find-unreserved-pair`
- If found, call `find-position-between-cities`
- Return both pair and position (carrier needs to store both)
- Complexity: O(C² + K + 289) = O(C²)

## Modified Functions

### `computer/stamping.cljc` — Carrier initialization

Add `:carrier-pair` field when spawning carrier:
```clojure
:carrier-pair [[r1 c1] [r2 c2]]
```

### `computer/production.cljc` — Production decision

Update `should-produce-carrier?` to use new `find-carrier-position` which returns pair info.

### `computer/ship.cljc` — Carrier movement

Update `position-carrier-with-target`:
- Validate that assigned pair is still valid (both cities still computer-owned)
- If pair invalid, clear assignment and call `find-unreserved-pair`

Update `position-carrier-without-target`:
- Call `find-unreserved-pair` and `find-position-between-cities`
- Store both pair and target

### `computer/ship.cljc` — Repositioning

Update repositioning logic:
- Trigger when assigned pair becomes invalid (city lost)
- Find new unreserved pair
- If no pair available, carrier becomes `:roaming` or stays `:holding`

## Edge Cases

1. **City captured** — Carrier's pair becomes invalid, must reposition
2. **No unreserved pairs** — No new carriers needed
3. **No valid sea position** — Rare; pair exists but no reachable sea between them
4. **Both cities lost** — Carrier must find entirely new pair

## Complexity Comparison

| Operation | Old | New |
|-----------|-----|-----|
| Find sites | O(M) | O(C²) |
| Generate pairs | O(S²) | O(C²) |
| Validate position | O(S) per cell | O(1) per cell |
| Find position | O(S³) | O(C² + 289) |
| **Total** | **O(M + S³)** | **O(C²)** |

With M=6000, S=30, C=20:
- Old: ~2.3 million operations worst case
- New: ~400 operations worst case

## Implementation Order

1. Add `find-distant-city-pairs` with tests
2. Add `find-reserved-pairs` with tests
3. Add `find-unreserved-pair` with tests
4. Add `find-position-between-cities` with tests
5. Replace `find-carrier-position`
6. Update `stamping.cljc` to store pair
7. Update `production.cljc` to use new return value
8. Update carrier movement to validate pair
9. Add repositioning on pair invalidation
10. Remove old functions: `find-refueling-sites`, `find-positioning-carrier-targets`, `valid-carrier-position?`

## Files Modified

- `src/empire/computer/ship.cljc` — Core positioning logic
- `src/empire/computer/stamping.cljc` — Carrier initialization
- `src/empire/computer/production.cljc` — Production decision
- `spec/empire/computer/ship_spec.clj` — Tests
