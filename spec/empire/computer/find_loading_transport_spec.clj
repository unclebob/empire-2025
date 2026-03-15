(ns empire.computer.find-loading-transport-spec
  (:require [empire.computer.core :as core]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-player-map! set-test-world! update-test-world!]]
            [speclj.core :refer :all]))

(describe "find-loading-transport"
  (before (reset-all-atoms!))

  (it "finds a loading transport with room"
    (set-test-world! [[{:type :sea :contents {:type :transport :owner :computer
                                              :transport-mission :loading :army-count 2}}]])
    (should= [0 0] (core/find-loading-transport)))

  (it "skips full transports"
    (set-test-world! [[{:type :sea :contents {:type :transport :owner :computer
                                              :transport-mission :loading :army-count 6}}]])
    (should-be-nil (core/find-loading-transport)))

  (it "skips non-loading transports"
    (set-test-world! [[{:type :sea :contents {:type :transport :owner :computer
                                              :transport-mission :sailing :army-count 0}}]])
    (should-be-nil (core/find-loading-transport)))

  (it "skips player transports"
    (set-test-world! [[{:type :sea :contents {:type :transport :owner :player
                                              :transport-mission :loading :army-count 0}}]])
    (should-be-nil (core/find-loading-transport)))

  (it "excludes transport with matching unload-event-id"
    (set-test-world! [[{:type :sea :contents {:type :transport :owner :computer
                                              :transport-mission :loading :army-count 0
                                              :unload-event-id 42}}]])
    (should-be-nil (core/find-loading-transport 42)))

  (it "includes transport with different unload-event-id"
    (set-test-world! [[{:type :sea :contents {:type :transport :owner :computer
                                              :transport-mission :loading :army-count 0
                                              :unload-event-id 42}}]])
    (should= [0 0] (core/find-loading-transport 99))))
