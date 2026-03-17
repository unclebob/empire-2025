(ns empire.debug-integrity-spec
  (:require [empire.game-mechanics.debug.integrity :as integrity]
            [empire.test.utils :as test-utils]
            [speclj.core :refer :all]))

(describe "world integrity validation"
  (before (test-utils/reset-all-atoms!))

  (it "detects malformed contents with missing type or owner"
    (let [invalids (integrity/invalid-cells [[{:type :land}
                                              {:type :sea :contents {:fuel 31}}]])]
      (should= 1 (count invalids))
      (should= [0 1] (:pos (first invalids)))))

  (it "reports only invalid cells"
    (let [invalids (integrity/invalid-cells [[{:type :land}
                                              {:type :sea :contents {:type :submarine :owner :computer}}]
                                             [{:contents {:owner :player}}
                                              {:type :sea}]])]
      (should= 1 (count invalids))
      (should= [1 0] (:pos (first invalids)))))

  (it "formats round diagnostics into the integrity report"
    (test-utils/set-test-state! :round-number 7)
    (test-utils/set-test-state! :action-log [{:timestamp 12 :action [:move :fighter [1 1] [1 2]]}])
    (test-utils/set-test-state! :computer-event-log [{:round 7 :event :fighter-move :pos [1 2] :to [1 3]}])
    (test-utils/set-test-state! :player-movement-log [{:round 7 :unit-type :army :from [2 2] :to [2 3] :mode :moving :event :move :reason nil}])
    (let [report (integrity/format-integrity-report
                  [{:pos [3 4]
                    :cell {:type :sea :contents {:fuel 31}}
                    :explain-data {:problem :bad-cell}}])]
      (should-contain "Empire Integrity Error" report)
      (should-contain "Position: [3 4]" report)
      (should-contain "Actions (last 50)" report)
      (should-contain "fighter-move" report)
      (should-contain "army [2 2]->[2 3]" report)))

  (it "formats empty diagnostics and movement reasons"
    (test-utils/set-test-state! :round-number 9)
    (test-utils/set-test-state! :action-log [])
    (test-utils/set-test-state! :computer-event-log [])
    (test-utils/set-test-state! :player-movement-log [{:round 9
                                                       :unit-type :fighter
                                                       :from [1 1]
                                                       :to [1 2]
                                                       :mode :moving
                                                       :event :blocked
                                                       :reason :fuel}])
    (let [report (integrity/format-integrity-report
                  [{:pos [0 0]
                    :cell {:type :land :contents {:fuel 1}}
                    :explain-data {:problem :bad-cell}}])]
      (should-contain "  (none)" report)
      (should-contain "fighter [1 1]->[1 2] moving blocked (fuel)" report)))

  (it "returns nil when the world is empty"
    (test-utils/set-test-world! [])
    (test-utils/set-test-state! :integrity-check-enabled true)
    (should-be-nil (integrity/check-world-integrity!)))

  (it "writes an error log when the world contains malformed cells"
    (test-utils/set-test-world! [[{:type :land :contents {:fuel 31}}]])
    (test-utils/set-test-state! :integrity-check-enabled true)
    (let [captured (atom nil)]
      (with-redefs [integrity/write-integrity-error-log! (fn [invalids]
                                                           (reset! captured invalids)
                                                           "error-test.log")]
        (should= "error-test.log" (integrity/check-world-integrity!))
        (should= 1 (count @captured)))))

  (it "does nothing when integrity checking is disabled"
    (test-utils/set-test-world! [[{:type :land :contents {:fuel 31}}]])
    (test-utils/set-test-state! :integrity-check-enabled false)
    (with-redefs [integrity/write-integrity-error-log! (fn [_]
                                                         (should-not "should not write a log")
                                                         "error-test.log")]
      (should-be-nil (integrity/check-world-integrity!))))

  (it "generates timestamped clj error log filenames"
    (should (re-matches #"error-\d{4}-\d{2}-\d{2}-\d{6}\.log"
                        (integrity/generate-error-filename)))))
