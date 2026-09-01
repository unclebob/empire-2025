(ns empire.notifications-spec
  (:require [empire.notifications :as sut]
            [empire.test.utils :refer [reset-all-atoms!]]
            [empire.test.utils :as test-utils]
            [speclj.core :refer :all]))

(describe "notifications port"
  (before (reset-all-atoms!))

  (it "uses a noop port by default"
    (should-be-nil (sut/play-alert! (sut/alert-port))))

  (it "stores and returns the active port"
    (let [port (reify sut/AlertPort
                 (play-alert! [_] :played))]
      (sut/set-alert-port! port)
      (should= port (sut/alert-port))))

  (it "alert! delegates to the active port"
    (let [calls (atom 0)
          port (reify sut/AlertPort
                 (play-alert! [_] (swap! calls inc)))]
      (sut/set-alert-port! port)
      (sut/alert!)
      (should= 1 @calls)))

  (it "warn! writes the warning message and alerts"
    (let [calls (atom 0)
          port (reify sut/AlertPort
                 (play-alert! [_] (swap! calls inc)))]
      (sut/set-alert-port! port)
      (sut/warn! "City lost.")
      (should= "City lost." (test-utils/read-test-state :warning-message))
      (should= 1 @calls)))

  (it "reset-alert-port! restores the noop port"
    (let [port (reify sut/AlertPort
                 (play-alert! [_] :played))]
      (sut/set-alert-port! port)
      (sut/reset-alert-port!)
      (should-be-nil (sut/play-alert! (sut/alert-port))))))
