(ns empire.computer.army.assignment-staging-spec
  (:require [empire.computer.army.assignment :as assignment]
            [empire.state.api :as sa]
            [empire.test.utils :refer [reset-all-atoms! build-test-map set-test-world! set-test-computer-map! update-test-world!]]
            [empire.test.utils :as test-utils]
            [speclj.core :refer :all]))

(describe "assign-load-target-staging-armies!"
  (before (reset-all-atoms!))

  (it "returns empty when no armies nearby"
    (set-test-world! (build-test-map ["~#~"
                                      "~~~"]))
    (update-test-world! assoc-in [1 0 :country-id] 1)
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (let [result (#'assignment/assign-load-target-staging-armies! 1 [1 0])]
      (should= [] result)))

  (it "assigns nearby armies to staging mode"
    (set-test-world! (build-test-map ["~#a"
                                      "~~~"]))
    (update-test-world! assoc-in [1 0 :country-id] 1)
    (update-test-world! assoc-in [2 0 :contents :country-id] 1)
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (let [result (#'assignment/assign-load-target-staging-armies! 1 [1 0])]
      (should (vector? result)))))
