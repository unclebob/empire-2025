(ns empire.debug-logging-spec
  (:require [empire.computer.transport.core :as transport-core]
            [empire.game-mechanics.debug.logging :as debug-logging]
            [empire.test.utils :as test-utils]
            [clojure.edn :as edn]
            [speclj.core :refer :all]))

(describe "log-player-movement!"
  (before (test-utils/reset-all-atoms!))

  (it "appends movement entry to log"
    (debug-logging/log-player-movement! :army [1 2] [1 3] :moving :move nil)
    (should= 1 (count (test-utils/read-test-state :player-movement-log)))
    (let [entry (first (test-utils/read-test-state :player-movement-log))]
      (should= :army (:unit-type entry))
      (should= [1 2] (:from entry))
      (should= [1 3] (:to entry))
      (should= :move (:event entry))))

  (it "includes round number"
    (test-utils/set-test-state! :round-number 5)
    (debug-logging/log-player-movement! :army [0 0] [0 1] :explore :move nil)
    (should= 5 (:round (first (test-utils/read-test-state :player-movement-log)))))

  (it "keeps exactly 500 entries without truncating"
    (dotimes [_ 500]
      (debug-logging/log-player-movement! :army [0 0] [0 1] :moving :move nil))
    (should= 500 (count (test-utils/read-test-state :player-movement-log))))

  (it "truncates to 500 when exceeding limit"
    (dotimes [_ 501]
      (debug-logging/log-player-movement! :army [0 0] [0 1] :moving :move nil))
    (should= 500 (count (test-utils/read-test-state :player-movement-log))))

  (it "includes wake reason"
    (debug-logging/log-player-movement! :army [0 0] [0 1] :explore :wake :steps-exhausted)
    (should= :steps-exhausted (:reason (first (test-utils/read-test-state :player-movement-log))))))

(describe "log-action!"
  (before (test-utils/reset-all-atoms!))

  (it "appends action to log"
    (debug-logging/log-action! [:move :army [4 6] [4 7]])
    (should= 1 (count (test-utils/read-test-state :action-log)))
    (should= [:move :army [4 6] [4 7]]
             (:action (first (test-utils/read-test-state :action-log)))))

  (it "includes timestamp"
    (debug-logging/log-action! [:test])
    (should (number? (:timestamp (first (test-utils/read-test-state :action-log))))))

  (it "caps log at 100 entries"
    (dotimes [i 110]
      (debug-logging/log-action! [:action i]))
    (should= 100 (count (test-utils/read-test-state :action-log)))))

(describe "log-computer-event!"
  (before (test-utils/reset-all-atoms!))

  (it "appends computer event entry"
    (debug-logging/log-computer-event! :army-move [1 2] {:to [1 3]})
    (should= 1 (count (test-utils/read-test-state :computer-event-log)))
    (let [entry (first (test-utils/read-test-state :computer-event-log))]
      (should= :army-move (:event entry))
      (should= [1 2] (:pos entry))
      (should= [1 3] (:to entry))))

  (it "caps computer event log at 2000 entries"
    (dotimes [i 2001]
      (debug-logging/log-computer-event! :tick [0 i] {:n i}))
    (should= 2000 (count (test-utils/read-test-state :computer-event-log))))

  (it "records transport mission transitions"
    (test-utils/set-test-state! :computer-map
                                [[{:type :sea
                                   :contents {:type :transport
                                              :owner :computer
                                              :transport-id 17
                                              :army-count 3
                                              :transport-mission :loading}}]])
    (transport-core/log-transport-mission-transition! [0 0] :loading :unloading)
    (let [entry (first (test-utils/read-test-state :computer-event-log))]
      (should= :transport-mission-transition (:event entry))
      (should= [0 0] (:pos entry))
      (should= :loading (:from entry))
      (should= :unloading (:to entry))
      (should= 17 (:transport-id entry))
      (should= 3 (:armies entry))))

  (it "records transport write misses"
    (test-utils/set-test-world! [[{:type :sea}]])
    (test-utils/set-test-state! :computer-map [[{:type :sea}]])
    (transport-core/assoc-transport-field! [0 0] :sail-path [[1 0]])
    (let [entry (first (test-utils/read-test-state :computer-event-log))]
      (should= :transport-write-miss (:event entry))
      (should= [0 0] (:pos entry))
      (should= :assoc-transport-field (:op entry))
      (should= :sea (:cell-type entry))
      (should= nil (:cell-contents entry)))))

(describe "computer unit log snapshots"
  (before (test-utils/reset-all-atoms!))

  (it "attributes active discovery counts to the bound computer unit"
    (debug-logging/with-computer-unit-context
      17
      #(debug-logging/record-active-computer-unit-discovery! 2))
    (should= {17 2}
             (test-utils/read-test-state :computer-unit-round-discoveries)))

  (it "includes discovered cells for each computer unit"
    (let [world [[{:type :land
                   :contents {:type :army :owner :computer :computer-unit-id 11}}
                  {:type :sea
                   :contents {:type :transport :owner :computer :computer-unit-id 22}}]]]
      (should= [{:round 7
                 :pos [0 0]
                 :unit {:type :army :owner :computer :computer-unit-id 11}
                 :discovered-cells 3}
                {:round 7
                 :pos [0 1]
                 :unit {:type :transport :owner :computer :computer-unit-id 22}
                 :discovered-cells 0}]
               (debug-logging/computer-unit-snapshots world 7 {11 3})))))

(describe "log-computer-units!"
  (before (test-utils/reset-all-atoms!))

  (it "writes discovered-cell totals into the unit log"
    (let [log-file "/tmp/empire-debug-logging-spec.log"]
      (spit log-file "")
      (test-utils/set-test-state! :computer-unit-log-file log-file)
      (test-utils/set-test-state! :round-number 9)
      (test-utils/set-test-state! :computer-unit-round-discoveries {31 4})
      (test-utils/set-test-world! [[{:type :land
                                     :contents {:type :fighter
                                                :owner :computer
                                                :computer-unit-id 31}}]])
      (debug-logging/log-computer-units!)
      (let [entry (-> log-file slurp edn/read-string)]
        (should= 9 (:round entry))
        (should= [0 0] (:pos entry))
        (should= 4 (:discovered-cells entry)))))) 
