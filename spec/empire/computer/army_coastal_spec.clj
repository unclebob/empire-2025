(ns empire.computer.army-coastal-spec
  (:require [speclj.core :refer :all]
            [empire.atoms :as atoms]
            [empire.computer.army.coastal :as coastal]
            [empire.test-utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-world! update-test-world!]]))

(describe "army coastal lake handling"
  (before (reset-all-atoms!))

  (it "does not sentry on a lake-only shore"
    (set-test-world! (build-test-map ["#####"
                                      "#####"
                                      "##~##"
                                      "#####"
                                      "#####"]))
    (set-test-computer-map! @atoms/game-map)
    (reset! atoms/lake-max-cells 20)
    (should-not (coastal/should-sentry-on-coast? [2 1] 1)))

  (it "finds a sea-shore target instead of a lake-shore target"
    (set-test-world! (build-test-map ["~~~~~~"
                                      "######"
                                      "######"
                                      "##~###"
                                      "######"]))
    (doseq [c (range 6)
            r (range 5)
            :when (= :land (get-in @atoms/game-map [c r :type]))]
      (update-test-world! assoc-in [c r :country-id] 1))
    (set-test-computer-map! @atoms/game-map)
    ;; Ocean is size 6; keep lake limit below that so only the inland sea is a lake.
    (reset! atoms/lake-max-cells 5)
    (let [target (coastal/find-coast-target-once [2 4] 1)]
      (should-not-be-nil target)
      ;; Row 1 is ocean coast; row 3 surrounds an inland lake.
      (should= 1 (second target))))

  (it "settles to sentry when it reaches its staged coast target"
    (set-test-world! (build-test-map ["~~~~~"
                                      "#####"
                                      "#####"
                                      "#####"
                                      "#####"]))
    (doseq [c (range 5)
            r (range 5)
            :when (= :land (get-in @atoms/game-map [c r :type]))]
      (update-test-world! assoc-in [c r :country-id] 1))
    (set-test-computer-map! @atoms/game-map)
    (reset! atoms/lake-max-cells 2)
    (update-test-world! assoc-in [2 3 :contents]
                       {:type :army :owner :computer :hits 1
                        :mode :move-to-coast-for-invasion
                        :country-id 1
                        :coast-target [2 3]})
    (coastal/process-move-to-coast-for-invasion [2 3] 1)
    (should= :sentry (get-in @atoms/game-map [2 3 :contents :mode])))

  (it "lake-retask army goes sentry when blocked and cannot progress"
    (set-test-world! (build-test-map ["~~~~~"
                                      "#####"
                                      "#####"]))
    (doseq [c (range 5)
            r (range 3)
            :when (= :land (get-in @atoms/game-map [c r :type]))]
      (update-test-world! assoc-in [c r :country-id] 1))
    (set-test-computer-map! @atoms/game-map)
    ;; Army at [2 2], target [2 1]. Block all empty passable neighbors.
    (update-test-world! assoc-in [2 2 :contents]
                       {:type :army :owner :computer :hits 1
                        :mode :move-to-coast-for-invasion
                        :country-id 1
                        :coast-target [2 1]
                        :lake-retask? true})
    (doseq [p [[1 1] [2 1] [3 1] [1 2] [3 2]]]
      (update-test-world! assoc-in (conj p :contents)
                         {:type :army :owner :computer :hits 1 :mode :sentry :country-id 1}))
    (coastal/process-move-to-coast-for-invasion [2 2] 1)
    (should= :sentry (get-in @atoms/game-map [2 2 :contents :mode]))))
