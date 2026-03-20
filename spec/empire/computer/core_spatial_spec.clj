(ns empire.computer.core-spatial-spec
  (:require [empire.computer.shared.action-resolution :as action-resolution]
            [empire.computer.shared.grid :as grid]
            [empire.computer.shared.world-query :as world-query]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-world!]]
            [speclj.core :refer :all]))

(describe "distance"
  (it "returns 0 for same position"
    (should= 0 (grid/distance [3 3] [3 3])))

  (it "returns Manhattan distance"
    (should= 5 (grid/distance [0 0] [3 2])))

  (it "handles negative direction"
    (should= 7 (grid/distance [5 5] [2 1])))

  (it "computes positive x distance correctly"
    (should= 5 (grid/distance [0 0] [5 0])))

  (it "computes positive y distance correctly"
    (should= 3 (grid/distance [0 0] [0 3]))))

(describe "chebyshev-distance"
  (it "returns 0 for same position"
    (should= 0 (grid/chebyshev-distance [3 3] [3 3])))

  (it "returns max of row/col differences"
    (should= 3 (grid/chebyshev-distance [0 0] [3 2])))

  (it "handles negative direction"
    (should= 4 (grid/chebyshev-distance [5 5] [1 2])))

  (it "computes positive row distance"
    (should= 5 (grid/chebyshev-distance [0 0] [5 0])))

  (it "computes positive col distance"
    (should= 3 (grid/chebyshev-distance [0 0] [0 3]))))

(describe "attackable-target?"
  (it "returns true for player city"
    (should (world-query/attackable-target? {:type :city :city-status :player})))

  (it "returns true for free city"
    (should (world-query/attackable-target? {:type :city :city-status :free})))

  (it "returns false for computer city"
    (should-not (world-query/attackable-target? {:type :city :city-status :computer})))

  (it "returns true for player unit"
    (should (world-query/attackable-target? {:contents {:owner :player}})))

  (it "returns false for computer unit"
    (should-not (world-query/attackable-target? {:contents {:owner :computer}})))

  (it "returns false for empty cell"
    (should-not (world-query/attackable-target? {:type :sea})))

  (it "returns false for land cell without contents"
    (should-not (world-query/attackable-target? {:type :land}))))

(describe "move-toward"
  (it "returns the neighbor closest to target"
    (should= [2 0] (grid/move-toward [1 0] [3 0] [[0 0] [2 0]])))

  (it "returns nil when no passable neighbors"
    (should-be-nil (grid/move-toward [1 0] [3 0] [])))

  (it "returns nil when passable-neighbors is nil"
    (should-be-nil (grid/move-toward [1 0] [3 0] nil))))

(describe "find-visible-cities"
  (before (reset-all-atoms!))

  (it "finds computer cities"
    (set-test-computer-map!
     [[{:type :city :city-status :computer} {:type :sea}]
      [{:type :sea} {:type :sea}]])
    (should= [[0 0]] (world-query/find-visible-cities #{:computer})))

  (it "finds player cities with predicate"
    (set-test-computer-map!
     [[{:type :city :city-status :player} {:type :sea}]
      [{:type :sea} {:type :city :city-status :computer}]])
    (should= [[0 0]] (world-query/find-visible-cities #{:player})))

  (it "returns empty when no matching cities"
    (set-test-computer-map!
     [[{:type :sea} {:type :sea}]
      [{:type :sea} {:type :sea}]])
    (should= [] (world-query/find-visible-cities #{:computer}))))

(describe "adjacent-to-computer-unexplored?"
  (before (reset-all-atoms!))

  (it "returns true when neighbor is nil (unexplored)"
    (set-test-computer-map!
     [[{:type :sea} nil]
      [{:type :sea} {:type :sea}]])
    (set-test-world!
     [[{:type :sea} {:type :sea}]
      [{:type :sea} {:type :sea}]])
    (should (world-query/adjacent-to-computer-unexplored? [0 0])))

  (it "returns false when all neighbors explored"
    (set-test-computer-map!
     [[{:type :sea} {:type :sea}]
      [{:type :sea} {:type :sea}]])
    (set-test-world!
     [[{:type :sea} {:type :sea}]
      [{:type :sea} {:type :sea}]])
    (should-not (world-query/adjacent-to-computer-unexplored? [0 0]))))

(describe "stamp-territory"
  (before (reset-all-atoms!))

  (it "stamps land cell with army's country-id"
    (set-test-world! (build-test-map ["#"]))
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (action-resolution/stamp-territory [0 0] {:type :army :owner :computer :country-id 3})
    (should= 3 (:country-id (get-in (test-utils/read-test-state :game-map) [0 0]))))

  (it "stamps city cell with army's country-id"
    (set-test-world! (build-test-map ["X"]))
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (action-resolution/stamp-territory [0 0] {:type :army :owner :computer :country-id 5})
    (should= 5 (:country-id (get-in (test-utils/read-test-state :game-map) [0 0]))))

  (it "does not stamp sea cell"
    (set-test-world! (build-test-map ["~"]))
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (action-resolution/stamp-territory [0 0] {:type :army :owner :computer :country-id 3})
    (should-be-nil (:country-id (get-in (test-utils/read-test-state :game-map) [0 0]))))

  (it "does not stamp for player army"
    (set-test-world! (build-test-map ["#"]))
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (action-resolution/stamp-territory [0 0] {:type :army :owner :player :country-id 3})
    (should-be-nil (:country-id (get-in (test-utils/read-test-state :game-map) [0 0]))))

  (it "does not stamp for non-army unit"
    (set-test-world! (build-test-map ["#"]))
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (action-resolution/stamp-territory [0 0] {:type :transport :owner :computer :country-id 3})
    (should-be-nil (:country-id (get-in (test-utils/read-test-state :game-map) [0 0]))))

  (it "does not stamp when army has no country-id"
    (set-test-world! (build-test-map ["#"]))
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (action-resolution/stamp-territory [0 0] {:type :army :owner :computer})
    (should-be-nil (:country-id (get-in (test-utils/read-test-state :game-map) [0 0])))))
