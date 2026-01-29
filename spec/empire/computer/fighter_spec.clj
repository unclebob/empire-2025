(ns empire.computer.fighter-spec
  "Tests for VMS Empire style computer fighter movement."
  (:require [speclj.core :refer :all]
            [empire.computer.fighter :as fighter]
            [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.test-utils :refer [build-test-map set-test-unit
                                       get-test-unit reset-all-atoms!]]))

(describe "process-fighter"
  (before (reset-all-atoms!))

  (describe "attack behavior"
    (it "attacks adjacent player unit"
      (reset! atoms/game-map (build-test-map ["fA"]))
      (set-test-unit atoms/game-map "f" :fuel 20)
      (reset! atoms/computer-map @atoms/game-map)
      (let [unit (:contents (get-in @atoms/game-map [0 0]))
            _result (fighter/process-fighter [0 0] unit)]
        ;; Combat should have occurred - one unit should be gone
        (let [cell0 (get-in @atoms/game-map [0 0])
              cell1 (get-in @atoms/game-map [0 1])]
          (should (or (nil? (:contents cell0))
                      (nil? (:contents cell1))
                      (= :computer (:owner (:contents cell1)))))))))

  (describe "fuel management"
    (it "returns to city when low on fuel"
      (reset! atoms/game-map (build-test-map ["X#f"]))
      (set-test-unit atoms/game-map "f" :fuel 3)
      (reset! atoms/computer-map @atoms/game-map)
      (let [unit (:contents (get-in @atoms/game-map [0 2]))]
        (fighter/process-fighter [0 2] unit)
        ;; With fuel 3, distance 2: should-return is true, moves toward city,
        ;; fuel decremented to 2, then adjacent to city, lands
        (should= 1 (:fighter-count (get-in @atoms/game-map [0 0])))))

    (it "lands at adjacent city"
      (reset! atoms/game-map (build-test-map ["Xf"]))
      (set-test-unit atoms/game-map "f" :fuel 2)
      (reset! atoms/computer-map @atoms/game-map)
      (let [unit (:contents (get-in @atoms/game-map [0 1]))
            result (fighter/process-fighter [0 1] unit)]
        ;; Fighter should land at city
        (should-be-nil result)
        ;; City should have fighter
        (should= 1 (:fighter-count (get-in @atoms/game-map [0 0])))))

    (it "consumes fuel each step"
      (reset! atoms/game-map (build-test-map ["X#########f##########"]))
      (set-test-unit atoms/game-map "f" :fuel 30)
      (reset! atoms/computer-map @atoms/game-map)
      (let [unit (get-in @atoms/game-map [0 10 :contents])]
        (fighter/process-fighter [0 10] unit)
        ;; Find the fighter - it should have moved and have fuel < 30
        (let [result (get-test-unit atoms/game-map "f")]
          (should-not-be-nil result)
          (should (< (:fuel (:unit result)) 30)))))

    (it "moves multiple cells per round"
      (reset! atoms/game-map (build-test-map ["X#########f##########"]))
      (set-test-unit atoms/game-map "f" :fuel 30)
      (reset! atoms/computer-map @atoms/game-map)
      (let [unit (get-in @atoms/game-map [0 10 :contents])]
        (fighter/process-fighter [0 10] unit)
        ;; Fighter should NOT still be at [0 10]
        (should-be-nil (get-in @atoms/game-map [0 10 :contents]))
        ;; Fighter should have moved more than 1 cell from start
        (let [result (get-test-unit atoms/game-map "f")
              [_ fighter-col] (:pos result)]
          (should-not-be-nil result)
          (should (> (Math/abs (- fighter-col 10)) 1)))))

    (it "fighter dies when fuel runs out"
      ;; Fighter with fuel 1 on open land, no city nearby.
      ;; After moving once, fuel becomes 0 and fighter should die.
      (reset! atoms/game-map (build-test-map ["f##"]))
      (set-test-unit atoms/game-map "f" :fuel 1)
      (reset! atoms/computer-map @atoms/game-map)
      (let [unit (get-in @atoms/game-map [0 0 :contents])]
        (fighter/process-fighter [0 0] unit)
        ;; Fighter should be gone from the entire map
        (should-be-nil (get-test-unit atoms/game-map "f"))))

    (it "stops moving after landing at city"
      ;; Fighter next to city with low fuel should land and not continue.
      (reset! atoms/game-map (build-test-map ["#Xf##"]))
      (set-test-unit atoms/game-map "f" :fuel 3)
      (reset! atoms/computer-map @atoms/game-map)
      (let [unit (get-in @atoms/game-map [0 2 :contents])]
        (fighter/process-fighter [0 2] unit)
        ;; Fighter should have landed at city [0 1]
        (should= 1 (:fighter-count (get-in @atoms/game-map [0 1])))
        ;; Fighter should NOT be on the map as a unit
        (should-be-nil (get-test-unit atoms/game-map "f")))))

  (describe "patrol behavior"
    (it "patrols toward player units when fuel allows"
      ;; Wide map so fighter patrols toward player unit
      ;; but doesn't reach it to avoid random combat outcomes
      (reset! atoms/game-map (build-test-map ["Xf##########A"]))
      (set-test-unit atoms/game-map "f" :fuel 20)
      (reset! atoms/computer-map @atoms/game-map)
      (let [unit (get-in @atoms/game-map [0 1 :contents])]
        (fighter/process-fighter [0 1] unit)
        ;; Fighter should have left [0 1] and moved toward the player army
        (should-be-nil (get-in @atoms/game-map [0 1 :contents]))
        ;; Fighter should be somewhere between start and player army
        (let [result (get-test-unit atoms/game-map "f")
              [_ fighter-col] (:pos result)]
          (should-not-be-nil result)
          (should (> fighter-col 1)))))

    (it "explores toward unexplored territory"
      ;; Wide map with unexplored cells to the right
      (reset! atoms/game-map (build-test-map ["Xf########"]))
      (set-test-unit atoms/game-map "f" :fuel 20)
      (reset! atoms/computer-map (build-test-map ["Xf........"]))
      (let [unit (get-in @atoms/game-map [0 1 :contents])]
        (fighter/process-fighter [0 1] unit)
        ;; Fighter should have moved away from start toward unexplored
        (should-be-nil (get-in @atoms/game-map [0 1 :contents]))
        (let [result (get-test-unit atoms/game-map "f")
              [_ fighter-col] (:pos result)]
          (should-not-be-nil result)
          (should (> fighter-col 1))))))

  (describe "ignores non-computer fighters"
    (it "returns nil for player fighter"
      (reset! atoms/game-map (build-test-map ["F"]))
      (set-test-unit atoms/game-map "F" :fuel 20)
      (let [unit (:contents (get-in @atoms/game-map [0 0]))]
        (should-be-nil (fighter/process-fighter [0 0] unit))))

    (it "returns nil for non-fighter"
      (reset! atoms/game-map (build-test-map ["a"]))
      (let [unit (:contents (get-in @atoms/game-map [0 0]))]
        (should-be-nil (fighter/process-fighter [0 0] unit))))))
