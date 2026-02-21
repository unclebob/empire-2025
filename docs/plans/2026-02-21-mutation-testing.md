# Mutation Testing Tool Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a Clojure mutation testing tool that walks source form trees, applies mutations one at a time, runs targeted specs, and reports survivors.

**Architecture:** Three namespaces under `src/empire/mutation/` — `mutations.cljc` (rules + matching), `runner.cljc` (shell execution + spec mapping), `core.cljc` (orchestration + CLI). Discovery uses `postwalk` with an atom counter to catalog mutation sites; execution applies one mutation per pass and shells out to Speclj.

**Tech Stack:** Clojure 1.12, `clojure.tools.reader` 1.4.2 for parsing `.cljc` files, `clojure.walk/postwalk` for tree traversal, `clojure.java.shell/sh` for test execution, Speclj for the tool's own tests.

---

### Task 1: Mutation Rules Table and Matching

**Files:**
- Create: `src/empire/mutation/mutations.cljc`
- Create: `spec/empire/mutation/mutations_spec.clj`

**Step 1: Write the failing test for the rules table**

```clojure
(ns empire.mutation.mutations-spec
  (:require [speclj.core :refer :all]
            [empire.mutation.mutations :as m]))

(describe "mutation-rules"
  (it "contains the core mutation set"
    (should (seq m/rules))
    (should (every? #(contains? % :original) m/rules))
    (should (every? #(contains? % :mutant) m/rules))
    (should (every? #(contains? % :category) m/rules))
    (should (every? #(contains? % :position) m/rules))))
```

**Step 2: Run test to verify it fails**

Run: `clj -M:spec spec/empire/mutation/mutations_spec.clj`
Expected: FAIL — namespace not found.

**Step 3: Write the rules table**

```clojure
(ns empire.mutation.mutations)

(def rules
  [{:original '+   :mutant '-   :category :arithmetic :position :head}
   {:original '-   :mutant '+   :category :arithmetic :position :head}
   {:original '*   :mutant '/   :category :arithmetic :position :head}
   {:original 'inc :mutant 'dec :category :arithmetic :position :head}
   {:original 'dec :mutant 'inc :category :arithmetic :position :head}
   {:original '>   :mutant '>=  :category :comparison :position :head}
   {:original '>=  :mutant '>   :category :comparison :position :head}
   {:original '<   :mutant '<=  :category :comparison :position :head}
   {:original '<=  :mutant '<   :category :comparison :position :head}
   {:original '=   :mutant 'not= :category :equality :position :head}
   {:original 'not= :mutant '= :category :equality :position :head}
   {:original true  :mutant false :category :boolean :position :any}
   {:original false :mutant true  :category :boolean :position :any}
   {:original 'if      :mutant 'if-not   :category :conditional :position :head}
   {:original 'if-not  :mutant 'if       :category :conditional :position :head}
   {:original 'when    :mutant 'when-not :category :conditional :position :head}
   {:original 'when-not :mutant 'when    :category :conditional :position :head}
   {:original 0 :mutant 1 :category :constant :position :any}
   {:original 1 :mutant 0 :category :constant :position :any}])
```

**Step 4: Run test to verify it passes**

Run: `clj -M:spec spec/empire/mutation/mutations_spec.clj`
Expected: PASS

**Step 5: Write failing tests for matching logic**

Add to `mutations_spec.clj`:

```clojure
(describe "matches-rule?"
  (it "matches a symbol in head position"
    (should (m/matches-rule? {:original '+ :position :head} '(+ 1 2) '+)))

  (it "rejects head-position rule when symbol is not first"
    (should-not (m/matches-rule? {:original '+ :position :head} '(foo + 2) '+)))

  (it "matches an :any-position rule anywhere"
    (should (m/matches-rule? {:original true :position :any} '(if true 1) true))))

(describe "find-mutations"
  (it "finds mutation sites in a simple form"
    (let [sites (m/find-mutations '(+ 1 2))]
      (should (some #(= (:original %) '+) sites))
      (should (some #(= (:original %) 1) sites))))

  (it "finds nested mutation sites"
    (let [sites (m/find-mutations '(if (> x 0) (+ x 1) (- x 1)))]
      (should (>= (count sites) 5)))))
```

**Step 6: Run test to verify it fails**

Run: `clj -M:spec spec/empire/mutation/mutations_spec.clj`
Expected: FAIL — `matches-rule?` and `find-mutations` not defined.

**Step 7: Implement matching logic**

Add to `mutations.cljc`:

```clojure
(defn matches-rule?
  "True if rule matches node. For :head rules, node must be
   a list/seq and the symbol must be its first element.
   parent-form is the enclosing list (or nil at top level)."
  [rule parent-form node]
  (and (= (:original rule) node)
       (or (= :any (:position rule))
           (and (= :head (:position rule))
                (seq? parent-form)
                (= node (first parent-form))))))

(defn find-mutations
  "Walk form tree, return vector of mutation sites.
   Each site: {:index N :original <form> :mutant <form> :description \"...\"}."
  [form]
  (let [counter (atom 0)
        sites (atom [])]
    (letfn [(walk [parent node]
              (doseq [rule rules]
                (when (matches-rule? rule parent node)
                  (swap! sites conj {:index @counter
                                     :original (:original rule)
                                     :mutant (:mutant rule)
                                     :category (:category rule)
                                     :description (str (:original rule) " → " (:mutant rule))})
                  (swap! counter inc)))
              (when (seq? node)
                (doseq [child node]
                  (walk node child))))]
      (walk nil form))
    @sites))
```

**Step 8: Run test to verify it passes**

Run: `clj -M:spec spec/empire/mutation/mutations_spec.clj`
Expected: PASS

**Step 9: Write failing test for apply-mutation**

Add to `mutations_spec.clj`:

```clojure
(describe "apply-mutation"
  (it "applies mutation at a specific index"
    (let [form '(+ 1 2)
          sites (m/find-mutations form)
          plus-site (first (filter #(= (:original %) '+) sites))
          result (m/apply-mutation form (:index plus-site))]
      (should= '(- 1 2) result)))

  (it "leaves other sites unchanged"
    (let [form '(+ 1 2)
          sites (m/find-mutations form)
          one-site (first (filter #(= (:original %) 1) sites))
          result (m/apply-mutation form (:index one-site))]
      (should= '(+ 0 2) result))))
```

**Step 10: Run test to verify it fails**

Run: `clj -M:spec spec/empire/mutation/mutations_spec.clj`
Expected: FAIL — `apply-mutation` not defined.

**Step 11: Implement apply-mutation**

Add to `mutations.cljc`:

```clojure
(defn apply-mutation
  "Walk form tree, apply the mutation at the given index.
   Returns the mutated form."
  [form target-index]
  (let [counter (atom 0)]
    (letfn [(walk [parent node]
              (let [matching-rule (first (filter #(matches-rule? % parent node) rules))]
                (if matching-rule
                  (let [idx @counter]
                    (swap! counter inc)
                    (if (= idx target-index)
                      ;; Apply mutation, but still walk children
                      (let [mutant (:mutant matching-rule)]
                        (if (seq? node)
                          (apply list mutant (map #(walk (cons mutant (rest node)) %) (rest node)))
                          mutant))
                      ;; Not this index, walk children
                      (if (seq? node)
                        (apply list (map #(walk node %) node))
                        node)))
                  ;; No match, walk children
                  (if (seq? node)
                    (apply list (map #(walk node %) node))
                    node))))]
      (walk nil form))))
```

**Step 12: Run test to verify it passes**

Run: `clj -M:spec spec/empire/mutation/mutations_spec.clj`
Expected: PASS

**Step 13: Commit**

```bash
git add src/empire/mutation/mutations.cljc spec/empire/mutation/mutations_spec.clj
git commit -m "feat: add mutation rules table, matching, and apply-mutation"
```

---

### Task 2: Test Runner and Source-to-Spec Mapping

**Files:**
- Create: `src/empire/mutation/runner.cljc`
- Create: `spec/empire/mutation/runner_spec.clj`

**Step 1: Write failing tests for source-to-spec mapping**

```clojure
(ns empire.mutation.runner-spec
  (:require [speclj.core :refer :all]
            [empire.mutation.runner :as runner]))

(describe "source->spec-path"
  (it "maps root source to spec"
    (should= "spec/empire/combat_spec.clj"
             (runner/source->spec-path "src/empire/combat.cljc")))

  (it "maps subdirectory source to spec"
    (should= "spec/empire/computer/ship_spec.clj"
             (runner/source->spec-path "src/empire/computer/ship.cljc")))

  (it "handles deeply nested paths"
    (should= "spec/empire/movement/map_utils_spec.clj"
             (runner/source->spec-path "src/empire/movement/map_utils.cljc"))))
```

**Step 2: Run test to verify it fails**

Run: `clj -M:spec spec/empire/mutation/runner_spec.clj`
Expected: FAIL — namespace not found.

**Step 3: Implement source-to-spec mapping**

```clojure
(ns empire.mutation.runner
  (:require [clojure.java.shell :as shell]
            [clojure.string :as str]))

(defn source->spec-path
  "Convert source path to spec path.
   src/empire/foo.cljc → spec/empire/foo_spec.clj"
  [source-path]
  (-> source-path
      (str/replace #"^src/" "spec/")
      (str/replace #"\.cljc$" "_spec.clj")))
```

**Step 4: Run test to verify it passes**

Run: `clj -M:spec spec/empire/mutation/runner_spec.clj`
Expected: PASS

**Step 5: Write failing test for spec-exists?**

Add to `runner_spec.clj`:

```clojure
(describe "spec-exists?"
  (it "returns true for existing spec"
    (should (runner/spec-exists? "spec/empire/combat_spec.clj")))

  (it "returns false for nonexistent spec"
    (should-not (runner/spec-exists? "spec/empire/nonexistent_spec.clj"))))
```

**Step 6: Run to verify it fails, then implement**

```clojure
(defn spec-exists?
  "True if the spec file exists on disk."
  [spec-path]
  (.exists (java.io.File. spec-path)))
```

**Step 7: Run test to verify it passes**

Run: `clj -M:spec spec/empire/mutation/runner_spec.clj`
Expected: PASS

**Step 8: Write failing test for run-spec**

Add to `runner_spec.clj`:

```clojure
(describe "run-spec"
  (it "returns :killed when spec fails (exit non-zero)"
    (with-redefs [shell/sh (fn [& _] {:exit 1 :out "" :err ""})]
      (should= :killed (runner/run-spec "spec/empire/combat_spec.clj"))))

  (it "returns :survived when spec passes (exit 0)"
    (with-redefs [shell/sh (fn [& _] {:exit 0 :out "" :err ""})]
      (should= :survived (runner/run-spec "spec/empire/combat_spec.clj")))))
```

**Step 9: Run to verify it fails, then implement**

```clojure
(defn run-spec
  "Run a spec file via clj -M:spec. Returns :killed or :survived."
  [spec-path]
  (let [result (shell/sh "clj" "-M:spec" spec-path)]
    (if (zero? (:exit result))
      :survived
      :killed)))
```

**Step 10: Run test to verify it passes**

Run: `clj -M:spec spec/empire/mutation/runner_spec.clj`
Expected: PASS

**Step 11: Commit**

```bash
git add src/empire/mutation/runner.cljc spec/empire/mutation/runner_spec.clj
git commit -m "feat: add runner with source-to-spec mapping and spec execution"
```

---

### Task 3: Core Orchestration — File Reading and Discovery

**Files:**
- Create: `src/empire/mutation/core.cljc`
- Create: `spec/empire/mutation/core_spec.clj`

**Step 1: Write failing test for read-source-forms**

```clojure
(ns empire.mutation.core-spec
  (:require [speclj.core :refer :all]
            [empire.mutation.core :as core]))

(describe "read-source-forms"
  (it "reads Clojure forms from a string"
    (let [forms (core/read-source-forms "(ns foo) (defn bar [] 42)")]
      (should= 2 (count forms))
      (should= 'ns (first (first forms))))))
```

**Step 2: Run test to verify it fails**

Run: `clj -M:spec spec/empire/mutation/core_spec.clj`
Expected: FAIL — namespace not found.

**Step 3: Implement read-source-forms**

```clojure
(ns empire.mutation.core
  (:require [clojure.tools.reader :as reader]
            [clojure.tools.reader.reader-types :as reader-types]
            [empire.mutation.mutations :as mutations]
            [empire.mutation.runner :as runner]))

(defn read-source-forms
  "Parse a source string into a vector of top-level forms.
   Handles .cljc reader conditionals."
  [source-str]
  (let [rdr (reader-types/source-logging-push-back-reader source-str)
        opts {:read-cond :allow :features #{:clj} :eof ::eof}]
    (loop [forms []]
      (let [form (reader/read opts rdr)]
        (if (= ::eof form)
          forms
          (recur (conj forms form)))))))
```

**Step 4: Run test to verify it passes**

Run: `clj -M:spec spec/empire/mutation/core_spec.clj`
Expected: PASS

**Step 5: Write failing test for discover-all-mutations**

Add to `core_spec.clj`:

```clojure
(describe "discover-all-mutations"
  (it "finds mutations across multiple forms"
    (let [forms (core/read-source-forms "(defn foo [] (+ 1 2)) (defn bar [] (> x 0))")
          sites (core/discover-all-mutations forms)]
      (should (some #(= (:original %) '+) sites))
      (should (some #(= (:original %) '>) sites))
      (should (some #(= (:original %) 1) sites)))))
```

**Step 6: Run to verify it fails, then implement**

```clojure
(defn discover-all-mutations
  "Find all mutation sites across a vector of top-level forms.
   Returns a flat vector of mutation sites with :form-index added."
  [forms]
  (vec (mapcat
         (fn [idx form]
           (map #(assoc % :form-index idx)
                (mutations/find-mutations form)))
         (range) forms)))
```

**Step 7: Run test to verify it passes**

Run: `clj -M:spec spec/empire/mutation/core_spec.clj`
Expected: PASS

**Step 8: Commit**

```bash
git add src/empire/mutation/core.cljc spec/empire/mutation/core_spec.clj
git commit -m "feat: add core orchestration with source reading and discovery"
```

---

### Task 4: Core Orchestration — Execution and Reporting

**Files:**
- Modify: `src/empire/mutation/core.cljc`
- Modify: `spec/empire/mutation/core_spec.clj`

**Step 1: Write failing test for mutate-and-test (single mutant)**

Add to `core_spec.clj`:

```clojure
(describe "mutate-and-test"
  (it "writes mutated file, runs spec, restores original"
    (let [temp-file (java.io.File/createTempFile "mutant" ".cljc")
          temp-path (.getPath temp-file)
          original-content "(ns test-ns)\n(defn foo [] (+ 1 2))\n"]
      (spit temp-path original-content)
      (with-redefs [runner/run-spec (fn [_] :killed)]
        (let [forms (core/read-source-forms original-content)
              sites (core/discover-all-mutations forms)
              plus-site (first (filter #(= (:original %) '+) sites))
              result (core/mutate-and-test temp-path original-content
                                           forms plus-site "fake_spec.clj")]
          (should= :killed (:result result))
          ;; Original file should be restored
          (should= original-content (slurp temp-path))))
      (.delete temp-file))))
```

**Step 2: Run to verify it fails**

Run: `clj -M:spec spec/empire/mutation/core_spec.clj`
Expected: FAIL — `mutate-and-test` not defined.

**Step 3: Implement mutate-and-test**

Add to `core.cljc`:

```clojure
(defn- serialize-forms
  "Serialize a vector of forms to a string."
  [forms]
  (clojure.string/join "\n\n" (map pr-str forms)))

(defn mutate-and-test
  "Apply one mutation, write file, run spec, restore original.
   Returns {:site site :result :killed/:survived}."
  [source-path original-content forms site spec-path]
  (let [mutated-forms (update forms (:form-index site)
                              #(mutations/apply-mutation % (:index site)))]
    (try
      (spit source-path (serialize-forms mutated-forms))
      {:site site :result (runner/run-spec spec-path)}
      (finally
        (spit source-path original-content)))))
```

**Step 4: Run test to verify it passes**

Run: `clj -M:spec spec/empire/mutation/core_spec.clj`
Expected: PASS

**Step 5: Write failing test for format-report**

Add to `core_spec.clj`:

```clojure
(describe "format-report"
  (it "produces summary with kill count"
    (let [results [{:site {:description "+ → -"} :result :killed}
                   {:site {:description "1 → 0"} :result :survived}]
          report (core/format-report "src/empire/foo.cljc" "spec/empire/foo_spec.clj" results)]
      (should-contain "1/2 mutants killed" report)
      (should-contain "SURVIVED" report)
      (should-contain "KILLED" report))))
```

**Step 6: Run to verify it fails, then implement**

Add to `core.cljc`:

```clojure
(defn format-report
  "Format mutation testing results as a console report string."
  [source-path spec-path results]
  (let [total (count results)
        killed (count (filter #(= :killed (:result %)) results))
        pct (if (zero? total) 0.0 (* 100.0 (/ killed total)))
        survivors (filter #(= :survived (:result %)) results)]
    (str
      (format "=== Mutation Testing: %s ===%n" source-path)
      (format "Spec: %s%n" spec-path)
      (format "Found %d mutation sites.%n%n" total)
      (apply str
        (map-indexed
          (fn [i r]
            (format "[%3d/%d] %-8s  %s%n"
                    (inc i) total
                    (if (= :killed (:result r)) "KILLED" "SURVIVED")
                    (:description (:site r))))
          results))
      (format "%n=== Summary ===%n")
      (format "%d/%d mutants killed (%.1f%%)%n" killed total pct)
      (when (seq survivors)
        (str "Survivors:\n"
             (apply str
               (map (fn [r]
                      (format "  #%d  %s%n"
                              (inc (:index (:site r)))
                              (:description (:site r))))
                    survivors)))))))
```

**Step 7: Run test to verify it passes**

Run: `clj -M:spec spec/empire/mutation/core_spec.clj`
Expected: PASS

**Step 8: Commit**

```bash
git add src/empire/mutation/core.cljc spec/empire/mutation/core_spec.clj
git commit -m "feat: add mutate-and-test execution and report formatting"
```

---

### Task 5: CLI Entry Point and deps.edn Alias

**Files:**
- Modify: `src/empire/mutation/core.cljc`
- Modify: `spec/empire/mutation/core_spec.clj`
- Modify: `deps.edn`

**Step 1: Write failing test for validate-args**

Add to `core_spec.clj`:

```clojure
(describe "validate-args"
  (it "returns error when no args given"
    (let [result (core/validate-args [])]
      (should-contain :error result)))

  (it "returns error when source file doesn't exist"
    (let [result (core/validate-args ["nonexistent.cljc"])]
      (should-contain :error result)))

  (it "returns error when spec file doesn't exist"
    (with-redefs [runner/spec-exists? (fn [_] false)]
      (let [temp (java.io.File/createTempFile "src" ".cljc")
            result (core/validate-args [(.getPath temp)])]
        (should-contain :error result)
        (.delete temp)))))
```

**Step 2: Run to verify it fails, then implement**

Add to `core.cljc`:

```clojure
(defn validate-args
  "Validate command-line arguments. Returns {:source-path ... :spec-path ...}
   or {:error \"message\"}."
  [args]
  (cond
    (empty? args)
    {:error "Usage: clj -M:mutate <source-file.cljc>"}

    (not (.exists (java.io.File. (first args))))
    {:error (str "Source file not found: " (first args))}

    :else
    (let [spec-path (runner/source->spec-path (first args))]
      (if (runner/spec-exists? spec-path)
        {:source-path (first args) :spec-path spec-path}
        {:error (str "No spec found at: " spec-path)}))))
```

**Step 3: Run test to verify it passes**

Run: `clj -M:spec spec/empire/mutation/core_spec.clj`
Expected: PASS

**Step 4: Implement -main**

Add to `core.cljc`:

```clojure
(defn run-mutation-testing
  "Run mutation testing on a single source file."
  [source-path spec-path]
  (let [original-content (slurp source-path)
        forms (read-source-forms original-content)
        sites (discover-all-mutations forms)]
    (println (format "=== Mutation Testing: %s ===" source-path))
    (println (format "Spec: %s" spec-path))
    (println (format "Found %d mutation sites.\n" (count sites)))
    (let [results (doall
                    (map-indexed
                      (fn [i site]
                        (let [result (mutate-and-test source-path original-content
                                                      forms site spec-path)]
                          (println (format "[%3d/%d] %-8s  %s"
                                          (inc i) (count sites)
                                          (if (= :killed (:result result)) "KILLED" "SURVIVED")
                                          (:description site)))
                          (flush)
                          result))
                      sites))
          killed (count (filter #(= :killed (:result %)) results))
          total (count results)
          pct (if (zero? total) 0.0 (* 100.0 (/ killed total)))
          survivors (filter #(= :survived (:result %)) results)]
      (println (format "\n=== Summary ==="))
      (println (format "%d/%d mutants killed (%.1f%%)" killed total pct))
      (when (seq survivors)
        (println "Survivors:")
        (doseq [r survivors]
          (println (format "  #%d  %s"
                          (inc (:index (:site r)))
                          (:description (:site r)))))))))

(defn -main [& args]
  (let [validated (validate-args (vec args))]
    (if (:error validated)
      (do (println (:error validated))
          (System/exit 1))
      (run-mutation-testing (:source-path validated) (:spec-path validated)))))
```

**Step 5: Add :mutate alias to deps.edn**

Add this alias alongside the existing ones in `deps.edn`:

```clojure
:mutate {:main-opts ["-m" "empire.mutation.core"]
         :extra-deps {org.clojure/tools.reader {:mvn/version "1.4.2"}}
         :extra-paths ["spec"]}
```

Note: `"spec"` is in `:extra-paths` so that the tool can find and run spec files.

**Step 6: Run all mutation tool tests**

Run: `clj -M:spec spec/empire/mutation/`
Expected: PASS — all tests across the three spec files pass.

**Step 7: Commit**

```bash
git add src/empire/mutation/core.cljc spec/empire/mutation/core_spec.clj deps.edn
git commit -m "feat: add CLI entry point and deps.edn :mutate alias"
```

---

### Task 6: Integration Test — Run Against a Real Source File

**Files:**
- Modify: `spec/empire/mutation/core_spec.clj`

**Step 1: Write an integration test**

Add to `core_spec.clj`:

```clojure
(describe "integration: discover mutations in a real source file"
  (it "finds mutation sites in combat.cljc"
    (let [content (slurp "src/empire/combat.cljc")
          forms (core/read-source-forms content)
          sites (core/discover-all-mutations forms)]
      (should (> (count sites) 0))
      (println (format "Found %d mutation sites in combat.cljc" (count sites))))))
```

**Step 2: Run to verify it passes**

Run: `clj -M:spec spec/empire/mutation/core_spec.clj`
Expected: PASS — prints count of mutation sites found.

**Step 3: Smoke test the CLI end-to-end**

Run: `clj -M:mutate src/empire/combat.cljc`
Expected: Tool runs, discovers mutations, executes specs for each, prints report. This will take a while (one `clj -M:spec` invocation per mutant). Verify it completes and restores the original file.

**Step 4: Verify original file unchanged**

Run: `git diff src/empire/combat.cljc`
Expected: No changes.

**Step 5: Commit**

```bash
git add spec/empire/mutation/core_spec.clj
git commit -m "test: add integration test for mutation discovery"
```

---

### Task 7: Run Full Test Suite and Verify

**Step 1: Run all project tests**

Run: `clj -M:spec`
Expected: All existing tests still pass. No regressions from the new mutation/ namespace.

**Step 2: Run mutation tool tests specifically**

Run: `clj -M:spec spec/empire/mutation/`
Expected: All mutation tool tests pass.
