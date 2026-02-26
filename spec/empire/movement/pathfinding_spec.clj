(ns empire.movement.pathfinding-spec
  (:require [speclj.core :refer :all]
            [empire.movement.pathfinding :as pathfinding]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [build-test-map reset-all-atoms!]]))

(describe "heuristic"
  (it "returns Manhattan distance"
    (should= 5 (pathfinding/heuristic [0 0] [3 2])))

  (it "returns 0 for same position"
    (should= 0 (pathfinding/heuristic [5 5] [5 5])))

  (it "handles negative coordinates correctly"
    (should= 4 (pathfinding/heuristic [0 0] [-2 -2]))))

(describe "passable?"
  (before (reset-all-atoms!))

  (it "returns false for unexplored cells"
    (should-not (pathfinding/passable? :army {:type :unexplored})))

  (it "returns false for nil cell"
    (should-not (pathfinding/passable? :army nil)))

  (it "returns true for land cell with army"
    (should (pathfinding/passable? :army {:type :land})))

  (it "returns false for sea cell with army"
    (should-not (pathfinding/passable? :army {:type :sea})))

  (it "returns true for sea cell with ship"
    (should (pathfinding/passable? :destroyer {:type :sea})))

  (it "returns false for land cell with ship"
    (should-not (pathfinding/passable? :destroyer {:type :land})))

  (it "returns true for any explored cell with fighter"
    (should (pathfinding/passable? :fighter {:type :land}))
    (should (pathfinding/passable? :fighter {:type :sea}))
    (should (pathfinding/passable? :fighter {:type :city}))))

(describe "get-passable-neighbors"
  (before (reset-all-atoms!))

  (it "returns land neighbors for army"
    (reset! atoms/game-map (build-test-map ["###"
                                             "#a#"
                                             "~~~"]))
    (let [neighbors (pathfinding/get-passable-neighbors [1 1] :army @atoms/game-map)]
      (should= 5 (count neighbors))
      (should-contain [0 0] neighbors)
      (should-contain [1 0] neighbors)
      (should-contain [2 0] neighbors)
      (should-contain [0 1] neighbors)
      (should-contain [2 1] neighbors)))

  (it "returns sea neighbors for ship"
    (reset! atoms/game-map (build-test-map ["~~~"
                                             "~d~"
                                             "###"]))
    (let [neighbors (pathfinding/get-passable-neighbors [1 1] :destroyer @atoms/game-map)]
      (should= 5 (count neighbors))
      (should-contain [0 0] neighbors)
      (should-contain [1 0] neighbors)
      (should-contain [2 0] neighbors)
      (should-contain [0 1] neighbors)
      (should-contain [2 1] neighbors)))

  (it "returns all neighbors for fighter"
    (reset! atoms/game-map (build-test-map ["#~#"
                                             "~f~"
                                             "#~#"]))
    (let [neighbors (pathfinding/get-passable-neighbors [1 1] :fighter @atoms/game-map)]
      (should= 8 (count neighbors)))))

(describe "a-star"
  (before (reset-all-atoms!))

  (it "finds direct path on clear terrain"
    (reset! atoms/game-map (build-test-map ["a##"]))
    (let [path (pathfinding/a-star [0 0] [2 0] :army @atoms/game-map)]
      (should= [[0 0] [1 0] [2 0]] path)))

  (it "returns just start position when already at goal"
    (reset! atoms/game-map (build-test-map ["a"]))
    (let [path (pathfinding/a-star [0 0] [0 0] :army @atoms/game-map)]
      (should= [[0 0]] path)))

  (it "navigates around obstacles"
    (reset! atoms/game-map (build-test-map ["#~#"
                                             "###"
                                             "#a#"]))
    ;; Army at [1 2] wants to reach [0 0], must go around the sea
    (let [path (pathfinding/a-star [1 2] [0 0] :army @atoms/game-map)]
      (should-not-be-nil path)
      (should= [1 2] (first path))
      (should= [0 0] (last path))
      ;; Path should not pass through sea at [1 0]
      (should-not-contain [1 0] path)))

  (it "keeps armies on land"
    (reset! atoms/game-map (build-test-map ["a~~#"]))
    ;; Army cannot cross water
    (let [path (pathfinding/a-star [0 0] [3 0] :army @atoms/game-map)]
      (should-be-nil path)))

  (it "keeps ships on sea"
    (reset! atoms/game-map (build-test-map ["d##~"]))
    ;; Ship cannot cross land
    (let [path (pathfinding/a-star [0 0] [3 0] :destroyer @atoms/game-map)]
      (should-be-nil path)))

  (it "allows fighters to fly over any terrain"
    (reset! atoms/game-map (build-test-map ["f~~#"]))
    (let [path (pathfinding/a-star [0 0] [3 0] :fighter @atoms/game-map)]
      (should-not-be-nil path)
      (should= [0 0] (first path))
      (should= [3 0] (last path))))

  (it "returns nil for unreachable goal"
    (reset! atoms/game-map (build-test-map ["a~~"
                                             "~~~"
                                             "~~#"]))
    ;; Army on island, land at [2 2] unreachable
    (let [path (pathfinding/a-star [0 0] [2 2] :army @atoms/game-map)]
      (should-be-nil path)))

  (it "finds optimal diagonal path on open grid"
    (let [size 15
          row (vec (repeat size {:type :land}))
          grid (vec (repeat size row))]
      (reset! atoms/game-map grid)
      (let [path (pathfinding/a-star [0 0] [(dec size) (dec size)]
                                      :army @atoms/game-map)]
        (should-not-be-nil path)
        (should= [0 0] (first path))
        (should= [(dec size) (dec size)] (last path))
        ;; Optimal Chebyshev distance on open grid is max(dx,dy)
        (should= size (count path)))))

  (it "respects neighbor-filter when provided"
    (reset! atoms/game-map (build-test-map ["###"
                                             "###"
                                             "###"]))
    ;; Filter excludes [1 0] — path must detour through row 1
    (let [filter-fn (fn [pos] (not= pos [1 0]))
          path (pathfinding/a-star [0 0] [2 0] :army @atoms/game-map nil filter-fn)]
      (should-not-be-nil path)
      (should= [0 0] (first path))
      (should= [2 0] (last path))
      (should-not-contain [1 0] path)))

  (it "finds path on larger map"
    (reset! atoms/game-map (build-test-map ["#####"
                                             "#~~~#"
                                             "#~#~#"
                                             "#~~~#"
                                             "a####"]))
    ;; Army at [0 4] needs to reach [4 0]
    (let [path (pathfinding/a-star [0 4] [4 0] :army @atoms/game-map)]
      (should-not-be-nil path)
      (should= [0 4] (first path))
      (should= [4 0] (last path)))))

(describe "next-step"
  (before (reset-all-atoms!))

  (it "returns first step of computed path"
    (reset! atoms/game-map (build-test-map ["a##"]))
    (let [step (pathfinding/next-step [0 0] [2 0] :army)]
      (should= [1 0] step)))

  (it "returns nil for unreachable goal"
    (reset! atoms/game-map (build-test-map ["a~~#"]))
    (let [step (pathfinding/next-step [0 0] [3 0] :army)]
      (should-be-nil step)))

  (it "returns nil when already at goal"
    (reset! atoms/game-map (build-test-map ["a"]))
    (let [step (pathfinding/next-step [0 0] [0 0] :army)]
      (should-be-nil step))))

(describe "path caching"
  (before
    (reset-all-atoms!)
    (pathfinding/clear-path-cache))

  (it "caches computed paths"
    (reset! atoms/game-map (build-test-map ["a####"]))
    ;; First call computes path
    (let [step1 (pathfinding/next-step [0 0] [4 0] :army)
          ;; Second call should use cached path
          step2 (pathfinding/next-step [0 0] [4 0] :army)]
      (should= [1 0] step1)
      (should= [1 0] step2)))

  (it "clear-path-cache resets the cache"
    (reset! atoms/game-map (build-test-map ["a##"]))
    (pathfinding/next-step [0 0] [2 0] :army)
    (pathfinding/clear-path-cache)
    ;; Cache should be empty now, so this should work fresh
    (reset! atoms/game-map (build-test-map ["a~#"]))
    ;; Path should now be nil since terrain changed
    (let [step (pathfinding/next-step [0 0] [2 0] :army)]
      (should-be-nil step)))

  (it "caches sub-paths for intermediate positions"
    (reset! atoms/game-map (build-test-map ["a####"]))
    (pathfinding/clear-path-cache)
    ;; Compute path from [0 0] to [4 0]
    (pathfinding/next-step [0 0] [4 0] :army)
    ;; Now [1 0] to [4 0] should be cached as a sub-path
    (let [step (pathfinding/next-step [1 0] [4 0] :army)]
      (should= [2 0] step))))

(describe "find-nearest-unexplored"
  (before (reset-all-atoms!))

  (it "finds sea cell adjacent to unexplored territory"
    (reset! atoms/game-map (build-test-map ["~~~"
                                            "~~~"
                                            "~~~"]))
    (reset! atoms/computer-map (build-test-map ["~~~"
                                                "~~~"
                                                "~~-"]))
    (let [target (pathfinding/find-nearest-unexplored [0 0] :transport)]
      (should-not-be-nil target)
      ;; Target should be adjacent to the unexplored cell [2,2]
      (should (some #{target} [[1 1] [1 2] [2 1]]))))

  (it "returns nil when no unexplored territory exists"
    (reset! atoms/game-map (build-test-map ["~~~"
                                            "~~~"]))
    (reset! atoms/computer-map (build-test-map ["~~~"
                                                "~~~"]))
    (should-be-nil (pathfinding/find-nearest-unexplored [0 0] :transport)))

  (it "does not exhibit northwest bias"
    ;; Transport at center [2,2], unexplored only in SE corner
    (reset! atoms/game-map (build-test-map ["~~~~~"
                                            "~~~~~"
                                            "~~~~~"
                                            "~~~~~"
                                            "~~~~~"]))
    (reset! atoms/computer-map (build-test-map ["~~~~~"
                                                "~~~~~"
                                                "~~~~~"
                                                "~~~~~"
                                                "~~~~-"]))
    (let [target (pathfinding/find-nearest-unexplored [2 2] :transport)]
      (should-not-be-nil target)
      ;; Target should be south-east of start, not northwest
      (should (>= (first target) 2))
      (should (>= (second target) 2))))

  (it "skips start position even if adjacent to unexplored"
    (reset! atoms/game-map (build-test-map ["~~"
                                            "~~"]))
    (reset! atoms/computer-map (build-test-map ["~-"
                                                "~~"]))
    ;; Start [0,0] is adjacent to unexplored [1,0] on computer-map
    ;; But [1,0] is sea on game-map, so BFS should find [1,0] as target
    (let [target (pathfinding/find-nearest-unexplored [0 0] :transport)]
      ;; Should find a cell adjacent to unexplored, but not start
      (should-not-be-nil target)
      (should-not= [0 0] target)))

  (it "returns nil when only start is adjacent to unexplored"
    ;; Only one sea cell, surrounded by land. Start is the only sea cell.
    (reset! atoms/game-map (build-test-map ["#~#"]))
    (reset! atoms/computer-map (build-test-map ["#~-"]))
    ;; Start [1,0] is adjacent to unexplored [2,0] but [2,0] is not passable sea
    ;; No other sea cells to explore toward
    (should-be-nil (pathfinding/find-nearest-unexplored [1 0] :transport)))

  (it "detects {:type :unexplored} cells as unexplored (real game format)"
    ;; In the actual game, computer-map cells are {:type :unexplored},
    ;; not nil as in test maps built with build-test-map.
    (reset! atoms/game-map (build-test-map ["~~~"
                                            "~~~"
                                            "~~~"]))
    (let [computer-map (vec (for [r (range 3)]
                              (vec (for [c (range 3)]
                                     (if (and (= r 2) (= c 2))
                                       {:type :unexplored}
                                       {:type :sea})))))]
      (reset! atoms/computer-map computer-map)
      (let [target (pathfinding/find-nearest-unexplored [0 0] :transport)]
        (should-not-be-nil target)
        ;; Target should be adjacent to the unexplored cell [2,2]
        (should (some #{target} [[1 1] [1 2] [2 1]])))))

  (it "works with fighter unit type over all terrain"
    ;; Fighter can traverse land and sea
    (reset! atoms/game-map (build-test-map ["##~"
                                            "#~~"
                                            "~~~"]))
    (reset! atoms/computer-map (build-test-map ["##~"
                                                "#~~"
                                                "~~-"]))
    (let [target (pathfinding/find-nearest-unexplored [0 0] :fighter)]
      (should-not-be-nil target)
      ;; Should find a cell adjacent to [2,2]
      (should (some #{target} [[1 1] [1 2] [2 1]])))))

(describe "BFS cache behavior"
  (before
    (reset-all-atoms!)
    (pathfinding/clear-path-cache))

  (it "caches unexplored BFS result for same unit-type"
    (reset! atoms/game-map (build-test-map ["~~~"
                                            "~~~"
                                            "~~~"]))
    (reset! atoms/computer-map (build-test-map ["~~~"
                                                "~~~"
                                                "~~-"]))
    ;; Two calls with different starts but same unit-type return same result
    (let [result1 (pathfinding/find-nearest-unexplored [0 0] :transport)
          result2 (pathfinding/find-nearest-unexplored [2 0] :transport)]
      (should-not-be-nil result1)
      (should= result1 result2)))

  (it "caches unload BFS result for same target-continent"
    (reset! atoms/game-map (build-test-map ["~~~"
                                            "~~~"
                                            "~~~"
                                            "###"]))
    (let [target-continent #{[0 3] [1 3] [2 3]}
          result1 (pathfinding/find-nearest-unload-position [0 0] target-continent)
          result2 (pathfinding/find-nearest-unload-position [2 0] target-continent)]
      (should-not-be-nil result1)
      (should= result1 result2)))

  (it "clear-path-cache also clears BFS caches"
    (reset! atoms/game-map (build-test-map ["~~~"
                                            "~~~"
                                            "~~~"]))
    (reset! atoms/computer-map (build-test-map ["~~~"
                                                "~~~"
                                                "~~-"]))
    ;; Populate cache
    (pathfinding/find-nearest-unexplored [0 0] :transport)
    ;; Clear caches
    (pathfinding/clear-path-cache)
    ;; Change the map so a fresh BFS would give a different result
    (reset! atoms/computer-map (build-test-map ["-~~"
                                                "~~~"
                                                "~~~"]))
    ;; After clearing, fresh BFS runs and finds new target
    (let [result (pathfinding/find-nearest-unexplored [1 1] :transport)]
      (should-not-be-nil result)
      ;; Should be adjacent to [0,0] now (the new unexplored cell)
      (should (some #{result} [[0 1] [1 0] [1 1]]))))

  (it "different unit-types get independent cache entries"
    (reset! atoms/game-map (build-test-map ["##~"
                                            "#~~"
                                            "~~~"]))
    (reset! atoms/computer-map (build-test-map ["##~"
                                                "#~~"
                                                "~~-"]))
    ;; Transport can only traverse sea; fighter can traverse all
    (let [transport-result (pathfinding/find-nearest-unexplored [2 0] :transport)
          fighter-result (pathfinding/find-nearest-unexplored [0 0] :fighter)]
      (should-not-be-nil transport-result)
      (should-not-be-nil fighter-result)
      ;; They should be independently cached (may differ since transport
      ;; BFS only covers sea cells while fighter covers all)
      ;; Key assertion: fighter result should not be the transport cached value
      ;; when called from a different start position on land
      (should (some #{fighter-result} [[1 1] [1 2] [2 1]])))))

(describe "find-nearest-unload-position"
  (before (reset-all-atoms!))

  (it "finds nearest sea cell adjacent to target-continent land"
    ;; Two continents separated by sea
    ;; Target continent at rows 4-5
    (reset! atoms/game-map (build-test-map ["###"
                                            "###"
                                            "~~~"
                                            "~~~"
                                            "###"
                                            "###"]))
    (let [target-continent #{[0 4] [1 4] [2 4] [0 5] [1 5] [2 5]}
          result (pathfinding/find-nearest-unload-position [1 2] target-continent)]
      ;; Should find a sea cell in row 3 adjacent to land in row 4
      (should-not-be-nil result)
      (should= 3 (second result))))

  (it "returns nil when target-continent land is unreachable"
    ;; Target continent positions don't exist near any reachable sea
    (reset! atoms/game-map (build-test-map ["###"
                                            "~~~"
                                            "~~~"]))
    (let [target-continent #{[10 10] [11 10]}
          result (pathfinding/find-nearest-unload-position [1 1] target-continent)]
      (should-be-nil result)))

  (it "ignores non-target-continent land"
    ;; Two landmasses: row 0 and row 4. Only row 4 is target.
    ;; BFS should skip row 1 sea (adjacent to non-target row 0 land)
    ;; and find row 3 sea (adjacent to target row 4 land).
    (reset! atoms/game-map (build-test-map ["###"
                                            "~~~"
                                            "~~~"
                                            "~~~"
                                            "###"]))
    (let [target-continent #{[0 4] [1 4] [2 4]}
          result (pathfinding/find-nearest-unload-position [1 1] target-continent)]
      (should-not-be-nil result)
      (should= 3 (second result))))

  (it "skips occupied sea cells as unload destinations"
    ;; Target continent at row 4. Sea cell [1,3] is occupied by enemy ship.
    (reset! atoms/game-map (build-test-map ["###"
                                            "~~~"
                                            "~~~"
                                            "~d~"
                                            "###"]))
    (let [target-continent #{[0 4] [1 4] [2 4]}
          result (pathfinding/find-nearest-unload-position [1 2] target-continent)]
      ;; Should skip [1,3] (occupied) and find [0,3] or [2,3]
      (should-not-be-nil result)
      (should= 3 (second result))
      (should-not= [1 3] result)))

  (it "finds globally nearest position on target continent"
    ;; Target continent connected land at rows 4 and 8 (same continent via land).
    ;; Transport at row 2 - should find row 3 (nearest to row 4 target land).
    (reset! atoms/game-map (build-test-map ["~~~"
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
          result (pathfinding/find-nearest-unload-position [1 2] target-continent)]
      (should-not-be-nil result)
      (should= 3 (second result)))))

(describe "find-nearest-unexplored-coastline"
  (before (reset-all-atoms!))

  (it "finds sea cell at coastal exploration frontier"
    ;; Known land at row 4, unexplored adjacent to it, sea between
    (reset! atoms/game-map (build-test-map ["~~~"
                                            "~~~"
                                            "~~~"
                                            "~~~"
                                            "###"]))
    (reset! atoms/computer-map [[{:type :sea} {:type :sea} {:type :sea} {:type :sea} {:type :land}]
                                [{:type :sea} {:type :sea} {:type :sea} {:type :sea} {:type :land}]
                                [{:type :sea} {:type :sea} {:type :sea} nil nil]])
    ;; [3,2] is sea with known land neighbor [4,1] and unexplored neighbor [4,2]
    ;; But wait, [3,2] itself needs to be a sea cell. Let me verify.
    ;; Actually the frontier cell needs to be adjacent to both unexplored AND known land.
    ;; [3,1] is sea, adjacent to known land [4,1] and adjacent to unexplored [4,2] and [3,2]
    (let [target (pathfinding/find-nearest-unexplored-coastline [0 0] :transport)]
      (should-not-be-nil target)
      ;; Target should be in row 2 or 3 (near the coast)
      (should (>= (second target) 2))))

  (it "returns nil when no coastline frontier exists"
    ;; All unexplored is open sea, no known land nearby
    (reset! atoms/game-map (build-test-map ["~~~"
                                            "~~~"
                                            "~~~"]))
    (reset! atoms/computer-map [[{:type :sea} {:type :sea} {:type :sea}]
                                [{:type :sea} {:type :sea} {:type :sea}]
                                [{:type :sea} {:type :sea} nil]])
    ;; No known land cells at all, so no frontier
    (should-be-nil (pathfinding/find-nearest-unexplored-coastline [0 0] :transport)))

  (it "uses distinct cache key from regular unexplored BFS"
    (pathfinding/clear-path-cache)
    (reset! atoms/game-map (build-test-map ["~~~"
                                            "~~~"
                                            "~~~"
                                            "~~~"
                                            "###"]))
    (reset! atoms/computer-map [[{:type :sea} {:type :sea} {:type :sea} {:type :sea} {:type :land}]
                                [{:type :sea} {:type :sea} {:type :sea} {:type :sea} {:type :land}]
                                [{:type :sea} {:type :sea} {:type :sea} nil nil]])
    ;; Both should return results but use independent cache entries
    (let [coastline (pathfinding/find-nearest-unexplored-coastline [0 0] :transport)
          general (pathfinding/find-nearest-unexplored [0 0] :transport)]
      (should-not-be-nil coastline)
      (should-not-be-nil general)))

  (it "returns cached result on second call with same unit-type"
    (pathfinding/clear-path-cache)
    (reset! atoms/game-map (build-test-map ["~~~"
                                            "~~~"
                                            "~~~"
                                            "~~~"
                                            "###"]))
    (reset! atoms/computer-map [[{:type :sea} {:type :sea} {:type :sea} {:type :sea} {:type :land}]
                                [{:type :sea} {:type :sea} {:type :sea} {:type :sea} {:type :land}]
                                [{:type :sea} {:type :sea} {:type :sea} nil nil]])
    (let [result1 (pathfinding/find-nearest-unexplored-coastline [0 0] :transport)
          result2 (pathfinding/find-nearest-unexplored-coastline [2 2] :transport)]
      (should-not-be-nil result1)
      (should= result1 result2))))
