(ns empire.map-spec
  (:require [speclj.core :refer :all]
            [empire.attention :as attention]
            [empire.atoms :as atoms]
            [empire.combat :as combat]
            [empire.config :as config]
            [empire.game-loop :as game-loop]
            [empire.input :as input]
            [empire.movement.movement :as movement]))

(describe "build-player-items"
  (it "returns coordinates of player cities"
    (reset! atoms/game-map [[{:type :city :city-status :player}
                             {:type :city :city-status :computer}]])
    (should= [[0 0]] (vec (game-loop/build-player-items))))

  (it "returns coordinates of player units"
    (reset! atoms/game-map [[{:type :land :contents {:type :army :owner :player}}
                             {:type :land :contents {:type :army :owner :computer}}]])
    (should= [[0 0]] (vec (game-loop/build-player-items))))

  (it "returns both cities and units"
    (reset! atoms/game-map [[{:type :city :city-status :player}
                             {:type :land :contents {:type :army :owner :player}}]
                            [{:type :land}
                             {:type :city :city-status :computer}]])
    (should= [[0 0] [0 1]] (vec (game-loop/build-player-items))))

  (it "returns empty list when no player items"
    (reset! atoms/game-map [[{:type :sea} {:type :land}]])
    (should= [] (vec (game-loop/build-player-items)))))

(describe "item-processed"
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
  (it "wakes all fighters in player city airports"
    (reset! atoms/game-map [[{:type :city :city-status :player :fighter-count 3 :awake-fighters 0}]])
    (game-loop/wake-airport-fighters)
    (should= 3 (:awake-fighters (get-in @atoms/game-map [0 0]))))

  (it "ignores computer cities"
    (reset! atoms/game-map [[{:type :city :city-status :computer :fighter-count 3 :awake-fighters 0}]])
    (game-loop/wake-airport-fighters)
    (should= 0 (:awake-fighters (get-in @atoms/game-map [0 0]))))

  (it "ignores cities with no fighters"
    (reset! atoms/game-map [[{:type :city :city-status :player :fighter-count 0}]])
    (game-loop/wake-airport-fighters)
    (should= nil (:awake-fighters (get-in @atoms/game-map [0 0])))))

(describe "cells-needing-attention"
  (it "returns empty list when no player cells"
    (reset! atoms/player-map [[{:type :sea }
                               {:type :land }]
                              [{:type :city :city-status :computer :contents nil}
                               {:type :land }]])
    (reset! atoms/production {})
    (should= [] (attention/cells-needing-attention)))

  (it "returns coordinates of awake units"
    (reset! atoms/player-map [[{:type :land :contents {:type :army :mode :awake :owner :player}}
                               {:type :land }]
                              [{:type :land }
                               {:type :land }]])
    (reset! atoms/production {})
    (should= [[0 0]] (attention/cells-needing-attention)))

  (it "returns coordinates of cities with no production"
    (reset! atoms/player-map [[{:type :land }
                               {:type :city :city-status :player :contents nil}]
                              [{:type :land }
                               {:type :land }]])
    (reset! atoms/production {})
    (should= [[0 1]] (attention/cells-needing-attention)))

  (it "excludes cities with production"
    (reset! atoms/player-map [[{:type :city :city-status :player :contents nil}
                               {:type :land }]])
    (reset! atoms/production {[0 0] {:item :army :remaining-rounds 5}})
    (should= [] (attention/cells-needing-attention)))

  (it "returns multiple coordinates"
    (reset! atoms/player-map [[{:type :land :contents {:type :army :mode :awake :owner :player}}
                               {:type :city :city-status :player :contents nil}]
                              [{:type :land }
                               {:type :land }]])
    (reset! atoms/production {})
    (should= [[0 0] [0 1]] (attention/cells-needing-attention))))

(describe "remove-dead-units"
  (it "removes units with hits at or below zero"
    (let [initial-map [[{:type :land :contents {:type :army :hits 0 :owner :player}}
                        {:type :land :contents {:type :fighter :hits 1 :owner :player}}
                        {:type :land :contents {:type :army :hits -1 :owner :player}}]
                       [{:type :land }
                        {:type :land }
                        {:type :land }]]]
      (reset! atoms/game-map initial-map)
      (game-loop/remove-dead-units)
      (should= {:type :land} (get-in @atoms/game-map [0 0]))
      (should= {:type :land :contents {:type :fighter :hits 1 :owner :player}} (get-in @atoms/game-map [0 1]))
      (should= {:type :land} (get-in @atoms/game-map [0 2])))))

(describe "reset-steps-remaining"
  (it "initializes steps-remaining for player units based on unit speed"
    (let [initial-map [[{:type :land :contents {:type :army :owner :player}}
                        {:type :land :contents {:type :fighter :owner :player}}]
                       [{:type :land :contents {:type :army :owner :computer}}
                        {:type :land}]]]
      (reset! atoms/game-map initial-map)
      (game-loop/reset-steps-remaining)
      (should= (config/unit-speed :army) (:steps-remaining (:contents (get-in @atoms/game-map [0 0]))))
      (should= (config/unit-speed :fighter) (:steps-remaining (:contents (get-in @atoms/game-map [0 1]))))
      (should= nil (:steps-remaining (:contents (get-in @atoms/game-map [1 0]))))))

  (it "overwrites existing steps-remaining values"
    (let [initial-map [[{:type :land :contents {:type :fighter :owner :player :steps-remaining 2}}]]]
      (reset! atoms/game-map initial-map)
      (game-loop/reset-steps-remaining)
      (should= (config/unit-speed :fighter) (:steps-remaining (:contents (get-in @atoms/game-map [0 0])))))))

(describe "set-unit-movement"
  (it "preserves existing steps-remaining when setting movement"
    (let [initial-map [[{:type :land :contents {:type :fighter :owner :player :mode :awake :steps-remaining 3}}
                        {:type :land}]]]
      (reset! atoms/game-map initial-map)
      (movement/set-unit-movement [0 0] [0 1])
      (let [unit (:contents (get-in @atoms/game-map [0 0]))]
        (should= :moving (:mode unit))
        (should= [0 1] (:target unit))
        (should= 3 (:steps-remaining unit))))))

(describe "move-current-unit"
  (before-all
    (reset! atoms/player-map {}))

  (it "decrements steps-remaining after each move"
    (let [initial-map [[{:type :land :contents {:type :fighter :owner :player :mode :moving :target [0 2] :steps-remaining 3}}
                        {:type :land}
                        {:type :land}]]]
      (reset! atoms/game-map initial-map)
      (game-loop/move-current-unit [0 0])
      (should= 2 (:steps-remaining (:contents (get-in @atoms/game-map [0 1]))))))

  (it "returns new coords when steps remain"
    (let [initial-map [[{:type :land :contents {:type :fighter :owner :player :mode :moving :target [0 2] :steps-remaining 3}}
                        {:type :land}
                        {:type :land}]]]
      (reset! atoms/game-map initial-map)
      (should= [0 1] (game-loop/move-current-unit [0 0]))))

  (it "returns nil when steps-remaining reaches zero"
    (let [initial-map [[{:type :land :contents {:type :army :owner :player :mode :moving :target [0 2] :steps-remaining 1}}
                        {:type :land}
                        {:type :land}]]]
      (reset! atoms/game-map initial-map)
      (should= nil (game-loop/move-current-unit [0 0]))))

  (it "returns new coords when unit wakes up with steps remaining"
    (let [initial-map [[{:type :land :contents {:type :army :owner :player :mode :moving :target [0 1] :steps-remaining 3}}
                        {:type :land}]]]
      (reset! atoms/game-map initial-map)
      (let [result (game-loop/move-current-unit [0 0])]
        (should= [0 1] result)
        (should= :awake (:mode (:contents (get-in @atoms/game-map [0 1])))))))

  (it "returns nil when unit wakes up with no steps remaining"
    (let [initial-map [[{:type :land :contents {:type :army :owner :player :mode :moving :target [0 1] :steps-remaining 1}}
                        {:type :land}]]]
      (reset! atoms/game-map initial-map)
      (should= nil (game-loop/move-current-unit [0 0]))))

  (it "limits unit to its rate per round even with new orders"
    (let [initial-map [[{:type :land :contents {:type :army :owner :player :mode :moving :target [0 1] :steps-remaining 1}}
                        {:type :land}
                        {:type :land}]]]
      (reset! atoms/game-map initial-map)
      ;; Move once, using the last step
      (game-loop/move-current-unit [0 0])
      (should= 0 (:steps-remaining (:contents (get-in @atoms/game-map [0 1]))))
      ;; Give new orders
      (movement/set-unit-movement [0 1] [0 2])
      ;; steps-remaining should still be 0
      (should= 0 (:steps-remaining (:contents (get-in @atoms/game-map [0 1]))))
      ;; Try to move again - should return nil since no steps left
      (should= nil (game-loop/move-current-unit [0 1])))))

(describe "attempt-fighter-overfly"
  (it "shoots down fighter when flying over free city"
    (let [initial-map [[{:type :land :contents {:type :fighter :owner :player :mode :awake}}
                        {:type :city :city-status :free}]]]
      (reset! atoms/game-map initial-map)
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

  (it "shoots down fighter when flying over computer city"
    (let [initial-map [[{:type :land :contents {:type :fighter :owner :player :mode :awake}}
                        {:type :city :city-status :computer}]]]
      (reset! atoms/game-map initial-map)
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
      (should= (:fighter-destroyed-by-city config/messages) @atoms/line3-message))))

(describe "sentry mode"
  (it "handle-key with 's' puts unit in sentry mode"
    (let [initial-map [[{:type :land :contents {:type :army :owner :player :mode :awake}}]]]
      (reset! atoms/game-map initial-map)
      (reset! atoms/cells-needing-attention [[0 0]])
      (input/handle-key :s)
      (should= :sentry (:mode (:contents (get-in @atoms/game-map [0 0]))))))

  (it "handle-key with 's' does not put unit in sentry when in city"
    (let [initial-map [[{:type :city :city-status :player :contents {:type :army :owner :player :mode :awake}}]]]
      (reset! atoms/game-map initial-map)
      (reset! atoms/cells-needing-attention [[0 0]])
      (input/handle-key :s)
      (should= :awake (:mode (:contents (get-in @atoms/game-map [0 0]))))))

  (it "sentry units do not move"
    (let [initial-map [[{:type :land :contents {:type :army :owner :player :mode :sentry}}
                        {:type :land}]]]
      (reset! atoms/game-map initial-map)
      (should= nil (game-loop/move-current-unit [0 0]))
      (should= :sentry (:mode (:contents (get-in @atoms/game-map [0 0]))))))

  (it "consume-sentry-fighter-fuel decrements fuel each round"
    (let [initial-map [[{:type :land :contents {:type :fighter :owner :player :mode :sentry :fuel 20}}]]]
      (reset! atoms/game-map initial-map)
      (game-loop/consume-sentry-fighter-fuel)
      (should= 19 (:fuel (:contents (get-in @atoms/game-map [0 0]))))))

  (it "consume-sentry-fighter-fuel wakes fighter with bingo warning when city in range"
    (let [initial-map [[{:type :land :contents {:type :fighter :owner :player :mode :sentry :fuel 9}}
                        {:type :city :city-status :player}]]]
      (reset! atoms/game-map initial-map)
      (game-loop/consume-sentry-fighter-fuel)
      (let [fighter (:contents (get-in @atoms/game-map [0 0]))]
        (should= 8 (:fuel fighter))
        (should= :awake (:mode fighter))
        (should= :fighter-bingo (:reason fighter)))))

  (it "consume-sentry-fighter-fuel wakes fighter with out-of-fuel warning"
    (let [initial-map [[{:type :land :contents {:type :fighter :owner :player :mode :sentry :fuel 2}}]]]
      (reset! atoms/game-map initial-map)
      (game-loop/consume-sentry-fighter-fuel)
      (let [fighter (:contents (get-in @atoms/game-map [0 0]))]
        (should= 1 (:fuel fighter))
        (should= :awake (:mode fighter))
        (should= :fighter-out-of-fuel (:reason fighter)))))

  (it "consume-sentry-fighter-fuel kills fighter when fuel hits zero"
    (let [initial-map [[{:type :land :contents {:type :fighter :owner :player :mode :sentry :fuel 1}}]]]
      (reset! atoms/game-map initial-map)
      (game-loop/consume-sentry-fighter-fuel)
      (should= 0 (:hits (:contents (get-in @atoms/game-map [0 0])))))))

(describe "explore mode"
  (it "handle-key with 'l' puts army in explore mode"
    (let [initial-map [[{:type :land :contents {:type :army :owner :player :mode :awake}}]]]
      (reset! atoms/game-map initial-map)
      (reset! atoms/cells-needing-attention [[0 0]])
      (input/handle-key :l)
      (let [unit (:contents (get-in @atoms/game-map [0 0]))]
        (should= :explore (:mode unit))
        (should= config/explore-steps (:explore-steps unit)))))

  (it "handle-key with 'x' moves non-army units south"
    (let [initial-map [[{:type :land :contents {:type :fighter :owner :player :mode :awake :fuel 20}}
                        {:type :land}]]]
      (reset! atoms/game-map initial-map)
      (reset! atoms/cells-needing-attention [[0 0]])
      (input/handle-key :x)
      (should= :moving (:mode (:contents (get-in @atoms/game-map [0 0]))))))

  (it "explore army moves to valid adjacent cell"
    (let [initial-map [[{:type :land :contents {:type :army :owner :player :mode :explore :explore-steps 50}}
                        {:type :land}]
                       [{:type :land}
                        {:type :land}]]]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 2 (vec (repeat 2 nil)))))
      (let [result (movement/move-explore-unit [0 0])]
        ;; Returns nil (one step per round)
        (should= nil result)
        ;; Original cell should be empty
        (should= nil (:contents (get-in @atoms/game-map [0 0])))
        ;; Unit should be at some new position with decremented steps
        (let [moved-unit (some #(:contents (get-in @atoms/game-map %))
                               [[0 1] [1 0] [1 1]])]
          (should= :army (:type moved-unit))
          (should= :explore (:mode moved-unit))
          (should= 49 (:explore-steps moved-unit))))))

  (it "explore army avoids cells with units"
    (let [initial-map [[{:type :land :contents {:type :army :owner :player :mode :explore :explore-steps 50}}
                        {:type :land :contents {:type :army :owner :computer}}]
                       [{:type :land :contents {:type :army :owner :computer}}
                        {:type :land}]]]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 2 (vec (repeat 2 nil)))))
      (movement/move-explore-unit [0 0])
      ;; Should move to [1 1] - the only valid cell
      (should-not-be-nil (:contents (get-in @atoms/game-map [1 1])))))

  (it "explore army avoids cities"
    (let [initial-map [[{:type :land :contents {:type :army :owner :player :mode :explore :explore-steps 50}}
                        {:type :city :city-status :free}]
                       [{:type :city :city-status :player}
                        {:type :land}]]]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 2 (vec (repeat 2 nil)))))
      (movement/move-explore-unit [0 0])
      ;; Should move to [1 1] - the only valid land cell
      (should-not-be-nil (:contents (get-in @atoms/game-map [1 1])))))

  (it "explore army wakes up after 50 steps"
    (let [initial-map [[{:type :land :contents {:type :army :owner :player :mode :explore :explore-steps 1}}
                        {:type :land}]]]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 1 (vec (repeat 2 nil)))))
      (let [result (movement/move-explore-unit [0 0])]
        ;; Should return nil (done exploring)
        (should= nil result)
        ;; Unit should be awake at original position
        (let [unit (:contents (get-in @atoms/game-map [0 0]))]
          (should= :awake (:mode unit))
          (should= nil (:explore-steps unit))))))

  (it "explore army wakes up when stuck"
    (let [initial-map [[{:type :land :contents {:type :army :owner :player :mode :explore :explore-steps 50}}
                        {:type :sea}]
                       [{:type :sea}
                        {:type :sea}]]]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 2 (vec (repeat 2 nil)))))
      (let [result (movement/move-explore-unit [0 0])]
        ;; Should return nil (stuck)
        (should= nil result)
        ;; Unit should be awake
        (should= :awake (:mode (:contents (get-in @atoms/game-map [0 0])))))))

  (it "explore army prefers coastal moves when on coast"
    (let [initial-map [[{:type :sea}
                        {:type :land :contents {:type :army :owner :player :mode :explore :explore-steps 50}}
                        {:type :land}]
                       [{:type :sea}
                        {:type :land}
                        {:type :land}]
                       [{:type :land}
                        {:type :land}
                        {:type :land}]]]
      ;; Make player-map fully explored so unexplored preference doesn't interfere
      (reset! atoms/player-map initial-map)
      ;; Run multiple times to check it stays on coast
      (dotimes [_ 10]
        (reset! atoms/game-map initial-map)
        (movement/move-explore-unit [0 1])
        ;; Find where the unit moved
        (let [new-pos (first (for [i (range 3) j (range 3)
                                   :when (:contents (get-in @atoms/game-map [i j]))]
                               [i j]))]
          ;; Should move to a coastal cell (adjacent to sea)
          (should (movement/adjacent-to-sea? new-pos atoms/game-map))))))

  (it "explore army prefers moves towards unexplored cells"
    (let [initial-map [[{:type :land}
                        {:type :land :contents {:type :army :owner :player :mode :explore :explore-steps 50}}
                        {:type :land}]
                       [{:type :land}
                        {:type :land}
                        {:type :land}]
                       [{:type :land}
                        {:type :land}
                        {:type :land}]]
          ;; Player map with only rows 0-1 explored - row 2 is unexplored
          player-map [[{:type :land} {:type :land} {:type :land}]
                      [{:type :land} {:type :land} {:type :land}]
                      [nil nil nil]]]
      ;; Run multiple times - should always move towards unexplored (into row 1)
      (dotimes [_ 10]
        (reset! atoms/game-map initial-map)
        (reset! atoms/player-map player-map)
        (movement/move-explore-unit [0 1])
        ;; Find where the unit moved
        (let [new-pos (first (for [i (range 3) j (range 3)
                                   :when (:contents (get-in @atoms/game-map [i j]))]
                               [i j]))]
          ;; Should move to row 1 (adjacent to unexplored row 2)
          (should= 1 (first new-pos))))))

  (it "explore army does not retrace steps"
    (let [initial-map [[{:type :land}
                        {:type :land :contents {:type :army :owner :player :mode :explore :explore-steps 50 :visited #{[0 0]}}}
                        {:type :land}]]]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map initial-map)
      ;; Run multiple times - should never go back to [0 0]
      (dotimes [_ 10]
        (reset! atoms/game-map initial-map)
        (movement/move-explore-unit [0 1])
        ;; Should move to [0 2], not back to [0 0]
        (should-not-be-nil (:contents (get-in @atoms/game-map [0 2]))))))

  (it "explore army wakes up when finding enemy city"
    (let [initial-map [[{:type :land :contents {:type :army :owner :player :mode :explore :explore-steps 50}}
                        {:type :land}
                        {:type :city :city-status :computer}]]]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map initial-map)
      (movement/move-explore-unit [0 0])
      ;; Army should have moved to [0 1] and woken up
      (let [unit (:contents (get-in @atoms/game-map [0 1]))]
        (should= :awake (:mode unit))
        (should= :army-found-city (:reason unit))
        (should= nil (:explore-steps unit)))))

  (it "explore army wakes up when finding free city"
    (let [initial-map [[{:type :land :contents {:type :army :owner :player :mode :explore :explore-steps 50}}
                        {:type :land}
                        {:type :city :city-status :free}]]]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map initial-map)
      (movement/move-explore-unit [0 0])
      ;; Army should have moved to [0 1] and woken up
      (let [unit (:contents (get-in @atoms/game-map [0 1]))]
        (should= :awake (:mode unit))
        (should= :army-found-city (:reason unit))))))

(describe "calculate-extended-target"
  (it "calculates target at map edge going east"
    (reset! atoms/game-map (vec (repeat 5 (vec (repeat 5 {:type :land})))))
    (should= [4 0] (#'input/calculate-extended-target [0 0] [1 0])))

  (it "calculates target at map edge going south"
    (reset! atoms/game-map (vec (repeat 5 (vec (repeat 5 {:type :land})))))
    (should= [0 4] (#'input/calculate-extended-target [0 0] [0 1])))

  (it "calculates target at map edge going southeast"
    (reset! atoms/game-map (vec (repeat 5 (vec (repeat 5 {:type :land})))))
    (should= [4 4] (#'input/calculate-extended-target [0 0] [1 1])))

  (it "calculates target at map edge going west"
    (reset! atoms/game-map (vec (repeat 5 (vec (repeat 5 {:type :land})))))
    (should= [0 2] (#'input/calculate-extended-target [4 2] [-1 0])))

  (it "calculates target at map edge going north"
    (reset! atoms/game-map (vec (repeat 5 (vec (repeat 5 {:type :land})))))
    (should= [2 0] (#'input/calculate-extended-target [2 4] [0 -1])))

  (it "returns starting position when already at edge"
    (reset! atoms/game-map (vec (repeat 5 (vec (repeat 5 {:type :land})))))
    (should= [0 0] (#'input/calculate-extended-target [0 0] [-1 0])))

  (it "works with non-square maps"
    (reset! atoms/game-map (vec (repeat 10 (vec (repeat 3 {:type :land})))))
    (should= [9 1] (#'input/calculate-extended-target [0 1] [1 0]))))