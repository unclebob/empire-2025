# Computer Ship Repair Acceptance Tests — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add parser/generator directives so the acceptance test pipeline can translate `acceptanceTests/computer-repair.txt` into runnable Speclj specs.

**Architecture:** Extend the existing parser pattern tables and generator dispatch with five new directive types — three GIVEN/THEN pairs for shipyard state, one GIVEN for placing a unit on a city, and one THEN for whole-map comparison. Follow TDD: write a parser spec first, then implement; write pipeline test, then implement generator.

**Tech Stack:** Clojure, Speclj, acceptance test pipeline (parser.cljc, generator.cljc)

---

### Task 1: GIVEN shipyard directive — parser

**Files:**
- Test: `spec/empire/acceptance/parser_spec.clj`
- Modify: `src/empire/acceptance/parser.cljc`

**Step 1: Write the failing parser test**

Add to the `parse-given` describe block in `parser_spec.clj`:

```clojure
    (it "parses 'X has a destroyer with 2 hits in its shipyard'"
      (let [lines ["X has a destroyer with 2 hits in its shipyard."]
            result (parser/parse-given lines {})]
        (should= [{:type :shipyard-state :city "X" :ship-type :destroyer :hits 2}]
                 (:givens result))))
```

**Step 2: Run test to verify it fails**

Run: `clj -M:spec spec/empire/acceptance/parser_spec.clj`
Expected: FAIL — the line won't match any pattern and will be silently dropped or parsed incorrectly.

**Step 3: Implement the parser pattern**

In `parser.cljc`, add a handler function before the `given-directive-patterns` def (around line 255):

```clojure
(defn- given-handle-shipyard-state [[_ city ship-type hits] _ctx]
  {:directive :shipyard-state
   :ir {:type :shipyard-state :city city :ship-type (keyword ship-type) :hits (Integer/parseInt hits)}})
```

Add to `given-directive-patterns` (before the `computer controls` pattern, around line 300):

```clojure
   {:regex #"(\w+)\s+has\s+(?:a|an)\s+(\w+)\s+with\s+(\d+)\s+hits?\s+in\s+its\s+shipyard"
    :handler given-handle-shipyard-state}
```

**Step 4: Run test to verify it passes**

Run: `clj -M:spec spec/empire/acceptance/parser_spec.clj`
Expected: PASS

**Step 5: Commit**

```
feat: add GIVEN shipyard-state directive to parser
```

---

### Task 2: GIVEN shipyard directive — generator

**Files:**
- Modify: `src/empire/acceptance/generator.cljc`

**Step 1: Add generator function**

Add before `generate-given` (around line 368):

```clojure
(defn- generate-shipyard-state-given [{:keys [city ship-type hits]}]
  (let [pos-expr (target-pos-expr city)]
    (str "    (let [" (str/lower-case city) "-pos " pos-expr "]\n"
         "      (swap! atoms/game-map update-in " (str/lower-case city) "-pos\n"
         "        update :shipyard (fnil conj []) {:type :" (name ship-type) " :hits " hits "}))")))
```

**Step 2: Add case to `generate-given` dispatch**

In the `generate-given` function (around line 374), add:

```clojure
     :shipyard-state (generate-shipyard-state-given given)
```

**Step 3: Run parser spec to verify nothing broke**

Run: `clj -M:spec spec/empire/acceptance/parser_spec.clj`
Expected: PASS

**Step 4: Commit**

```
feat: add GIVEN shipyard-state generator
```

---

### Task 3: GIVEN city-unit directive — parser

**Files:**
- Test: `spec/empire/acceptance/parser_spec.clj`
- Modify: `src/empire/acceptance/parser.cljc`

**Step 1: Write the failing parser test**

```clojure
    (it "parses 'X has a computer army'"
      (let [lines ["X has a computer army."]
            result (parser/parse-given lines {})]
        (should= [{:type :city-unit :city "X" :unit-type :army :owner :computer}]
                 (:givens result))))
```

**Step 2: Run test to verify it fails**

Run: `clj -M:spec spec/empire/acceptance/parser_spec.clj`
Expected: FAIL

**Step 3: Implement the parser pattern**

Handler (near other given handlers):

```clojure
(defn- given-handle-city-unit [[_ city owner unit-type] _ctx]
  {:directive :city-unit
   :ir {:type :city-unit :city city :unit-type (keyword unit-type) :owner (keyword owner)}})
```

Pattern in `given-directive-patterns` (before the `computer controls` pattern):

```clojure
   {:regex #"(\w+)\s+has\s+(?:a|an)\s+(player|computer)\s+(\w+)"
    :handler given-handle-city-unit}
```

**Step 4: Run test to verify it passes**

Run: `clj -M:spec spec/empire/acceptance/parser_spec.clj`
Expected: PASS

**Step 5: Commit**

```
feat: add GIVEN city-unit directive to parser
```

---

### Task 4: GIVEN city-unit directive — generator

**Files:**
- Modify: `src/empire/acceptance/generator.cljc`

**Step 1: Add generator function**

```clojure
(defn- generate-city-unit-given [{:keys [city unit-type owner]}]
  (let [pos-expr (target-pos-expr city)]
    (str "    (let [" (str/lower-case city) "-pos " pos-expr "]\n"
         "      (swap! atoms/game-map assoc-in (conj " (str/lower-case city) "-pos :contents)\n"
         "        {:type :" (name unit-type) " :owner :" (name owner) " :mode :sentry :hits 1}))")))
```

**Step 2: Add case to `generate-given` dispatch**

```clojure
     :city-unit (generate-city-unit-given given)
```

**Step 3: Run parser spec**

Run: `clj -M:spec spec/empire/acceptance/parser_spec.clj`
Expected: PASS

**Step 4: Commit**

```
feat: add GIVEN city-unit generator
```

---

### Task 5: THEN shipyard-has-ship directive — parser

**Files:**
- Test: `spec/empire/acceptance/parser_spec.clj`
- Modify: `src/empire/acceptance/parser.cljc`

**Step 1: Write the failing parser test**

```clojure
    (it "parses 'X has a destroyer with 2 hits in its shipyard' in THEN"
      (let [lines ["THEN X has a destroyer with 2 hits in its shipyard."]
            result (parser/parse-then lines {})]
        (should= [{:type :shipyard-has-ship :city "X" :ship-type :destroyer :hits 2}]
                 (:thens result))))
```

**Step 2: Run test to verify it fails**

Run: `clj -M:spec spec/empire/acceptance/parser_spec.clj`
Expected: FAIL

**Step 3: Implement the handler and pattern**

Handler:

```clojure
(defn- then-handle-shipyard-has-ship [[_ city ship-type hits]]
  {:type :shipyard-has-ship :city city :ship-type (keyword ship-type) :hits (Integer/parseInt hits)})
```

Add to `then-bare-patterns` (before the `cell` patterns, around line 770):

```clojure
   {:regex #"^(\w+)\s+has\s+(?:a|an)\s+(\w+)\s+with\s+(\d+)\s+hits?\s+in\s+its\s+shipyard"
    :handler then-handle-shipyard-has-ship}
```

**Step 4: Run test to verify it passes**

Run: `clj -M:spec spec/empire/acceptance/parser_spec.clj`
Expected: PASS

**Step 5: Commit**

```
feat: add THEN shipyard-has-ship directive to parser
```

---

### Task 6: THEN shipyard-has-ship directive — generator

**Files:**
- Modify: `src/empire/acceptance/generator.cljc`

**Step 1: Add generator function**

```clojure
(defn- generate-shipyard-has-ship-then [{:keys [city ship-type hits]}]
  (let [pos-expr (target-pos-expr city)]
    (str "    (let [cell (get-in @atoms/game-map " pos-expr ")\n"
         "          shipyard (:shipyard cell [])]\n"
         "      (should (some #(and (= :" (name ship-type) " (:type %)) (= " hits " (:hits %))) shipyard)))")))
```

**Step 2: Add case to `generate-then` dispatch**

```clojure
    :shipyard-has-ship (generate-shipyard-has-ship-then then-ir)
```

**Step 3: Run parser spec**

Run: `clj -M:spec spec/empire/acceptance/parser_spec.clj`
Expected: PASS

**Step 4: Commit**

```
feat: add THEN shipyard-has-ship generator
```

---

### Task 7: THEN shipyard-empty directive — parser

**Files:**
- Test: `spec/empire/acceptance/parser_spec.clj`
- Modify: `src/empire/acceptance/parser.cljc`

**Step 1: Write the failing parser test**

```clojure
    (it "parses 'X has no ships in its shipyard'"
      (let [lines ["THEN X has no ships in its shipyard."]
            result (parser/parse-then lines {})]
        (should= [{:type :shipyard-empty :city "X"}]
                 (:thens result))))
```

**Step 2: Run test to verify it fails**

Run: `clj -M:spec spec/empire/acceptance/parser_spec.clj`
Expected: FAIL

**Step 3: Implement handler and pattern**

Handler:

```clojure
(defn- then-handle-shipyard-empty [[_ city]]
  {:type :shipyard-empty :city city})
```

Add to `then-bare-patterns` (right after the shipyard-has-ship pattern):

```clojure
   {:regex #"^(\w+)\s+has\s+no\s+ships?\s+in\s+its\s+shipyard"
    :handler then-handle-shipyard-empty}
```

**Step 4: Run test to verify it passes**

Run: `clj -M:spec spec/empire/acceptance/parser_spec.clj`
Expected: PASS

**Step 5: Commit**

```
feat: add THEN shipyard-empty directive to parser
```

---

### Task 8: THEN shipyard-empty directive — generator

**Files:**
- Modify: `src/empire/acceptance/generator.cljc`

**Step 1: Add generator function**

```clojure
(defn- generate-shipyard-empty-then [{:keys [city]}]
  (let [pos-expr (target-pos-expr city)]
    (str "    (let [cell (get-in @atoms/game-map " pos-expr ")\n"
         "          shipyard (:shipyard cell [])]\n"
         "      (should= [] shipyard))")))
```

**Step 2: Add case to `generate-then` dispatch**

```clojure
    :shipyard-empty (generate-shipyard-empty-then then-ir)
```

**Step 3: Run parser spec**

Run: `clj -M:spec spec/empire/acceptance/parser_spec.clj`
Expected: PASS

**Step 4: Commit**

```
feat: add THEN shipyard-empty generator
```

---

### Task 9: THEN map-is directive — parser

**Files:**
- Test: `spec/empire/acceptance/parser_spec.clj`
- Modify: `src/empire/acceptance/parser.cljc`

**Step 1: Write the failing parser test**

```clojure
    (it "parses 'the map is dX#'"
      (let [lines ["THEN the map is dX#."]
            result (parser/parse-then lines {})]
        (should= [{:type :map-is :expected "dX#"}]
                 (:thens result))))
```

**Step 2: Run test to verify it fails**

Run: `clj -M:spec spec/empire/acceptance/parser_spec.clj`
Expected: FAIL

**Step 3: Implement handler and pattern**

Handler:

```clojure
(defn- then-handle-map-is [[_ map-str]]
  {:type :map-is :expected (strip-trailing-period map-str)})
```

Add to `then-bare-patterns` (before the cell patterns):

```clojure
   {:regex #"^(?:the\s+)?map\s+is\s+(\S+)"
    :handler then-handle-map-is}
```

**Step 4: Run test to verify it passes**

Run: `clj -M:spec spec/empire/acceptance/parser_spec.clj`
Expected: PASS

**Step 5: Commit**

```
feat: add THEN map-is directive to parser
```

---

### Task 10: THEN map-is directive — generator

**Files:**
- Modify: `src/empire/acceptance/generator.cljc`

The `map-is` assertion builds an expected map from the string and compares each cell's type, city-status, and contents (type + owner) against the actual game map.

**Step 1: Add generator function**

```clojure
(defn- generate-map-is-then [{:keys [expected]}]
  (str "    (let [expected (build-test-map [\"" expected "\"])\n"
       "          actual @atoms/game-map]\n"
       "      (doseq [col (range (count expected))\n"
       "              row (range (count (first expected)))\n"
       "              :let [exp-cell (get-in expected [col row])\n"
       "                    act-cell (get-in actual [col row])]\n"
       "              :when exp-cell]\n"
       "        (should= (:type exp-cell) (:type act-cell))\n"
       "        (when (:city-status exp-cell)\n"
       "          (should= (:city-status exp-cell) (:city-status act-cell)))\n"
       "        (if (:contents exp-cell)\n"
       "          (do (should-not-be-nil (:contents act-cell))\n"
       "              (should= (:type (:contents exp-cell)) (:type (:contents act-cell)))\n"
       "              (should= (:owner (:contents exp-cell)) (:owner (:contents act-cell))))\n"
       "          (should-be-nil (:contents act-cell)))))"))
```

**Step 2: Add case to `generate-then` dispatch**

```clojure
    :map-is (generate-map-is-then then-ir)
```

**Step 3: Run parser spec**

Run: `clj -M:spec spec/empire/acceptance/parser_spec.clj`
Expected: PASS

**Step 4: Commit**

```
feat: add THEN map-is generator
```

---

### Task 11: Run the full acceptance test pipeline

**Step 1: Run parser on the new file**

Run: `clj -M:parse-tests`

Check that `acceptanceTests/edn/computer-repair.edn` is produced with no unrecognized directives.

**Step 2: Run generator**

Run: `clj -M:generate-specs`

Check that `generated-acceptance-specs/acceptance/computer_repair_spec.clj` is produced. Read the file and verify the generated code looks correct.

**Step 3: Run the generated specs**

Run: `clj -M:spec generated-acceptance-specs/`

Expected:
- Scenario 1 (dock): May fail if computer movement doesn't use dock check — documents a bug.
- Scenario 2 (repair): Should PASS — `repair-damaged-ships` runs during `start-new-round`.
- Scenario 3 (launch): Will FAIL — `launch-ship-from-shipyard` places ship on city cell, not adjacent sea.
- Scenario 4 (launch when occupied): Will FAIL — code checks `(nil? (:contents current-cell))`.

**Step 4: Run all existing specs to verify no regressions**

Run: `clj -M:spec`

Expected: All existing tests still pass.

**Step 5: Commit**

```
feat: add computer-repair acceptance tests (3 expected failures)
```

---

### Task 12: Run all tests (full verification)

Follow the "run all tests" protocol:

1. Clear generated files (list each explicitly with `rm -f`)
2. Run unit tests: `clj -M:spec`
3. Run acceptance pipeline: `clj -M:parse-tests && clj -M:generate-specs && clj -M:spec generated-acceptance-specs/`

Report pass/fail counts. Acceptance test failures in `computer-repair` scenarios 1, 3, and 4 are expected.
