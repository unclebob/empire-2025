(ns empire.movement.pathfinding-bfs-spec
  (:require [speclj.core :refer :all]
            [empire.movement.pathfinding-bfs :as pathfinding-bfs]
            [empire.movement.map-utils :as map-utils]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-world!]]))

(describe "bfs-to-unexplored-coast"
  (before (reset-all-atoms!))

  (it "finds path to sea cell adjacent to unexplored territory"
    ;; Explored sea at cols 0-2, unexplored at col 3
    (let [computer-map [[{:type :sea}] [{:type :sea}] [{:type :sea}] [nil]]]
      (let [path (pathfinding-bfs/bfs-to-unexplored-coast [0 0] computer-map)]
        (should-not-be-nil path)
        (should (vector? path))
        ;; Path excludes start, ends at cell adjacent to unexplored [3,0]
        (should= [2 0] (last path)))))

  (it "returns nil when no unexplored territory reachable"
    (let [computer-map [[{:type :sea}] [{:type :sea}] [{:type :sea}]]]
      (should-be-nil (pathfinding-bfs/bfs-to-unexplored-coast [0 0] computer-map))))

  (it "returns nil when start is not passable sea"
    (let [computer-map [[{:type :land}] [{:type :sea}] [nil]]]
      (should-be-nil (pathfinding-bfs/bfs-to-unexplored-coast [0 0] computer-map))))

  (it "does not traverse unexplored cells"
    ;; Gap of unexplored between two explored sea regions
    (let [computer-map [[{:type :sea}] [nil] [{:type :sea}] [nil]]]
      ;; From [0,0], cannot cross nil at [1,0] to reach [2,0]
      ;; No other explored sea cells reachable, so no path found.
      (should-be-nil (pathfinding-bfs/bfs-to-unexplored-coast [0 0] computer-map))))

  (it "returns path excluding start position"
    (let [computer-map [[{:type :sea}] [{:type :sea}] [nil]]]
      (let [path (pathfinding-bfs/bfs-to-unexplored-coast [0 0] computer-map)]
        (should= [[1 0]] path))))

  (it "finds shortest path through multi-step sea"
    ;; 5 columns of sea, unexplored at col 5
    (let [computer-map (vec (for [c (range 6)]
                              [(if (< c 5) {:type :sea} nil)]))]
      (let [path (pathfinding-bfs/bfs-to-unexplored-coast [0 0] computer-map)]
        (should-not-be-nil path)
        ;; Path should be [1,0] [2,0] [3,0] [4,0] — shortest to fog
        (should= [[1 0] [2 0] [3 0] [4 0]] path))))

  (it "navigates around land on computer-map"
    ;; Row 0: sea, land, sea, unexplored
    ;; Row 1: sea, sea,  sea, unexplored
    (let [computer-map [[{:type :sea} {:type :sea}]
                         [{:type :land} {:type :sea}]
                         [{:type :sea} {:type :sea}]
                         [nil nil]]]
      (let [path (pathfinding-bfs/bfs-to-unexplored-coast [0 0] computer-map)]
        (should-not-be-nil path)
        ;; Should go around land at [1,0] via row 1
        (should-not-contain [1 0] path)
        ;; Should end at col 2 — adjacent to unexplored col 3
        (should= 2 (first (last path))))))

  (it "handles {:type :unexplored} cells as unexplored"
    (let [computer-map [[{:type :sea}] [{:type :sea}] [{:type :unexplored}]]]
      (let [path (pathfinding-bfs/bfs-to-unexplored-coast [0 0] computer-map)]
        (should= [[1 0]] path)))))

(describe "bfs-to-unowned-coast"
  (before (reset-all-atoms!))

  (it "finds path to coast adjacent to free city"
    ;; All explored, free city at [3,0]
    (let [game-map [[{:type :sea}] [{:type :sea}] [{:type :sea}]
                     [{:type :city :city-status :free}]]
          computer-map [[{:type :sea}] [{:type :sea}] [{:type :sea}]
                         [{:type :city :city-status :free}]]]
      (let [path (pathfinding-bfs/bfs-to-unowned-coast [0 0] computer-map game-map)]
        (should-not-be-nil path)
        ;; Path should end at [2,0] — sea cell adjacent to free city [3,0]
        (should= [2 0] (last path)))))

  (it "finds path to coast adjacent to player city"
    (let [game-map [[{:type :sea}] [{:type :sea}]
                     [{:type :city :city-status :player}]]
          computer-map [[{:type :sea}] [{:type :sea}]
                         [{:type :city :city-status :player}]]]
      (let [path (pathfinding-bfs/bfs-to-unowned-coast [0 0] computer-map game-map)]
        (should= [[1 0]] path))))

  (it "does not target computer-owned cities"
    (let [game-map [[{:type :sea}] [{:type :sea}]
                     [{:type :city :city-status :computer}]]
          computer-map [[{:type :sea}] [{:type :sea}]
                         [{:type :city :city-status :computer}]]]
      (should-be-nil (pathfinding-bfs/bfs-to-unowned-coast [0 0] computer-map game-map))))

  (it "finds path to coast adjacent to unowned land"
    ;; Land at [2,0] with no country-id (unowned)
    (let [game-map [[{:type :sea}] [{:type :sea}] [{:type :land}]]
          computer-map [[{:type :sea}] [{:type :sea}] [{:type :land}]]]
      (let [path (pathfinding-bfs/bfs-to-unowned-coast [0 0] computer-map game-map)]
        (should= [[1 0]] path))))

  (it "does not target computer-owned land"
    (let [game-map [[{:type :sea}] [{:type :sea}]
                     [{:type :land :country-id 1}]]
          computer-map [[{:type :sea}] [{:type :sea}]
                         [{:type :land}]]]
      (should-be-nil (pathfinding-bfs/bfs-to-unowned-coast [0 0] computer-map game-map))))

  (it "returns nil when start is not sea"
    (let [game-map [[{:type :land}] [{:type :sea}] [{:type :city :city-status :free}]]
          computer-map [[{:type :land}] [{:type :sea}] [{:type :city :city-status :free}]]]
      (should-be-nil (pathfinding-bfs/bfs-to-unowned-coast [0 0] computer-map game-map))))

  (it "returns nil when no unowned land reachable"
    (let [game-map [[{:type :sea}] [{:type :sea}] [{:type :sea}]]
          computer-map [[{:type :sea}] [{:type :sea}] [{:type :sea}]]]
      (should-be-nil (pathfinding-bfs/bfs-to-unowned-coast [0 0] computer-map game-map)))))

(describe "bfs-to-coast-target"
  (before (reset-all-atoms!))

  (it "prefers unowned coast over nearer unexplored"
    (let [computer-map [[{:type :sea}] [{:type :sea}]
                         [{:type :sea}] [{:type :sea}]
                         [{:type :city :city-status :free}]]]
      (let [path (pathfinding-bfs/bfs-to-coast-target
                   [0 0] computer-map)]
        (should= [3 0] (last path)))))

  (it "falls back to unexplored when no unowned within lookahead"
    ;; Only unexplored territory (nil), no unowned land at all
    (let [computer-map [[{:type :sea}] [{:type :sea}] [nil]]]
      (let [path (pathfinding-bfs/bfs-to-coast-target
                   [0 0] computer-map)]
        (should= [[1 0]] path))))

  (it "returns nil when no targets exist"
    (let [computer-map [[{:type :sea}] [{:type :sea}] [{:type :sea}]]]
      (should-be-nil (pathfinding-bfs/bfs-to-coast-target
                       [0 0] computer-map))))

  (it "returns nil when start is not sea"
    (let [computer-map [[{:type :land}] [{:type :sea}] [nil]]]
      (should-be-nil (pathfinding-bfs/bfs-to-coast-target
                       [0 0] computer-map))))

  (it "does not look past lookahead limit"
    ;; Unexplored at depth 1 (nil at row 2), unowned at depth 6 (beyond 1+4=5)
    ;; Should pick unexplored since unowned is too far past first hit
    (let [computer-map (vec (for [r (range 8)]
                              [(cond (= r 2) nil
                                     (= r 7) {:type :city :city-status :free}
                                     :else {:type :sea})]))]
      (let [path (pathfinding-bfs/bfs-to-coast-target
                   [0 0] computer-map)]
        ;; Unexplored at row 2, so path ends at [1,0]
        (should= [1 0] (last path))))))

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
    (should (@#'empire.movement.pathfinding-bfs/available-for-target? [5 5] [0 0] 4 #{})))
  (it "returns false when not deep enough"
    (should-not (@#'empire.movement.pathfinding-bfs/available-for-target? [5 5] [0 0] 3 #{})))
  (it "returns false when current is start"
    (should-not (@#'empire.movement.pathfinding-bfs/available-for-target? [0 0] [0 0] 4 #{})))
  (it "returns false when current is excluded"
    (should-not (@#'empire.movement.pathfinding-bfs/available-for-target? [5 5] [0 0] 4 #{[5 5]}))))

(describe "unexplored-target?"
  (it "returns true when no best-unexplored and adjacent to unexplored"
    (let [computer-map [[{:type :sea} nil]
                         [{:type :sea} {:type :sea}]]]
      (should (@#'empire.movement.pathfinding-bfs/unexplored-target? [0 0] nil computer-map))))
  (it "returns false when best-unexplored already found"
    (let [computer-map [[{:type :sea} nil]
                         [{:type :sea} {:type :sea}]]]
      (should-not (@#'empire.movement.pathfinding-bfs/unexplored-target? [0 0] [3 3] computer-map))))
  (it "returns false when not adjacent to unexplored"
    (let [computer-map [[{:type :sea} {:type :sea}]
                         [{:type :sea} {:type :sea}]]]
      (should-not (@#'empire.movement.pathfinding-bfs/unexplored-target? [0 0] nil computer-map)))))

(describe "adjacent-to-unexplored?"
  (it "returns truthy when neighbor is nil"
    (let [computer-map [[{:type :sea} nil]
                         [{:type :sea} {:type :sea}]]]
      (should (#'empire.movement.pathfinding-bfs/adjacent-to-unexplored? [0 0] computer-map))))

  (it "returns truthy when neighbor is {:type :unexplored}"
    (let [computer-map [[{:type :sea} {:type :unexplored}]
                         [{:type :sea} {:type :sea}]]]
      (should (#'empire.movement.pathfinding-bfs/adjacent-to-unexplored? [0 0] computer-map))))

  (it "returns falsy when all neighbors are explored"
    (let [computer-map [[{:type :sea} {:type :sea}]
                         [{:type :sea} {:type :sea}]]]
      (should-not (#'empire.movement.pathfinding-bfs/adjacent-to-unexplored? [0 0] computer-map))))

  (it "handles edge position correctly"
    (let [computer-map [[{:type :sea}]]]
      ;; Single cell, no neighbors within bounds, should return falsy
      (should-not (#'empire.movement.pathfinding-bfs/adjacent-to-unexplored? [0 0] computer-map)))))

(describe "at-exploration-frontier?"
  (it "returns truthy when adjacent to both unexplored and known land"
    (let [computer-map [[{:type :sea} nil]
                         [{:type :land} {:type :sea}]]]
      ;; [0 0] is adjacent to nil [1 0] (unexplored) and [0 1] is land
      (should (#'empire.movement.pathfinding-bfs/at-exploration-frontier? [0 0] computer-map))))

  (it "returns falsy when only adjacent to unexplored (no known land)"
    (let [computer-map [[{:type :sea} nil]
                         [{:type :sea} {:type :sea}]]]
      (should-not (#'empire.movement.pathfinding-bfs/at-exploration-frontier? [0 0] computer-map))))

  (it "returns falsy when only adjacent to known land (no unexplored)"
    (let [computer-map [[{:type :sea} {:type :sea}]
                         [{:type :land} {:type :sea}]]]
      (should-not (#'empire.movement.pathfinding-bfs/at-exploration-frontier? [0 0] computer-map))))

  (it "returns falsy when no neighbors"
    (let [computer-map [[{:type :sea}]]]
      (should-not (#'empire.movement.pathfinding-bfs/at-exploration-frontier? [0 0] computer-map))))

  (it "recognizes :city as known land for frontier detection"
    (let [computer-map [[{:type :sea} nil]
                         [{:type :city} {:type :sea}]]]
      (should (#'empire.movement.pathfinding-bfs/at-exploration-frontier? [0 0] computer-map)))))

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
        (should (#'empire.movement.pathfinding-bfs/adjacent-to-target-continent-land?
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
