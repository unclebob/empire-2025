(ns empire.computer.patrol-boat-spec
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.computer.ship :as ship]
            [empire.movement.pathfinding-bfs :as pathfinding-bfs]
            [empire.test.utils :as tu]))

(describe "seen-coast atom"
  (before (tu/reset-all-atoms!))

  (it "starts as an empty set"
    (should= #{} (test-utils/read-test-state :seen-coast)))

  (it "is reset to empty set by reset-all-atoms!"
    (test-utils/set-test-state! :seen-coast #{[3 4] [5 6]})
    (tu/reset-all-atoms!)
    (should= #{} (test-utils/read-test-state :seen-coast))))

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

(describe "patrol-crawl-step"
  (before (tu/reset-all-atoms!))

  ;; Map layout for most tests:
  ;; "###"   row 0: all land
  ;; "#p~"   row 1: land, patrol boat at [1,1], sea at [2,1]
  ;; "#~~"   row 2: land, sea, sea
  ;; Patrol boat at [1,1] is coastal (adjacent to land)

  (it "moves to an adjacent coastal cell"
    (let [game-map (tu/build-test-map ["###"
                                       "#p~"
                                       "#~~"])]
      (tu/set-test-world! game-map)
      (tu/set-test-computer-map! game-map)
      (with-redefs [rand-nth first]
        (let [result (ship/patrol-crawl-step [1 1])]
          (should-not-be-nil result)
          ;; Should move to a sea cell adjacent to land
          (should= :sea (:type (get-in (test-utils/read-test-state :game-map) result)))))))

  (it "records current position in seen-coast"
    (let [game-map (tu/build-test-map ["###"
                                       "#p~"
                                       "#~~"])]
      (tu/set-test-world! game-map)
      (tu/set-test-computer-map! game-map)
      (with-redefs [rand-nth first]
        (ship/patrol-crawl-step [1 1])
        (should-contain [1 1] (test-utils/read-test-state :seen-coast)))))

  (it "prefers unseen coast over seen coast"
    (let [game-map (tu/build-test-map ["###~"
                                       "#p~~"
                                       "#~~~"
                                       "~~~~"])]
      (tu/set-test-world! game-map)
      (tu/set-test-computer-map! game-map)
      ;; Mark [1,2] as seen, leave [2,0] unseen
      ;; Coastal neighbors of [1,1]: [1,2] and [2,0] are adjacent to land
      (test-utils/set-test-state! :seen-coast #{[1 2]})
      (with-redefs [rand-nth first]
        (let [result (ship/patrol-crawl-step [1 1])]
          (should-not-be-nil result)
          ;; Should NOT go to [1,2] which is seen
          (should-not= [1 2] result)))))

  (it "switches to exploring when all coastal neighbors are seen"
    (let [game-map (tu/build-test-map ["###"
                                       "#p~"
                                       "#~~"])]
      (tu/set-test-world! game-map)
      (tu/set-test-computer-map! game-map)
      ;; Mark all coastal neighbors as seen
      ;; Coastal neighbors of [1,1]: empty sea cells adjacent to land
      ;; are [1,2] and [2,1].
      (test-utils/set-test-state! :seen-coast #{[1 2] [2 1]})
      (with-redefs [rand-nth first]
        (ship/patrol-crawl-step [1 1])
        ;; After moving, unit should have :patrol-mode :exploring
        (let [result-pos (first (remove #{[1 1]}
                                        (for [c (range 4) r (range 4)
                                              :let [cell (get-in (test-utils/read-test-state :game-map) [c r])]
                                              :when (and (:contents cell)
                                                         (= :patrol-boat (:type (:contents cell))))]
                                          [c r])))
              unit (get-in (test-utils/read-test-state :game-map) (conj result-pos :contents))]
          (should= :exploring (:patrol-mode unit))))))

  (it "returns nil when no coastal cells available"
    (let [game-map (tu/build-test-map ["~p~"
                                       "~~~"
                                       "~~~"])]
      (tu/set-test-world! game-map)
      (tu/set-test-computer-map! game-map)
      (should-be-nil (ship/patrol-crawl-step [1 0])))))

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
      (let [result (ship/patrol-explore-step [1 1])]
        (should-be-nil result)
        (let [unit (get-in (test-utils/read-test-state :game-map) [1 1 :contents])]
          (should-be-nil (:explore-path unit)))))))

(describe "process-patrol-boat (unified)"
  (before (tu/reset-all-atoms!))

  (it "crawls along coast when patrol-mode is :crawling"
    (let [game-map (tu/build-test-map ["########"
                                       "#p~~~~~~"
                                       "########"])]
      (tu/set-test-world! game-map)
      (tu/set-test-computer-map! game-map)
      (tu/set-test-unit (test-utils/game-map-atom) "p" :patrol-mode :crawling)
      (with-redefs [rand-nth first]
        (ship/process-ship [1 1] :patrol-boat)
        ;; Should have moved along coast (up to 4 steps)
        (let [{:keys [pos unit]} (tu/get-test-unit (test-utils/game-map-atom) "p")]
          (should= :patrol-boat (:type unit))
          (should-not= [1 1] pos)))))

  (it "explores toward coast when patrol-mode is :exploring"
    (let [game-map (tu/build-test-map ["~~~~~~~#"
                                       "~p~~~~~#"
                                       "~~~~~~~#"])]
      (tu/set-test-world! game-map)
      (tu/set-test-computer-map! game-map)
      (tu/set-test-unit (test-utils/game-map-atom) "p" :patrol-mode :exploring)
      (ship/process-ship [1 1] :patrol-boat)
      (let [{:keys [pos]} (tu/get-test-unit (test-utils/game-map-atom) "p")]
        ;; Should have moved toward coast (col > 1)
        (should (> (first pos) 1)))))

  (it "attacks adjacent player transport before crawling"
    (let [game-map (tu/build-test-map ["########"
                                       "#pT~~~~~"
                                       "########"])]
      (tu/set-test-world! game-map)
      (tu/set-test-computer-map! game-map)
      (tu/set-test-unit (test-utils/game-map-atom) "p" :patrol-mode :crawling)
      (ship/process-ship [1 1] :patrol-boat)
      ;; Combat should have occurred — patrol boat no longer at [1,1]
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [1 1 :contents]))))

  (it "flees from adjacent non-transport enemy before crawling"
    (let [game-map (tu/build-test-map ["~###"
                                       "~pD~"
                                       "~###"])]
      (tu/set-test-world! game-map)
      (tu/set-test-computer-map! game-map)
      (tu/set-test-unit (test-utils/game-map-atom) "p" :patrol-mode :crawling)
      (ship/process-ship [1 1] :patrol-boat)
      ;; Patrol boat should have moved away from destroyer at [2,1]
      (let [{:keys [pos]} (tu/get-test-unit (test-utils/game-map-atom) "p")]
        (should (< (first pos) 2)))))

  (it "does not require patrol-country-id to be routed"
    ;; A patrol boat with only :patrol-mode should still work
    (let [game-map (tu/build-test-map ["########"
                                       "#p~~~~~~"
                                       "########"])]
      (tu/set-test-world! game-map)
      (tu/set-test-computer-map! game-map)
      (tu/set-test-unit (test-utils/game-map-atom) "p" :patrol-mode :crawling)
      (with-redefs [rand-nth first]
        (ship/process-ship [1 1] :patrol-boat)
        (let [{:keys [pos]} (tu/get-test-unit (test-utils/game-map-atom) "p")]
          (should-not= [1 1] pos)))))

  (it "patrol boat without patrol-mode defaults to crawling"
    (let [game-map (tu/build-test-map ["########"
                                       "#p~~~~~~"
                                       "########"])]
      (tu/set-test-world! game-map)
      (tu/set-test-computer-map! game-map)
      ;; No :patrol-mode set — defaults to :crawling
      (ship/process-ship [1 1] :patrol-boat)
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [1 1 :contents]))))

  (it "seen-coast is shared between multiple patrol boats"
    ;; Two patrol boats crawling the same coastline
    ;; Second boat should see first boat's entries in seen-coast
    (let [game-map (tu/build-test-map ["########"
                                       "~p~~~~~~"
                                       "########"])]
      (tu/set-test-world! game-map)
      (tu/set-test-computer-map! game-map)
      (tu/set-test-unit (test-utils/game-map-atom) "p" :patrol-mode :crawling)
      (tu/update-test-world! assoc-in [6 1 :contents]
             {:type :patrol-boat :owner :computer :hits 1 :patrol-mode :crawling})
      (with-redefs [rand-nth first]
        ;; Process first boat — it adds [1 1] to seen-coast
        (ship/process-ship [1 1] :patrol-boat)
        (should-contain [1 1] (test-utils/read-test-state :seen-coast))
        ;; Process second boat — it adds [6 1] to seen-coast
        (ship/process-ship [6 1] :patrol-boat)
        (should-contain [6 1] (test-utils/read-test-state :seen-coast))
        ;; Both entries should coexist
        (should-contain [1 1] (test-utils/read-test-state :seen-coast)))))

  (it "crawling at map edge with all neighbors seen switches to exploring"
    ;; Patrol boat at [0,1] — map edge (col 0). All coastal neighbors seen.
    ;; "##"  row 0: land, land
    ;; "p~"  row 1: patrol boat at [0,1], sea at [1,1]
    ;; "##"  row 2: land, land
    ;; Coastal neighbors of [0,1]: only [1,1] is empty sea adj to land.
    (let [game-map (tu/build-test-map ["##"
                                       "p~"
                                       "##"])]
      (tu/set-test-world! game-map)
      (tu/set-test-computer-map! game-map)
      (tu/set-test-unit (test-utils/game-map-atom) "p" :patrol-mode :crawling)
      (test-utils/set-test-state! :seen-coast #{[1 1]})
      (with-redefs [rand-nth first]
        (ship/process-ship [0 1] :patrol-boat)
        ;; After moving, unit should have switched to :exploring
        (let [{:keys [unit]} (tu/get-test-unit (test-utils/game-map-atom) "p")]
          (should= :exploring (:patrol-mode unit))))))

  (it "moves multiple steps per round"
    ;; Long coastline — patrol boat should move up to 4 steps
    (let [game-map (tu/build-test-map ["########"
                                       "~p~~~~~~"
                                       "########"])]
      (tu/set-test-world! game-map)
      (tu/set-test-computer-map! game-map)
      (tu/set-test-unit (test-utils/game-map-atom) "p" :patrol-mode :crawling)
      (with-redefs [rand-nth first]
        (ship/process-ship [1 1] :patrol-boat)
        (let [{:keys [pos]} (tu/get-test-unit (test-utils/game-map-atom) "p")]
          ;; Should have moved multiple steps (not just 1)
          (should (> (Math/abs (- (first pos) 1)) 1)))))))
