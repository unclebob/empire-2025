(ns empire.computer.find-adjacent-loading-transport-spec
  (:require [empire.computer.core :as core]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-player-map! set-test-world! update-test-world!]]
            [speclj.core :refer :all]))

(describe "find-adjacent-loading-transport"
  (before (reset-all-atoms!))

  (it "finds adjacent loading transport"
    (set-test-world! [[{:type :land} {:type :sea}]
                      [{:type :sea :contents {:type :transport :owner :computer
                                              :transport-mission :loading :army-count 0}} {:type :sea}]])
    (should= [1 0] (core/find-adjacent-loading-transport [0 0])))

  (it "returns nil when no adjacent transport"
    (set-test-world! [[{:type :land} {:type :sea}]
                      [{:type :sea} {:type :sea}]])
    (should-be-nil (core/find-adjacent-loading-transport [0 0])))

  (it "skips full adjacent transports"
    (set-test-world! [[{:type :land} {:type :sea}]
                      [{:type :sea :contents {:type :transport :owner :computer
                                              :transport-mission :loading :army-count 6}} {:type :sea}]])
    (should-be-nil (core/find-adjacent-loading-transport [0 0])))

  (it "excludes transport with matching unload-event-id"
    (set-test-world! [[{:type :land} {:type :sea}]
                      [{:type :sea :contents {:type :transport :owner :computer
                                              :transport-mission :loading :army-count 0
                                              :unload-event-id 42}} {:type :sea}]])
    (should-be-nil (core/find-adjacent-loading-transport [0 0] 42))))
