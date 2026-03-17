(ns empire.computer.transport-core-spec
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.computer.transport-core :as tc]
            [empire.test.utils :refer [reset-all-atoms! set-test-computer-map! set-test-world!]]))

(describe "transport-core"
  (before (reset-all-atoms!))

  (context "recently-unloaded-country? (L21)"
    (it "returns truthy when unloaded within 10 rounds"
      (test-utils/set-test-state! :round-number 15)
      (should (tc/recently-unloaded-country? {1 10} 1)))

    (it "returns falsy when unloaded 10+ rounds ago"
      (test-utils/set-test-state! :round-number 20)
      (should-not (tc/recently-unloaded-country? {1 10} 1)))

    (it "returns nil when country-id not in map"
      (test-utils/set-test-state! :round-number 5)
      (should-be-nil (tc/recently-unloaded-country? {2 1} 1)))

    (it "boundary: exactly 10 rounds ago is not recent"
      (test-utils/set-test-state! :round-number 15)
      (should-not (tc/recently-unloaded-country? {1 5} 1))))

  (context "computer-map decisions"
    (it "treats sea hidden occupancy as passable"
      (set-test-world! [[{:type :sea :contents {:type :transport :owner :computer}}
                         {:type :sea :contents {:type :destroyer :owner :player}}]])
      (set-test-computer-map! [[{:type :sea :contents {:type :transport :owner :computer}}
                                {:type :sea}]])
      (should (some #{[0 1]} (tc/get-passable-sea-neighbors [0 0]))))

    (it "does not detect adjacent land visible only on game-map"
      (set-test-world! [[{:type :sea :contents {:type :transport :owner :computer}}
                         {:type :land}]])
      (set-test-computer-map! [[{:type :sea :contents {:type :transport :owner :computer}}
                                nil]])
      (should-not (tc/adjacent-to-land? [0 0]))
      (should-be-nil (tc/find-adjacent-land-pos [0 0])))))
