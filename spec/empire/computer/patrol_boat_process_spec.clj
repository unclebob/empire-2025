(ns empire.computer.patrol-boat-process-spec
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.computer.ship :as ship]
            [empire.game-mechanics.movement.pathfinding-bfs :as pathfinding-bfs]
            [empire.test.utils :as tu]))
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
          (should (> (Math/abs (- (first pos) 1)) 1)))))

  (it "moves during random walk when an empty sea neighbor exists"
    (let [game-map (tu/build-test-map ["pp~"])]
      (tu/set-test-world! [[{:type :sea}
                            {:type :sea :contents {:type :patrol-boat :owner :computer :hits 1}}
                            {:type :sea}]])
      (with-redefs [empire.computer.ship-core/get-passable-sea-neighbors (fn [_] [[0 0] [2 0]])
                    rand-nth (fn [xs] (last xs))]
        (should= [2 0] (@#'empire.computer.ship-patrol/patrol-random-walk-step [1 0])))))

  (it "stays put when a major-invasion attack step returns nil"
    (let [world [[{:type :sea :contents {:type :patrol-boat :owner :computer :hits 1
                                         :major-invasion true}}]]]
      (tu/set-test-world! world)
      (with-redefs [empire.computer.ship-core/find-adjacent-enemy-ship (fn [_] [1 0])
                    empire.computer.ship-patrol/major-invasion-step (fn [_] nil)]
        (should= [0 0] (@#'empire.computer.ship-patrol/process-standard-patrol [0 0])))))))
