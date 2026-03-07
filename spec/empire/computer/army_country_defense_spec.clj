(ns empire.computer.army-country-defense-spec
  (:require [speclj.core :refer :all]
            [empire.computer.army :as army]
            [empire.test.utils :as test-utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-world! update-test-world!]]))

(describe "army country-defense behavior"
  (before (reset-all-atoms!))

  (it "moves toward detected intruder when on country-defense mission"
    (set-test-world! (build-test-map ["a#A"]))
    (set-test-computer-map! (build-test-map ["a#A"]))
    (update-test-world! assoc-in [0 0 :contents]
                        {:type :army :owner :computer :hits 1
                         :mode :sentry
                         :country-id 1
                         :threat-mission :country-defense
                         :threat-center [2 0]})
    (update-test-world! assoc-in [0 0 :country-id] 1)
    (update-test-world! assoc-in [1 0 :country-id] 1)
    (update-test-world! assoc-in [2 0 :country-id] 1)
    (army/process-army [0 0])
    (should-be-nil (get-in (test-utils/read-test-state :game-map) [0 0 :contents]))
    (should= :army (get-in (test-utils/read-test-state :game-map) [1 0 :contents :type]))))
