(ns empire.movement.waypoint-spec
  (:require
    [empire.atoms :as atoms]
    [empire.config :as config]
    [empire.game-loop :as game-loop]
    [empire.movement.movement :as movement]
    [empire.movement.waypoint :as waypoint]
    [speclj.core :refer :all]))

(describe "waypoints"
  (before
    (reset! atoms/game-map nil)
    (reset! atoms/player-map nil)
    (reset! atoms/destination nil))

  (context "waypoint creation"
    (it "creates a waypoint on an empty land cell"
      (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                            (assoc-in [4 4] {:type :land :contents nil}))]
        (reset! atoms/game-map initial-map)
        (waypoint/create-waypoint [4 4])
        (should-not-be-nil (:waypoint (get-in @atoms/game-map [4 4])))))

    (it "does not create a waypoint on a sea cell"
      (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                            (assoc-in [4 4] {:type :sea :contents nil}))]
        (reset! atoms/game-map initial-map)
        (waypoint/create-waypoint [4 4])
        (should-be-nil (:waypoint (get-in @atoms/game-map [4 4])))))

    (it "does not create a waypoint on a cell with contents"
      (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                            (assoc-in [4 4] {:type :land :contents {:type :army :owner :player}}))]
        (reset! atoms/game-map initial-map)
        (waypoint/create-waypoint [4 4])
        (should-be-nil (:waypoint (get-in @atoms/game-map [4 4])))))

    (it "does not create a waypoint on a city"
      (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                            (assoc-in [4 4] {:type :city :city-status :player :contents nil}))]
        (reset! atoms/game-map initial-map)
        (waypoint/create-waypoint [4 4])
        (should-be-nil (:waypoint (get-in @atoms/game-map [4 4])))))

    (it "removes an existing waypoint when w is pressed again"
      (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                            (assoc-in [4 4] {:type :land :contents nil :waypoint {:marching-orders [5 5]}}))]
        (reset! atoms/game-map initial-map)
        (waypoint/create-waypoint [4 4])
        (should-be-nil (:waypoint (get-in @atoms/game-map [4 4]))))))

  (context "waypoint marching orders"
    (it "sets marching orders on a waypoint using current destination"
      (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                            (assoc-in [4 4] {:type :land :contents nil :waypoint {}}))]
        (reset! atoms/game-map initial-map)
        (reset! atoms/destination [6 6])
        (waypoint/set-waypoint-orders [4 4])
        (should= [6 6] (:marching-orders (:waypoint (get-in @atoms/game-map [4 4]))))))

    (it "does not set orders on a non-waypoint cell"
      (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                            (assoc-in [4 4] {:type :land :contents nil}))]
        (reset! atoms/game-map initial-map)
        (reset! atoms/destination [6 6])
        (should-be-nil (waypoint/set-waypoint-orders [4 4]))))

    (it "sets marching orders on waypoint by direction to map edge"
      (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                            (assoc-in [4 4] {:type :land :contents nil :waypoint {}}))]
        (reset! atoms/game-map initial-map)
        (waypoint/set-waypoint-orders-by-direction [4 4] [0 1])  ; south
        (should= [4 8] (:marching-orders (:waypoint (get-in @atoms/game-map [4 4])))))))

  (context "waypoint display"
    (it "has waypoint-color defined in config as green"
      (should= [0 255 0] config/waypoint-color)))

  (context "army interaction with waypoints"
    (it "army takes marching orders from waypoint without waking"
      (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                            (assoc-in [4 4] {:type :land
                                             :contents {:type :army :mode :moving :owner :player :target [4 5] :steps-remaining 1}})
                            (assoc-in [4 5] {:type :land :waypoint {:marching-orders [4 8]}}))]
        (reset! atoms/game-map initial-map)
        (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
        (game-loop/move-current-unit [4 4])
        (let [moved-unit (:contents (get-in @atoms/game-map [4 5]))]
          (should= :moving (:mode moved-unit))
          (should= [4 8] (:target moved-unit)))))

    (it "army wakes normally if waypoint has no marching orders"
      (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                            (assoc-in [4 4] {:type :land
                                             :contents {:type :army :mode :moving :owner :player :target [4 5] :steps-remaining 1}})
                            (assoc-in [4 5] {:type :land :waypoint {}}))]
        (reset! atoms/game-map initial-map)
        (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
        (game-loop/move-current-unit [4 4])
        (let [moved-unit (:contents (get-in @atoms/game-map [4 5]))]
          (should= :awake (:mode moved-unit)))))

    (it "army continues through multiple waypoints"
      (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                            (assoc-in [4 4] {:type :land
                                             :contents {:type :army :mode :moving :owner :player :target [4 5] :steps-remaining 1}})
                            (assoc-in [4 5] {:type :land :waypoint {:marching-orders [4 6]}})
                            (assoc-in [4 6] {:type :land :waypoint {:marching-orders [4 7]}})
                            (assoc-in [4 7] {:type :land}))]
        (reset! atoms/game-map initial-map)
        (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
        ;; Move to first waypoint - army takes orders to [4 6]
        (game-loop/move-current-unit [4 4])
        (let [unit-at-5 (:contents (get-in @atoms/game-map [4 5]))]
          (should= :moving (:mode unit-at-5))
          (should= [4 6] (:target unit-at-5)))))

    (it "army passing through waypoint takes new orders even if not at target"
      (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                            (assoc-in [4 4] {:type :land
                                             :contents {:type :army :mode :moving :owner :player :target [4 8] :steps-remaining 1}})
                            (assoc-in [4 5] {:type :land :waypoint {:marching-orders [4 2]}})
                            (assoc-in [4 8] {:type :land}))]
        (reset! atoms/game-map initial-map)
        (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
        ;; Army is heading to [4 8] but passes through waypoint at [4 5]
        (game-loop/move-current-unit [4 4])
        (let [moved-unit (:contents (get-in @atoms/game-map [4 5]))]
          ;; Army should take waypoint's orders, redirecting to [4 2]
          (should= :moving (:mode moved-unit))
          (should= [4 2] (:target moved-unit))))))

  (context "fighter interaction with waypoints"
    (it "fighter flies over waypoint with no effect"
      (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                            (assoc-in [4 4] {:type :land
                                             :contents {:type :fighter :mode :moving :owner :player :fuel 20 :target [4 5] :steps-remaining 1 :hits 1}})
                            (assoc-in [4 5] {:type :land :waypoint {:marching-orders [4 8]}}))]
        (reset! atoms/game-map initial-map)
        (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
        (game-loop/move-current-unit [4 4])
        (let [moved-unit (:contents (get-in @atoms/game-map [4 5]))]
          ;; Fighter should wake (reached target) but NOT take waypoint orders
          (should= :awake (:mode moved-unit))
          (should-be-nil (:target moved-unit)))))))
