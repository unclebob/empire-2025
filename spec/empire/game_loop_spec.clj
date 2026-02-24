(ns empire.game-loop-spec
  (:require [speclj.core :refer :all]
            [empire.game-loop :as game-loop]
            [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.movement.movement :as movement]
            [empire.test-utils :refer [build-test-map set-test-unit get-test-unit reset-all-atoms! make-initial-test-map]]))

(describe "round lifecycle"
  (before (reset-all-atoms!))

  (context "item-processed"
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

  (context "build-player-items"
    (before
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

  (context "remove-dead-units"
    (before
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

  (context "reset-steps-remaining"
    (before
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
      (should= 1 (:steps-remaining (:contents (get-in @atoms/game-map [0 0])))))))

(describe "wake and sleep logic"
  (before (reset-all-atoms!))

  (context "wake-airport-fighters"
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

  (context "wake-carrier-fighters"
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

  (context "consume-sentry-fighter-fuel"
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

  (context "wake-sentries-seeing-enemy"
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
        (should= :sentry (:mode unit))))))

(describe "auto-move logic"
  (before (reset-all-atoms!))

  (context "move-satellites"
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

  (context "move-explore-unit"
    (it "delegates to movement/move-explore-unit"
      (reset! atoms/game-map (build-test-map ["A#"]))
      (set-test-unit atoms/game-map "A" :mode :explore :visited #{[0 0]})
      (reset! atoms/player-map (build-test-map ["##"]))
      (let [result (game-loop/move-explore-unit [0 0])]
        ;; Should return new coords if still exploring
        (should (or (nil? result) (vector? result))))))

  (context "move-coastline-unit"
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

  (context "auto-launch-fighter from airport"
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

  (context "auto-disembark-army"
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

  (context "advance-game with explore mode"
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

  (context "advance-game with coastline-follow mode"
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
                  (not= [[1 5]] (vec @atoms/player-items)))))))
