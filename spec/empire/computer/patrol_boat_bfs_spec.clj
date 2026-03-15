(ns empire.computer.patrol-boat-bfs-spec
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.computer.ship :as ship]
            [empire.game-mechanics.movement.pathfinding-bfs :as pathfinding-bfs]
            [empire.test.utils :as tu]))
(describe "bfs-to-unseen-coast"
  (before (tu/reset-all-atoms!))

  (it "finds path to unseen coastal cell"
    (let [game-map (tu/build-test-map ["~~~~~~~#"
                                       "~~~~~~~#"
                                       "~~~~~~~#"])]
      (tu/set-test-world! game-map)
      (let [path (pathfinding-bfs/bfs-to-unseen-coast [0 0] game-map #{})]
        (should-not-be-nil path)
        (should (pos? (count path)))
        (let [target (last path)]
          (should= :sea (:type (get-in game-map target)))))))

  (it "returns nil when no coast is reachable"
    (let [game-map (tu/build-test-map ["~~~"
                                       "~~~"
                                       "~~~"])]
      (tu/set-test-world! game-map)
      (should-be-nil (pathfinding-bfs/bfs-to-unseen-coast [0 0] game-map #{}))))

  (it "excludes cells already in seen-coast"
    (let [game-map (tu/build-test-map ["~~#"
                                       "~~#"
                                       "~~#"])]
      (tu/set-test-world! game-map)
      (test-utils/set-test-state! :seen-coast #{[1 0] [1 1] [1 2]})
      (should-be-nil (pathfinding-bfs/bfs-to-unseen-coast [0 0] game-map #{}))))

  (it "skips targets within min-distance of 4 levels"
    (let [game-map (tu/build-test-map ["~#~~~~~~~~~~~#"
                                       "~#~~~~~~~~~~~#"
                                       "~#~~~~~~~~~~~#"])]
      (tu/set-test-world! game-map)
      (let [path (pathfinding-bfs/bfs-to-unseen-coast [2 0] game-map #{})
            target (last path)]
        (should-not-be-nil path)
        (should (>= (first target) 10)))))

  (it "prefers unseen coast over unexplored territory"
    (let [computer-map (tu/build-test-map ["~~~~~~~~~~#"
                                           "~~~~~.~~~~#"
                                           "~~~~~~~~~~#"])]
      (let [path (pathfinding-bfs/bfs-to-unseen-coast [0 0] computer-map #{})
            target (last path)]
        (should-not-be-nil path)
        (should (>= (first target) 8)))))

  (it "skips targets in excluded set"
    (let [game-map (tu/build-test-map ["~~~~~~~#"
                                       "~~~~~~~#"
                                       "~~~~~~~#"])]
      (tu/set-test-world! game-map)
      ;; Find the natural target first
      (let [path1 (pathfinding-bfs/bfs-to-unseen-coast [0 0] game-map #{})
            target1 (last path1)]
        (should-not-be-nil path1)
        ;; Exclude that target — should find a different one
        (let [path2 (pathfinding-bfs/bfs-to-unseen-coast [0 0] game-map #{target1})]
          (should-not-be-nil path2)
          (should-not= target1 (last path2)))))))
