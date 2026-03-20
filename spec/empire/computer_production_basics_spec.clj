(ns empire.computer-production-basics-spec
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
(describe "Computer Production"
  (before (reset-all-atoms!))

  (context "computer-production/city-is-coastal?"
    (it "returns true when city has adjacent sea"
      (set-test-world! (build-test-map ["~X#"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (should (computer-production/city-is-coastal? [1 0])))

    (it "returns false when city has no adjacent sea"
      (set-test-world! (build-test-map ["#X#"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (should-not (computer-production/city-is-coastal? [1 0]))))

  (context "computer-production/count-computer-units"
    (it "counts computer units by type"
      (set-test-world! (build-test-map ["aad"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [counts (computer-production/count-computer-units)]
        (should= 2 (get counts :army))
        (should= 1 (get counts :destroyer))))

    (it "ignores player units"
      (set-test-world! (build-test-map ["aAD"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [counts (computer-production/count-computer-units)]
        (should= 1 (get counts :army))
        (should-be-nil (get counts :destroyer))))))
