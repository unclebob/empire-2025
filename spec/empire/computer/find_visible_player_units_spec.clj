(ns empire.computer.find-visible-player-units-spec
  (:require [empire.computer.shared.world-query :as world-query]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-player-map! set-test-world! update-test-world!]]
            [speclj.core :refer :all]))

(describe "find-visible-player-units"
  (before (reset-all-atoms!))

  (it "finds player units on computer map"
    (set-test-computer-map!
     [[{:type :sea :contents {:type :army :owner :player}} {:type :sea}]
      [{:type :sea} {:type :sea}]])
    (should= [[0 0]] (world-query/find-visible-player-units)))

  (it "ignores computer units"
    (set-test-computer-map!
     [[{:type :sea :contents {:type :army :owner :computer}} {:type :sea}]
      [{:type :sea} {:type :sea}]])
    (should= [] (world-query/find-visible-player-units)))

  (it "returns empty when no units"
    (set-test-computer-map!
     [[{:type :sea} {:type :sea}]])
    (should= [] (world-query/find-visible-player-units))))
