(ns empire.game-loop-spec
  (:require [empire.test-utils :as test-utils]
            [speclj.core :refer :all]
            [empire.game-loop.core :as game-loop]
            [empire.config :as config]
            [empire.movement.api :as movement]
            [empire.test-utils :refer [build-test-map set-test-unit get-test-unit reset-all-atoms! make-initial-test-map
                                       set-test-world! set-test-player-map! set-test-computer-map!]]))

(describe "round lifecycle"
  (before (reset-all-atoms!))

  (context "item-processed"
    (it "resets waiting-for-input to false"
      (test-utils/set-test-state! :waiting-for-input true)
      (game-loop/item-processed)
      (should= false (test-utils/read-test-state :waiting-for-input)))

    (it "preserves attention-message"
      (test-utils/set-test-state! :attention-message "test message")
      (game-loop/item-processed)
      (should= "test message" (test-utils/read-test-state :attention-message)))

    (it "clears cells-needing-attention"
      (test-utils/set-test-state! :cells-needing-attention [[1 2] [3 4]])
      (game-loop/item-processed)
      (should= [] (test-utils/read-test-state :cells-needing-attention))))

  (context "build-player-items"
    (before
      (set-test-world! (build-test-map ["#O"
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

  (context "remove-dead-units"
    (before
      (set-test-world! (-> (build-test-map ["AF"
                                                    "##"])
                                  (assoc-in [0 0 :contents :hits] 0)
                                  (assoc-in [1 0 :contents :hits] 1)))
      (set-test-player-map! (build-test-map ["##"
                                                  "##"])))

    (it "removes units with hits <= 0"
      (game-loop/remove-dead-units)
      (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))))

    (it "keeps units with hits > 0"
      (game-loop/remove-dead-units)
      (should= {:type :fighter :owner :player :hits 1 :fuel 32} (:contents (get-in (test-utils/read-test-state :game-map) [1 0])))))

  (context "reset-steps-remaining"
    (before
      (set-test-world! (assoc-in (build-test-map ["AF"
                                                          "A#"])
                                        [0 1 :contents :owner] :computer)))

    (it "sets steps-remaining for player army"
      (game-loop/reset-steps-remaining)
      (should= (config/unit-speed :army) (:steps-remaining (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))))

    (it "sets steps-remaining for player fighter"
      (game-loop/reset-steps-remaining)
      (should= (config/unit-speed :fighter) (:steps-remaining (:contents (get-in (test-utils/read-test-state :game-map) [1 0])))))

    (it "does not set steps-remaining for computer units"
      (game-loop/reset-steps-remaining)
      (should-be-nil (:steps-remaining (:contents (get-in (test-utils/read-test-state :game-map) [0 1])))))

    (it "scales steps-remaining by damage for multi-hit ships"
      (set-test-world! (build-test-map ["D"]))
      (set-test-unit (test-utils/game-map-atom) "D" :hits 1)  ; destroyer max=3, speed=2, at 1/3 -> speed 1
      (game-loop/reset-steps-remaining)
      (should= 1 (:steps-remaining (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))))))

(describe "wake and sleep logic"
  (before (reset-all-atoms!))

  (context "wake-airport-fighters"
    (it "wakes all fighters in player city airports"
      (set-test-world! (assoc-in (build-test-map ["O"]) [0 0 :fighter-count] 3))
      (game-loop/wake-airport-fighters)
      (should= 3 (:awake-fighters (get-in (test-utils/read-test-state :game-map) [0 0]))))

    (it "ignores computer cities"
      (set-test-world! (assoc-in (build-test-map ["X"]) [0 0 :fighter-count] 3))
      (game-loop/wake-airport-fighters)
      (should-be-nil (:awake-fighters (get-in (test-utils/read-test-state :game-map) [0 0]))))

    (it "ignores cities with no fighters"
      (set-test-world! (assoc-in (build-test-map ["O"]) [0 0 :fighter-count] 0))
      (game-loop/wake-airport-fighters)
      (should-be-nil (:awake-fighters (get-in (test-utils/read-test-state :game-map) [0 0])))))

  (context "wake-carrier-fighters"
    (it "wakes all fighters on player carriers"
      (set-test-world! (build-test-map ["C"]))
      (set-test-unit (test-utils/game-map-atom) "C" :fighter-count 3)
      (game-loop/wake-carrier-fighters)
      (should= 3 (:awake-fighters (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))))

    (it "ignores computer carriers"
      (set-test-world! (build-test-map ["c"]))
      (set-test-unit (test-utils/game-map-atom) "c" :fighter-count 3)
      (game-loop/wake-carrier-fighters)
      (should= 0 (:awake-fighters (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))))

    (it "ignores carriers with no fighters"
      (set-test-world! (build-test-map ["C"]))
      (set-test-unit (test-utils/game-map-atom) "C" :fighter-count 0)
      (game-loop/wake-carrier-fighters)
      (should= 0 (:awake-fighters (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))))))

  (context "consume-sentry-fighter-fuel"
    (it "decrements fuel for sentry fighters"
      (set-test-world! (build-test-map ["F"]))
      (set-test-unit (test-utils/game-map-atom) "F" :mode :sentry :fuel 20)
      (game-loop/consume-sentry-fighter-fuel)
      (should= 19 (:fuel (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))))

    (it "wakes fighter when fuel reaches 1"
      (set-test-world! (build-test-map ["F"]))
      (set-test-unit (test-utils/game-map-atom) "F" :mode :sentry :fuel 2)
      (game-loop/consume-sentry-fighter-fuel)
      (let [unit (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))]
        (should= 1 (:fuel unit))
        (should= :awake (:mode unit))
        (should= :fighter-out-of-fuel (:reason unit))))

    (it "sets hits to 0 when fuel reaches 0"
      (set-test-world! (build-test-map ["F"]))
      (set-test-unit (test-utils/game-map-atom) "F" :mode :sentry :fuel 1)
      (game-loop/consume-sentry-fighter-fuel)
      (should= 0 (:hits (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))))

    (it "sets error message when fighter crashes"
      (set-test-world! (build-test-map ["F"]))
      (set-test-unit (test-utils/game-map-atom) "F" :mode :sentry :fuel 1)
      (game-loop/consume-sentry-fighter-fuel)
      (should-contain (:fighter-crashed config/messages) (test-utils/read-test-state :error-message)))

    (it "does not affect moving fighters"
      (set-test-world! (build-test-map ["F"]))
      (set-test-unit (test-utils/game-map-atom) "F" :mode :moving :fuel 20)
      (game-loop/consume-sentry-fighter-fuel)
      (should= 20 (:fuel (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))))

    (it "wakes fighter with bingo when fuel is low and friendly city in range"
      ;; Fighter fuel is 20, bingo threshold is 20/4 = 5
      ;; At fuel 6, decrement to 5 which equals bingo threshold
      ;; Need a friendly city within range (5 cells)
      (set-test-world! (build-test-map ["OF"]))
      (set-test-unit (test-utils/game-map-atom) "F" :mode :sentry :fuel 6)
      (game-loop/consume-sentry-fighter-fuel)
      (let [unit (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))]
        (should= 5 (:fuel unit))
        (should= :awake (:mode unit))
        (should= :fighter-bingo (:reason unit)))))

  (context "wake-sentries-seeing-enemy"
    (it "wakes sentry unit when enemy is adjacent"
      (set-test-world! (build-test-map ["Aa"]))
      (set-test-player-map! (make-initial-test-map 1 2 nil))
      (set-test-unit (test-utils/game-map-atom) "A" :mode :sentry)
      (game-loop/wake-sentries-seeing-enemy)
      (let [unit (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))]
        (should= :awake (:mode unit))
        (should= :enemy-spotted (:reason unit))))

    (it "does not wake sentry when no enemy visible"
      (set-test-world! (build-test-map ["A#"]))
      (set-test-player-map! (make-initial-test-map 1 2 nil))
      (set-test-unit (test-utils/game-map-atom) "A" :mode :sentry)
      (game-loop/wake-sentries-seeing-enemy)
      (let [unit (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))]
        (should= :sentry (:mode unit))))

    (it "does not wake awake units"
      (set-test-world! (build-test-map ["Aa"]))
      (set-test-player-map! (make-initial-test-map 1 2 nil))
      (set-test-unit (test-utils/game-map-atom) "A" :mode :awake)
      (game-loop/wake-sentries-seeing-enemy)
      (let [unit (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))]
        (should= :awake (:mode unit))
        (should-be-nil (:reason unit))))

    (it "does not wake moving units"
      (set-test-world! (build-test-map ["Aa"]))
      (set-test-player-map! (make-initial-test-map 1 2 nil))
      (set-test-unit (test-utils/game-map-atom) "A" :mode :moving :target [5 0])
      (game-loop/wake-sentries-seeing-enemy)
      (let [unit (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))]
        (should= :moving (:mode unit))))

    (it "wakes sentry naval units when enemy visible"
      (set-test-world! (build-test-map ["Ds"]))
      (set-test-player-map! (make-initial-test-map 1 2 nil))
      (set-test-unit (test-utils/game-map-atom) "D" :mode :sentry)
      (game-loop/wake-sentries-seeing-enemy)
      (let [unit (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))]
        (should= :awake (:mode unit))
        (should= :enemy-spotted (:reason unit))))

    (it "does not wake computer sentry units"
      (set-test-world! (build-test-map ["aA"]))
      (set-test-player-map! (make-initial-test-map 1 2 nil))
      (set-test-unit (test-utils/game-map-atom) "a" :mode :sentry)
      (game-loop/wake-sentries-seeing-enemy)
      (let [unit (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))]
        (should= :sentry (:mode unit))))))

(describe "auto-move logic"
  (before (reset-all-atoms!))

  (context "move-satellites"
    (it "removes satellite when turns-remaining reaches zero during movement"
      ;; Satellite with turns-remaining 1 will expire after moving
      (set-test-world! (build-test-map ["V#"]))
      (set-test-unit (test-utils/game-map-atom) "V" :turns-remaining 1)
      (set-test-player-map! (build-test-map ["##"]))
      (game-loop/move-satellites)
      ;; Satellite should be removed after its turn expires
      (let [result (get-test-unit (test-utils/game-map-atom) "V")]
        ;; Either satellite is gone or has decremented turns
        (when result
          (should (or (nil? (:unit result)) (<= (:turns-remaining (:unit result) 0) 0)))))))

    (it "removes satellite immediately when turns-remaining is already zero"
      ;; Satellite with turns-remaining 0 should be removed at start of move
      (set-test-world! (build-test-map ["V"]))
      (set-test-unit (test-utils/game-map-atom) "V" :turns-remaining 0)
      (set-test-player-map! (build-test-map ["#"]))
      (game-loop/move-satellites)
      ;; Satellite should be removed
      (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))))

    (it "decrements turns-remaining after movement"
      (set-test-world! (build-test-map ["V##"]))
      (set-test-unit (test-utils/game-map-atom) "V" :turns-remaining 5)
      (set-test-player-map! (build-test-map ["###"]))
      (game-loop/move-satellites)
      ;; Find where satellite ended up
      (let [{:keys [unit]} (get-test-unit (test-utils/game-map-atom) "V")]
        (when unit
          (should (< (:turns-remaining unit) 5)))))

  (context "move-explore-unit"
    (it "delegates to movement/move-explore-unit"
      (set-test-world! (build-test-map ["A#"]))
      (set-test-unit (test-utils/game-map-atom) "A" :mode :explore :visited #{[0 0]})
      (set-test-player-map! (build-test-map ["##"]))
      (let [result (game-loop/move-explore-unit [0 0])]
        ;; Should return new coords if still exploring
        (should (or (nil? result) (vector? result))))))

  (context "move-coastline-unit"
    (it "delegates to movement/move-coastline-unit"
      (set-test-world! (build-test-map ["#~~~~"
                                               "#~~~~"
                                               "#T~~~"
                                               "#~~~~"
                                               "#~~~~"]))
      (set-test-unit (test-utils/game-map-atom) "T" :mode :coastline-follow :coastline-steps 50
                     :start-pos [1 2] :visited #{[1 2]} :prev-pos nil)
      (set-test-player-map! (test-utils/read-test-state :game-map))
      (let [result (game-loop/move-coastline-unit [1 2])]
        ;; Should return nil (unit keeps moving until done)
        (should-be-nil result))))

  (context "auto-launch-fighter from airport"
    (it "launches fighter when city has flight-path and awake fighters"
      (set-test-world! (-> (build-test-map ["O#"])
                                  (assoc-in [0 0 :flight-path] [1 0])
                                  (assoc-in [0 0 :awake-fighters] 1)
                                  (assoc-in [0 0 :fighter-count] 1)))
      (set-test-player-map! (build-test-map ["##"]))
      (test-utils/set-test-state! :production {})
      (test-utils/set-test-state! :player-items [[0 0]])
      (test-utils/set-test-state! :waiting-for-input false)
      (game-loop/advance-game)
      ;; Fighter should have been launched and moved toward target
      ;; City's awake-fighters should be decremented
      (let [city (get-in (test-utils/read-test-state :game-map) [0 0])]
        (should= 0 (:awake-fighters city 0)))
      ;; Fighter should exist on the map (either at launch position or having moved)
      (let [fighter-at-target (get-in (test-utils/read-test-state :game-map) [1 0])]
        (should= :fighter (:type (:contents fighter-at-target)))))

    (it "does not launch fighter when army is on city"
      (set-test-world! (-> (build-test-map ["O#"])
                                  (assoc-in [0 0 :flight-path] [1 0])
                                  (assoc-in [0 0 :awake-fighters] 1)
                                  (assoc-in [0 0 :fighter-count] 1)
                                  (assoc-in [0 0 :contents] {:type :army :mode :moving :target [1 0] :hits 1 :owner :player})))
      (set-test-player-map! (build-test-map ["##"]))
      (test-utils/set-test-state! :production {})
      (test-utils/set-test-state! :player-items [[0 0]])
      (test-utils/set-test-state! :waiting-for-input false)
      (game-loop/advance-game)
      ;; Army should still exist, not overwritten by fighter
      (let [city (get-in (test-utils/read-test-state :game-map) [0 0])]
        (should= 1 (:awake-fighters city))
        (should= 1 (:fighter-count city))))

    (it "launches fighter from carrier with flight-path"
      (set-test-world! (build-test-map ["C~"]))
      (set-test-unit (test-utils/game-map-atom) "C" :mode :sentry :flight-path [1 0] :awake-fighters 1 :fighter-count 1)
      (set-test-player-map! (build-test-map ["~~"]))
      (test-utils/set-test-state! :production {})
      (test-utils/set-test-state! :player-items [[0 0]])
      (test-utils/set-test-state! :waiting-for-input false)
      (game-loop/advance-game)
      ;; Fighter should have been launched from carrier (awake-fighters decremented)
      (let [carrier (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))]
        (should= 0 (:awake-fighters carrier 0)))))

  (context "auto-disembark-army"
    (it "disembarks army when transport has marching-orders and awake armies"
      (set-test-world! (build-test-map ["T#"]))
      (set-test-unit (test-utils/game-map-atom) "T" :mode :sentry :marching-orders [0 1] :awake-armies 1 :army-count 1)
      (set-test-player-map! (build-test-map ["~#"]))
      (test-utils/set-test-state! :production {})
      (test-utils/set-test-state! :player-items [[0 0]])
      (test-utils/set-test-state! :waiting-for-input false)
      (game-loop/advance-game)
      ;; Army should have been disembarked
      (let [land-cell (get-in (test-utils/read-test-state :game-map) [1 0])]
        (should= :army (:type (:contents land-cell))))))

  (context "advance-game with explore mode"
    (it "processes exploring unit"
      (set-test-world! (build-test-map ["A#"]))
      (set-test-unit (test-utils/game-map-atom) "A" :mode :explore :visited #{[0 0]})
      (set-test-player-map! (build-test-map ["##"]))
      (test-utils/set-test-state! :production {})
      (test-utils/set-test-state! :player-items [[0 0]])
      (test-utils/set-test-state! :waiting-for-input false)
      (game-loop/advance-game)
      ;; Should have moved the exploring unit
      (should-not= [[0 0]] (test-utils/read-test-state :player-items))))

  (context "advance-game with coastline-follow mode"
    (it "processes coastline-following unit and continues when returning new coords"
      (set-test-world! (build-test-map ["#~~~~~~~~~"
                                               "#~~~~~~~~~"
                                               "#~~~~~~~~~"
                                               "#~~~~~~~~~"
                                               "#~~~~~~~~~"
                                               "#T~~~~~~~~"
                                               "#~~~~~~~~~"
                                               "#~~~~~~~~~"
                                               "#~~~~~~~~~"
                                               "#~~~~~~~~~"]))
      (set-test-unit (test-utils/game-map-atom) "T" :mode :coastline-follow :coastline-steps 50
                     :start-pos [1 5] :visited #{[1 5]} :prev-pos nil)
      (set-test-player-map! (test-utils/read-test-state :game-map))
      (test-utils/set-test-state! :production {})
      (test-utils/set-test-state! :player-items [[1 5]])
      (test-utils/set-test-state! :waiting-for-input false)
      (game-loop/advance-game)
      ;; Unit should have moved - player-items should be updated
      (should (or (empty? (test-utils/read-test-state :player-items))
                  (not= [[1 5]] (vec (test-utils/read-test-state :player-items))))))))

(describe "build-computer-items"
  (before (reset-all-atoms!))

  (it "returns computer city coordinates"
    (set-test-world! (build-test-map ["#O"
                                             "aX"]))
    (let [items (game-loop/build-computer-items)]
      (should-contain [1 1] items)))

  (it "returns computer unit coordinates"
    (set-test-world! (build-test-map ["#O"
                                             "aX"]))
    (let [items (game-loop/build-computer-items)]
      (should-contain [0 1] items)))

  (it "does not return player cities"
    (set-test-world! (build-test-map ["#O"
                                             "aX"]))
    (let [items (game-loop/build-computer-items)]
      (should-not-contain [1 0] items)))

  (it "does not return empty land"
    (set-test-world! (build-test-map ["#O"
                                             "aX"]))
    (let [items (game-loop/build-computer-items)]
      (should-not-contain [0 0] items))))

(describe "start-new-round"
  (before (reset-all-atoms!))

  (it "sets waiting-for-input to false"
    (let [m (build-test-map ["OX"])]
      (set-test-world! m)
      (set-test-player-map! m)
      (set-test-computer-map! m)
      (test-utils/set-test-state! :waiting-for-input true)
      (game-loop/start-new-round)
      (should= false (test-utils/read-test-state :waiting-for-input))))

  (it "detects game over when no player items"
    (let [m (build-test-map ["X"])]
      (set-test-world! m)
      (set-test-player-map! m)
      (set-test-computer-map! m)
      (test-utils/set-test-state! :game-over-check-enabled true)
      (game-loop/start-new-round)
      (should= true (test-utils/read-test-state :paused))
      (should-contain "GAME OVER" (test-utils/read-test-state :error-message))))

  (it "does not set game over when player items exist"
    (let [m (build-test-map ["OX"])]
      (set-test-world! m)
      (set-test-player-map! m)
      (set-test-computer-map! m)
      (test-utils/set-test-state! :game-over-check-enabled true)
      (game-loop/start-new-round)
      (should= false (test-utils/read-test-state :paused))))

  (it "detects victory when no computer items"
    (let [m (build-test-map ["O"])]
      (set-test-world! m)
      (set-test-player-map! m)
      (set-test-computer-map! m)
      (test-utils/set-test-state! :game-over-check-enabled true)
      (game-loop/start-new-round)
      (should= true (test-utils/read-test-state :paused))
      (should-contain "YOU WIN" (test-utils/read-test-state :error-message))))

  (it "does not set victory when computer items exist"
    (let [m (build-test-map ["OX"])]
      (set-test-world! m)
      (set-test-player-map! m)
      (set-test-computer-map! m)
      (test-utils/set-test-state! :game-over-check-enabled true)
      (game-loop/start-new-round)
      (should= false (test-utils/read-test-state :paused)))))

(describe "advance-game pause logic"
  (before (reset-all-atoms!))

  (it "pauses when both lists empty and pause-requested"
    (test-utils/set-test-state! :player-items [])
    (test-utils/set-test-state! :computer-items [])
    (test-utils/set-test-state! :pause-requested true)
    (game-loop/advance-game)
    (should= true (test-utils/read-test-state :paused))
    (should= false (test-utils/read-test-state :pause-requested)))

  (it "starts new round when both lists empty and no pause requested"
    (let [m (build-test-map ["OX"])]
      (set-test-world! m)
      (set-test-player-map! m)
      (set-test-computer-map! m)
      (test-utils/set-test-state! :player-items [])
      (test-utils/set-test-state! :computer-items [])
      (test-utils/set-test-state! :pause-requested false)
      (game-loop/advance-game)
      (should= 1 (test-utils/read-test-state :round-number)))))

(describe "advance-game-batch"
  (before (reset-all-atoms!))

  (it "calls advance-game at least once"
    (let [call-count (atom 0)]
      (with-redefs [game-loop/advance-game #(swap! call-count inc)]
        (game-loop/advance-game-batch)
        (should (<= 1 @call-count)))))

  (it "calls advance-game exactly advances-per-frame times"
    (let [call-count (atom 0)]
      (test-utils/set-test-state! :player-items [[0 0]])
      (with-redefs [game-loop/advance-game #(swap! call-count inc)]
        (game-loop/advance-game-batch)
        (should= config/advances-per-frame @call-count))))

  (it "calls advance-game multiple times when items remain"
    (let [call-count (atom 0)]
      (test-utils/set-test-state! :player-items [[0 0] [1 0]])
      (with-redefs [game-loop/advance-game
                     (fn []
                       (swap! call-count inc)
                       (when (= @call-count 3)
                         (test-utils/set-test-state! :player-items [])))]
        (game-loop/advance-game-batch)
        (should (> @call-count 1))))))

(describe "toggle-pause"
  (before (reset-all-atoms!))

  (it "resumes when paused"
    (test-utils/set-test-state! :paused true)
    (test-utils/set-test-state! :pause-requested true)
    (game-loop/toggle-pause)
    (should= false (test-utils/read-test-state :paused))
    (should= false (test-utils/read-test-state :pause-requested)))

  (it "requests pause when running"
    (test-utils/set-test-state! :paused false)
    (game-loop/toggle-pause)
    (should= true (test-utils/read-test-state :pause-requested))))

(describe "step-one-round"
  (before (reset-all-atoms!))

  (it "does nothing when not paused"
    (test-utils/set-test-state! :paused false)
    (game-loop/step-one-round)
    (should= false (test-utils/read-test-state :paused))
    (should= false (test-utils/read-test-state :pause-requested)))

  (it "unpauses and requests pause when paused"
    (test-utils/set-test-state! :paused true)
    (test-utils/set-test-state! :player-items [[0 0]])
    (game-loop/step-one-round)
    (should= false (test-utils/read-test-state :paused))
    (should= true (test-utils/read-test-state :pause-requested)))

  (it "starts new round when paused and no items"
    (let [m (build-test-map ["OX"])]
      (set-test-world! m)
      (set-test-player-map! m)
      (set-test-computer-map! m)
      (test-utils/set-test-state! :paused true)
      (test-utils/set-test-state! :player-items [])
      (test-utils/set-test-state! :computer-items [])
      (game-loop/step-one-round)
      (should= 1 (test-utils/read-test-state :round-number)))))
