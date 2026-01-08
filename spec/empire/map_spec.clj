(ns empire.map-spec
  (:require [speclj.core :refer :all]
            [empire.map :as map]
            [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.movement :as movement]))

(describe "cells-needing-attention"
  (it "returns empty list when no player cells"
    (reset! atoms/player-map [[{:type :sea }
                               {:type :land }]
                              [{:type :city :city-status :computer :contents nil}
                               {:type :land }]])
    (reset! atoms/production {})
    (should= [] (map/cells-needing-attention)))

  (it "returns coordinates of awake units"
    (reset! atoms/player-map [[{:type :land :contents {:type :army :mode :awake :owner :player}}
                               {:type :land }]
                              [{:type :land }
                               {:type :land }]])
    (reset! atoms/production {})
    (should= [[0 0]] (map/cells-needing-attention)))

  (it "returns coordinates of cities with no production"
    (reset! atoms/player-map [[{:type :land }
                               {:type :city :city-status :player :contents nil}]
                              [{:type :land }
                               {:type :land }]])
    (reset! atoms/production {})
    (should= [[0 1]] (map/cells-needing-attention)))

  (it "excludes cities with production"
    (reset! atoms/player-map [[{:type :city :city-status :player :contents nil}
                               {:type :land }]])
    (reset! atoms/production {[0 0] {:item :army :remaining-rounds 5}})
    (should= [] (map/cells-needing-attention)))

  (it "returns multiple coordinates"
    (reset! atoms/player-map [[{:type :land :contents {:type :army :mode :awake :owner :player}}
                               {:type :city :city-status :player :contents nil}]
                              [{:type :land }
                               {:type :land }]])
    (reset! atoms/production {})
    (should= [[0 0] [0 1]] (map/cells-needing-attention))))

(describe "remove-dead-units"
  (it "removes units with hits at or below zero"
    (let [initial-map [[{:type :land :contents {:type :army :hits 0 :owner :player}}
                        {:type :land :contents {:type :fighter :hits 1 :owner :player}}
                        {:type :land :contents {:type :army :hits -1 :owner :player}}]
                       [{:type :land }
                        {:type :land }
                        {:type :land }]]]
      (reset! atoms/game-map initial-map)
      (map/remove-dead-units)
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
      (map/reset-steps-remaining)
      (should= (config/unit-speed :army) (:steps-remaining (:contents (get-in @atoms/game-map [0 0]))))
      (should= (config/unit-speed :fighter) (:steps-remaining (:contents (get-in @atoms/game-map [0 1]))))
      (should= nil (:steps-remaining (:contents (get-in @atoms/game-map [1 0]))))))

  (it "overwrites existing steps-remaining values"
    (let [initial-map [[{:type :land :contents {:type :fighter :owner :player :steps-remaining 2}}]]]
      (reset! atoms/game-map initial-map)
      (map/reset-steps-remaining)
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
      (map/move-current-unit [0 0])
      (should= 2 (:steps-remaining (:contents (get-in @atoms/game-map [0 1]))))))

  (it "returns new coords when steps remain"
    (let [initial-map [[{:type :land :contents {:type :fighter :owner :player :mode :moving :target [0 2] :steps-remaining 3}}
                        {:type :land}
                        {:type :land}]]]
      (reset! atoms/game-map initial-map)
      (should= [0 1] (map/move-current-unit [0 0]))))

  (it "returns nil when steps-remaining reaches zero"
    (let [initial-map [[{:type :land :contents {:type :army :owner :player :mode :moving :target [0 2] :steps-remaining 1}}
                        {:type :land}
                        {:type :land}]]]
      (reset! atoms/game-map initial-map)
      (should= nil (map/move-current-unit [0 0]))))

  (it "returns new coords when unit wakes up with steps remaining"
    (let [initial-map [[{:type :land :contents {:type :army :owner :player :mode :moving :target [0 1] :steps-remaining 3}}
                        {:type :land}]]]
      (reset! atoms/game-map initial-map)
      (let [result (map/move-current-unit [0 0])]
        (should= [0 1] result)
        (should= :awake (:mode (:contents (get-in @atoms/game-map [0 1])))))))

  (it "returns nil when unit wakes up with no steps remaining"
    (let [initial-map [[{:type :land :contents {:type :army :owner :player :mode :moving :target [0 1] :steps-remaining 1}}
                        {:type :land}]]]
      (reset! atoms/game-map initial-map)
      (should= nil (map/move-current-unit [0 0]))))

  (it "limits unit to its rate per round even with new orders"
    (let [initial-map [[{:type :land :contents {:type :army :owner :player :mode :moving :target [0 1] :steps-remaining 1}}
                        {:type :land}
                        {:type :land}]]]
      (reset! atoms/game-map initial-map)
      ;; Move once, using the last step
      (map/move-current-unit [0 0])
      (should= 0 (:steps-remaining (:contents (get-in @atoms/game-map [0 1]))))
      ;; Give new orders
      (movement/set-unit-movement [0 1] [0 2])
      ;; steps-remaining should still be 0
      (should= 0 (:steps-remaining (:contents (get-in @atoms/game-map [0 1]))))
      ;; Try to move again - should return nil since no steps left
      (should= nil (map/move-current-unit [0 1])))))

(describe "attempt-fighter-overfly"
  (it "shoots down fighter when flying over free city"
    (let [initial-map [[{:type :land :contents {:type :fighter :owner :player :mode :awake}}
                        {:type :city :city-status :free}]]]
      (reset! atoms/game-map initial-map)
      (reset! atoms/line3-message "")
      (map/attempt-fighter-overfly [0 0] [0 1])
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
      (map/attempt-fighter-overfly [0 0] [0 1])
      ;; Fighter should be removed from original cell
      (should= nil (:contents (get-in @atoms/game-map [0 0])))
      ;; Fighter should be on city with hits=0
      (let [fighter (:contents (get-in @atoms/game-map [0 1]))]
        (should= 0 (:hits fighter))
        (should= 0 (:steps-remaining fighter))
        (should= :awake (:mode fighter))
        (should= :fighter-shot-down (:reason fighter)))
      (should= (:fighter-destroyed-by-city config/messages) @atoms/line3-message))))