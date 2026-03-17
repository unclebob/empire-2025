(ns empire.game-mechanics.movement.pathfinding-bfs-coast-spec
  (:require [speclj.core :refer :all]
            [empire.game-mechanics.movement.pathfinding-bfs :as pathfinding-bfs]
            [empire.game-mechanics.movement.map-utils :as map-utils]
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
                         [{:type :land :country-id 1}]]]
      (should-be-nil (pathfinding-bfs/bfs-to-unowned-coast [0 0] computer-map game-map))))

  (it "returns nil when start is not sea"
    (let [game-map [[{:type :land}] [{:type :sea}] [{:type :city :city-status :free}]]
          computer-map [[{:type :land}] [{:type :sea}] [{:type :city :city-status :free}]]]
      (should-be-nil (pathfinding-bfs/bfs-to-unowned-coast [0 0] computer-map game-map))))

  (it "returns nil when no unowned land reachable"
    (let [game-map [[{:type :sea}] [{:type :sea}] [{:type :sea}]]
          computer-map [[{:type :sea}] [{:type :sea}] [{:type :sea}]]]
      (should-be-nil (pathfinding-bfs/bfs-to-unowned-coast [0 0] computer-map game-map))))

  (it "does not target coast revealed only on game-map"
    (let [game-map [[{:type :sea}] [{:type :sea}]
                    [{:type :city :city-status :free}]]
          computer-map [[{:type :sea}] [{:type :sea}] [nil]]]
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
