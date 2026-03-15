(ns empire.game-mechanics.movement.fighter-carrier-spec
  (:require [empire.test.utils :as test-utils]
            [empire.game-mechanics.containers.ops :as container-ops]
            [empire.game.loop.core :as game-loop]
            [empire.game-mechanics.movement.api :refer :all]
            [empire.test.utils :refer [build-test-map get-test-unit set-test-unit reset-all-atoms! set-test-player-map! make-initial-test-map set-test-world! update-test-world!]]
            [speclj.core :refer :all]))

(describe "carrier fighter deployment"
  (before (reset-all-atoms!))
  (it "fighter lands on carrier and sleeps"
    (set-test-world! (build-test-map ["-JC"]))
    (let [fighter-coords (:pos (get-test-unit (test-utils/game-map-atom) "J"))
          carrier-coords (:pos (get-test-unit (test-utils/game-map-atom) "C"))]
      (set-test-unit (test-utils/game-map-atom) "J" :mode :moving :target carrier-coords :fuel 10 :steps-remaining 1)
      (set-test-unit (test-utils/game-map-atom) "C" :mode :sentry :hits 8)
      (set-test-player-map! (make-initial-test-map 1 3 nil))
      (game-loop/move-current-unit fighter-coords)
      (let [carrier-cell (get-in (test-utils/read-test-state :game-map) carrier-coords)
            carrier (:contents carrier-cell)]
        (should= :carrier (:type carrier))
        (should= 1 (:fighter-count carrier))
        (should= 0 (:awake-fighters carrier 0)))))

  (it "wake-fighters-on-carrier wakes all fighters and sets carrier to sentry"
    (set-test-world! (build-test-map ["-C-"]))
    (let [carrier-coords (:pos (get-test-unit (test-utils/game-map-atom) "C"))]
      (set-test-unit (test-utils/game-map-atom) "C" :mode :awake :hits 8 :fighter-count 2)
      (container-ops/wake-fighters-on-carrier carrier-coords)
      (let [carrier (:contents (get-in (test-utils/read-test-state :game-map) carrier-coords))]
        (should= :sentry (:mode carrier))
        (should= 2 (:fighter-count carrier))
        (should= 2 (:awake-fighters carrier)))))

  (it "sleep-fighters-on-carrier puts fighters to sleep and wakes carrier"
    (set-test-world! (build-test-map ["-C-"]))
    (let [carrier-coords (:pos (get-test-unit (test-utils/game-map-atom) "C"))]
      (set-test-unit (test-utils/game-map-atom) "C" :mode :sentry :hits 8 :fighter-count 2 :awake-fighters 2)
      (container-ops/sleep-fighters-on-carrier carrier-coords)
      (let [carrier (:contents (get-in (test-utils/read-test-state :game-map) carrier-coords))]
        (should= :awake (:mode carrier))
        (should= 2 (:fighter-count carrier))
        (should= 0 (:awake-fighters carrier)))))

  (it "launch-fighter-from-carrier removes fighter and places it at adjacent cell"
    (set-test-world! (build-test-map ["-C~-"]))
    (let [carrier-coords (:pos (get-test-unit (test-utils/game-map-atom) "C"))
          adjacent-coords [(inc (first carrier-coords)) (second carrier-coords)]
          target-coords [(+ (first carrier-coords) 2) (second carrier-coords)]]
      (set-test-unit (test-utils/game-map-atom) "C" :mode :sentry :hits 8 :fighter-count 2 :awake-fighters 2)
      (set-test-player-map! (make-initial-test-map 1 4 nil))
      (container-ops/launch-fighter-from-carrier carrier-coords target-coords)
      (let [carrier (:contents (get-in (test-utils/read-test-state :game-map) carrier-coords))
            launched-fighter (:contents (get-in (test-utils/read-test-state :game-map) adjacent-coords))]
        (should= 1 (:fighter-count carrier))
        (should= 1 (:awake-fighters carrier))
        (should= :fighter (:type launched-fighter))
        (should= :moving (:mode launched-fighter))
        (should= target-coords (:target launched-fighter)))))

  (it "launch-fighter-from-carrier keeps carrier in sentry mode after last fighter launches"
    (set-test-world! (build-test-map ["-C~-"]))
    (let [carrier-coords (:pos (get-test-unit (test-utils/game-map-atom) "C"))
          target-coords [(+ (first carrier-coords) 2) (second carrier-coords)]]
      (set-test-unit (test-utils/game-map-atom) "C" :mode :sentry :hits 8 :fighter-count 1 :awake-fighters 1)
      (set-test-player-map! (make-initial-test-map 1 4 nil))
      (container-ops/launch-fighter-from-carrier carrier-coords target-coords)
      (let [carrier (:contents (get-in (test-utils/read-test-state :game-map) carrier-coords))]
        (should= :sentry (:mode carrier))
        (should= 0 (:fighter-count carrier)))))

  (it "launch-fighter-from-carrier sets steps-remaining to speed minus one"
    (set-test-world! (build-test-map ["-C~-"]))
    (let [carrier-coords (:pos (get-test-unit (test-utils/game-map-atom) "C"))
          adjacent-coords [(inc (first carrier-coords)) (second carrier-coords)]
          target-coords [(+ (first carrier-coords) 2) (second carrier-coords)]]
      (set-test-unit (test-utils/game-map-atom) "C" :mode :sentry :hits 8 :fighter-count 1 :awake-fighters 1)
      (set-test-player-map! (make-initial-test-map 1 4 nil))
      (container-ops/launch-fighter-from-carrier carrier-coords target-coords)
      (let [fighter (:contents (get-in (test-utils/read-test-state :game-map) adjacent-coords))]
        (should= 7 (:steps-remaining fighter)))))

  (it "get-active-unit returns synthetic fighter when carrier has awake fighters"
    (let [cell {:type :sea :contents {:type :carrier :mode :sentry :owner :player :fighter-count 3 :awake-fighters 2}}]
      (let [active (get-active-unit cell)]
        (should= :fighter (:type active))
        (should= :awake (:mode active))
        (should= true (:from-carrier active)))))

  (it "get-active-unit returns carrier when no awake fighters"
    (let [cell {:type :sea :contents {:type :carrier :mode :awake :owner :player :fighter-count 1 :awake-fighters 0}}]
      (let [active (get-active-unit cell)]
        (should= :carrier (:type active))
        (should= :awake (:mode active)))))

  (it "is-fighter-from-carrier? returns true for synthetic fighter with :from-carrier"
    (let [fighter {:type :fighter :mode :awake :owner :player :from-carrier true}]
      (should= true (is-fighter-from-carrier? fighter))))

  (it "is-fighter-from-carrier? returns falsy for fighter without :from-carrier"
    (let [fighter {:type :fighter :mode :awake :owner :player :hits 1}]
      (should-not (is-fighter-from-carrier? fighter))))

  (it "fighter launched from carrier and landing back has awake-fighters 0"
    (set-test-world! (build-test-map ["-C~~"]))
    (let [carrier-coords (:pos (get-test-unit (test-utils/game-map-atom) "C"))
          adjacent-coords [(inc (first carrier-coords)) (second carrier-coords)]
          target-coords [(+ (first carrier-coords) 2) (second carrier-coords)]]
      (set-test-unit (test-utils/game-map-atom) "C" :mode :sentry :hits 8 :fighter-count 1 :awake-fighters 1)
      (set-test-player-map! (make-initial-test-map 1 4 nil))
      (container-ops/launch-fighter-from-carrier carrier-coords target-coords)
      (let [carrier (:contents (get-in (test-utils/read-test-state :game-map) carrier-coords))]
        (should= 0 (:fighter-count carrier))
        (should= 0 (:awake-fighters carrier)))
      (let [fighter-cell (get-in (test-utils/read-test-state :game-map) adjacent-coords)
            fighter (:contents fighter-cell)
            returning-fighter (assoc fighter :target carrier-coords :steps-remaining 1)]
        (update-test-world! assoc-in (conj adjacent-coords :contents) returning-fighter)
        (game-loop/move-current-unit adjacent-coords)
        (let [carrier (:contents (get-in (test-utils/read-test-state :game-map) carrier-coords))]
          (should= :carrier (:type carrier))
          (should= 1 (:fighter-count carrier))
          (should= 0 (:awake-fighters carrier 0))))))

  (it "fighter out of fuel crashing near carrier does not destroy carrier"
    (set-test-world! (build-test-map ["-JC"]))
    (let [fighter-coords (:pos (get-test-unit (test-utils/game-map-atom) "J"))
          carrier-coords (:pos (get-test-unit (test-utils/game-map-atom) "C"))]
      (set-test-unit (test-utils/game-map-atom) "J" :mode :moving :target carrier-coords :fuel 0 :steps-remaining 1)
      (set-test-unit (test-utils/game-map-atom) "C" :mode :sentry :hits 8 :fighter-count 1)
      (set-test-player-map! (make-initial-test-map 1 3 nil))
      (game-loop/move-current-unit fighter-coords)
      (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) fighter-coords)))
      (let [carrier-cell (get-in (test-utils/read-test-state :game-map) carrier-coords)
            carrier (:contents carrier-cell)]
        (should= :carrier (:type carrier))
        (should= 1 (:fighter-count carrier))))))
