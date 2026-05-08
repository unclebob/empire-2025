(ns empire.game-mechanics.movement.ray-pathfinding-spec
  (:require [empire.game-mechanics.movement.ray-pathfinding :as ray]
            [empire.test.utils :refer [reset-all-atoms! build-test-map set-test-world!]]
            [empire.test.utils :as test-utils]
            [empire.state.api :as sa]
            [empire.game.initialization :as init]
            [speclj.core :refer :all]))

(describe "ray-clear?"
  (it "returns true for open water ray"
    (let [game-map (build-test-map ["~~~~~"])]
      (should (ray/ray-clear? game-map [0 0] [4 0]))))

  (it "returns false when ray hits land"
    (let [game-map (build-test-map ["~~#~~"])]
      (should-not (ray/ray-clear? game-map [0 0] [4 0]))))

  (it "returns true for zero-length ray"
    (let [game-map (build-test-map ["~"])]
      (should (ray/ray-clear? game-map [0 0] [0 0])))))

(describe "bresenham-line"
  (it "returns cells along horizontal line"
    (should= [[0 0] [1 0] [2 0] [3 0]] (ray/bresenham-line [0 0] [3 0])))

  (it "returns cells along vertical line"
    (should= [[0 0] [0 1] [0 2]] (ray/bresenham-line [0 0] [0 2])))

  (it "returns cells along diagonal"
    (should= [[0 0] [1 1] [2 2]] (ray/bresenham-line [0 0] [2 2]))))

(describe "find-sea-path"
  (before (reset-all-atoms!))

  (it "returns an empty path when already at target"
    (let [game-map (build-test-map ["~"])]
      (set-test-world! game-map)
      (should= [] (ray/find-sea-path [0 0] [0 0]))))

  (it "returns straight line for open water"
    (let [game-map (build-test-map ["~~~~~"
                                    "~~~~~"])]
      (set-test-world! game-map)
      (sa/write-state! :coastal-index (init/build-coastal-index game-map))
      (let [path (ray/find-sea-path [0 0] [4 0])]
        (should-not-be-nil path)
        (should= [4 0] (last path)))))

  (it "routes around a single island"
    (let [game-map (build-test-map ["~~~~~"
                                    "~~#~~"
                                    "~~~~~"])]
      (set-test-world! game-map)
      (sa/write-state! :coastal-index (init/build-coastal-index game-map))
      (let [path (ray/find-sea-path [0 0] [4 0])]
        (should-not-be-nil path)
        (should= [4 0] (last path))
        (should (every? (fn [pos] (= :sea (:type (get-in game-map pos)))) path)))))

  (it "routes around a wall"
    (let [game-map (build-test-map ["~~~~~"
                                    "~###~"
                                    "~~~~~"])]
      (set-test-world! game-map)
      (sa/write-state! :coastal-index (init/build-coastal-index game-map))
      (let [path (ray/find-sea-path [0 0] [4 0])]
        (should-not-be-nil path)
        (should= [4 0] (last path))
        (should (every? (fn [pos] (= :sea (:type (get-in game-map pos)))) path)))))

  (it "falls back to BFS for complex geography"
    ;; Maze that requires more than 4 rays
    (let [game-map (build-test-map ["~~#~~#~~"
                                    "~##~~##~"
                                    "~~#~~#~~"
                                    "~~####~~"
                                    "~~~~~~~~"])]
      (set-test-world! game-map)
      (sa/write-state! :coastal-index (init/build-coastal-index game-map))
      (let [path (ray/find-sea-path [0 0] [7 0])]
        ;; Should still find a path via BFS fallback
        (should-not-be-nil path)
        (should= [7 0] (last path)))))

  (it "returns nil when no path exists"
    (let [game-map [[{:type :sea}  {:type :land} {:type :sea}]
                    [{:type :land} {:type :land} {:type :land}]
                    [{:type :sea}  {:type :land} {:type :sea}]]]
      (set-test-world! game-map)
      (sa/write-state! :coastal-index (init/build-coastal-index game-map))
      (should-be-nil (ray/find-sea-path [0 0] [2 0])))))

(describe "ray-crawl continuation"
  (it "returns the exit cell and path pieces when a coastal crawl clears the target ray"
    (let [game-map [[{:type :sea}  {:type :sea}]
                    [{:type :sea}  {:type :sea}]
                    [{:type :land} {:type :sea}]
                    [{:type :sea}  {:type :sea}]
                    [{:type :sea}  {:type :sea}]]
          continuation (@#'ray/ray-crawl-continuation game-map
                                                       #{[1 0]}
                                                       {[1 0] #{[1 1]}}
                                                       [0 0]
                                                       [4 0]
                                                       4)]
      (should= [1 1] (:exit-cell continuation))
      (should= [[1 0]] (:ray-to-coast continuation))
      (should= [[1 1]] (:coast-path continuation))))

  (it "appends ray-to-coast before the rest of the coast path"
    (should= [[1 0] [1 1] [2 1]]
             (@#'ray/append-ray-crawl-continuation []
                                                   {:ray-to-coast [[1 0]]
                                                    :coast-path [[1 0] [1 1] [2 1]]})))

  (it "returns the target when the ray-crawl loop is already there"
    (let [game-map [[{:type :sea}]]]
      (should= [[0 0]]
               (@#'ray/ray-crawl-path game-map
                                      {:coastal-sea-cells #{}
                                       :coastal-sea-neighbors {}}
                                      [0 0]
                                      [0 0]
                                      4))))

  (it "continues from a coastal crawl exit to finish the route"
    (let [game-map [[{:type :sea}  {:type :sea}]
                    [{:type :sea}  {:type :sea}]
                    [{:type :land} {:type :sea}]
                    [{:type :sea}  {:type :sea}]
                    [{:type :sea}  {:type :sea}]]
          coastal-index {:coastal-sea-cells #{[1 0]}
                         :coastal-sea-neighbors {[1 0] #{[1 1]}}}]
      (should= [[1 0] [2 1] [3 0] [4 0]]
               (@#'ray/ray-crawl-path game-map coastal-index [0 0] [4 0] 4)))))
