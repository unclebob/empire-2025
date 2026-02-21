(ns empire.mutation.core-spec
  (:require [speclj.core :refer :all]
            [empire.mutation.core :as core]))

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

(run-specs)
