(ns empire.application.state-access-spec
  (:require [speclj.core :refer :all]
            [empire.application.state-access :as sa]
            [empire.test.utils :as tu]))

(describe "state-access"
  (before (tu/reset-all-atoms!))

  (context "current-world"
    (it "returns the game map"
      (tu/set-test-world! (tu/build-test-map ["~"]))
      (should= (tu/read-test-world) (sa/current-world))))

  (context "read-state / write-state!"
    (it "reads and writes runtime state"
      (sa/write-state! :round-number 42)
      (should= 42 (sa/read-state :round-number))))

  (context "update-state!"
    (it "applies f to current value"
      (sa/write-state! :round-number 10)
      (sa/update-state! :round-number inc)
      (should= 11 (sa/read-state :round-number))))

  (context "update-world!"
    (it "applies f to the world and saves"
      (tu/set-test-world! (tu/build-test-map ["~"]))
      (sa/update-world! assoc-in [0 0 :test-key] :test-val)
      (should= :test-val (get-in (sa/current-world) [0 0 :test-key])))))
