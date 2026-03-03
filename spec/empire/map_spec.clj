(ns empire.map-spec
  (:require [empire.test-utils :as test-utils]
            [speclj.core :refer :all]
            [empire.player.attention :as attention]
            [empire.combat :as combat]
            [empire.config :as config]
            [empire.game-loop :as game-loop]
            [empire.ui.util.input.actions :as input]
            [empire.movement.explore :as explore]
            [empire.movement.map-utils :as map-utils]
            [empire.movement.api :as movement]
            [empire.test-utils :refer [build-test-map set-test-unit get-test-unit reset-all-atoms! make-initial-test-map
                                       set-test-world! set-test-player-map!]]))

(describe "build-player-items"
  (before (reset-all-atoms!))
  (it "returns coordinates of player cities"
    (set-test-world! (build-test-map ["OX"]))
    (should= [[0 0]] (vec (game-loop/build-player-items))))

  (it "returns coordinates of player units"
    (set-test-world! (build-test-map ["Aa"]))
    (should= [[0 0]] (vec (game-loop/build-player-items))))

  (it "returns both cities and units"
    (set-test-world! (build-test-map ["OA"
                                             "#X"]))
    (should= [[0 0] [1 0]] (vec (game-loop/build-player-items))))

  (it "returns empty list when no player items"
    (set-test-world! (build-test-map ["~#"]))
    (should= [] (vec (game-loop/build-player-items)))))

(describe "item-processed"
  (before (reset-all-atoms!))
  (it "resets waiting-for-input to false"
    (test-utils/set-test-state! :waiting-for-input true)
    (game-loop/item-processed)
    (should= false (test-utils/read-test-state :waiting-for-input)))

  (it "preserves attention-message"
    (test-utils/set-test-state! :attention-message "Some message")
    (game-loop/item-processed)
    (should= "Some message" (test-utils/read-test-state :attention-message)))

  (it "clears cells-needing-attention"
    (test-utils/set-test-state! :cells-needing-attention [[0 0] [1 1]])
    (game-loop/item-processed)
    (should= [] (test-utils/read-test-state :cells-needing-attention))))

(describe "wake-airport-fighters"
  (before (reset-all-atoms!))
  (it "wakes all fighters in player city airports"
    (set-test-world! (-> (build-test-map ["O"])
                               (assoc-in [0 0 :fighter-count] 3)
                               (assoc-in [0 0 :awake-fighters] 0)))
    (game-loop/wake-airport-fighters)
    (should= 3 (:awake-fighters (get-in (test-utils/read-test-state :game-map) [0 0]))))

  (it "ignores computer cities"
    (set-test-world! (-> (build-test-map ["X"])
                               (assoc-in [0 0 :fighter-count] 3)
                               (assoc-in [0 0 :awake-fighters] 0)))
    (game-loop/wake-airport-fighters)
    (should= 0 (:awake-fighters (get-in (test-utils/read-test-state :game-map) [0 0]))))

  (it "ignores cities with no fighters"
    (set-test-world! (assoc-in (build-test-map ["O"]) [0 0 :fighter-count] 0))
    (game-loop/wake-airport-fighters)
    (should= nil (:awake-fighters (get-in (test-utils/read-test-state :game-map) [0 0])))))

(describe "cells-needing-attention"
  (before (reset-all-atoms!))
  (it "returns empty list when no player cells"
    (set-test-player-map! (build-test-map ["~#"
                                               "X#"]))
    (test-utils/set-test-state! :production {})
    (should= [] (attention/cells-needing-attention)))

  (it "returns coordinates of awake units"
    (set-test-player-map! (build-test-map ["A#"
                                               "##"]))
    (set-test-unit (test-utils/player-map-atom) "A" :mode :awake)
    (test-utils/set-test-state! :production {})
    (should= [[0 0]] (attention/cells-needing-attention)))

  (it "returns coordinates of cities with no production"
    (set-test-player-map! (build-test-map ["#O"
                                               "##"]))
    (test-utils/set-test-state! :production {})
    (should= [[1 0]] (attention/cells-needing-attention)))

  (it "excludes cities with production"
    (set-test-player-map! (build-test-map ["O#"]))
    (test-utils/set-test-state! :production {[0 0] {:item :army :remaining-rounds 5}})
    (should= [] (attention/cells-needing-attention)))

  (it "returns multiple coordinates"
    (set-test-player-map! (build-test-map ["AO"
                                               "##"]))
    (set-test-unit (test-utils/player-map-atom) "A" :mode :awake)
    (test-utils/set-test-state! :production {})
    (should= [[0 0] [1 0]] (attention/cells-needing-attention))))

(describe "remove-dead-units"
  (before (reset-all-atoms!))
  (it "removes units with hits at or below zero"
    (set-test-world! (build-test-map ["AFA"
                                             "###"]))
    (set-test-unit (test-utils/game-map-atom) "A" :hits 0)
    (set-test-unit (test-utils/game-map-atom) "F" :hits 1)
    (set-test-unit (test-utils/game-map-atom) "A2" :hits -1)
    (game-loop/remove-dead-units)
    (should= {:type :land} (get-in (test-utils/read-test-state :game-map) [0 0]))
    (should= {:type :land :contents {:type :fighter :hits 1 :owner :player :fuel 32}} (get-in (test-utils/read-test-state :game-map) [1 0]))
    (should= {:type :land} (get-in (test-utils/read-test-state :game-map) [2 0]))))

(describe "reset-steps-remaining"
  (before (reset-all-atoms!))
  (it "initializes steps-remaining for player units based on unit speed"
    (set-test-world! (build-test-map ["AF"
                                             "a#"]))
    (game-loop/reset-steps-remaining)
    (should= (config/unit-speed :army) (:steps-remaining (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))))
    (should= (config/unit-speed :fighter) (:steps-remaining (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))))
    (should= nil (:steps-remaining (:contents (get-in (test-utils/read-test-state :game-map) [0 1])))))

  (it "overwrites existing steps-remaining values"
    (set-test-world! (build-test-map ["F"]))
    (set-test-unit (test-utils/game-map-atom) "F" :steps-remaining 2)
    (game-loop/reset-steps-remaining)
    (should= (config/unit-speed :fighter) (:steps-remaining (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))))))

(describe "set-unit-movement"
  (before (reset-all-atoms!))
  (it "preserves existing steps-remaining when setting movement"
    (set-test-world! (build-test-map ["F#"]))
    (set-test-unit (test-utils/game-map-atom) "F" :mode :awake :steps-remaining 3)
    (movement/set-unit-movement [0 0] [1 0])
    (let [unit (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))]
      (should= :moving (:mode unit))
      (should= [1 0] (:target unit))
      (should= 3 (:steps-remaining unit))))

  (it "clamps out-of-bounds targets to map edge"
    (set-test-world! (build-test-map ["F##"]))
    (set-test-unit (test-utils/game-map-atom) "F" :mode :awake :steps-remaining 3)
    (movement/set-unit-movement [0 0] [99 99])
    (let [unit (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))]
      (should= :moving (:mode unit))
      (should= [2 0] (:target unit)))))

(describe "move-current-unit"
  (before (reset-all-atoms!))
  (before-all
    (set-test-player-map! {}))

  (it "decrements steps-remaining after each move"
    (set-test-world! (build-test-map ["F##"]))
    (set-test-unit (test-utils/game-map-atom) "F" :mode :moving :target [2 0] :steps-remaining 3)
    (game-loop/move-current-unit [0 0])
    (should= 2 (:steps-remaining (:contents (get-in (test-utils/read-test-state :game-map) [1 0])))))

  (it "returns new coords when steps remain"
    (set-test-world! (build-test-map ["F##"]))
    (set-test-unit (test-utils/game-map-atom) "F" :mode :moving :target [2 0] :steps-remaining 3)
    (should= [1 0] (game-loop/move-current-unit [0 0])))

  (it "returns nil when steps-remaining reaches zero"
    (set-test-world! (build-test-map ["A##"]))
    (set-test-unit (test-utils/game-map-atom) "A" :mode :moving :target [2 0] :steps-remaining 1)
    (should= nil (game-loop/move-current-unit [0 0])))

  (it "returns new coords when unit wakes up with steps remaining"
    (set-test-world! (build-test-map ["A#"]))
    (set-test-unit (test-utils/game-map-atom) "A" :mode :moving :target [1 0] :steps-remaining 3)
    (let [result (game-loop/move-current-unit [0 0])]
      (should= [1 0] result)
      (should= :awake (:mode (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))))))

  (it "returns nil when unit wakes up at target with no steps remaining and no reason"
    (set-test-world! (build-test-map ["A#"]))
    (set-test-unit (test-utils/game-map-atom) "A" :mode :moving :target [1 0] :steps-remaining 1)
    (should= nil (game-loop/move-current-unit [0 0]))
    ;; Unit should still be awake at target, just not needing immediate attention
    (let [unit (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))]
      (should= :awake (:mode unit))
      (should= 0 (:steps-remaining unit))))

  (it "limits unit to its rate per round even with new orders"
    (set-test-world! (build-test-map ["A##"]))
    (set-test-unit (test-utils/game-map-atom) "A" :mode :moving :target [1 0] :steps-remaining 1)
    ;; Move once, using the last step
    (game-loop/move-current-unit [0 0])
    (should= 0 (:steps-remaining (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))))
    ;; Give new orders
    (movement/set-unit-movement [1 0] [2 0])
    ;; steps-remaining should still be 0
    (should= 0 (:steps-remaining (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))))
    ;; Try to move again - should return nil since no steps left
    (should= nil (game-loop/move-current-unit [1 0]))))

(describe "attempt-fighter-overfly"
  (before (reset-all-atoms!))
  (it "shoots down fighter when flying over free city"
    (set-test-world! (build-test-map ["F+"]))
    (set-test-unit (test-utils/game-map-atom) "F" :mode :awake)
    (test-utils/set-test-state! :error-message "")
    (combat/attempt-fighter-overfly [0 0] [1 0])
    ;; Fighter should be removed from original cell
    (should= nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))
    ;; Fighter should be on city with hits=0
    (let [fighter (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))]
      (should= 0 (:hits fighter))
      (should= 0 (:steps-remaining fighter))
      (should= :awake (:mode fighter))
      (should= :fighter-shot-down (:reason fighter)))
    (should= (:fighter-destroyed-by-city config/messages) (test-utils/read-test-state :error-message)))

  (it "shoots down fighter when flying over computer city"
    (set-test-world! (build-test-map ["FX"]))
    (set-test-unit (test-utils/game-map-atom) "F" :mode :awake)
    (test-utils/set-test-state! :error-message "")
    (combat/attempt-fighter-overfly [0 0] [1 0])
    ;; Fighter should be removed from original cell
    (should= nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))
    ;; Fighter should be on city with hits=0
    (let [fighter (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))]
      (should= 0 (:hits fighter))
      (should= 0 (:steps-remaining fighter))
      (should= :awake (:mode fighter))
      (should= :fighter-shot-down (:reason fighter)))
    (should= (:fighter-destroyed-by-city config/messages) (test-utils/read-test-state :error-message))))

(describe "sentry mode"
  (before (reset-all-atoms!))
  (it "handle-key with 's' puts unit in sentry mode"
    (set-test-world! (build-test-map ["A"]))
    (set-test-unit (test-utils/game-map-atom) "A" :mode :awake)
    (test-utils/set-test-state! :cells-needing-attention [[0 0]])
    (input/handle-key :s)
    (should= :sentry (:mode (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))))

  (it "handle-key with 's' does not put unit in sentry when in city"
    (set-test-world! (assoc-in (build-test-map ["O"])
                                     [0 0 :contents]
                                     {:type :army :owner :player :mode :awake}))
    (test-utils/set-test-state! :cells-needing-attention [[0 0]])
    (input/handle-key :s)
    (should= :awake (:mode (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))))

  (it "sentry units do not move"
    (set-test-world! (build-test-map ["A#"]))
    (set-test-unit (test-utils/game-map-atom) "A" :mode :sentry)
    (should= nil (game-loop/move-current-unit [0 0]))
    (should= :sentry (:mode (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))))

  (it "consume-sentry-fighter-fuel decrements fuel each round"
    (set-test-world! (build-test-map ["F"]))
    (set-test-unit (test-utils/game-map-atom) "F" :mode :sentry :fuel 20)
    (game-loop/consume-sentry-fighter-fuel)
    (should= 19 (:fuel (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))))

  (it "consume-sentry-fighter-fuel wakes fighter with bingo warning when city in range"
    (set-test-world! (build-test-map ["FO"]))
    (set-test-unit (test-utils/game-map-atom) "F" :mode :sentry :fuel 9)
    (game-loop/consume-sentry-fighter-fuel)
    (let [fighter (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))]
      (should= 8 (:fuel fighter))
      (should= :awake (:mode fighter))
      (should= :fighter-bingo (:reason fighter))))

  (it "consume-sentry-fighter-fuel wakes fighter with out-of-fuel warning"
    (set-test-world! (build-test-map ["F"]))
    (set-test-unit (test-utils/game-map-atom) "F" :mode :sentry :fuel 2)
    (game-loop/consume-sentry-fighter-fuel)
    (let [fighter (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))]
      (should= 1 (:fuel fighter))
      (should= :awake (:mode fighter))
      (should= :fighter-out-of-fuel (:reason fighter))))

  (it "consume-sentry-fighter-fuel kills fighter when fuel hits zero"
    (set-test-world! (build-test-map ["F"]))
    (set-test-unit (test-utils/game-map-atom) "F" :mode :sentry :fuel 1)
    (game-loop/consume-sentry-fighter-fuel)
    (should= 0 (:hits (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))))))

(describe "explore mode"
  (before (reset-all-atoms!))
  (it "handle-key with 'l' puts army in explore mode"
    (set-test-world! (build-test-map ["A"]))
    (set-test-unit (test-utils/game-map-atom) "A" :mode :awake)
    (test-utils/set-test-state! :cells-needing-attention [[0 0]])
    (input/handle-key :l)
    (let [unit (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))]
      (should= :explore (:mode unit))
      (should= config/explore-steps (:explore-steps unit))))

  (it "handle-key with 'x' moves non-army units south"
    (set-test-world! (build-test-map ["F#"]))
    (set-test-unit (test-utils/game-map-atom) "F" :mode :awake :fuel 20)
    (test-utils/set-test-state! :cells-needing-attention [[0 0]])
    (input/handle-key :x)
    (should= :moving (:mode (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))))

  (it "explore army moves to valid adjacent cell"
    (set-test-world! (build-test-map ["A#"
                                             "##"]))
    (set-test-unit (test-utils/game-map-atom) "A" :mode :explore :explore-steps 50)
    (set-test-player-map! (make-initial-test-map 2 2 nil))
    (let [result (explore/move-explore-unit [0 0])]
      ;; Returns nil (one step per round)
      (should= nil result)
      ;; Original cell should be empty
      (should= nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))
      ;; Unit should be at some new position with decremented steps
      (let [moved-unit (some #(:contents (get-in (test-utils/read-test-state :game-map) %))
                             [[1 0] [0 1] [1 1]])]
        (should= :army (:type moved-unit))
        (should= :explore (:mode moved-unit))
        (should= 49 (:explore-steps moved-unit)))))

  (it "explore army avoids cells with units"
    (set-test-world! (build-test-map ["Aa"
                                             "a#"]))
    (set-test-unit (test-utils/game-map-atom) "A" :mode :explore :explore-steps 50)
    (set-test-player-map! (make-initial-test-map 2 2 nil))
    (explore/move-explore-unit [0 0])
    ;; Should move to [1 1] - the only valid cell
    (should-not-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [1 1]))))

  (it "explore army avoids cities"
    (set-test-world! (build-test-map ["A+"
                                             "O#"]))
    (set-test-unit (test-utils/game-map-atom) "A" :mode :explore :explore-steps 50)
    (set-test-player-map! (make-initial-test-map 2 2 nil))
    (explore/move-explore-unit [0 0])
    ;; Should move to [1 1] - the only valid land cell
    (should-not-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [1 1]))))

  (it "explore army wakes up after 50 steps"
    (set-test-world! (build-test-map ["A#"]))
    (set-test-unit (test-utils/game-map-atom) "A" :mode :explore :explore-steps 1)
    (set-test-player-map! (make-initial-test-map 1 2 nil))
    (let [result (explore/move-explore-unit [0 0])]
      ;; Should return nil (done exploring)
      (should= nil result)
      ;; Unit should be awake at original position
      (let [unit (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))]
        (should= :awake (:mode unit))
        (should= nil (:explore-steps unit)))))

  (it "explore army wakes up when stuck"
    (set-test-world! (build-test-map ["A~"
                                             "~~"]))
    (set-test-unit (test-utils/game-map-atom) "A" :mode :explore :explore-steps 50)
    (set-test-player-map! (make-initial-test-map 2 2 nil))
    (let [result (explore/move-explore-unit [0 0])]
      ;; Should return nil (stuck)
      (should= nil result)
      ;; Unit should be awake
      (should= :awake (:mode (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))))))

  (it "explore army prefers coastal moves when on coast"
    (let [initial-map (build-test-map ["~A#"
                                        "~##"
                                        "###"])]
      (set-test-world! initial-map)
      (set-test-unit (test-utils/game-map-atom) "A" :mode :explore :explore-steps 50)
      ;; Make player-map fully explored so unexplored preference doesn't interfere
      (set-test-player-map! (test-utils/read-test-state :game-map))
      ;; Run multiple times to check it stays on coast
      (dotimes [_ 10]
        (set-test-world! initial-map)
        (set-test-unit (test-utils/game-map-atom) "A" :mode :explore :explore-steps 50)
        (explore/move-explore-unit [1 0])
        ;; Find where the unit moved
        (let [{:keys [pos]} (get-test-unit (test-utils/game-map-atom) "A")]
          ;; Should move to a coastal cell (adjacent to sea)
          (should (map-utils/adjacent-to-sea? pos (test-utils/game-map-atom)))))))

  (it "explore army prefers moves towards unexplored cells"
    (let [initial-map (build-test-map ["#A#"
                                        "###"
                                        "###"])
          ;; Player map with only rows 0-1 explored - row 2 is unexplored
          player-map [[{:type :land} {:type :land} nil]
                      [{:type :land} {:type :land} nil]
                      [{:type :land} {:type :land} nil]]]
      ;; Run multiple times - should always move towards unexplored (into row 1)
      (dotimes [_ 10]
        (set-test-world! initial-map)
        (set-test-unit (test-utils/game-map-atom) "A" :mode :explore :explore-steps 50)
        (set-test-player-map! player-map)
        (explore/move-explore-unit [1 0])
        ;; Find where the unit moved
        (let [{:keys [pos]} (get-test-unit (test-utils/game-map-atom) "A")]
          ;; Should move to row 1 (adjacent to unexplored row 2)
          (should= 1 (second pos))))))

  (it "explore army does not retrace steps"
    (let [initial-map (build-test-map ["#A#"])]
      (set-test-world! initial-map)
      (set-test-unit (test-utils/game-map-atom) "A" :mode :explore :explore-steps 50 :visited #{[0 0]})
      (set-test-player-map! (test-utils/read-test-state :game-map))
      ;; Run multiple times - should never go back to [0 0]
      (dotimes [_ 10]
        (set-test-world! initial-map)
        (set-test-unit (test-utils/game-map-atom) "A" :mode :explore :explore-steps 50 :visited #{[0 0]})
        (explore/move-explore-unit [1 0])
        ;; Should move to [2 0], not back to [0 0]
        (should-not-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [2 0]))))))

  (it "explore army wakes up when finding enemy city"
    (set-test-world! (build-test-map ["A#X"]))
    (set-test-unit (test-utils/game-map-atom) "A" :mode :explore :explore-steps 50)
    (set-test-player-map! (test-utils/read-test-state :game-map))
    (explore/move-explore-unit [0 0])
    ;; Army should have moved to [1 0] and woken up
    (let [unit (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))]
      (should= :awake (:mode unit))
      (should= :army-found-city (:reason unit))
      (should= nil (:explore-steps unit))))

  (it "explore army wakes up when finding free city"
    (set-test-world! (build-test-map ["A#+"]))
    (set-test-unit (test-utils/game-map-atom) "A" :mode :explore :explore-steps 50)
    (set-test-player-map! (test-utils/read-test-state :game-map))
    (explore/move-explore-unit [0 0])
    ;; Army should have moved to [1 0] and woken up
    (let [unit (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))]
      (should= :awake (:mode unit))
      (should= :army-found-city (:reason unit)))))

(describe "calculate-extended-target"
  (before (reset-all-atoms!))
  (it "calculates target at map edge going east"
    (set-test-world! (build-test-map ["#####"
                                             "#####"
                                             "#####"
                                             "#####"
                                             "#####"]))
    (should= [4 0] (#'input/calculate-extended-target [0 0] [1 0])))

  (it "calculates target at map edge going south"
    (set-test-world! (build-test-map ["#####"
                                             "#####"
                                             "#####"
                                             "#####"
                                             "#####"]))
    (should= [0 4] (#'input/calculate-extended-target [0 0] [0 1])))

  (it "calculates target at map edge going southeast"
    (set-test-world! (build-test-map ["#####"
                                             "#####"
                                             "#####"
                                             "#####"
                                             "#####"]))
    (should= [4 4] (#'input/calculate-extended-target [0 0] [1 1])))

  (it "calculates target at map edge going west"
    (set-test-world! (build-test-map ["#####"
                                             "#####"
                                             "#####"
                                             "#####"
                                             "#####"]))
    (should= [0 2] (#'input/calculate-extended-target [4 2] [-1 0])))

  (it "calculates target at map edge going north"
    (set-test-world! (build-test-map ["#####"
                                             "#####"
                                             "#####"
                                             "#####"
                                             "#####"]))
    (should= [2 0] (#'input/calculate-extended-target [2 4] [0 -1])))

  (it "returns starting position when already at edge"
    (set-test-world! (build-test-map ["#####"
                                             "#####"
                                             "#####"
                                             "#####"
                                             "#####"]))
    (should= [0 0] (#'input/calculate-extended-target [0 0] [-1 0])))

  (it "works with non-square maps"
    (set-test-world! (build-test-map ["###"
                                             "###"
                                             "###"
                                             "###"
                                             "###"
                                             "###"
                                             "###"
                                             "###"
                                             "###"
                                             "###"]))
    (should= [2 1] (#'input/calculate-extended-target [0 1] [1 0]))))
