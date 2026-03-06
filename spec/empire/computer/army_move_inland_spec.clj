(ns empire.computer.army-move-inland-spec
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.computer.army :as army]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-player-map! set-test-computer-map! set-test-world! update-test-world!]]))

(describe "process-move-inland"
  (before (reset-all-atoms!))

  (context "when not adjacent to sea"
    (it "switches to :random-explore mode"
      (set-test-world! (build-test-map ["###"
                                        "#a#"
                                        "###"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [1 1 :contents :mode] :move-inland)
      (with-redefs [rand-nth (constantly [1 0])]
        (army/process-army [1 1]))
      (should= :random-explore (get-in (test-utils/read-test-state :game-map) [1 1 :contents :mode])))

    (it "sets a random-explore-direction"
      (set-test-world! (build-test-map ["###"
                                        "#a#"
                                        "###"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [1 1 :contents :mode] :move-inland)
      (with-redefs [rand-nth (constantly [-1 1])]
        (army/process-army [1 1]))
      (should= [-1 1] (get-in (test-utils/read-test-state :game-map) [1 1 :contents :random-explore-direction]))))

  (context "when adjacent to sea with valid inland neighbor"
    (it "moves army to an empty inland cell"
      ;; col:  0     1     2     3     4
      ;; y=0:  sea   land  land  land  land
      ;; y=1:  sea   ARMY  land  land  land
      ;; y=2:  sea   land  land  land  land
      ;; [1,1] is adjacent to sea column 0. Inland cells [2,0],[2,1],[2,2] are valid.
      (set-test-world! (build-test-map ["~####"
                                        "~a###"
                                        "~####"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [1 1 :contents :mode] :move-inland)
      (with-redefs [rand-nth (fn [v] (first v))]
        (army/process-army [1 1]))
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [1 1 :contents]))
      (should= :army (get-in (test-utils/read-test-state :game-map) [2 0 :contents :type]))))

  (context "when adjacent to sea with no valid inland neighbor"
    (it "stays put when all land neighbors are also adjacent to sea"
      ;; col:  0     1     2
      ;; y=0:  sea   land  sea
      ;; y=1:  sea   ARMY  sea
      ;; y=2:  sea   land  sea
      ;; All land neighbors [1,0] and [1,2] are adjacent to sea.
      (set-test-world! (build-test-map ["~~~"
                                        "#a#"
                                        "~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [1 1 :contents :mode] :move-inland)
      (army/process-army [1 1])
      (should= :army (get-in (test-utils/read-test-state :game-map) [1 1 :contents :type]))
      (should= :move-inland (get-in (test-utils/read-test-state :game-map) [1 1 :contents :mode])))

    (it "stays put when all inland neighbors are occupied"
      ;; col:  0     1     2     3
      ;; y=0:  sea   army  army  army
      ;; y=1:  sea   ARMY  army  army
      ;; y=2:  sea   army  army  army
      (set-test-world! (build-test-map ["~aaa"
                                        "~aaa"
                                        "~aaa"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [1 1 :contents :mode] :move-inland)
      (army/process-army [1 1])
      (should= :army (get-in (test-utils/read-test-state :game-map) [1 1 :contents :type]))
      (should= :move-inland (get-in (test-utils/read-test-state :game-map) [1 1 :contents :mode])))))

(run-specs)
