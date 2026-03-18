(ns empire.computer.patrol-boat-explore-spec
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.computer.ship :as ship]
            [empire.game-mechanics.movement.pathfinding-bfs :as pathfinding-bfs]
            [empire.test.utils :as tu]))
(describe "patrol-explore-step"
  (before (tu/reset-all-atoms!))

  (it "moves toward unseen coast via BFS"
    ;; Patrol boat in open sea, coast is to the right
    (let [game-map (tu/build-test-map ["~~~~~~~#"
                                       "~p~~~~~#"
                                       "~~~~~~~#"])]
      (tu/set-test-world! game-map)
      (tu/set-test-computer-map! game-map)
      (let [result (ship/patrol-explore-step [1 1])]
        (should-not-be-nil result)
        ;; Should move closer to the coast (col increases)
        (should (> (first result) 1)))))

  (it "switches to crawling when arriving at unseen coast"
    ;; Nearby land at [3,1] and distant land at col 11.
    ;; BFS skips nearby coast (depth < 4), targets distant coast.
    ;; move-toward moves boat to [2,0], adjacent to [3,1] land.
    ;; arrived-at-unseen-coast? triggers mode switch to :crawling.
    (let [game-map (tu/build-test-map ["~~~~~~~~~~~#"
                                       "~p~#~~~~~~~#"
                                       "~~~~~~~~~~~#"])]
      (tu/set-test-world! game-map)
      (tu/set-test-computer-map! game-map)
      (let [result (ship/patrol-explore-step [1 1])]
        (should-not-be-nil result)
        (let [unit (get-in (test-utils/read-test-state :game-map) (conj result :contents))]
          (should= :crawling (:patrol-mode unit))))))

  (it "random walks when BFS finds no target"
    (let [game-map (tu/build-test-map ["~~~"
                                       "~p~"
                                       "~~~"])]
      (tu/set-test-world! game-map)
      (tu/set-test-computer-map! game-map)
      ;; All sea, no coast — falls back to random walk
      (with-redefs [rand-nth first]
        (let [result (ship/patrol-explore-step [1 1])]
          (should-not-be-nil result)
          (should-not= [1 1] result)))))

  (it "stores BFS path on unit and follows it step by step"
    (let [game-map (tu/build-test-map ["~~~~~~~#"
                                       "~p~~~~~#"
                                       "~~~~~~~#"])]
      (tu/set-test-world! game-map)
      (tu/set-test-computer-map! game-map)
      (tu/set-test-unit (test-utils/game-map-atom) "p" :patrol-mode :exploring)
      (let [result (ship/patrol-explore-step [1 1])
            unit (get-in (test-utils/read-test-state :game-map) (conj result :contents))]
        (should-not-be-nil result)
        ;; Should store remaining path (not just target)
        (should-not-be-nil (:explore-path unit))
        (should (vector? (:explore-path unit))))))

  (it "follows stored path without re-running BFS"
    (let [game-map (tu/build-test-map ["~~~~~~~#"
                                       "~p~~~~~#"
                                       "~~~~~~~#"])]
      (tu/set-test-world! game-map)
      (tu/set-test-computer-map! game-map)
      (tu/set-test-unit (test-utils/game-map-atom) "p" :patrol-mode :exploring)
      ;; Pre-store a path on the unit
      (tu/update-test-world! assoc-in [1 1 :contents :explore-path]
             [[2 1] [3 1] [4 1]])
      (tu/update-test-state! :computer-map assoc-in [1 1 :contents :patrol-mode] :exploring)
      (tu/update-test-state! :computer-map assoc-in [1 1 :contents :explore-path]
                             [[2 1] [3 1] [4 1]])
      (let [bfs-call-count (atom 0)]
        (with-redefs [pathfinding-bfs/bfs-to-unseen-coast
                      (fn [& _] (swap! bfs-call-count inc) nil)]
          (let [result (ship/patrol-explore-step [1 1])]
            (should= [2 1] result)
            (should= 0 @bfs-call-count)
            ;; Remaining path should be [[3 1] [4 1]]
            (let [unit (get-in (test-utils/read-test-state :game-map) [2 1 :contents])]
              (should= [[3 1] [4 1]] (:explore-path unit))))))))

  (it "clears explore-path when arriving at unseen coast"
    ;; Place patrol boat one step from coast via stored path
    (let [game-map (tu/build-test-map ["####"
                                       "~p~#"
                                       "####"])]
      (tu/set-test-world! game-map)
      (tu/set-test-computer-map! game-map)
      ;; Path leads to [2,1] which is adjacent to land at [3,1]
      (tu/update-test-world! assoc-in [1 1 :contents :explore-path] [[2 1]])
      (tu/update-test-state! :computer-map assoc-in [1 1 :contents :explore-path] [[2 1]])
      (let [result (ship/patrol-explore-step [1 1])
            unit (get-in (test-utils/read-test-state :game-map) (conj result :contents))]
        (should= [2 1] result)
        (should-be-nil (:explore-path unit))
        (should= :crawling (:patrol-mode unit)))))

  (it "clears explore-path when step is blocked by occupant"
    (let [game-map (tu/build-test-map ["~~~"
                                       "~pd"
                                       "~~~"])]
      (tu/set-test-world! game-map)
      (tu/set-test-computer-map! game-map)
      ;; Path leads to [2,1] which is occupied by a computer destroyer
      (tu/update-test-world! assoc-in [1 1 :contents :explore-path] [[2 1]])
      (tu/update-test-state! :computer-map assoc-in [1 1 :contents :explore-path] [[2 1]])
      (let [result (ship/patrol-explore-step [1 1])]
        (should-be-nil result)
        (let [unit (get-in (test-utils/read-test-state :game-map) [1 1 :contents])]
          (should-be-nil (:explore-path unit)))))))
