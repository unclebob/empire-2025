(ns empire.ui.util.input.mouse-down-spec
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.game.save-load :as save-load]
            [empire.ui.util.input.dispatch :as dispatch]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-world!]]))

(describe "mouse-down"
  (before (reset-all-atoms!)
          (set-test-world! (build-test-map ["O##"
                                            "###"
                                            "###"]))
          (test-utils/set-test-state! :map-screen-dimensions [300 300]))

  (context "when load menu is open"
    (it "delegates to handle-load-menu-click on left click"
      (test-utils/set-test-state! :load-menu-open true)
      (test-utils/set-test-state! :load-menu-files ["save1.edn" "save2.edn"])
      (test-utils/set-test-state! :load-menu-hovered 0)
      (let [loaded (atom nil)]
        (with-redefs [save-load/load-game! (fn [f] (reset! loaded f))]
          (dispatch/mouse-down 50 50 :left))
        (should= "save1.edn" @loaded)))

    (it "ignores right click when load menu is open"
      (test-utils/set-test-state! :load-menu-open true)
      (test-utils/set-test-state! :load-menu-files ["save1.edn"])
      (test-utils/set-test-state! :load-menu-hovered 0)
      (let [loaded (atom false)]
        (with-redefs [save-load/load-game! (fn [_] (reset! loaded true))]
          (dispatch/mouse-down 50 50 :right))
        (should= false @loaded))))

  (context "when save menu is open"
    (it "ignores left click while save menu is open"
      (test-utils/set-test-state! :save-menu-open true)
      (test-utils/set-test-state! :last-clicked-cell nil)
      (dispatch/mouse-down 150 150 :left)
      (should-be-nil (test-utils/read-test-state :last-clicked-cell))))

  (context "normal map click"
    (it "sets last-clicked-cell on left click within map"
      (dispatch/mouse-down 150 150 :left)
      (should= [1 1] (test-utils/read-test-state :last-clicked-cell)))

    (it "does not set last-clicked-cell on right click"
      (test-utils/set-test-state! :last-clicked-cell nil)
      (dispatch/mouse-down 150 150 :right)
      (should-be-nil (test-utils/read-test-state :last-clicked-cell)))

    (it "does not set last-clicked-cell when click is off map"
      (test-utils/set-test-state! :last-clicked-cell nil)
      (dispatch/mouse-down 500 500 :left)
      (should-be-nil (test-utils/read-test-state :last-clicked-cell)))))

(run-specs)
