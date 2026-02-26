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

(describe "mutation-killing tests"
  (before
    (reset-all-atoms!)
    (pathfinding/clear-path-cache))

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
        (should= [3 0] step)))))
