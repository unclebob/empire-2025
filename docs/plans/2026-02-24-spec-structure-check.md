# Spec Structure Checker Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a Clojure tool that scans Speclj spec files for parenthesis and structural errors — primarily `(it)` nested inside `(it)`.

**Architecture:** Single-pass character scanner maintains paren depth, tracks Speclj form nesting on a stack, and reports structural violations. No Clojure reader — it doesn't preserve line numbers and can't detect structural errors in balanced code.

**Speclj structure (2-3 layers, no nesting within same level):**
- Level 1 (top): `(describe)` — never nested inside another `describe`
- Level 2 (inside describe): `(it)`, `(context)`, `(before)`, `(with-stubs)`, etc.
- Level 3 (inside context): `(it)`, `(before)`, etc.
- Nothing structural inside `(it)`

**Tech Stack:** Clojure, Speclj for testing. No external dependencies.

---

### Task 1: Scanner Core — Character Mode Tracking

**Files:**
- Create: `src/empire/paren_check/core.cljc`
- Create: `spec/empire/paren_check/core_spec.clj`

**Step 1: Write failing tests for mode transitions**

```clojure
(ns empire.paren-check.core-spec
  (:require [speclj.core :refer :all]
            [empire.paren-check.core :as pc]))

(describe "scan"
  (it "returns OK for empty string"
    (should= {:errors [] :depth 0} (pc/scan "")))

  (it "tracks paren depth"
    (should= 0 (:depth (pc/scan "(foo)")))
    (should= 1 (:depth (pc/scan "(foo")))
    (should= 0 (:depth (pc/scan "(foo (bar))"))))

  (it "ignores parens inside strings"
    (should= 0 (:depth (pc/scan "(def x \"(((\")"))))

  (it "ignores parens inside comments"
    (should= 0 (:depth (pc/scan "(def x 1) ; ((("))))

  (it "handles escaped quotes in strings"
    (should= 0 (:depth (pc/scan "(def x \"a\\\"b\")")))))
```

**Step 2: Run tests to verify they fail**

Run: `clj -M:spec spec/empire/paren_check/core_spec.clj`
Expected: FAIL — namespace not found

**Step 3: Implement scan function — mode tracking and depth counting**

```clojure
(ns empire.paren-check.core
  (:require [clojure.string :as str]))

(defn- process-char [state c next-c]
  (let [{:keys [mode depth line escape]} state]
    (cond
      escape
      (assoc state :escape false)

      (= mode :comment)
      (if (= c \newline)
        (assoc state :mode :normal :line (inc line))
        state)

      (= mode :string)
      (cond
        (= c \\) (assoc state :escape true)
        (= c \") (assoc state :mode :normal)
        (= c \newline) (update state :line inc)
        :else state)

      (= mode :regex)
      (cond
        (= c \\) (assoc state :escape true)
        (= c \") (assoc state :mode :normal)
        (= c \newline) (update state :line inc)
        :else state)

      ;; :normal mode
      (= c \;) (assoc state :mode :comment)
      (= c \\) (assoc state :escape true)
      (= c \") (assoc state :mode :string)
      (and (= c \#) (= next-c \")) (assoc state :mode :regex)
      (= c \newline) (update state :line inc)
      (= c \() (update state :depth inc)
      (= c \)) (update state :depth dec)
      :else state)))

(defn scan [text]
  (let [chars (vec text)
        n (count chars)
        init {:mode :normal :depth 0 :line 1 :escape false
              :errors [] :stack []}
        result (reduce
                 (fn [state i]
                   (let [c (nth chars i)
                         next-c (when (< (inc i) n) (nth chars (inc i)))]
                     (process-char state c next-c)))
                 init
                 (range n))]
    (select-keys result [:errors :depth])))
```

**Step 4: Run tests to verify they pass**

Run: `clj -M:spec spec/empire/paren_check/core_spec.clj`
Expected: PASS

**Step 5: Commit**

```bash
git add src/empire/paren_check/core.cljc spec/empire/paren_check/core_spec.clj
git commit -m "feat: spec structure checker — scanner core with mode tracking"
```

---

### Task 2: Speclj Form Detection and Stack Tracking

**Files:**
- Modify: `src/empire/paren_check/core.cljc`
- Modify: `spec/empire/paren_check/core_spec.clj`

**Step 1: Write failing tests for form detection**

Add to the spec:

```clojure
(describe "speclj form tracking"
  (it "detects describe form"
    (let [result (pc/scan "(describe \"foo\")")]
      (should= [{:form "describe" :line 1}] (:forms result))))

  (it "detects describe with it children"
    (let [result (pc/scan "(describe \"foo\"\n  (it \"bar\"))")]
      (should= [{:form "describe" :line 1
                 :children [{:form "it" :line 2}]}]
               (:forms result))))

  (it "detects context inside describe"
    (let [result (pc/scan "(describe \"foo\"\n  (context \"ctx\"\n    (it \"bar\")))")]
      (should= [{:form "describe" :line 1
                 :children [{:form "context" :line 2
                             :children [{:form "it" :line 3}]}]}]
               (:forms result))))

  (it "detects before and with-stubs"
    (let [result (pc/scan "(describe \"x\"\n  (before (reset!))\n  (with-stubs)\n  (it \"y\"))")]
      (should= 4 (count (:children (first (:forms result)))))))
```

**Step 2: Run to verify failure**

Run: `clj -M:spec spec/empire/paren_check/core_spec.clj`
Expected: FAIL — :forms not in result or wrong values

**Step 3: Implement form detection**

When `(` is encountered in normal mode, look ahead through subsequent characters to see if the token matches a Speclj keyword. If so, push `{:form name :line line :depth depth}` onto the stack. When `)` returns depth to a stacked form's depth, pop it and attach as a child to its parent (or to the top-level `:forms` list).

Speclj keywords to detect: `describe`, `context`, `it`, `before`, `before-all`, `after`, `with-stubs`, `with`, `around`.

**Step 4: Run tests to verify pass**

Run: `clj -M:spec spec/empire/paren_check/core_spec.clj`
Expected: PASS

**Step 5: Commit**

```bash
git add src/empire/paren_check/core.cljc spec/empire/paren_check/core_spec.clj
git commit -m "feat: spec structure checker — speclj form detection and nesting"
```

---

### Task 3: Error Detection — `(it)` Inside `(it)` and Other Violations

**Files:**
- Modify: `src/empire/paren_check/core.cljc`
- Modify: `spec/empire/paren_check/core_spec.clj`

**Step 1: Write failing tests for error cases**

```clojure
(describe "error detection"
  (it "detects (it) inside (it)"
    (let [result (pc/scan "(describe \"x\"\n  (it \"outer\"\n    (it \"inner\")))")]
      (should= 1 (count (:errors result)))
      (should-contain "line 3" (first (:errors result)))
      (should-contain "(it) inside (it)" (first (:errors result)))))

  (it "detects (describe) inside (describe)"
    (let [result (pc/scan "(describe \"x\"\n  (describe \"y\"))")]
      (should= 1 (count (:errors result)))))

  (it "detects (context) inside (context)"
    (let [result (pc/scan "(describe \"x\"\n  (context \"a\"\n    (context \"b\")))")]
      (should= 1 (count (:errors result)))))

  (it "detects (describe) inside (it)"
    (let [result (pc/scan "(describe \"x\"\n  (it \"y\"\n    (describe \"z\")))")]
      (should= 1 (count (:errors result)))))

  (it "detects (before) inside (it)"
    (let [result (pc/scan "(describe \"x\"\n  (it \"y\"\n    (before (reset!))))")]
      (should= 1 (count (:errors result)))))

  (it "detects (context) inside (it)"
    (let [result (pc/scan "(describe \"x\"\n  (it \"y\"\n    (context \"z\")))")]
      (should= 1 (count (:errors result)))))

  (it "no error for correct nesting"
    (let [result (pc/scan "(describe \"x\"\n  (before (reset!))\n  (it \"a\")\n  (it \"b\"))")]
      (should= 0 (count (:errors result)))))

  (it "reports unclosed form at EOF"
    (let [result (pc/scan "(describe \"x\"\n  (it \"y\"")]
      (should= 1 (count (:errors result)))
      (should-contain "unclosed" (first (:errors result)))))

  (it "reports unbalanced parens at EOF"
    (let [result (pc/scan "(describe \"x\"")]
      (should-not= 0 (:depth result)))))
```

**Step 2: Run to verify failure**

Run: `clj -M:spec spec/empire/paren_check/core_spec.clj`
Expected: FAIL — errors vector empty

**Step 3: Implement error checking**

When pushing a Speclj form onto the stack, validate against parent:
- Parent is `:it` → error (nothing structural inside `it`)
- Parent is `:describe` and form is `:describe` → error (no nested describe)
- Parent is `:context` and form is `:context` → error (no nested context)
- No parent and form is not `:describe` → error (only describe at top level)

Error format: `"ERROR line N: (form) inside (parent) at line M"`.

At EOF, check if stack is non-empty. For each remaining entry: `"ERROR line N: unclosed (form) from line M"`.

**Step 4: Run tests to verify pass**

Run: `clj -M:spec spec/empire/paren_check/core_spec.clj`
Expected: PASS

**Step 5: Commit**

```bash
git add src/empire/paren_check/core.cljc spec/empire/paren_check/core_spec.clj
git commit -m "feat: spec structure checker — error detection for structural violations"
```

---

### Task 4: CLI Entry Point and File I/O

**Files:**
- Modify: `src/empire/paren_check/core.cljc`
- Modify: `spec/empire/paren_check/core_spec.clj`
- Modify: `deps.edn`

**Step 1: Write failing test for check-file function**

```clojure
(describe "check-file"
  (it "returns OK for a well-formed spec file"
    (let [result (pc/check-file "spec/empire/combat_spec.clj")]
      (should= "OK" result)))

  (it "handles --tree flag"
    (let [result (pc/check-file "spec/empire/combat_spec.clj" {:tree true})]
      (should-contain "describe" result))))
```

**Step 2: Run to verify failure**

Run: `clj -M:spec spec/empire/paren_check/core_spec.clj`
Expected: FAIL — check-file not defined

**Step 3: Implement check-file and -main**

`check-file` reads the file with `slurp`, calls `scan`, formats output. Returns `"OK"` or error lines.

`-main` parses args (file paths, `--tree` flag), calls `check-file` on each, prints results. For directory args, recursively finds `.clj` files.

**Step 4: Run tests to verify pass**

Run: `clj -M:spec spec/empire/paren_check/core_spec.clj`
Expected: PASS

**Step 5: Add deps.edn alias**

Add to `:aliases` in `deps.edn`:

```clojure
:spec-structure-check {:main-opts ["-m" "empire.paren-check.core"]
                       :extra-paths ["spec"]}
```

**Step 6: Smoke test CLI**

Run: `clj -M:spec-structure-check spec/empire/combat_spec.clj`
Expected: `OK`

Run: `clj -M:spec-structure-check spec/`
Expected: `OK` for each file (or errors if any exist)

**Step 7: Commit**

```bash
git add src/empire/paren_check/core.cljc spec/empire/paren_check/core_spec.clj deps.edn
git commit -m "feat: spec structure checker — CLI entry point and file I/O"
```

---

### Task 5: Tree Output (--tree flag)

**Files:**
- Modify: `src/empire/paren_check/core.cljc`
- Modify: `spec/empire/paren_check/core_spec.clj`

**Step 1: Write failing test for tree formatting**

```clojure
(describe "format-tree"
  (it "formats nested forms with indentation"
    (let [forms [{:form "describe" :line 1
                  :children [{:form "before" :line 2}
                             {:form "it" :line 3}
                             {:form "it" :line 5}]}]
          result (pc/format-tree forms)]
      (should= (str "(describe :line 1\n"
                    "  (before :line 2)\n"
                    "  (it :line 3)\n"
                    "  (it :line 5))")
               result))))
```

**Step 2: Run to verify failure**

**Step 3: Implement format-tree**

Recursive function that indents by 2 spaces per nesting level, printing `(form :line N)` for leaf nodes and `(form :line N\n  children...)` for nodes with children.

**Step 4: Run tests to verify pass**

**Step 5: Commit**

```bash
git add src/empire/paren_check/core.cljc spec/empire/paren_check/core_spec.clj
git commit -m "feat: spec structure checker — tree output formatting"
```

---

### Task 6: Integration Test Against Real Spec Files

**Files:**
- Modify: `spec/empire/paren_check/core_spec.clj`

**Step 1: Write integration tests that scan actual project spec files**

```clojure
(describe "integration — real spec files"
  (it "combat_spec.clj has no errors"
    (should= "OK" (pc/check-file "spec/empire/combat_spec.clj")))

  (it "computer/army_spec.clj has no errors"
    (should= "OK" (pc/check-file "spec/empire/computer/army_spec.clj")))

  (it "batch scan of spec/ has no errors"
    (let [results (pc/check-directory "spec/")]
      (should (every? #(= "OK" (:result %)) results)))))
```

**Step 2: Run to verify pass (these should pass if existing specs are well-formed)**

Run: `clj -M:spec spec/empire/paren_check/core_spec.clj`

**Step 3: Commit**

```bash
git add spec/empire/paren_check/core_spec.clj
git commit -m "feat: spec structure checker — integration tests against real specs"
```

---

### Task 7: Update Memory and Documentation

**Files:**
- Modify: `MEMORY.md` (auto-memory)

**Step 1: Update MEMORY.md**

Add entry for the spec structure checker tool: invocation command, what it does, when Claude should run it.

**Step 2: Update paren editing discipline in MEMORY.md**

Replace the python one-liner with `clj -M:spec-structure-check`.

**Step 3: Commit**

Not applicable — memory file is not in repo.
