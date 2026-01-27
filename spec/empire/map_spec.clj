(ns empire.map-spec
  (:require [speclj.core :refer :all]
            [empire.player.attention :as attention]
            [empire.atoms :as atoms]
            [empire.player.combat :as combat]
            [empire.config :as config]
            [empire.game-loop :as game-loop]
            [empire.ui.input :as input]
            [empire.ui.input-movement :as input-move]
            [empire.movement.explore :as explore]
            [empire.movement.map-utils :as map-utils]
            [empire.movement.movement :as movement]
            [empire.test-utils :refer [build-test-map set-test-unit get-test-unit reset-all-atoms! make-initial-test-map]]))

(describe "build-player-items"
  (before (reset-all-atoms!))
  (it "returns coordinates of player cities"
    (reset! atoms/game-map (build-test-map ["OX"]))
    (should= [[0 0]] (vec (game-loop/build-player-items))))

  (it "returns coordinates of player units"
    (reset! atoms/game-map (build-test-map ["Aa"]))
    (should= [[0 0]] (vec (game-loop/build-player-items))))

  (it "returns both cities and units"
    (reset! atoms/game-map (build-test-map ["OA"
                                             "#X"]))
    (should= [[0 0] [0 1]] (vec (game-loop/build-player-items))))

  (it "returns empty list when no player items"
    (reset! atoms/game-map (build-test-map ["~#"]))
    (should= [] (vec (game-loop/build-player-items)))))

(describe "item-processed"
  (before (reset-all-atoms!))
  (it "resets waiting-for-input to false"
    (reset! atoms/waiting-for-input true)
    (game-loop/item-processed)
    (should= false @atoms/waiting-for-input))

  (it "clears message"
    (reset! atoms/message "Some message")
    (game-loop/item-processed)
    (should= "" @atoms/message))

  (it "clears cells-needing-attention"
    (reset! atoms/cells-needing-attention [[0 0] [1 1]])
    (game-loop/item-processed)
    (should= [] @atoms/cells-needing-attention)))

(describe "wake-airport-fighters"
  (before (reset-all-atoms!))
  (it "wakes all fighters in player city airports"
    (reset! atoms/game-map (-> (build-test-map ["O"])
                               (assoc-in [0 0 :fighter-count] 3)
                               (assoc-in [0 0 :awake-fighters] 0)))
    (game-loop/wake-airport-fighters)
    (should= 3 (:awake-fighters (get-in @atoms/game-map [0 0]))))

  (it "ignores computer cities"
    (reset! atoms/game-map (-> (build-test-map ["X"])
                               (assoc-in [0 0 :fighter-count] 3)
                               (assoc-in [0 0 :awake-fighters] 0)))
    (game-loop/wake-airport-fighters)
    (should= 0 (:awake-fighters (get-in @atoms/game-map [0 0]))))

  (it "ignores cities with no fighters"
    (reset! atoms/game-map (assoc-in (build-test-map ["O"]) [0 0 :fighter-count] 0))
    (game-loop/wake-airport-fighters)
    (should= nil (:awake-fighters (get-in @atoms/game-map [0 0])))))

(describe "cells-needing-attention"
  (before (reset-all-atoms!))
  (it "returns empty list when no player cells"
    (reset! atoms/player-map (build-test-map ["~#"
                                               "X#"]))
    (reset! atoms/production {})
    (should= [] (attention/cells-needing-attention)))

  (it "returns coordinates of awake units"
    (reset! atoms/player-map (build-test-map ["A#"
                                               "##"]))
    (set-test-unit atoms/player-map "A" :mode :awake)
    (reset! atoms/production {})
    (should= [[0 0]] (attention/cells-needing-attention)))

  (it "returns coordinates of cities with no production"
    (reset! atoms/player-map (build-test-map ["#O"
                                               "##"]))
    (reset! atoms/production {})
    (should= [[0 1]] (attention/cells-needing-attention)))

  (it "excludes cities with production"
    (reset! atoms/player-map (build-test-map ["O#"]))
    (reset! atoms/production {[0 0] {:item :army :remaining-rounds 5}})
    (should= [] (attention/cells-needing-attention)))

  (it "returns multiple coordinates"
    (reset! atoms/player-map (build-test-map ["AO"
                                               "##"]))
    (set-test-unit atoms/player-map "A" :mode :awake)
    (reset! atoms/production {})
    (should= [[0 0] [0 1]] (attention/cells-needing-attention))))

(describe "remove-dead-units"
  (before (reset-all-atoms!))
  (it "removes units with hits at or below zero"
    (reset! atoms/game-map (build-test-map ["AFA"
                                             "###"]))
    (set-test-unit atoms/game-map "A" :hits 0)
    (set-test-unit atoms/game-map "F" :hits 1)
    (set-test-unit atoms/game-map "A2" :hits -1)
    (game-loop/remove-dead-units)
    (should= {:type :land} (get-in @atoms/game-map [0 0]))
    (should= {:type :land :contents {:type :fighter :hits 1 :owner :player}} (get-in @atoms/game-map [0 1]))
    (should= {:type :land} (get-in @atoms/game-map [0 2]))))

(describe "reset-steps-remaining"
  (before (reset-all-atoms!))
  (it "initializes steps-remaining for player units based on unit speed"
    (reset! atoms/game-map (build-test-map ["AF"
                                             "a#"]))
    (game-loop/reset-steps-remaining)
    (should= (config/unit-speed :army) (:steps-remaining (:contents (get-in @atoms/game-map [0 0]))))
    (should= (config/unit-speed :fighter) (:steps-remaining (:contents (get-in @atoms/game-map [0 1]))))
    (should= nil (:steps-remaining (:contents (get-in @atoms/game-map [1 0])))))

  (it "overwrites existing steps-remaining values"
    (reset! atoms/game-map (build-test-map ["F"]))
    (set-test-unit atoms/game-map "F" :steps-remaining 2)
    (game-loop/reset-steps-remaining)
    (should= (config/unit-speed :fighter) (:steps-remaining (:contents (get-in @atoms/game-map [0 0]))))))

(describe "set-unit-movement"
  (before (reset-all-atoms!))
  (it "preserves existing steps-remaining when setting movement"
    (reset! atoms/game-map (build-test-map ["F#"]))
    (set-test-unit atoms/game-map "F" :mode :awake :steps-remaining 3)
    (movement/set-unit-movement [0 0] [0 1])
    (let [unit (:contents (get-in @atoms/game-map [0 0]))]
      (should= :moving (:mode unit))
      (should= [0 1] (:target unit))
      (should= 3 (:steps-remaining unit)))))

(describe "move-current-unit"
  (before (reset-all-atoms!))
  (before-all
    (reset! atoms/player-map {}))

  (it "decrements steps-remaining after each move"
    (reset! atoms/game-map (build-test-map ["F##"]))
    (set-test-unit atoms/game-map "F" :mode :moving :target [0 2] :steps-remaining 3)
    (game-loop/move-current-unit [0 0])
    (should= 2 (:steps-remaining (:contents (get-in @atoms/game-map [0 1])))))

  (it "returns new coords when steps remain"
    (reset! atoms/game-map (build-test-map ["F##"]))
    (set-test-unit atoms/game-map "F" :mode :moving :target [0 2] :steps-remaining 3)
    (should= [0 1] (game-loop/move-current-unit [0 0])))

  (it "returns nil when steps-remaining reaches zero"
    (reset! atoms/game-map (build-test-map ["A##"]))
    (set-test-unit atoms/game-map "A" :mode :moving :target [0 2] :steps-remaining 1)
    (should= nil (game-loop/move-current-unit [0 0])))

  (it "returns new coords when unit wakes up with steps remaining"
    (reset! atoms/game-map (build-test-map ["A#"]))
    (set-test-unit atoms/game-map "A" :mode :moving :target [0 1] :steps-remaining 3)
    (let [result (game-loop/move-current-unit [0 0])]
      (should= [0 1] result)
      (should= :awake (:mode (:contents (get-in @atoms/game-map [0 1]))))))

  (it "returns nil when unit wakes up with no steps remaining"
    (reset! atoms/game-map (build-test-map ["A#"]))
    (set-test-unit atoms/game-map "A" :mode :moving :target [0 1] :steps-remaining 1)
    (should= nil (game-loop/move-current-unit [0 0])))

  (it "limits unit to its rate per round even with new orders"
    (reset! atoms/game-map (build-test-map ["A##"]))
    (set-test-unit atoms/game-map "A" :mode :moving :target [0 1] :steps-remaining 1)
    ;; Move once, using the last step
    (game-loop/move-current-unit [0 0])
    (should= 0 (:steps-remaining (:contents (get-in @atoms/game-map [0 1]))))
    ;; Give new orders
    (movement/set-unit-movement [0 1] [0 2])
    ;; steps-remaining should still be 0
    (should= 0 (:steps-remaining (:contents (get-in @atoms/game-map [0 1]))))
    ;; Try to move again - should return nil since no steps left
    (should= nil (game-loop/move-current-unit [0 1]))))

(describe "attempt-fighter-overfly"
  (before (reset-all-atoms!))
  (it "shoots down fighter when flying over free city"
    (reset! atoms/game-map (build-test-map ["F+"]))
    (set-test-unit atoms/game-map "F" :mode :awake)
    (reset! atoms/line3-message "")
    (combat/attempt-fighter-overfly [0 0] [0 1])
    ;; Fighter should be removed from original cell
    (should= nil (:contents (get-in @atoms/game-map [0 0])))
    ;; Fighter should be on city with hits=0
    (let [fighter (:contents (get-in @atoms/game-map [0 1]))]
      (should= 0 (:hits fighter))
      (should= 0 (:steps-remaining fighter))
      (should= :awake (:mode fighter))
      (should= :fighter-shot-down (:reason fighter)))
    (should= (:fighter-destroyed-by-city config/messages) @atoms/line3-message))

  (it "shoots down fighter when flying over computer city"
    (reset! atoms/game-map (build-test-map ["FX"]))
    (set-test-unit atoms/game-map "F" :mode :awake)
    (reset! atoms/line3-message "")
    (combat/attempt-fighter-overfly [0 0] [0 1])
    ;; Fighter should be removed from original cell
    (should= nil (:contents (get-in @atoms/game-map [0 0])))
    ;; Fighter should be on city with hits=0
    (let [fighter (:contents (get-in @atoms/game-map [0 1]))]
      (should= 0 (:hits fighter))
      (should= 0 (:steps-remaining fighter))
      (should= :awake (:mode fighter))
      (should= :fighter-shot-down (:reason fighter)))
    (should= (:fighter-destroyed-by-city config/messages) @atoms/line3-message)))

(describe "sentry mode"
  (before (reset-all-atoms!))
  (it "handle-key with 's' puts unit in sentry mode"
    (reset! atoms/game-map (build-test-map ["A"]))
    (set-test-unit atoms/game-map "A" :mode :awake)
    (reset! atoms/cells-needing-attention [[0 0]])
    (input/handle-key :s)
    (should= :sentry (:mode (:contents (get-in @atoms/game-map [0 0])))))

  (it "handle-key with 's' does not put unit in sentry when in city"
    (reset! atoms/game-map (assoc-in (build-test-map ["O"])
                                     [0 0 :contents]
                                     {:type :army :owner :player :mode :awake}))
    (reset! atoms/cells-needing-attention [[0 0]])
    (input/handle-key :s)
    (should= :awake (:mode (:contents (get-in @atoms/game-map [0 0])))))

  (it "sentry units do not move"
    (reset! atoms/game-map (build-test-map ["A#"]))
    (set-test-unit atoms/game-map "A" :mode :sentry)
    (should= nil (game-loop/move-current-unit [0 0]))
    (should= :sentry (:mode (:contents (get-in @atoms/game-map [0 0])))))

  (it "consume-sentry-fighter-fuel decrements fuel each round"
    (reset! atoms/game-map (build-test-map ["F"]))
    (set-test-unit atoms/game-map "F" :mode :sentry :fuel 20)
    (game-loop/consume-sentry-fighter-fuel)
    (should= 19 (:fuel (:contents (get-in @atoms/game-map [0 0])))))

  (it "consume-sentry-fighter-fuel wakes fighter with bingo warning when city in range"
    (reset! atoms/game-map (build-test-map ["FO"]))
    (set-test-unit atoms/game-map "F" :mode :sentry :fuel 9)
    (game-loop/consume-sentry-fighter-fuel)
    (let [fighter (:contents (get-in @atoms/game-map [0 0]))]
      (should= 8 (:fuel fighter))
      (should= :awake (:mode fighter))
      (should= :fighter-bingo (:reason fighter))))

  (it "consume-sentry-fighter-fuel wakes fighter with out-of-fuel warning"
    (reset! atoms/game-map (build-test-map ["F"]))
    (set-test-unit atoms/game-map "F" :mode :sentry :fuel 2)
    (game-loop/consume-sentry-fighter-fuel)
    (let [fighter (:contents (get-in @atoms/game-map [0 0]))]
      (should= 1 (:fuel fighter))
      (should= :awake (:mode fighter))
      (should= :fighter-out-of-fuel (:reason fighter))))

  (it "consume-sentry-fighter-fuel kills fighter when fuel hits zero"
    (reset! atoms/game-map (build-test-map ["F"]))
    (set-test-unit atoms/game-map "F" :mode :sentry :fuel 1)
    (game-loop/consume-sentry-fighter-fuel)
    (should= 0 (:hits (:contents (get-in @atoms/game-map [0 0]))))))

(describe "explore mode"
  (before (reset-all-atoms!))
  (it "handle-key with 'l' puts army in explore mode"
    (reset! atoms/game-map (build-test-map ["A"]))
    (set-test-unit atoms/game-map "A" :mode :awake)
    (reset! atoms/cells-needing-attention [[0 0]])
    (input/handle-key :l)
    (let [unit (:contents (get-in @atoms/game-map [0 0]))]
      (should= :explore (:mode unit))
      (should= config/explore-steps (:explore-steps unit))))

  (it "handle-key with 'x' moves non-army units south"
    (reset! atoms/game-map (build-test-map ["F#"]))
    (set-test-unit atoms/game-map "F" :mode :awake :fuel 20)
    (reset! atoms/cells-needing-attention [[0 0]])
    (input/handle-key :x)
    (should= :moving (:mode (:contents (get-in @atoms/game-map [0 0])))))

  (it "explore army moves to valid adjacent cell"
    (reset! atoms/game-map (build-test-map ["A#"
                                             "##"]))
    (set-test-unit atoms/game-map "A" :mode :explore :explore-steps 50)
    (reset! atoms/player-map (make-initial-test-map 2 2 nil))
    (let [result (explore/move-explore-unit [0 0])]
      ;; Returns nil (one step per round)
      (should= nil result)
      ;; Original cell should be empty
      (should= nil (:contents (get-in @atoms/game-map [0 0])))
      ;; Unit should be at some new position with decremented steps
      (let [moved-unit (some #(:contents (get-in @atoms/game-map %))
                             [[0 1] [1 0] [1 1]])]
        (should= :army (:type moved-unit))
        (should= :explore (:mode moved-unit))
        (should= 49 (:explore-steps moved-unit)))))

  (it "explore army avoids cells with units"
    (reset! atoms/game-map (build-test-map ["Aa"
                                             "a#"]))
    (set-test-unit atoms/game-map "A" :mode :explore :explore-steps 50)
    (reset! atoms/player-map (make-initial-test-map 2 2 nil))
    (explore/move-explore-unit [0 0])
    ;; Should move to [1 1] - the only valid cell
    (should-not-be-nil (:contents (get-in @atoms/game-map [1 1]))))

  (it "explore army avoids cities"
    (reset! atoms/game-map (build-test-map ["A+"
                                             "O#"]))
    (set-test-unit atoms/game-map "A" :mode :explore :explore-steps 50)
    (reset! atoms/player-map (make-initial-test-map 2 2 nil))
    (explore/move-explore-unit [0 0])
    ;; Should move to [1 1] - the only valid land cell
    (should-not-be-nil (:contents (get-in @atoms/game-map [1 1]))))

  (it "explore army wakes up after 50 steps"
    (reset! atoms/game-map (build-test-map ["A#"]))
    (set-test-unit atoms/game-map "A" :mode :explore :explore-steps 1)
    (reset! atoms/player-map (make-initial-test-map 1 2 nil))
    (let [result (explore/move-explore-unit [0 0])]
      ;; Should return nil (done exploring)
      (should= nil result)
      ;; Unit should be awake at original position
      (let [unit (:contents (get-in @atoms/game-map [0 0]))]
        (should= :awake (:mode unit))
        (should= nil (:explore-steps unit)))))

  (it "explore army wakes up when stuck"
    (reset! atoms/game-map (build-test-map ["A~"
                                             "~~"]))
    (set-test-unit atoms/game-map "A" :mode :explore :explore-steps 50)
    (reset! atoms/player-map (make-initial-test-map 2 2 nil))
    (let [result (explore/move-explore-unit [0 0])]
      ;; Should return nil (stuck)
      (should= nil result)
      ;; Unit should be awake
      (should= :awake (:mode (:contents (get-in @atoms/game-map [0 0]))))))

  (it "explore army prefers coastal moves when on coast"
    (let [initial-map (build-test-map ["~A#"
                                        "~##"
                                        "###"])]
      (reset! atoms/game-map initial-map)
      (set-test-unit atoms/game-map "A" :mode :explore :explore-steps 50)
      ;; Make player-map fully explored so unexplored preference doesn't interfere
      (reset! atoms/player-map @atoms/game-map)
      ;; Run multiple times to check it stays on coast
      (dotimes [_ 10]
        (reset! atoms/game-map initial-map)
        (set-test-unit atoms/game-map "A" :mode :explore :explore-steps 50)
        (explore/move-explore-unit [0 1])
        ;; Find where the unit moved
        (let [{:keys [pos]} (get-test-unit atoms/game-map "A")]
          ;; Should move to a coastal cell (adjacent to sea)
          (should (map-utils/adjacent-to-sea? pos atoms/game-map))))))

  (it "explore army prefers moves towards unexplored cells"
    (let [initial-map (build-test-map ["#A#"
                                        "###"
                                        "###"])
          ;; Player map with only rows 0-1 explored - row 2 is unexplored
          player-map [[{:type :land} {:type :land} {:type :land}]
                      [{:type :land} {:type :land} {:type :land}]
                      [nil nil nil]]]
      ;; Run multiple times - should always move towards unexplored (into row 1)
      (dotimes [_ 10]
        (reset! atoms/game-map initial-map)
        (set-test-unit atoms/game-map "A" :mode :explore :explore-steps 50)
        (reset! atoms/player-map player-map)
        (explore/move-explore-unit [0 1])
        ;; Find where the unit moved
        (let [{:keys [pos]} (get-test-unit atoms/game-map "A")]
          ;; Should move to row 1 (adjacent to unexplored row 2)
          (should= 1 (first pos))))))

  (it "explore army does not retrace steps"
    (let [initial-map (build-test-map ["#A#"])]
      (reset! atoms/game-map initial-map)
      (set-test-unit atoms/game-map "A" :mode :explore :explore-steps 50 :visited #{[0 0]})
      (reset! atoms/player-map @atoms/game-map)
      ;; Run multiple times - should never go back to [0 0]
      (dotimes [_ 10]
        (reset! atoms/game-map initial-map)
        (set-test-unit atoms/game-map "A" :mode :explore :explore-steps 50 :visited #{[0 0]})
        (explore/move-explore-unit [0 1])
        ;; Should move to [0 2], not back to [0 0]
        (should-not-be-nil (:contents (get-in @atoms/game-map [0 2]))))))

  (it "explore army wakes up when finding enemy city"
    (reset! atoms/game-map (build-test-map ["A#X"]))
    (set-test-unit atoms/game-map "A" :mode :explore :explore-steps 50)
    (reset! atoms/player-map @atoms/game-map)
    (explore/move-explore-unit [0 0])
    ;; Army should have moved to [0 1] and woken up
    (let [unit (:contents (get-in @atoms/game-map [0 1]))]
      (should= :awake (:mode unit))
      (should= :army-found-city (:reason unit))
      (should= nil (:explore-steps unit))))

  (it "explore army wakes up when finding free city"
    (reset! atoms/game-map (build-test-map ["A#+"]))
    (set-test-unit atoms/game-map "A" :mode :explore :explore-steps 50)
    (reset! atoms/player-map @atoms/game-map)
    (explore/move-explore-unit [0 0])
    ;; Army should have moved to [0 1] and woken up
    (let [unit (:contents (get-in @atoms/game-map [0 1]))]
      (should= :awake (:mode unit))
      (should= :army-found-city (:reason unit)))))

(describe "calculate-extended-target"
  (before (reset-all-atoms!))
  (it "calculates target at map edge going east"
    (reset! atoms/game-map (build-test-map ["#####"
                                             "#####"
                                             "#####"
                                             "#####"
                                             "#####"]))
    (should= [4 0] (#'input-move/calculate-extended-target [0 0] [1 0])))

  (it "calculates target at map edge going south"
    (reset! atoms/game-map (build-test-map ["#####"
                                             "#####"
                                             "#####"
                                             "#####"
                                             "#####"]))
    (should= [0 4] (#'input-move/calculate-extended-target [0 0] [0 1])))

  (it "calculates target at map edge going southeast"
    (reset! atoms/game-map (build-test-map ["#####"
                                             "#####"
                                             "#####"
                                             "#####"
                                             "#####"]))
    (should= [4 4] (#'input-move/calculate-extended-target [0 0] [1 1])))

  (it "calculates target at map edge going west"
    (reset! atoms/game-map (build-test-map ["#####"
                                             "#####"
                                             "#####"
                                             "#####"
                                             "#####"]))
    (should= [0 2] (#'input-move/calculate-extended-target [4 2] [-1 0])))

  (it "calculates target at map edge going north"
    (reset! atoms/game-map (build-test-map ["#####"
                                             "#####"
                                             "#####"
                                             "#####"
                                             "#####"]))
    (should= [2 0] (#'input-move/calculate-extended-target [2 4] [0 -1])))

  (it "returns starting position when already at edge"
    (reset! atoms/game-map (build-test-map ["#####"
                                             "#####"
                                             "#####"
                                             "#####"
                                             "#####"]))
    (should= [0 0] (#'input-move/calculate-extended-target [0 0] [-1 0])))

  (it "works with non-square maps"
    (reset! atoms/game-map (build-test-map ["###"
                                             "###"
                                             "###"
                                             "###"
                                             "###"
                                             "###"
                                             "###"
                                             "###"
                                             "###"
                                             "###"]))
    (should= [9 1] (#'input-move/calculate-extended-target [0 1] [1 0]))))