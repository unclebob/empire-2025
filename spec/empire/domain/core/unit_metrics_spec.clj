(ns empire.domain.core.unit-metrics-spec
  (:require [speclj.core :refer :all]
            [empire.domain.core.unit-metrics :as unit-metrics]))

(describe "unit hit-scaling math"
  (it "scales values with ceiling division semantics"
    (should= 1 (unit-metrics/scale-by-hits 2 1 3))
    (should= 2 (unit-metrics/scale-by-hits 2 2 3))
    (should= 2 (unit-metrics/scale-by-hits 2 3 3)))

  (it "computes effective speed"
    (should= 1 (unit-metrics/effective-speed 2 1 3))
    (should= 2 (unit-metrics/effective-speed 2 3 3)))

  (it "computes effective capacity"
    (should= 4 (unit-metrics/effective-capacity 8 4 8))
    (should= 8 (unit-metrics/effective-capacity 8 8 8))))

(describe "naval unit predicate"
  (it "recognizes naval unit types"
    (should (unit-metrics/naval-unit? :carrier))
    (should-not (unit-metrics/naval-unit? :army))))
