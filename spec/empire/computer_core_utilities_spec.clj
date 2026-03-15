(ns empire.computer-core-utilities-spec
  "Tests for computer AI modules - post CommandingGeneral refactor.
   Decision logic has been gutted; these tests cover preserved utilities."
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.game.loop.core :as game-loop]
            [empire.computer.coordinator :as computer]
            [empire.computer.army :as army]
            [empire.computer.core :as computer-core]
            [empire.computer.fighter :as fighter]
            [empire.computer.production :as computer-production]
            [empire.computer.ship :as ship]
            [empire.computer.threat :as threat]
            [empire.computer.transport :as transport]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-world! update-test-world! set-test-player-map! set-test-computer-map!]]))
(describe "Computer Core Utilities"
  (before (reset-all-atoms!))

  (context "computer-core/get-neighbors"
    (it "returns neighbors for center position"
      (set-test-world! (build-test-map ["###"
                                               "###"
                                               "###"]))
      (let [neighbors (computer-core/get-neighbors [1 1])]
        (should= 8 (count neighbors))))

    (it "returns fewer neighbors for corner position"
      (set-test-world! (build-test-map ["###"
                                               "###"
                                               "###"]))
      (let [neighbors (computer-core/get-neighbors [0 0])]
        (should= 3 (count neighbors)))))

  (context "computer-core/distance"
    (it "calculates manhattan distance"
      (should= 0 (computer-core/distance [0 0] [0 0]))
      (should= 1 (computer-core/distance [0 0] [0 1]))
      (should= 2 (computer-core/distance [0 0] [1 1]))
      (should= 5 (computer-core/distance [0 0] [2 3]))))

  (context "computer-core/attackable-target?"
    (it "returns true for player unit"
      (should (computer-core/attackable-target? {:contents {:owner :player}})))

    (it "returns true for free city"
      (should (computer-core/attackable-target? {:type :city :city-status :free})))

    (it "returns true for player city"
      (should (computer-core/attackable-target? {:type :city :city-status :player})))

    (it "returns false for computer city"
      (should-not (computer-core/attackable-target? {:type :city :city-status :computer})))

    (it "returns false for empty cell"
      (should-not (computer-core/attackable-target? {:type :land}))))

  (context "computer-core/find-visible-cities"
    (it "finds cities matching status predicate"
      (set-test-computer-map! (build-test-map ["X+O"]))
      (should= [[0 0]] (computer-core/find-visible-cities #{:computer}))
      (should= [[1 0]] (computer-core/find-visible-cities #{:free}))
      (should= [[2 0]] (computer-core/find-visible-cities #{:player}))))

  (context "computer-core/move-toward"
    (it "returns neighbor closest to target"
      (let [passable [[0 1] [1 0] [1 1]]]
        (should= [0 1] (computer-core/move-toward [0 0] [0 5] passable))))

    (it "returns nil for empty passable list"
      (should-be-nil (computer-core/move-toward [0 0] [5 5] []))))

  (context "computer-core/adjacent-to-computer-unexplored?"
    (it "returns true when adjacent to nil cell"
      (set-test-computer-map! [[{:type :land} nil]
                                   [{:type :land} {:type :land}]])
      (should (computer-core/adjacent-to-computer-unexplored? [0 0])))

    (it "returns false when all neighbors explored"
      (set-test-computer-map! [[{:type :land} {:type :land}]
                                   [{:type :land} {:type :land}]])
      (should-not (computer-core/adjacent-to-computer-unexplored? [0 0]))))

  (context "computer-core/move-unit-to"
    (it "moves unit from one position to another"
      (set-test-world! (build-test-map ["a#"]))
      (computer-core/move-unit-to [0 0] [1 0])
      (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))
      (should= :army (:type (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))))))

  (context "computer-core/find-visible-player-units"
    (it "finds player units on computer-map"
      (set-test-computer-map! (build-test-map ["aA#"]))
      (should= [[1 0]] (computer-core/find-visible-player-units))))

  (context "computer-core/board-transport"
    (it "loads army onto adjacent transport"
      (set-test-world! (build-test-map ["at"]))
      (computer-core/board-transport [0 0] [1 0])
      (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))
      (should= 1 (:army-count (:contents (get-in (test-utils/read-test-state :game-map) [1 0])))))

    (it "throws when positions are not adjacent"
      (set-test-world! (build-test-map ["a#t"]))
      (should-throw (computer-core/board-transport [0 0] [2 0])))))
