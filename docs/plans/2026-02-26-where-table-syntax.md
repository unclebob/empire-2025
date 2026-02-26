# WHERE Table Syntax for Acceptance Tests

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add WHERE table syntax to the acceptance test parser so parameterized scenarios expand into individual IR test entries.

**Architecture:** WHERE is pure syntactic sugar in `.txt` files. The parser's `split-into-tests` captures WHERE lines as a new section on each test group. A post-processing step (`expand-where-tables`) substitutes `<var>` placeholders in GIVEN/WHEN/THEN lines for each table row, producing one test group per row. The generator and Speclj runner are unchanged — they see normal expanded scenarios.

**Tech Stack:** Clojure, Speclj, existing parser pipeline

---

## Syntax

```
;===============================================================
; Ship sentry mode.
;===============================================================
GIVEN game map
  <unit>~
GIVEN <unit> is waiting for input.

WHEN the player presses s.

THEN <unit> has mode sentry.

WHERE
  unit
  D
  S
  B
  P
```

Multi-column:

```
;===============================================================
; Army moves in direction.
;===============================================================
GIVEN game map
  ###
  #A#
  ###
GIVEN A is waiting for input.

WHEN the player presses <key>.

THEN at the next step A will be at <target>.

WHERE
  key | target
  q   | [0 0]
  w   | [1 0]
  e   | [2 0]
  a   | [0 1]
  d   | [2 1]
  z   | [0 2]
  x   | [1 2]
  c   | [2 2]
```

Rules:
- `WHERE` keyword starts the table section (after THEN lines).
- First non-blank line after WHERE is the header (column names, `|`-separated).
- Subsequent non-blank lines are data rows (`|`-separated, or single value if one column).
- `<col-name>` in GIVEN/WHEN/THEN lines is replaced with the cell value.
- Each row produces one standalone test in the IR.
- The expanded test description appends the row values: `"Ship sentry mode. (unit=D)"`.
- The expanded test's `:line` stays the same (points to the original GIVEN).
- Blank/comment lines inside WHERE are ignored.
- Variables in map rows are substituted too (e.g., `<unit>~` becomes `D~`).

---

### Task 1: Recognize WHERE in split-into-tests

**Files:**
- Modify: `src/empire/acceptance/parser.cljc:12-62`
- Test: `spec/empire/acceptance/parser_spec.clj`

**Step 1: Write the failing test — WHERE lines captured in test group**

Add to `parser_spec.clj` in the `"split-into-tests"` context:

```clojure
(it "captures WHERE lines as a section"
  (let [lines [";==============================================================="
               "; Parameterized test."
               ";==============================================================="
               "GIVEN game map"
               "  <unit>~"
               "GIVEN <unit> is waiting for input."
               ""
               "WHEN the player presses s."
               ""
               "THEN <unit> has mode sentry."
               ""
               "WHERE"
               "  unit"
               "  D"
               "  S"]
        tests (parser/split-into-tests lines)]
    (should= 1 (count tests))
    (should= ["unit" "D" "S"] (:where-lines (first tests)))))
```

**Step 2: Run test to verify it fails**

Run: `clj -M:spec spec/empire/acceptance/parser_spec.clj`
Expected: FAIL — `:where-lines` is nil (WHERE not recognized as a section).

**Step 3: Implement WHERE recognition in split-into-tests**

In `parser.cljc`:

1. Add `"WHERE"` to `classify-directive`:
```clojure
(defn- classify-directive [trimmed]
  (cond
    (str/starts-with? trimmed "GIVEN") :given
    (str/starts-with? trimmed "WHEN") :when
    (or (str/starts-with? trimmed "THEN")
        (re-matches #"^and\s+.*" trimmed)) :then
    (str/starts-with? trimmed "WHERE") :where
    :else nil))
```

2. Add `:where` to `section-keys`:
```clojure
(def ^:private section-keys
  {:given :given-lines :when :when-lines :then :then-lines :where :where-lines})
```

3. Add `:where-lines []` to the initial test map in `ensure-test-started`:
```clojure
(defn- ensure-test-started [state line-num]
  (if (:current-test state)
    state
    (assoc state :current-test {:line line-num
                                :description (or (:header-desc state) "")
                                :given-lines [] :when-lines [] :then-lines [] :where-lines []})))
```

4. Handle `:where` in `process-test-line` — the WHERE keyword line itself is not added (just like GIVEN/WHEN/THEN keywords carry content after them, but WHERE is standalone). Content lines after WHERE go into `:where-lines`:
```clojure
:where (assoc state :section :where)
```

Wait — the current code for `:given`, `:when`, `:then` calls `add-to-section` which adds the entire line (including the keyword). For WHERE, the keyword line itself has no content. We just set the section and let subsequent `:content` lines accumulate via `add-content-line`.

**Step 4: Run test to verify it passes**

Run: `clj -M:spec spec/empire/acceptance/parser_spec.clj`
Expected: PASS

**Step 5: Run the spec structure checker**

Run: `clj -M:spec-structure-check spec/empire/acceptance/parser_spec.clj`
Expected: OK

**Step 6: Commit**

```bash
git add src/empire/acceptance/parser.cljc spec/empire/acceptance/parser_spec.clj
git commit -m "feat: recognize WHERE section in acceptance test parser"
```

---

### Task 2: Expand single-column WHERE tables

**Files:**
- Modify: `src/empire/acceptance/parser.cljc`
- Test: `spec/empire/acceptance/parser_spec.clj`

**Step 1: Write the failing test — single-column WHERE expands into multiple tests**

```clojure
(it "expands single-column WHERE into multiple test groups"
  (let [lines [";==============================================================="
               "; Ship sentry mode."
               ";==============================================================="
               "GIVEN game map"
               "  <unit>~"
               "GIVEN <unit> is waiting for input."
               ""
               "WHEN the player presses s."
               ""
               "THEN <unit> has mode sentry."
               ""
               "WHERE"
               "  unit"
               "  D"
               "  S"]
        tests (parser/split-into-tests lines)
        expanded (parser/expand-where-tables tests)]
    (should= 2 (count expanded))
    (should= "Ship sentry mode. (unit=D)" (:description (first expanded)))
    (should= "Ship sentry mode. (unit=S)" (:description (second expanded)))
    (should= ["GIVEN game map" "D~" "GIVEN D is waiting for input."]
             (:given-lines (first expanded)))
    (should= ["WHEN the player presses s."]
             (:when-lines (first expanded)))
    (should= ["THEN D has mode sentry."]
             (:then-lines (first expanded)))))
```

**Step 2: Run test to verify it fails**

Run: `clj -M:spec spec/empire/acceptance/parser_spec.clj`
Expected: FAIL — `expand-where-tables` does not exist.

**Step 3: Implement expand-where-tables**

In `parser.cljc`, add these private helpers and one public function:

```clojure
(defn- parse-where-header [line]
  (mapv str/trim (str/split line #"\|")))

(defn- parse-where-row [line]
  (mapv str/trim (str/split line #"\|")))

(defn- substitute-vars [line bindings]
  (reduce (fn [s [var-name value]]
            (str/replace s (str "<" var-name ">") value))
          line bindings))

(defn- expand-one-where
  [{:keys [description line given-lines when-lines then-lines where-lines]}]
  (let [header (parse-where-header (first where-lines))
        rows (->> (rest where-lines)
                  (map parse-where-row)
                  (remove #(every? str/blank? %)))]
    (mapv (fn [row]
            (let [bindings (zipmap header row)
                  sub (fn [lines] (mapv #(substitute-vars % bindings) lines))
                  label (str/join ", " (map #(str %1 "=" %2) header row))]
              {:line line
               :description (str description " (" label ")")
               :given-lines (sub given-lines)
               :when-lines (sub when-lines)
               :then-lines (sub then-lines)
               :where-lines []}))
          rows)))

(defn expand-where-tables [test-groups]
  (vec (mapcat (fn [group]
                 (if (seq (:where-lines group))
                   (expand-one-where group)
                   [group]))
               test-groups)))
```

**Step 4: Run test to verify it passes**

Run: `clj -M:spec spec/empire/acceptance/parser_spec.clj`
Expected: PASS

**Step 5: Commit**

```bash
git add src/empire/acceptance/parser.cljc spec/empire/acceptance/parser_spec.clj
git commit -m "feat: expand single-column WHERE tables into individual tests"
```

---

### Task 3: Expand multi-column WHERE tables

**Files:**
- Modify: `spec/empire/acceptance/parser_spec.clj`

**Step 1: Write the failing test — multi-column WHERE**

```clojure
(it "expands multi-column WHERE into multiple test groups"
  (let [lines [";==============================================================="
               "; Direction test."
               ";==============================================================="
               "GIVEN game map"
               "  ###"
               "  #A#"
               "  ###"
               "GIVEN A is waiting for input."
               ""
               "WHEN the player presses <key>."
               ""
               "THEN at the next step A will be at <target>."
               ""
               "WHERE"
               "  key | target"
               "  q   | [0 0]"
               "  w   | [1 0]"]
        tests (parser/split-into-tests lines)
        expanded (parser/expand-where-tables tests)]
    (should= 2 (count expanded))
    (should= "Direction test. (key=q, target=[0 0])" (:description (first expanded)))
    (should= ["WHEN the player presses q."] (:when-lines (first expanded)))
    (should= ["THEN at the next step A will be at [0 0]."] (:then-lines (first expanded)))
    (should= ["WHEN the player presses w."] (:when-lines (second expanded)))
    (should= ["THEN at the next step A will be at [1 0]."] (:then-lines (second expanded)))))
```

**Step 2: Run test to verify it passes (or fails)**

Run: `clj -M:spec spec/empire/acceptance/parser_spec.clj`
Expected: This should PASS with the existing implementation since `parse-where-header` and `parse-where-row` already split on `|`. If it fails, adjust the implementation.

**Step 3: Commit if passing**

```bash
git add spec/empire/acceptance/parser_spec.clj
git commit -m "test: verify multi-column WHERE table expansion"
```

---

### Task 4: Wire expansion into parse-file

**Files:**
- Modify: `src/empire/acceptance/parser.cljc:116-125`
- Test: `spec/empire/acceptance/parser_spec.clj`

**Step 1: Write the failing integration test**

Create a small test `.txt` file with WHERE syntax and verify `parse-file` produces the right number of expanded tests.

```clojure
(it "parse-file expands WHERE tables"
  (spit "/tmp/where-test.txt"
        (str ";===============================================================\n"
             "; Ship sentry.\n"
             ";===============================================================\n"
             "GIVEN game map\n"
             "  <unit>~\n"
             "GIVEN <unit> is waiting for input.\n"
             "\n"
             "WHEN the player presses s.\n"
             "\n"
             "THEN <unit> has mode sentry.\n"
             "\n"
             "WHERE\n"
             "  unit\n"
             "  D\n"
             "  S\n"
             "  B\n"))
  (let [result (parser/parse-file "/tmp/where-test.txt")]
    (should= 3 (count (:tests result)))
    (should= "Ship sentry. (unit=D)" (:description (first (:tests result))))
    ;; Verify the IR is correct — GIVEN should have the expanded map
    (let [first-given (-> result :tests first :givens first)]
      (should= :map (:type first-given))
      (should= ["D~"] (:rows first-given)))))
```

**Step 2: Run test to verify it fails**

Run: `clj -M:spec spec/empire/acceptance/parser_spec.clj`
Expected: FAIL — `parse-file` doesn't call `expand-where-tables` yet, so WHERE lines are ignored or cause parse errors.

**Step 3: Wire expand-where-tables into parse-file**

In `parse-file`, insert `expand-where-tables` between `split-into-tests` and `mapv parse-test`:

```clojure
(defn parse-file [path]
  (let [content (slurp path)
        lines (str/split-lines content)
        source (last (str/split path #"/"))
        raw-tests (split-into-tests lines)
        expanded (expand-where-tables raw-tests)
        tests (mapv parse-test expanded)]
    {:source source
     :tests tests}))
```

**Step 4: Run test to verify it passes**

Run: `clj -M:spec spec/empire/acceptance/parser_spec.clj`
Expected: PASS

**Step 5: Run all existing parser tests to confirm no regressions**

Run: `clj -M:spec spec/empire/acceptance/`
Expected: All existing tests pass. The existing `.txt` files have no WHERE blocks, so `expand-where-tables` is a no-op for them.

**Step 6: Commit**

```bash
git add src/empire/acceptance/parser.cljc spec/empire/acceptance/parser_spec.clj
git commit -m "feat: wire WHERE table expansion into parse-file pipeline"
```

---

### Task 5: Handle WHERE with non-test groups gracefully

**Files:**
- Test: `spec/empire/acceptance/parser_spec.clj`

**Step 1: Write the test — WHERE mixed with normal tests**

```clojure
(it "WHERE tests and normal tests coexist"
  (let [lines [";==============================================================="
               "; Normal test."
               ";==============================================================="
               "GIVEN game map"
               "  D~"
               "GIVEN D is waiting for input."
               ""
               "WHEN the player presses s."
               ""
               "THEN D has mode sentry."
               ""
               ";==============================================================="
               "; Parameterized test."
               ";==============================================================="
               "GIVEN game map"
               "  <unit>~"
               ""
               "THEN <unit> has hits <hits>."
               ""
               "WHERE"
               "  unit | hits"
               "  T    | 1"
               "  S    | 2"]
        tests (parser/split-into-tests lines)
        expanded (parser/expand-where-tables tests)]
    (should= 3 (count expanded))
    (should= "Normal test." (:description (first expanded)))
    (should= "Parameterized test. (unit=T, hits=1)" (:description (second expanded)))
    (should= "Parameterized test. (unit=S, hits=2)" (:description (nth expanded 2)))))
```

**Step 2: Run test**

Run: `clj -M:spec spec/empire/acceptance/parser_spec.clj`
Expected: PASS (should work with existing implementation).

**Step 3: Commit**

```bash
git add spec/empire/acceptance/parser_spec.clj
git commit -m "test: verify WHERE coexists with normal tests"
```

---

### Task 6: Full pipeline integration test

**Files:**
- Test: `spec/empire/acceptance/parser_spec.clj`

**Step 1: Write end-to-end test through parse-file and the generator**

```clojure
(it "full pipeline with WHERE produces correct spec count"
  (spit "/tmp/where-pipeline.txt"
        (str ";===============================================================\n"
             "; Ship speed test.\n"
             ";===============================================================\n"
             "GIVEN game map\n"
             "  <unit>~~=\n"
             "GIVEN <unit> is waiting for input.\n"
             "\n"
             "WHEN the player presses D.\n"
             "\n"
             "THEN at next round <unit> will be at =.\n"
             "\n"
             "WHERE\n"
             "  unit\n"
             "  D\n"
             "  S\n"
             "  B\n"
             "  T\n"))
  (let [result (parser/parse-file "/tmp/where-pipeline.txt")]
    (should= 4 (count (:tests result)))
    (should= "Ship speed test. (unit=D)" (:description (first (:tests result))))
    (should= "Ship speed test. (unit=T)" (:description (last (:tests result))))))
```

**Step 2: Run test**

Run: `clj -M:spec spec/empire/acceptance/parser_spec.clj`
Expected: PASS

**Step 3: Run all tests to confirm no regressions**

Run: `clj -M:spec`
Expected: All pass.

**Step 4: Commit**

```bash
git add spec/empire/acceptance/parser_spec.clj
git commit -m "test: full pipeline integration test for WHERE tables"
```

---

### Task 7: Update parser pattern catalog

**Files:**
- Modify: `plans/permanent/parser-pattern-catalog.md`

Add a new section documenting the WHERE syntax:

```markdown
## WHERE Tables (parameterized scenarios)

WHERE blocks expand a template scenario into multiple tests at parse time.
The generator and runner never see WHERE — they receive normal expanded scenarios.

### Syntax

After the THEN lines, add a WHERE block:

    WHERE
      col1 [| col2 | ...]
      val1 [| val2 | ...]
      val1 [| val2 | ...]

- First non-blank line is the header (column names, `|`-separated or bare for single column)
- Subsequent lines are data rows
- `<col-name>` in GIVEN/WHEN/THEN lines is replaced with the cell value
- Each row produces one test; description gets ` (col=val, ...)` appended

### Single-column example

    GIVEN game map
      <unit>~
    WHEN the player presses s.
    THEN <unit> has mode sentry.
    WHERE
      unit
      D
      S
      B

Expands to 3 tests: one for D, one for S, one for B.

### Multi-column example

    WHEN the player presses <key>.
    THEN A will be at <target>.
    WHERE
      key | target
      q   | [0 0]
      w   | [1 0]

Expands to 2 tests.
```

**Step 1: Update the catalog file**

**Step 2: Commit**

```bash
git add plans/permanent/parser-pattern-catalog.md
git commit -m "docs: add WHERE table syntax to parser pattern catalog"
```

---

## Summary

| Task | What | Files touched |
|------|------|---------------|
| 1 | Recognize WHERE in split-into-tests | parser.cljc, parser_spec.clj |
| 2 | Expand single-column WHERE tables | parser.cljc, parser_spec.clj |
| 3 | Test multi-column WHERE expansion | parser_spec.clj |
| 4 | Wire expansion into parse-file | parser.cljc, parser_spec.clj |
| 5 | Test WHERE + normal tests coexistence | parser_spec.clj |
| 6 | Full pipeline integration test | parser_spec.clj |
| 7 | Update parser pattern catalog | parser-pattern-catalog.md |

After this plan, conversion of existing `.txt` files to use WHERE is a separate effort — the parser feature lands first.
