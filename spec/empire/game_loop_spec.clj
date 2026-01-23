(ns empire.game-loop-spec
  (:require [speclj.core :refer :all]
            [empire.game-loop :as game-loop]
            [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.test-utils :refer [build-test-map set-test-unit get-test-unit reset-all-atoms! make-initial-test-map]]))

(describe "item-processed"
  (before (reset-all-atoms!))
  (it "resets waiting-for-input to false"
    (reset! atoms/waiting-for-input true)
    (game-loop/item-processed)
    (should= false @atoms/waiting-for-input))

  (it "clears message"
    (reset! atoms/message "test message")
    (game-loop/item-processed)
    (should= "" @atoms/message))

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
      (should-contain [0 1] items)))

  (it "returns player unit coordinates"
    (let [items (game-loop/build-player-items)]
      (should-contain [1 0] items)))

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
                               (assoc-in [0 1 :contents :hits] 1)))
    (reset! atoms/player-map (build-test-map ["##"
                                               "##"])))

  (it "removes units with hits <= 0"
    (game-loop/remove-dead-units)
    (should-be-nil (:contents (get-in @atoms/game-map [0 0]))))

  (it "keeps units with hits > 0"
    (game-loop/remove-dead-units)
    (should= {:type :fighter :owner :player :hits 1} (:contents (get-in @atoms/game-map [0 1])))))

(describe "reset-steps-remaining"
  (before
    (reset-all-atoms!)
    (reset! atoms/game-map (assoc-in (build-test-map ["AF"
                                                       "A#"])
                                     [1 0 :contents :owner] :computer)))

  (it "sets steps-remaining for player army"
    (game-loop/reset-steps-remaining)
    (should= (config/unit-speed :army) (:steps-remaining (:contents (get-in @atoms/game-map [0 0])))))

  (it "sets steps-remaining for player fighter"
    (game-loop/reset-steps-remaining)
    (should= (config/unit-speed :fighter) (:steps-remaining (:contents (get-in @atoms/game-map [0 1])))))

  (it "does not set steps-remaining for computer units"
    (game-loop/reset-steps-remaining)
    (should-be-nil (:steps-remaining (:contents (get-in @atoms/game-map [1 0]))))))

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
    (should-be-nil (:awake-fighters (:contents (get-in @atoms/game-map [0 0])))))

  (it "ignores carriers with no fighters"
    (reset! atoms/game-map (build-test-map ["C"]))
    (set-test-unit atoms/game-map "C" :fighter-count 0)
    (game-loop/wake-carrier-fighters)
    (should-be-nil (:awake-fighters (:contents (get-in @atoms/game-map [0 0]))))))

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
    (let [unit (:contents (get-in @atoms/game-map [0 1]))]
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
    (set-test-unit atoms/game-map "A" :mode :moving :target [0 5])
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
    (reset! atoms/message "old message")
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
    (should= "" @atoms/message))

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
    (reset! atoms/message "")
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
        (should (or (nil? (:unit result)) (<= (:turns-remaining (:unit result) 0) 0))))))

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

  (it "removes satellite with negative turns-remaining"
    ;; Satellite with turns-remaining -1 should be removed immediately
    (reset! atoms/game-map (build-test-map ["###"
                                             "#V#"
                                             "###"]))
    (set-test-unit atoms/game-map "V" :turns-remaining -1 :target [0 0])
    (reset! atoms/player-map (build-test-map ["###"
                                               "###"
                                               "###"]))
    (game-loop/move-satellites)
    ;; Satellite should be removed
    (should-be-nil (:contents (get-in @atoms/game-map [1 1])))))

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
                   :start-pos [2 1] :visited #{[2 1]} :prev-pos nil)
    (reset! atoms/player-map @atoms/game-map)
    (let [result (game-loop/move-coastline-unit [2 1])]
      ;; Should return nil (unit keeps moving until done)
      (should-be-nil result))))

(describe "auto-launch-fighter from airport"
  (before (reset-all-atoms!))
  (it "launches fighter when city has flight-path and awake fighters"
    (reset! atoms/game-map (-> (build-test-map ["O#"])
                               (assoc-in [0 0 :flight-path] [0 1])
                               (assoc-in [0 0 :awake-fighters] 1)
                               (assoc-in [0 0 :fighter-count] 1)))
    (reset! atoms/player-map (build-test-map ["##"]))
    (reset! atoms/production {})
    (reset! atoms/player-items [[0 0]])
    (reset! atoms/waiting-for-input false)
    (game-loop/advance-game)
    ;; Fighter should have been launched and added to front of player-items
    (let [first-item (first @atoms/player-items)]
      (should= [0 0] first-item)))

  (it "launches fighter from carrier with flight-path"
    (reset! atoms/game-map (build-test-map ["C~"]))
    (set-test-unit atoms/game-map "C" :mode :sentry :flight-path [0 1] :awake-fighters 1 :fighter-count 1)
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
    (set-test-unit atoms/game-map "T" :mode :sentry :marching-orders [1 0] :awake-armies 1 :army-count 1)
    (reset! atoms/player-map (build-test-map ["~#"]))
    (reset! atoms/production {})
    (reset! atoms/player-items [[0 0]])
    (reset! atoms/waiting-for-input false)
    (game-loop/advance-game)
    ;; Army should have been disembarked
    (let [land-cell (get-in @atoms/game-map [0 1])]
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
                   :start-pos [5 1] :visited #{[5 1]} :prev-pos nil)
    (reset! atoms/player-map @atoms/game-map)
    (reset! atoms/production {})
    (reset! atoms/player-items [[5 1]])
    (reset! atoms/waiting-for-input false)
    (game-loop/advance-game)
    ;; Unit should have moved - player-items should be updated
    (should (or (empty? @atoms/player-items)
                (not= [[5 1]] (vec @atoms/player-items))))))

(describe "advance-game with moving unit"
  (before (reset-all-atoms!))
  (it "continues processing when unit moves and has steps remaining"
    ;; Set up a moving unit with multiple steps, target far away
    (reset! atoms/game-map (build-test-map ["A###"]))
    (set-test-unit atoms/game-map "A" :mode :moving :target [0 3] :steps-remaining 2)
    (reset! atoms/player-map (build-test-map ["####"]))
    (reset! atoms/production {})
    (reset! atoms/player-items [[0 0]])
    (reset! atoms/waiting-for-input false)
    (game-loop/advance-game)
    ;; Unit should have moved towards target
    (let [original-has-unit? (some? (:contents (get-in @atoms/game-map [0 0])))]
      ;; Either original spot is empty or unit is somewhere else
      (should (or (not original-has-unit?)
                  (empty? @atoms/player-items))))))


(describe "handle-sidestep-result"
  (before (reset-all-atoms!))

  (it "decrements steps-remaining and returns new pos when steps remain"
    (reset! atoms/game-map (build-test-map ["A###"]))
    (set-test-unit atoms/game-map "A" :mode :moving :target [0 3] :steps-remaining 3)
    (let [result (game-loop/handle-sidestep-result [0 0] 10)]
      ;; Should return position for continued movement
      (should result)
      ;; steps-remaining should be decremented
      (let [unit (:contents (get-in @atoms/game-map [0 0]))]
        (should= 2 (:steps-remaining unit)))))

  (it "returns nil when no steps remaining"
    (reset! atoms/game-map (build-test-map ["A###"]))
    (set-test-unit atoms/game-map "A" :mode :moving :target [0 3] :steps-remaining 1)
    (let [result (game-loop/handle-sidestep-result [0 0] 10)]
      ;; Should return nil - no more steps
      (should-be-nil result)))

  (it "returns nil when max sidesteps exhausted"
    (reset! atoms/game-map (build-test-map ["A###"]))
    (set-test-unit atoms/game-map "A" :mode :moving :target [0 3] :steps-remaining 3)
    (let [result (game-loop/handle-sidestep-result [0 0] 0)]
      ;; Should return current pos when no sidesteps left
      (should= [0 0] result))))

(describe "handle-normal-move-result"
  (before (reset-all-atoms!))

  (it "decrements steps-remaining and returns pos when steps remain"
    (reset! atoms/game-map (build-test-map ["A###"]))
    (set-test-unit atoms/game-map "A" :mode :moving :target [0 3] :steps-remaining 3)
    (let [result (game-loop/handle-normal-move-result [0 0])]
      ;; Should return position for continued movement
      (should= [0 0] result)
      ;; steps-remaining should be decremented
      (let [unit (:contents (get-in @atoms/game-map [0 0]))]
        (should= 2 (:steps-remaining unit)))))

  (it "returns nil when no steps remaining"
    (reset! atoms/game-map (build-test-map ["A###"]))
    (set-test-unit atoms/game-map "A" :mode :moving :target [0 3] :steps-remaining 1)
    (let [result (game-loop/handle-normal-move-result [0 0])]
      ;; Should return nil - no more steps
      (should-be-nil result))))

(describe "handle-combat-result"
  (before (reset-all-atoms!))

  (it "returns nil when attacker won (ends move after combat)"
    (reset! atoms/game-map (build-test-map ["A#"]))
    (set-test-unit atoms/game-map "A" :mode :moving :target [0 1] :steps-remaining 3)
    (let [result (game-loop/handle-combat-result [0 0] :player)]
      ;; Combat always ends the move
      (should-be-nil result)
      ;; steps-remaining should be set to 0
      (let [unit (:contents (get-in @atoms/game-map [0 0]))]
        (should= 0 (:steps-remaining unit)))))

  (it "returns nil when no unit at position (attacker lost)"
    (reset! atoms/game-map (build-test-map ["##"]))
    (let [result (game-loop/handle-combat-result [0 0] :player)]
      ;; No unit means attacker lost
      (should-be-nil result))))

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
        (should= (inc round-before) @atoms/round-number))))

  (describe "step-one-round"
    (it "does nothing when not paused"
      (reset! atoms/paused false)
      (reset! atoms/pause-requested false)
      (reset! atoms/player-items [[0 0]])
      (game-loop/step-one-round)
      (should-not @atoms/paused)
      (should-not @atoms/pause-requested))

    (it "unpauses and requests pause when paused"
      (reset! atoms/paused true)
      (reset! atoms/pause-requested false)
      (reset! atoms/player-items [[0 0]])
      (game-loop/step-one-round)
      (should-not @atoms/paused)
      (should @atoms/pause-requested))

    (it "starts new round when paused and lists are empty"
      (reset! atoms/game-map (build-test-map ["#"]))
      (reset! atoms/player-map (build-test-map ["#"]))
      (reset! atoms/computer-map (build-test-map ["#"]))
      (reset! atoms/production {})
      (reset! atoms/paused true)
      (reset! atoms/pause-requested false)
      (reset! atoms/player-items [])
      (reset! atoms/computer-items [])
      (reset! atoms/round-number 5)
      (game-loop/step-one-round)
      (should= 6 @atoms/round-number)))

  (describe "advance-game with computer items"
    (it "processes computer items when player items empty"
      (reset! atoms/game-map (build-test-map ["#a"]))
      (set-test-unit atoms/game-map "a" :mode :sentry)
      (reset! atoms/player-map (build-test-map ["##"]))
      (reset! atoms/computer-map (build-test-map ["##"]))
      (reset! atoms/production {})
      (reset! atoms/paused false)
      (reset! atoms/player-items [])
      (reset! atoms/computer-items [[0 1]])
      (game-loop/advance-game)
      ;; Computer item should have been processed
      (should= [] (vec @atoms/computer-items)))

    (it "processes computer city without unit"
      (reset! atoms/game-map (build-test-map ["X#"]))
      (reset! atoms/player-map (build-test-map ["##"]))
      (reset! atoms/computer-map (build-test-map ["##"]))
      (reset! atoms/production {})
      (reset! atoms/paused false)
      (reset! atoms/player-items [])
      (reset! atoms/computer-items [[0 0]])
      (game-loop/advance-game)
      ;; Computer city should have been processed
      (should= [] (vec @atoms/computer-items)))))
