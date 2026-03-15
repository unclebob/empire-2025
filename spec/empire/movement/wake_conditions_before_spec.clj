(ns empire.game-mechanics.movement.wake-conditions-before-spec
  (:require [empire.game-mechanics.movement.wake-conditions :refer :all]
            [empire.test.utils :refer [reset-all-atoms!]]
            [speclj.core :refer :all]))

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
