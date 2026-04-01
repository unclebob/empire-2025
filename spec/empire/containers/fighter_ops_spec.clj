(ns empire.containers.fighter-ops-spec
  (:require [empire.test.utils :as test-utils]
            [empire.config.core :as config]
            [empire.game-mechanics.containers.ops :refer :all]
            [empire.game-mechanics.containers.helpers :as uc]
            [empire.test.utils :refer [build-test-map get-test-unit reset-all-atoms! set-test-unit set-test-world! update-test-world!]]
            [speclj.core :refer :all]))
(describe "wake-fighters-on-carrier"
  (before (reset-all-atoms!))

  (it "wakes all fighters and sets carrier to sentry"
    (set-test-world! (build-test-map ["-C-"]))
    (set-test-unit (test-utils/game-map-atom) "C" :mode :awake :hits 8 :fighter-count 2)
    (let [carrier-coords (:pos (get-test-unit (test-utils/game-map-atom) "C"))]
      (wake-fighters-on-carrier carrier-coords)
      (let [carrier (:contents (get-in (test-utils/read-test-state :game-map) carrier-coords))]
        (should= :sentry (:mode carrier))
        (should= 2 (:fighter-count carrier))
        (should= 2 (:awake-fighters carrier))))))

(describe "sleep-fighters-on-carrier"
  (before (reset-all-atoms!))

  (it "puts fighters to sleep and wakes carrier"
    (set-test-world! (build-test-map ["-C-"]))
    (set-test-unit (test-utils/game-map-atom) "C" :mode :sentry :hits 8 :fighter-count 2 :awake-fighters 2)
    (let [carrier-coords (:pos (get-test-unit (test-utils/game-map-atom) "C"))]
      (sleep-fighters-on-carrier carrier-coords)
      (let [carrier (:contents (get-in (test-utils/read-test-state :game-map) carrier-coords))]
        (should= :awake (:mode carrier))
        (should= 2 (:fighter-count carrier))
        (should= 0 (:awake-fighters carrier))))))

(describe "launch-fighter-from-carrier"
  (before (reset-all-atoms!))

  (it "removes fighter and places it at adjacent cell"
    (set-test-world! (build-test-map ["-C~-"]))
    (set-test-unit (test-utils/game-map-atom) "C" :mode :sentry :hits 8 :fighter-count 2 :awake-fighters 2)
    (let [carrier-coords (:pos (get-test-unit (test-utils/game-map-atom) "C"))
          adjacent-cell [(inc (first carrier-coords)) (second carrier-coords)]
          target-coords [(+ 2 (first carrier-coords)) (second carrier-coords)]]
      (launch-fighter-from-carrier carrier-coords target-coords)
      (let [carrier (:contents (get-in (test-utils/read-test-state :game-map) carrier-coords))
            launched-fighter (:contents (get-in (test-utils/read-test-state :game-map) adjacent-cell))]
        (should= 1 (:fighter-count carrier))
        (should= 1 (:awake-fighters carrier))
        (should= :fighter (:type launched-fighter))
        (should= :moving (:mode launched-fighter))
        (should= target-coords (:target launched-fighter)))))

  (it "keeps carrier in sentry mode after last fighter launches"
    (set-test-world! (build-test-map ["-C~-"]))
    (set-test-unit (test-utils/game-map-atom) "C" :mode :sentry :hits 8 :fighter-count 1 :awake-fighters 1)
    (let [carrier-coords (:pos (get-test-unit (test-utils/game-map-atom) "C"))
          target-coords [(+ 2 (first carrier-coords)) (second carrier-coords)]]
      (launch-fighter-from-carrier carrier-coords target-coords)
      (let [carrier (:contents (get-in (test-utils/read-test-state :game-map) carrier-coords))]
        (should= :sentry (:mode carrier))
        (should= 0 (:fighter-count carrier)))))

  (it "launches fighter toward target in negative x direction"
    (set-test-world! (build-test-map ["-~C-"]))
    ;; After transpose: C at [2 0], ~ at [1 0]
    (set-test-unit (test-utils/game-map-atom) "C" :mode :sentry :hits 8 :fighter-count 1 :awake-fighters 1)
    (let [carrier-coords (:pos (get-test-unit (test-utils/game-map-atom) "C"))
          target-coords [(- (first carrier-coords) 2) (second carrier-coords)]
          expected-step [(dec (first carrier-coords)) (second carrier-coords)]
          result (launch-fighter-from-carrier carrier-coords target-coords)]
      (should= expected-step result)
      (let [fighter (:contents (get-in (test-utils/read-test-state :game-map) expected-step))]
        (should= :fighter (:type fighter))
        (should= target-coords (:target fighter)))))

  (it "launches fighter toward target in y direction"
    (set-test-world! (build-test-map ["--"
                                             "-C"
                                             "-~"
                                             "--"]))
    ;; After transpose: C at [1 1], ~ at [1 2]
    (set-test-unit (test-utils/game-map-atom) "C" :mode :sentry :hits 8 :fighter-count 1 :awake-fighters 1)
    (let [carrier-coords (:pos (get-test-unit (test-utils/game-map-atom) "C"))
          target-coords [(first carrier-coords) (+ 2 (second carrier-coords))]
          expected-step [(first carrier-coords) (inc (second carrier-coords))]
          result (launch-fighter-from-carrier carrier-coords target-coords)]
      (should= expected-step result)
      (let [fighter (:contents (get-in (test-utils/read-test-state :game-map) expected-step))]
        (should= :fighter (:type fighter))
        (should= 1 (:hits fighter)))))

  (it "launches fighter toward target in negative y direction"
    ;; Carrier at y=3, target at y=1, so dy should be -1
    ;; This kills the mutant (+ ty cy) because ty=1, cy=3, (+ 1 3)=4>0 gives dy=1 (wrong)
    (set-test-world! (build-test-map ["----"
                                             "----"
                                             "----"
                                             "-~C-"
                                             "----"]))
    ;; After transpose: C at [2 3], ~ at [1 3]
    (set-test-unit (test-utils/game-map-atom) "C" :mode :sentry :hits 8 :fighter-count 1 :awake-fighters 1)
    (let [carrier-coords (:pos (get-test-unit (test-utils/game-map-atom) "C"))
          ;; Target at same x, y=1 (negative y direction)
          target-coords [(first carrier-coords) 1]
          expected-step [(first carrier-coords) (dec (second carrier-coords))]
          result (launch-fighter-from-carrier carrier-coords target-coords)]
      (should= expected-step result)
      (let [fighter (:contents (get-in (test-utils/read-test-state :game-map) expected-step))]
        (should= :fighter (:type fighter))
        (should= 1 (:hits fighter))
        (should= target-coords (:target fighter)))))

  (it "launches fighter along x-axis when target at same y"
    ;; Kills M27: (- ty cy) → (+ ty cy) in zero? check
    ;; When carrier at y=1 and target at y=1, (+ 1 1) = 2 ≠ 0 makes mutant give wrong dy
    (set-test-world! (build-test-map ["---"
                                             "-C~"
                                             "---"]))
    ;; After transpose: C at [1 1], ~ at [2 1]
    (set-test-unit (test-utils/game-map-atom) "C" :mode :sentry :hits 8 :fighter-count 1 :awake-fighters 1)
    (let [carrier-coords (:pos (get-test-unit (test-utils/game-map-atom) "C"))
          target-coords [(+ 2 (first carrier-coords)) (second carrier-coords)]
          expected-step [(inc (first carrier-coords)) (second carrier-coords)]
          result (launch-fighter-from-carrier carrier-coords target-coords)]
      (should= expected-step result)
      (let [fighter (:contents (get-in (test-utils/read-test-state :game-map) expected-step))]
        (should= :fighter (:type fighter))
        (should= (second carrier-coords) (second (:target fighter))))))

  (it "sets steps-remaining to speed minus one"
    (set-test-world! (build-test-map ["-C~-"]))
    (set-test-unit (test-utils/game-map-atom) "C" :mode :sentry :hits 8 :fighter-count 1 :awake-fighters 1)
    (let [carrier-coords (:pos (get-test-unit (test-utils/game-map-atom) "C"))
          adjacent-cell [(inc (first carrier-coords)) (second carrier-coords)]
          target-coords [(+ 2 (first carrier-coords)) (second carrier-coords)]]
      (launch-fighter-from-carrier carrier-coords target-coords)
      (let [fighter (:contents (get-in (test-utils/read-test-state :game-map) adjacent-cell))]
        (should= 7 (:steps-remaining fighter))))))

(describe "launch-fighter-from-airport"
  (before (reset-all-atoms!))

  (it "removes fighter from airport and places it moving"
    (set-test-world! (build-test-map ["-O#-"]))
    (update-test-world! assoc-in [1 0 :fighter-count] 2)
    (update-test-world! assoc-in [1 0 :awake-fighters] 2)
    (launch-fighter-from-airport [1 0] [3 0])
    (let [world (test-utils/read-test-state :game-map)
          city (get-in world [1 0])
          fighter (get-in world [2 0 :contents])]
      (should= 1 (:fighter-count city))
      (should= 1 (:awake-fighters city))
      (should= :fighter (:type fighter))
      (should= :moving (:mode fighter))
      (should= [3 0] (:target fighter))
      (should= 1 (:hits fighter)))))

  (it "uses a second adjacent cell when the nearest launch cell is occupied"
    (set-test-world! (build-test-map ["---"
                                      "-O-"
                                      "---"]))
    (update-test-world! assoc-in [1 1 :fighter-count] 2)
    (update-test-world! assoc-in [1 1 :awake-fighters] 2)
    (launch-fighter-from-airport [1 1] [3 1])
    (launch-fighter-from-airport [1 1] [3 1])
    (let [world (test-utils/read-test-state :game-map)]
      (should= :fighter (get-in world [2 1 :contents :type]))
      (should= :fighter (get-in world [2 0 :contents :type]))
      (should= 0 (get-in world [1 1 :awake-fighters]))
      (should= 0 (get-in world [1 1 :fighter-count]))))

  (it "finishes the launch when the target is the adjacent land cell"
    (set-test-world! (build-test-map ["-O#"]))
    (update-test-world! assoc-in [1 0 :fighter-count] 1)
    (update-test-world! assoc-in [1 0 :awake-fighters] 1)
    (launch-fighter-from-airport [1 0] [2 0])
    (let [world (test-utils/read-test-state :game-map)
          fighter (get-in world [2 0 :contents])]
      (should= :fighter (:type fighter))
      (should= :awake (:mode fighter))
      (should= 0 (:steps-remaining fighter))
      (should-be-nil (:target fighter))))
