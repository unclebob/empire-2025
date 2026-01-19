(ns empire.movement.fighter-spec
  (:require
    [empire.atoms :as atoms]
    [empire.game-loop :as game-loop]
    [empire.movement.movement :refer :all]
    [empire.test-utils :refer [build-test-map get-test-unit get-test-city set-test-unit reset-all-atoms! make-initial-test-map]]
    [speclj.core :refer :all]))

(describe "fighter fuel"
  (before (reset-all-atoms!))
  (it "moves fighter and decrements fuel"
    (reset! atoms/game-map (build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----F#---"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (let [fighter-coords (:pos (get-test-unit atoms/game-map "F"))
          target-coords [(first fighter-coords) (inc (second fighter-coords))]]
      (set-test-unit atoms/game-map "F" :mode :moving :target target-coords :fuel 10 :steps-remaining 1)
      (reset! atoms/player-map (make-initial-test-map 9 9 nil))
      (game-loop/move-current-unit fighter-coords)
      (should= {:type :land} (get-in @atoms/game-map fighter-coords))
      (should= {:type :land :contents {:type :fighter :mode :awake :owner :player :fuel 9 :steps-remaining 0}} (get-in @atoms/game-map target-coords))))

  (it "fighter wakes when fuel reaches 0"
    (reset! atoms/game-map (build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----F#---"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (let [fighter-coords (:pos (get-test-unit atoms/game-map "F"))
          target-coords [(first fighter-coords) (inc (second fighter-coords))]]
      (set-test-unit atoms/game-map "F" :mode :moving :target target-coords :fuel 1 :steps-remaining 1)
      (reset! atoms/player-map (make-initial-test-map 9 9 nil))
      (game-loop/move-current-unit fighter-coords)
      (should= {:type :land} (get-in @atoms/game-map fighter-coords))
      (should= {:type :land :contents {:type :fighter :mode :awake :owner :player :fuel 0 :reason :fighter-out-of-fuel :steps-remaining 0}} (get-in @atoms/game-map target-coords))))

  (it "fighter crashes when trying to move with 0 fuel"
    (reset! atoms/game-map (build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----F#---"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (let [fighter-coords (:pos (get-test-unit atoms/game-map "F"))
          target-coords [(first fighter-coords) (inc (second fighter-coords))]]
      (set-test-unit atoms/game-map "F" :mode :moving :target target-coords :fuel 0 :steps-remaining 1)
      (reset! atoms/player-map (make-initial-test-map 9 9 nil))
      (game-loop/move-current-unit fighter-coords)
      (should= {:type :land} (get-in @atoms/game-map fighter-coords))
      (should= {:type :land} (get-in @atoms/game-map target-coords))
      (should= 2 (count (filter (complement nil?) (flatten @atoms/game-map))))))

  (it "fighter lands in city, refuels, and awakens"
    (reset! atoms/game-map (build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----FO---"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (let [fighter-coords (:pos (get-test-unit atoms/game-map "F"))
          city-coords (:pos (get-test-city atoms/game-map "O"))]
      (set-test-unit atoms/game-map "F" :mode :moving :target city-coords :fuel 5 :steps-remaining 1)
      (reset! atoms/player-map (make-initial-test-map 9 9 nil))
      (game-loop/move-current-unit fighter-coords)
      (should= {:type :land} (get-in @atoms/game-map fighter-coords))
      (let [city-cell (get-in @atoms/game-map city-coords)]
        (should= :city (:type city-cell))
        (should= :player (:city-status city-cell))
        (should= 1 (:fighter-count city-cell))
        (should= 0 (:awake-fighters city-cell 0)))))


  (it "fighter safely lands at friendly city"
    (reset! atoms/game-map (build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----FO---"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (let [fighter-coords (:pos (get-test-unit atoms/game-map "F"))
          city-coords (:pos (get-test-city atoms/game-map "O"))]
      (set-test-unit atoms/game-map "F" :mode :moving :target city-coords :fuel 10 :steps-remaining 1)
      (reset! atoms/player-map (make-initial-test-map 9 9 nil))
      (reset! atoms/line3-message "")
      (game-loop/move-current-unit fighter-coords)
      (let [city-cell (get-in @atoms/game-map city-coords)]
        (should= 1 (:fighter-count city-cell))
        (should= 0 (:awake-fighters city-cell 0)))
      (should= "" @atoms/line3-message)))

  (it "fighter wakes before flying over free city"
    (reset! atoms/game-map (build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----F+#--"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (let [fighter-coords (:pos (get-test-unit atoms/game-map "F"))
          city-coords (:pos (get-test-city atoms/game-map "+"))
          target-coords [(first fighter-coords) (+ (second fighter-coords) 2)]]
      (set-test-unit atoms/game-map "F" :mode :moving :target target-coords :fuel 10 :steps-remaining 1)
      (reset! atoms/player-map (make-initial-test-map 9 9 nil))
      (game-loop/move-current-unit fighter-coords)
      ;; Fighter should stay at starting position, awake
      (let [fighter (:contents (get-in @atoms/game-map fighter-coords))]
        (should= :fighter (:type fighter))
        (should= :awake (:mode fighter))
        (should= :fighter-over-defended-city (:reason fighter)))
      ;; City should be empty
      (should= nil (:contents (get-in @atoms/game-map city-coords)))))

  (it "fighter wakes before flying over computer city"
    (reset! atoms/game-map (build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----FX#--"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (let [fighter-coords (:pos (get-test-unit atoms/game-map "F"))
          city-coords (:pos (get-test-city atoms/game-map "X"))
          target-coords [(first fighter-coords) (+ (second fighter-coords) 2)]]
      (set-test-unit atoms/game-map "F" :mode :moving :target target-coords :fuel 10 :steps-remaining 1)
      (reset! atoms/player-map (make-initial-test-map 9 9 nil))
      (game-loop/move-current-unit fighter-coords)
      ;; Fighter should stay at starting position, awake
      (let [fighter (:contents (get-in @atoms/game-map fighter-coords))]
        (should= :fighter (:type fighter))
        (should= :awake (:mode fighter))
        (should= :fighter-over-defended-city (:reason fighter)))
      ;; City should be empty
      (should= nil (:contents (get-in @atoms/game-map city-coords)))))

  (it "fighter wakes with bingo warning when fuel at 25% and friendly city in range"
    (reset! atoms/game-map (build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "O---F#--#"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (let [fighter-coords (:pos (get-test-unit atoms/game-map "F"))
          target-coords [(first fighter-coords) 8]
          dest-coords [(first fighter-coords) (inc (second fighter-coords))]]
      (set-test-unit atoms/game-map "F" :mode :moving :target target-coords :fuel 8 :steps-remaining 1)
      (reset! atoms/player-map (make-initial-test-map 9 9 nil))
      (game-loop/move-current-unit fighter-coords)
      ;; Fighter should wake up with bingo warning
      (let [fighter (:contents (get-in @atoms/game-map dest-coords))]
        (should= :fighter (:type fighter))
        (should= :awake (:mode fighter))
        (should= :fighter-bingo (:reason fighter)))))

  (it "fighter does not wake with bingo warning when no friendly city in range"
    (reset! atoms/game-map (build-test-map ["O--------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----F#---"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (let [fighter-coords (:pos (get-test-unit atoms/game-map "F"))
          target-coords [(first fighter-coords) (inc (second fighter-coords))]]
      (set-test-unit atoms/game-map "F" :mode :moving :target target-coords :fuel 3 :steps-remaining 1)
      (reset! atoms/player-map (make-initial-test-map 9 9 nil))
      (game-loop/move-current-unit fighter-coords)
      ;; Fighter should wake at target, not due to bingo (city at [0 0] is distance 5, beyond fuel 3)
      (let [fighter (:contents (get-in @atoms/game-map target-coords))]
        (should= :fighter (:type fighter))
        (should= :awake (:mode fighter))
        (should= nil (:reason fighter)))))

  (it "fighter does not wake with bingo warning when fuel above 25%"
    (reset! atoms/game-map (build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "O---F#---"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (let [fighter-coords (:pos (get-test-unit atoms/game-map "F"))
          target-coords [(first fighter-coords) (inc (second fighter-coords))]]
      (set-test-unit atoms/game-map "F" :mode :moving :target target-coords :fuel 10 :steps-remaining 1)
      (reset! atoms/player-map (make-initial-test-map 9 9 nil))
      (game-loop/move-current-unit fighter-coords)
      ;; Fighter should wake at target, not due to bingo (fuel 10 > 8 = 25% of 32)
      (let [fighter (:contents (get-in @atoms/game-map target-coords))]
        (should= :fighter (:type fighter))
        (should= :awake (:mode fighter))
        (should= nil (:reason fighter)))))

  (it "fighter does not wake with bingo when target is a reachable friendly city"
    (reset! atoms/game-map (build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----F#-O-"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (let [fighter-coords (:pos (get-test-unit atoms/game-map "F"))
          city-coords (:pos (get-test-city atoms/game-map "O"))
          dest-coords [(first fighter-coords) (inc (second fighter-coords))]]
      ;; Fighter with fuel 8 (bingo level), target is friendly city
      (set-test-unit atoms/game-map "F" :mode :moving :target city-coords :fuel 8 :steps-remaining 1)
      (reset! atoms/player-map (make-initial-test-map 9 9 nil))
      (game-loop/move-current-unit fighter-coords)
      ;; Fighter should NOT bingo - target city is 2 cells away, fuel 7 after move is sufficient
      (let [fighter (:contents (get-in @atoms/game-map dest-coords))]
        (should= :fighter (:type fighter))
        (should= :moving (:mode fighter))
        (should= nil (:reason fighter)))))

  (it "fighter does not wake with bingo when target is a reachable friendly carrier"
    (reset! atoms/game-map (build-test-map ["O--------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----F~C--"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (let [fighter-coords (:pos (get-test-unit atoms/game-map "F"))
          carrier-coords (:pos (get-test-unit atoms/game-map "C"))
          dest-coords [(first fighter-coords) (inc (second fighter-coords))]]
      ;; Fighter with fuel 8 (bingo level), target is carrier
      ;; Distance to carrier is 2, worst-case fuel needed = 2 * 4/3 = 2.67, so 8 fuel is enough
      (set-test-unit atoms/game-map "F" :mode :moving :target carrier-coords :fuel 8 :steps-remaining 1)
      (set-test-unit atoms/game-map "C" :mode :sentry)
      (reset! atoms/player-map (make-initial-test-map 9 9 nil))
      (game-loop/move-current-unit fighter-coords)
      ;; Fighter should NOT bingo - carrier is reachable even if moving away
      (let [fighter (:contents (get-in @atoms/game-map dest-coords))]
        (should= :fighter (:type fighter))
        (should= :moving (:mode fighter))
        (should= nil (:reason fighter)))))

  (it "fighter wakes with bingo when carrier is too far to reach"
    (reset! atoms/game-map (build-test-map ["O-----------"
                                             "------------"
                                             "------------"
                                             "------------"
                                             "----F~----C-"
                                             "------------"
                                             "------------"
                                             "------------"
                                             "------------"
                                             "------------"
                                             "------------"
                                             "------------"]))
    (let [fighter-coords (:pos (get-test-unit atoms/game-map "F"))
          carrier-coords (:pos (get-test-unit atoms/game-map "C"))
          dest-coords [(first fighter-coords) (inc (second fighter-coords))]]
      ;; Fighter with fuel 6 (bingo level), target is carrier
      ;; Distance after move is 5, worst-case fuel needed = 5 * 4/3 = 6.67 > 6
      (set-test-unit atoms/game-map "F" :mode :moving :target carrier-coords :fuel 6 :steps-remaining 1)
      (set-test-unit atoms/game-map "C" :mode :sentry)
      (reset! atoms/player-map (make-initial-test-map 12 12 nil))
      (game-loop/move-current-unit fighter-coords)
      ;; Fighter should bingo - carrier too far (needs 6.67 fuel, only has 6)
      (let [fighter (:contents (get-in @atoms/game-map dest-coords))]
        (should= :fighter (:type fighter))
        (should= :awake (:mode fighter))
        (should= :fighter-bingo (:reason fighter))))))

(describe "carrier fighter deployment"
  (before (reset-all-atoms!))
  (it "fighter lands on carrier and sleeps"
    (reset! atoms/game-map (build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----JC---"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (let [fighter-coords (:pos (get-test-unit atoms/game-map "J"))
          carrier-coords (:pos (get-test-unit atoms/game-map "C"))]
      (set-test-unit atoms/game-map "J" :mode :moving :target carrier-coords :fuel 10 :steps-remaining 1)
      (set-test-unit atoms/game-map "C" :mode :sentry :hits 8)
      (reset! atoms/player-map (make-initial-test-map 9 9 nil))
      (game-loop/move-current-unit fighter-coords)
      (let [carrier-cell (get-in @atoms/game-map carrier-coords)
            carrier (:contents carrier-cell)]
        (should= :carrier (:type carrier))
        (should= 1 (:fighter-count carrier))
        (should= 0 (:awake-fighters carrier 0)))))

  (it "wake-fighters-on-carrier wakes all fighters and sets carrier to sentry"
    (reset! atoms/game-map (build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----C----"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (let [carrier-coords (:pos (get-test-unit atoms/game-map "C"))]
      (set-test-unit atoms/game-map "C" :mode :awake :hits 8 :fighter-count 2)
      (wake-fighters-on-carrier carrier-coords)
      (let [carrier (:contents (get-in @atoms/game-map carrier-coords))]
        (should= :sentry (:mode carrier))
        (should= 2 (:fighter-count carrier))
        (should= 2 (:awake-fighters carrier)))))

  (it "sleep-fighters-on-carrier puts fighters to sleep and wakes carrier"
    (reset! atoms/game-map (build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----C----"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (let [carrier-coords (:pos (get-test-unit atoms/game-map "C"))]
      (set-test-unit atoms/game-map "C" :mode :sentry :hits 8 :fighter-count 2 :awake-fighters 2)
      (sleep-fighters-on-carrier carrier-coords)
      (let [carrier (:contents (get-in @atoms/game-map carrier-coords))]
        (should= :awake (:mode carrier))
        (should= 2 (:fighter-count carrier))
        (should= 0 (:awake-fighters carrier)))))

  (it "launch-fighter-from-carrier removes fighter and places it at adjacent cell"
    (reset! atoms/game-map (build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----C~---"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (let [carrier-coords (:pos (get-test-unit atoms/game-map "C"))
          adjacent-coords [(first carrier-coords) (inc (second carrier-coords))]
          target-coords [(first carrier-coords) (+ (second carrier-coords) 2)]]
      (set-test-unit atoms/game-map "C" :mode :sentry :hits 8 :fighter-count 2 :awake-fighters 2)
      (reset! atoms/player-map (make-initial-test-map 9 9 nil))
      (launch-fighter-from-carrier carrier-coords target-coords)
      (let [carrier (:contents (get-in @atoms/game-map carrier-coords))
            launched-fighter (:contents (get-in @atoms/game-map adjacent-coords))]
        (should= 1 (:fighter-count carrier))
        (should= 1 (:awake-fighters carrier))
        (should= :fighter (:type launched-fighter))
        (should= :moving (:mode launched-fighter))
        (should= target-coords (:target launched-fighter)))))

  (it "launch-fighter-from-carrier keeps carrier in sentry mode after last fighter launches"
    (reset! atoms/game-map (build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----C~---"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (let [carrier-coords (:pos (get-test-unit atoms/game-map "C"))
          target-coords [(first carrier-coords) (+ (second carrier-coords) 2)]]
      (set-test-unit atoms/game-map "C" :mode :sentry :hits 8 :fighter-count 1 :awake-fighters 1)
      (reset! atoms/player-map (make-initial-test-map 9 9 nil))
      (launch-fighter-from-carrier carrier-coords target-coords)
      (let [carrier (:contents (get-in @atoms/game-map carrier-coords))]
        (should= :sentry (:mode carrier))
        (should= 0 (:fighter-count carrier)))))

  (it "launch-fighter-from-carrier sets steps-remaining to speed minus one"
    (reset! atoms/game-map (build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----C~---"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (let [carrier-coords (:pos (get-test-unit atoms/game-map "C"))
          adjacent-coords [(first carrier-coords) (inc (second carrier-coords))]
          target-coords [(first carrier-coords) (+ (second carrier-coords) 2)]]
      (set-test-unit atoms/game-map "C" :mode :sentry :hits 8 :fighter-count 1 :awake-fighters 1)
      (reset! atoms/player-map (make-initial-test-map 9 9 nil))
      (launch-fighter-from-carrier carrier-coords target-coords)
      (let [fighter (:contents (get-in @atoms/game-map adjacent-coords))]
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
    (reset! atoms/game-map (build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----C~~--"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (let [carrier-coords (:pos (get-test-unit atoms/game-map "C"))
          adjacent-coords [(first carrier-coords) (inc (second carrier-coords))]
          target-coords [(first carrier-coords) (+ (second carrier-coords) 2)]]
      (set-test-unit atoms/game-map "C" :mode :sentry :hits 8 :fighter-count 1 :awake-fighters 1)
      (reset! atoms/player-map (make-initial-test-map 9 9 nil))
      ;; Launch fighter from carrier toward target
      (launch-fighter-from-carrier carrier-coords target-coords)
      ;; Verify carrier now has 0 fighters
      (let [carrier (:contents (get-in @atoms/game-map carrier-coords))]
        (should= 0 (:fighter-count carrier))
        (should= 0 (:awake-fighters carrier)))
      ;; Fighter is at adjacent-coords moving toward target
      ;; Now simulate fighter returning to carrier - set its target to carrier
      (let [fighter-cell (get-in @atoms/game-map adjacent-coords)
            fighter (:contents fighter-cell)
            returning-fighter (assoc fighter :target carrier-coords :steps-remaining 1)]
        (swap! atoms/game-map assoc-in (conj adjacent-coords :contents) returning-fighter)
        ;; Move fighter back to carrier
        (game-loop/move-current-unit adjacent-coords)
        ;; Verify fighter landed and is sleeping
        (let [carrier (:contents (get-in @atoms/game-map carrier-coords))]
          (should= :carrier (:type carrier))
          (should= 1 (:fighter-count carrier))
          (should= 0 (:awake-fighters carrier 0))))))

  (it "fighter out of fuel crashing near carrier does not destroy carrier"
    ;; Fighter with fuel 0 adjacent to carrier - when it tries to land, it crashes
    ;; but the carrier should remain intact
    (reset! atoms/game-map (build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----JC---"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (let [fighter-coords (:pos (get-test-unit atoms/game-map "J"))
          carrier-coords (:pos (get-test-unit atoms/game-map "C"))]
      (set-test-unit atoms/game-map "J" :mode :moving :target carrier-coords :fuel 0 :steps-remaining 1)
      (set-test-unit atoms/game-map "C" :mode :sentry :hits 8 :fighter-count 1)
      (reset! atoms/player-map (make-initial-test-map 9 9 nil))
      (game-loop/move-current-unit fighter-coords)
      ;; Fighter should be gone (crashed)
      (should-be-nil (:contents (get-in @atoms/game-map fighter-coords)))
      ;; Carrier should still exist with its original fighter count
      (let [carrier-cell (get-in @atoms/game-map carrier-coords)
            carrier (:contents carrier-cell)]
        (should= :carrier (:type carrier))
        (should= 1 (:fighter-count carrier))))))

(describe "fighter shot down by city"
  (before (reset-all-atoms!))
  (it "fighter is destroyed when flying into hostile city"
    (reset! atoms/game-map (build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----FX---"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (let [fighter-coords (:pos (get-test-unit atoms/game-map "F"))
          city-coords (:pos (get-test-city atoms/game-map "X"))]
      (set-test-unit atoms/game-map "F" :mode :moving :target city-coords :fuel 10 :steps-remaining 1 :hits 1)
      (reset! atoms/player-map (make-initial-test-map 9 9 nil))
      (reset! atoms/line3-message "")
      ;; wake-after-move takes unit, from-pos, final-pos, and current-map (atom)
      (let [cell (get-in @atoms/game-map fighter-coords)
            unit (:contents cell)
            result (wake-after-move unit fighter-coords city-coords atoms/game-map)]
        (should= 0 (:hits result))))))

(describe "fighter landing at city"
  (before (reset-all-atoms!))
  (it "fighter lands at city and increments fighter-count"
    (reset! atoms/game-map (build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----FO---"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (let [fighter-coords (:pos (get-test-unit atoms/game-map "F"))
          city-coords (:pos (get-test-city atoms/game-map "O"))]
      (set-test-unit atoms/game-map "F" :mode :moving :target city-coords :fuel 10 :steps-remaining 1 :hits 1)
      (swap! atoms/game-map assoc-in (conj city-coords :fighter-count) 0)
      (reset! atoms/player-map (make-initial-test-map 9 9 nil))
      (game-loop/move-current-unit fighter-coords)
      (let [city (get-in @atoms/game-map city-coords)]
        (should= 1 (:fighter-count city))
        (should-be-nil (:contents city))))))

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
    (reset! atoms/game-map (build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----O#---"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))
          target-coords [(first city-coords) (+ (second city-coords) 2)]]
      (swap! atoms/game-map assoc-in (conj city-coords :fighter-count) 2)
      (swap! atoms/game-map assoc-in (conj city-coords :awake-fighters) 2)
      (reset! atoms/player-map (make-initial-test-map 9 9 nil))
      (launch-fighter-from-airport city-coords target-coords)
      (let [city (get-in @atoms/game-map city-coords)
            fighter (:contents city)]
        (should= 1 (:fighter-count city))
        (should= 1 (:awake-fighters city))
        (should= :fighter (:type fighter))
        (should= :moving (:mode fighter))
        (should= target-coords (:target fighter))))))
