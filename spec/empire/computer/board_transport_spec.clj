(ns empire.computer.board-transport-spec
  (:require [empire.computer.core :as core]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-player-map! set-test-world! update-test-world!]]
            [speclj.core :refer :all]))

(describe "board-transport"
  (before (reset-all-atoms!))

  (it "loads army onto adjacent transport"
    (set-test-world! (build-test-map ["at"]))
    (update-test-world! assoc-in [1 0 :contents :transport-mission] :loading)
    (update-test-world! assoc-in [1 0 :contents :army-count] 0)
    (core/board-transport [0 0] [1 0])
    (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))
    (should= 1 (:army-count (:contents (get-in (test-utils/read-test-state :game-map) [1 0])))))

  (it "throws when not adjacent"
    (set-test-world! (build-test-map ["a.t"]))
    (update-test-world! assoc-in [2 0 :contents :transport-mission] :loading)
    (update-test-world! assoc-in [2 0 :contents :army-count] 0)
    (should-throw (core/board-transport [0 0] [2 0])))

  (it "loads army at non-zero positions (kills - -> + in adjacent?)"
    (set-test-world! (build-test-map ["~~~~"
                                      "~~~~"
                                      "~~~~"
                                      "~~at"]))
    (update-test-world! assoc-in [3 3 :contents :transport-mission] :loading)
    (update-test-world! assoc-in [3 3 :contents :army-count] 0)
    (core/board-transport [2 3] [3 3])
    (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [2 3])))
    (should= 1 (:army-count (:contents (get-in (test-utils/read-test-state :game-map) [3 3])))))

  (it "increments from 0 when army-count is nil (kills fnil 0 -> 1)"
    (set-test-world! (build-test-map ["at"]))
    (update-test-world! assoc-in [1 0 :contents :transport-mission] :loading)
    (update-test-world! update-in [1 0 :contents] dissoc :army-count)
    (core/board-transport [0 0] [1 0])
    (should= 1 (:army-count (:contents (get-in (test-utils/read-test-state :game-map) [1 0])))))

  (it "loads army diagonally adjacent (kills <= -> < on dc in adjacent?)"
    (set-test-world! (build-test-map ["~~~~"
                                      "~~~~"
                                      "~~a~"
                                      "~~~t"]))
    (update-test-world! assoc-in [3 3 :contents :transport-mission] :loading)
    (update-test-world! assoc-in [3 3 :contents :army-count] 0)
    (core/board-transport [2 2] [3 3])
    (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [2 2])))
    (should= 1 (:army-count (:contents (get-in (test-utils/read-test-state :game-map) [3 3]))))))
