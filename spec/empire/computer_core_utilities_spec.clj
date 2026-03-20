(ns empire.computer-core-utilities-spec
  "Tests for computer AI modules - post CommandingGeneral refactor.
   Decision logic has been gutted; these tests cover preserved utilities."
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.computer.shared.action-resolution :as action-resolution]
            [empire.computer.shared.grid :as grid]
            [empire.computer.shared.world-query :as world-query]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-world! update-test-world! set-test-player-map! set-test-computer-map!]]))
(describe "Computer Core Utilities"
  (before (reset-all-atoms!))

  (context "world-query/get-neighbors"
    (it "returns neighbors for center position"
      (let [world (build-test-map ["###"
                                   "###"
                                   "###"])]
        (set-test-world! world)
        (set-test-computer-map! world))
      (let [neighbors (world-query/get-neighbors [1 1])]
        (should= 8 (count neighbors))))

    (it "returns fewer neighbors for corner position"
      (let [world (build-test-map ["###"
                                   "###"
                                   "###"])]
        (set-test-world! world)
        (set-test-computer-map! world))
      (let [neighbors (world-query/get-neighbors [0 0])]
        (should= 3 (count neighbors)))))

  (context "grid/distance"
    (it "calculates manhattan distance"
      (should= 0 (grid/distance [0 0] [0 0]))
      (should= 1 (grid/distance [0 0] [0 1]))
      (should= 2 (grid/distance [0 0] [1 1]))
      (should= 5 (grid/distance [0 0] [2 3]))))

  (context "world-query/attackable-target?"
    (it "returns true for player unit"
      (should (world-query/attackable-target? {:contents {:owner :player}})))

    (it "returns true for free city"
      (should (world-query/attackable-target? {:type :city :city-status :free})))

    (it "returns true for player city"
      (should (world-query/attackable-target? {:type :city :city-status :player})))

    (it "returns false for computer city"
      (should-not (world-query/attackable-target? {:type :city :city-status :computer})))

    (it "returns false for empty cell"
      (should-not (world-query/attackable-target? {:type :land}))))

  (context "world-query/find-visible-cities"
    (it "finds cities matching status predicate"
      (set-test-computer-map! (build-test-map ["X+O"]))
      (should= [[0 0]] (world-query/find-visible-cities #{:computer}))
      (should= [[1 0]] (world-query/find-visible-cities #{:free}))
      (should= [[2 0]] (world-query/find-visible-cities #{:player}))))

  (context "grid/move-toward"
    (it "returns neighbor closest to target"
      (let [passable [[0 1] [1 0] [1 1]]]
        (should= [0 1] (grid/move-toward [0 0] [0 5] passable))))

    (it "returns nil for empty passable list"
      (should-be-nil (grid/move-toward [0 0] [5 5] []))))

  (context "world-query/adjacent-to-computer-unexplored?"
    (it "returns true when adjacent to nil cell"
      (set-test-computer-map! [[{:type :land} nil]
                                   [{:type :land} {:type :land}]])
      (should (world-query/adjacent-to-computer-unexplored? [0 0])))

    (it "returns false when all neighbors explored"
      (set-test-computer-map! [[{:type :land} {:type :land}]
                                   [{:type :land} {:type :land}]])
      (should-not (world-query/adjacent-to-computer-unexplored? [0 0]))))

  (context "action-resolution/move-unit-to"
    (it "moves unit from one position to another"
      (set-test-world! (build-test-map ["a#"]))
      (action-resolution/move-unit-to [0 0] [1 0])
      (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))
      (should= :army (:type (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))))))

  (context "world-query/find-visible-player-units"
    (it "finds player units on computer-map"
      (set-test-computer-map! (build-test-map ["aA#"]))
      (should= [[1 0]] (world-query/find-visible-player-units))))

  (context "action-resolution/board-transport"
    (it "loads army onto adjacent transport"
      (set-test-world! (build-test-map ["at"]))
      (action-resolution/board-transport [0 0] [1 0])
      (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))
      (should= 1 (:army-count (:contents (get-in (test-utils/read-test-state :game-map) [1 0])))))

    (it "throws when positions are not adjacent"
      (set-test-world! (build-test-map ["a#t"]))
      (should-throw (action-resolution/board-transport [0 0] [2 0])))))
