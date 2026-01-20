# Phase 5: Repair Mechanics - Implementation Plan

## Overview

Ships that dock in a friendly city can repair 1 hit point per turn. The city's `:shipyard` field holds docked ships. When a ship is fully repaired, it wakes up; damaged ships remain asleep.

---

## Docking Rules

- **Damaged ship moving to friendly city** → automatically docks in shipyard
- **Undamaged ship** → cannot move into city (movement blocked)
- Ships are never in cell `:contents` at a city - always in `:shipyard`
- No explicit "dock" command needed

---

## Shipyard Data Structure

**Minimal ship entry in shipyard (only essential fields):**
```clojure
{:type :destroyer :hits 2}
```

- `:owner` - Implicit from city's `:city-status`
- `:mode` - Always `:sentry` while docked, `:awake` when launched
- Other fields - Reconstructed from dispatcher defaults on launch

**City with shipyard:**
```clojure
{:type :city
 :city-status :player
 :shipyard [{:type :destroyer :hits 2}
            {:type :battleship :hits 7}]}
```

---

## Implementation Steps

### Step 1: Add Shipyard Helper Functions

**File: `src/empire/unit_container.cljc`**

Add helper functions for shipyard management:

```clojure
(defn add-ship-to-shipyard [city ship-type hits])  ;; Add {:type t :hits h} to :shipyard
(defn remove-ship-from-shipyard [city index])      ;; Remove ship at index
(defn get-shipyard-ships [city])                   ;; Get list of docked ships
```

---

### Step 2: Modify Ship Movement Validation

**Files: `src/empire/units/destroyer.cljc`, `submarine.cljc`, `battleship.cljc`, `carrier.cljc`, `transport.cljc`, `patrol_boat.cljc`**

Update `can-move-to?` for each ship type:

```clojure
(defn can-move-to? [ship cell]
  (cond
    (= :city (:type cell))
    (and (friendly-city? ship cell)                         ;; friendly city
         (< (:hits ship) (dispatcher/hits (:type ship))))   ;; ship is damaged

    (= :sea (:type cell))
    (nil? (:contents cell))  ;; empty sea

    :else false))
```

---

### Step 3: Handle Docking in Movement

**File: `src/empire/movement.cljc`**

When `move-unit` moves a ship to a city cell:
- Don't place ship in `:contents`
- Add `{:type ship-type :hits ship-hits}` to city's `:shipyard`
- Ship is automatically sleeping while docked

---

### Step 4: Implement `repair-damaged-ships` Function

**File: `src/empire/game_loop.cljc`**

```clojure
(defn repair-damaged-ships []
  "Repair 1 hit point for each ship docked in a friendly city.
   Wake ships that reach full health."
  ;; For each city in game-map:
  ;;   If city has :shipyard with ships:
  ;;     For each ship in shipyard:
  ;;       Increment :hits by 1 (cap at dispatcher/hits for type)
  ;;       If ship at full health:
  ;;         Launch ship onto map with :mode :awake
  )
```

**Rate:** 1 hit per turn per ship

---

### Step 5: Integrate Repair into Game Loop

**File: `src/empire/game_loop.cljc`**

Modify `start-new-round`:

```clojure
(defn start-new-round []
  (swap! atoms/round-number inc)
  (move-satellites)
  (consume-sentry-fighter-fuel)
  (wake-sentries-seeing-enemy)
  (remove-dead-units)
  (production/update-production)
  (repair-damaged-ships)              ;; <-- NEW
  (reset-steps-remaining)
  (wake-airport-fighters)
  ;; ... rest of function
  )
```

---

### Step 6: Ship Launching (on full repair)

**File: `src/empire/container_ops.cljc`**

```clojure
(defn launch-ship-from-shipyard [game-map city-coords ship-index]
  "Remove ship from city's shipyard and place on map at city cell.
   Reconstruct full unit from minimal shipyard data.")
```

**Reconstruct full unit on launch:**
```clojure
{:type ship-type
 :owner city-owner              ;; from city's :city-status
 :hits ship-hits                ;; from shipyard data (now at max)
 :mode :awake
 :steps-remaining (dispatcher/speed ship-type)}
```

---

### Step 7: Update Test Utilities

**File: `spec/empire/test_utils.cljc`**

Add helper for creating cities with shipyards:

```clojure
(defn build-city-with-shipyard [status ships]
  "Create a city cell with ships in the shipyard.")
```

---

## Test Plan

**File: `spec/empire/repair_spec.clj`** (new file)

| Test | Description |
|------|-------------|
| `damaged-ship-can-move-to-friendly-city` | Movement validation allows damaged ship to enter |
| `undamaged-ship-cannot-move-to-city` | Movement validation blocks undamaged ship |
| `ship-enters-shipyard-on-move` | Ship added to `:shipyard`, not `:contents` |
| `ship-removed-from-map-when-docked` | Ship disappears from original cell |
| `repair-increases-hits-by-one` | After one round, ship has +1 hit |
| `repair-capped-at-max-hits` | Ship doesn't exceed its max hits |
| `fully-repaired-ship-launched` | Ship with max hits placed on map with `:mode :awake` |
| `damaged-ship-stays-in-shipyard` | Ship with < max hits remains docked |
| `multiple-ships-repair-per-round` | Each ship in shipyard repairs independently |
| `only-friendly-cities-repair` | Enemy/free cities don't have shipyards |
| `computer-ships-also-repair` | Same rules apply to computer ships |

---

## Files to Create/Modify

| File | Action | Purpose |
|------|--------|---------|
| `src/empire/unit_container.cljc` | Modify | Add shipyard helper functions |
| `src/empire/units/*.cljc` | Modify | Update `can-move-to?` for all ship types |
| `src/empire/movement.cljc` | Modify | Handle docking when moving to city |
| `src/empire/container_ops.cljc` | Modify | Add `launch-ship-from-shipyard` |
| `src/empire/game_loop.cljc` | Modify | Add `repair-damaged-ships`, integrate into `start-new-round` |
| `spec/empire/repair_spec.clj` | Create | Tests for repair mechanics |
| `spec/empire/test_utils.cljc` | Modify | Add shipyard test helpers |

---

## Implementation Order

1. Write tests for shipyard helpers → implement helpers in `unit_container.cljc`
2. Write tests for movement validation → update `can-move-to?` in ship modules
3. Write tests for docking on move → modify `movement.cljc`
4. Write tests for repair function → implement `repair-damaged-ships`
5. Write tests for ship launching → implement `launch-ship-from-shipyard`
6. Integration: wire `repair-damaged-ships` into `start-new-round`
