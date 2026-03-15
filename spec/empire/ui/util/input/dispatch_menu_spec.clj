(ns empire.ui.util.input.dispatch-menu-spec
  (:require [clojure.string :as string]
            [empire.game.save-load :as save-load]
            [empire.test.utils :as test-utils]
            [empire.ui.util.input.dispatch :as dispatch]
            [speclj.core :refer :all]))

(describe "save/load key handling"
  (around [it]
    (test-utils/reset-all-atoms!)
    (test-utils/set-test-state! :map-screen-dimensions [300 300])
    (it))

  (it "! key opens save menu"
    (dispatch/dispatch-key (keyword "!") nil)
    (should= true (test-utils/read-test-state :save-menu-open)))

  (it "! key saves immediately when dialog is unavailable"
    (test-utils/set-test-state! :map-screen-dimensions [0 0])
    (with-redefs [save-load/save-game! (fn [_] "auto-save.edn")]
      (dispatch/dispatch-key (keyword "!") nil)
      (should (string/includes? (test-utils/read-test-state :turn-message) "auto-save.edn"))))

  (it "^ key opens load menu"
    (dispatch/dispatch-key (keyword "^") nil)
    (should= true (test-utils/read-test-state :load-menu-open))))

(describe "save menu key handling"
  (around [it]
    (test-utils/reset-all-atoms!)
    (test-utils/set-test-state! :save-menu-open true)
    (test-utils/set-test-state! :save-menu-input "save-2026-03-07-000000")
    (test-utils/set-test-state! :save-menu-default-active false)
    (it))

  (it "appends valid filename characters"
    (dispatch/dispatch-key :a nil)
    (should= "save-2026-03-07-000000a" (test-utils/read-test-state :save-menu-input)))

  (it "ignores invalid filename characters once default is not active"
    (dispatch/dispatch-key :space nil)
    (should= "save-2026-03-07-000000" (test-utils/read-test-state :save-menu-input)))

  (it "backspace removes one character"
    (dispatch/dispatch-key :backspace nil)
    (should= "save-2026-03-07-00000" (test-utils/read-test-state :save-menu-input)))

  (it "delete removes one character from the end"
    (dispatch/dispatch-key :delete nil)
    (should= "save-2026-03-07-00000" (test-utils/read-test-state :save-menu-input)))

  (it "del removes one character from the end"
    (dispatch/dispatch-key :del nil)
    (should= "save-2026-03-07-00000" (test-utils/read-test-state :save-menu-input)))

  (it "forward-delete removes one character from the end"
    (dispatch/dispatch-key :forward-delete nil)
    (should= "save-2026-03-07-00000" (test-utils/read-test-state :save-menu-input)))

  (it "escape closes save menu"
    (dispatch/dispatch-key :escape nil)
    (should= false (test-utils/read-test-state :save-menu-open)))

  (it "enter saves and shows confirmation"
    (with-redefs [save-load/save-from-menu! (fn []
                                              (save-load/close-save-menu!)
                                              "named-save.edn")]
      (dispatch/dispatch-key :enter nil)
      (should= false (test-utils/read-test-state :save-menu-open))
      (should (string/includes? (test-utils/read-test-state :turn-message) "named-save.edn"))))

  (it "newline saves and shows confirmation"
    (with-redefs [save-load/save-from-menu! (fn []
                                              (save-load/close-save-menu!)
                                              "named-save.edn")]
      (dispatch/dispatch-key :newline nil)
      (should= false (test-utils/read-test-state :save-menu-open))
      (should (string/includes? (test-utils/read-test-state :turn-message) "named-save.edn")))))

(describe "save menu default input behavior"
  (around [it]
    (test-utils/reset-all-atoms!)
    (test-utils/set-test-state! :save-menu-open true)
    (test-utils/set-test-state! :save-menu-input "save-2026-03-07-000000")
    (test-utils/set-test-state! :save-menu-default-active true)
    (it))

  (it "enter accepts the default value"
    (let [saved-input (atom nil)]
      (with-redefs [save-load/save-from-menu! (fn []
                                                (reset! saved-input (test-utils/read-test-state :save-menu-input))
                                                (save-load/close-save-menu!)
                                                "save-2026-03-07-000000.edn")]
        (dispatch/dispatch-key :enter nil)
        (should= "save-2026-03-07-000000" @saved-input))))

  (it "typing any other key clears default before applying input"
    (dispatch/dispatch-key :a nil)
    (should= false (test-utils/read-test-state :save-menu-default-active))
    (should= "a" (test-utils/read-test-state :save-menu-input)))

  (it "non-text key clears default"
    (dispatch/dispatch-key :space nil)
    (should= false (test-utils/read-test-state :save-menu-default-active))
    (should= "" (test-utils/read-test-state :save-menu-input))))

(describe "Escape key with load menu"
  (around [it]
    (test-utils/reset-all-atoms!)
    (it))

  (it "closes load menu when open"
    (test-utils/set-test-state! :load-menu-open true)
    (test-utils/set-test-state! :load-menu-files ["file.edn"])
    (dispatch/dispatch-key :escape nil)
    (should= false (test-utils/read-test-state :load-menu-open))
    (should= [] (test-utils/read-test-state :load-menu-files)))

  (it "does nothing when load menu is closed"
    (dispatch/dispatch-key :escape nil)
    (should= false (test-utils/read-test-state :load-menu-open))))

(describe "key blocking while load menu open"
  (around [it]
    (test-utils/reset-all-atoms!)
    (it))

  (it "ignores non-escape keys when menu is open"
    (test-utils/set-test-state! :load-menu-open true)
    (test-utils/set-test-state! :pause-requested false)
    (dispatch/dispatch-key :P nil)
    (should= false (test-utils/read-test-state :pause-requested)))

  (it "processes normal keys when menu is closed"
    (test-utils/set-test-state! :load-menu-open false)
    (test-utils/set-test-state! :paused false)
    (dispatch/dispatch-key :P nil)
    (should= true (test-utils/read-test-state :pause-requested))))

(describe "load menu click handling"
  (around [it]
    (test-utils/reset-all-atoms!)
    (it))

  (it "loads selected file when clicking on menu item"
    (let [loaded (atom nil)]
      (test-utils/set-test-state! :load-menu-open true)
      (test-utils/set-test-state! :load-menu-files ["file1.edn" "file2.edn"])
      (test-utils/set-test-state! :load-menu-hovered 1)
      (with-redefs [save-load/load-game! (fn [f] (reset! loaded f))]
        (dispatch/handle-load-menu-click)
        (should= "file2.edn" @loaded))))

  (it "does nothing when no file is hovered"
    (let [loaded (atom nil)]
      (test-utils/set-test-state! :load-menu-open true)
      (test-utils/set-test-state! :load-menu-files ["file1.edn"])
      (test-utils/set-test-state! :load-menu-hovered nil)
      (with-redefs [save-load/load-game! (fn [f] (reset! loaded f))]
        (dispatch/handle-load-menu-click)
        (should-be-nil @loaded)))))
