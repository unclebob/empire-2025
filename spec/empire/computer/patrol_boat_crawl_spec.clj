(ns empire.computer.patrol-boat-crawl-spec
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.computer.ship :as ship]
            [empire.game-mechanics.movement.pathfinding-bfs :as pathfinding-bfs]
            [empire.test.utils :as tu]))
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
