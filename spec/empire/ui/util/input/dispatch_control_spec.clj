(ns empire.ui.util.input.dispatch-control-spec
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.ui.util.input.dispatch :as dispatch]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-player-map! set-test-computer-map! set-test-world!]]))

(describe "key-down :P"
  (before
    (reset-all-atoms!)
    (test-utils/set-test-state! :paused false)
    (test-utils/set-test-state! :pause-requested false)
    (test-utils/set-test-state! :backtick-pressed false))

  (it "toggles pause when P is pressed"
    (dispatch/dispatch-key :P nil)
    (should (test-utils/read-test-state :pause-requested))))

(describe "key-down :space when paused"
  (before
    (reset-all-atoms!)
    (set-test-world! (build-test-map ["O"]))
    (set-test-player-map! (build-test-map ["#"]))
    (set-test-computer-map! (build-test-map ["#"]))
    (test-utils/set-test-state! :paused true)
    (test-utils/set-test-state! :pause-requested false)
    (test-utils/set-test-state! :backtick-pressed false)
    (test-utils/set-test-state! :player-items [])
    (test-utils/set-test-state! :computer-items [])
    (test-utils/set-test-state! :round-number 5))

  (it "starts new round when both item lists are empty"
    (dispatch/dispatch-key :space nil)
    (should= 6 (test-utils/read-test-state :round-number)))

  (it "sets pause-requested to pause after round"
    (dispatch/dispatch-key :space nil)
    (should= true (test-utils/read-test-state :pause-requested)))

  (it "unpauses to allow game loop to process"
    (dispatch/dispatch-key :space nil)
    (should= false (test-utils/read-test-state :paused))))
