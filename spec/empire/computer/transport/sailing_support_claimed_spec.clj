(ns empire.computer.transport.sailing-support-claimed-spec
  (:require [empire.computer.transport.sailing-support :as support]
            [empire.state.api :as sa]
            [empire.test.utils :refer [reset-all-atoms! build-test-map set-test-computer-map!]]
            [speclj.core :refer :all]))

(describe "claimed-land? (sailing-support)"
  (it "returns true for land with country-id"
    (should (@#'support/claimed-land? {:type :land :country-id 1})))

  (it "returns true for computer city"
    (should (@#'support/claimed-land? {:type :city :city-status :computer})))

  (it "returns false for unclaimed land"
    (should-not (@#'support/claimed-land? {:type :land})))

  (it "returns false for sea"
    (should-not (@#'support/claimed-land? {:type :sea}))))

(describe "random-sail-path"
  (before (reset-all-atoms!))

  (it "returns a path of sea cells away from claimed land"
    (set-test-computer-map! [[{:type :sea} {:type :sea} {:type :sea} {:type :sea} {:type :sea} {:type :sea}]
                             [{:type :sea} {:type :sea} {:type :sea} {:type :sea} {:type :sea} {:type :sea}]])
    (let [path (support/random-sail-path [0 0])]
      (should (or (nil? path) (and (vector? path) (<= (count path) 4))))))

  (it "returns nil when surrounded by claimed land"
    (set-test-computer-map! [[{:type :land :country-id 1} {:type :sea} {:type :land :country-id 1}]
                             [{:type :land :country-id 1} {:type :land :country-id 1} {:type :land :country-id 1}]])
    (should-be-nil (support/random-sail-path [1 0]))))
