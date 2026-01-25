(ns empire.ui.debug-drag-spec
  (:require [speclj.core :refer :all]
            [empire.ui.input :as input]
            [empire.atoms :as atoms]
            [empire.debug :as debug]
            [empire.test-utils :refer [build-test-map reset-all-atoms!]]))

(describe "debug-drag-start!"
  (before (reset-all-atoms!))

  (it "sets debug-drag-start to screen coords when called"
    (input/debug-drag-start! 100 200)
    (should= [100 200] @atoms/debug-drag-start))

  (it "sets debug-drag-current to same coords as start"
    (input/debug-drag-start! 100 200)
    (should= [100 200] @atoms/debug-drag-current))

  (it "overwrites previous drag start if called again"
    (input/debug-drag-start! 100 200)
    (input/debug-drag-start! 300 400)
    (should= [300 400] @atoms/debug-drag-start)))

(describe "debug-drag-update!"
  (before (reset-all-atoms!))

  (it "updates debug-drag-current when drag is active"
    (input/debug-drag-start! 100 200)
    (input/debug-drag-update! 150 250)
    (should= [150 250] @atoms/debug-drag-current))

  (it "does nothing when no drag is active"
    (input/debug-drag-update! 150 250)
    (should-be-nil @atoms/debug-drag-current)))

(describe "debug-drag-end!"
  (before
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["~~~~"
                                             "~~~~"
                                             "~~~~"
                                             "~~~~"]))
    (reset! atoms/map-screen-dimensions [400 400]))

  (it "resets drag atoms when called"
    (input/debug-drag-start! 100 100)
    (input/debug-drag-update! 200 200)
    (input/debug-drag-end! 200 200)
    (should-be-nil @atoms/debug-drag-start)
    (should-be-nil @atoms/debug-drag-current))

  (it "does nothing when no drag is active"
    (input/debug-drag-end! 200 200)
    (should-be-nil @atoms/debug-drag-start))

  (it "calls write-dump! with cell range when drag ends"
    (let [dump-called (atom nil)]
      (with-redefs [debug/write-dump! (fn [start end]
                                         (reset! dump-called [start end])
                                         "test-file.txt")]
        (input/debug-drag-start! 0 0)
        (input/debug-drag-end! 200 200)
        (should-not-be-nil @dump-called)))))

(describe "modifier-held?"
  (it "returns true when :shift is in modifiers set"
    (should (input/modifier-held? #{:shift})))

  (it "returns true when :ctrl is in modifiers set"
    (should (input/modifier-held? #{:ctrl})))

  (it "returns true when :control is in modifiers set"
    (should (input/modifier-held? #{:control})))

  (it "returns true when both shift and ctrl are in modifiers"
    (should (input/modifier-held? #{:shift :ctrl})))

  (it "returns false when only :alt is in modifiers"
    (should-not (input/modifier-held? #{:alt})))

  (it "returns false for empty set"
    (should-not (input/modifier-held? #{})))

  (it "returns false for nil"
    (should-not (input/modifier-held? nil))))
