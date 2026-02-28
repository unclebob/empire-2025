(ns empire.computer.transport-core-spec
  (:require [speclj.core :refer :all]
            [empire.computer.transport-core :as tc]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [reset-all-atoms!]]))

(describe "transport-core"
  (before (reset-all-atoms!))

  (context "recently-unloaded-country? (L21)"
    (it "returns truthy when unloaded within 10 rounds"
      (reset! atoms/round-number 15)
      (should (tc/recently-unloaded-country? {1 10} 1)))

    (it "returns falsy when unloaded 10+ rounds ago"
      (reset! atoms/round-number 20)
      (should-not (tc/recently-unloaded-country? {1 10} 1)))

    (it "returns nil when country-id not in map"
      (reset! atoms/round-number 5)
      (should-be-nil (tc/recently-unloaded-country? {2 1} 1)))

    (it "boundary: exactly 10 rounds ago is not recent"
      (reset! atoms/round-number 15)
      (should-not (tc/recently-unloaded-country? {1 5} 1)))))
