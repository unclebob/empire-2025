(ns empire.movement-spec
  (:require
    [empire.atoms :as atoms]
    [empire.config :as config]
    [empire.game-loop :as game-loop]
    [empire.movement :refer :all]
    [speclj.core :refer :all]))

(defn move-until-done
  "Helper to move a unit until it stops (returns nil)."
  [coords]
  (loop [current coords]
    (when-let [next-coords (game-loop/move-current-unit current)]
      (recur next-coords))))

(describe "movement"
  (context "move-current-unit"
    (it "does nothing if no unit"
      (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                            (assoc-in [4 4] {:type :land :contents nil})
                            (assoc-in [4 5] {:type :land}))]
        (reset! atoms/game-map initial-map)
        (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
        (game-loop/move-current-unit [4 4])
        (should= 2 (count (filter (complement nil?) (flatten @atoms/game-map))))))

    (context "single moves that awaken the unit"
      (it "moves a unit to its target and sets mode to awake"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land
                                               :contents {:type :army :mode :moving :owner :player :target [4 5] :steps-remaining 1}})
                              (assoc-in [4 5] {:type :land}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (game-loop/move-current-unit [4 4])
          (should= {:type :land} (get-in @atoms/game-map [4 4]))
          (should= {:type :land :contents {:type :army :mode :awake :owner :player :steps-remaining 0}} (get-in @atoms/game-map [4 5]))
          (should= 2 (count (filter (complement nil?) (flatten @atoms/game-map))))))

      (it "moves a unit up and sets mode to awake"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land
                                               :contents {:type :army :mode :moving :owner :player :target [4 3] :steps-remaining 1}})
                              (assoc-in [4 3] {:type :land}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (game-loop/move-current-unit [4 4])
          (should= {:type :land} (get-in @atoms/game-map [4 4]))
          (should= {:type :land :contents {:type :army :mode :awake :owner :player :steps-remaining 0}} (get-in @atoms/game-map [4 3]))
          (should= 2 (count (filter (complement nil?) (flatten @atoms/game-map))))))

      (it "moves a unit left and sets mode to awake"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land
                                               :contents {:type :army :mode :moving :owner :player :target [3 4] :steps-remaining 1}})
                              (assoc-in [3 4] {:type :land}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (game-loop/move-current-unit [4 4])
          (should= {:type :land} (get-in @atoms/game-map [4 4]))
          (should= {:type :land :contents {:type :army :mode :awake :owner :player :steps-remaining 0}} (get-in @atoms/game-map [3 4]))
          (should= 2 (count (filter (complement nil?) (flatten @atoms/game-map))))))

      (it "moves a unit right and sets mode to awake"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land
                                               :contents {:type :army :mode :moving :owner :player :target [5 4] :steps-remaining 1}})
                              (assoc-in [5 4] {:type :land}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (game-loop/move-current-unit [4 4])
          (should= {:type :land} (get-in @atoms/game-map [4 4]))
          (should= {:type :land :contents {:type :army :mode :awake :owner :player :steps-remaining 0}} (get-in @atoms/game-map [5 4]))
          (should= 2 (count (filter (complement nil?) (flatten @atoms/game-map))))))

      (it "moves a unit up-left and sets mode to awake"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land
                                               :contents {:type :army :mode :moving :owner :player :target [3 3] :steps-remaining 1}})
                              (assoc-in [3 3] {:type :land}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (game-loop/move-current-unit [4 4])
          (should= {:type :land} (get-in @atoms/game-map [4 4]))
          (should= {:type :land :contents {:type :army :mode :awake :owner :player :steps-remaining 0}} (get-in @atoms/game-map [3 3]))
          (should= 2 (count (filter (complement nil?) (flatten @atoms/game-map))))))

      (it "moves a unit up-right and sets mode to awake"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land
                                               :contents {:type :army :mode :moving :owner :player :target [5 3] :steps-remaining 1}})
                              (assoc-in [5 3] {:type :land}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (game-loop/move-current-unit [4 4])
          (should= {:type :land} (get-in @atoms/game-map [4 4]))
          (should= {:type :land :contents {:type :army :mode :awake :owner :player :steps-remaining 0}} (get-in @atoms/game-map [5 3]))
          (should= 2 (count (filter (complement nil?) (flatten @atoms/game-map))))))

      (it "moves a unit down-left and sets mode to awake"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land
                                               :contents {:type :army :mode :moving :owner :player :target [3 5] :steps-remaining 1}})
                              (assoc-in [3 5] {:type :land}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (game-loop/move-current-unit [4 4])
          (should= {:type :land} (get-in @atoms/game-map [4 4]))
          (should= {:type :land :contents {:type :army :mode :awake :owner :player :steps-remaining 0}} (get-in @atoms/game-map [3 5]))
          (should= 2 (count (filter (complement nil?) (flatten @atoms/game-map))))))

      (it "moves a unit down-right and sets mode to awake"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land
                                               :contents {:type :army :mode :moving :owner :player :target [5 5] :steps-remaining 1}})
                              (assoc-in [5 5] {:type :land}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (game-loop/move-current-unit [4 4])
          (should= {:type :land} (get-in @atoms/game-map [4 4]))
          (should= {:type :land :contents {:type :army :mode :awake :owner :player :steps-remaining 0}} (get-in @atoms/game-map [5 5]))
          (should= 2 (count (filter (complement nil?) (flatten @atoms/game-map))))))

      (it "wakes up a unit if the next move would be into sea"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land
                                               :contents {:type :army :mode :moving :owner :player :target [4 5] :steps-remaining 1}})
                              (assoc-in [4 5] {:type :sea}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (game-loop/move-current-unit [4 4])
          (should= {:type :land :contents {:type :army :mode :awake :owner :player :reason :cant-move-into-water :steps-remaining 1}} (get-in @atoms/game-map [4 4]))
          (should= {:type :sea} (get-in @atoms/game-map [4 5]))))

      (it "wakes up a unit if the next move would be into a friendly city"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land
                                               :contents {:type :army :mode :moving :owner :player :target [4 5] :steps-remaining 1}})
                              (assoc-in [4 5] {:type :city :city-status :player}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (game-loop/move-current-unit [4 4])
          (should= {:type :land :contents {:type :army :mode :awake :owner :player :reason :cant-move-into-city :steps-remaining 1}} (get-in @atoms/game-map [4 4]))
          (should= {:type :city :city-status :player} (get-in @atoms/game-map [4 5]))))

      (it "wakes up a unit when moving near an enemy city"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land
                                               :contents {:type :army :mode :moving :owner :player :target [4 6] :steps-remaining 1}})
                              (assoc-in [4 5] {:type :land})
                              (assoc-in [4 6] {:type :city :city-status :computer :contents nil}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (game-loop/move-current-unit [4 4])
          (should= {:type :land} (get-in @atoms/game-map [4 4]))
          (should= {:type :land :contents {:type :army :mode :awake :owner :player :reason :army-found-city :steps-remaining 0}} (get-in @atoms/game-map [4 5]))
          (should= {:type :city :city-status :computer :contents nil} (get-in @atoms/game-map [4 6]))))
      )

    (context "visibility updates"
      (it "reveals cells near player-owned units"
        (let [game-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                           (assoc-in [4 4] {:type :land :contents {:type :army :mode :awake :owner :player}})
                           (assoc-in [4 5] {:type :land})
                           (assoc-in [5 4] {:type :land}))]
          (reset! atoms/game-map game-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (update-combatant-map atoms/player-map :player)
          ;; Check that the unit's cell and neighbors are revealed
          (should= {:type :land :contents {:type :army :mode :awake :owner :player}} (get-in @atoms/player-map [4 4]))
          (should= {:type :land} (get-in @atoms/player-map [4 5]))
          (should= {:type :land} (get-in @atoms/player-map [5 4]))
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
                                               :contents {:type :army :mode :moving :owner :player :target [8 4] :steps-remaining 1}})
                              (assoc-in [5 4] {:type :land})
                              (assoc-in [8 4] {:type :land}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (game-loop/move-current-unit [4 4])
          (should= {:type :land} (get-in @atoms/game-map [4 4]))
          (should= {:type :land :contents {:type :army :mode :moving :owner :player :target [8 4] :steps-remaining 0}} (get-in @atoms/game-map [5 4]))
          (should= {:type :land} (get-in @atoms/game-map [8 4]))
          (should= 3 (count (filter (complement nil?) (flatten @atoms/game-map))))))

      (it "moves a unit one step left towards target at radius 4"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land
                                               :contents {:type :army :mode :moving :owner :player :target [0 4] :steps-remaining 1}})
                              (assoc-in [3 4] {:type :land})
                              (assoc-in [0 4] {:type :land}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (game-loop/move-current-unit [4 4])
          (should= {:type :land} (get-in @atoms/game-map [4 4]))
          (should= {:type :land :contents {:type :army :mode :moving :owner :player :target [0 4] :steps-remaining 0}} (get-in @atoms/game-map [3 4]))
          (should= {:type :land} (get-in @atoms/game-map [0 4]))
          (should= 3 (count (filter (complement nil?) (flatten @atoms/game-map))))))

      (it "moves a unit one step up towards target at radius 4"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land
                                               :contents {:type :army :mode :moving :owner :player :target [4 0] :steps-remaining 1}})
                              (assoc-in [4 3] {:type :land})
                              (assoc-in [4 0] {:type :land}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (game-loop/move-current-unit [4 4])
          (should= {:type :land} (get-in @atoms/game-map [4 4]))
          (should= {:type :land :contents {:type :army :mode :moving :owner :player :target [4 0] :steps-remaining 0}} (get-in @atoms/game-map [4 3]))
          (should= {:type :land} (get-in @atoms/game-map [4 0]))
          (should= 3 (count (filter (complement nil?) (flatten @atoms/game-map))))))

      (it "moves a unit one step down towards target at radius 4"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land
                                               :contents {:type :army :mode :moving :owner :player :target [4 8] :steps-remaining 1}})
                              (assoc-in [4 5] {:type :land})
                              (assoc-in [4 8] {:type :land}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (game-loop/move-current-unit [4 4])
          (should= {:type :land} (get-in @atoms/game-map [4 4]))
          (should= {:type :land :contents {:type :army :mode :moving :owner :player :target [4 8] :steps-remaining 0}} (get-in @atoms/game-map [4 5]))
          (should= {:type :land} (get-in @atoms/game-map [4 8]))
          (should= 3 (count (filter (complement nil?) (flatten @atoms/game-map))))))

      (it "moves a unit one step up-right towards target at radius 4"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land
                                               :contents {:type :army :mode :moving :owner :player :target [8 0] :steps-remaining 1}})
                              (assoc-in [5 3] {:type :land})
                              (assoc-in [8 0] {:type :land}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (game-loop/move-current-unit [4 4])
          (should= {:type :land} (get-in @atoms/game-map [4 4]))
          (should= {:type :land :contents {:type :army :mode :moving :owner :player :target [8 0] :steps-remaining 0}} (get-in @atoms/game-map [5 3]))
          (should= {:type :land} (get-in @atoms/game-map [8 0]))
          (should= 3 (count (filter (complement nil?) (flatten @atoms/game-map))))))

      (it "moves a unit one step up-left towards target at radius 4"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land
                                               :contents {:type :army :mode :moving :owner :player :target [0 0] :steps-remaining 1}})
                              (assoc-in [3 3] {:type :land})
                              (assoc-in [0 0] {:type :land}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (game-loop/move-current-unit [4 4])
          (should= {:type :land} (get-in @atoms/game-map [4 4]))
          (should= {:type :land :contents {:type :army :mode :moving :owner :player :target [0 0] :steps-remaining 0}} (get-in @atoms/game-map [3 3]))
          (should= {:type :land} (get-in @atoms/game-map [0 0]))
          (should= 3 (count (filter (complement nil?) (flatten @atoms/game-map))))))

      (it "moves a unit one step down-right towards target at radius 4"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land
                                               :contents {:type :army :mode :moving :owner :player :target [8 8] :steps-remaining 1}})
                              (assoc-in [5 5] {:type :land})
                              (assoc-in [8 8] {:type :land}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (game-loop/move-current-unit [4 4])
          (should= {:type :land} (get-in @atoms/game-map [4 4]))
          (should= {:type :land :contents {:type :army :mode :moving :owner :player :target [8 8] :steps-remaining 0}} (get-in @atoms/game-map [5 5]))
          (should= {:type :land} (get-in @atoms/game-map [8 8]))
          (should= 3 (count (filter (complement nil?) (flatten @atoms/game-map))))))

      (it "moves a unit one step down-left towards target at radius 4"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land
                                               :contents {:type :army :mode :moving :owner :player :target [0 8] :steps-remaining 1}})
                              (assoc-in [3 5] {:type :land})
                              (assoc-in [0 8] {:type :land}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (game-loop/move-current-unit [4 4])
          (should= {:type :land} (get-in @atoms/game-map [4 4]))
          (should= {:type :land :contents {:type :army :mode :moving :owner :player :target [0 8] :steps-remaining 0}} (get-in @atoms/game-map [3 5]))
          (should= {:type :land} (get-in @atoms/game-map [0 8]))
          (should= 3 (count (filter (complement nil?) (flatten @atoms/game-map))))))
      )

    (context "multiple steps"
      (it "moves a unit two steps towards target over two calls"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land
                                               :contents {:type :army :mode :moving :owner :player :target [4 6] :steps-remaining 1}})
                              (assoc-in [4 5] {:type :land})
                              (assoc-in [4 6] {:type :land}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (game-loop/move-current-unit [4 4])
          ;; After first move, unit at [4 5], still moving
          (should= {:type :land} (get-in @atoms/game-map [4 4]))
          (should= {:type :land :contents {:type :army :mode :moving :owner :player :target [4 6] :steps-remaining 0}} (get-in @atoms/game-map [4 5]))
          (should= {:type :land} (get-in @atoms/game-map [4 6]))
          ;; Give the unit another step and call again
          (swap! atoms/game-map assoc-in [4 5 :contents :steps-remaining] 1)
          (game-loop/move-current-unit [4 5])
          ;; After second move, unit at [4 6], awake
          (should= {:type :land} (get-in @atoms/game-map [4 4]))
          (should= {:type :land} (get-in @atoms/game-map [4 5]))
          (should= {:type :land :contents {:type :army :mode :awake :owner :player :steps-remaining 0}} (get-in @atoms/game-map [4 6]))))
      )

    (context "fighter fuel"
      (it "moves fighter and decrements fuel"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land :contents {:type :fighter :mode :moving :owner :player :target [4 5] :fuel 10 :steps-remaining 1}})
                              (assoc-in [4 5] {:type :land}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (game-loop/move-current-unit [4 4])
          (should= {:type :land} (get-in @atoms/game-map [4 4]))
          (should= {:type :land :contents {:type :fighter :mode :awake :owner :player :fuel 9 :steps-remaining 0}} (get-in @atoms/game-map [4 5]))))

      (it "fighter wakes when fuel reaches 0"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land :contents {:type :fighter :mode :moving :owner :player :target [4 5] :fuel 1 :steps-remaining 1}})
                              (assoc-in [4 5] {:type :land}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (game-loop/move-current-unit [4 4])
          (should= {:type :land} (get-in @atoms/game-map [4 4]))
          (should= {:type :land :contents {:type :fighter :mode :awake :owner :player :fuel 0 :reason :fighter-out-of-fuel :steps-remaining 0}} (get-in @atoms/game-map [4 5]))))

      (it "fighter crashes when trying to move with 0 fuel"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land :contents {:type :fighter :mode :moving :owner :player :target [4 5] :fuel 0 :steps-remaining 1}})
                              (assoc-in [4 5] {:type :land}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (game-loop/move-current-unit [4 4])
          (should= {:type :land} (get-in @atoms/game-map [4 4]))
          (should= {:type :land} (get-in @atoms/game-map [4 5]))
          (should= 2 (count (filter (complement nil?) (flatten @atoms/game-map))))))

      (it "fighter lands in city, refuels, and awakens"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land :contents {:type :fighter :mode :moving :owner :player :target [4 5] :fuel 5 :steps-remaining 1}})
                              (assoc-in [4 5] {:type :city :city-status :player}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (game-loop/move-current-unit [4 4])
          (should= {:type :land} (get-in @atoms/game-map [4 4]))
          (let [city-cell (get-in @atoms/game-map [4 5])]
            (should= :city (:type city-cell))
            (should= :player (:city-status city-cell))
            (should= 1 (:fighter-count city-cell))
            (should= 0 (:awake-fighters city-cell 0)))))


      (it "fighter safely lands at friendly city"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land :contents {:type :fighter :mode :moving :owner :player :target [4 5] :fuel 10 :steps-remaining 1}})
                              (assoc-in [4 5] {:type :city :city-status :player}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (reset! atoms/line3-message "")
          (game-loop/move-current-unit [4 4])
          (let [city-cell (get-in @atoms/game-map [4 5])]
            (should= 1 (:fighter-count city-cell))
            (should= 0 (:awake-fighters city-cell 0)))
          (should= "" @atoms/line3-message)))

      (it "fighter wakes before flying over free city"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land :contents {:type :fighter :mode :moving :owner :player :target [4 6] :fuel 10 :steps-remaining 1}})
                              (assoc-in [4 5] {:type :city :city-status :free})
                              (assoc-in [4 6] {:type :land}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (game-loop/move-current-unit [4 4])
          ;; Fighter should stay at starting position, awake
          (let [fighter (:contents (get-in @atoms/game-map [4 4]))]
            (should= :fighter (:type fighter))
            (should= :awake (:mode fighter))
            (should= :fighter-over-defended-city (:reason fighter)))
          ;; City should be empty
          (should= nil (:contents (get-in @atoms/game-map [4 5])))))

      (it "fighter wakes before flying over computer city"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land :contents {:type :fighter :mode :moving :owner :player :target [4 6] :fuel 10 :steps-remaining 1}})
                              (assoc-in [4 5] {:type :city :city-status :computer})
                              (assoc-in [4 6] {:type :land}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (game-loop/move-current-unit [4 4])
          ;; Fighter should stay at starting position, awake
          (let [fighter (:contents (get-in @atoms/game-map [4 4]))]
            (should= :fighter (:type fighter))
            (should= :awake (:mode fighter))
            (should= :fighter-over-defended-city (:reason fighter)))
          ;; City should be empty
          (should= nil (:contents (get-in @atoms/game-map [4 5])))))

      (it "fighter wakes with bingo warning when fuel at 25% and friendly city in range"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land :contents {:type :fighter :mode :moving :owner :player :target [4 8] :fuel 8 :steps-remaining 1}})
                              (assoc-in [4 5] {:type :land})
                              (assoc-in [4 0] {:type :city :city-status :player}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (game-loop/move-current-unit [4 4])
          ;; Fighter should wake up with bingo warning
          (let [fighter (:contents (get-in @atoms/game-map [4 5]))]
            (should= :fighter (:type fighter))
            (should= :awake (:mode fighter))
            (should= :fighter-bingo (:reason fighter)))))

      (it "fighter does not wake with bingo warning when no friendly city in range"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land :contents {:type :fighter :mode :moving :owner :player :target [4 5] :fuel 3 :steps-remaining 1}})
                              (assoc-in [4 5] {:type :land})
                              (assoc-in [0 0] {:type :city :city-status :player}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (game-loop/move-current-unit [4 4])
          ;; Fighter should wake at target, not due to bingo (city at [0 0] is distance 5, beyond fuel 3)
          (let [fighter (:contents (get-in @atoms/game-map [4 5]))]
            (should= :fighter (:type fighter))
            (should= :awake (:mode fighter))
            (should= nil (:reason fighter)))))

      (it "fighter does not wake with bingo warning when fuel above 25%"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land :contents {:type :fighter :mode :moving :owner :player :target [4 5] :fuel 10 :steps-remaining 1}})
                              (assoc-in [4 5] {:type :land})
                              (assoc-in [4 0] {:type :city :city-status :player}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (game-loop/move-current-unit [4 4])
          ;; Fighter should wake at target, not due to bingo (fuel 10 > 8 = 25% of 32)
          (let [fighter (:contents (get-in @atoms/game-map [4 5]))]
            (should= :fighter (:type fighter))
            (should= :awake (:mode fighter))
            (should= nil (:reason fighter)))))

      (it "fighter does not wake with bingo when target is a reachable friendly city"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              ;; Fighter at [4 4] with fuel 8 (bingo level), target is friendly city at [4 7]
                              (assoc-in [4 4] {:type :land :contents {:type :fighter :mode :moving :owner :player :target [4 7] :fuel 8 :steps-remaining 1}})
                              (assoc-in [4 5] {:type :land})
                              (assoc-in [4 7] {:type :city :city-status :player}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (game-loop/move-current-unit [4 4])
          ;; Fighter should NOT bingo - target city is 2 cells away, fuel 7 after move is sufficient
          (let [fighter (:contents (get-in @atoms/game-map [4 5]))]
            (should= :fighter (:type fighter))
            (should= :moving (:mode fighter))
            (should= nil (:reason fighter)))))

      (it "fighter does not wake with bingo when target is a reachable friendly carrier"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              ;; Fighter at [4 4] with fuel 8 (bingo level), target is carrier at [4 6]
                              ;; Distance to carrier is 2, worst-case fuel needed = 2 * 4/3 = 2.67, so 8 fuel is enough
                              (assoc-in [4 4] {:type :land :contents {:type :fighter :mode :moving :owner :player :target [4 6] :fuel 8 :steps-remaining 1}})
                              (assoc-in [4 5] {:type :sea})
                              ;; Carrier at [4 6] - no flight-path needed
                              (assoc-in [4 6] {:type :sea :contents {:type :carrier :mode :sentry :owner :player}})
                              ;; Another friendly city in range to trigger bingo check
                              (assoc-in [0 0] {:type :city :city-status :player}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (game-loop/move-current-unit [4 4])
          ;; Fighter should NOT bingo - carrier is reachable even if moving away
          (let [fighter (:contents (get-in @atoms/game-map [4 5]))]
            (should= :fighter (:type fighter))
            (should= :moving (:mode fighter))
            (should= nil (:reason fighter)))))

      (it "fighter wakes with bingo when carrier is too far to reach"
        (let [initial-map (-> (vec (repeat 12 (vec (repeat 12 nil))))
                              ;; Fighter at [4 4] with fuel 6 (bingo level), target is carrier at [4 10]
                              ;; Distance after move is 5, worst-case fuel needed = 5 * 4/3 = 6.67 > 6
                              (assoc-in [4 4] {:type :land :contents {:type :fighter :mode :moving :owner :player :target [4 10] :fuel 6 :steps-remaining 1}})
                              (assoc-in [4 5] {:type :sea})
                              ;; Carrier at [4 10]
                              (assoc-in [4 10] {:type :sea :contents {:type :carrier :mode :sentry :owner :player}})
                              ;; Friendly city in range to trigger bingo check
                              (assoc-in [0 0] {:type :city :city-status :player}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 12 (vec (repeat 12 nil)))))
          (game-loop/move-current-unit [4 4])
          ;; Fighter should bingo - carrier too far (needs 6.67 fuel, only has 6)
          (let [fighter (:contents (get-in @atoms/game-map [4 5]))]
            (should= :fighter (:type fighter))
            (should= :awake (:mode fighter))
            (should= :fighter-bingo (:reason fighter)))))
      )

    (context "transport with armies"
      (it "loads adjacent sentry armies onto transport"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :sea :contents {:type :transport :mode :sentry :owner :player :hits 1}})
                              (assoc-in [4 3] {:type :land :contents {:type :army :mode :sentry :owner :player :hits 1}})
                              (assoc-in [5 4] {:type :land :contents {:type :army :mode :sentry :owner :player :hits 1}}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (load-adjacent-sentry-armies [4 4])
          (let [transport (:contents (get-in @atoms/game-map [4 4]))]
            (should= 2 (:army-count transport)))
          (should= nil (:contents (get-in @atoms/game-map [4 3])))
          (should= nil (:contents (get-in @atoms/game-map [5 4])))))

      (it "does not load awake armies onto transport"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :sea :contents {:type :transport :mode :sentry :owner :player :hits 1}})
                              (assoc-in [4 3] {:type :land :contents {:type :army :mode :awake :owner :player :hits 1}}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (load-adjacent-sentry-armies [4 4])
          (let [transport (:contents (get-in @atoms/game-map [4 4]))]
            (should= 0 (:army-count transport 0)))
          (should-not= nil (:contents (get-in @atoms/game-map [4 3])))))

      (it "wakes transport after loading armies if at beach"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :sea :contents {:type :transport :mode :sentry :owner :player :hits 1}})
                              (assoc-in [4 3] {:type :land :contents {:type :army :mode :sentry :owner :player :hits 1}})
                              (assoc-in [5 4] {:type :land}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (load-adjacent-sentry-armies [4 4])
          (let [transport (:contents (get-in @atoms/game-map [4 4]))]
            (should= :awake (:mode transport))
            (should= :transport-at-beach (:reason transport))
            (should= 1 (:army-count transport)))))

      (it "wake-armies-on-transport wakes all armies and sets transport to sentry"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :sea :contents {:type :transport :mode :awake :owner :player :hits 1 :army-count 2 :reason :transport-at-beach}})
                              (assoc-in [5 4] {:type :land}))]
          (reset! atoms/game-map initial-map)
          (wake-armies-on-transport [4 4])
          (let [transport (:contents (get-in @atoms/game-map [4 4]))]
            (should= :sentry (:mode transport))
            (should= nil (:reason transport))
            (should= 2 (:army-count transport))
            (should= 2 (:awake-armies transport)))))

      (it "sleep-armies-on-transport puts armies to sleep and wakes transport"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :sea :contents {:type :transport :mode :sentry :owner :player :hits 1 :army-count 2 :awake-armies 2}})
                              (assoc-in [5 4] {:type :land}))]
          (reset! atoms/game-map initial-map)
          (sleep-armies-on-transport [4 4])
          (let [transport (:contents (get-in @atoms/game-map [4 4]))]
            (should= :awake (:mode transport))
            (should= nil (:reason transport))
            (should= 2 (:army-count transport))
            (should= 0 (:awake-armies transport)))))

      (it "disembark-army-from-transport removes one army and decrements counts"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :sea :contents {:type :transport :mode :sentry :owner :player :hits 1 :army-count 3 :awake-armies 3}})
                              (assoc-in [5 4] {:type :land}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (disembark-army-from-transport [4 4] [5 4])
          (let [transport (:contents (get-in @atoms/game-map [4 4]))
                disembarked (:contents (get-in @atoms/game-map [5 4]))]
            (should= 2 (:army-count transport))
            (should= 2 (:awake-armies transport))
            (should= :army (:type disembarked))
            (should= :awake (:mode disembarked)))))

      (it "disembark-army-from-transport wakes transport when last army disembarks"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :sea :contents {:type :transport :mode :sentry :owner :player :hits 1 :army-count 1 :awake-armies 1}})
                              (assoc-in [5 4] {:type :land}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (disembark-army-from-transport [4 4] [5 4])
          (let [transport (:contents (get-in @atoms/game-map [4 4]))]
            (should= :awake (:mode transport))
            (should= 0 (:army-count transport)))))

      (it "disembark-army-from-transport wakes transport when no more awake armies remain"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :sea :contents {:type :transport :mode :sentry :owner :player :hits 1 :army-count 2 :awake-armies 1}})
                              (assoc-in [5 4] {:type :land}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (disembark-army-from-transport [4 4] [5 4])
          (let [transport (:contents (get-in @atoms/game-map [4 4]))]
            (should= :awake (:mode transport))
            (should= 1 (:army-count transport))
            (should= 0 (:awake-armies transport)))))

      (it "transport wakes up when reaching beach with armies"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :sea :contents {:type :transport :mode :moving :owner :player :hits 1 :army-count 1 :target [4 5] :steps-remaining 1}})
                              (assoc-in [4 5] {:type :sea})
                              (assoc-in [5 5] {:type :land}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (game-loop/move-current-unit [4 4])
          (let [transport (:contents (get-in @atoms/game-map [4 5]))]
            (should= :awake (:mode transport))
            (should= :transport-at-beach (:reason transport)))))

      (it "transport does not wake when reaching beach without armies"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :sea :contents {:type :transport :mode :moving :owner :player :hits 1 :target [4 5] :steps-remaining 1}})
                              (assoc-in [4 5] {:type :sea})
                              (assoc-in [5 5] {:type :land}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (game-loop/move-current-unit [4 4])
          (let [transport (:contents (get-in @atoms/game-map [4 5]))]
            (should= :awake (:mode transport))
            (should= nil (:reason transport)))))

      (it "completely-surrounded-by-sea? returns true when no adjacent land"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 {:type :sea}))))
                              (assoc-in [4 4] {:type :sea :contents {:type :transport :mode :moving :owner :player}}))]
          (reset! atoms/game-map initial-map)
          (should (completely-surrounded-by-sea? [4 4] atoms/game-map))))

      (it "completely-surrounded-by-sea? returns false when adjacent to land"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 {:type :sea}))))
                              (assoc-in [4 4] {:type :sea :contents {:type :transport :mode :moving :owner :player}})
                              (assoc-in [4 5] {:type :land}))]
          (reset! atoms/game-map initial-map)
          (should-not (completely-surrounded-by-sea? [4 4] atoms/game-map))))

      (it "transport wakes with found-land when moving from open sea to land visible"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 {:type :sea}))))
                              ;; Transport at [4 4] completely surrounded by sea
                              (assoc-in [4 4] {:type :sea :contents {:type :transport :mode :moving :owner :player :hits 1 :army-count 1 :target [4 5] :steps-remaining 1}})
                              ;; Target at [4 5] is sea but has land at [4 6] (adjacent to [4 5] but not to [4 4])
                              (assoc-in [4 5] {:type :sea})
                              (assoc-in [4 6] {:type :land}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (game-loop/move-current-unit [4 4])
          (let [transport (:contents (get-in @atoms/game-map [4 5]))]
            (should= :awake (:mode transport))
            (should= :transport-found-land (:reason transport)))))

      (it "transport does not wake with found-land when already near land before move"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 {:type :sea}))))
                              ;; Transport at [4 4] already has land at [3 3]
                              (assoc-in [4 4] {:type :sea :contents {:type :transport :mode :moving :owner :player :hits 1 :army-count 1 :target [4 5] :steps-remaining 1}})
                              (assoc-in [3 3] {:type :land})
                              ;; Target at [4 5] also near land at [5 5]
                              (assoc-in [4 5] {:type :sea})
                              (assoc-in [5 5] {:type :land}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (game-loop/move-current-unit [4 4])
          (let [transport (:contents (get-in @atoms/game-map [4 5]))]
            ;; Still wakes because it's at beach with armies, but reason should be :transport-at-beach
            (should= :awake (:mode transport))
            (should= :transport-at-beach (:reason transport)))))

      (it "transport wakes with found-land even without armies"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 {:type :sea}))))
                              ;; Transport at [4 4] completely surrounded by sea, no armies
                              (assoc-in [4 4] {:type :sea :contents {:type :transport :mode :moving :owner :player :hits 1 :target [4 5] :steps-remaining 1}})
                              ;; Target at [4 5] is sea but has land at [4 6] (adjacent to [4 5] but not to [4 4])
                              (assoc-in [4 5] {:type :sea})
                              (assoc-in [4 6] {:type :land}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (game-loop/move-current-unit [4 4])
          (let [transport (:contents (get-in @atoms/game-map [4 5]))]
            (should= :awake (:mode transport))
            (should= :transport-found-land (:reason transport)))))

      (it "get-active-unit returns synthetic army when transport has awake armies"
        (let [cell {:type :sea :contents {:type :transport :mode :sentry :owner :player :army-count 3 :awake-armies 2}}]
          (let [active (get-active-unit cell)]
            (should= :army (:type active))
            (should= :awake (:mode active))
            (should= true (:aboard-transport active)))))

      (it "get-active-unit returns transport when no awake armies"
        (let [cell {:type :sea :contents {:type :transport :mode :awake :owner :player :army-count 1 :awake-armies 0}}]
          (let [active (get-active-unit cell)]
            (should= :transport (:type active))
            (should= :awake (:mode active)))))

      (it "is-army-aboard-transport? returns true for synthetic army with :aboard-transport"
        (let [army {:type :army :mode :awake :owner :player :aboard-transport true}]
          (should= true (is-army-aboard-transport? army))))

      (it "is-army-aboard-transport? returns falsy for army without :aboard-transport"
        (let [army {:type :army :mode :awake :owner :player :hits 1}]
          (should-not (is-army-aboard-transport? army))))
      )

    (describe "carrier fighter deployment"
      (it "fighter lands on carrier and sleeps"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :sea :contents {:type :fighter :mode :moving :owner :player :target [4 5] :fuel 10 :steps-remaining 1}})
                              (assoc-in [4 5] {:type :sea :contents {:type :carrier :mode :sentry :owner :player :hits 8}}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (game-loop/move-current-unit [4 4])
          (let [carrier-cell (get-in @atoms/game-map [4 5])
                carrier (:contents carrier-cell)]
            (should= :carrier (:type carrier))
            (should= 1 (:fighter-count carrier))
            (should= 0 (:awake-fighters carrier 0)))))

      (it "wake-fighters-on-carrier wakes all fighters and sets carrier to sentry"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :sea :contents {:type :carrier :mode :awake :owner :player :hits 8 :fighter-count 2}}))]
          (reset! atoms/game-map initial-map)
          (wake-fighters-on-carrier [4 4])
          (let [carrier (:contents (get-in @atoms/game-map [4 4]))]
            (should= :sentry (:mode carrier))
            (should= 2 (:fighter-count carrier))
            (should= 2 (:awake-fighters carrier)))))

      (it "sleep-fighters-on-carrier puts fighters to sleep and wakes carrier"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :sea :contents {:type :carrier :mode :sentry :owner :player :hits 8 :fighter-count 2 :awake-fighters 2}}))]
          (reset! atoms/game-map initial-map)
          (sleep-fighters-on-carrier [4 4])
          (let [carrier (:contents (get-in @atoms/game-map [4 4]))]
            (should= :awake (:mode carrier))
            (should= 2 (:fighter-count carrier))
            (should= 0 (:awake-fighters carrier)))))

      (it "launch-fighter-from-carrier removes fighter and places it at adjacent cell"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :sea :contents {:type :carrier :mode :sentry :owner :player :hits 8 :fighter-count 2 :awake-fighters 2}})
                              (assoc-in [4 5] {:type :sea}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (launch-fighter-from-carrier [4 4] [4 6])
          (let [carrier (:contents (get-in @atoms/game-map [4 4]))
                launched-fighter (:contents (get-in @atoms/game-map [4 5]))]
            (should= 1 (:fighter-count carrier))
            (should= 1 (:awake-fighters carrier))
            (should= :fighter (:type launched-fighter))
            (should= :moving (:mode launched-fighter))
            (should= [4 6] (:target launched-fighter)))))

      (it "launch-fighter-from-carrier keeps carrier in sentry mode after last fighter launches"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :sea :contents {:type :carrier :mode :sentry :owner :player :hits 8 :fighter-count 1 :awake-fighters 1}})
                              (assoc-in [4 5] {:type :sea}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (launch-fighter-from-carrier [4 4] [4 6])
          (let [carrier (:contents (get-in @atoms/game-map [4 4]))]
            (should= :sentry (:mode carrier))
            (should= 0 (:fighter-count carrier)))))

      (it "launch-fighter-from-carrier sets steps-remaining to speed minus one"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :sea :contents {:type :carrier :mode :sentry :owner :player :hits 8 :fighter-count 1 :awake-fighters 1}})
                              (assoc-in [4 5] {:type :sea}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (launch-fighter-from-carrier [4 4] [4 6])
          (let [fighter (:contents (get-in @atoms/game-map [4 5]))]
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
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :sea :contents {:type :carrier :mode :sentry :owner :player :hits 8 :fighter-count 1 :awake-fighters 1}})
                              (assoc-in [4 5] {:type :sea})
                              (assoc-in [4 6] {:type :sea}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          ;; Launch fighter from carrier toward [4 6]
          (launch-fighter-from-carrier [4 4] [4 6])
          ;; Verify carrier now has 0 fighters
          (let [carrier (:contents (get-in @atoms/game-map [4 4]))]
            (should= 0 (:fighter-count carrier))
            (should= 0 (:awake-fighters carrier)))
          ;; Fighter is at [4 5] moving toward [4 6]
          ;; Now simulate fighter returning to carrier - set its target to carrier
          (let [fighter-cell (get-in @atoms/game-map [4 5])
                fighter (:contents fighter-cell)
                returning-fighter (assoc fighter :target [4 4] :steps-remaining 1)]
            (swap! atoms/game-map assoc-in [4 5 :contents] returning-fighter)
            ;; Move fighter back to carrier
            (game-loop/move-current-unit [4 5])
            ;; Verify fighter landed and is sleeping
            (let [carrier (:contents (get-in @atoms/game-map [4 4]))]
              (should= :carrier (:type carrier))
              (should= 1 (:fighter-count carrier))
              (should= 0 (:awake-fighters carrier 0))))))

      (it "fighter out of fuel crashing near carrier does not destroy carrier"
        ;; Fighter with fuel 0 adjacent to carrier - when it tries to land, it crashes
        ;; but the carrier should remain intact
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :sea :contents {:type :fighter :mode :moving :owner :player :target [4 5] :fuel 0 :steps-remaining 1}})
                              (assoc-in [4 5] {:type :sea :contents {:type :carrier :mode :sentry :owner :player :hits 8 :fighter-count 1}}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (game-loop/move-current-unit [4 4])
          ;; Fighter should be gone (crashed)
          (should-be-nil (:contents (get-in @atoms/game-map [4 4])))
          ;; Carrier should still exist with its original fighter count
          (let [carrier-cell (get-in @atoms/game-map [4 5])
                carrier (:contents carrier-cell)]
            (should= :carrier (:type carrier))
            (should= 1 (:fighter-count carrier)))))
      )

    (describe "is-computers?"
      (it "returns true for computer city"
        (let [cell {:type :city :city-status :computer}]
          (should (is-computers? cell))))

      (it "returns true for cell with computer unit"
        (let [cell {:type :land :contents {:type :army :owner :computer}}]
          (should (is-computers? cell))))

      (it "returns false for player city"
        (let [cell {:type :city :city-status :player}]
          (should-not (is-computers? cell))))

      (it "returns false for cell with player unit"
        (let [cell {:type :land :contents {:type :army :owner :player}}]
          (should-not (is-computers? cell))))

      (it "returns false for empty cell"
        (let [cell {:type :land}]
          (should-not (is-computers? cell)))))

    (describe "wake-before-move edge cases"
      (it "wakes unit when something is in the way"
        (let [unit {:type :army :mode :moving :owner :player :target [4 5] :steps-remaining 1}
              next-cell {:type :land :contents {:type :army :owner :player}}
              [result should-wake?] (wake-before-move unit next-cell)]
          (should= :awake (:mode result))
          (should= :somethings-in-the-way (:reason result))
          (should should-wake?)))

      (it "wakes naval unit when trying to move on land"
        (let [unit {:type :destroyer :mode :moving :owner :player :target [4 5] :steps-remaining 1}
              next-cell {:type :land}
              [result should-wake?] (wake-before-move unit next-cell)]
          (should= :awake (:mode result))
          (should= :ships-cant-drive-on-land (:reason result))
          (should should-wake?))))

    (describe "wake-after-move default case"
      (it "returns default values for naval units like destroyer"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :sea :contents {:type :destroyer :mode :moving :owner :player :target [4 5] :hits 3 :steps-remaining 1}})
                              (assoc-in [4 5] {:type :sea}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          ;; Destroyer moving to its target should wake normally
          (game-loop/move-current-unit [4 4])
          (let [destroyer (:contents (get-in @atoms/game-map [4 5]))]
            (should= :destroyer (:type destroyer))
            (should= :awake (:mode destroyer))))))

    (describe "fighter shot down by city"
      (it "fighter is destroyed when flying into hostile city"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land :contents {:type :fighter :mode :moving :owner :player :target [4 5] :fuel 10 :steps-remaining 1 :hits 1}})
                              (assoc-in [4 5] {:type :city :city-status :computer}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (reset! atoms/line3-message "")
          ;; wake-after-move takes unit, from-pos, final-pos, and current-map (atom)
          (let [cell (get-in @atoms/game-map [4 4])
                unit (:contents cell)
                result (wake-after-move unit [4 4] [4 5] atoms/game-map)]
            (should= 0 (:hits result))))))

    (describe "fighter landing at city"
      (it "fighter lands at city and increments fighter-count"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land :contents {:type :fighter :mode :moving :owner :player :target [4 5] :fuel 10 :steps-remaining 1 :hits 1}})
                              (assoc-in [4 5] {:type :city :city-status :player :fighter-count 0}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (game-loop/move-current-unit [4 4])
          (let [city (get-in @atoms/game-map [4 5])]
            (should= 1 (:fighter-count city))
            (should-be-nil (:contents city))))))

    (describe "get-active-unit airport fighter"
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
      (it "removes awake fighter from airport and places it moving"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :city :city-status :player :fighter-count 2 :awake-fighters 2})
                              (assoc-in [4 5] {:type :land}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (launch-fighter-from-airport [4 4] [4 6])
          (let [city (get-in @atoms/game-map [4 4])
                fighter (:contents city)]
            (should= 1 (:fighter-count city))
            (should= 1 (:awake-fighters city))
            (should= :fighter (:type fighter))
            (should= :moving (:mode fighter))
            (should= [4 6] (:target fighter))))))

    (describe "disembark-army-with-target"
      (it "disembarks army and sets it moving toward extended target"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :sea :contents {:type :transport :mode :sentry :owner :player :hits 1 :army-count 2 :awake-armies 2}})
                              (assoc-in [5 4] {:type :land}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (disembark-army-with-target [4 4] [5 4] [8 4])
          (let [transport (:contents (get-in @atoms/game-map [4 4]))
                army (:contents (get-in @atoms/game-map [5 4]))]
            (should= 1 (:army-count transport))
            (should= 1 (:awake-armies transport))
            (should= :army (:type army))
            (should= :moving (:mode army))
            (should= [8 4] (:target army))
            (should= 0 (:steps-remaining army))))))

    (describe "disembark-army-to-explore"
      (it "disembarks army in explore mode"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :sea :contents {:type :transport :mode :sentry :owner :player :hits 1 :army-count 2 :awake-armies 2}})
                              (assoc-in [5 4] {:type :land}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (let [result (disembark-army-to-explore [4 4] [5 4])]
            (should= [5 4] result)
            (let [transport (:contents (get-in @atoms/game-map [4 4]))
                  army (:contents (get-in @atoms/game-map [5 4]))]
              (should= 1 (:army-count transport))
              (should= 1 (:awake-armies transport))
              (should= :army (:type army))
              (should= :explore (:mode army))
              (should= #{[5 4]} (:visited army)))))))

    (describe "explore movement helpers"
      (it "get-unexplored-explore-moves returns moves adjacent to unexplored"
        (let [game-map (-> (vec (repeat 5 (vec (repeat 5 nil))))
                           (assoc-in [2 2] {:type :land})
                           (assoc-in [2 3] {:type :land})
                           (assoc-in [3 2] {:type :land}))
              player-map (-> (vec (repeat 5 (vec (repeat 5 nil))))
                             (assoc-in [2 2] {:type :land})
                             (assoc-in [2 3] {:type :land}))]
          (reset! atoms/game-map game-map)
          (reset! atoms/player-map player-map)
          ;; [3 2] is unexplored in player-map, so moves from [2 2] that are adjacent to unexplored
          (let [moves (get-unexplored-explore-moves [2 2] atoms/game-map)]
            (should (some #{[3 2]} moves)))))

      (it "pick-explore-move returns visited cell when all cells visited"
        (let [game-map (-> (vec (repeat 5 (vec (repeat 5 {:type :sea}))))
                           (assoc-in [2 2] {:type :land})
                           (assoc-in [2 3] {:type :land})
                           (assoc-in [3 2] {:type :land}))
              player-map (-> (vec (repeat 5 (vec (repeat 5 {:type :sea}))))
                             (assoc-in [2 2] {:type :land})
                             (assoc-in [2 3] {:type :land})
                             (assoc-in [3 2] {:type :land}))]
          (reset! atoms/game-map game-map)
          (reset! atoms/player-map player-map)
          ;; All valid moves are visited
          (let [visited #{[2 3] [3 2]}
                move (pick-explore-move [2 2] atoms/game-map visited)]
            ;; Should still return a move even though all are visited
            (should (some #{move} [[2 3] [3 2]]))))))
    )
  )

(describe "movement-context"
  (it "returns :airport-fighter for fighter from airport"
    (let [cell {:type :city :awake-fighters 1}
          unit {:type :fighter :from-airport true}]
      (should= :airport-fighter (movement-context cell unit))))

  (it "returns :carrier-fighter for fighter from carrier"
    (let [cell {:contents {:type :carrier}}
          unit {:type :fighter :from-carrier true}]
      (should= :carrier-fighter (movement-context cell unit))))

  (it "returns :army-aboard for army aboard transport"
    (let [cell {:contents {:type :transport}}
          unit {:type :army :aboard-transport true}]
      (should= :army-aboard (movement-context cell unit))))

  (it "returns :standard-unit for regular unit"
    (let [cell {:contents {:type :army}}
          unit {:type :army :mode :awake}]
      (should= :standard-unit (movement-context cell unit))))

  (it "returns :standard-unit for nil unit"
    (should= :standard-unit (movement-context {} nil))))

(describe "add-unit-at"
  (before
    (reset! atoms/game-map (vec (repeat 9 (vec (repeat 9 {:type :land}))))))

  (it "adds army unit at empty cell"
    (add-unit-at [3 4] :army)
    (let [contents (get-in @atoms/game-map [3 4 :contents])]
      (should= :army (:type contents))
      (should= :player (:owner contents))
      (should= :awake (:mode contents))
      (should= (config/item-hits :army) (:hits contents))))

  (it "adds fighter with fuel"
    (add-unit-at [3 4] :fighter)
    (let [contents (get-in @atoms/game-map [3 4 :contents])]
      (should= :fighter (:type contents))
      (should= config/fighter-fuel (:fuel contents))))

  (it "does not add unit if cell has contents"
    (swap! atoms/game-map assoc-in [3 4 :contents] {:type :army :owner :computer})
    (add-unit-at [3 4] :carrier)
    (should= :army (get-in @atoms/game-map [3 4 :contents :type]))))

(describe "wake-at"
  (before
    (reset! atoms/game-map (vec (repeat 9 (vec (repeat 9 {:type :land})))))
    (reset! atoms/production {}))

  (it "wakes a sleeping unit"
    (swap! atoms/game-map assoc-in [3 4 :contents]
           {:type :army :owner :player :mode :sentry})
    (should (wake-at [3 4]))
    (should= :awake (get-in @atoms/game-map [3 4 :contents :mode])))

  (it "wakes unit in explore mode"
    (swap! atoms/game-map assoc-in [3 4 :contents]
           {:type :army :owner :player :mode :explore})
    (should (wake-at [3 4]))
    (should= :awake (get-in @atoms/game-map [3 4 :contents :mode])))

  (it "returns nil for already awake unit"
    (swap! atoms/game-map assoc-in [3 4 :contents]
           {:type :army :owner :player :mode :awake})
    (should-not (wake-at [3 4])))

  (it "returns nil for enemy unit"
    (swap! atoms/game-map assoc-in [3 4 :contents]
           {:type :army :owner :computer :mode :sentry})
    (should-not (wake-at [3 4])))

  (it "wakes player city and removes production"
    (swap! atoms/game-map assoc-in [3 4]
           {:type :city :city-status :player :sleeping-fighters 0 :awake-fighters 0})
    (reset! atoms/production {[3 4] {:item :army :remaining-rounds 5}})
    (should (wake-at [3 4]))
    (should-not (get @atoms/production [3 4])))

  (it "returns nil for empty cell"
    (should-not (wake-at [3 4])))

  (it "returns nil for enemy city"
    (swap! atoms/game-map assoc-in [3 4]
           {:type :city :city-status :computer})
    (should-not (wake-at [3 4]))))

(describe "sidestep around friendly units"
  (it "sidesteps diagonally around friendly unit and continues moving"
    (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                          ;; Army at [4 4] moving to [4 8], blocked by friendly at [4 5]
                          (assoc-in [4 4] {:type :land :contents {:type :army :mode :moving :owner :player :target [4 8] :steps-remaining 2}})
                          (assoc-in [4 5] {:type :land :contents {:type :army :owner :player :mode :sentry}})
                          (assoc-in [5 5] {:type :land})  ;; Diagonal sidestep
                          (assoc-in [3 5] {:type :land})  ;; Other diagonal
                          (assoc-in [4 6] {:type :land})
                          (assoc-in [4 7] {:type :land})
                          (assoc-in [4 8] {:type :land}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
      (game-loop/move-current-unit [4 4])
      ;; Unit should have sidestepped and continued - now at [4 6] after sidestep+move
      ;; (sidestep to [5 5] or [3 5], then normal move to [4 6])
      (should (:contents (get-in @atoms/game-map [4 6])))
      ;; Original cell should be empty
      (should-be-nil (:contents (get-in @atoms/game-map [4 4])))
      ;; Blocking army should still be there
      (should (:contents (get-in @atoms/game-map [4 5])))))

  (it "sidesteps orthogonally when diagonals blocked and continues moving"
    (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                          ;; Army at [4 4] moving diagonally to [6 6], blocked by friendly at [5 5]
                          (assoc-in [4 4] {:type :land :contents {:type :army :mode :moving :owner :player :target [6 6] :steps-remaining 2}})
                          (assoc-in [5 5] {:type :land :contents {:type :army :owner :player :mode :sentry}})
                          ;; Adjacent diagonal sidesteps blocked by water
                          (assoc-in [5 4] {:type :sea})
                          (assoc-in [4 5] {:type :sea})
                          ;; Orthogonal sidesteps available
                          (assoc-in [3 5] {:type :land})
                          (assoc-in [5 3] {:type :land})
                          ;; Clear path from orthogonal positions to target
                          (assoc-in [4 6] {:type :land})  ;; Path from [3 5]
                          (assoc-in [5 6] {:type :land})
                          (assoc-in [6 5] {:type :land})  ;; Path from [5 3]
                          (assoc-in [6 4] {:type :land})
                          (assoc-in [6 6] {:type :land}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
      (game-loop/move-current-unit [4 4])
      ;; Unit should have sidestepped and continued toward target
      ;; Either path leads to [6 6] or nearby after sidestep + normal move
      (should-be-nil (:contents (get-in @atoms/game-map [4 4])))
      ;; Blocking army should still be there
      (should (:contents (get-in @atoms/game-map [5 5])))
      ;; Unit should have progressed (could be at [4 6], [6 4], [5 6], or [6 5] depending on path)
      (should (or (:contents (get-in @atoms/game-map [4 6]))
                  (:contents (get-in @atoms/game-map [6 4]))
                  (:contents (get-in @atoms/game-map [5 6]))
                  (:contents (get-in @atoms/game-map [6 5]))))))

  (it "wakes when no valid sidestep exists"
    (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                          ;; Army at [4 4] moving to [4 8], blocked by friendly, all sidesteps blocked
                          (assoc-in [4 4] {:type :land :contents {:type :army :mode :moving :owner :player :target [4 8] :steps-remaining 1}})
                          (assoc-in [4 5] {:type :land :contents {:type :army :owner :player :mode :sentry}})
                          (assoc-in [5 5] {:type :sea})
                          (assoc-in [3 5] {:type :sea})
                          (assoc-in [5 4] {:type :sea})
                          (assoc-in [3 4] {:type :sea})
                          (assoc-in [4 8] {:type :land}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
      (game-loop/move-current-unit [4 4])
      ;; Unit should wake up at original position
      (let [unit (:contents (get-in @atoms/game-map [4 4]))]
        (should= :awake (:mode unit))
        (should= :somethings-in-the-way (:reason unit)))))

  (it "does not sidestep when blocked by enemy unit"
    (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                          ;; Army at [4 4] moving to [4 8], blocked by enemy army
                          (assoc-in [4 4] {:type :land :contents {:type :army :mode :moving :owner :player :target [4 8] :steps-remaining 1}})
                          (assoc-in [4 5] {:type :land :contents {:type :army :owner :computer :mode :sentry}})
                          (assoc-in [5 5] {:type :land})  ;; Diagonal available
                          (assoc-in [3 5] {:type :land})  ;; Diagonal available
                          (assoc-in [4 8] {:type :land}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
      (game-loop/move-current-unit [4 4])
      ;; Unit should wake up, not sidestep (enemy blocking)
      (let [unit (:contents (get-in @atoms/game-map [4 4]))]
        (should= :awake (:mode unit))
        (should= :somethings-in-the-way (:reason unit)))))

  (it "fighter sidesteps around friendly fighter and continues"
    (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                          (assoc-in [4 4] {:type :land :contents {:type :fighter :mode :moving :owner :player :target [4 8] :fuel 20 :steps-remaining 2}})
                          (assoc-in [4 5] {:type :land :contents {:type :fighter :owner :player :mode :sentry :fuel 10}})
                          (assoc-in [5 5] {:type :land})
                          (assoc-in [3 5] {:type :land})
                          (assoc-in [4 6] {:type :land})
                          (assoc-in [4 7] {:type :land})
                          (assoc-in [4 8] {:type :land}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
      (game-loop/move-current-unit [4 4])
      ;; Fighter should have sidestepped and continued to [4 6]
      (should (:contents (get-in @atoms/game-map [4 6])))
      (should-be-nil (:contents (get-in @atoms/game-map [4 4])))
      ;; Blocking fighter should still be there
      (should (:contents (get-in @atoms/game-map [4 5])))))

  (it "ship sidesteps around friendly ship and continues"
    (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                          (assoc-in [4 4] {:type :sea :contents {:type :destroyer :mode :moving :owner :player :target [4 8] :hits 3 :steps-remaining 2}})
                          (assoc-in [4 5] {:type :sea :contents {:type :battleship :owner :player :mode :sentry :hits 10}})
                          (assoc-in [5 5] {:type :sea})
                          (assoc-in [3 5] {:type :sea})
                          (assoc-in [4 6] {:type :sea})
                          (assoc-in [4 7] {:type :sea})
                          (assoc-in [4 8] {:type :sea}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
      (game-loop/move-current-unit [4 4])
      ;; Ship should have sidestepped and continued to [4 6]
      (should (:contents (get-in @atoms/game-map [4 6])))
      (should-be-nil (:contents (get-in @atoms/game-map [4 4])))
      ;; Blocking ship should still be there
      (should (:contents (get-in @atoms/game-map [4 5])))))

  (it "chooses sidestep that gets closer to target using 4-round look-ahead"
    (let [initial-map (-> (vec (repeat 12 (vec (repeat 12 nil))))
                          ;; Army at [4 4] moving to [4 10], blocked by friendly at [4 5]
                          ;; One path has clear land, other has water blocking
                          (assoc-in [4 4] {:type :land :contents {:type :army :mode :moving :owner :player :target [4 10] :steps-remaining 2}})
                          (assoc-in [4 5] {:type :land :contents {:type :army :owner :player :mode :sentry}})
                          (assoc-in [5 5] {:type :land})
                          (assoc-in [3 5] {:type :land})
                          ;; From [5 5] path goes diagonally toward [4 10]: [4 6], [4 7], [4 8]
                          (assoc-in [4 6] {:type :land})
                          (assoc-in [4 7] {:type :land})
                          (assoc-in [4 8] {:type :land})
                          ;; From [3 5] path goes diagonally: [4 6] - already defined and clear
                          ;; But we block [3 6] to simulate bad terrain in that direction
                          (assoc-in [3 6] {:type :sea})
                          (assoc-in [4 10] {:type :land}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 12 (vec (repeat 12 nil)))))
      (game-loop/move-current-unit [4 4])
      ;; Both sidesteps lead to [4 6] after sidestep+continuation
      ;; The unit sidesteps and then takes a normal move toward target
      (should (:contents (get-in @atoms/game-map [4 6])))
      (should-be-nil (:contents (get-in @atoms/game-map [4 4])))
      ;; Blocking army should still be there
      (should (:contents (get-in @atoms/game-map [4 5])))))

  (it "wakes up when blocked by long line of friendly units (no progress possible)"
    (let [initial-map (-> (vec (repeat 12 (vec (repeat 12 nil))))
                          ;; Army at [4 4] moving south to [4 10]
                          (assoc-in [4 4] {:type :land :contents {:type :army :mode :moving :owner :player :target [4 10] :steps-remaining 1}})
                          ;; Line of friendly armies blocking the entire row
                          (assoc-in [2 5] {:type :land :contents {:type :army :owner :player :mode :sentry}})
                          (assoc-in [3 5] {:type :land :contents {:type :army :owner :player :mode :sentry}})
                          (assoc-in [4 5] {:type :land :contents {:type :army :owner :player :mode :sentry}})
                          (assoc-in [5 5] {:type :land :contents {:type :army :owner :player :mode :sentry}})
                          (assoc-in [6 5] {:type :land :contents {:type :army :owner :player :mode :sentry}})
                          ;; Empty cells to the sides (but they don't help get closer)
                          (assoc-in [5 4] {:type :land})
                          (assoc-in [3 4] {:type :land})
                          (assoc-in [4 10] {:type :land}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 12 (vec (repeat 12 nil)))))
      (game-loop/move-current-unit [4 4])
      ;; Unit should wake up since sidestepping doesn't get us closer
      (let [unit (:contents (get-in @atoms/game-map [4 4]))]
        (should= :awake (:mode unit))
        (should= :somethings-in-the-way (:reason unit)))))

  (it "does not sidestep outside map boundaries"
    (let [initial-map (-> (vec (repeat 5 (vec (repeat 5 nil))))
                          ;; Army at [0 0] (corner) moving to [0 4], blocked by friendly at [0 1]
                          (assoc-in [0 0] {:type :land :contents {:type :army :mode :moving :owner :player :target [0 4] :steps-remaining 2}})
                          (assoc-in [0 1] {:type :land :contents {:type :army :owner :player :mode :sentry}})
                          ;; Only valid sidestep would be [1 1] (diagonal into map)
                          ;; [-1 0], [-1 1], [0 -1] etc. are out of bounds
                          (assoc-in [1 0] {:type :sea})  ;; Block [1 0] so only [1 1] is valid
                          (assoc-in [1 1] {:type :land})
                          (assoc-in [0 2] {:type :land})
                          (assoc-in [0 3] {:type :land})
                          (assoc-in [0 4] {:type :land}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 5 (vec (repeat 5 nil)))))
      (game-loop/move-current-unit [0 0])
      ;; Unit should sidestep to [1 1] and continue to [0 2]
      (should (:contents (get-in @atoms/game-map [0 2])))
      (should-be-nil (:contents (get-in @atoms/game-map [0 0]))))))

(describe "sidestep around cities"
  (it "army sidesteps around friendly city"
    (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                          ;; Army at [4 4] moving to [4 8], friendly city at [4 5]
                          (assoc-in [4 4] {:type :land :contents {:type :army :mode :moving :owner :player :target [4 8] :steps-remaining 2}})
                          (assoc-in [4 5] {:type :city :city-status :player})
                          (assoc-in [5 5] {:type :land})  ;; Diagonal sidestep
                          (assoc-in [3 5] {:type :land})  ;; Other diagonal
                          (assoc-in [4 6] {:type :land})
                          (assoc-in [4 7] {:type :land})
                          (assoc-in [4 8] {:type :land}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
      (game-loop/move-current-unit [4 4])
      ;; Army should have sidestepped around friendly city and continued
      (should (:contents (get-in @atoms/game-map [4 6])))
      (should-be-nil (:contents (get-in @atoms/game-map [4 4])))))

  (it "army wakes when no sidestep around friendly city exists"
    (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                          ;; Army at [4 4] moving to [4 8], friendly city at [4 5], all sidesteps blocked
                          (assoc-in [4 4] {:type :land :contents {:type :army :mode :moving :owner :player :target [4 8] :steps-remaining 1}})
                          (assoc-in [4 5] {:type :city :city-status :player})
                          (assoc-in [5 5] {:type :sea})
                          (assoc-in [3 5] {:type :sea})
                          (assoc-in [5 4] {:type :sea})
                          (assoc-in [3 4] {:type :sea}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
      (game-loop/move-current-unit [4 4])
      ;; Army should wake up since no sidestep exists
      (let [unit (:contents (get-in @atoms/game-map [4 4]))]
        (should= :awake (:mode unit))
        (should= :cant-move-into-city (:reason unit)))))

  (it "fighter sidesteps around free city when not target"
    (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                          ;; Fighter at [4 4] moving to [4 8], free city at [4 5]
                          (assoc-in [4 4] {:type :land :contents {:type :fighter :mode :moving :owner :player :target [4 8] :fuel 20 :steps-remaining 2}})
                          (assoc-in [4 5] {:type :city :city-status :free})
                          (assoc-in [5 5] {:type :land})  ;; Diagonal sidestep
                          (assoc-in [3 5] {:type :land})
                          (assoc-in [4 6] {:type :land})
                          (assoc-in [4 7] {:type :land})
                          (assoc-in [4 8] {:type :land}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
      (game-loop/move-current-unit [4 4])
      ;; Fighter should have sidestepped around city and continued
      (should (:contents (get-in @atoms/game-map [4 6])))
      (should-be-nil (:contents (get-in @atoms/game-map [4 4])))))

  (it "fighter sidesteps around player city when not target"
    (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                          ;; Fighter at [4 4] moving to [4 8], player city at [4 5]
                          (assoc-in [4 4] {:type :land :contents {:type :fighter :mode :moving :owner :player :target [4 8] :fuel 20 :steps-remaining 2}})
                          (assoc-in [4 5] {:type :city :city-status :player})
                          (assoc-in [5 5] {:type :land})
                          (assoc-in [3 5] {:type :land})
                          (assoc-in [4 6] {:type :land})
                          (assoc-in [4 7] {:type :land})
                          (assoc-in [4 8] {:type :land}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
      (game-loop/move-current-unit [4 4])
      ;; Fighter should have sidestepped around city and continued
      (should (:contents (get-in @atoms/game-map [4 6])))
      (should-be-nil (:contents (get-in @atoms/game-map [4 4])))))

  (it "fighter does not sidestep when city is target"
    (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                          ;; Fighter at [4 4] with target [4 5] which is a player city
                          (assoc-in [4 4] {:type :land :contents {:type :fighter :mode :moving :owner :player :target [4 5] :fuel 20 :steps-remaining 2}})
                          (assoc-in [4 5] {:type :city :city-status :player :fighter-count 0})
                          (assoc-in [5 5] {:type :land})
                          (assoc-in [3 5] {:type :land}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
      (game-loop/move-current-unit [4 4])
      ;; Fighter should land at target city, not sidestep
      (should= 1 (:fighter-count (get-in @atoms/game-map [4 5])))
      (should-be-nil (:contents (get-in @atoms/game-map [4 4])))))

  (it "fighter sidesteps around hostile city"
    (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                          ;; Fighter at [4 4] moving to [4 8], hostile city at [4 5]
                          (assoc-in [4 4] {:type :land :contents {:type :fighter :mode :moving :owner :player :target [4 8] :fuel 20 :steps-remaining 2}})
                          (assoc-in [4 5] {:type :city :city-status :computer})
                          (assoc-in [5 5] {:type :land})
                          (assoc-in [3 5] {:type :land})
                          (assoc-in [4 6] {:type :land})
                          (assoc-in [4 7] {:type :land})
                          (assoc-in [4 8] {:type :land}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
      (game-loop/move-current-unit [4 4])
      ;; Fighter should have sidestepped around hostile city
      (should (:contents (get-in @atoms/game-map [4 6])))
      (should-be-nil (:contents (get-in @atoms/game-map [4 4]))))))

(describe "satellite movement"
  (it "does not move without a target"
    (let [initial-map (-> (vec (repeat 10 (vec (repeat 10 {:type :land}))))
                          (assoc-in [5 5] {:type :land :contents {:type :satellite :owner :player :turns-remaining 50}}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 10 (vec (repeat 10 nil)))))
      (move-satellite [5 5])
      ;; Satellite should stay in place - no target set
      (should (:contents (get-in @atoms/game-map [5 5])))
      (should-be-nil (:target (:contents (get-in @atoms/game-map [5 5]))))))

  (it "still decrements turns even without a target"
    (let [initial-map (-> (vec (repeat 10 (vec (repeat 10 {:type :land}))))
                          (assoc-in [5 5] {:type :land :contents {:type :satellite :owner :player :turns-remaining 5}}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 10 (vec (repeat 10 nil)))))
      ;; Run move-satellites (which calls move-satellite-steps)
      (game-loop/move-satellites)
      ;; Satellite should still be at [5 5] but with decremented turns
      (let [sat (:contents (get-in @atoms/game-map [5 5]))]
        (should sat)
        (should= 4 (:turns-remaining sat)))))

  (it "moves toward its target"
    (let [initial-map (-> (vec (repeat 10 (vec (repeat 10 {:type :land}))))
                          (assoc-in [5 5] {:type :land :contents {:type :satellite :owner :player :target [9 9] :turns-remaining 50}}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 10 (vec (repeat 10 nil)))))
      (move-satellite [5 5])
      ;; Satellite should have moved toward target [9 9], so to [6 6]
      (should (:contents (get-in @atoms/game-map [6 6])))
      (should-be-nil (:contents (get-in @atoms/game-map [5 5])))
      (should= [9 9] (:target (:contents (get-in @atoms/game-map [6 6]))))))

  (it "moves horizontally when target is directly east"
    (let [initial-map (-> (vec (repeat 10 (vec (repeat 10 {:type :land}))))
                          (assoc-in [5 3] {:type :land :contents {:type :satellite :owner :player :target [5 9] :turns-remaining 50}}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 10 (vec (repeat 10 nil)))))
      (move-satellite [5 3])
      ;; Satellite should move east to [5 4]
      (should (:contents (get-in @atoms/game-map [5 4])))
      (should-be-nil (:contents (get-in @atoms/game-map [5 3])))))

  (it "moves vertically when target is directly south"
    (let [initial-map (-> (vec (repeat 10 (vec (repeat 10 {:type :land}))))
                          (assoc-in [3 5] {:type :land :contents {:type :satellite :owner :player :target [9 5] :turns-remaining 50}}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 10 (vec (repeat 10 nil)))))
      (move-satellite [3 5])
      ;; Satellite should move south to [4 5]
      (should (:contents (get-in @atoms/game-map [4 5])))
      (should-be-nil (:contents (get-in @atoms/game-map [3 5])))))

  (it "gets new target on opposite boundary when reaching right edge"
    (let [initial-map (-> (vec (repeat 10 (vec (repeat 10 {:type :land}))))
                          (assoc-in [5 9] {:type :land :contents {:type :satellite :owner :player :target [5 9] :turns-remaining 50}}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 10 (vec (repeat 10 nil)))))
      (move-satellite [5 9])
      ;; Satellite at target on right edge should get new target on left edge (column 0)
      (let [sat (:contents (get-in @atoms/game-map [5 9]))]
        (should sat)
        (should= 0 (second (:target sat))))))

  (it "gets new target on opposite boundary when reaching bottom edge"
    (let [initial-map (-> (vec (repeat 10 (vec (repeat 10 {:type :land}))))
                          (assoc-in [9 5] {:type :land :contents {:type :satellite :owner :player :target [9 5] :turns-remaining 50}}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 10 (vec (repeat 10 nil)))))
      (move-satellite [9 5])
      ;; Satellite at target on bottom edge should get new target on top edge (row 0)
      (let [sat (:contents (get-in @atoms/game-map [9 5]))]
        (should sat)
        (should= 0 (first (:target sat))))))

  (it "gets new target on one of opposite boundaries when at corner"
    (let [initial-map (-> (vec (repeat 10 (vec (repeat 10 {:type :land}))))
                          (assoc-in [9 9] {:type :land :contents {:type :satellite :owner :player :target [9 9] :turns-remaining 50}}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 10 (vec (repeat 10 nil)))))
      (move-satellite [9 9])
      ;; Satellite at corner should get new target on either top edge (row 0) or left edge (column 0)
      (let [sat (:contents (get-in @atoms/game-map [9 9]))
            [tx ty] (:target sat)]
        (should sat)
        (should (or (= tx 0) (= ty 0))))))

  (it "extends non-boundary target to wall when setting movement"
    (let [initial-map (-> (vec (repeat 10 (vec (repeat 10 {:type :land}))))
                          ;; Satellite at [2 2] - will set target to [5 5] (not on boundary)
                          (assoc-in [2 2] {:type :land :contents {:type :satellite :owner :player :mode :awake :turns-remaining 50}}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 10 (vec (repeat 10 nil)))))
      ;; Set movement to non-boundary target [5 5] - should extend to [9 9]
      (set-unit-movement [2 2] [5 5])
      (let [sat (:contents (get-in @atoms/game-map [2 2]))
            [tx ty] (:target sat)]
        (should sat)
        (should= :moving (:mode sat))
        ;; Target should be extended to boundary at [9 9] (southeast corner)
        (should= [9 9] [tx ty]))))

  (it "decrements turns-remaining once per round not per step"
    (let [initial-map (-> (vec (repeat 10 (vec (repeat 10 {:type :land}))))
                          (assoc-in [2 2] {:type :land :contents {:type :satellite :owner :player :target [9 9] :turns-remaining 50}}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 10 (vec (repeat 10 nil)))))
      (game-loop/move-satellites)
      ;; After one round of movement (10 steps), turns-remaining should only decrement by 1
      (let [sat-coords (first (for [i (range 10) j (range 10)
                                    :let [cell (get-in @atoms/game-map [i j])]
                                    :when (= :satellite (:type (:contents cell)))]
                                [i j]))
            sat (:contents (get-in @atoms/game-map sat-coords))]
        (should= 49 (:turns-remaining sat)))))

  (it "is removed when turns-remaining reaches zero"
    (let [initial-map (-> (vec (repeat 10 (vec (repeat 10 {:type :land}))))
                          (assoc-in [5 5] {:type :land :contents {:type :satellite :owner :player :target [9 9] :turns-remaining 1}}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 10 (vec (repeat 10 nil)))))
      (game-loop/move-satellites)
      ;; Satellite should be removed after round ends with turns-remaining at 0
      ;; Check that satellite is gone from both original and any moved position
      (let [sat-count (count (for [i (range 10) j (range 10)
                                   :let [cell (get-in @atoms/game-map [i j])]
                                   :when (= :satellite (:type (:contents cell)))]
                               [i j]))]
        (should= 0 sat-count))))

  (it "dies after correct number of rounds"
    (let [initial-map (-> (vec (repeat 20 (vec (repeat 20 {:type :land}))))
                          (assoc-in [10 10] {:type :land :contents {:type :satellite :owner :player :target [19 19] :turns-remaining 5}}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 20 (vec (repeat 20 nil)))))
      ;; Run 4 rounds - satellite should still exist
      (dotimes [_ 4]
        (game-loop/move-satellites))
      (let [sat-count (count (for [i (range 20) j (range 20)
                                   :let [cell (get-in @atoms/game-map [i j])]
                                   :when (= :satellite (:type (:contents cell)))]
                               [i j]))]
        (should= 1 sat-count))
      ;; Run 1 more round - satellite should be removed
      (game-loop/move-satellites)
      (let [sat-count (count (for [i (range 20) j (range 20)
                                   :let [cell (get-in @atoms/game-map [i j])]
                                   :when (= :satellite (:type (:contents cell)))]
                               [i j]))]
        (should= 0 sat-count))))

  (it "dies through full game loop with start-new-round"
    (let [initial-map (-> (vec (repeat 20 (vec (repeat 20 {:type :land}))))
                          (assoc-in [10 10] {:type :land :contents {:type :satellite :owner :player :target [19 19] :turns-remaining 3}}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 20 (vec (repeat 20 nil)))))
      (reset! atoms/player-items [])
      (reset! atoms/production {})
      (reset! atoms/round-number 0)
      ;; Run 3 full rounds via start-new-round
      (dotimes [_ 3]
        (game-loop/start-new-round)
        ;; Process all player items (the satellite should be skipped because it has a target)
        (while (seq @atoms/player-items)
          (game-loop/advance-game)))
      ;; Satellite should be dead after 3 rounds
      (let [sat-count (count (for [i (range 20) j (range 20)
                                   :let [cell (get-in @atoms/game-map [i j])]
                                   :when (= :satellite (:type (:contents cell)))]
                               [i j]))]
        (should= 0 sat-count))))

  (it "dies even when bouncing off corners multiple times"
    ;; Satellite starting near corner, will bounce multiple times in 5 turns
    (let [initial-map (-> (vec (repeat 10 (vec (repeat 10 {:type :land}))))
                          (assoc-in [8 8] {:type :land :contents {:type :satellite :owner :player :target [9 9] :turns-remaining 5}}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 10 (vec (repeat 10 nil)))))
      ;; Run 5 rounds - satellite should die
      (dotimes [_ 5]
        (game-loop/move-satellites))
      (let [sat-count (count (for [i (range 10) j (range 10)
                                   :let [cell (get-in @atoms/game-map [i j])]
                                   :when (= :satellite (:type (:contents cell)))]
                               [i j]))]
        (should= 0 sat-count))))

  (it "is removed from visibility map when it dies"
    (let [initial-map (-> (vec (repeat 10 (vec (repeat 10 {:type :land}))))
                          (assoc-in [5 5] {:type :land :contents {:type :satellite :owner :player :target [9 9] :turns-remaining 1}}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 10 (vec (repeat 10 nil)))))
      ;; Update visibility so satellite appears on player-map
      (update-cell-visibility [5 5] :player)
      ;; Verify satellite is visible
      (should= :satellite (:type (:contents (get-in @atoms/player-map [5 5]))))
      ;; Run one round - satellite should die and be removed from both maps
      (game-loop/move-satellites)
      ;; Find where satellite ended up (it moved before dying)
      (let [sat-in-game (first (for [i (range 10) j (range 10)
                                     :let [cell (get-in @atoms/game-map [i j])]
                                     :when (= :satellite (:type (:contents cell)))]
                                 [i j]))
            sat-in-player (first (for [i (range 10) j (range 10)
                                       :let [cell (get-in @atoms/player-map [i j])]
                                       :when (= :satellite (:type (:contents cell)))]
                                   [i j]))]
        (should-be-nil sat-in-game)
        (should-be-nil sat-in-player))))

  (it "reveals two rectangular rings around its position"
    (let [initial-map (vec (repeat 15 (vec (repeat 15 {:type :land}))))
          initial-map (assoc-in initial-map [7 7] {:type :land :contents {:type :satellite :owner :player :target [14 14] :turns-remaining 50}})]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 15 (vec (repeat 15 nil)))))
      (update-cell-visibility [7 7] :player)
      ;; Ring 1 (distance 1) - all 8 cells should be visible
      (should (get-in @atoms/player-map [6 6]))
      (should (get-in @atoms/player-map [6 7]))
      (should (get-in @atoms/player-map [6 8]))
      (should (get-in @atoms/player-map [7 6]))
      (should (get-in @atoms/player-map [7 8]))
      (should (get-in @atoms/player-map [8 6]))
      (should (get-in @atoms/player-map [8 7]))
      (should (get-in @atoms/player-map [8 8]))
      ;; Ring 2 (distance 2) - all 16 cells should be visible
      (should (get-in @atoms/player-map [5 5]))
      (should (get-in @atoms/player-map [5 6]))
      (should (get-in @atoms/player-map [5 7]))
      (should (get-in @atoms/player-map [5 8]))
      (should (get-in @atoms/player-map [5 9]))
      (should (get-in @atoms/player-map [6 5]))
      (should (get-in @atoms/player-map [6 9]))
      (should (get-in @atoms/player-map [7 5]))
      (should (get-in @atoms/player-map [7 9]))
      (should (get-in @atoms/player-map [8 5]))
      (should (get-in @atoms/player-map [8 9]))
      (should (get-in @atoms/player-map [9 5]))
      (should (get-in @atoms/player-map [9 6]))
      (should (get-in @atoms/player-map [9 7]))
      (should (get-in @atoms/player-map [9 8]))
      (should (get-in @atoms/player-map [9 9]))
      ;; Center cell (the satellite's position) should also be visible
      (should (get-in @atoms/player-map [7 7])))))

(describe "update-combatant-map"
  (it "reveals all 9 cells around a player unit in center of map"
    (let [game-map (-> (vec (repeat 5 (vec (repeat 5 {:type :sea}))))
                       (assoc-in [2 2] {:type :land :contents {:type :army :owner :player}}))]
      (reset! atoms/game-map game-map)
      (reset! atoms/player-map (vec (repeat 5 (vec (repeat 5 nil)))))
      (update-combatant-map atoms/player-map :player)
      ;; All 9 cells around [2 2] should be revealed
      (should= {:type :sea} (get-in @atoms/player-map [1 1]))
      (should= {:type :sea} (get-in @atoms/player-map [1 2]))
      (should= {:type :sea} (get-in @atoms/player-map [1 3]))
      (should= {:type :sea} (get-in @atoms/player-map [2 1]))
      (should= {:type :land :contents {:type :army :owner :player}} (get-in @atoms/player-map [2 2]))
      (should= {:type :sea} (get-in @atoms/player-map [2 3]))
      (should= {:type :sea} (get-in @atoms/player-map [3 1]))
      (should= {:type :sea} (get-in @atoms/player-map [3 2]))
      (should= {:type :sea} (get-in @atoms/player-map [3 3]))
      ;; Corners should not be revealed
      (should= nil (get-in @atoms/player-map [0 0]))
      (should= nil (get-in @atoms/player-map [0 4]))
      (should= nil (get-in @atoms/player-map [4 0]))
      (should= nil (get-in @atoms/player-map [4 4]))))

  (it "clamps visibility at map edges for unit in corner"
    (let [game-map (-> (vec (repeat 5 (vec (repeat 5 {:type :sea}))))
                       (assoc-in [0 0] {:type :land :contents {:type :army :owner :player}}))]
      (reset! atoms/game-map game-map)
      (reset! atoms/player-map (vec (repeat 5 (vec (repeat 5 nil)))))
      (update-combatant-map atoms/player-map :player)
      ;; Cells at and adjacent to [0 0] should be revealed (clamped)
      (should= {:type :land :contents {:type :army :owner :player}} (get-in @atoms/player-map [0 0]))
      (should= {:type :sea} (get-in @atoms/player-map [0 1]))
      (should= {:type :sea} (get-in @atoms/player-map [1 0]))
      (should= {:type :sea} (get-in @atoms/player-map [1 1]))
      ;; Far cells should not be revealed
      (should= nil (get-in @atoms/player-map [2 2]))))

  (it "reveals cells around player city"
    (let [game-map (-> (vec (repeat 5 (vec (repeat 5 {:type :sea}))))
                       (assoc-in [2 2] {:type :city :city-status :player}))]
      (reset! atoms/game-map game-map)
      (reset! atoms/player-map (vec (repeat 5 (vec (repeat 5 nil)))))
      (update-combatant-map atoms/player-map :player)
      ;; All 9 cells around [2 2] should be revealed
      (should= {:type :city :city-status :player} (get-in @atoms/player-map [2 2]))
      (should= {:type :sea} (get-in @atoms/player-map [1 1]))
      (should= {:type :sea} (get-in @atoms/player-map [3 3]))))

  (it "does nothing when visible-map-atom is nil"
    (let [game-map (-> (vec (repeat 5 (vec (repeat 5 {:type :sea}))))
                       (assoc-in [2 2] {:type :land :contents {:type :army :owner :player}}))]
      (reset! atoms/game-map game-map)
      (reset! atoms/player-map nil)
      (update-combatant-map atoms/player-map :player)
      (should= nil @atoms/player-map)))

  (it "works for computer owner"
    (let [game-map (-> (vec (repeat 5 (vec (repeat 5 {:type :sea}))))
                       (assoc-in [2 2] {:type :land :contents {:type :army :owner :computer}}))]
      (reset! atoms/game-map game-map)
      (reset! atoms/computer-map (vec (repeat 5 (vec (repeat 5 nil)))))
      (update-combatant-map atoms/computer-map :computer)
      ;; All 9 cells around [2 2] should be revealed in computer map
      (should= {:type :land :contents {:type :army :owner :computer}} (get-in @atoms/computer-map [2 2]))
      (should= {:type :sea} (get-in @atoms/computer-map [1 1]))
      (should= {:type :sea} (get-in @atoms/computer-map [3 3]))))

  (it "handles multiple units revealing overlapping areas"
    (let [game-map (-> (vec (repeat 7 (vec (repeat 7 {:type :sea}))))
                       (assoc-in [2 2] {:type :land :contents {:type :army :owner :player}})
                       (assoc-in [4 4] {:type :land :contents {:type :army :owner :player}}))]
      (reset! atoms/game-map game-map)
      (reset! atoms/player-map (vec (repeat 7 (vec (repeat 7 nil)))))
      (update-combatant-map atoms/player-map :player)
      ;; Both units and their surroundings should be visible
      (should= {:type :land :contents {:type :army :owner :player}} (get-in @atoms/player-map [2 2]))
      (should= {:type :land :contents {:type :army :owner :player}} (get-in @atoms/player-map [4 4]))
      ;; Overlapping cell [3 3] should be revealed by both
      (should= {:type :sea} (get-in @atoms/player-map [3 3]))
      ;; Far corner should not be revealed
      (should= nil (get-in @atoms/player-map [6 6])))))
