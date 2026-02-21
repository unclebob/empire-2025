(ns empire.mutation.core-spec
  (:require [speclj.core :refer :all]
            [empire.mutation.core :as core]
            [empire.mutation.coverage :as coverage]
            [empire.mutation.runner :as runner]))

(describe "read-source-forms"
  (it "reads Clojure forms from a string"
    (let [forms (core/read-source-forms "(ns foo) (defn bar [] 42)")]
      (should= 2 (count forms))
      (should= 'ns (first (first forms))))))

(describe "discover-all-mutations"
  (it "finds mutations across multiple forms"
    (let [forms (core/read-source-forms "(defn foo [] (+ 1 2)) (defn bar [] (> x 0))")
          sites (core/discover-all-mutations forms)]
      (should (some #(= (:original %) '+) sites))
      (should (some #(= (:original %) '>) sites))
      (should (some #(= (:original %) 1) sites)))))

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

(describe "partition-by-coverage"
  (it "separates covered from uncovered sites"
    (let [sites [{:line 1 :original '+} {:line 2 :original '-} {:line 3 :original '>}]
          covered-lines #{1 3}
          [covered uncovered] (core/partition-by-coverage sites covered-lines)]
      (should= 2 (count covered))
      (should= 1 (count uncovered))
      (should= 2 (:line (first uncovered)))))

  (it "treats nil-line sites as covered"
    (let [sites [{:line nil :original '+} {:line 5 :original '-}]
          covered-lines #{5}
          [covered uncovered] (core/partition-by-coverage sites covered-lines)]
      (should= 2 (count covered))
      (should= 0 (count uncovered))))

  (it "treats all sites as covered when coverage is nil"
    (let [sites [{:line 1 :original '+} {:line 2 :original '-}]
          [covered uncovered] (core/partition-by-coverage sites nil)]
      (should= 2 (count covered))
      (should= 0 (count uncovered)))))

(describe "format-report"
  (it "produces summary with kill count"
    (let [results [{:site {:description "+ -> -"} :result :killed}
                   {:site {:description "1 -> 0"} :result :survived}]
          report (core/format-report "src/empire/foo.cljc" "spec/empire/foo_spec.clj" results 0)]
      (should-contain "1/2 mutants killed" report)
      (should-contain "SURVIVED" report)
      (should-contain "KILLED" report)))

  (it "includes uncovered count in summary"
    (let [results [{:site {:description "+ -> -"} :result :killed}]
          report (core/format-report "src/empire/foo.cljc" "spec/empire/foo_spec.clj" results 3)]
      (should-contain "1/1 mutants killed" report)
      (should-contain "3 uncovered" report))))

(describe "integration: discover mutations in a real source file"
  (it "finds mutation sites in combat.cljc"
    (let [content (slurp "src/empire/combat.cljc")
          forms (core/read-source-forms content)
          sites (core/discover-all-mutations forms)]
      (should (> (count sites) 0))
      (println (format "Found %d mutation sites in combat.cljc" (count sites))))))

(run-specs)
