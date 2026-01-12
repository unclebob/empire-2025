(ns empire.movement-spec
  (:require
    [empire.atoms :as atoms]
    [empire.config :as config]
    [empire.map :as map]
    [empire.movement :refer :all]
    [speclj.core :refer :all]))

(defn move-until-done
  "Helper to move a unit until it stops (returns nil)."
  [coords]
  (loop [current coords]
    (when-let [next-coords (map/move-current-unit current)]
      (recur next-coords))))

(describe "movement"
  (context "move-current-unit"
    (it "does nothing if no unit"
      (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                            (assoc-in [4 4] {:type :land :contents nil})
                            (assoc-in [4 5] {:type :land}))]
        (reset! atoms/game-map initial-map)
        (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
        (map/move-current-unit [4 4])
        (should= 2 (count (filter (complement nil?) (flatten @atoms/game-map))))))

    (context "single moves that awaken the unit"
      (it "moves a unit to its target and sets mode to awake"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land
                                               :contents {:type :army :mode :moving :owner :player :target [4 5] :steps-remaining 1}})
                              (assoc-in [4 5] {:type :land}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (map/move-current-unit [4 4])
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
          (map/move-current-unit [4 4])
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
          (map/move-current-unit [4 4])
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
          (map/move-current-unit [4 4])
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
          (map/move-current-unit [4 4])
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
          (map/move-current-unit [4 4])
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
          (map/move-current-unit [4 4])
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
          (map/move-current-unit [4 4])
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
          (map/move-current-unit [4 4])
          (should= {:type :land :contents {:type :army :mode :awake :owner :player :reason :cant-move-into-water :steps-remaining 1}} (get-in @atoms/game-map [4 4]))
          (should= {:type :sea} (get-in @atoms/game-map [4 5]))))

      (it "wakes up a unit if the next move would be into a friendly city"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land
                                               :contents {:type :army :mode :moving :owner :player :target [4 5] :steps-remaining 1}})
                              (assoc-in [4 5] {:type :city :city-status :player}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (map/move-current-unit [4 4])
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
          (map/move-current-unit [4 4])
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
          (map/move-current-unit [4 4])
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
          (map/move-current-unit [4 4])
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
          (map/move-current-unit [4 4])
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
          (map/move-current-unit [4 4])
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
          (map/move-current-unit [4 4])
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
          (map/move-current-unit [4 4])
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
          (map/move-current-unit [4 4])
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
          (map/move-current-unit [4 4])
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
          (map/move-current-unit [4 4])
          ;; After first move, unit at [4 5], still moving
          (should= {:type :land} (get-in @atoms/game-map [4 4]))
          (should= {:type :land :contents {:type :army :mode :moving :owner :player :target [4 6] :steps-remaining 0}} (get-in @atoms/game-map [4 5]))
          (should= {:type :land} (get-in @atoms/game-map [4 6]))
          ;; Give the unit another step and call again
          (swap! atoms/game-map assoc-in [4 5 :contents :steps-remaining] 1)
          (map/move-current-unit [4 5])
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
          (map/move-current-unit [4 4])
          (should= {:type :land} (get-in @atoms/game-map [4 4]))
          (should= {:type :land :contents {:type :fighter :mode :awake :owner :player :fuel 9 :steps-remaining 0}} (get-in @atoms/game-map [4 5]))))

      (it "fighter wakes when fuel reaches 0"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land :contents {:type :fighter :mode :moving :owner :player :target [4 5] :fuel 1 :steps-remaining 1}})
                              (assoc-in [4 5] {:type :land}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (map/move-current-unit [4 4])
          (should= {:type :land} (get-in @atoms/game-map [4 4]))
          (should= {:type :land :contents {:type :fighter :mode :awake :owner :player :fuel 0 :reason :fighter-out-of-fuel :steps-remaining 0}} (get-in @atoms/game-map [4 5]))))

      (it "fighter crashes when trying to move with 0 fuel"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land :contents {:type :fighter :mode :moving :owner :player :target [4 5] :fuel 0 :steps-remaining 1}})
                              (assoc-in [4 5] {:type :land}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (map/move-current-unit [4 4])
          (should= {:type :land} (get-in @atoms/game-map [4 4]))
          (should= {:type :land} (get-in @atoms/game-map [4 5]))
          (should= 2 (count (filter (complement nil?) (flatten @atoms/game-map))))))

      (it "fighter lands in city, refuels, and awakens"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land :contents {:type :fighter :mode :moving :owner :player :target [4 5] :fuel 5 :steps-remaining 1}})
                              (assoc-in [4 5] {:type :city :city-status :player}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (map/move-current-unit [4 4])
          (should= {:type :land} (get-in @atoms/game-map [4 4]))
          (let [city-cell (get-in @atoms/game-map [4 5])
                fighter (first (:airport city-cell))]
            (should= :city (:type city-cell))
            (should= :player (:city-status city-cell))
            (should= :fighter (:type fighter))
            (should= :awake (:mode fighter))
            (should= config/fighter-fuel (:fuel fighter))
            (should= :fighter-landed-and-refueled (:reason fighter)))))


      (it "fighter safely lands at friendly city"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land :contents {:type :fighter :mode :moving :owner :player :target [4 5] :fuel 10 :steps-remaining 1}})
                              (assoc-in [4 5] {:type :city :city-status :player}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (reset! atoms/line3-message "")
          (map/move-current-unit [4 4])
          (let [city-cell (get-in @atoms/game-map [4 5])
                fighter (first (:airport city-cell))]
            (should= :fighter (:type fighter))
            (should= :awake (:mode fighter))
            (should= :fighter-landed-and-refueled (:reason fighter))
            (should-not-be-nil fighter))
          (should= "" @atoms/line3-message)))

      (it "fighter wakes before flying over free city"
        (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :land :contents {:type :fighter :mode :moving :owner :player :target [4 6] :fuel 10 :steps-remaining 1}})
                              (assoc-in [4 5] {:type :city :city-status :free})
                              (assoc-in [4 6] {:type :land}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (map/move-current-unit [4 4])
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
          (map/move-current-unit [4 4])
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
          (map/move-current-unit [4 4])
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
          (map/move-current-unit [4 4])
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
          (map/move-current-unit [4 4])
          ;; Fighter should wake at target, not due to bingo (fuel 10 > 8 = 25% of 32)
          (let [fighter (:contents (get-in @atoms/game-map [4 5]))]
            (should= :fighter (:type fighter))
            (should= :awake (:mode fighter))
            (should= nil (:reason fighter)))))
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
            (should= 2 (count (:armies transport)))
            (should (every? #(= :sentry (:mode %)) (:armies transport))))
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
            (should= 0 (count (or (:armies transport) []))))
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
            (should= 1 (count (:armies transport))))))

      (it "wake-armies-on-transport wakes all armies and sets transport to sentry"
        (let [armies [{:type :army :mode :sentry :owner :player :hits 1}
                      {:type :army :mode :sentry :owner :player :hits 1}]
              initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :sea :contents {:type :transport :mode :awake :owner :player :hits 1 :armies armies :reason :transport-at-beach}})
                              (assoc-in [5 4] {:type :land}))]
          (reset! atoms/game-map initial-map)
          (wake-armies-on-transport [4 4])
          (let [transport (:contents (get-in @atoms/game-map [4 4]))]
            (should= :sentry (:mode transport))
            (should= nil (:reason transport))
            (should= 2 (count (:armies transport)))
            (should (every? #(= :awake (:mode %)) (:armies transport))))))

      (it "sleep-armies-on-transport puts armies to sleep and wakes transport"
        (let [armies [{:type :army :mode :awake :owner :player :hits 1}
                      {:type :army :mode :awake :owner :player :hits 1}]
              initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :sea :contents {:type :transport :mode :sentry :owner :player :hits 1 :armies armies}})
                              (assoc-in [5 4] {:type :land}))]
          (reset! atoms/game-map initial-map)
          (sleep-armies-on-transport [4 4])
          (let [transport (:contents (get-in @atoms/game-map [4 4]))]
            (should= :awake (:mode transport))
            (should= nil (:reason transport))
            (should= 2 (count (:armies transport)))
            (should (every? #(= :sentry (:mode %)) (:armies transport))))))

      (it "disembark-army-from-transport removes only first awake army"
        (let [armies [{:type :army :mode :awake :owner :player :hits 1}
                      {:type :army :mode :awake :owner :player :hits 1}
                      {:type :army :mode :awake :owner :player :hits 1}]
              initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :sea :contents {:type :transport :mode :sentry :owner :player :hits 1 :armies armies}})
                              (assoc-in [5 4] {:type :land}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (disembark-army-from-transport [4 4] [5 4])
          (let [transport (:contents (get-in @atoms/game-map [4 4]))
                disembarked (:contents (get-in @atoms/game-map [5 4]))]
            (should= 2 (count (:armies transport)))
            (should (every? #(= :awake (:mode %)) (:armies transport)))
            (should= :army (:type disembarked))
            (should= :awake (:mode disembarked)))))

      (it "disembark-army-from-transport wakes transport when last army disembarks"
        (let [armies [{:type :army :mode :awake :owner :player :hits 1}]
              initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :sea :contents {:type :transport :mode :sentry :owner :player :hits 1 :armies armies}})
                              (assoc-in [5 4] {:type :land}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (disembark-army-from-transport [4 4] [5 4])
          (let [transport (:contents (get-in @atoms/game-map [4 4]))]
            (should= :awake (:mode transport))
            (should= 0 (count (or (:armies transport) []))))))

      (it "disembark-army-from-transport wakes transport when no more awake armies remain"
        (let [armies [{:type :army :mode :awake :owner :player :hits 1}
                      {:type :army :mode :sentry :owner :player :hits 1}]
              initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :sea :contents {:type :transport :mode :sentry :owner :player :hits 1 :armies armies}})
                              (assoc-in [5 4] {:type :land}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (disembark-army-from-transport [4 4] [5 4])
          (let [transport (:contents (get-in @atoms/game-map [4 4]))]
            (should= :awake (:mode transport))
            (should= 1 (count (:armies transport)))
            (should= :sentry (:mode (first (:armies transport)))))))

      (it "transport wakes up when reaching beach with armies"
        (let [armies [{:type :army :mode :sentry :owner :player :hits 1}]
              initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                              (assoc-in [4 4] {:type :sea :contents {:type :transport :mode :moving :owner :player :hits 1 :armies armies :target [4 5] :steps-remaining 1}})
                              (assoc-in [4 5] {:type :sea})
                              (assoc-in [5 5] {:type :land}))]
          (reset! atoms/game-map initial-map)
          (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
          (map/move-current-unit [4 4])
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
          (map/move-current-unit [4 4])
          (let [transport (:contents (get-in @atoms/game-map [4 5]))]
            (should= :awake (:mode transport))
            (should= nil (:reason transport)))))

      (it "get-active-unit returns first awake army aboard transport"
        (let [armies [{:type :army :mode :sentry :owner :player :hits 1}
                      {:type :army :mode :awake :owner :player :hits 1}
                      {:type :army :mode :awake :owner :player :hits 1}]
              cell {:type :sea :contents {:type :transport :mode :sentry :owner :player :armies armies}}]
          (let [active (get-active-unit cell)]
            (should= :army (:type active))
            (should= :awake (:mode active)))))

      (it "get-active-unit returns transport when no awake armies"
        (let [armies [{:type :army :mode :sentry :owner :player :hits 1}]
              cell {:type :sea :contents {:type :transport :mode :awake :owner :player :armies armies}}]
          (let [active (get-active-unit cell)]
            (should= :transport (:type active))
            (should= :awake (:mode active)))))

      (it "is-army-aboard-transport? returns true for army in transport"
        (let [army {:type :army :mode :awake :owner :player :hits 1}
              armies [army {:type :army :mode :sentry :owner :player :hits 1}]
              cell {:type :sea :contents {:type :transport :mode :sentry :owner :player :armies armies}}]
          (should= true (is-army-aboard-transport? cell army))))

      (it "is-army-aboard-transport? returns falsy for army not in transport"
        (let [army {:type :army :mode :awake :owner :player :hits 1}
              armies [{:type :army :mode :sentry :owner :player :hits 1}]
              cell {:type :sea :contents {:type :transport :mode :sentry :owner :player :armies armies}}]
          (should-not (is-army-aboard-transport? cell army))))
      )
    )
  )
