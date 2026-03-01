(ns empire.application.runtime-spec
  (:require [speclj.core :refer :all]
            [empire.application.runtime :as runtime]))

(describe "default-state-ctx"
  (it "returns load/save/invariant functions"
    (let [ctx (runtime/default-state-ctx)]
      (should (fn? (:load-world ctx)))
      (should (fn? (:save-world! ctx)))
      (should (fn? (:check-invariants ctx))))))

(run-specs)
