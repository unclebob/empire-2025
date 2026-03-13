(ns empire.game-mechanics.movement.wake-conditions-spec
  (:require [empire.test.utils :as test-utils]
            [empire.config.core :as config]
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

(describe "wake-before-move"
  (before (reset-all-atoms!))
  (it "wakes unit when something is in the way"
    (let [unit {:type :army :mode :moving :owner :player :target [4 5] :steps-remaining 1}
          next-cell {:type :land :contents {:type :army :owner :player}}
          [result should-wake?] (wake-before-move unit next-cell)]
      (should= :awake (:mode result))
      (should= :somethings-in-the-way (:reason result))
      (should should-wake?)))

  (it "wakes army when trying to move into water"
    (let [unit {:type :army :mode :moving :owner :player :target [4 5] :steps-remaining 1}
          next-cell {:type :sea}
          [result should-wake?] (wake-before-move unit next-cell)]
      (should= :awake (:mode result))
      (should= :cant-move-into-water (:reason result))
      (should should-wake?)))

  (it "wakes army when trying to move into friendly city"
    (let [unit {:type :army :mode :moving :owner :player :target [4 5] :steps-remaining 1}
          next-cell {:type :city :city-status :player}
          [result should-wake?] (wake-before-move unit next-cell)]
      (should= :awake (:mode result))
      (should= :cant-move-into-city (:reason result))
      (should should-wake?)))

  (it "wakes army when trying to move into free city"
    (let [unit {:type :army :mode :moving :owner :player :target [4 5] :steps-remaining 1}
          next-cell {:type :city :city-status :free}
          [result should-wake?] (wake-before-move unit next-cell)]
      (should= :awake (:mode result))
      (should= :army-found-city (:reason result))
      (should should-wake?)))

  (it "wakes army when trying to move into computer city"
    (let [unit {:type :army :mode :moving :owner :player :target [4 5] :steps-remaining 1}
          next-cell {:type :city :city-status :computer}
          [result should-wake?] (wake-before-move unit next-cell)]
      (should= :awake (:mode result))
      (should= :army-found-city (:reason result))
      (should should-wake?)))

  (it "wakes fighter when trying to fly over hostile city"
    (let [unit {:type :fighter :mode :moving :owner :player :target [4 6] :fuel 10}
          next-cell {:type :city :city-status :computer}
          [result should-wake?] (wake-before-move unit next-cell)]
      (should= :awake (:mode result))
      (should= :fighter-over-defended-city (:reason result))
      (should should-wake?)))

  (it "wakes naval unit when trying to move on land"
    (let [unit {:type :destroyer :mode :moving :owner :player :target [4 5] :steps-remaining 1}
          next-cell {:type :land}
          [result should-wake?] (wake-before-move unit next-cell)]
      (should= :awake (:mode result))
      (should= :ships-cant-drive-on-land (:reason result))
      (should should-wake?)))

  (it "allows fighter to land on friendly carrier"
    (let [unit {:type :fighter :mode :moving :owner :player :target [4 5] :fuel 10}
          next-cell {:type :sea :contents {:type :carrier :owner :player :mode :sentry :fighter-count 0}}
          [_result should-wake?] (wake-before-move unit next-cell)]
      (should-not should-wake?)))

  (it "blocks fighter when carrier is full"
    (let [unit {:type :fighter :mode :moving :owner :player :target [4 5] :fuel 10}
          next-cell {:type :sea :contents {:type :carrier :owner :player :hits 3 :fighter-count 8}}
          [result should-wake?] (wake-before-move unit next-cell)]
      (should should-wake?)
      (should= :somethings-in-the-way (:reason result))))

  (it "blocks fighter when carrier is enemy"
    (let [unit {:type :fighter :mode :moving :owner :player :target [4 5] :fuel 10}
          next-cell {:type :sea :contents {:type :carrier :owner :computer :hits 3 :fighter-count 0}}
          [result should-wake?] (wake-before-move unit next-cell)]
      (should should-wake?)
      (should= :somethings-in-the-way (:reason result))))

  (it "blocks fighter when friendly non-carrier unit is in the way"
    (let [unit {:type :fighter :mode :moving :owner :player :target [4 5] :fuel 10}
          next-cell {:type :land :contents {:type :army :owner :player :hits 1}}
          [result should-wake?] (wake-before-move unit next-cell)]
      (should should-wake?)
      (should= :somethings-in-the-way (:reason result))))

  (it "allows fighter to land at player city with contents"
    (let [unit {:type :fighter :mode :moving :owner :player :target [4 5] :fuel 10}
          next-cell {:type :city :city-status :player :contents {:type :fighter :owner :player :fuel 20}}
          [_result should-wake?] (wake-before-move unit next-cell)]
      (should-not should-wake?)))

  (it "wakes fighter when trying to fly over free city"
    (let [unit {:type :fighter :mode :moving :owner :player :target [4 6] :fuel 10}
          next-cell {:type :city :city-status :free}
          [result should-wake?] (wake-before-move unit next-cell)]
      (should= :fighter-over-defended-city (:reason result))
      (should should-wake?)))

  (it "wakes naval unit when trying to enter city"
    (let [unit {:type :destroyer :mode :moving :owner :player :target [4 5] :hits 3}
          next-cell {:type :city :city-status :player}
          [result should-wake?] (wake-before-move unit next-cell)]
      (should should-wake?)
      (should= :ships-cant-enter-city (:reason result))))

  (it "removes target when unit wakes"
    (let [unit {:type :army :mode :moving :owner :player :target [4 5] :steps-remaining 1}
          next-cell {:type :sea}
          [result _] (wake-before-move unit next-cell)]
      (should-be-nil (:target result))))

  (it "does not wake for normal movement"
    (let [unit {:type :army :mode :moving :owner :player :target [4 5] :steps-remaining 1}
          next-cell {:type :land}
          [_result should-wake?] (wake-before-move unit next-cell)]
      (should-not should-wake?))))

(describe "wake-after-move"
  (before (reset-all-atoms!))
  (it "wakes army when near hostile city"
    (let [game-map (atom (build-test-map ["##X"]))]
      (set-test-world! @game-map)
      (let [unit {:type :army :mode :moving :owner :player :target [2 0]}
            result (wake-after-move unit [0 0] [1 0] game-map)]
        (should= :awake (:mode result))
        (should= :army-found-city (:reason result)))))

  (it "wakes fighter when entering friendly city"
    (let [game-map (atom (build-test-map ["#O"]))]
      (set-test-world! @game-map)
      (let [unit {:type :fighter :mode :moving :owner :player :target [1 0] :fuel 10}
            result (wake-after-move unit [0 0] [1 0] game-map)]
        (should= :awake (:mode result))
        (should= :fighter-landed-and-refueled (:reason result))
        (should= config/fighter-fuel (:fuel result)))))

  (it "wakes fighter when fuel reaches 1"
    (let [game-map (atom (build-test-map ["###"]))]
      (set-test-world! @game-map)
      (let [unit {:type :fighter :mode :moving :owner :player :target [2 0] :fuel 1}
            result (wake-after-move unit [0 0] [1 0] game-map)]
        (should= :awake (:mode result))
        (should= :fighter-out-of-fuel (:reason result)))))

  (it "wakes transport when finding land from open sea"
    (let [game-map (atom (build-test-map ["~~~#~"]))]
      (set-test-world! @game-map)
      (let [unit {:type :transport :mode :moving :owner :player :target [3 0] :army-count 1}
            result (wake-after-move unit [1 0] [2 0] game-map)]
        (should= :awake (:mode result))
        (should= :transport-found-land (:reason result)))))

  (it "wakes transport at beach with armies"
    (let [game-map (atom (build-test-map ["#~~#~"]))]
      (set-test-world! @game-map)
      ;; Transport at [1 0] (sea, adjacent to land at [0 0]) moving to [2 0] (sea, adjacent to land at [3 0])
      ;; Since it wasn't in open sea before, found-land? is false, but at-beach with armies triggers
      (let [unit {:type :transport :mode :moving :owner :player :target [3 0] :army-count 1}
            result (wake-after-move unit [1 0] [2 0] game-map)]
        (should= :awake (:mode result))
        (should= :transport-at-beach (:reason result)))))

  (it "follows waypoint orders when arriving"
    (let [game-map (atom (assoc-in (build-test-map ["###"])
                                   [2 0] {:type :land :waypoint {:marching-orders [2 2]}}))]
      (set-test-world! @game-map)
      (let [unit {:type :army :mode :moving :owner :player :target [2 0]}
            result (wake-after-move unit [1 0] [2 0] game-map)]
        (should= :moving (:mode result))
        (should= [2 2] (:target result)))))

  (it "returns unit unchanged for naval units without special conditions"
    (let [game-map (atom (build-test-map ["~~~~"]))]
      (set-test-world! @game-map)
      (let [unit {:type :destroyer :mode :moving :owner :player :target [3 0] :hits 3}
            result (wake-after-move unit [1 0] [2 0] game-map)]
        (should= :moving (:mode result)))))

  (it "sets hit-edge reason when extended-move unit reaches target at map edge"
    (let [game-map (atom (build-test-map ["###"]))]
      (set-test-world! @game-map)
      (let [unit {:type :fighter :mode :moving :owner :player :target [2 0] :fuel 20 :extended true}
            result (wake-after-move unit [1 0] [2 0] game-map)]
        (should= :awake (:mode result))
        (should= :hit-edge (:reason result)))))

  (it "does not set hit-edge reason for non-extended move to map edge"
    (let [game-map (atom (build-test-map ["###"]))]
      (set-test-world! @game-map)
      (let [unit {:type :fighter :mode :moving :owner :player :target [2 0] :fuel 20}
            result (wake-after-move unit [1 0] [2 0] game-map)]
        (should= :awake (:mode result))
        (should-be-nil (:reason result)))))

  (it "sets hits to 0 when fighter is shot down over hostile city"
    (let [game-map (atom (build-test-map ["#X"]))]
      (set-test-world! @game-map)
      (let [unit {:type :fighter :mode :moving :owner :player :target [1 0] :fuel 10}
            result (wake-after-move unit [0 0] [1 0] game-map)]
        (should= :awake (:mode result))
        (should= :fighter-shot-down (:reason result))
        (should= 0 (:hits result))
        (should= 0 (:steps-remaining result)))))

  (it "sets error message when fighter is shot down"
    (let [game-map (atom (build-test-map ["#X"]))]
      (set-test-world! @game-map)
      (let [unit {:type :fighter :mode :moving :owner :player :target [1 0] :fuel 10}]
        (wake-after-move unit [0 0] [1 0] game-map)
        (should= (:fighter-destroyed-by-city config/messages) (test-utils/read-test-state :error-message)))))

  (it "wakes fighter at bingo fuel near friendly city"
    (let [game-map (atom (build-test-map ["O####"]))]
      (set-test-world! @game-map)
      (let [unit {:type :fighter :mode :moving :owner :player :target [4 0] :fuel 5}
            result (wake-after-move unit [2 0] [3 0] game-map)]
        (should= :awake (:mode result))
        (should= :fighter-bingo (:reason result)))))

  (it "does not wake fighter at bingo fuel when target is reachable friendly city"
    (let [game-map (atom (build-test-map ["O####"]))]
      (set-test-world! @game-map)
      (let [unit {:type :fighter :mode :moving :owner :player :target [0 0] :fuel 5}
            result (wake-after-move unit [2 0] [3 0] game-map)]
        (should= :moving (:mode result)))))

  (it "does not wake fighter at bingo fuel when a friendly city is on its current path"
    (let [game-map (atom (build-test-map ["#####O"]))]
      (set-test-world! @game-map)
      (let [unit {:type :fighter :mode :moving :owner :player :target [5 0] :fuel 9}
            result (wake-after-move unit [0 0] [1 0] game-map)]
        (should= :moving (:mode result))
        (should-be-nil (:reason result)))))

  (it "does not wake transport without armies at beach"
    (let [game-map (atom (build-test-map ["#~~#~"]))]
      (set-test-world! @game-map)
      (let [unit {:type :transport :mode :moving :owner :player :target [3 0] :army-count 0}
            result (wake-after-move unit [1 0] [2 0] game-map)]
        (should= :moving (:mode result)))))

  (it "sets been-to-sea when transport enters open sea"
    (let [game-map (atom (build-test-map ["#~~~~"]))]
      (set-test-world! @game-map)
      (let [unit {:type :transport :mode :moving :owner :player :target [4 0] :army-count 0}
            result (wake-after-move unit [1 0] [2 0] game-map)]
        (should= :moving (:mode result))
        (should (:been-to-sea result)))))

  (it "sets been-to-sea to false when transport finds land"
    (let [game-map (atom (build-test-map ["~~~#~"]))]
      (set-test-world! @game-map)
      (let [unit {:type :transport :mode :moving :owner :player :target [3 0] :army-count 1 :been-to-sea true}
            result (wake-after-move unit [1 0] [2 0] game-map)]
        (should= :awake (:mode result))
        (should-not (:been-to-sea result)))))

  (it "wakes unit when arriving at target"
    (let [game-map (atom (build-test-map ["~~~~"]))]
      (set-test-world! @game-map)
      (let [unit {:type :destroyer :mode :moving :owner :player :target [2 0] :hits 3}
            result (wake-after-move unit [1 0] [2 0] game-map)]
        (should= :awake (:mode result)))))

  (it "preserves handler reason when enemy is also spotted"
    (let [game-map (atom (build-test-map ["#X#a"]))]
      (set-test-world! @game-map)
      (let [unit {:type :army :mode :moving :owner :player :target [3 0]}
            result (wake-after-move unit [0 0] [2 0] game-map)]
        (should= :army-found-city (:reason result)))))

  (it "removes target when unit wakes after move"
    (let [game-map (atom (build-test-map ["##X"]))]
      (set-test-world! @game-map)
      (let [unit {:type :army :mode :moving :owner :player :target [2 0]}
            result (wake-after-move unit [0 0] [1 0] game-map)]
        (should-be-nil (:target result)))))

  (it "follows waypoint orders even when enemy is spotted"
    (let [game-map (atom (assoc-in (build-test-map ["##a"])
                                   [1 0] {:type :land :waypoint {:marching-orders [5 5]}}))]
      (set-test-world! @game-map)
      (let [unit {:type :army :mode :moving :owner :player :target [1 0]}
            result (wake-after-move unit [0 0] [1 0] game-map)]
        (should= :moving (:mode result))
        (should= [5 5] (:target result))))))

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
      ;; Enemy at [4 0], unit at [0 0], distance is 4, radius is 1
      (let [unit {:type :army :mode :moving :owner :player}]
        (should-not (enemy-unit-visible? unit [0 0] game-map)))))

  (it "returns true for satellite with larger visibility radius"
    (let [game-map (atom (build-test-map ["#-a"]))]
      (set-test-world! @game-map)
      (set-test-player-map! (make-initial-test-map 1 3 nil))
      ;; Enemy at [2 0], unit at [0 0], distance is 2, satellite radius is 2
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

(describe "wake-after-move enemy spotted"
  (before (reset-all-atoms!))

  (it "wakes unit when enemy is spotted"
    (let [game-map (atom (build-test-map ["##a"]))]
      (set-test-world! @game-map)
      (set-test-player-map! (make-initial-test-map 1 3 nil))
      (let [unit {:type :army :mode :moving :owner :player :target [2 0]}
            result (wake-after-move unit [0 0] [1 0] game-map)]
        (should= :awake (:mode result))
        (should= :enemy-spotted (:reason result)))))

  (it "does not wake when no enemy is visible"
    (let [game-map (atom (build-test-map ["###"]))]
      (set-test-world! @game-map)
      (set-test-player-map! (make-initial-test-map 1 3 nil))
      (let [unit {:type :army :mode :moving :owner :player :target [2 0]}
            result (wake-after-move unit [0 0] [1 0] game-map)]
        (should= :moving (:mode result)))))

  (it "wakes naval unit when enemy is spotted"
    (let [game-map (atom (build-test-map ["~~s"]))]
      (set-test-world! @game-map)
      (set-test-player-map! (make-initial-test-map 1 3 nil))
      ;; Enemy submarine at [2 0], unit at [1 0] after move, distance is 1
      (let [unit {:type :destroyer :mode :moving :owner :player :target [2 0] :hits 3}
            result (wake-after-move unit [0 0] [1 0] game-map)]
        (should= :awake (:mode result))
        (should= :enemy-spotted (:reason result))))))
