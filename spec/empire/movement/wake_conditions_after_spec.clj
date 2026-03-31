(ns empire.game-mechanics.movement.wake-conditions-after-spec
  (:require [empire.config.core :as config]
            [empire.test.utils :as test-utils]
            [empire.game-mechanics.movement.wake-conditions :refer :all]
            [empire.test.utils :refer [build-test-map make-initial-test-map reset-all-atoms! set-test-player-map! set-test-world!]]
            [speclj.core :refer :all]))

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
        (should= (:fighter-destroyed-by-city config/messages) (test-utils/read-test-state :warning-message)))))

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
      (let [unit {:type :destroyer :mode :moving :owner :player :target [2 0] :hits 3}
            result (wake-after-move unit [0 0] [1 0] game-map)]
        (should= :awake (:mode result))
        (should= :enemy-spotted (:reason result))))))
