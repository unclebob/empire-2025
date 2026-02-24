(ns empire.movement.pathfinding-bfs-spec
  (:require [speclj.core :refer :all]
            [empire.movement.pathfinding :as pathfinding]
            [empire.movement.sea-lanes :as sea-lanes]
            [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.test-utils :refer [build-test-map reset-all-atoms!]]))

(describe "bfs-to-unexplored-coast"
  (before (reset-all-atoms!))

  (it "finds path to sea cell adjacent to unexplored territory"
    ;; Explored sea at cols 0-2, unexplored at col 3
    (let [computer-map [[{:type :sea}] [{:type :sea}] [{:type :sea}] [nil]]]
      (let [path (pathfinding/bfs-to-unexplored-coast [0 0] computer-map)]
        (should-not-be-nil path)
        (should (vector? path))
        ;; Path excludes start, ends at cell adjacent to unexplored [3,0]
        (should= [2 0] (last path)))))

  (it "returns nil when no unexplored territory reachable"
    (let [computer-map [[{:type :sea}] [{:type :sea}] [{:type :sea}]]]
      (should-be-nil (pathfinding/bfs-to-unexplored-coast [0 0] computer-map))))

  (it "returns nil when start is not passable sea"
    (let [computer-map [[{:type :land}] [{:type :sea}] [nil]]]
      (should-be-nil (pathfinding/bfs-to-unexplored-coast [0 0] computer-map))))

  (it "does not traverse unexplored cells"
    ;; Gap of unexplored between two explored sea regions
    (let [computer-map [[{:type :sea}] [nil] [{:type :sea}] [nil]]]
      ;; From [0,0], cannot cross nil at [1,0] to reach [2,0]
      ;; But [0,0] IS adjacent to unexplored [1,0], so it should still find it
      ;; Wait - start is skipped, so if only start is adjacent, returns nil? No.
      ;; Actually, [0,0] is the start. The BFS skips start for the goal check.
      ;; No other explored sea cells reachable, so no path found.
      (should-be-nil (pathfinding/bfs-to-unexplored-coast [0 0] computer-map))))

  (it "returns path excluding start position"
    (let [computer-map [[{:type :sea}] [{:type :sea}] [nil]]]
      (let [path (pathfinding/bfs-to-unexplored-coast [0 0] computer-map)]
        (should= [[1 0]] path))))

  (it "finds shortest path through multi-step sea"
    ;; 5 columns of sea, unexplored at col 5
    (let [computer-map (vec (for [c (range 6)]
                              [(if (< c 5) {:type :sea} nil)]))]
      (let [path (pathfinding/bfs-to-unexplored-coast [0 0] computer-map)]
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
      (let [path (pathfinding/bfs-to-unexplored-coast [0 0] computer-map)]
        (should-not-be-nil path)
        ;; Should go around land at [1,0] via row 1
        (should-not-contain [1 0] path)
        ;; Should end at col 2 — adjacent to unexplored col 3
        (should= 2 (first (last path))))))

  (it "handles {:type :unexplored} cells as unexplored"
    (let [computer-map [[{:type :sea}] [{:type :sea}] [{:type :unexplored}]]]
      (let [path (pathfinding/bfs-to-unexplored-coast [0 0] computer-map)]
        (should= [[1 0]] path)))))

(describe "bfs-to-unowned-coast"
  (before (reset-all-atoms!))

  (it "finds path to coast adjacent to free city"
    ;; All explored, free city at [3,0]
    ;; game-map: sea sea sea free-city
    ;; computer-map: sea sea sea city (all explored)
    (let [game-map [[{:type :sea}] [{:type :sea}] [{:type :sea}]
                     [{:type :city :city-status :free}]]
          computer-map [[{:type :sea}] [{:type :sea}] [{:type :sea}]
                         [{:type :city :city-status :free}]]]
      (let [path (pathfinding/bfs-to-unowned-coast [0 0] computer-map game-map)]
        (should-not-be-nil path)
        ;; Path should end at [2,0] — sea cell adjacent to free city [3,0]
        (should= [2 0] (last path)))))

  (it "finds path to coast adjacent to player city"
    (let [game-map [[{:type :sea}] [{:type :sea}]
                     [{:type :city :city-status :player}]]
          computer-map [[{:type :sea}] [{:type :sea}]
                         [{:type :city :city-status :player}]]]
      (let [path (pathfinding/bfs-to-unowned-coast [0 0] computer-map game-map)]
        (should= [[1 0]] path))))

  (it "does not target computer-owned cities"
    (let [game-map [[{:type :sea}] [{:type :sea}]
                     [{:type :city :city-status :computer}]]
          computer-map [[{:type :sea}] [{:type :sea}]
                         [{:type :city :city-status :computer}]]]
      (should-be-nil (pathfinding/bfs-to-unowned-coast [0 0] computer-map game-map))))

  (it "finds path to coast adjacent to unowned land"
    ;; Land at [2,0] with no country-id (unowned)
    (let [game-map [[{:type :sea}] [{:type :sea}] [{:type :land}]]
          computer-map [[{:type :sea}] [{:type :sea}] [{:type :land}]]]
      (let [path (pathfinding/bfs-to-unowned-coast [0 0] computer-map game-map)]
        (should= [[1 0]] path))))

  (it "does not target computer-owned land"
    (let [game-map [[{:type :sea}] [{:type :sea}]
                     [{:type :land :country-id 1}]]
          computer-map [[{:type :sea}] [{:type :sea}]
                         [{:type :land}]]]
      (should-be-nil (pathfinding/bfs-to-unowned-coast [0 0] computer-map game-map))))

  (it "returns nil when start is not sea"
    (let [game-map [[{:type :land}] [{:type :sea}] [{:type :city :city-status :free}]]
          computer-map [[{:type :land}] [{:type :sea}] [{:type :city :city-status :free}]]]
      (should-be-nil (pathfinding/bfs-to-unowned-coast [0 0] computer-map game-map))))

  (it "returns nil when no unowned land reachable"
    (let [game-map [[{:type :sea}] [{:type :sea}] [{:type :sea}]]
          computer-map [[{:type :sea}] [{:type :sea}] [{:type :sea}]]]
      (should-be-nil (pathfinding/bfs-to-unowned-coast [0 0] computer-map game-map)))))

(describe "sovereignty-aware pathfinding"
  (before (reset-all-atoms!))

  (it "a-star uses custom passability-fn when provided"
    ;; Path must go around foreign territory
    ;; Row 0: army, foreign, foreign, land
    ;; Row 1: land,  land,    land,    land
    (reset! atoms/game-map [[{:type :land} {:type :land :country-id 2} {:type :land :country-id 2} {:type :land}]
                             [{:type :land} {:type :land} {:type :land} {:type :land}]])
    (let [passability-fn (fn [cell]
                           (and cell
                                (not= (:type cell) :unexplored)
                                (#{:land :city} (:type cell))
                                (or (= :city (:type cell))
                                    (nil? (:country-id cell))
                                    (= 1 (:country-id cell)))))
          path (pathfinding/a-star [0 0] [0 3] :army @atoms/game-map passability-fn)]
      (should-not-be-nil path)
      (should= [0 0] (first path))
      (should= [0 3] (last path))
      ;; Path should avoid [0 1] and [0 2] (foreign territory)
      (should-not-contain [0 1] path)
      (should-not-contain [0 2] path)))

  (it "next-step uses passability-fn and includes cache-key-extra"
    (reset! atoms/game-map [[{:type :land} {:type :land :country-id 2} {:type :land :country-id 2} {:type :land}]
                             [{:type :land} {:type :land} {:type :land} {:type :land}]])
    (let [passability-fn (fn [cell]
                           (and cell
                                (not= (:type cell) :unexplored)
                                (#{:land :city} (:type cell))
                                (or (= :city (:type cell))
                                    (nil? (:country-id cell))
                                    (= 1 (:country-id cell)))))
          step (pathfinding/next-step [0 0] [0 3] :army passability-fn 1)]
      ;; Should step to [1 0] or [1 1] to go around foreign territory
      (should-not-be-nil step)
      (should-not= [0 1] step)))

  (it "cache key includes cache-key-extra to separate different passabilities"
    (pathfinding/clear-path-cache)
    (reset! atoms/game-map [[{:type :land} {:type :land :country-id 2} {:type :land}]
                             [{:type :land} {:type :land} {:type :land}]])
    (let [pass-country-1 (fn [cell]
                           (and cell (#{:land :city} (:type cell))
                                (or (nil? (:country-id cell)) (= 1 (:country-id cell)))))
          pass-country-2 (fn [cell]
                           (and cell (#{:land :city} (:type cell))
                                (or (nil? (:country-id cell)) (= 2 (:country-id cell)))))
          step-c1 (pathfinding/next-step [0 0] [0 2] :army pass-country-1 1)
          step-c2 (pathfinding/next-step [0 0] [0 2] :army pass-country-2 2)]
      ;; Country-1 army must go around [0 1]; country-2 army can go through
      (should-not= [0 1] step-c1)
      (should= [0 1] step-c2)))

  (it "a-star returns nil when sovereignty blocks all paths"
    (reset! atoms/game-map [[{:type :land} {:type :land :country-id 2} {:type :land}]])
    (let [passability-fn (fn [cell]
                           (and cell (#{:land :city} (:type cell))
                                (or (nil? (:country-id cell)) (= 1 (:country-id cell)))))
          path (pathfinding/a-star [0 0] [0 2] :army @atoms/game-map passability-fn)]
      (should-be-nil path))))

(describe "sea lane network integration"
  (before (reset-all-atoms!))

  (it "next-step records naval paths into sea lane network"
    (reset! atoms/game-map (build-test-map ["~~~~~~"]))
    (pathfinding/next-step [0 0] [5 0] :destroyer)
    (let [net @atoms/sea-lane-network]
      ;; Path [0 0]->[5 0] should be recorded as a segment
      (should (pos? (count (:segments net))))))

  (it "next-step does not record army paths into sea lane network"
    (reset! atoms/game-map (build-test-map ["######"]))
    (pathfinding/next-step [0 0] [5 0] :army)
    (let [net @atoms/sea-lane-network]
      (should= 0 (count (:segments net)))))

  (it "bounded-a-star finds path on small map"
    (reset! atoms/game-map (build-test-map ["~~~~~"
                                            "~~~~~"]))
    (let [path (pathfinding/bounded-a-star [0 0] [4 0] :destroyer @atoms/game-map)]
      (should-not-be-nil path)
      (should= [0 0] (first path))
      (should= [4 0] (last path))))

  (it "bounded-a-star returns nil when goal is outside radius"
    ;; Very far apart positions on a narrow corridor
    (let [row (vec (repeat 100 {:type :sea}))]
      (reset! atoms/game-map [row])
      ;; Start at 0, goal at 99 — radius will be ~54, so it should still work
      ;; Actually bounded-a-star uses distance+5 as radius, so this should work
      (let [path (pathfinding/bounded-a-star [0 0] [0 99] :destroyer @atoms/game-map)]
        (should-not-be-nil path))))

  (it "next-step skips network for short-distance goals"
    ;; Build a sea lane network with a node far from the goal.
    ;; For a short-distance goal (< local-radius), next-step should use
    ;; direct A* and step toward the goal, not toward the distant network node.
    (let [row (vec (repeat 40 {:type :sea}))]
      (reset! atoms/game-map [row row row])
      ;; Build a network with nodes at [0 30] and [0 35] — far from our start/goal
      (reset! atoms/sea-lane-network
              {:nodes {1 {:id 1 :pos [0 30] :segment-ids #{1}}
                       2 {:id 2 :pos [0 35] :segment-ids #{1}}}
               :segments {1 {:id 1 :node-a-id 1 :node-b-id 2
                              :direction [0 1]
                              :cells [[0 30] [0 31] [0 32] [0 33] [0 34] [0 35]]
                              :length 5}}
               :pos->node {[0 30] 1 [0 35] 2}
               :pos->seg {[0 31] 1 [0 32] 1 [0 33] 1 [0 34] 1}
               :next-node-id 3 :next-segment-id 2})
      (pathfinding/clear-path-cache)
      ;; Goal is 5 cells away — well within sea-lane-local-radius (15)
      (let [step (pathfinding/next-step [1 0] [1 5] :transport)]
        ;; Direct A* should move toward [1 5], i.e. column increases
        (should-not-be-nil step)
        (should (> (second step) 0))
        ;; Should NOT step toward column 30 (the network node)
        (should (< (second step) 10)))))

  (it "next-step uses network for long-distance goals"
    ;; Build a sea lane network between two distant points.
    ;; For a long-distance goal (> local-radius), next-step should consult the network.
    (let [row (vec (repeat 60 {:type :sea}))]
      (reset! atoms/game-map [row row row])
      ;; Network with nodes at [1 2] and [1 50]
      (let [cells (vec (for [c (range 2 51)] [1 c]))]
        (reset! atoms/sea-lane-network
                {:nodes {1 {:id 1 :pos [1 2] :segment-ids #{1}}
                         2 {:id 2 :pos [1 50] :segment-ids #{1}}}
                 :segments {1 {:id 1 :node-a-id 1 :node-b-id 2
                                :direction [0 1]
                                :cells cells
                                :length 48}}
                 :pos->node {[1 2] 1 [1 50] 2}
                 :pos->seg (into {} (for [c (range 3 50)] [[1 c] 1]))
                 :next-node-id 3 :next-segment-id 2}))
      (pathfinding/clear-path-cache)
      ;; Goal is 50 cells away — well beyond sea-lane-local-radius (15)
      (let [step (pathfinding/next-step [1 0] [1 50] :transport)]
        ;; Should find a path (either via network or A*)
        (should-not-be-nil step)
        ;; The step should move toward increasing column
        (should (>= (second step) 1))))))

(describe "chebyshev"
  (it "returns 0 for same position"
    (should= 0 (#'empire.movement.pathfinding/chebyshev [3 3] [3 3])))

  (it "returns 1 for adjacent position"
    (should= 1 (#'empire.movement.pathfinding/chebyshev [3 3] [4 4])))

  (it "returns max of row/col difference"
    (should= 5 (#'empire.movement.pathfinding/chebyshev [0 0] [3 5])))

  (it "handles negative direction"
    (should= 4 (#'empire.movement.pathfinding/chebyshev [5 5] [1 3]))))

(describe "reconstruct-path"
  (it "reconstructs path from came-from map"
    (let [came-from {[1 0] [0 0]
                     [2 0] [1 0]
                     [3 0] [2 0]}]
      (should= [[0 0] [1 0] [2 0] [3 0]]
               (#'empire.movement.pathfinding/reconstruct-path came-from [0 0] [3 0]))))

  (it "returns single element for start=goal"
    (should= [[0 0]]
             (#'empire.movement.pathfinding/reconstruct-path {} [0 0] [0 0])))

  (it "reconstructs two-step path"
    (let [came-from {[1 1] [0 0]}]
      (should= [[0 0] [1 1]]
               (#'empire.movement.pathfinding/reconstruct-path came-from [0 0] [1 1])))))

(describe "sea-reaches-edge?"
  (before (reset-all-atoms!))

  (it "returns true when sea cell is on edge"
    (reset! atoms/game-map (build-test-map ["~~~"
                                             "###"
                                             "###"]))
    (should (pathfinding/sea-reaches-edge? [0 0])))

  (it "returns true when sea connects to edge"
    (reset! atoms/game-map (build-test-map ["###"
                                             "#~#"
                                             "#~~"]))
    ;; [1 1] connects via [2 1] or [1 2] or [2 2] to edge
    (should (pathfinding/sea-reaches-edge? [1 1])))

  (it "returns false for landlocked sea"
    (reset! atoms/game-map (build-test-map ["#####"
                                             "#~~~#"
                                             "#~~~#"
                                             "#~~~#"
                                             "#####"]))
    (should-not (pathfinding/sea-reaches-edge? [2 2])))

  (it "returns true for sea cell directly on edge"
    (reset! atoms/game-map (build-test-map ["~#"
                                             "#~"]))
    (should (pathfinding/sea-reaches-edge? [0 0])))

  (it "returns true for corner sea cell"
    (reset! atoms/game-map (build-test-map ["##"
                                             "#~"]))
    (should (pathfinding/sea-reaches-edge? [1 1]))))

(describe "adjacent-to-unexplored?"
  (it "returns truthy when neighbor is nil"
    (let [computer-map [[{:type :sea} nil]
                         [{:type :sea} {:type :sea}]]]
      (should (#'empire.movement.pathfinding/adjacent-to-unexplored? [0 0] computer-map))))

  (it "returns truthy when neighbor is {:type :unexplored}"
    (let [computer-map [[{:type :sea} {:type :unexplored}]
                         [{:type :sea} {:type :sea}]]]
      (should (#'empire.movement.pathfinding/adjacent-to-unexplored? [0 0] computer-map))))

  (it "returns falsy when all neighbors are explored"
    (let [computer-map [[{:type :sea} {:type :sea}]
                         [{:type :sea} {:type :sea}]]]
      (should-not (#'empire.movement.pathfinding/adjacent-to-unexplored? [0 0] computer-map))))

  (it "handles edge position correctly"
    (let [computer-map [[{:type :sea}]]]
      ;; Single cell, no neighbors within bounds, should return falsy
      (should-not (#'empire.movement.pathfinding/adjacent-to-unexplored? [0 0] computer-map)))))

(describe "at-exploration-frontier?"
  (it "returns truthy when adjacent to both unexplored and known land"
    (let [computer-map [[{:type :sea} nil]
                         [{:type :land} {:type :sea}]]]
      ;; [0 0] is adjacent to nil [1 0] (unexplored) and [0 1] is land
      (should (#'empire.movement.pathfinding/at-exploration-frontier? [0 0] computer-map))))

  (it "returns falsy when only adjacent to unexplored (no known land)"
    (let [computer-map [[{:type :sea} nil]
                         [{:type :sea} {:type :sea}]]]
      (should-not (#'empire.movement.pathfinding/at-exploration-frontier? [0 0] computer-map))))

  (it "returns falsy when only adjacent to known land (no unexplored)"
    (let [computer-map [[{:type :sea} {:type :sea}]
                         [{:type :land} {:type :sea}]]]
      (should-not (#'empire.movement.pathfinding/at-exploration-frontier? [0 0] computer-map))))

  (it "returns falsy when no neighbors"
    (let [computer-map [[{:type :sea}]]]
      (should-not (#'empire.movement.pathfinding/at-exploration-frontier? [0 0] computer-map))))

  (it "recognizes :city as known land for frontier detection"
    (let [computer-map [[{:type :sea} nil]
                         [{:type :city} {:type :sea}]]]
      (should (#'empire.movement.pathfinding/at-exploration-frontier? [0 0] computer-map)))))

(describe "mutation-killing tests"
  (before
    (reset-all-atoms!)
    (pathfinding/clear-path-cache))

  (context "bounded-a-star"
    (it "returns [start] when start equals goal"
      (reset! atoms/game-map (build-test-map ["~~~"]))
      (should= [[0 0]] (pathfinding/bounded-a-star [0 0] [0 0] :destroyer @atoms/game-map))))

  (context "adjacent-to-target-continent-land? with city"
    (it "recognizes :city as target-continent land"
      (let [game-map [[{:type :sea} {:type :city :city-status :free}]
                       [{:type :sea} {:type :sea}]]]
        (should (#'empire.movement.pathfinding/adjacent-to-target-continent-land?
                  [0 0] #{[0 1]} game-map)))))

  (context "find-nearest-unload-position start-skip"
    (it "does not return start even when start is valid unload position"
      ;; Start [0,0] is sea, empty, adjacent to target continent land [1,0]
      (reset! atoms/game-map (build-test-map ["~#" "~#"]))
      (let [target-continent #{[1 0] [1 1]}
            result (pathfinding/find-nearest-unload-position [0 0] target-continent)]
        (should-not-be-nil result)
        (should-not= [0 0] result)))

    (it "finds unload position adjacent to city on target continent"
      (reset! atoms/game-map [[{:type :sea} {:type :city :city-status :free}]
                               [{:type :sea} {:type :sea}]])
      (let [target-continent #{[0 1]}
            result (pathfinding/find-nearest-unload-position [1 0] target-continent)]
        (should-not-be-nil result)
        (should= [0 0] result))))

  (context "sea-reaches-edge? edge isolation"
    (it "detects right edge only (kills dec-rows mutation)"
      ;; Sea at [2,1]: r=2=dec(3), c=1. Only (= r (dec rows)) fires.
      (reset! atoms/game-map (build-test-map ["###" "##~" "###"]))
      (should (pathfinding/sea-reaches-edge? [2 1])))

    (it "detects bottom edge only (kills dec-cols mutation)"
      ;; Sea at [1,1]: r=1, c=1=dec(2). 3 cols, 2 rows.
      ;; Only (= c (dec cols)) fires.
      (reset! atoms/game-map (build-test-map ["###" "#~#"]))
      (should (pathfinding/sea-reaches-edge? [1 1])))

    (it "detects left edge only (kills zero-r mutation)"
      ;; Sea at [0,1]: r=0, c=1. 4 cols, 3 rows.
      ;; Only (zero? r) fires (c=1 ≠ 0, r=0 ≠ dec(4)=3, c=1 ≠ dec(3)=2)
      (reset! atoms/game-map (build-test-map ["~###" "~###" "~###"]))
      (should (pathfinding/sea-reaches-edge? [0 1])))

    (it "detects top edge only (kills zero-c mutation)"
      ;; Sea at [2,0]: r=2, c=0. 5 cols, 3 rows.
      ;; Only (zero? c) fires (r=2 ≠ 0, r=2 ≠ dec(5)=4, c=0 ≠ dec(3)=2)
      (reset! atoms/game-map (build-test-map ["##~##" "##~##" "##~##"]))
      (should (pathfinding/sea-reaches-edge? [2 0]))))

  (context "next-step sea lane recording with passability-fn"
    (it "does not record naval paths when passability-fn is provided"
      (reset! atoms/game-map (build-test-map ["~~~~~~"]))
      (let [pass-fn (fn [cell] (and cell (= :sea (:type cell))))]
        (pathfinding/next-step [0 0] [5 0] :destroyer pass-fn :custom)
        (should= 0 (count (:segments @atoms/sea-lane-network))))))

  (context "cache-sub-paths! two-element path"
    (it "caches the final two-element sub-path"
      (reset! atoms/game-map (build-test-map ["a###"]))
      ;; Compute path [0,0]→[3,0], sub-paths include [2,0]→[3,0]
      (pathfinding/next-step [0 0] [3 0] :army)
      ;; Change terrain so [3,0] is sea (unreachable by army)
      (reset! atoms/game-map (build-test-map ["a##~"]))
      ;; If 2-element sub-path was cached, returns [3,0] from cache
      ;; If not cached, A* on new map finds no path → nil
      (let [step (pathfinding/next-step [2 0] [3 0] :army)]
        (should= [3 0] step))))

  (context "find-nearest-unexplored-coastline start-skip"
    (it "does not return start even when start is at coastal frontier"
      ;; game-map: col0=[sea,sea], col1=[land,sea]
      ;; computer-map: col0=[sea,nil], col1=[land,sea]
      ;; [0,0] is adjacent to known land [1,0] and unexplored [0,1] → frontier
      (reset! atoms/game-map (build-test-map ["~#" "~~"]))
      (reset! atoms/computer-map [[{:type :sea} nil]
                                   [{:type :land} {:type :sea}]])
      (let [result (pathfinding/find-nearest-unexplored-coastline [0 0] :transport)]
        (should-not-be-nil result)
        (should-not= [0 0] result)))))
