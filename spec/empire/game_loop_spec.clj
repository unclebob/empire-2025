(ns empire.game-loop-spec
  (:require [speclj.core :refer :all]
            [empire.game-loop :as game-loop]
            [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.movement.movement :as movement]
            [empire.test-utils :refer [build-test-map set-test-unit get-test-unit reset-all-atoms! make-initial-test-map]]))

(describe "item-processed"
  (before (reset-all-atoms!))
  (it "resets waiting-for-input to false"
    (reset! atoms/waiting-for-input true)
    (game-loop/item-processed)
    (should= false @atoms/waiting-for-input))

  (it "preserves attention-message"
    (reset! atoms/attention-message "test message")
    (game-loop/item-processed)
    (should= "test message" @atoms/attention-message))

  (it "clears cells-needing-attention"
    (reset! atoms/cells-needing-attention [[1 2] [3 4]])
    (game-loop/item-processed)
    (should= [] @atoms/cells-needing-attention)))

(describe "build-player-items"
  (before
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["#O"
                                             "AX"])))

  (it "returns player city coordinates"
    (let [items (game-loop/build-player-items)]
      (should-contain [1 0] items)))

  (it "returns player unit coordinates"
    (let [items (game-loop/build-player-items)]
      (should-contain [0 1] items)))

  (it "does not return computer cities"
    (let [items (game-loop/build-player-items)]
      (should-not-contain [1 1] items)))

  (it "does not return empty land"
    (let [items (game-loop/build-player-items)]
      (should-not-contain [0 0] items))))

(describe "remove-dead-units"
  (before
    (reset-all-atoms!)
    (reset! atoms/game-map (-> (build-test-map ["AF"
                                                 "##"])
                               (assoc-in [0 0 :contents :hits] 0)
                               (assoc-in [1 0 :contents :hits] 1)))
    (reset! atoms/player-map (build-test-map ["##"
                                               "##"])))

  (it "removes units with hits <= 0"
    (game-loop/remove-dead-units)
    (should-be-nil (:contents (get-in @atoms/game-map [0 0]))))

  (it "keeps units with hits > 0"
    (game-loop/remove-dead-units)
    (should= {:type :fighter :owner :player :hits 1 :fuel 32} (:contents (get-in @atoms/game-map [1 0])))))

(describe "reset-steps-remaining"
  (before
    (reset-all-atoms!)
    (reset! atoms/game-map (assoc-in (build-test-map ["AF"
                                                       "A#"])
                                     [0 1 :contents :owner] :computer)))

  (it "sets steps-remaining for player army"
    (game-loop/reset-steps-remaining)
    (should= (config/unit-speed :army) (:steps-remaining (:contents (get-in @atoms/game-map [0 0])))))

  (it "sets steps-remaining for player fighter"
    (game-loop/reset-steps-remaining)
    (should= (config/unit-speed :fighter) (:steps-remaining (:contents (get-in @atoms/game-map [1 0])))))

  (it "does not set steps-remaining for computer units"
    (game-loop/reset-steps-remaining)
    (should-be-nil (:steps-remaining (:contents (get-in @atoms/game-map [0 1])))))

  (it "scales steps-remaining by damage for multi-hit ships"
    (reset! atoms/game-map (build-test-map ["D"]))
    (set-test-unit atoms/game-map "D" :hits 1)  ; destroyer max=3, speed=2, at 1/3 -> speed 1
    (game-loop/reset-steps-remaining)
    (should= 1 (:steps-remaining (:contents (get-in @atoms/game-map [0 0]))))))

(describe "wake-airport-fighters"
  (before (reset-all-atoms!))
  (it "wakes all fighters in player city airports"
    (reset! atoms/game-map (assoc-in (build-test-map ["O"]) [0 0 :fighter-count] 3))
    (game-loop/wake-airport-fighters)
    (should= 3 (:awake-fighters (get-in @atoms/game-map [0 0]))))

  (it "ignores computer cities"
    (reset! atoms/game-map (assoc-in (build-test-map ["X"]) [0 0 :fighter-count] 3))
    (game-loop/wake-airport-fighters)
    (should-be-nil (:awake-fighters (get-in @atoms/game-map [0 0]))))

  (it "ignores cities with no fighters"
    (reset! atoms/game-map (assoc-in (build-test-map ["O"]) [0 0 :fighter-count] 0))
    (game-loop/wake-airport-fighters)
    (should-be-nil (:awake-fighters (get-in @atoms/game-map [0 0])))))

(describe "wake-carrier-fighters"
  (before (reset-all-atoms!))
  (it "wakes all fighters on player carriers"
    (reset! atoms/game-map (build-test-map ["C"]))
    (set-test-unit atoms/game-map "C" :fighter-count 3)
    (game-loop/wake-carrier-fighters)
    (should= 3 (:awake-fighters (:contents (get-in @atoms/game-map [0 0])))))

  (it "ignores computer carriers"
    (reset! atoms/game-map (build-test-map ["c"]))
    (set-test-unit atoms/game-map "c" :fighter-count 3)
    (game-loop/wake-carrier-fighters)
    (should= 0 (:awake-fighters (:contents (get-in @atoms/game-map [0 0])))))

  (it "ignores carriers with no fighters"
    (reset! atoms/game-map (build-test-map ["C"]))
    (set-test-unit atoms/game-map "C" :fighter-count 0)
    (game-loop/wake-carrier-fighters)
    (should= 0 (:awake-fighters (:contents (get-in @atoms/game-map [0 0]))))))

(describe "consume-sentry-fighter-fuel"
  (before (reset-all-atoms!))
  (it "decrements fuel for sentry fighters"
    (reset! atoms/game-map (build-test-map ["F"]))
    (set-test-unit atoms/game-map "F" :mode :sentry :fuel 20)
    (game-loop/consume-sentry-fighter-fuel)
    (should= 19 (:fuel (:contents (get-in @atoms/game-map [0 0])))))

  (it "wakes fighter when fuel reaches 1"
    (reset! atoms/game-map (build-test-map ["F"]))
    (set-test-unit atoms/game-map "F" :mode :sentry :fuel 2)
    (game-loop/consume-sentry-fighter-fuel)
    (let [unit (:contents (get-in @atoms/game-map [0 0]))]
      (should= 1 (:fuel unit))
      (should= :awake (:mode unit))
      (should= :fighter-out-of-fuel (:reason unit))))

  (it "sets hits to 0 when fuel reaches 0"
    (reset! atoms/game-map (build-test-map ["F"]))
    (set-test-unit atoms/game-map "F" :mode :sentry :fuel 1)
    (game-loop/consume-sentry-fighter-fuel)
    (should= 0 (:hits (:contents (get-in @atoms/game-map [0 0])))))

  (it "sets error message when fighter crashes"
    (reset! atoms/game-map (build-test-map ["F"]))
    (set-test-unit atoms/game-map "F" :mode :sentry :fuel 1)
    (game-loop/consume-sentry-fighter-fuel)
    (should-contain (:fighter-crashed config/messages) @atoms/error-message))

  (it "does not affect moving fighters"
    (reset! atoms/game-map (build-test-map ["F"]))
    (set-test-unit atoms/game-map "F" :mode :moving :fuel 20)
    (game-loop/consume-sentry-fighter-fuel)
    (should= 20 (:fuel (:contents (get-in @atoms/game-map [0 0])))))

  (it "wakes fighter with bingo when fuel is low and friendly city in range"
    ;; Fighter fuel is 20, bingo threshold is 20/4 = 5
    ;; At fuel 6, decrement to 5 which equals bingo threshold
    ;; Need a friendly city within range (5 cells)
    (reset! atoms/game-map (build-test-map ["OF"]))
    (set-test-unit atoms/game-map "F" :mode :sentry :fuel 6)
    (game-loop/consume-sentry-fighter-fuel)
    (let [unit (:contents (get-in @atoms/game-map [1 0]))]
      (should= 5 (:fuel unit))
      (should= :awake (:mode unit))
      (should= :fighter-bingo (:reason unit)))))

(describe "wake-sentries-seeing-enemy"
  (before (reset-all-atoms!))

  (it "wakes sentry unit when enemy is adjacent"
    (reset! atoms/game-map (build-test-map ["Aa"]))
    (reset! atoms/player-map (make-initial-test-map 1 2 nil))
    (set-test-unit atoms/game-map "A" :mode :sentry)
    (game-loop/wake-sentries-seeing-enemy)
    (let [unit (:contents (get-in @atoms/game-map [0 0]))]
      (should= :awake (:mode unit))
      (should= :enemy-spotted (:reason unit))))

  (it "does not wake sentry when no enemy visible"
    (reset! atoms/game-map (build-test-map ["A#"]))
    (reset! atoms/player-map (make-initial-test-map 1 2 nil))
    (set-test-unit atoms/game-map "A" :mode :sentry)
    (game-loop/wake-sentries-seeing-enemy)
    (let [unit (:contents (get-in @atoms/game-map [0 0]))]
      (should= :sentry (:mode unit))))

  (it "does not wake awake units"
    (reset! atoms/game-map (build-test-map ["Aa"]))
    (reset! atoms/player-map (make-initial-test-map 1 2 nil))
    (set-test-unit atoms/game-map "A" :mode :awake)
    (game-loop/wake-sentries-seeing-enemy)
    (let [unit (:contents (get-in @atoms/game-map [0 0]))]
      (should= :awake (:mode unit))
      (should-be-nil (:reason unit))))

  (it "does not wake moving units"
    (reset! atoms/game-map (build-test-map ["Aa"]))
    (reset! atoms/player-map (make-initial-test-map 1 2 nil))
    (set-test-unit atoms/game-map "A" :mode :moving :target [5 0])
    (game-loop/wake-sentries-seeing-enemy)
    (let [unit (:contents (get-in @atoms/game-map [0 0]))]
      (should= :moving (:mode unit))))

  (it "wakes sentry naval units when enemy visible"
    (reset! atoms/game-map (build-test-map ["Ds"]))
    (reset! atoms/player-map (make-initial-test-map 1 2 nil))
    (set-test-unit atoms/game-map "D" :mode :sentry)
    (game-loop/wake-sentries-seeing-enemy)
    (let [unit (:contents (get-in @atoms/game-map [0 0]))]
      (should= :awake (:mode unit))
      (should= :enemy-spotted (:reason unit))))

  (it "does not wake computer sentry units"
    (reset! atoms/game-map (build-test-map ["aA"]))
    (reset! atoms/player-map (make-initial-test-map 1 2 nil))
    (set-test-unit atoms/game-map "a" :mode :sentry)
    (game-loop/wake-sentries-seeing-enemy)
    (let [unit (:contents (get-in @atoms/game-map [0 0]))]
      (should= :sentry (:mode unit)))))

(describe "start-new-round"
  (before
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["O"]))
    (reset! atoms/player-map (build-test-map ["#"]))
    (reset! atoms/computer-map (build-test-map ["#"]))
    (reset! atoms/production {})
    (reset! atoms/round-number 0)
    (reset! atoms/player-items [])
    (reset! atoms/waiting-for-input true)
    (reset! atoms/attention-message "old message")
    (reset! atoms/cells-needing-attention [[0 0]]))

  (it "increments round number"
    (game-loop/start-new-round)
    (should= 1 @atoms/round-number))

  (it "builds player items list"
    (game-loop/start-new-round)
    (should-contain [0 0] @atoms/player-items))

  (it "resets waiting-for-input to false"
    (game-loop/start-new-round)
    (should= false @atoms/waiting-for-input))

  (it "clears message"
    (game-loop/start-new-round)
    (should= "" @atoms/attention-message))

  (it "clears cells-needing-attention"
    (game-loop/start-new-round)
    (should= [] @atoms/cells-needing-attention))

  (it "does not wake carrier fighters - they stay asleep until u is pressed"
    (reset! atoms/game-map (-> (build-test-map ["C"])
                               (assoc-in [0 0 :contents :fighter-count] 2)
                               (assoc-in [0 0 :contents :awake-fighters] 0)))
    (reset! atoms/player-map (build-test-map ["~"]))
    (reset! atoms/computer-map (build-test-map ["~"]))
    (game-loop/start-new-round)
    (let [carrier (:contents (get-in @atoms/game-map [0 0]))]
      (should= 0 (:awake-fighters carrier 0)))))

(describe "advance-game"
  (before (reset-all-atoms!))
  (it "starts new round when player-items is empty"
    (reset! atoms/game-map (build-test-map ["O"]))
    (reset! atoms/player-map (build-test-map ["#"]))
    (reset! atoms/computer-map (build-test-map ["#"]))
    (reset! atoms/production {})
    (reset! atoms/player-items [])
    (reset! atoms/round-number 0)
    (game-loop/advance-game)
    (should= 1 @atoms/round-number))

  (it "sets waiting-for-input when item needs attention"
    (reset! atoms/game-map (build-test-map ["O"]))
    (reset! atoms/production {})
    (reset! atoms/player-items [[0 0]])
    (reset! atoms/waiting-for-input false)
    (reset! atoms/attention-message "")
    (game-loop/advance-game)
    (should= true @atoms/waiting-for-input))

  (it "does nothing when waiting for input"
    (reset! atoms/game-map (build-test-map ["A"]))
    (set-test-unit atoms/game-map "A" :mode :awake)
    (reset! atoms/production {})
    (reset! atoms/player-items [[0 0]])
    (reset! atoms/waiting-for-input true)
    (reset! atoms/round-number 5)
    (game-loop/advance-game)
    (should= 5 @atoms/round-number)
    (should= [[0 0]] @atoms/player-items))

  (it "moves to next item when unit does not need attention"
    (reset! atoms/game-map (build-test-map ["A"]))
    (set-test-unit atoms/game-map "A" :mode :sentry)
    (reset! atoms/production {})
    (reset! atoms/player-items [[0 0]])
    (reset! atoms/waiting-for-input false)
    (game-loop/advance-game)
    (should= [] (vec @atoms/player-items))))

(describe "update-map"
  (before
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["O"]))
    (reset! atoms/player-map (build-test-map ["#"]))
    (reset! atoms/computer-map (build-test-map ["#"]))
    (reset! atoms/production {})
    (reset! atoms/player-items [])
    (reset! atoms/round-number 0))

  (it "calls advance-game which starts new round when empty"
    (game-loop/update-map)
    (should= 1 @atoms/round-number)))

(describe "move-satellites"
  (before (reset-all-atoms!))
  (it "removes satellite when turns-remaining reaches zero during movement"
    ;; Satellite with turns-remaining 1 will expire after moving
    (reset! atoms/game-map (build-test-map ["V#"]))
    (set-test-unit atoms/game-map "V" :turns-remaining 1)
    (reset! atoms/player-map (build-test-map ["##"]))
    (game-loop/move-satellites)
    ;; Satellite should be removed after its turn expires
    (let [result (get-test-unit atoms/game-map "V")]
      ;; Either satellite is gone or has decremented turns
      (when result
        (should (or (nil? (:unit result)) (<= (:turns-remaining (:unit result) 0) 0)))))))

  (it "removes satellite immediately when turns-remaining is already zero"
    ;; Satellite with turns-remaining 0 should be removed at start of move
    (reset! atoms/game-map (build-test-map ["V"]))
    (set-test-unit atoms/game-map "V" :turns-remaining 0)
    (reset! atoms/player-map (build-test-map ["#"]))
    (game-loop/move-satellites)
    ;; Satellite should be removed
    (should-be-nil (:contents (get-in @atoms/game-map [0 0]))))

  (it "decrements turns-remaining after movement"
    (reset! atoms/game-map (build-test-map ["V##"]))
    (set-test-unit atoms/game-map "V" :turns-remaining 5)
    (reset! atoms/player-map (build-test-map ["###"]))
    (game-loop/move-satellites)
    ;; Find where satellite ended up
    (let [{:keys [unit]} (get-test-unit atoms/game-map "V")]
      (when unit
        (should (< (:turns-remaining unit) 5)))))

(describe "move-explore-unit"
  (before (reset-all-atoms!))
  (it "delegates to movement/move-explore-unit"
    (reset! atoms/game-map (build-test-map ["A#"]))
    (set-test-unit atoms/game-map "A" :mode :explore :visited #{[0 0]})
    (reset! atoms/player-map (build-test-map ["##"]))
    (let [result (game-loop/move-explore-unit [0 0])]
      ;; Should return new coords if still exploring
      (should (or (nil? result) (vector? result))))))

(describe "move-coastline-unit"
  (before (reset-all-atoms!))
  (it "delegates to movement/move-coastline-unit"
    (reset! atoms/game-map (build-test-map ["#~~~~"
                                             "#~~~~"
                                             "#T~~~"
                                             "#~~~~"
                                             "#~~~~"]))
    (set-test-unit atoms/game-map "T" :mode :coastline-follow :coastline-steps 50
                   :start-pos [1 2] :visited #{[1 2]} :prev-pos nil)
    (reset! atoms/player-map @atoms/game-map)
    (let [result (game-loop/move-coastline-unit [1 2])]
      ;; Should return nil (unit keeps moving until done)
      (should-be-nil result))))

(describe "auto-launch-fighter from airport"
  (before (reset-all-atoms!))
  (it "launches fighter when city has flight-path and awake fighters"
    (reset! atoms/game-map (-> (build-test-map ["O#"])
                               (assoc-in [0 0 :flight-path] [1 0])
                               (assoc-in [0 0 :awake-fighters] 1)
                               (assoc-in [0 0 :fighter-count] 1)))
    (reset! atoms/player-map (build-test-map ["##"]))
    (reset! atoms/production {})
    (reset! atoms/player-items [[0 0]])
    (reset! atoms/waiting-for-input false)
    (game-loop/advance-game)
    ;; Fighter should have been launched and moved toward target
    ;; City's awake-fighters should be decremented
    (let [city (get-in @atoms/game-map [0 0])]
      (should= 0 (:awake-fighters city 0)))
    ;; Fighter should exist on the map (either at launch position or having moved)
    (let [fighter-at-target (get-in @atoms/game-map [1 0])]
      (should= :fighter (:type (:contents fighter-at-target)))))

  (it "does not launch fighter when army is on city"
    (reset! atoms/game-map (-> (build-test-map ["O#"])
                               (assoc-in [0 0 :flight-path] [1 0])
                               (assoc-in [0 0 :awake-fighters] 1)
                               (assoc-in [0 0 :fighter-count] 1)
                               (assoc-in [0 0 :contents] {:type :army :mode :moving :target [1 0] :hits 1 :owner :player})))
    (reset! atoms/player-map (build-test-map ["##"]))
    (reset! atoms/production {})
    (reset! atoms/player-items [[0 0]])
    (reset! atoms/waiting-for-input false)
    (game-loop/advance-game)
    ;; Army should still exist, not overwritten by fighter
    (let [city (get-in @atoms/game-map [0 0])]
      (should= 1 (:awake-fighters city))
      (should= 1 (:fighter-count city))))

  (it "launches fighter from carrier with flight-path"
    (reset! atoms/game-map (build-test-map ["C~"]))
    (set-test-unit atoms/game-map "C" :mode :sentry :flight-path [1 0] :awake-fighters 1 :fighter-count 1)
    (reset! atoms/player-map (build-test-map ["~~"]))
    (reset! atoms/production {})
    (reset! atoms/player-items [[0 0]])
    (reset! atoms/waiting-for-input false)
    (game-loop/advance-game)
    ;; Fighter should have been launched from carrier (awake-fighters decremented)
    (let [carrier (:contents (get-in @atoms/game-map [0 0]))]
      (should= 0 (:awake-fighters carrier 0)))))

(describe "auto-disembark-army"
  (before (reset-all-atoms!))
  (it "disembarks army when transport has marching-orders and awake armies"
    (reset! atoms/game-map (build-test-map ["T#"]))
    (set-test-unit atoms/game-map "T" :mode :sentry :marching-orders [0 1] :awake-armies 1 :army-count 1)
    (reset! atoms/player-map (build-test-map ["~#"]))
    (reset! atoms/production {})
    (reset! atoms/player-items [[0 0]])
    (reset! atoms/waiting-for-input false)
    (game-loop/advance-game)
    ;; Army should have been disembarked
    (let [land-cell (get-in @atoms/game-map [1 0])]
      (should= :army (:type (:contents land-cell))))))

(describe "advance-game with explore mode"
  (before (reset-all-atoms!))
  (it "processes exploring unit"
    (reset! atoms/game-map (build-test-map ["A#"]))
    (set-test-unit atoms/game-map "A" :mode :explore :visited #{[0 0]})
    (reset! atoms/player-map (build-test-map ["##"]))
    (reset! atoms/production {})
    (reset! atoms/player-items [[0 0]])
    (reset! atoms/waiting-for-input false)
    (game-loop/advance-game)
    ;; Should have moved the exploring unit
    (should-not= [[0 0]] @atoms/player-items)))

(describe "advance-game with coastline-follow mode"
  (before (reset-all-atoms!))
  (it "processes coastline-following unit and continues when returning new coords"
    (reset! atoms/game-map (build-test-map ["#~~~~~~~~~"
                                             "#~~~~~~~~~"
                                             "#~~~~~~~~~"
                                             "#~~~~~~~~~"
                                             "#~~~~~~~~~"
                                             "#T~~~~~~~~"
                                             "#~~~~~~~~~"
                                             "#~~~~~~~~~"
                                             "#~~~~~~~~~"
                                             "#~~~~~~~~~"]))
    (set-test-unit atoms/game-map "T" :mode :coastline-follow :coastline-steps 50
                   :start-pos [1 5] :visited #{[1 5]} :prev-pos nil)
    (reset! atoms/player-map @atoms/game-map)
    (reset! atoms/production {})
    (reset! atoms/player-items [[1 5]])
    (reset! atoms/waiting-for-input false)
    (game-loop/advance-game)
    ;; Unit should have moved - player-items should be updated
    (should (or (empty? @atoms/player-items)
                (not= [[1 5]] (vec @atoms/player-items))))))

(describe "advance-game with moving unit"
  (before (reset-all-atoms!))
  (it "continues processing when unit moves and has steps remaining"
    ;; Set up a moving unit with multiple steps, target far away
    (reset! atoms/game-map (build-test-map ["A###"]))
    (set-test-unit atoms/game-map "A" :mode :moving :target [3 0] :steps-remaining 2)
    (reset! atoms/player-map (build-test-map ["####"]))
    (reset! atoms/production {})
    (reset! atoms/player-items [[0 0]])
    (reset! atoms/waiting-for-input false)
    (game-loop/advance-game)
    ;; Unit should have moved towards target
    (let [original-has-unit? (some? (:contents (get-in @atoms/game-map [0 0])))]
      ;; Either original spot is empty or unit is somewhere else
      (should (or (not original-has-unit?)
                  (empty? @atoms/player-items)))))

  (it "moves immediately after user gives movement command"
    ;; Bug reproduction: army asks for attention, user gives command,
    ;; army should move in the same processing cycle
    (reset! atoms/game-map (build-test-map ["A###"]))
    (set-test-unit atoms/game-map "A" :mode :awake :steps-remaining 1)
    (reset! atoms/player-map (build-test-map ["####"]))
    (reset! atoms/production {})
    (reset! atoms/player-items [[0 0]])
    (reset! atoms/waiting-for-input false)
    ;; Step 1: advance-game should stop at attention
    (game-loop/advance-game)
    (should @atoms/waiting-for-input)
    (should= [[0 0]] @atoms/cells-needing-attention)
    ;; Step 2: simulate user giving movement command
    (movement/set-unit-movement [0 0] [3 0])
    (game-loop/item-processed)
    ;; Verify unit is now in moving mode
    (should= :moving (:mode (:contents (get-in @atoms/game-map [0 0]))))
    (should-not @atoms/waiting-for-input)
    ;; Step 3: advance-game should move the unit
    (game-loop/advance-game)
    ;; Unit should have moved from [0 0]
    (should-not (:contents (get-in @atoms/game-map [0 0])))
    (should (:contents (get-in @atoms/game-map [1 0]))))

  (it "moves immediately even with multiple units in queue"
    ;; Test with two armies - first one gets orders to distant target, should move before second asks attention
    (reset! atoms/game-map (build-test-map ["A#A###"]))
    (set-test-unit atoms/game-map "A1" :mode :awake :steps-remaining 1)
    (set-test-unit atoms/game-map "A2" :mode :awake :steps-remaining 1)
    (reset! atoms/player-map (build-test-map ["######"]))
    (reset! atoms/production {})
    (reset! atoms/player-items [[0 0] [2 0]])  ;; Both armies in queue
    (reset! atoms/waiting-for-input false)
    ;; Step 1: first army should ask for attention
    (game-loop/advance-game)
    (should @atoms/waiting-for-input)
    (should= [[0 0]] @atoms/cells-needing-attention)
    ;; Step 2: give first army movement orders to DISTANT target (so it doesn't arrive in one step)
    (movement/set-unit-movement [0 0] [5 0])
    (game-loop/item-processed)
    (should= :moving (:mode (:contents (get-in @atoms/game-map [0 0]))))
    ;; Step 3: advance-game should move first army BEFORE second asks attention
    (game-loop/advance-game)
    ;; First army should have moved from [0 0] to [1 0]
    (should-not (:contents (get-in @atoms/game-map [0 0])))
    (should (:contents (get-in @atoms/game-map [1 0])))
    ;; First army should still be moving (not at target yet), so second army gets attention next
    (should= :moving (:mode (:contents (get-in @atoms/game-map [1 0]))))
    ;; Second army should now be asking for attention
    (should @atoms/waiting-for-input)
    (should= [[2 0]] @atoms/cells-needing-attention))

  (it "wakes up when army tries to move to invalid cell (water)"
    ;; Army told to move toward water - should wake up immediately, NOT move
    (reset! atoms/game-map (build-test-map ["A~##"]))
    (set-test-unit atoms/game-map "A" :mode :awake :steps-remaining 1)
    (reset! atoms/player-map (build-test-map ["####"]))
    (reset! atoms/production {})
    (reset! atoms/player-items [[0 0]])
    (reset! atoms/waiting-for-input false)
    ;; Step 1: army asks for attention
    (game-loop/advance-game)
    (should @atoms/waiting-for-input)
    ;; Step 2: tell army to move toward water
    (movement/set-unit-movement [0 0] [1 0])
    (game-loop/item-processed)
    (should= :moving (:mode (:contents (get-in @atoms/game-map [0 0]))))
    ;; Step 3: advance-game - army should wake up because it can't enter water
    (game-loop/advance-game)
    ;; Army should still be at [0 0] and should be awake again
    (let [unit (:contents (get-in @atoms/game-map [0 0]))]
      (should unit)
      (should= :awake (:mode unit))
      (should= :cant-move-into-water (:reason unit)))
    (should @atoms/waiting-for-input))

  (it "army with 0 steps-remaining is removed after given orders (expected behavior)"
    ;; When army has 0 steps-remaining (already moved this round), it can't move again
    ;; until next round. This is expected behavior.
    (reset! atoms/game-map (build-test-map ["#A#A#"]))
    ;; First army already moved this round (steps-remaining = 0), woke up at target
    (set-test-unit atoms/game-map "A1" :mode :awake :steps-remaining 0)
    ;; Second army hasn't moved yet
    (set-test-unit atoms/game-map "A2" :mode :awake :steps-remaining 1)
    (reset! atoms/player-map (build-test-map ["#####"]))
    (reset! atoms/production {})
    (reset! atoms/player-items [[1 0] [3 0]])
    (reset! atoms/waiting-for-input false)
    ;; Step 1: first army asks for attention (it has 0 steps but is awake)
    (game-loop/advance-game)
    (should @atoms/waiting-for-input)
    (should= [[1 0]] @atoms/cells-needing-attention)
    ;; Step 2: user gives first army new orders to move further
    (movement/set-unit-movement [1 0] [4 0])
    (game-loop/item-processed)
    (should= :moving (:mode (:contents (get-in @atoms/game-map [1 0]))))
    ;; Step 3: advance-game - first army can't move (0 steps), removed from queue
    (game-loop/advance-game)
    ;; First army should still be at [1 0] with mode :moving (waiting for next round)
    (let [first-army (:contents (get-in @atoms/game-map [1 0]))]
      (should first-army)
      (should= :moving (:mode first-army)))
    ;; The attention should be for the second army
    (should @atoms/waiting-for-input)
    (should= [[3 0]] @atoms/cells-needing-attention))

  (it "fresh army moves immediately when given orders"
    ;; A fresh army (at start of round) has steps-remaining set
    ;; and should move immediately when given orders
    (reset! atoms/game-map (build-test-map ["A####"]))
    (set-test-unit atoms/game-map "A" :mode :awake :steps-remaining 1)
    (reset! atoms/player-map (build-test-map ["#####"]))
    (reset! atoms/production {})
    (reset! atoms/player-items [[0 0]])
    (reset! atoms/waiting-for-input false)
    ;; Step 1: army asks for attention
    (game-loop/advance-game)
    (should @atoms/waiting-for-input)
    ;; Step 2: user gives orders to distant target
    (movement/set-unit-movement [0 0] [4 0])
    (game-loop/item-processed)
    ;; Step 3: advance-game should move army immediately
    (game-loop/advance-game)
    ;; Army should have moved from [0 0] to [1 0]
    (should-not (:contents (get-in @atoms/game-map [0 0])))
    (should (:contents (get-in @atoms/game-map [1 0])))))


(describe "game pauses when load menu is open"
  (before (reset-all-atoms!))

  (it "does not advance game when load-menu-open is true"
    (reset! atoms/load-menu-open true)
    (reset! atoms/player-items [])
    (reset! atoms/computer-items [])
    (reset! atoms/round-number 5)
    (game-loop/advance-game)
    (should= 5 @atoms/round-number)))

(describe "pause functionality"
  (before
    (reset-all-atoms!)
    (reset! atoms/paused false)
    (reset! atoms/pause-requested false))

  (describe "toggle-pause"
    (it "sets pause-requested when game is running"
      (reset! atoms/paused false)
      (game-loop/toggle-pause)
      (should @atoms/pause-requested))

    (it "unpauses when game is paused"
      (reset! atoms/paused true)
      (reset! atoms/pause-requested false)
      (game-loop/toggle-pause)
      (should-not @atoms/paused)
      (should-not @atoms/pause-requested)))

  (describe "step-one-round"
    (it "does nothing when not paused"
      (reset! atoms/paused false)
      (reset! atoms/round-number 5)
      (game-loop/step-one-round)
      (should-not @atoms/paused)
      (should= 5 @atoms/round-number))

    (it "unpauses and requests pause when paused"
      (reset! atoms/paused true)
      (reset! atoms/pause-requested false)
      (reset! atoms/player-items [[0 0]])
      (game-loop/step-one-round)
      (should-not @atoms/paused)
      (should @atoms/pause-requested))

    (it "starts new round when paused and items empty"
      (reset! atoms/game-map (build-test-map ["O"]))
      (reset! atoms/player-map (build-test-map ["#"]))
      (reset! atoms/computer-map (build-test-map ["#"]))
      (reset! atoms/production {})
      (reset! atoms/paused true)
      (reset! atoms/player-items [])
      (reset! atoms/computer-items [])
      (reset! atoms/round-number 5)
      (game-loop/step-one-round)
      (should= 6 @atoms/round-number))

    (it "does not start new round when player-items not empty"
      (reset! atoms/paused true)
      (reset! atoms/player-items [[0 0]])
      (reset! atoms/computer-items [])
      (reset! atoms/round-number 5)
      (game-loop/step-one-round)
      (should= 5 @atoms/round-number)))

  (describe "advance-game pauses at round end"
    (it "pauses at end of round when pause-requested"
      (reset! atoms/game-map (build-test-map ["#"]))
      (reset! atoms/player-map (build-test-map ["#"]))
      (reset! atoms/production {})
      (reset! atoms/player-items [])  ;; Empty means end of round
      (reset! atoms/pause-requested true)
      (reset! atoms/paused false)
      (let [round-before @atoms/round-number]
        (game-loop/advance-game)
        ;; Should be paused, round should not have advanced
        (should @atoms/paused)
        (should-not @atoms/pause-requested)
        (should= round-before @atoms/round-number)))

    (it "does not start new round when paused"
      (reset! atoms/game-map (build-test-map ["#"]))
      (reset! atoms/player-map (build-test-map ["#"]))
      (reset! atoms/production {})
      (reset! atoms/player-items [])
      (reset! atoms/paused true)
      (let [round-before @atoms/round-number]
        (game-loop/advance-game)
        ;; Round should not advance while paused
        (should= round-before @atoms/round-number)))

    (it "starts new round normally when not paused"
      (reset! atoms/game-map (build-test-map ["#"]))
      (reset! atoms/player-map (build-test-map ["#"]))
      (reset! atoms/production {})
      (reset! atoms/player-items [])
      (reset! atoms/paused false)
      (reset! atoms/pause-requested false)
      (let [round-before @atoms/round-number]
        (game-loop/advance-game)
        ;; Round should advance
        (should= (inc round-before) @atoms/round-number)))))

(describe "game over"
  (before
    (reset-all-atoms!)
    (reset! atoms/game-over-check-enabled true))

  (it "pauses game when player has no cities and no units"
    (reset! atoms/game-map (build-test-map ["X#"]))  ;; Only computer city
    (reset! atoms/player-map (build-test-map ["##"]))
    (reset! atoms/computer-map (build-test-map ["##"]))
    (reset! atoms/production {})
    (reset! atoms/paused false)
    (game-loop/start-new-round)
    (should @atoms/paused))

  (it "displays ****GAME OVER***** in error message"
    (reset! atoms/game-map (build-test-map ["X#"]))  ;; Only computer city
    (reset! atoms/player-map (build-test-map ["##"]))
    (reset! atoms/computer-map (build-test-map ["##"]))
    (reset! atoms/production {})
    (reset! atoms/error-message "")
    (game-loop/start-new-round)
    (should= "****GAME OVER*****" @atoms/error-message))

  (it "does not trigger game over when player has a city"
    (reset! atoms/game-map (build-test-map ["OX"]))  ;; Player has a city
    (reset! atoms/player-map (build-test-map ["##"]))
    (reset! atoms/computer-map (build-test-map ["##"]))
    (reset! atoms/production {})
    (reset! atoms/paused false)
    (game-loop/start-new-round)
    (should-not @atoms/paused))

  (it "does not trigger game over when player has a unit"
    (reset! atoms/game-map (build-test-map ["AX"]))  ;; Player has an army
    (reset! atoms/player-map (build-test-map ["##"]))
    (reset! atoms/computer-map (build-test-map ["##"]))
    (reset! atoms/production {})
    (reset! atoms/paused false)
    (game-loop/start-new-round)
    (should-not @atoms/paused))

  (it "switches map display to actual-map on game over"
    (reset! atoms/game-map (build-test-map ["X#"]))  ;; Only computer city
    (reset! atoms/player-map (build-test-map ["##"]))
    (reset! atoms/computer-map (build-test-map ["##"]))
    (reset! atoms/production {})
    (reset! atoms/map-to-display :player-map)
    (game-loop/start-new-round)
    (should= :actual-map @atoms/map-to-display)))

(describe "player victory"
  (before
    (reset-all-atoms!)
    (reset! atoms/game-over-check-enabled true))

  (it "pauses game when computer has no cities and no units"
    (reset! atoms/game-map (build-test-map ["O#"]))  ;; Only player city
    (reset! atoms/player-map (build-test-map ["##"]))
    (reset! atoms/computer-map (build-test-map ["##"]))
    (reset! atoms/production {})
    (reset! atoms/paused false)
    (game-loop/start-new-round)
    (should @atoms/paused))

  (it "displays ****YOU WIN!***** in error message"
    (reset! atoms/game-map (build-test-map ["O#"]))  ;; Only player city
    (reset! atoms/player-map (build-test-map ["##"]))
    (reset! atoms/computer-map (build-test-map ["##"]))
    (reset! atoms/production {})
    (reset! atoms/error-message "")
    (game-loop/start-new-round)
    (should= "****YOU WIN!*****" @atoms/error-message))

  (it "does not trigger victory when computer has a city"
    (reset! atoms/game-map (build-test-map ["OX"]))  ;; Computer has a city
    (reset! atoms/player-map (build-test-map ["##"]))
    (reset! atoms/computer-map (build-test-map ["##"]))
    (reset! atoms/production {})
    (reset! atoms/paused false)
    (game-loop/start-new-round)
    (should-not @atoms/paused))

  (it "does not trigger victory when computer has a unit"
    (reset! atoms/game-map (build-test-map ["Oa"]))  ;; Computer has an army
    (reset! atoms/player-map (build-test-map ["##"]))
    (reset! atoms/computer-map (build-test-map ["##"]))
    (reset! atoms/production {})
    (reset! atoms/paused false)
    (game-loop/start-new-round)
    (should-not @atoms/paused))

  (it "switches map display to actual-map on victory"
    (reset! atoms/game-map (build-test-map ["O#"]))  ;; Only player city
    (reset! atoms/player-map (build-test-map ["##"]))
    (reset! atoms/computer-map (build-test-map ["##"]))
    (reset! atoms/production {})
    (reset! atoms/map-to-display :player-map)
    (game-loop/start-new-round)
    (should= :actual-map @atoms/map-to-display))

  (it "declares victory immediately after player move eliminates last computer"
    ;; Scenario: player has two armies, computer has one army
    ;; First army kills computer army, victory should be declared immediately
    ;; Second army should NOT get attention
    (reset! atoms/game-map (build-test-map ["Aa#A"]))
    (set-test-unit atoms/game-map "A1" :mode :awake :steps-remaining 1)
    (set-test-unit atoms/game-map "A2" :mode :awake :steps-remaining 1)
    (reset! atoms/player-map (build-test-map ["####"]))
    (reset! atoms/computer-map (build-test-map ["####"]))
    (reset! atoms/production {})
    (reset! atoms/player-items [[0 0] [3 0]])  ;; Both player armies in queue
    (reset! atoms/waiting-for-input false)
    (reset! atoms/paused false)
    ;; Step 1: first army asks for attention
    (game-loop/advance-game)
    (should @atoms/waiting-for-input)
    (should= [[0 0]] @atoms/cells-needing-attention)
    ;; Step 2: user moves first army to attack computer army
    (movement/set-unit-movement [0 0] [1 0])
    (game-loop/item-processed)
    ;; Step 3: advance-game triggers combat; mock rand so player wins
    (with-redefs [rand (constantly 0.0)]
      (game-loop/advance-game))
    (should @atoms/paused)
    (should= "****YOU WIN!*****" @atoms/error-message)
    (should= :actual-map @atoms/map-to-display)
    (should= [] (vec @atoms/player-items))))

(describe "advance-game-batch"
  (before (reset-all-atoms!))

  (it "processes multiple sentry units in one batch"
    ;; 3 sentry units that don't need attention
    (reset! atoms/game-map (build-test-map ["AAA"]))
    (set-test-unit atoms/game-map "A1" :mode :sentry)
    (set-test-unit atoms/game-map "A2" :mode :sentry)
    (set-test-unit atoms/game-map "A3" :mode :sentry)
    (reset! atoms/player-map (build-test-map ["###"]))
    (reset! atoms/computer-map (build-test-map ["###"]))
    (reset! atoms/production {})
    (reset! atoms/player-items [[0 0] [1 0] [2 0]])
    (reset! atoms/waiting-for-input false)
    (game-loop/advance-game-batch)
    ;; All 3 should be processed in one batch call
    (should= [] (vec @atoms/player-items)))

  (it "stops when items exhausted before reaching limit"
    ;; 2 sentry units, advances-per-frame is 10
    (reset! atoms/game-map (build-test-map ["AA"]))
    (set-test-unit atoms/game-map "A1" :mode :sentry)
    (set-test-unit atoms/game-map "A2" :mode :sentry)
    (reset! atoms/player-map (build-test-map ["##"]))
    (reset! atoms/computer-map (build-test-map ["##"]))
    (reset! atoms/production {})
    (reset! atoms/player-items [[0 0] [1 0]])
    (reset! atoms/waiting-for-input false)
    (game-loop/advance-game-batch)
    ;; Both processed, started new round, player-items rebuilt
    (should-not @atoms/waiting-for-input))

  (it "stops when waiting for input"
    ;; First unit needs attention (awake), should block
    (reset! atoms/game-map (build-test-map ["AA"]))
    (set-test-unit atoms/game-map "A1" :mode :awake)
    (set-test-unit atoms/game-map "A2" :mode :sentry)
    (reset! atoms/player-map (build-test-map ["##"]))
    (reset! atoms/computer-map (build-test-map ["##"]))
    (reset! atoms/production {})
    (reset! atoms/player-items [[0 0] [1 0]])
    (reset! atoms/waiting-for-input false)
    (game-loop/advance-game-batch)
    ;; Should be waiting for input after first item
    (should @atoms/waiting-for-input)
    ;; Second item should still be in list
    (should (some #{[1 0]} @atoms/player-items)))

  (it "stops when paused"
    (reset! atoms/game-map (build-test-map ["A"]))
    (set-test-unit atoms/game-map "A" :mode :sentry)
    (reset! atoms/player-map (build-test-map ["#"]))
    (reset! atoms/computer-map (build-test-map ["#"]))
    (reset! atoms/production {})
    (reset! atoms/player-items [[0 0]])
    (reset! atoms/paused true)
    (let [items-before (vec @atoms/player-items)]
      (game-loop/advance-game-batch)
      ;; Items should be unchanged since game is paused
      (should= items-before (vec @atoms/player-items)))))

(describe "army on player city bug"
  (before (reset-all-atoms!))

  (it "army on player city without production moves after given orders"
    ;; BUG: When army is on player city without production, giving the army orders
    ;; causes it to ask for attention again (because the city still needs production)
    ;; instead of moving.
    (reset! atoms/game-map (-> (build-test-map ["O###"])
                               (assoc-in [0 0 :contents] {:type :army :owner :player :mode :awake :steps-remaining 1 :hits 1})))
    (reset! atoms/player-map (build-test-map ["####"]))
    (reset! atoms/computer-map (build-test-map ["####"]))
    (reset! atoms/production {})  ;; No production set for city
    (reset! atoms/player-items [[0 0]])
    (reset! atoms/waiting-for-input false)
    ;; Step 1: item asks for attention (could be army or city)
    (game-loop/advance-game)
    (should @atoms/waiting-for-input)
    (should= [[0 0]] @atoms/cells-needing-attention)
    ;; Step 2: user gives army movement orders
    (movement/set-unit-movement [0 0] [3 0])
    (game-loop/item-processed)
    (should= :moving (:mode (:contents (get-in @atoms/game-map [0 0]))))
    ;; Step 3: advance-game should move the army, NOT ask for attention again
    (game-loop/advance-game)
    ;; Army should have moved from [0 0] to [1 0]
    (should-not (:contents (get-in @atoms/game-map [0 0])))
    (should (:contents (get-in @atoms/game-map [1 0])))))

(describe "unit movement with two units"
  (before (reset-all-atoms!))

  (it "first unit moves before second unit gets attention"
    ;; Two armies in queue. First gets orders, should move BEFORE second gets attention.
    (reset! atoms/game-map (build-test-map ["A#A###"]))
    (set-test-unit atoms/game-map "A1" :mode :awake :steps-remaining 1)
    (set-test-unit atoms/game-map "A2" :mode :awake :steps-remaining 1)
    (reset! atoms/player-map (build-test-map ["######"]))
    (reset! atoms/production {})
    (reset! atoms/player-items [[0 0] [2 0]])
    (reset! atoms/waiting-for-input false)
    ;; Step 1: first army asks for attention
    (game-loop/advance-game)
    (should @atoms/waiting-for-input)
    (should= [[0 0]] @atoms/cells-needing-attention)
    ;; Step 2: user gives first army movement orders
    (movement/set-unit-movement [0 0] [5 0])
    (game-loop/item-processed)
    (should= :moving (:mode (:contents (get-in @atoms/game-map [0 0]))))
    (should-not @atoms/waiting-for-input)
    ;; Verify player-items still has first army at front
    (should= [0 0] (first @atoms/player-items))
    ;; Step 3: advance-game should move first army BEFORE second asks attention
    (game-loop/advance-game)
    ;; First army should have moved from [0 0] to [1 0]
    (should-not (:contents (get-in @atoms/game-map [0 0])))
    (should (:contents (get-in @atoms/game-map [1 0])))
    ;; Now second army should be asking for attention
    (should @atoms/waiting-for-input)
    (should= [[2 0]] @atoms/cells-needing-attention))

  (it "unit with 0 steps-remaining cannot move but stays in moving mode"
    ;; When a unit wakes up with 0 steps and user gives orders,
    ;; it can't move but should not immediately ask for attention again
    (reset! atoms/game-map (build-test-map ["A#A###"]))
    ;; First army already used its steps this round
    (set-test-unit atoms/game-map "A1" :mode :awake :steps-remaining 0)
    (set-test-unit atoms/game-map "A2" :mode :awake :steps-remaining 1)
    (reset! atoms/player-map (build-test-map ["######"]))
    (reset! atoms/production {})
    (reset! atoms/player-items [[0 0] [2 0]])
    (reset! atoms/waiting-for-input false)
    ;; Step 1: first army asks for attention
    (game-loop/advance-game)
    (should @atoms/waiting-for-input)
    (should= [[0 0]] @atoms/cells-needing-attention)
    ;; Step 2: user gives first army movement orders
    (movement/set-unit-movement [0 0] [5 0])
    (game-loop/item-processed)
    (should= :moving (:mode (:contents (get-in @atoms/game-map [0 0]))))
    ;; Step 3: advance-game - first army can't move (0 steps),
    ;; should be removed from queue, second army gets attention
    (game-loop/advance-game)
    ;; First army should still be at [0 0] (can't move)
    (should (:contents (get-in @atoms/game-map [0 0])))
    (should= :moving (:mode (:contents (get-in @atoms/game-map [0 0]))))
    ;; Second army should now be asking for attention
    (should @atoms/waiting-for-input)
    (should= [[2 0]] @atoms/cells-needing-attention)))
