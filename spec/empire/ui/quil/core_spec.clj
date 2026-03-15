(ns empire.ui.quil.core-spec
  (:require [empire.ui.quil.core :as quil-core]
            [empire.ui.quil.input :as quil-input]
            [empire.state.api :as sa]
            [empire.test.utils :refer [reset-all-atoms!]]
            [empire.ui.util.core :as util-core]
            [speclj.core :refer :all]))

(describe "-main"
  (before (reset-all-atoms!))

  (it "prints usage and returns before startup when help is requested"
    (with-redefs [util-core/help-requested? (constantly true)
                  util-core/usage-text (constantly "Usage text")
                  empire.ui.quil.core/screen-dimensions (fn [] (throw (ex-info "should not run" {})))]
      (should= "Usage text\n"
               (with-out-str
                 (quil-core/-main "--help"))))))

(describe "key-pressed"
  (before (reset-all-atoms!))

  (it "normalizes special keys and remembers them"
    (let [downs (atom [])]
      (with-redefs [quil.core/key-as-keyword (constantly :ignored)
                    quil.core/key-code (constantly java.awt.event.KeyEvent/VK_DELETE)
                    quil-input/key-down (fn [k] (swap! downs conj k))]
        (should= :state (quil-core/key-pressed :state nil))
        (should= [:delete] @downs)
        (should= :delete (sa/read-state :last-key)))))

  (it "ignores shift presses"
    (let [downs (atom [])]
      (with-redefs [quil.core/key-as-keyword (constantly :shift)
                    quil.core/key-code (constantly 0)
                    quil-input/key-down (fn [k] (swap! downs conj k))]
        (should= :state (quil-core/key-pressed :state nil))
        (should= [] @downs)
        (should-be-nil (sa/read-state :last-key)))))

  (it "does not repeat key-down while a key is already held"
    (let [downs (atom [])]
      (sa/write-state! :last-key :existing)
      (with-redefs [quil.core/key-as-keyword (constantly :a)
                    quil.core/key-code (constantly 0)
                    quil-input/key-down (fn [k] (swap! downs conj k))]
        (should= :state (quil-core/key-pressed :state nil))
        (should= [] @downs)
        (should= :a (sa/read-state :last-key))))))
