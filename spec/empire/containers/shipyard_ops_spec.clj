(ns empire.containers.shipyard-ops-spec
  (:require [empire.test.utils :as test-utils]
            [empire.config.core :as config]
            [empire.game-mechanics.containers.ops :refer :all]
            [empire.game-mechanics.containers.helpers :as uc]
            [empire.test.utils :refer [build-test-map get-test-unit reset-all-atoms! set-test-unit set-test-world! update-test-world!]]
            [speclj.core :refer :all]))
(describe "launch-ship-from-shipyard"
  (before (reset-all-atoms!))

  (it "launches ship at city coords when no launch-pos given"
    (set-test-world! (build-test-map ["-O-"]))
    (update-test-world! assoc-in [1 0 :shipyard]
           [{:type :destroyer :hits 3}])
    (launch-ship-from-shipyard [1 0] 0)
    (let [city (get-in (test-utils/read-test-state :game-map) [1 0])
          ship (:contents city)]
      (should= [] (:shipyard city))
      (should-not-be-nil ship)
      (should= :destroyer (:type ship))
      (should= :player (:owner ship))
      (should= 3 (:hits ship))
      (should= :awake (:mode ship))))

  (it "launches ship at separate launch-pos when provided"
    (set-test-world! (build-test-map ["-O~-"]))
    (update-test-world! assoc-in [1 0 :shipyard]
           [{:type :destroyer :hits 3}])
    (launch-ship-from-shipyard [1 0] 0 [2 0])
    (let [city (get-in (test-utils/read-test-state :game-map) [1 0])
          ship (:contents (get-in (test-utils/read-test-state :game-map) [2 0]))]
      (should= [] (:shipyard city))
      (should-be-nil (:contents city))
      (should-not-be-nil ship)
      (should= :destroyer (:type ship))
      (should= :player (:owner ship)))))
