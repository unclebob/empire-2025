(ns empire.ui.util.input.dispatch-debug-spec
  (:require [empire.test.utils :as test-utils]
            [empire.ui.util.input.dispatch :as dispatch]
            [speclj.core :refer :all]))

(describe "debug drag helpers"
  (before (test-utils/reset-all-atoms!))

  (it "modifier-held? returns true when any modifier key is true"
    (should (dispatch/modifier-held? {:ctrl true}))
    (should (dispatch/modifier-held? {:meta true}))
    (should (dispatch/modifier-held? {:alt true})))

  (it "modifier-held? returns false when no modifier is true"
    (should-not (dispatch/modifier-held? {:ctrl false :meta false :alt false})))

  (it "debug-drag-start! sets start and current positions"
    (dispatch/debug-drag-start! 10 20)
    (should= [10 20] (test-utils/read-test-state :debug-drag-start))
    (should= [10 20] (test-utils/read-test-state :debug-drag-current)))

  (it "debug-drag-update! updates current position only when dragging"
    (test-utils/set-test-state! :debug-drag-start nil)
    (test-utils/set-test-state! :debug-drag-current nil)
    (dispatch/debug-drag-update! 1 2)
    (should-be-nil (test-utils/read-test-state :debug-drag-current))
    (dispatch/debug-drag-start! 3 4)
    (dispatch/debug-drag-update! 5 6)
    (should= [5 6] (test-utils/read-test-state :debug-drag-current)))

  (it "debug-drag-end! clears drag state even when no modifier held"
    (dispatch/debug-drag-start! 10 10)
    (dispatch/debug-drag-end! 20 20 {:ctrl false :meta false :alt false})
    (should-be-nil (test-utils/read-test-state :debug-drag-start))
    (should-be-nil (test-utils/read-test-state :debug-drag-current))
    (should= "" (test-utils/read-test-state :debug-message)))

  (it "debug-drag-end! does not write dump when selection has no area"
    (dispatch/debug-drag-start! 10 10)
    (with-redefs [empire.game-mechanics.debug.dump/screen-coords-to-cell-range (fn [_ _] [[1 1] [1 1]])
                  empire.game-mechanics.debug.dump/write-dump! (fn [_ _] (throw (ex-info "should not dump" {})))]
      (dispatch/debug-drag-end! 20 20 {:ctrl true})
      (should= "" (test-utils/read-test-state :debug-message)))
    (should-be-nil (test-utils/read-test-state :debug-drag-start))
    (should-be-nil (test-utils/read-test-state :debug-drag-current)))

  (it "debug-drag-end! writes dump and updates debug message for area selection"
    (dispatch/debug-drag-start! 10 10)
    (with-redefs [empire.game-mechanics.debug.dump/screen-coords-to-cell-range (fn [_ _] [[1 1] [2 3]])
                  empire.game-mechanics.debug.dump/write-dump! (fn [start end]
                                                                 (should= [1 1] start)
                                                                 (should= [2 3] end)
                                                                 "debug-dump.txt")]
      (dispatch/debug-drag-end! 20 30 {:ctrl true})
      (should= "Debug: debug-dump.txt" (test-utils/read-test-state :debug-message))
      (should= "Debug log written: debug-dump.txt" (test-utils/read-test-state :turn-message)))
    (should-be-nil (test-utils/read-test-state :debug-drag-start))
    (should-be-nil (test-utils/read-test-state :debug-drag-current)))

  (it "debug-drag-end! is no-op when no drag is active"
    (dispatch/debug-drag-end! 1 1 {:ctrl true})
    (should-be-nil (test-utils/read-test-state :debug-drag-start))
    (should-be-nil (test-utils/read-test-state :debug-drag-current))))
