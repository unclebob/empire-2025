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

  (it "writes an error log when the world contains malformed cells"
    (test-utils/set-test-world! [[{:type :land :contents {:fuel 31}}]])
    (let [captured (atom nil)]
      (with-redefs [integrity/write-integrity-error-log! (fn [invalids]
                                                           (reset! captured invalids)
                                                           "error-test.log")]
        (should= "error-test.log" (integrity/check-world-integrity!))
        (should= 1 (count @captured))))))
