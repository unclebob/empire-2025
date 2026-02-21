(ns empire.mutation.mutations-spec
  (:require [speclj.core :refer :all]
            [empire.mutation.mutations :as m]
            [empire.mutation.core :as core]))

(describe "mutation-rules"
  (it "contains the core mutation set"
    (should (seq m/rules))
    (should (every? #(contains? % :original) m/rules))
    (should (every? #(contains? % :mutant) m/rules))
    (should (every? #(contains? % :category) m/rules))
    (should (every? #(contains? % :position) m/rules))))

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
      (should (>= (count sites) 5))))

  (it "finds mutations inside vectors (let bindings)"
    (let [sites (m/find-mutations '(let [x 0] (+ x 1)))]
      (should (some #(and (= (:original %) 0) (= (:category %) :constant)) sites))))

  (it "returns empty vector for form with no matches"
    (should= [] (m/find-mutations '(foo bar baz)))))

(describe "equivalent mutant suppression"
  (it "suppresses < -> <= when comparing (rand) to a number"
    (let [sites (m/find-mutations '(if (< (rand) 0.5) :a :b))]
      (should-not (some #(and (= (:original %) '<) (= (:mutant %) '<=)) sites))))

  (it "suppresses <= -> < when comparing (rand) to a number"
    (let [sites (m/find-mutations '(if (<= (rand) 0.5) :a :b))]
      (should-not (some #(and (= (:original %) '<=) (= (:mutant %) '<)) sites))))

  (it "does not suppress < -> <= for non-rand comparisons"
    (let [sites (m/find-mutations '(if (< x 10) :a :b))]
      (should (some #(and (= (:original %) '<) (= (:mutant %) '<=)) sites))))

  (it "suppresses > -> >= when comparing (rand) to a number"
    (let [sites (m/find-mutations '(if (> (rand) 0.5) :a :b))]
      (should-not (some #(and (= (:original %) '>) (= (:mutant %) '>=)) sites))))

  (it "does not suppress > -> >= for non-rand comparisons"
    (let [sites (m/find-mutations '(if (> hits 0) :a :b))]
      (should (some #(and (= (:original %) '>) (= (:mutant %) '>=)) sites)))))

(describe "line numbers"
  (it "attaches :line from reader metadata for symbols"
    (let [forms (core/read-source-forms "(defn foo [] (+ 1 2))")
          sites (m/find-mutations (first forms))
          plus-site (first (filter #(= (:original %) '+) sites))]
      (should-not-be-nil (:line plus-site))))

  (it "attaches :line from parent metadata for literals"
    (let [forms (core/read-source-forms "(defn foo [] (+ 1 2))")
          sites (m/find-mutations (first forms))
          one-site (first (filter #(= (:original %) 1) sites))]
      (should-not-be-nil (:line one-site))))

  (it "returns nil :line for forms without metadata"
    (let [form (list (symbol "+") 1 2)
          sites (m/find-mutations form)
          plus-site (first (filter #(= (:original %) '+) sites))]
      (should-be-nil (:line plus-site)))))

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
