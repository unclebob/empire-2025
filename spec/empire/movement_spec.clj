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

      (it "launch-fighter-from-carrier wakes carrier when last awake fighter launches"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :sea :contents {:type :carrier :mode :sentry :owner :player :hits 8 :fighter-count 1 :awake-fighters 1}})
                              (assoc-in [4 5] {:type :sea}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (launch-fighter-from-carrier [4 4] [4 6])
          (let [carrier (:contents (get-in @atoms/game-map [4 4]))]
            (should= :awake (:mode carrier))
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
          ;; wake-after-move takes unit, final-pos, and current-map (atom)
          (let [cell (get-in @atoms/game-map [4 4])
                unit (:contents cell)
                result (wake-after-move unit [4 5] atoms/game-map)]
            (should= 0 (:hits result))))))

    (describe "fighter landing at city"
      (it "fighter lands at target city and becomes sleeping fighter"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land :contents {:type :fighter :mode :moving :owner :player :target [4 5] :fuel 10 :steps-remaining 1 :hits 1}})
                              (assoc-in [4 5] {:type :city :city-status :player :fighter-count 0}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (game-loop/move-current-unit [4 4])
          (let [city (get-in @atoms/game-map [4 5])]
            (should= 1 (:fighter-count city))
            (should= 1 (:sleeping-fighters city 0)))))

      (it "fighter lands at non-target city and becomes resting fighter"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land :contents {:type :fighter :mode :moving :owner :player :target [4 6] :fuel 10 :steps-remaining 1 :hits 1}})
                              (assoc-in [4 5] {:type :city :city-status :player :fighter-count 0}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (game-loop/move-current-unit [4 4])
          (let [city (get-in @atoms/game-map [4 5])]
            (should= 1 (:fighter-count city))
            (should= 1 (:resting-fighters city 0))))))

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

  (it "wakes sleeping fighters in city airport"
    (swap! atoms/game-map assoc-in [3 4]
           {:type :city :city-status :player :sleeping-fighters 3 :awake-fighters 1})
    (should (wake-at [3 4]))
    (should= 0 (get-in @atoms/game-map [3 4 :sleeping-fighters]))
    (should= 4 (get-in @atoms/game-map [3 4 :awake-fighters])))

  (it "returns nil for empty cell"
    (should-not (wake-at [3 4])))

  (it "returns nil for enemy city"
    (swap! atoms/game-map assoc-in [3 4]
           {:type :city :city-status :computer})
    (should-not (wake-at [3 4]))))
