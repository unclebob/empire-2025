(ns empire.movement.wake-conditions-spec
  (:require [speclj.core :refer :all]
            [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.movement.wake-conditions :refer :all]
            [empire.test-utils :refer [build-test-map]]))

(describe "near-hostile-city?"
  (it "returns true when adjacent to a computer city"
    (let [game-map (build-test-map ["LLLLL"
                                    "LLLLL"
                                    "LLLXL"
                                    "LLLLL"
                                    "LLLLL"])]
      (should (near-hostile-city? [2 2] game-map))))

  (it "returns true when adjacent to a free city"
    (let [game-map (build-test-map ["LLLLL"
                                    "LLLLL"
                                    "LLL+L"
                                    "LLLLL"
                                    "LLLLL"])]
      (should (near-hostile-city? [2 2] game-map))))

  (it "returns false when adjacent to a player city"
    (let [game-map (build-test-map ["LLLLL"
                                    "LLLLL"
                                    "LLLOL"
                                    "LLLLL"
                                    "LLLLL"])]
      (should-not (near-hostile-city? [2 2] game-map))))

  (it "returns false when not adjacent to any city"
    (let [game-map (build-test-map ["LLLLL"
                                    "LLLLL"
                                    "LLLLL"
                                    "LLLLL"
                                    "LLLLL"])]
      (should-not (near-hostile-city? [2 2] game-map)))))

(describe "friendly-city-in-range?"
  (it "returns true when friendly city is within range"
    (let [game-map (build-test-map ["LLLLLLLLL"
                                    "LLLLLLLLL"
                                    "LLLLLLLLL"
                                    "LLLLLLLLL"
                                    "OLLLLLLLL"
                                    "LLLLLLLLL"
                                    "LLLLLLLLL"
                                    "LLLLLLLLL"
                                    "LLLLLLLLL"])]
      (should (friendly-city-in-range? [4 4] 5 game-map))))

  (it "returns false when friendly city is out of range"
    (let [game-map (build-test-map ["OLLLLLLLL"
                                    "LLLLLLLLL"
                                    "LLLLLLLLL"
                                    "LLLLLLLLL"
                                    "LLLLLLLLL"
                                    "LLLLLLLLL"
                                    "LLLLLLLLL"
                                    "LLLLLLLLL"
                                    "LLLLLLLLL"])]
      (should-not (friendly-city-in-range? [4 5] 3 game-map))))

  (it "returns false when only computer cities are in range"
    (let [game-map (build-test-map ["LLLLLLLLL"
                                    "LLLLLLLLL"
                                    "LLLLLLLLL"
                                    "LLLLLLLLL"
                                    "LLLLLXLLL"
                                    "LLLLLLLLL"
                                    "LLLLLLLLL"
                                    "LLLLLLLLL"
                                    "LLLLLLLLL"])]
      (should-not (friendly-city-in-range? [4 4] 5 game-map)))))

(describe "wake-before-move"
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

  (it "does not wake for normal movement"
    (let [unit {:type :army :mode :moving :owner :player :target [4 5] :steps-remaining 1}
          next-cell {:type :land}
          [_result should-wake?] (wake-before-move unit next-cell)]
      (should-not should-wake?))))

(describe "wake-after-move"
  (it "wakes army when near hostile city"
    (let [game-map (build-test-map ["LLLLLLLLL"
                                    "LLLLLLLLL"
                                    "LLLLLLLLL"
                                    "LLLLLLLLL"
                                    "LLLLLLXLL"
                                    "LLLLLLLLL"
                                    "LLLLLLLLL"
                                    "LLLLLLLLL"
                                    "LLLLLLLLL"])]
      (reset! atoms/game-map @game-map)
      (let [unit {:type :army :mode :moving :owner :player :target [4 6]}
            result (wake-after-move unit [4 4] [4 5] game-map)]
        (should= :awake (:mode result))
        (should= :army-found-city (:reason result)))))

  (it "wakes fighter when entering friendly city"
    (let [game-map (build-test-map ["LLLLLLLLL"
                                    "LLLLLLLLL"
                                    "LLLLLLLLL"
                                    "LLLLLLLLL"
                                    "LLLLLOLLL"
                                    "LLLLLLLLL"
                                    "LLLLLLLLL"
                                    "LLLLLLLLL"
                                    "LLLLLLLLL"])]
      (reset! atoms/game-map @game-map)
      (let [unit {:type :fighter :mode :moving :owner :player :target [4 5] :fuel 10}
            result (wake-after-move unit [4 4] [4 5] game-map)]
        (should= :awake (:mode result))
        (should= :fighter-landed-and-refueled (:reason result))
        (should= config/fighter-fuel (:fuel result)))))

  (it "wakes fighter when fuel reaches 1"
    (let [game-map (build-test-map ["LLLLLLLLL"
                                    "LLLLLLLLL"
                                    "LLLLLLLLL"
                                    "LLLLLLLLL"
                                    "LLLLLLLLL"
                                    "LLLLLLLLL"
                                    "LLLLLLLLL"
                                    "LLLLLLLLL"
                                    "LLLLLLLLL"])]
      (reset! atoms/game-map @game-map)
      (let [unit {:type :fighter :mode :moving :owner :player :target [4 8] :fuel 1}
            result (wake-after-move unit [4 4] [4 5] game-map)]
        (should= :awake (:mode result))
        (should= :fighter-out-of-fuel (:reason result)))))

  (it "wakes transport when finding land from open sea"
    (let [game-map (build-test-map ["sssssssss"
                                    "sssssssss"
                                    "sssssssss"
                                    "sssssssss"
                                    "ssssssLss"
                                    "sssssssss"
                                    "sssssssss"
                                    "sssssssss"
                                    "sssssssss"])]
      (reset! atoms/game-map @game-map)
      (let [unit {:type :transport :mode :moving :owner :player :target [4 6] :army-count 1}
            result (wake-after-move unit [4 4] [4 5] game-map)]
        (should= :awake (:mode result))
        (should= :transport-found-land (:reason result)))))

  (it "wakes transport at beach with armies"
    (let [game-map (build-test-map ["sssssssss"
                                    "sssssssss"
                                    "sssssssss"
                                    "sssssssss"
                                    "sssLssLss"
                                    "sssssssss"
                                    "sssssssss"
                                    "sssssssss"
                                    "sssssssss"])]
      (reset! atoms/game-map @game-map)
      ;; Transport at [4 4] (sea, adjacent to land at [4 3]) moving to [4 5] (sea, adjacent to land at [4 6])
      ;; Since it wasn't in open sea before, found-land? is false, but at-beach with armies triggers
      (let [unit {:type :transport :mode :moving :owner :player :target [4 6] :army-count 1}
            result (wake-after-move unit [4 4] [4 5] game-map)]
        (should= :awake (:mode result))
        (should= :transport-at-beach (:reason result)))))

  (it "follows waypoint orders when arriving"
    (let [game-map (assoc-in @(build-test-map ["LLLLLLLLL"
                                               "LLLLLLLLL"
                                               "LLLLLLLLL"
                                               "LLLLLLLLL"
                                               "LLLLLLLLL"
                                               "LLLLLLLLL"
                                               "LLLLLLLLL"
                                               "LLLLLLLLL"
                                               "LLLLLLLLL"])
                             [4 5] {:type :land :waypoint {:marching-orders [8 8]}})]
      (reset! atoms/game-map game-map)
      (let [unit {:type :army :mode :moving :owner :player :target [4 5]}
            result (wake-after-move unit [4 4] [4 5] (atom game-map))]
        (should= :moving (:mode result))
        (should= [8 8] (:target result)))))

  (it "returns unit unchanged for naval units without special conditions"
    (let [game-map (build-test-map ["sssssssss"
                                    "sssssssss"
                                    "sssssssss"
                                    "sssssssss"
                                    "sssssssss"
                                    "sssssssss"
                                    "sssssssss"
                                    "sssssssss"
                                    "sssssssss"])]
      (reset! atoms/game-map @game-map)
      (let [unit {:type :destroyer :mode :moving :owner :player :target [4 8] :hits 3}
            result (wake-after-move unit [4 4] [4 5] game-map)]
        (should= :moving (:mode result))))))
