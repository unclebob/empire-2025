(ns empire.mutation.core-spec
  (:require [speclj.core :refer :all]
            [empire.mutation.core :as core]
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

(describe "format-report"
  (it "produces summary with kill count"
    (let [results [{:site {:description "+ -> -"} :result :killed}
                   {:site {:description "1 -> 0"} :result :survived}]
          report (core/format-report "src/empire/foo.cljc" "spec/empire/foo_spec.clj" results)]
      (should-contain "1/2 mutants killed" report)
      (should-contain "SURVIVED" report)
      (should-contain "KILLED" report))))

(run-specs)
