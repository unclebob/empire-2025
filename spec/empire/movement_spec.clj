(ns empire.movement-spec
  (:require
    [empire.atoms :as atoms]
    [empire.movement :refer :all]
    [speclj.core :refer :all]))

(describe "movement"
  (context "move-units"
    (it "does nothing if no unit"
      (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                            (assoc-in [4 4] {:type :land  :contents nil})
                            (assoc-in [4 5] {:type :land }))]
        (reset! atoms/game-map initial-map)
        (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
        (move-units)
        (should= 2 (count (filter (complement nil?) (flatten @atoms/game-map))))))

    (context "single moves that awaken the unit"
      (it "moves a unit to its target and sets mode to awake"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land 
                                               :contents {:type :army :mode :moving :owner :player :target [4 5]}})
                              (assoc-in [4 5] {:type :land }))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (move-units)
          (should= {:type :land  :contents nil} (get-in @atoms/game-map [4 4]))
          (should= {:type :land  :contents {:type :army :mode :awake :owner :player}} (get-in @atoms/game-map [4 5]))
          (should= 2 (count (filter (complement nil?) (flatten @atoms/game-map))))))

      (it "moves a unit up and sets mode to awake"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land 
                                               :contents {:type :army :mode :moving :owner :player :target [4 3]}})
                              (assoc-in [4 3] {:type :land }))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (move-units)
          (should= {:type :land  :contents nil} (get-in @atoms/game-map [4 4]))
          (should= {:type :land  :contents {:type :army :mode :awake :owner :player}} (get-in @atoms/game-map [4 3]))
          (should= 2 (count (filter (complement nil?) (flatten @atoms/game-map))))))

      (it "moves a unit left and sets mode to awake"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land 
                                               :contents {:type :army :mode :moving :owner :player :target [3 4]}})
                              (assoc-in [3 4] {:type :land }))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (move-units)
          (should= {:type :land  :contents nil} (get-in @atoms/game-map [4 4]))
          (should= {:type :land  :contents {:type :army :mode :awake :owner :player}} (get-in @atoms/game-map [3 4]))
          (should= 2 (count (filter (complement nil?) (flatten @atoms/game-map))))))

      (it "moves a unit right and sets mode to awake"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land 
                                               :contents {:type :army :mode :moving :owner :player :target [5 4]}})
                              (assoc-in [5 4] {:type :land }))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (move-units)
          (should= {:type :land  :contents nil} (get-in @atoms/game-map [4 4]))
          (should= {:type :land  :contents {:type :army :mode :awake :owner :player}} (get-in @atoms/game-map [5 4]))
          (should= 2 (count (filter (complement nil?) (flatten @atoms/game-map))))))

      (it "moves a unit up-left and sets mode to awake"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land 
                                               :contents {:type :army :mode :moving :owner :player :target [3 3]}})
                              (assoc-in [3 3] {:type :land }))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (move-units)
          (should= {:type :land  :contents nil} (get-in @atoms/game-map [4 4]))
          (should= {:type :land  :contents {:type :army :mode :awake :owner :player}} (get-in @atoms/game-map [3 3]))
          (should= 2 (count (filter (complement nil?) (flatten @atoms/game-map))))))

      (it "moves a unit up-right and sets mode to awake"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land 
                                               :contents {:type :army :mode :moving :owner :player :target [5 3]}})
                              (assoc-in [5 3] {:type :land }))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (move-units)
          (should= {:type :land  :contents nil} (get-in @atoms/game-map [4 4]))
          (should= {:type :land  :contents {:type :army :mode :awake :owner :player}} (get-in @atoms/game-map [5 3]))
          (should= 2 (count (filter (complement nil?) (flatten @atoms/game-map))))))

      (it "moves a unit down-left and sets mode to awake"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land 
                                               :contents {:type :army :mode :moving :owner :player :target [3 5]}})
                              (assoc-in [3 5] {:type :land }))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (move-units)
          (should= {:type :land  :contents nil} (get-in @atoms/game-map [4 4]))
          (should= {:type :land  :contents {:type :army :mode :awake :owner :player}} (get-in @atoms/game-map [3 5]))
          (should= 2 (count (filter (complement nil?) (flatten @atoms/game-map))))))

      (it "moves a unit down-right and sets mode to awake"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land 
                                               :contents {:type :army :mode :moving :owner :player :target [5 5]}})
                              (assoc-in [5 5] {:type :land }))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (move-units)
          (should= {:type :land  :contents nil} (get-in @atoms/game-map [4 4]))
          (should= {:type :land  :contents {:type :army :mode :awake :owner :player}} (get-in @atoms/game-map [5 5]))
          (should= 2 (count (filter (complement nil?) (flatten @atoms/game-map))))))

      (it "wakes up a unit if the next move would be into sea"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land 
                                               :contents {:type :army :mode :moving :owner :player :target [4 5]}})
                              (assoc-in [4 5] {:type :sea}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (move-units)
          (should= {:type :land :contents {:type :army :mode :awake :owner :player}} (get-in @atoms/game-map [4 4]))
          (should= {:type :sea} (get-in @atoms/game-map [4 5]))))

      (it "wakes up a unit when moving near an enemy city"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land 
                                               :contents {:type :army :mode :moving :owner :player :target [4 6]}})
                              (assoc-in [4 5] {:type :land})
                              (assoc-in [4 6] {:type :city :city-status :computer :contents nil}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (move-units)
          (should= {:type :land :contents nil} (get-in @atoms/game-map [4 4]))
          (should= {:type :land :contents {:type :army :mode :awake :owner :player}} (get-in @atoms/game-map [4 5]))
          (should= {:type :city :city-status :computer :contents nil} (get-in @atoms/game-map [4 6]))))
      )

    (context "visibility updates"
      (it "reveals cells near player-owned units"
        (let [game-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                           (assoc-in [4 4] {:type :land  :contents {:type :army :mode :awake :owner :player}})
                           (assoc-in [4 5] {:type :land })
                           (assoc-in [5 4] {:type :land }))]
          (reset! atoms/game-map game-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (update-combatant-map atoms/player-map :player)
          ;; Check that the unit's cell and neighbors are revealed
          (should= {:type :land  :contents {:type :army :mode :awake :owner :player}} (get-in @atoms/player-map [4 4]))
          (should= {:type :land } (get-in @atoms/player-map [4 5]))
          (should= {:type :land } (get-in @atoms/player-map [5 4]))
          (should= nil (get-in @atoms/player-map [3 4]))
          (should= nil (get-in @atoms/player-map [4 3]))
          (should= nil (get-in @atoms/player-map [5 5]))
          (should= nil (get-in @atoms/player-map [3 3]))
          (should= nil (get-in @atoms/player-map [5 3]))
          (should= nil (get-in @atoms/player-map [3 5]))
          ;; Check that distant cells are not revealed
          (should= nil (get-in @atoms/player-map [0 0]))
          (should= nil (get-in @atoms/player-map [8 8]))))
      )

    (context "multi-step moves take one step towards the target, keeping mode as moving"
      (it "moves a unit one step right towards target at radius 4"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land 
                                               :contents {:type :army :mode :moving :owner :player :target [8 4]}})
                              (assoc-in [5 4] {:type :land })
                              (assoc-in [8 4] {:type :land }))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (move-units)
          (should= {:type :land  :contents nil} (get-in @atoms/game-map [4 4]))
          (should= {:type :land  :contents {:type :army :mode :moving :owner :player :target [8 4]}} (get-in @atoms/game-map [5 4]))
          (should= {:type :land } (get-in @atoms/game-map [8 4]))
          (should= 3 (count (filter (complement nil?) (flatten @atoms/game-map))))))

      (it "moves a unit one step left towards target at radius 4"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land 
                                               :contents {:type :army :mode :moving :owner :player :target [0 4]}})
                              (assoc-in [3 4] {:type :land })
                              (assoc-in [0 4] {:type :land }))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (move-units)
          (should= {:type :land  :contents nil} (get-in @atoms/game-map [4 4]))
          (should= {:type :land  :contents {:type :army :mode :moving :owner :player :target [0 4]}} (get-in @atoms/game-map [3 4]))
          (should= {:type :land } (get-in @atoms/game-map [0 4]))
          (should= 3 (count (filter (complement nil?) (flatten @atoms/game-map))))))

      (it "moves a unit one step up towards target at radius 4"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land 
                                               :contents {:type :army :mode :moving :owner :player :target [4 0]}})
                              (assoc-in [4 3] {:type :land })
                              (assoc-in [4 0] {:type :land }))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (move-units)
          (should= {:type :land  :contents nil} (get-in @atoms/game-map [4 4]))
          (should= {:type :land  :contents {:type :army :mode :moving :owner :player :target [4 0]}} (get-in @atoms/game-map [4 3]))
          (should= {:type :land } (get-in @atoms/game-map [4 0]))
          (should= 3 (count (filter (complement nil?) (flatten @atoms/game-map))))))

      (it "moves a unit one step down towards target at radius 4"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land 
                                               :contents {:type :army :mode :moving :owner :player :target [4 8]}})
                              (assoc-in [4 5] {:type :land })
                              (assoc-in [4 8] {:type :land }))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (move-units)
          (should= {:type :land  :contents nil} (get-in @atoms/game-map [4 4]))
          (should= {:type :land  :contents {:type :army :mode :moving :owner :player :target [4 8]}} (get-in @atoms/game-map [4 5]))
          (should= {:type :land } (get-in @atoms/game-map [4 8]))
          (should= 3 (count (filter (complement nil?) (flatten @atoms/game-map))))))

      (it "moves a unit one step up-right towards target at radius 4"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land 
                                               :contents {:type :army :mode :moving :owner :player :target [8 0]}})
                              (assoc-in [5 3] {:type :land })
                              (assoc-in [8 0] {:type :land }))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (move-units)
          (should= {:type :land  :contents nil} (get-in @atoms/game-map [4 4]))
          (should= {:type :land  :contents {:type :army :mode :moving :owner :player :target [8 0]}} (get-in @atoms/game-map [5 3]))
          (should= {:type :land } (get-in @atoms/game-map [8 0]))
          (should= 3 (count (filter (complement nil?) (flatten @atoms/game-map))))))

      (it "moves a unit one step up-left towards target at radius 4"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land 
                                               :contents {:type :army :mode :moving :owner :player :target [0 0]}})
                              (assoc-in [3 3] {:type :land })
                              (assoc-in [0 0] {:type :land }))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (move-units)
          (should= {:type :land  :contents nil} (get-in @atoms/game-map [4 4]))
          (should= {:type :land  :contents {:type :army :mode :moving :owner :player :target [0 0]}} (get-in @atoms/game-map [3 3]))
          (should= {:type :land } (get-in @atoms/game-map [0 0]))
          (should= 3 (count (filter (complement nil?) (flatten @atoms/game-map))))))

      (it "moves a unit one step down-right towards target at radius 4"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land 
                                               :contents {:type :army :mode :moving :owner :player :target [8 8]}})
                              (assoc-in [5 5] {:type :land })
                              (assoc-in [8 8] {:type :land }))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (move-units)
          (should= {:type :land  :contents nil} (get-in @atoms/game-map [4 4]))
          (should= {:type :land  :contents {:type :army :mode :moving :owner :player :target [8 8]}} (get-in @atoms/game-map [5 5]))
          (should= {:type :land } (get-in @atoms/game-map [8 8]))
          (should= 3 (count (filter (complement nil?) (flatten @atoms/game-map))))))

      (it "moves a unit one step down-left towards target at radius 4"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land 
                                               :contents {:type :army :mode :moving :owner :player :target [0 8]}})
                              (assoc-in [3 5] {:type :land })
                              (assoc-in [0 8] {:type :land }))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (move-units)
          (should= {:type :land  :contents nil} (get-in @atoms/game-map [4 4]))
          (should= {:type :land  :contents {:type :army :mode :moving :owner :player :target [0 8]}} (get-in @atoms/game-map [3 5]))
          (should= {:type :land } (get-in @atoms/game-map [0 8]))
          (should= 3 (count (filter (complement nil?) (flatten @atoms/game-map))))))
      )

    (context "multiple steps"
      (it "moves a unit two steps towards target over two calls"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land 
                                               :contents {:type :army :mode :moving :owner :player :target [4 6]}})
                              (assoc-in [4 5] {:type :land })
                              (assoc-in [4 6] {:type :land }))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (move-units)
          ;; After first move, unit at [4 5], still moving
          (should= {:type :land  :contents nil} (get-in @atoms/game-map [4 4]))
          (should= {:type :land  :contents {:type :army :mode :moving :owner :player :target [4 6]}} (get-in @atoms/game-map [4 5]))
          (should= {:type :land } (get-in @atoms/game-map [4 6]))
          ;; Call move-units again
          (move-units)
          ;; After second move, unit at [4 6], awake
          (should= {:type :land  :contents nil} (get-in @atoms/game-map [4 4]))
          (should= {:type :land  :contents nil} (get-in @atoms/game-map [4 5]))
          (should= {:type :land  :contents {:type :army :mode :awake :owner :player}} (get-in @atoms/game-map [4 6]))))
      )
    )
  )




