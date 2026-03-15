(ns empire.game-mechanics.movement.pathfinding-bfs-frontier-spec
  (:require [speclj.core :refer :all]
            [empire.game-mechanics.movement.pathfinding-bfs :as pathfinding-bfs]
            [empire.game-mechanics.movement.map-utils :as map-utils]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-world!]]))
(describe "reconstruct-path"
  (it "reconstructs path from came-from map"
    (let [came-from {[1 0] [0 0]
                     [2 0] [1 0]
                     [3 0] [2 0]}]
      (should= [[0 0] [1 0] [2 0] [3 0]]
               (map-utils/reconstruct-path came-from [0 0] [3 0]))))

  (it "returns single element for start=goal"
    (should= [[0 0]]
             (map-utils/reconstruct-path {} [0 0] [0 0])))

  (it "reconstructs two-step path"
    (let [came-from {[1 1] [0 0]}]
      (should= [[0 0] [1 1]]
               (map-utils/reconstruct-path came-from [0 0] [1 1])))))

(describe "sea-reaches-edge?"
  (before (reset-all-atoms!))

  (it "returns true when sea cell is on edge"
    (set-test-world! (build-test-map ["~~~"
                                             "###"
                                             "###"]))
    (should (pathfinding-bfs/sea-reaches-edge? [0 0])))

  (it "returns true when sea connects to edge"
    (set-test-world! (build-test-map ["###"
                                             "#~#"
                                             "#~~"]))
    ;; [1 1] connects via [2 1] or [1 2] or [2 2] to edge
    (should (pathfinding-bfs/sea-reaches-edge? [1 1])))

  (it "returns false for landlocked sea"
    (set-test-world! (build-test-map ["#####"
                                             "#~~~#"
                                             "#~~~#"
                                             "#~~~#"
                                             "#####"]))
    (should-not (pathfinding-bfs/sea-reaches-edge? [2 2])))

  (it "returns true for sea cell directly on edge"
    (set-test-world! (build-test-map ["~#"
                                             "#~"]))
    (should (pathfinding-bfs/sea-reaches-edge? [0 0])))

  (it "returns true for corner sea cell"
    (set-test-world! (build-test-map ["##"
                                             "#~"]))
    (should (pathfinding-bfs/sea-reaches-edge? [1 1]))))

(describe "available-for-target?"
  (it "returns true when deep enough, not start, not excluded"
    (should (@#'empire.game-mechanics.movement.pathfinding-bfs/available-for-target? [5 5] [0 0] 4 #{})))
  (it "returns false when not deep enough"
    (should-not (@#'empire.game-mechanics.movement.pathfinding-bfs/available-for-target? [5 5] [0 0] 3 #{})))
  (it "returns false when current is start"
    (should-not (@#'empire.game-mechanics.movement.pathfinding-bfs/available-for-target? [0 0] [0 0] 4 #{})))
  (it "returns false when current is excluded"
    (should-not (@#'empire.game-mechanics.movement.pathfinding-bfs/available-for-target? [5 5] [0 0] 4 #{[5 5]}))))

(describe "unexplored-target?"
  (it "returns true when no best-unexplored and adjacent to unexplored"
    (let [computer-map [[{:type :sea} nil]
                         [{:type :sea} {:type :sea}]]]
      (should (@#'empire.game-mechanics.movement.pathfinding-bfs/unexplored-target? [0 0] nil computer-map))))
  (it "returns false when best-unexplored already found"
    (let [computer-map [[{:type :sea} nil]
                         [{:type :sea} {:type :sea}]]]
      (should-not (@#'empire.game-mechanics.movement.pathfinding-bfs/unexplored-target? [0 0] [3 3] computer-map))))
  (it "returns false when not adjacent to unexplored"
    (let [computer-map [[{:type :sea} {:type :sea}]
                         [{:type :sea} {:type :sea}]]]
      (should-not (@#'empire.game-mechanics.movement.pathfinding-bfs/unexplored-target? [0 0] nil computer-map)))))

(describe "adjacent-to-unexplored?"
  (it "returns truthy when neighbor is nil"
    (let [computer-map [[{:type :sea} nil]
                         [{:type :sea} {:type :sea}]]]
      (should (#'empire.game-mechanics.movement.pathfinding-bfs/adjacent-to-unexplored? [0 0] computer-map))))

  (it "returns truthy when neighbor is {:type :unexplored}"
    (let [computer-map [[{:type :sea} {:type :unexplored}]
                         [{:type :sea} {:type :sea}]]]
      (should (#'empire.game-mechanics.movement.pathfinding-bfs/adjacent-to-unexplored? [0 0] computer-map))))

  (it "returns falsy when all neighbors are explored"
    (let [computer-map [[{:type :sea} {:type :sea}]
                         [{:type :sea} {:type :sea}]]]
      (should-not (#'empire.game-mechanics.movement.pathfinding-bfs/adjacent-to-unexplored? [0 0] computer-map))))

  (it "handles edge position correctly"
    (let [computer-map [[{:type :sea}]]]
      ;; Single cell, no neighbors within bounds, should return falsy
      (should-not (#'empire.game-mechanics.movement.pathfinding-bfs/adjacent-to-unexplored? [0 0] computer-map)))))

(describe "at-exploration-frontier?"
  (it "returns truthy when adjacent to both unexplored and known land"
    (let [computer-map [[{:type :sea} nil]
                         [{:type :land} {:type :sea}]]]
      ;; [0 0] is adjacent to nil [1 0] (unexplored) and [0 1] is land
      (should (#'empire.game-mechanics.movement.pathfinding-bfs/at-exploration-frontier? [0 0] computer-map))))

  (it "returns falsy when only adjacent to unexplored (no known land)"
    (let [computer-map [[{:type :sea} nil]
                         [{:type :sea} {:type :sea}]]]
      (should-not (#'empire.game-mechanics.movement.pathfinding-bfs/at-exploration-frontier? [0 0] computer-map))))

  (it "returns falsy when only adjacent to known land (no unexplored)"
    (let [computer-map [[{:type :sea} {:type :sea}]
                         [{:type :land} {:type :sea}]]]
      (should-not (#'empire.game-mechanics.movement.pathfinding-bfs/at-exploration-frontier? [0 0] computer-map))))

  (it "returns falsy when no neighbors"
    (let [computer-map [[{:type :sea}]]]
      (should-not (#'empire.game-mechanics.movement.pathfinding-bfs/at-exploration-frontier? [0 0] computer-map))))

  (it "recognizes :city as known land for frontier detection"
    (let [computer-map [[{:type :sea} nil]
                         [{:type :city} {:type :sea}]]]
      (should (#'empire.game-mechanics.movement.pathfinding-bfs/at-exploration-frontier? [0 0] computer-map)))))

(describe "find-nearest-unexplored"
  (before (reset-all-atoms!))

  (it "finds sea cell adjacent to unexplored territory"
    (set-test-world! (build-test-map ["~~~"
                                            "~~~"
                                            "~~~"]))
    (set-test-computer-map! (build-test-map ["~~~"
                                                "~~~"
                                                "~~-"]))
    (let [target (pathfinding-bfs/find-nearest-unexplored [0 0] :transport)]
      (should-not-be-nil target)
      ;; Target should be adjacent to the unexplored cell [2,2]
      (should (some #{target} [[1 1] [1 2] [2 1]]))))

  (it "returns nil when no unexplored territory exists"
    (set-test-world! (build-test-map ["~~~"
                                            "~~~"]))
    (set-test-computer-map! (build-test-map ["~~~"
                                                "~~~"]))
    (should-be-nil (pathfinding-bfs/find-nearest-unexplored [0 0] :transport)))

  (it "does not exhibit northwest bias"
    ;; Transport at center [2,2], unexplored only in SE corner
    (set-test-world! (build-test-map ["~~~~~"
                                            "~~~~~"
                                            "~~~~~"
                                            "~~~~~"
                                            "~~~~~"]))
    (set-test-computer-map! (build-test-map ["~~~~~"
                                                "~~~~~"
                                                "~~~~~"
                                                "~~~~~"
                                                "~~~~-"]))
    (let [target (pathfinding-bfs/find-nearest-unexplored [2 2] :transport)]
      (should-not-be-nil target)
      ;; Target should be south-east of start, not northwest
      (should (>= (first target) 2))
      (should (>= (second target) 2))))

  (it "skips start position even if adjacent to unexplored"
    (set-test-world! (build-test-map ["~~"
                                            "~~"]))
    (set-test-computer-map! (build-test-map ["~-"
                                                "~~"]))
    (let [target (pathfinding-bfs/find-nearest-unexplored [0 0] :transport)]
      (should-not-be-nil target)
      (should-not= [0 0] target)))

  (it "returns nil when only start is adjacent to unexplored"
    ;; Only one sea cell, surrounded by land. Start is the only sea cell.
    (set-test-world! (build-test-map ["#~#"]))
    (set-test-computer-map! (build-test-map ["#~-"]))
    (should-be-nil (pathfinding-bfs/find-nearest-unexplored [1 0] :transport)))

  (it "detects {:type :unexplored} cells as unexplored (real game format)"
    (set-test-world! (build-test-map ["~~~"
                                            "~~~"
                                            "~~~"]))
    (let [computer-map (vec (for [r (range 3)]
                              (vec (for [c (range 3)]
                                     (if (and (= r 2) (= c 2))
                                       {:type :unexplored}
                                       {:type :sea})))))]
      (set-test-computer-map! computer-map)
      (let [target (pathfinding-bfs/find-nearest-unexplored [0 0] :transport)]
        (should-not-be-nil target)
        ;; Target should be adjacent to the unexplored cell [2,2]
        (should (some #{target} [[1 1] [1 2] [2 1]])))))

  (it "works with fighter unit type over all terrain"
    ;; Fighter can traverse land and sea
    (set-test-world! (build-test-map ["##~"
                                            "#~~"
                                            "~~~"]))
    (set-test-computer-map! (build-test-map ["##~"
                                                "#~~"
                                                "~~-"]))
    (let [target (pathfinding-bfs/find-nearest-unexplored [0 0] :fighter)]
      (should-not-be-nil target)
      ;; Should find a cell adjacent to [2,2]
      (should (some #{target} [[1 1] [1 2] [2 1]])))))

