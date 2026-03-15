(ns empire.game-mechanics.movement.wake-conditions-predicates-spec
  (:require [empire.test.utils :as test-utils]
            [empire.game-mechanics.movement.wake-conditions :refer :all]
            [empire.test.utils :refer [build-test-map make-initial-test-map reset-all-atoms! set-test-player-map! set-test-world!]]
            [speclj.core :refer :all]))

(describe "near-hostile-city?"
  (before (reset-all-atoms!))
  (it "returns true when adjacent to a computer city"
    (let [game-map (atom (build-test-map ["###"
                                          "#X#"
                                          "###"]))]
      (should (near-hostile-city? [0 1] game-map))))

  (it "returns true when adjacent to a free city"
    (let [game-map (atom (build-test-map ["#+"]))]
      (should (near-hostile-city? [0 0] game-map))))

  (it "returns false when adjacent to a player city"
    (let [game-map (atom (build-test-map ["#O"]))]
      (should-not (near-hostile-city? [0 0] game-map))))

  (it "returns false when not adjacent to any city"
    (let [game-map (atom (make-initial-test-map 3 3 {:type :land}))]
      (should-not (near-hostile-city? [1 1] game-map)))))

(describe "friendly-city-in-range?"
  (before (reset-all-atoms!))
  (it "returns true when friendly city is within range"
    (let [game-map (atom (build-test-map ["O###"
                                          "####"]))]
      (should (friendly-city-in-range? [3 1] 5 game-map))))

  (it "returns false when friendly city is out of range"
    (let [game-map (atom (build-test-map ["O------"
                                          "-------"
                                          "-------"
                                          "-------"
                                          "-------"]))]
      (should-not (friendly-city-in-range? [5 4] 3 game-map))))

  (it "returns false when only computer cities are in range"
    (let [game-map (atom (build-test-map ["##X"
                                          "###"]))]
      (should-not (friendly-city-in-range? [1 1] 5 game-map))))

  (it "returns true when friendly city is at exactly max distance"
    (let [game-map (atom (build-test-map ["O###"]))]
      (should (friendly-city-in-range? [3 0] 3 game-map))))

  (it "returns false when only one axis is within range"
    (let [game-map (atom (build-test-map ["O~~~~~"
                                          "~~~~~~"
                                          "~~~~~~"
                                          "~~~~~~"
                                          "~~~~~~"]))]
      (should-not (friendly-city-in-range? [4 1] 3 game-map)))))

(describe "enemy-unit-visible?"
  (before (reset-all-atoms!))

  (it "returns true when enemy unit is adjacent"
    (let [game-map (atom (build-test-map ["#a"]))]
      (set-test-world! @game-map)
      (set-test-player-map! (make-initial-test-map 1 2 nil))
      (let [unit {:type :army :mode :moving :owner :player}]
        (should (enemy-unit-visible? unit [0 0] game-map)))))

  (it "returns false when no enemy units are visible"
    (let [game-map (atom (build-test-map ["###"]))]
      (set-test-world! @game-map)
      (set-test-player-map! (make-initial-test-map 1 3 nil))
      (let [unit {:type :army :mode :moving :owner :player}]
        (should-not (enemy-unit-visible? unit [1 0] game-map)))))

  (it "returns false when only friendly units are visible"
    (let [game-map (atom (build-test-map ["#A"]))]
      (set-test-world! @game-map)
      (set-test-player-map! (make-initial-test-map 1 2 nil))
      (let [unit {:type :army :mode :moving :owner :player}]
        (should-not (enemy-unit-visible? unit [0 0] game-map)))))

  (it "returns false when enemy unit is outside visibility radius"
    (let [game-map (atom (build-test-map ["#---a"]))]
      (set-test-world! @game-map)
      (set-test-player-map! (make-initial-test-map 1 5 nil))
      (let [unit {:type :army :mode :moving :owner :player}]
        (should-not (enemy-unit-visible? unit [0 0] game-map)))))

  (it "returns true for satellite with larger visibility radius"
    (let [game-map (atom (build-test-map ["#-a"]))]
      (set-test-world! @game-map)
      (set-test-player-map! (make-initial-test-map 1 3 nil))
      (let [unit {:type :satellite :mode :moving :owner :player}]
        (should (enemy-unit-visible? unit [0 0] game-map))))))

(describe "found-land?"
  (it "returns true when both in open sea and at beach"
    (should (@#'empire.game-mechanics.movement.wake-conditions.transport/found-land? true true)))

  (it "returns false when not in open sea"
    (should-not (@#'empire.game-mechanics.movement.wake-conditions.transport/found-land? false true)))

  (it "returns false when not at beach"
    (should-not (@#'empire.game-mechanics.movement.wake-conditions.transport/found-land? true false)))

  (it "returns false when neither in open sea nor at beach"
    (should-not (@#'empire.game-mechanics.movement.wake-conditions.transport/found-land? false false))))

(describe "should-wake-at-beach?"
  (it "returns true when all conditions met"
    (should (@#'empire.game-mechanics.movement.wake-conditions.transport/should-wake-at-beach? true true true)))

  (it "returns false when no armies"
    (should-not (@#'empire.game-mechanics.movement.wake-conditions.transport/should-wake-at-beach? false true true)))

  (it "returns false when not at beach"
    (should-not (@#'empire.game-mechanics.movement.wake-conditions.transport/should-wake-at-beach? true false true)))

  (it "returns false when not been to sea"
    (should-not (@#'empire.game-mechanics.movement.wake-conditions.transport/should-wake-at-beach? true true false)))

  (it "returns false when no armies and not at beach"
    (should-not (@#'empire.game-mechanics.movement.wake-conditions.transport/should-wake-at-beach? false false true))))
