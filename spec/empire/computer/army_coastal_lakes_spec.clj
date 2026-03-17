(ns empire.computer.army-coastal-lakes-spec
  (:require [empire.computer.army.coastal :as coastal]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-world! update-test-world!]]
            [speclj.core :refer :all]))

(describe "army coastal lake handling"
  (before (reset-all-atoms!))

  (it "does not sentry on a lake-only shore"
    (set-test-world! (build-test-map ["#####"
                                      "#####"
                                      "##~##"
                                      "#####"
                                      "#####"]))
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (test-utils/set-test-state! :lake-max-cells 20)
    (should-not (coastal/should-sentry-on-coast? [2 1] 1)))

  (it "finds a sea-shore target instead of a lake-shore target"
    (set-test-world! (build-test-map ["~~~~~~"
                                      "######"
                                      "######"
                                      "##~###"
                                      "######"]))
    (doseq [c (range 6)
            r (range 5)
            :when (= :land (get-in (test-utils/read-test-state :game-map) [c r :type]))]
      (update-test-world! assoc-in [c r :country-id] 1))
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (test-utils/set-test-state! :lake-max-cells 5)
    (let [target (coastal/find-coast-target-once [2 4] 1)]
      (should-not-be-nil target)
      (should= 1 (second target)))))
