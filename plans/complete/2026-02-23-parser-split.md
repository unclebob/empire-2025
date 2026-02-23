# Parser Split Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Split the 1186-line acceptance test parser into 4-5 files along GIVEN/WHEN/THEN seams to reduce context window pressure.

**Architecture:** Extract helpers, GIVEN, WHEN, and THEN parsing into `parser/` subdirectory. The top-level `parser.cljc` becomes a thin facade requiring the sub-namespaces and delegating. Split the spec along the same seams.

**Tech Stack:** Clojure (`.cljc`), Speclj test framework

---

### Task 1: Create `parser/helpers.cljc`

**Files:**
- Create: `src/empire/acceptance/parser/helpers.cljc`
- Source: `src/empire/acceptance/parser.cljc:7-112`

**Step 1: Create the helpers file**

Copy lines 7-112 from `parser.cljc` into a new file with adjusted namespace and visibility. All `defn-` become `defn` (these were private in the monolith but are now cross-namespace).

```clojure
(ns empire.acceptance.parser.helpers
  (:require [clojure.string :as str]))

;; --- Helpers ---

(defn strip-trailing-period [s]
  (if (str/ends-with? s ".")
    (subs s 0 (dec (count s)))
    s))

(defn strip-keyword-prefix [line]
  (-> line
      (str/replace #"^(?:GIVEN|WHEN|THEN)\s+" "")
      str/trim))

(defn blank-or-comment? [line]
  (or (str/blank? line)
      (str/starts-with? (str/trim line) ";")))

(defn separator-line? [line]
  (re-matches #"\s*;=+\s*" line))

(defn map-row? [line]
  ;; ... exact copy of current L26-32
  )

(defn territory-map-row? [line]
  ;; ... exact copy of current L34-39
  )

(defn lowercase-direction? [k]
  ;; ... exact copy of L41-42
  )

(defn uppercase-direction? [k]
  ;; ... exact copy of L44-48
  )

(defn parse-coords [s]
  ;; ... exact copy of L50-52
  )

(defn parse-number [s]
  ;; ... exact copy of L54-55
  )

(def unit-name->char
  ;; ... exact copy of L57-63
  )

(def player-unit-chars #{"A" "F" "T" "D" "C" "S" "B" "P" "V"})
(def computer-unit-chars #{"a" "f" "t" "d" "c" "s" "b" "p" "v"})
(def city-chars #{"X" "+" "*"})

(defn unit-char? [s]
  (or (contains? player-unit-chars s)
      (contains? computer-unit-chars s)
      (contains? #{"O" "o"} s)))

(defn city-or-unit-char? [s]
  (or (unit-char? s) (contains? city-chars s)))

(def cell-prop-aliases
  ;; ... exact copy of L78-89
  )

(defn parse-count [s]
  ;; ... exact copy of L82-84
  )

(defn resolve-cell-prop [k]
  (or (get cell-prop-aliases k) (keyword k)))

;; --- Pattern table dispatch ---

(defn first-matching-pattern [patterns text]
  ;; ... exact copy of L95-103
  )

(defn first-matching-pattern-with-context [patterns text context]
  ;; ... exact copy of L105-112
  )
```

**Step 2: Run all tests to verify nothing is broken yet**

Run: `clj -M:spec spec/empire/acceptance/parser_spec.clj`
Expected: All tests PASS (new file isn't required by anything yet)

**Step 3: Commit**

```bash
git add src/empire/acceptance/parser/helpers.cljc
git commit -m "refactor: extract parser/helpers.cljc from monolith"
```

---

### Task 2: Create `parser/given.cljc`

**Files:**
- Create: `src/empire/acceptance/parser/given.cljc`
- Source: `src/empire/acceptance/parser.cljc:114-428`

**Step 1: Create the given file**

Copy lines 114-428. Namespace requires helpers. Handlers stay `defn-`. Only `parse-given` is `defn`.

```clojure
(ns empire.acceptance.parser.given
  (:require [clojure.string :as str]
            [empire.acceptance.parser.helpers :as h]))
```

Replace all bare calls to helper functions with `h/` prefixed calls:
- `strip-trailing-period` → `h/strip-trailing-period`
- `strip-keyword-prefix` → `h/strip-keyword-prefix`
- `blank-or-comment?` → `h/blank-or-comment?`
- `map-row?` → `h/map-row?`
- `parse-coords` → `h/parse-coords`
- `parse-number` → `h/parse-number`
- `parse-count` → `h/parse-count`
- `city-or-unit-char?` → `h/city-or-unit-char?`
- `resolve-cell-prop` → `h/resolve-cell-prop`
- `first-matching-pattern` → `h/first-matching-pattern`
- `first-matching-pattern-with-context` → `h/first-matching-pattern-with-context`
- `unit-name->char` → `h/unit-name->char`

All handler functions (`given-handle-*`, `parse-unit-props-line`, `parse-container-state-line`) stay `defn-`.
`parse-given` stays `defn` (public).
`unit-prop-extractors`, `given-map-patterns`, `given-directive-patterns` stay `def ^:private`.

**Step 2: Run tests**

Run: `clj -M:spec spec/empire/acceptance/parser_spec.clj`
Expected: PASS (new file not wired in yet)

**Step 3: Commit**

```bash
git add src/empire/acceptance/parser/given.cljc
git commit -m "refactor: extract parser/given.cljc from monolith"
```

---

### Task 3: Create `parser/when.cljc`

**Files:**
- Create: `src/empire/acceptance/parser/when.cljc`
- Source: `src/empire/acceptance/parser.cljc:430-578`

**Step 1: Create the when file**

```clojure
(ns empire.acceptance.parser.when
  (:require [clojure.string :as str]
            [empire.acceptance.parser.helpers :as h]))
```

Replace helper calls with `h/` prefix:
- `uppercase-direction?` → `h/uppercase-direction?`
- `lowercase-direction?` → `h/lowercase-direction?`
- `strip-trailing-period` → `h/strip-trailing-period`
- `strip-keyword-prefix` → `h/strip-keyword-prefix`
- `blank-or-comment?` → `h/blank-or-comment?`
- `parse-count` → `h/parse-count`
- `first-matching-pattern-with-context` → `h/first-matching-pattern-with-context`

All `when-handle-*` stay `defn-`. `determine-key-type`, `determine-combat-type` stay `defn-`.
`parse-when` stays `defn` (public).

**Step 2: Run tests**

Run: `clj -M:spec spec/empire/acceptance/parser_spec.clj`
Expected: PASS

**Step 3: Commit**

```bash
git add src/empire/acceptance/parser/when.cljc
git commit -m "refactor: extract parser/when.cljc from monolith"
```

---

### Task 4: Create `parser/then.cljc`

**Files:**
- Create: `src/empire/acceptance/parser/then.cljc`
- Source: `src/empire/acceptance/parser.cljc:580-1027`

**Step 1: Create the then file**

```clojure
(ns empire.acceptance.parser.then
  (:require [clojure.string :as str]
            [empire.acceptance.parser.helpers :as h]))
```

Replace helper calls with `h/` prefix:
- `strip-trailing-period` → `h/strip-trailing-period`
- `blank-or-comment?` → `h/blank-or-comment?`
- `map-row?` → `h/map-row?`
- `territory-map-row?` → `h/territory-map-row?`
- `parse-count` → `h/parse-count`
- `parse-number` → `h/parse-number`
- `parse-coords` → `h/parse-coords`
- `city-or-unit-char?` → `h/city-or-unit-char?`
- `resolve-cell-prop` → `h/resolve-cell-prop`
- `first-matching-pattern` → `h/first-matching-pattern`

All `then-handle-*` stay `defn-`. `strip-then-preamble`, `tag-timing`, `parse-single-then-clause`, `split-then-continuations`, `split-compound-then`, `extract-then-map-blocks` stay `defn-`.
`parse-then` stays `defn` (public).

**Step 2: Run tests**

Run: `clj -M:spec spec/empire/acceptance/parser_spec.clj`
Expected: PASS

**Step 3: Commit**

```bash
git add src/empire/acceptance/parser/then.cljc
git commit -m "refactor: extract parser/then.cljc from monolith"
```

---

### Task 5: Rewire `parser.cljc` to delegate to sub-namespaces

**Files:**
- Modify: `src/empire/acceptance/parser.cljc`

**Step 1: Replace the monolith with the thin facade**

Delete lines 7-1027 (helpers, GIVEN, WHEN, THEN sections). Replace the ns form and body with:

```clojure
(ns empire.acceptance.parser
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [empire.config :as config]
            [empire.acceptance.parser.helpers :as h]
            [empire.acceptance.parser.given :as given]
            [empire.acceptance.parser.when :as when-parser]
            [empire.acceptance.parser.then :as then-parser]))

;; --- Test splitting ---
;; (keep split-into-tests exactly as-is from L1031-1103,
;;  replacing bare helper calls with h/ prefix:
;;  separator-line? → h/separator-line?
;;  blank-or-comment? → h/blank-or-comment?)

;; --- Context building ---
;; (keep extract-unit-types-from-givens from L1107-1120,
;;  replacing player-unit-chars → h/player-unit-chars,
;;  computer-unit-chars → h/computer-unit-chars)

;; (keep has-waiting-for-input? from L1122-1123)

;; --- Top-level parsing ---
;; (keep parse-test from L1127-1142,
;;  replacing parse-given → given/parse-given,
;;  parse-when → when-parser/parse-when,
;;  parse-then → then-parser/parse-then)

;; (keep parse-file, validate-config-keys, write-edn, -main as-is)
```

The alias for `when` must not shadow `clojure.core/when` — use `when-parser`. Similarly `then-parser` for clarity.

**Step 2: Run all parser tests**

Run: `clj -M:spec spec/empire/acceptance/parser_spec.clj`
Expected: PASS — all tests still pass through the public API

**Step 3: Run the acceptance pipeline**

Run: `clj -M:parse-tests && clj -M:generate-specs && clj -M:spec generated-acceptance-specs/`
Expected: All acceptance tests PASS

**Step 4: Commit**

```bash
git add src/empire/acceptance/parser.cljc
git commit -m "refactor: rewire parser.cljc to delegate to sub-namespaces"
```

---

### Task 6: Split the spec — helpers

**Files:**
- Create: `spec/empire/acceptance/parser/helpers_spec.clj`
- Modify: `spec/empire/acceptance/parser_spec.clj`

**Step 1: Move helper specs to new file**

Move the `first-matching-pattern` and `first-matching-pattern-with-context` describes (current spec L6-37) into a new file:

```clojure
(ns empire.acceptance.parser.helpers-spec
  (:require [speclj.core :refer :all]
            [empire.acceptance.parser.helpers :as h]))

(describe "first-matching-pattern"
  ;; Replace @#'parser/first-matching-pattern with h/first-matching-pattern
  ;; (these are now public in helpers)
  (it "returns nil for empty patterns"
    (should-be-nil (h/first-matching-pattern [] "hello")))
  ;; ... remaining tests with h/ prefix instead of @#'parser/ deref
  )

(describe "first-matching-pattern-with-context"
  ;; Same — use h/first-matching-pattern-with-context directly
  )
```

**Step 2: Delete the moved describes from `parser_spec.clj`**

Remove lines 6-37 from the original spec.

**Step 3: Run both specs**

Run: `clj -M:spec spec/empire/acceptance/parser_spec.clj spec/empire/acceptance/parser/helpers_spec.clj`
Expected: PASS

**Step 4: Commit**

```bash
git add spec/empire/acceptance/parser/helpers_spec.clj spec/empire/acceptance/parser_spec.clj
git commit -m "refactor: split helpers specs into parser/helpers_spec.clj"
```

---

### Task 7: Split the spec — given

**Files:**
- Create: `spec/empire/acceptance/parser/given_spec.clj`
- Modify: `spec/empire/acceptance/parser_spec.clj`

**Step 1: Move parse-given describes to new file**

The `(describe "parse-given" ...)` block (current spec L87-406) moves to:

```clojure
(ns empire.acceptance.parser.given-spec
  (:require [speclj.core :refer :all]
            [empire.acceptance.parser.given :as given]))

(describe "parse-given"
  ;; Replace parser/parse-given with given/parse-given
  (it "parses game map"
    (let [lines ["GIVEN game map" "  A#" "  ##"]
          result (given/parse-given lines {})]
      (should= [{:type :map :target :game-map :rows ["A#" "##"]}]
               (:givens result))))
  ;; ... all other parse-given tests
  )
```

**Step 2: Delete moved describes from `parser_spec.clj`**

**Step 3: Run specs**

Run: `clj -M:spec spec/empire/acceptance/parser_spec.clj spec/empire/acceptance/parser/`
Expected: PASS

**Step 4: Commit**

```bash
git add spec/empire/acceptance/parser/given_spec.clj spec/empire/acceptance/parser_spec.clj
git commit -m "refactor: split given specs into parser/given_spec.clj"
```

---

### Task 8: Split the spec — when

**Files:**
- Create: `spec/empire/acceptance/parser/when_spec.clj`
- Modify: `spec/empire/acceptance/parser_spec.clj`

**Step 1: Move parse-when describes to new file**

The `(describe "parse-when" ...)` block (current spec L408-554) moves to:

```clojure
(ns empire.acceptance.parser.when-spec
  (:require [speclj.core :refer :all]
            [empire.acceptance.parser.when :as when-parser]))

(describe "parse-when"
  ;; Replace parser/parse-when with when-parser/parse-when
  ;; ... all parse-when tests
  )
```

**Step 2: Delete moved describes from `parser_spec.clj`**

**Step 3: Run specs**

Run: `clj -M:spec spec/empire/acceptance/parser_spec.clj spec/empire/acceptance/parser/`
Expected: PASS

**Step 4: Commit**

```bash
git add spec/empire/acceptance/parser/when_spec.clj spec/empire/acceptance/parser_spec.clj
git commit -m "refactor: split when specs into parser/when_spec.clj"
```

---

### Task 9: Split the spec — then

**Files:**
- Create: `spec/empire/acceptance/parser/then_spec.clj`
- Modify: `spec/empire/acceptance/parser_spec.clj`

**Step 1: Move parse-then describes to new file**

The `(describe "parse-then" ...)` block (current spec L556-979) moves to:

```clojure
(ns empire.acceptance.parser.then-spec
  (:require [speclj.core :refer :all]
            [empire.acceptance.parser.then :as then-parser]))

(describe "parse-then"
  ;; Replace parser/parse-then with then-parser/parse-then
  ;; ... all parse-then tests
  )
```

**Step 2: Delete moved describes from `parser_spec.clj`**

**Step 3: Run specs**

Run: `clj -M:spec spec/empire/acceptance/parser_spec.clj spec/empire/acceptance/parser/`
Expected: PASS

**Step 4: Commit**

```bash
git add spec/empire/acceptance/parser/then_spec.clj spec/empire/acceptance/parser_spec.clj
git commit -m "refactor: split then specs into parser/then_spec.clj"
```

---

### Task 10: Final verification

**Step 1: Run all unit tests**

Run: `clj -M:spec`
Expected: All PASS

**Step 2: Run full acceptance pipeline**

Run: `clj -M:parse-tests && clj -M:generate-specs && clj -M:spec generated-acceptance-specs/`
Expected: All PASS

**Step 3: Verify file sizes**

Run: `wc -l src/empire/acceptance/parser.cljc src/empire/acceptance/parser/*.cljc`
Expected: `parser.cljc` ~85 lines, `helpers.cljc` ~110 lines, `given.cljc` ~315 lines, `when.cljc` ~150 lines, `then.cljc` ~450 lines

**Step 4: Commit and clean up**

Move the design doc to `plans/complete/`:
```bash
mv plans/2026-02-23-parser-split-design.md plans/complete/
git add plans/
git commit -m "refactor: parser split complete, move design to complete"
```

---

## Execution Notes

- **No behavior changes** — this is a pure extract-and-delegate refactor. Every test that passes before should pass after.
- **Key risk**: `when` is a Clojure core name. The alias `when-parser` avoids shadowing.
- **Private var derefs** (`@#'parser/...`) in the helpers spec must change to direct public calls (`h/...`) since the functions are now public in their new namespace.
- **`parse-given`, `parse-when`, `parse-then` are already public** — specs call them through the `parser/` alias today; after the split they call through `given/`, `when-parser/`, `then-parser/` aliases.
- The `split-into-tests`, `parse-test`, `parse-file`, `validate-config-keys`, `write-edn`, `-main` functions stay in `parser.cljc` since they're the public API and CLI entry point.
- The `extract-unit-types-from-givens` and `has-waiting-for-input?` functions stay in `parser.cljc` since they reference `h/player-unit-chars` and `h/computer-unit-chars` and are only used by `parse-test`.
