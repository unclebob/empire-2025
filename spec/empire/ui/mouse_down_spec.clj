(ns empire.ui.mouse-down-spec
  (:require [speclj.core :refer :all]
            [empire.ui.input :as input]
            [empire.atoms :as atoms]
            [empire.save-load :as save-load]
            [empire.test-utils :refer [build-test-map reset-all-atoms!]]))

(describe "mouse-down"
  (before (reset-all-atoms!)
          (reset! atoms/game-map (build-test-map ["O##"
                                                   "###"
                                                   "###"]))
          (reset! atoms/map-screen-dimensions [300 300]))

  (context "when load menu is open"
    (it "delegates to handle-load-menu-click on left click"
      (reset! atoms/load-menu-open true)
      (reset! atoms/load-menu-files ["save1.edn" "save2.edn"])
      (reset! atoms/load-menu-hovered 0)
      (let [loaded (atom nil)]
        (with-redefs [save-load/load-game! (fn [f] (reset! loaded f))]
          (input/mouse-down 50 50 :left))
        (should= "save1.edn" @loaded)))

    (it "ignores right click when load menu is open"
      (reset! atoms/load-menu-open true)
      (reset! atoms/load-menu-files ["save1.edn"])
      (reset! atoms/load-menu-hovered 0)
      (let [loaded (atom false)]
        (with-redefs [save-load/load-game! (fn [_] (reset! loaded true))]
          (input/mouse-down 50 50 :right))
        (should= false @loaded))))

  (context "normal map click"
    (it "sets last-clicked-cell on left click within map"
      (input/mouse-down 150 150 :left)
      (should= [1 1] @atoms/last-clicked-cell))

    (it "does not set last-clicked-cell on right click"
      (reset! atoms/last-clicked-cell nil)
      (input/mouse-down 150 150 :right)
      (should-be-nil @atoms/last-clicked-cell))

    (it "does not set last-clicked-cell when click is off map"
      (reset! atoms/last-clicked-cell nil)
      (input/mouse-down 500 500 :left)
      (should-be-nil @atoms/last-clicked-cell))))

(run-specs)
