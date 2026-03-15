(ns empire.game-mechanics.movement.pathfinding-bfs-cache-spec
  (:require [speclj.core :refer :all]
            [empire.game-mechanics.movement.pathfinding-bfs :as pathfinding-bfs]
            [empire.game-mechanics.movement.map-utils :as map-utils]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-world!]]))
(describe "BFS cache behavior"
  (before
    (reset-all-atoms!)
    (pathfinding-bfs/clear-bfs-caches))

  (it "caches unexplored BFS result for same unit-type"
    (set-test-world! (build-test-map ["~~~"
                                            "~~~"
                                            "~~~"]))
    (set-test-computer-map! (build-test-map ["~~~"
                                                "~~~"
                                                "~~-"]))
    ;; Two calls with different starts but same unit-type return same result
    (let [result1 (pathfinding-bfs/find-nearest-unexplored [0 0] :transport)
          result2 (pathfinding-bfs/find-nearest-unexplored [2 0] :transport)]
      (should-not-be-nil result1)
      (should= result1 result2)))

  (it "caches unload BFS result for same target-continent"
    (set-test-world! (build-test-map ["~~~"
                                            "~~~"
                                            "~~~"
                                            "###"]))
    (let [target-continent #{[0 3] [1 3] [2 3]}
          result1 (pathfinding-bfs/find-nearest-unload-position [0 0] target-continent)
          result2 (pathfinding-bfs/find-nearest-unload-position [2 0] target-continent)]
      (should-not-be-nil result1)
      (should= result1 result2)))

  (it "clear-bfs-caches resets the caches"
    (set-test-world! (build-test-map ["~~~"
                                            "~~~"
                                            "~~~"]))
    (set-test-computer-map! (build-test-map ["~~~"
                                                "~~~"
                                                "~~-"]))
    ;; Populate cache
    (pathfinding-bfs/find-nearest-unexplored [0 0] :transport)
    ;; Clear caches
    (pathfinding-bfs/clear-bfs-caches)
    ;; Change the map so a fresh BFS would give a different result
    (set-test-computer-map! (build-test-map ["-~~"
                                                "~~~"
                                                "~~~"]))
    ;; After clearing, fresh BFS runs and finds new target
    (let [result (pathfinding-bfs/find-nearest-unexplored [1 1] :transport)]
      (should-not-be-nil result)
      ;; Should be adjacent to [0,0] now (the new unexplored cell)
      (should (some #{result} [[0 1] [1 0] [1 1]]))))

  (it "different unit-types get independent cache entries"
    (set-test-world! (build-test-map ["##~"
                                            "#~~"
                                            "~~~"]))
    (set-test-computer-map! (build-test-map ["##~"
                                                "#~~"
                                                "~~-"]))
    ;; Transport can only traverse sea; fighter can traverse all
    (let [transport-result (pathfinding-bfs/find-nearest-unexplored [2 0] :transport)
          fighter-result (pathfinding-bfs/find-nearest-unexplored [0 0] :fighter)]
      (should-not-be-nil transport-result)
      (should-not-be-nil fighter-result)
      ;; They should be independently cached (may differ since transport
      ;; BFS only covers sea cells while fighter covers all)
      (should (some #{fighter-result} [[1 1] [1 2] [2 1]])))))

(describe "find-nearest-unload-position"
  (before (reset-all-atoms!))

  (it "finds nearest sea cell adjacent to target-continent land"
    (set-test-world! (build-test-map ["###"
                                            "###"
                                            "~~~"
                                            "~~~"
                                            "###"
                                            "###"]))
    (let [target-continent #{[0 4] [1 4] [2 4] [0 5] [1 5] [2 5]}
          result (pathfinding-bfs/find-nearest-unload-position [1 2] target-continent)]
      ;; Should find a sea cell in row 3 adjacent to land in row 4
      (should-not-be-nil result)
      (should= 3 (second result))))

  (it "returns nil when target-continent land is unreachable"
    (set-test-world! (build-test-map ["###"
                                            "~~~"
                                            "~~~"]))
    (let [target-continent #{[10 10] [11 10]}
          result (pathfinding-bfs/find-nearest-unload-position [1 1] target-continent)]
      (should-be-nil result)))

  (it "ignores non-target-continent land"
    (set-test-world! (build-test-map ["###"
                                            "~~~"
                                            "~~~"
                                            "~~~"
                                            "###"]))
    (let [target-continent #{[0 4] [1 4] [2 4]}
          result (pathfinding-bfs/find-nearest-unload-position [1 1] target-continent)]
      (should-not-be-nil result)
      (should= 3 (second result))))

  (it "skips occupied sea cells as unload destinations"
    (set-test-world! (build-test-map ["###"
                                            "~~~"
                                            "~~~"
                                            "~d~"
                                            "###"]))
    (let [target-continent #{[0 4] [1 4] [2 4]}
          result (pathfinding-bfs/find-nearest-unload-position [1 2] target-continent)]
      (should-not-be-nil result)
      (should= 3 (second result))
      (should-not= [1 3] result)))

  (it "finds globally nearest position on target continent"
    (set-test-world! (build-test-map ["~~~"
                                            "~~~"
                                            "~~~"
                                            "~~~"
                                            "O##"
                                            "###"
                                            "###"
                                            "###"
                                            "###"]))
    (let [target-continent #{[0 4] [1 4] [2 4] [0 5] [1 5] [2 5]
                             [0 6] [1 6] [2 6] [0 7] [1 7] [2 7]
                             [0 8] [1 8] [2 8]}
          result (pathfinding-bfs/find-nearest-unload-position [1 2] target-continent)]
      (should-not-be-nil result)
      (should= 3 (second result)))))

(describe "find-nearest-unexplored-coastline"
  (before (reset-all-atoms!))

  (it "finds sea cell at coastal exploration frontier"
    (set-test-world! (build-test-map ["~~~"
                                            "~~~"
                                            "~~~"
                                            "~~~"
                                            "###"]))
    (set-test-computer-map! [[{:type :sea} {:type :sea} {:type :sea} {:type :sea} {:type :land}]
                                [{:type :sea} {:type :sea} {:type :sea} {:type :sea} {:type :land}]
                                [{:type :sea} {:type :sea} {:type :sea} nil nil]])
    (let [target (pathfinding-bfs/find-nearest-unexplored-coastline [0 0] :transport)]
      (should-not-be-nil target)
      (should (>= (second target) 2))))

  (it "returns nil when no coastline frontier exists"
    (set-test-world! (build-test-map ["~~~"
                                            "~~~"
                                            "~~~"]))
    (set-test-computer-map! [[{:type :sea} {:type :sea} {:type :sea}]
                                [{:type :sea} {:type :sea} {:type :sea}]
                                [{:type :sea} {:type :sea} nil]])
    (should-be-nil (pathfinding-bfs/find-nearest-unexplored-coastline [0 0] :transport)))

  (it "uses distinct cache key from regular unexplored BFS"
    (pathfinding-bfs/clear-bfs-caches)
    (set-test-world! (build-test-map ["~~~"
                                            "~~~"
                                            "~~~"
                                            "~~~"
                                            "###"]))
    (set-test-computer-map! [[{:type :sea} {:type :sea} {:type :sea} {:type :sea} {:type :land}]
                                [{:type :sea} {:type :sea} {:type :sea} {:type :sea} {:type :land}]
                                [{:type :sea} {:type :sea} {:type :sea} nil nil]])
    (let [coastline (pathfinding-bfs/find-nearest-unexplored-coastline [0 0] :transport)
          general (pathfinding-bfs/find-nearest-unexplored [0 0] :transport)]
      (should-not-be-nil coastline)
      (should-not-be-nil general)))

  (it "returns cached result on second call with same unit-type"
    (pathfinding-bfs/clear-bfs-caches)
    (set-test-world! (build-test-map ["~~~"
                                            "~~~"
                                            "~~~"
                                            "~~~"
                                            "###"]))
    (set-test-computer-map! [[{:type :sea} {:type :sea} {:type :sea} {:type :sea} {:type :land}]
                                [{:type :sea} {:type :sea} {:type :sea} {:type :sea} {:type :land}]
                                [{:type :sea} {:type :sea} {:type :sea} nil nil]])
    (let [result1 (pathfinding-bfs/find-nearest-unexplored-coastline [0 0] :transport)
          result2 (pathfinding-bfs/find-nearest-unexplored-coastline [2 2] :transport)]
      (should-not-be-nil result1)
      (should= result1 result2))))

(describe "mutation-killing tests"
  (before
    (reset-all-atoms!)
    (pathfinding-bfs/clear-bfs-caches))

  (context "adjacent-to-target-continent-land? with city"
    (it "recognizes :city as target-continent land"
      (let [game-map [[{:type :sea} {:type :city :city-status :free}]
                       [{:type :sea} {:type :sea}]]]
        (should (#'empire.game-mechanics.movement.pathfinding-bfs/adjacent-to-target-continent-land?
                  [0 0] #{[0 1]} game-map)))))

  (context "find-nearest-unload-position start-skip"
    (it "does not return start even when start is valid unload position"
      ;; Start [0,0] is sea, empty, adjacent to target continent land [1,0]
      (set-test-world! (build-test-map ["~#" "~#"]))
      (let [target-continent #{[1 0] [1 1]}
            result (pathfinding-bfs/find-nearest-unload-position [0 0] target-continent)]
        (should-not-be-nil result)
        (should-not= [0 0] result)))

    (it "finds unload position adjacent to city on target continent"
      (set-test-world! [[{:type :sea} {:type :city :city-status :free}]
                               [{:type :sea} {:type :sea}]])
      (let [target-continent #{[0 1]}
            result (pathfinding-bfs/find-nearest-unload-position [1 0] target-continent)]
        (should-not-be-nil result)
        (should= [0 0] result))))

  (context "sea-reaches-edge? edge isolation"
    (it "detects right edge only (kills dec-rows mutation)"
      (set-test-world! (build-test-map ["###" "##~" "###"]))
      (should (pathfinding-bfs/sea-reaches-edge? [2 1])))

    (it "detects bottom edge only (kills dec-cols mutation)"
      (set-test-world! (build-test-map ["###" "#~#"]))
      (should (pathfinding-bfs/sea-reaches-edge? [1 1])))

    (it "detects left edge only (kills zero-r mutation)"
      (set-test-world! (build-test-map ["~###" "~###" "~###"]))
      (should (pathfinding-bfs/sea-reaches-edge? [0 1])))

    (it "detects top edge only (kills zero-c mutation)"
      (set-test-world! (build-test-map ["##~##" "##~##" "##~##"]))
      (should (pathfinding-bfs/sea-reaches-edge? [2 0]))))

  (context "find-nearest-unexplored-coastline start-skip"
    (it "does not return start even when start is at coastal frontier"
      (set-test-world! (build-test-map ["~#" "~~"]))
      (set-test-computer-map! [[{:type :sea} nil]
                                   [{:type :land} {:type :sea}]])
      (let [result (pathfinding-bfs/find-nearest-unexplored-coastline [0 0] :transport)]
        (should-not-be-nil result)
        (should-not= [0 0] result)))))
