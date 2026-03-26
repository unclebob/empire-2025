(ns empire.computer.ship.patrol-repulsion-spec
  (:require [empire.computer.ship.patrol :as patrol]
            [empire.state.api :as sa]
            [empire.test.utils :refer [reset-all-atoms! build-test-map set-test-computer-map!]]
            [empire.test.utils :as test-utils]
            [speclj.core :refer :all]))

(describe "nearby-patrol-boat-count"
  (before (reset-all-atoms!))

  (it "returns 0 when no other patrol boats nearby"
    (set-test-computer-map! (build-test-map ["~p~" "~~~" "~~~"]))
    (should= 0 (patrol/nearby-patrol-boat-count [1 0] [1 0] 3)))

  (it "counts patrol boats within radius excluding self"
    (set-test-computer-map! (build-test-map ["pp~" "~~~" "~~~"]))
    (should= 1 (patrol/nearby-patrol-boat-count [0 0] [0 0] 3)))

  (it "excludes patrol boats beyond radius"
    (set-test-computer-map! (build-test-map ["p~~~~p" "~~~~~~"]))
    (should= 0 (patrol/nearby-patrol-boat-count [0 0] [0 0] 3))))

(describe "prefer-dispersed"
  (before (reset-all-atoms!))

  (it "prefers candidate farther from other patrol boats"
    (set-test-computer-map! (build-test-map ["~p~~~~~" "~~~~~~~"]))
    ;; Patrol boat at [1,0]. Self at [3,0].
    ;; Candidate [2,0]: 1 nearby boat (at [1,0])
    ;; Candidate [6,0]: 0 nearby boats (beyond radius 3)
    (should= [6 0] (patrol/prefer-dispersed [3 0] [[2 0] [6 0]] [3 0]))))
