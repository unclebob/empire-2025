# Consolidate Atoms Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace 75 individual atoms with 4 grouped atoms (world, computer, player, ui), hiding them behind the existing `state.api` so no callers change.

**Architecture:** Each group becomes a single `(atom {...})` in its own private namespace. `state.api` maps keywords to group atoms internally using `get`/`assoc`/`update` on the appropriate grouped atom. All callers continue using `sa/read-state`, `sa/write-state!`, `sa/update-state!` with the same keywords.

**Tech Stack:** Clojure, Speclj

---

## Group Definitions

### World (maps, production, rounds, topology)
Keys and default values:
```clojure
{:random-seed nil
 :map-size [0 0]
 :map-size-constants {}
 :round-number 0
 :production {}
 :game-map nil
 :player-map {}
 :computer-map {}
 :continent-groups {}
 :next-country-id 1
 :game-over-check-enabled true}
```

### Computer (AI caches, IDs, objectives)
Keys and default values:
```clojure
{:computer-items []
 :computer-turn false
 :claimed-objectives #{}
 :claimed-transport-targets #{}
 :claimed-patrol-targets #{}
 :last-transport-city {}
 :fighter-leg-records {}
 :computer-city-positions #{}
 :computer-carrier-positions #{}
 :country-stats {}
 :coastal-cells-by-country {}
 :coast-walkers-produced {}
 :patrol-boats-produced {}
 :seen-coast #{}
 :land-ho-targets []
 :major-invasion-state {:active? false
                        :detection-points #{}
                        :target-land-set #{}
                        :started-round nil}
 :transport-fully-loaded? false
 :early-patrol-boat-produced? false
 :early-satellite-produced? false
 :computer-event-log []
 :distant-city-pairs nil
 :lake-max-cells 0
 :known-lake-cells #{}
 :next-transport-id 1
 :next-unload-event-id 1
 :next-destroyer-id 1
 :next-carrier-id 1
 :next-escort-id 1}
```

### Player (turn mechanics, attention)
Keys and default values:
```clojure
{:player-items []
 :cells-needing-attention []
 :waiting-for-input false
 :destination nil
 :paused false
 :pause-requested false}
```

### UI (display, messages, fonts, input, debug, logging)
Keys and default values:
```clojure
{:last-key nil
 :backtick-pressed false
 :last-clicked-cell nil
 :map-screen-dimensions [0 0]
 :text-area-dimensions [0 0 0 0]
 :map-to-display :player-map
 :text-font nil
 :production-char-font nil
 :attention-message ""
 :turn-message ""
 :turn-message-until 0
 :hover-message ""
 :error-message ""
 :error-until 0
 :production-status ""
 :debug-drag-start nil
 :debug-drag-current nil
 :debug-message ""
 :action-log []
 :player-movement-log []
 :load-menu-open false
 :load-menu-files []
 :load-menu-hovered nil}
```

## File Plan

| File | Action |
|---|---|
| `src/empire/state/world.cljc` | Create — single atom + defaults |
| `src/empire/state/computer.cljc` | Create — single atom + defaults |
| `src/empire/state/player.cljc` | Create — single atom + defaults |
| `src/empire/state/ui.cljc` | Create — single atom + defaults |
| `src/empire/state/api.cljc` | Rewrite — route keys to group atoms |
| `src/empire/state/atoms.cljc` | Delete |
| `src/empire/state/runtime.cljc` | Delete |
| `src/empire/test/utils.cljc` | Update `reset-all-atoms!` |

No other files change — all callers go through `state.api`.

---

### Task 1: Create `state/world.cljc`

**Files:**
- Create: `src/empire/state/world.cljc`

**Step 1: Create the world atom namespace**

```clojure
(ns empire.state.world)

(def defaults
  {:random-seed nil
   :map-size [0 0]
   :map-size-constants {}
   :round-number 0
   :production {}
   :game-map nil
   :player-map {}
   :computer-map {}
   :continent-groups {}
   :next-country-id 1
   :game-over-check-enabled true})

(def state (atom defaults))
```

**Step 2: Run tests to verify no breakage**

Run: `clj -M:spec`
Expected: All existing tests pass (new file is not yet wired in).

**Step 3: Commit**
```
feat: add state/world.cljc — grouped world atom
```

---

### Task 2: Create `state/computer.cljc`

**Files:**
- Create: `src/empire/state/computer.cljc`

**Step 1: Create the computer atom namespace**

```clojure
(ns empire.state.computer)

(def defaults
  {:computer-items []
   :computer-turn false
   :claimed-objectives #{}
   :claimed-transport-targets #{}
   :claimed-patrol-targets #{}
   :last-transport-city {}
   :fighter-leg-records {}
   :computer-city-positions #{}
   :computer-carrier-positions #{}
   :country-stats {}
   :coastal-cells-by-country {}
   :coast-walkers-produced {}
   :patrol-boats-produced {}
   :seen-coast #{}
   :land-ho-targets []
   :major-invasion-state {:active? false
                          :detection-points #{}
                          :target-land-set #{}
                          :started-round nil}
   :transport-fully-loaded? false
   :early-patrol-boat-produced? false
   :early-satellite-produced? false
   :computer-event-log []
   :distant-city-pairs nil
   :lake-max-cells 0
   :known-lake-cells #{}
   :next-transport-id 1
   :next-unload-event-id 1
   :next-destroyer-id 1
   :next-carrier-id 1
   :next-escort-id 1})

(def state (atom defaults))
```

**Step 2: Run tests**

Run: `clj -M:spec`
Expected: All existing tests pass.

**Step 3: Commit**
```
feat: add state/computer.cljc — grouped computer atom
```

---

### Task 3: Create `state/player.cljc`

**Files:**
- Create: `src/empire/state/player.cljc`

**Step 1: Create the player atom namespace**

```clojure
(ns empire.state.player)

(def defaults
  {:player-items []
   :cells-needing-attention []
   :waiting-for-input false
   :destination nil
   :paused false
   :pause-requested false})

(def state (atom defaults))
```

**Step 2: Run tests**

Run: `clj -M:spec`
Expected: All existing tests pass.

**Step 3: Commit**
```
feat: add state/player.cljc — grouped player atom
```

---

### Task 4: Create `state/ui.cljc`

**Files:**
- Create: `src/empire/state/ui.cljc`

**Step 1: Create the ui atom namespace**

```clojure
(ns empire.state.ui)

(def defaults
  {:last-key nil
   :backtick-pressed false
   :last-clicked-cell nil
   :map-screen-dimensions [0 0]
   :text-area-dimensions [0 0 0 0]
   :map-to-display :player-map
   :text-font nil
   :production-char-font nil
   :attention-message ""
   :turn-message ""
   :turn-message-until 0
   :hover-message ""
   :error-message ""
   :error-until 0
   :production-status ""
   :debug-drag-start nil
   :debug-drag-current nil
   :debug-message ""
   :action-log []
   :player-movement-log []
   :load-menu-open false
   :load-menu-files []
   :load-menu-hovered nil})

(def state (atom defaults))
```

**Step 2: Run tests**

Run: `clj -M:spec`
Expected: All existing tests pass.

**Step 3: Commit**
```
feat: add state/ui.cljc — grouped ui atom
```

---

### Task 5: Rewrite `state/api.cljc`

**Files:**
- Modify: `src/empire/state/api.cljc`

**Step 1: Rewrite api.cljc to route keys to grouped atoms**

Replace the entire file with:

```clojure
(ns empire.state.api
  "Direct atom-backed state access. Public boundary for all game state."
  (:require [empire.state.world :as world]
            [empire.state.computer :as computer]
            [empire.state.player :as player]
            [empire.state.ui :as ui]
            [empire.config.domain.core.continents :as continents]
            [empire.config.domain.core.refueling :as refueling]))

(def ^:private key->group
  (merge
    (zipmap (keys world/defaults) (repeat ::world))
    (zipmap (keys computer/defaults) (repeat ::computer))
    (zipmap (keys player/defaults) (repeat ::player))
    (zipmap (keys ui/defaults) (repeat ::ui))))

(defn- group-atom [k]
  (case (or (get key->group k)
            (throw (ex-info (str "Unknown state key: " k) {:key k})))
    ::world world/state
    ::computer computer/state
    ::player player/state
    ::ui ui/state))

(defn current-world [] (:game-map @world/state))

(defn update-world! [f & args]
  (apply swap! world/state update :game-map f args))

(defn read-state [k] (get @(group-atom k) k))

(defn write-state! [k v] (swap! (group-atom k) assoc k v))

(defn update-state! [k f & args]
  (apply swap! (group-atom k) update k f args))

(defn merge-continents! [stamp-id existing-cid]
  (swap! world/state update :continent-groups
         continents/merge-continents stamp-id existing-cid))

(defn on-same-continent? [cid1 cid2]
  (continents/on-same-continent? (:continent-groups @world/state) cid1 cid2))

(defn rebuild-refueling-caches! []
  (let [{:keys [cities carriers]}
        (refueling/scan-refueling-positions (:game-map @world/state))]
    (swap! computer/state assoc
           :computer-city-positions cities
           :computer-carrier-positions carriers)))

(defn world-atom [] world/state)
```

**Step 2: Run tests to verify all callers still work**

Run: `clj -M:spec`
Expected: All tests pass. Every caller uses `sa/read-state`, `sa/write-state!`, `sa/update-state!` with keyword keys — the routing is transparent.

**Step 3: Commit**
```
refactor: rewrite state/api to route keys to 4 grouped atoms
```

---

### Task 6: Update `test/utils.cljc` reset

**Files:**
- Modify: `src/empire/test/utils.cljc`

**Step 1: Simplify `reset-all-atoms!` to reset the four group atoms**

Replace the `reset-all-atoms!` function (lines 251-323) with:

```clojure
(defn reset-all-atoms! []
  (require 'empire.state.world 'empire.state.computer
           'empire.state.player 'empire.state.ui)
  (reset! @(resolve 'empire.state.world/state)
          @(resolve 'empire.state.world/defaults))
  (reset! @(resolve 'empire.state.computer/state)
          @(resolve 'empire.state.computer/defaults))
  (reset! @(resolve 'empire.state.player/state)
          @(resolve 'empire.state.player/defaults))
  (reset! @(resolve 'empire.state.ui/state)
          @(resolve 'empire.state.ui/defaults))
  (pathfinding/clear-path-cache)
  (pathfinding-bfs/clear-bfs-caches)
  (land-objectives/clear-continent-cache!)
  (visibility/drain-detections!))
```

Wait — the architecture rules forbid `resolve`/`ns-resolve`. Since `test/utils.cljc` already requires `empire.state.api`, just add requires for the four group namespaces:

Add to the `:require` vector:
```clojure
[empire.state.world :as world]
[empire.state.computer :as computer-state]
[empire.state.player :as player-state]
[empire.state.ui :as ui-state]
```

Replace `reset-all-atoms!`:
```clojure
(defn reset-all-atoms! []
  (reset! world/state world/defaults)
  (reset! computer-state/state computer-state/defaults)
  (reset! player-state/state player-state/defaults)
  (reset! ui-state/state ui-state/defaults)
  (pathfinding/clear-path-cache)
  (pathfinding-bfs/clear-bfs-caches)
  (land-objectives/clear-continent-cache!)
  (visibility/drain-detections!))
```

Note: `game-over-check-enabled` defaults to `true` in `world/defaults` but was reset to `false` in tests. This must be preserved. Override after the bulk reset:

```clojure
(defn reset-all-atoms! []
  (reset! world/state world/defaults)
  (reset! computer-state/state computer-state/defaults)
  (reset! player-state/state player-state/defaults)
  (reset! ui-state/state ui-state/defaults)
  (sa/write-state! :game-over-check-enabled false)
  (pathfinding/clear-path-cache)
  (pathfinding-bfs/clear-bfs-caches)
  (land-objectives/clear-continent-cache!)
  (visibility/drain-detections!))
```

**Step 2: Run tests**

Run: `clj -M:spec`
Expected: All tests pass. The reset function now resets 4 atoms instead of 75 individual writes.

**Step 3: Commit**
```
refactor: simplify reset-all-atoms! to reset 4 grouped atoms
```

---

### Task 7: Fix `set-test-world!` and `world-atom` usage

**Files:**
- Modify: `src/empire/test/utils.cljc`
- Modify: `src/empire/state/api.cljc`

The old `world-atom` returned a bare atom that callers could `reset!` directly. Now `world/state` is a map-atom where `:game-map` is one key. `set-test-world!` calls `reset! (sa/world-atom) world` — this would clobber the entire world state.

**Step 1: Fix `set-test-world!`**

Change:
```clojure
(defn set-test-world! [world]
  (reset! (sa/world-atom) world))
```
To:
```clojure
(defn set-test-world! [world]
  (sa/write-state! :game-map world))
```

**Step 2: Check all callers of `world-atom`**

Search for `world-atom` across the codebase. If any callers do `@(sa/world-atom)` to get the game map, they need to switch to `(sa/current-world)`. If any callers do `swap!` on it, they need `sa/update-world!`.

Audit and fix each caller.

**Step 3: Run tests**

Run: `clj -M:spec`
Expected: All tests pass.

**Step 4: Commit**
```
fix: update world-atom callers for grouped atom structure
```

---

### Task 8: Delete old atom files

**Files:**
- Delete: `src/empire/state/atoms.cljc`
- Delete: `src/empire/state/runtime.cljc`

**Step 1: Delete the files**

```bash
rm -f src/empire/state/atoms.cljc src/empire/state/runtime.cljc
```

**Step 2: Run tests**

Run: `clj -M:spec`
Expected: All tests pass. No file references `empire.state.atoms` or `empire.state.runtime` anymore (only `state.api` did, and it was rewritten in Task 5).

**Step 3: Commit**
```
delete: remove atoms.cljc and runtime.cljc — replaced by 4 grouped atoms
```

---

### Task 9: Run acceptance tests

**Step 1: Run full acceptance pipeline**

```bash
clj -M:parse-tests && clj -M:generate-specs && clj -M:spec generated-acceptance-specs/
```

Expected: All acceptance tests pass.

**Step 2: Run spec structure check**

```bash
clj -M:spec-structure-check
```

Expected: OK

**Step 3: Commit (if any fixes needed)**

---

### Task 10: Verify helper functions migrated

The old `atoms.cljc` had helper functions that are no longer needed by external callers:
- `set-error-message` / `set-turn-message` — only called from within `atoms.cljc` itself, now dead
- `on-same-continent?` / `merge-continents!` — moved to `state/api.cljc` in Task 5
- `rebuild-refueling-caches!` — moved to `state/api.cljc` in Task 5
- `computer-city-cell?` / `computer-carrier-cell?` — these delegate to `refueling` module; check if any caller uses them through `atoms/` or `sa/`

**Step 1: Search for any remaining references to atoms or runtime**

```bash
grep -r "empire.state.atoms\|empire.state.runtime\|state\.atoms\|state\.runtime" src/ spec/
```

Expected: Zero matches.

**Step 2: Search for `computer-city-cell?` and `computer-carrier-cell?` callers**

These were on `atoms.cljc` but are pure functions (no atom access). If callers exist, move them to `state/api.cljc` or let callers use `refueling` directly.

**Step 3: Run tests, commit if needed**

```
chore: verify no references to deleted atom files remain
```
