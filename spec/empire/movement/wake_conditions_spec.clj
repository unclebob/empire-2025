(ns empire.movement.wake-conditions-spec
  (:require [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.movement.wake-conditions :refer :all]
            [empire.test-utils :refer [build-test-map make-initial-test-map reset-all-atoms!]]
            [speclj.core :refer :all]))

(describe "near-hostile-city?"
  (before (reset-all-atoms!))
  (it "returns true when adjacent to a computer city"
    (let [game-map (atom (build-test-map ["###"
                                          "#X#"
                                          "###"]))]
      (should (near-hostile-city? [1 0] game-map))))

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
      (should (friendly-city-in-range? [1 3] 5 game-map))))

  (it "returns false when friendly city is out of range"
    (let [game-map (atom (build-test-map ["O------"
                                          "-------"
                                          "-------"
                                          "-------"
                                          "-------"]))]
      (should-not (friendly-city-in-range? [4 5] 3 game-map))))

  (it "returns false when only computer cities are in range"
    (let [game-map (atom (build-test-map ["##X"
                                          "###"]))]
      (should-not (friendly-city-in-range? [1 1] 5 game-map)))))

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
  (before (reset-all-atoms!))
  (it "wakes army when near hostile city"
    (let [game-map (atom (build-test-map ["##X"]))]
      (reset! atoms/game-map @game-map)
      (let [unit {:type :army :mode :moving :owner :player :target [0 2]}
            result (wake-after-move unit [0 0] [0 1] game-map)]
        (should= :awake (:mode result))
        (should= :army-found-city (:reason result)))))

  (it "wakes fighter when entering friendly city"
    (let [game-map (atom (build-test-map ["#O"]))]
      (reset! atoms/game-map @game-map)
      (let [unit {:type :fighter :mode :moving :owner :player :target [0 1] :fuel 10}
            result (wake-after-move unit [0 0] [0 1] game-map)]
        (should= :awake (:mode result))
        (should= :fighter-landed-and-refueled (:reason result))
        (should= config/fighter-fuel (:fuel result)))))

  (it "wakes fighter when fuel reaches 1"
    (let [game-map (atom (build-test-map ["###"]))]
      (reset! atoms/game-map @game-map)
      (let [unit {:type :fighter :mode :moving :owner :player :target [0 2] :fuel 1}
            result (wake-after-move unit [0 0] [0 1] game-map)]
        (should= :awake (:mode result))
        (should= :fighter-out-of-fuel (:reason result)))))

  (it "wakes transport when finding land from open sea"
    (let [game-map (atom (build-test-map ["~~~#~"]))]
      (reset! atoms/game-map @game-map)
      (let [unit {:type :transport :mode :moving :owner :player :target [0 3] :army-count 1}
            result (wake-after-move unit [0 1] [0 2] game-map)]
        (should= :awake (:mode result))
        (should= :transport-found-land (:reason result)))))

  (it "wakes transport at beach with armies"
    (let [game-map (atom (build-test-map ["#~~#~"]))]
      (reset! atoms/game-map @game-map)
      ;; Transport at [0 1] (sea, adjacent to land at [0 0]) moving to [0 2] (sea, adjacent to land at [0 3])
      ;; Since it wasn't in open sea before, found-land? is false, but at-beach with armies triggers
      (let [unit {:type :transport :mode :moving :owner :player :target [0 3] :army-count 1}
            result (wake-after-move unit [0 1] [0 2] game-map)]
        (should= :awake (:mode result))
        (should= :transport-at-beach (:reason result)))))

  (it "follows waypoint orders when arriving"
    (let [game-map (atom (assoc-in (build-test-map ["###"])
                                   [0 2] {:type :land :waypoint {:marching-orders [2 2]}}))]
      (reset! atoms/game-map @game-map)
      (let [unit {:type :army :mode :moving :owner :player :target [0 2]}
            result (wake-after-move unit [0 1] [0 2] game-map)]
        (should= :moving (:mode result))
        (should= [2 2] (:target result)))))

  (it "returns unit unchanged for naval units without special conditions"
    (let [game-map (atom (build-test-map ["~~~~"]))]
      (reset! atoms/game-map @game-map)
      (let [unit {:type :destroyer :mode :moving :owner :player :target [0 3] :hits 3}
            result (wake-after-move unit [0 1] [0 2] game-map)]
        (should= :moving (:mode result))))))

(describe "enemy-unit-visible?"
  (before (reset-all-atoms!))

  (it "returns true when enemy unit is adjacent"
    (let [game-map (atom (build-test-map ["#a"]))]
      (reset! atoms/game-map @game-map)
      (reset! atoms/player-map (make-initial-test-map 1 2 nil))
      (let [unit {:type :army :mode :moving :owner :player}]
        (should (enemy-unit-visible? unit [0 0] game-map)))))

  (it "returns false when no enemy units are visible"
    (let [game-map (atom (build-test-map ["###"]))]
      (reset! atoms/game-map @game-map)
      (reset! atoms/player-map (make-initial-test-map 1 3 nil))
      (let [unit {:type :army :mode :moving :owner :player}]
        (should-not (enemy-unit-visible? unit [0 1] game-map)))))

  (it "returns false when only friendly units are visible"
    (let [game-map (atom (build-test-map ["#A"]))]
      (reset! atoms/game-map @game-map)
      (reset! atoms/player-map (make-initial-test-map 1 2 nil))
      (let [unit {:type :army :mode :moving :owner :player}]
        (should-not (enemy-unit-visible? unit [0 0] game-map)))))

  (it "returns false when enemy unit is outside visibility radius"
    (let [game-map (atom (build-test-map ["#---a"]))]
      (reset! atoms/game-map @game-map)
      (reset! atoms/player-map (make-initial-test-map 1 5 nil))
      ;; Enemy at [0 4], unit at [0 0], distance is 4, radius is 1
      (let [unit {:type :army :mode :moving :owner :player}]
        (should-not (enemy-unit-visible? unit [0 0] game-map)))))

  (it "returns true for satellite with larger visibility radius"
    (let [game-map (atom (build-test-map ["#-a"]))]
      (reset! atoms/game-map @game-map)
      (reset! atoms/player-map (make-initial-test-map 1 3 nil))
      ;; Enemy at [0 2], unit at [0 0], distance is 2, satellite radius is 2
      (let [unit {:type :satellite :mode :moving :owner :player}]
        (should (enemy-unit-visible? unit [0 0] game-map))))))

(describe "wake-after-move enemy spotted"
  (before (reset-all-atoms!))

  (it "wakes unit when enemy is spotted"
    (let [game-map (atom (build-test-map ["##a"]))]
      (reset! atoms/game-map @game-map)
      (reset! atoms/player-map (make-initial-test-map 1 3 nil))
      (let [unit {:type :army :mode :moving :owner :player :target [0 2]}
            result (wake-after-move unit [0 0] [0 1] game-map)]
        (should= :awake (:mode result))
        (should= :enemy-spotted (:reason result)))))

  (it "does not wake when no enemy is visible"
    (let [game-map (atom (build-test-map ["###"]))]
      (reset! atoms/game-map @game-map)
      (reset! atoms/player-map (make-initial-test-map 1 3 nil))
      (let [unit {:type :army :mode :moving :owner :player :target [0 2]}
            result (wake-after-move unit [0 0] [0 1] game-map)]
        (should= :moving (:mode result)))))

  (it "wakes naval unit when enemy is spotted"
    (let [game-map (atom (build-test-map ["~~s"]))]
      (reset! atoms/game-map @game-map)
      (reset! atoms/player-map (make-initial-test-map 1 3 nil))
      ;; Enemy submarine at [0 2], unit at [0 1] after move, distance is 1
      (let [unit {:type :destroyer :mode :moving :owner :player :target [0 2] :hits 3}
            result (wake-after-move unit [0 0] [0 1] game-map)]
        (should= :awake (:mode result))
        (should= :enemy-spotted (:reason result))))))
