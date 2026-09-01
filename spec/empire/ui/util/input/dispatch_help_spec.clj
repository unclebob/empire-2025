(ns empire.ui.util.input.dispatch-help-spec
  (:require [empire.test.utils :as test-utils]
            [empire.ui.util.help :as help]
            [empire.ui.util.input.dispatch :as dispatch]
            [speclj.core :refer :all]))

(describe "? key opens help"
  (before (test-utils/reset-all-atoms!))

  (it "opens the help window"
    (dispatch/dispatch-key (keyword "?") nil)
    (should= true (test-utils/read-test-state :help-open))))

(describe "keys while help is open"
  (before
    (test-utils/reset-all-atoms!)
    (test-utils/set-test-state! :help-open true)
    (test-utils/set-test-state! :pause-requested false)
    (test-utils/set-test-state! :paused false))

  (it "does not pause the game"
    (dispatch/dispatch-key :P nil)
    (should= false (test-utils/read-test-state :pause-requested)))

  (it "does not place backtick units"
    (test-utils/set-test-state! :backtick-pressed true)
    (dispatch/dispatch-key :A [0 0])
    (should= true (test-utils/read-test-state :help-open))
    (should= true (test-utils/read-test-state :backtick-pressed))))

(describe "mouse while help is open"
  (before
    (test-utils/reset-all-atoms!)
    (test-utils/set-test-state! :map-screen-dimensions [800 600])
    (test-utils/set-test-state! :text-area-dimensions [0 600 800 80]))

  (it "dismisses help when the dismiss button is clicked"
    (help/open-help!)
    (let [geom (help/help-geometry 800 680)
          button (:dismiss-button geom)]
      (test-utils/set-test-state! :help-geometry geom)
      (dispatch/mouse-down (+ (:x button) 2) (+ (:y button) 2) :left)
      (should= false (test-utils/read-test-state :help-open))))

  (it "does not dismiss help or click the map when missing the button"
    (help/open-help!)
    (test-utils/set-test-state! :help-geometry (help/help-geometry 800 680))
    (test-utils/set-test-state! :last-clicked-cell nil)
    (dispatch/mouse-down 1 1 :left)
    (should= true (test-utils/read-test-state :help-open))
    (should-be-nil (test-utils/read-test-state :last-clicked-cell)))

  (it "ignores right-clicks on the dismiss button"
    (help/open-help!)
    (let [geom (help/help-geometry 800 680)
          button (:dismiss-button geom)]
      (test-utils/set-test-state! :help-geometry geom)
      (dispatch/mouse-down (+ (:x button) 2) (+ (:y button) 2) :right)
      (should= true (test-utils/read-test-state :help-open)))))

(describe "scrolling while help is open"
  (before
    (test-utils/reset-all-atoms!)
    (test-utils/set-test-state! :help-open true)
    (test-utils/set-test-state! :help-geometry (help/help-geometry 400 280)))

  (it "scrolls down with the down arrow"
    (dispatch/dispatch-key :down nil)
    (should= help/line-height (test-utils/read-test-state :help-scroll)))

  (it "scrolls with the mouse wheel"
    (dispatch/mouse-wheel 1)
    (should= help/line-height (test-utils/read-test-state :help-scroll)))

  (it "does not scroll the help list when help is closed"
    (test-utils/set-test-state! :help-open false)
    (dispatch/mouse-wheel 3)
    (should= 0 (test-utils/read-test-state :help-scroll))))
