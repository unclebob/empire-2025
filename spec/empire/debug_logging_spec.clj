(ns empire.debug-logging-spec
  (:require [empire.game-mechanics.debug.logging :as debug-logging]
            [empire.test.utils :as test-utils]
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
    (should= 2000 (count (test-utils/read-test-state :computer-event-log)))))
