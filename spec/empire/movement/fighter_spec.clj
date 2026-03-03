(ns empire.movement.fighter-spec
  (:require [empire.test-utils :as test-utils]
    [empire.containers.ops :as container-ops]
    [empire.game-loop :as game-loop]
    [empire.movement.api :refer :all]
    [empire.movement.wake-conditions :as wake]
    [empire.test-utils :refer [build-test-map get-test-unit get-test-city set-test-unit reset-all-atoms! set-test-player-map! make-initial-test-map set-test-world! update-test-world!]]
    [speclj.core :refer :all]))

(describe "fighter fuel"
  (before (reset-all-atoms!))
  (it "moves fighter and decrements fuel"
    (set-test-world! (build-test-map ["-F#"]))
    (let [fighter-coords (:pos (get-test-unit (test-utils/game-map-atom) "F"))
          target-coords [(inc (first fighter-coords)) (second fighter-coords)]]
      (set-test-unit (test-utils/game-map-atom) "F" :mode :moving :target target-coords :fuel 10 :steps-remaining 1)
      (set-test-player-map! (make-initial-test-map 1 3 nil))
      (game-loop/move-current-unit fighter-coords)
      (should= {:type :land} (get-in (test-utils/read-test-state :game-map) fighter-coords))
      (should= {:type :land :contents {:type :fighter :owner :player :hits 1 :steps-remaining 0 :mode :awake :fuel 9}} (get-in (test-utils/read-test-state :game-map) target-coords))))

  (it "fighter wakes when fuel reaches 0"
    (set-test-world! (build-test-map ["-F#"]))
    (let [fighter-coords (:pos (get-test-unit (test-utils/game-map-atom) "F"))
          target-coords [(inc (first fighter-coords)) (second fighter-coords)]]
      (set-test-unit (test-utils/game-map-atom) "F" :mode :moving :target target-coords :fuel 1 :steps-remaining 1)
      (set-test-player-map! (make-initial-test-map 1 3 nil))
      (game-loop/move-current-unit fighter-coords)
      (should= {:type :land} (get-in (test-utils/read-test-state :game-map) fighter-coords))
      (should= {:type :land :contents {:type :fighter :owner :player :hits 1 :steps-remaining 0 :mode :awake :fuel 0 :reason :fighter-out-of-fuel}} (get-in (test-utils/read-test-state :game-map) target-coords))))

  (it "fighter crashes when trying to move with 0 fuel"
    (set-test-world! (build-test-map ["-F#"]))
    (let [fighter-coords (:pos (get-test-unit (test-utils/game-map-atom) "F"))
          target-coords [(inc (first fighter-coords)) (second fighter-coords)]]
      (set-test-unit (test-utils/game-map-atom) "F" :mode :moving :target target-coords :fuel 0 :steps-remaining 1)
      (set-test-player-map! (make-initial-test-map 1 3 nil))
      (game-loop/move-current-unit fighter-coords)
      (should= {:type :land} (get-in (test-utils/read-test-state :game-map) fighter-coords))
      (should= {:type :land} (get-in (test-utils/read-test-state :game-map) target-coords))
      (should= 2 (count (filter (complement nil?) (flatten (test-utils/read-test-state :game-map)))))))

  (it "fighter lands in city, refuels, and awakens"
    (set-test-world! (build-test-map ["-FO"]))
    (let [fighter-coords (:pos (get-test-unit (test-utils/game-map-atom) "F"))
          city-coords (:pos (get-test-city (test-utils/game-map-atom) "O"))]
      (set-test-unit (test-utils/game-map-atom) "F" :mode :moving :target city-coords :fuel 5 :steps-remaining 1)
      (set-test-player-map! (make-initial-test-map 1 3 nil))
      (game-loop/move-current-unit fighter-coords)
      (should= {:type :land} (get-in (test-utils/read-test-state :game-map) fighter-coords))
      (let [city-cell (get-in (test-utils/read-test-state :game-map) city-coords)]
        (should= :city (:type city-cell))
        (should= :player (:city-status city-cell))
        (should= 1 (:fighter-count city-cell))
        (should= 0 (:awake-fighters city-cell 0)))))


  (it "fighter safely lands at friendly city"
    (set-test-world! (build-test-map ["-FO"]))
    (let [fighter-coords (:pos (get-test-unit (test-utils/game-map-atom) "F"))
          city-coords (:pos (get-test-city (test-utils/game-map-atom) "O"))]
      (set-test-unit (test-utils/game-map-atom) "F" :mode :moving :target city-coords :fuel 10 :steps-remaining 1)
      (set-test-player-map! (make-initial-test-map 1 3 nil))
      (test-utils/set-test-state! :error-message "")
      (game-loop/move-current-unit fighter-coords)
      (let [city-cell (get-in (test-utils/read-test-state :game-map) city-coords)]
        (should= 1 (:fighter-count city-cell))
        (should= 0 (:awake-fighters city-cell 0)))
      (should= "" (test-utils/read-test-state :error-message))))

  (it "fighter wakes before flying over free city"
    (set-test-world! (build-test-map ["F+#"]))
    (let [fighter-coords (:pos (get-test-unit (test-utils/game-map-atom) "F"))
          city-coords (:pos (get-test-city (test-utils/game-map-atom) "+"))
          target-coords [(+ (first fighter-coords) 2) (second fighter-coords)]]
      (set-test-unit (test-utils/game-map-atom) "F" :mode :moving :target target-coords :fuel 10 :steps-remaining 1)
      (set-test-player-map! (make-initial-test-map 1 3 nil))
      (game-loop/move-current-unit fighter-coords)
      ;; Fighter should stay at starting position, awake
      (let [fighter (:contents (get-in (test-utils/read-test-state :game-map) fighter-coords))]
        (should= :fighter (:type fighter))
        (should= :awake (:mode fighter))
        (should= :fighter-over-defended-city (:reason fighter)))
      ;; City should be empty
      (should= nil (:contents (get-in (test-utils/read-test-state :game-map) city-coords)))))

  (it "fighter wakes before flying over computer city"
    (set-test-world! (build-test-map ["FX#"]))
    (let [fighter-coords (:pos (get-test-unit (test-utils/game-map-atom) "F"))
          city-coords (:pos (get-test-city (test-utils/game-map-atom) "X"))
          target-coords [(+ (first fighter-coords) 2) (second fighter-coords)]]
      (set-test-unit (test-utils/game-map-atom) "F" :mode :moving :target target-coords :fuel 10 :steps-remaining 1)
      (set-test-player-map! (make-initial-test-map 1 3 nil))
      (game-loop/move-current-unit fighter-coords)
      ;; Fighter should stay at starting position, awake
      (let [fighter (:contents (get-in (test-utils/read-test-state :game-map) fighter-coords))]
        (should= :fighter (:type fighter))
        (should= :awake (:mode fighter))
        (should= :fighter-over-defended-city (:reason fighter)))
      ;; City should be empty
      (should= nil (:contents (get-in (test-utils/read-test-state :game-map) city-coords)))))

  (it "fighter wakes with bingo warning when fuel at 25% and friendly city in range"
    (set-test-world! (build-test-map ["O--F#-#"]))
    (let [fighter-coords (:pos (get-test-unit (test-utils/game-map-atom) "F"))
          target-coords [6 (second fighter-coords)]
          dest-coords [(inc (first fighter-coords)) (second fighter-coords)]]
      (set-test-unit (test-utils/game-map-atom) "F" :mode :moving :target target-coords :fuel 8 :steps-remaining 1)
      (set-test-player-map! (make-initial-test-map 1 7 nil))
      (game-loop/move-current-unit fighter-coords)
      ;; Fighter should wake up with bingo warning
      (let [fighter (:contents (get-in (test-utils/read-test-state :game-map) dest-coords))]
        (should= :fighter (:type fighter))
        (should= :awake (:mode fighter))
        (should= :fighter-bingo (:reason fighter)))))

  (it "fighter does not wake with bingo warning when no friendly city in range"
    (set-test-world! (build-test-map ["O--------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----F#---"]))
    (let [fighter-coords (:pos (get-test-unit (test-utils/game-map-atom) "F"))
          target-coords [(inc (first fighter-coords)) (second fighter-coords)]]
      (set-test-unit (test-utils/game-map-atom) "F" :mode :moving :target target-coords :fuel 3 :steps-remaining 1)
      (set-test-player-map! (make-initial-test-map 5 9 nil))
      (game-loop/move-current-unit fighter-coords)
      ;; Fighter should wake at target, not due to bingo (city at [0 0] is distance 9, beyond fuel 2)
      (let [fighter (:contents (get-in (test-utils/read-test-state :game-map) target-coords))]
        (should= :fighter (:type fighter))
        (should= :awake (:mode fighter))
        (should= nil (:reason fighter)))))

  (it "fighter does not wake with bingo warning when fuel above 25%"
    (set-test-world! (build-test-map ["O--F#"]))
    (let [fighter-coords (:pos (get-test-unit (test-utils/game-map-atom) "F"))
          target-coords [(inc (first fighter-coords)) (second fighter-coords)]]
      (set-test-unit (test-utils/game-map-atom) "F" :mode :moving :target target-coords :fuel 10 :steps-remaining 1)
      (set-test-player-map! (make-initial-test-map 1 5 nil))
      (game-loop/move-current-unit fighter-coords)
      ;; Fighter should wake at target, not due to bingo (fuel 10 > 8 = 25% of 32)
      (let [fighter (:contents (get-in (test-utils/read-test-state :game-map) target-coords))]
        (should= :fighter (:type fighter))
        (should= :awake (:mode fighter))
        (should= nil (:reason fighter)))))

  (it "fighter does not wake with bingo when target is a reachable friendly city"
    (set-test-world! (build-test-map ["F#-O"]))
    (let [fighter-coords (:pos (get-test-unit (test-utils/game-map-atom) "F"))
          city-coords (:pos (get-test-city (test-utils/game-map-atom) "O"))
          dest-coords [(inc (first fighter-coords)) (second fighter-coords)]]
      ;; Fighter with fuel 8 (bingo level), target is friendly city
      (set-test-unit (test-utils/game-map-atom) "F" :mode :moving :target city-coords :fuel 8 :steps-remaining 1)
      (set-test-player-map! (make-initial-test-map 1 4 nil))
      (game-loop/move-current-unit fighter-coords)
      ;; Fighter should NOT bingo - target city is 2 cells away, fuel 7 after move is sufficient
      (let [fighter (:contents (get-in (test-utils/read-test-state :game-map) dest-coords))]
        (should= :fighter (:type fighter))
        (should= :moving (:mode fighter))
        (should= nil (:reason fighter)))))

  (it "fighter does not wake with bingo when target is a reachable friendly carrier"
    (set-test-world! (build-test-map ["O---"
                                             "F~C-"]))
    (let [fighter-coords (:pos (get-test-unit (test-utils/game-map-atom) "F"))
          carrier-coords (:pos (get-test-unit (test-utils/game-map-atom) "C"))
          dest-coords [(inc (first fighter-coords)) (second fighter-coords)]]
      ;; Fighter with fuel 8 (bingo level), target is carrier
      ;; Distance to carrier is 2, worst-case fuel needed = 2 * 4/3 = 2.67, so 8 fuel is enough
      (set-test-unit (test-utils/game-map-atom) "F" :mode :moving :target carrier-coords :fuel 8 :steps-remaining 1)
      (set-test-unit (test-utils/game-map-atom) "C" :mode :sentry)
      (set-test-player-map! (make-initial-test-map 2 4 nil))
      (game-loop/move-current-unit fighter-coords)
      ;; Fighter should NOT bingo - carrier is reachable even if moving away
      (let [fighter (:contents (get-in (test-utils/read-test-state :game-map) dest-coords))]
        (should= :fighter (:type fighter))
        (should= :moving (:mode fighter))
        (should= nil (:reason fighter)))))

  (it "fighter wakes with bingo when carrier is too far to reach"
    (set-test-world! (build-test-map ["O---------"
                                             "F~------C-"]))
    (let [fighter-coords (:pos (get-test-unit (test-utils/game-map-atom) "F"))
          carrier-coords (:pos (get-test-unit (test-utils/game-map-atom) "C"))
          dest-coords [(inc (first fighter-coords)) (second fighter-coords)]]
      ;; Fighter with fuel 6 (bingo level), target is carrier
      ;; Distance after move is 6, worst-case fuel needed = 6 * 4/3 = 8 > 5
      (set-test-unit (test-utils/game-map-atom) "F" :mode :moving :target carrier-coords :fuel 6 :steps-remaining 1)
      (set-test-unit (test-utils/game-map-atom) "C" :mode :sentry)
      (set-test-player-map! (make-initial-test-map 2 10 nil))
      (game-loop/move-current-unit fighter-coords)
      ;; Fighter should bingo - carrier too far (needs 8 fuel, only has 5 after move)
      (let [fighter (:contents (get-in (test-utils/read-test-state :game-map) dest-coords))]
        (should= :fighter (:type fighter))
        (should= :awake (:mode fighter))
        (should= :fighter-bingo (:reason fighter))))))

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
    ;; Simulate: launch a fighter, have it fly and return to carrier
    ;; awake-fighters should be 0 after landing
    (set-test-world! (build-test-map ["-C~~"]))
    (let [carrier-coords (:pos (get-test-unit (test-utils/game-map-atom) "C"))
          adjacent-coords [(inc (first carrier-coords)) (second carrier-coords)]
          target-coords [(+ (first carrier-coords) 2) (second carrier-coords)]]
      (set-test-unit (test-utils/game-map-atom) "C" :mode :sentry :hits 8 :fighter-count 1 :awake-fighters 1)
      (set-test-player-map! (make-initial-test-map 1 4 nil))
      ;; Launch fighter from carrier toward target
      (container-ops/launch-fighter-from-carrier carrier-coords target-coords)
      ;; Verify carrier now has 0 fighters
      (let [carrier (:contents (get-in (test-utils/read-test-state :game-map) carrier-coords))]
        (should= 0 (:fighter-count carrier))
        (should= 0 (:awake-fighters carrier)))
      ;; Fighter is at adjacent-coords moving toward target
      ;; Now simulate fighter returning to carrier - set its target to carrier
      (let [fighter-cell (get-in (test-utils/read-test-state :game-map) adjacent-coords)
            fighter (:contents fighter-cell)
            returning-fighter (assoc fighter :target carrier-coords :steps-remaining 1)]
        (update-test-world! assoc-in (conj adjacent-coords :contents) returning-fighter)
        ;; Move fighter back to carrier
        (game-loop/move-current-unit adjacent-coords)
        ;; Verify fighter landed and is sleeping
        (let [carrier (:contents (get-in (test-utils/read-test-state :game-map) carrier-coords))]
          (should= :carrier (:type carrier))
          (should= 1 (:fighter-count carrier))
          (should= 0 (:awake-fighters carrier 0))))))

  (it "fighter out of fuel crashing near carrier does not destroy carrier"
    ;; Fighter with fuel 0 adjacent to carrier - when it tries to land, it crashes
    ;; but the carrier should remain intact
    (set-test-world! (build-test-map ["-JC"]))
    (let [fighter-coords (:pos (get-test-unit (test-utils/game-map-atom) "J"))
          carrier-coords (:pos (get-test-unit (test-utils/game-map-atom) "C"))]
      (set-test-unit (test-utils/game-map-atom) "J" :mode :moving :target carrier-coords :fuel 0 :steps-remaining 1)
      (set-test-unit (test-utils/game-map-atom) "C" :mode :sentry :hits 8 :fighter-count 1)
      (set-test-player-map! (make-initial-test-map 1 3 nil))
      (game-loop/move-current-unit fighter-coords)
      ;; Fighter should be gone (crashed)
      (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) fighter-coords)))
      ;; Carrier should still exist with its original fighter count
      (let [carrier-cell (get-in (test-utils/read-test-state :game-map) carrier-coords)
            carrier (:contents carrier-cell)]
        (should= :carrier (:type carrier))
        (should= 1 (:fighter-count carrier))))))

(describe "fighter shot down by city"
  (before (reset-all-atoms!))
  (it "fighter is destroyed when flying into hostile city"
    (set-test-world! (build-test-map ["-FX"]))
    (let [fighter-coords (:pos (get-test-unit (test-utils/game-map-atom) "F"))
          city-coords (:pos (get-test-city (test-utils/game-map-atom) "X"))]
      (set-test-unit (test-utils/game-map-atom) "F" :mode :moving :target city-coords :fuel 10 :steps-remaining 1 :hits 1)
      (set-test-player-map! (make-initial-test-map 1 3 nil))
      (test-utils/set-test-state! :error-message "")
      ;; wake-after-move takes unit, from-pos, final-pos, and current-map (atom)
      (let [cell (get-in (test-utils/read-test-state :game-map) fighter-coords)
            unit (:contents cell)
            result (wake/wake-after-move unit fighter-coords city-coords (test-utils/game-map-atom))]
        (should= 0 (:hits result))))))

(describe "fighter landing at city"
  (before (reset-all-atoms!))
  (it "fighter lands at city and increments fighter-count"
    (set-test-world! (build-test-map ["-FO"]))
    (let [fighter-coords (:pos (get-test-unit (test-utils/game-map-atom) "F"))
          city-coords (:pos (get-test-city (test-utils/game-map-atom) "O"))]
      (set-test-unit (test-utils/game-map-atom) "F" :mode :moving :target city-coords :fuel 10 :steps-remaining 1 :hits 1)
      (update-test-world! assoc-in (conj city-coords :fighter-count) 0)
      (set-test-player-map! (make-initial-test-map 1 3 nil))
      (game-loop/move-current-unit fighter-coords)
      (let [city (get-in (test-utils/read-test-state :game-map) city-coords)]
        (should= 1 (:fighter-count city))
        (should-be-nil (:contents city)))))

  (it "fighter lands at city with army on it"
    (set-test-world! (build-test-map ["-FO"]))
    (let [fighter-coords (:pos (get-test-unit (test-utils/game-map-atom) "F"))
          city-coords (:pos (get-test-city (test-utils/game-map-atom) "O"))]
      (set-test-unit (test-utils/game-map-atom) "F" :mode :moving :target city-coords :fuel 10 :steps-remaining 1 :hits 1)
      (update-test-world! assoc-in (conj city-coords :fighter-count) 0)
      (update-test-world! assoc-in (conj city-coords :contents)
             {:type :army :mode :moving :target [2 0] :hits 1 :owner :player})
      (set-test-player-map! (make-initial-test-map 1 3 nil))
      (game-loop/move-current-unit fighter-coords)
      (let [city (get-in (test-utils/read-test-state :game-map) city-coords)]
        (should= 1 (:fighter-count city))
        (should= :army (:type (:contents city)))))))

(describe "get-active-unit airport fighter"
  (before (reset-all-atoms!))
  (it "returns synthetic fighter when city has awake airport fighters"
    (let [cell {:type :city :city-status :player :fighter-count 2 :awake-fighters 1}]
      (let [active (get-active-unit cell)]
        (should= :fighter (:type active))
        (should= :awake (:mode active))
        (should= true (:from-airport active)))))

  (it "is-fighter-from-airport? returns true for synthetic airport fighter"
    (let [fighter {:type :fighter :mode :awake :owner :player :from-airport true}]
      (should= true (is-fighter-from-airport? fighter))))

  (it "is-fighter-from-airport? returns falsy for regular fighter"
    (let [fighter {:type :fighter :mode :awake :owner :player :hits 1}]
      (should-not (is-fighter-from-airport? fighter)))))

(describe "launch-fighter-from-airport"
  (before (reset-all-atoms!))
  (it "removes awake fighter from airport and places it moving"
    (set-test-world! (build-test-map ["-O#-"]))
    (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "O"))
          target-coords [(+ (first city-coords) 2) (second city-coords)]]
      (update-test-world! assoc-in (conj city-coords :fighter-count) 2)
      (update-test-world! assoc-in (conj city-coords :awake-fighters) 2)
      (set-test-player-map! (make-initial-test-map 1 4 nil))
      (container-ops/launch-fighter-from-airport city-coords target-coords)
      (let [city (get-in (test-utils/read-test-state :game-map) city-coords)
            fighter (:contents city)]
        (should= 1 (:fighter-count city))
        (should= 1 (:awake-fighters city))
        (should= :fighter (:type fighter))
        (should= :moving (:mode fighter))
        (should= target-coords (:target fighter))))))

(describe "fighter landing via do-move"
  (before (reset-all-atoms!))

  (it "fighter lands at player city — added to airport"
    (set-test-world! (build-test-map ["-O"
                                             "-#"]))
    (update-test-world! assoc-in [0 0 :contents]
           {:type :fighter :owner :player :hits 1 :fuel 10 :mode :moving :target [1 0]})
    (set-test-player-map! (make-initial-test-map 2 2 nil))
    (let [from [0 0]
          cell (get-in (test-utils/read-test-state :game-map) from)
          unit (:contents cell)]
      (do-move from [1 0] cell unit)
      (let [city (get-in (test-utils/read-test-state :game-map) [1 0])]
        (should= 1 (:fighter-count city))
        (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))))))

  (it "fighter lands on friendly carrier — added to carrier"
    (set-test-world! (build-test-map ["~~"
                                             "~~"]))
    (update-test-world! assoc-in [0 0 :contents]
           {:type :fighter :owner :player :hits 1 :fuel 10 :mode :moving :target [1 0]})
    (update-test-world! assoc-in [1 0 :contents]
           {:type :carrier :owner :player :hits 3 :fighter-count 0
            :awake-fighters 0 :mode :sentry})
    (set-test-player-map! (make-initial-test-map 2 2 nil))
    (let [from [0 0]
          cell (get-in (test-utils/read-test-state :game-map) from)
          unit (:contents cell)]
      (do-move from [1 0] cell unit)
      (should= 1 (get-in (test-utils/read-test-state :game-map) [1 0 :contents :fighter-count]))
      (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))))))
