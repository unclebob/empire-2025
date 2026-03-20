(ns empire.computer-threat-assessment-spec
  "Tests for computer AI modules - post CommandingGeneral refactor.
   Decision logic has been gutted; these tests cover preserved utilities."
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.game.loop.core :as game-loop]
            [empire.computer.coordinator :as computer]
            [empire.computer.army :as army]
            [empire.computer.fighter :as fighter]
            [empire.computer.production :as computer-production]
            [empire.computer.ship :as ship]
            [empire.computer.shared.threat :as threat]
            [empire.computer.transport :as transport]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-world! update-test-world! set-test-player-map! set-test-computer-map!]]))
(describe "Threat Assessment"
  (before (reset-all-atoms!))

  (context "threat/unit-threat"
    (it "returns correct threat values for unit types"
      (should= 10 (threat/unit-threat :battleship))
      (should= 8 (threat/unit-threat :carrier))
      (should= 6 (threat/unit-threat :destroyer))
      (should= 5 (threat/unit-threat :submarine))
      (should= 4 (threat/unit-threat :fighter))
      (should= 3 (threat/unit-threat :patrol-boat))
      (should= 2 (threat/unit-threat :army))
      (should= 1 (threat/unit-threat :transport))
      (should= 0 (threat/unit-threat :satellite))))

  (context "threat/threat-level"
    (it "returns 0 with no enemies nearby"
      (set-test-computer-map! (build-test-map ["~~~"
                                                   "~d~"
                                                   "~~~"]))
      (should= 0 (threat/threat-level (test-utils/read-test-state :computer-map) [1 1])))

    (it "sums threat of adjacent enemies"
      (set-test-computer-map! (build-test-map ["~B~"
                                                   "~d~"
                                                   "~D~"]))
      ;; Battleship = 10, Destroyer = 6
      (should= 16 (threat/threat-level (test-utils/read-test-state :computer-map) [1 1])))

    (it "ignores friendly units"
      (set-test-computer-map! (build-test-map ["~b~"
                                                   "~d~"
                                                   "~b~"]))
      (should= 0 (threat/threat-level (test-utils/read-test-state :computer-map) [1 1]))))

  (context "threat/safe-moves"
    (it "returns all moves unchanged when unit at full health"
      (set-test-computer-map! (build-test-map ["~B~"
                                                   "~d~"
                                                   "~~~"]))
      (let [unit {:type :destroyer :hits 3}
            moves [[1 0] [0 1] [2 1] [1 2]]]
        (should= moves (threat/safe-moves (test-utils/read-test-state :computer-map) [1 1] unit moves)))))

  (context "threat/should-retreat?"
    (it "returns true when damaged and under threat"
      (set-test-computer-map! (build-test-map ["~B~"
                                                   "~d~"
                                                   "~~~"]))
      (let [unit {:type :destroyer :hits 2}]
        (should (threat/should-retreat? [1 1] unit (test-utils/read-test-state :computer-map)))))

    (it "returns false for healthy unit under threat"
      (set-test-computer-map! (build-test-map ["~B~"
                                                   "~d~"
                                                   "~~~"]))
      (let [unit {:type :destroyer :hits 3}]
        (should-not (threat/should-retreat? [1 1] unit (test-utils/read-test-state :computer-map))))))

  (context "threat/retreat-move"
    (it "moves toward nearest friendly city"
      (set-test-world! (build-test-map ["X~~~B"
                                               "~~~~~"
                                               "~~d~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [unit {:type :destroyer :hits 1}
            passable [[2 1] [3 1] [1 2] [3 2]]]
        (let [retreat (threat/retreat-move [2 2] unit (test-utils/read-test-state :computer-map) passable)]
          (should-not-be-nil retreat)
          (should (#{[2 1] [1 2] [3 1]} retreat)))))

    (it "returns nil when no friendly city exists"
      (set-test-world! (build-test-map ["~~~~B"
                                               "~~~~~"
                                               "~~d~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [unit {:type :destroyer :hits 1}
            passable [[2 1] [3 1] [1 2] [3 2]]]
        (should-be-nil (threat/retreat-move [2 2] unit (test-utils/read-test-state :computer-map) passable))))))
