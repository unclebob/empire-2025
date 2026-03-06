(ns empire.movement.waypoint-spec
  (:require [empire.test.utils :as test-utils]
    [empire.config.core :as config]
    [empire.game-loop.core :as game-loop]
    [empire.movement.api :as movement]
    [empire.movement.waypoint :as waypoint]
    [empire.test.utils :refer [build-test-map set-test-unit reset-all-atoms! set-test-player-map! set-test-computer-map! set-test-world!]]
    [speclj.core :refer :all]))

(describe "waypoints"
  (before
    (reset-all-atoms!)
    (set-test-world! nil)
    (set-test-player-map! nil)
    (test-utils/set-test-state! :destination nil))

  (context "waypoint creation"
    (it "creates a waypoint on an empty land cell"
      (let [initial-map (assoc-in (build-test-map ["#"])
                                  [0 0 :contents] nil)]
        (set-test-world! initial-map)
        (should= true (waypoint/create-waypoint [0 0]))
        (should-not-be-nil (:waypoint (get-in (test-utils/read-test-state :game-map) [0 0])))))

    (it "does not create a waypoint on a sea cell"
      (let [initial-map (assoc-in (build-test-map ["~"])
                                  [0 0 :contents] nil)]
        (set-test-world! initial-map)
        (waypoint/create-waypoint [0 0])
        (should-be-nil (:waypoint (get-in (test-utils/read-test-state :game-map) [0 0])))))

    (it "does not create a waypoint on a cell with contents"
      (set-test-world! (build-test-map ["A"]))
      (waypoint/create-waypoint [0 0])
      (should-be-nil (:waypoint (get-in (test-utils/read-test-state :game-map) [0 0]))))

    (it "does not create a waypoint on a city"
      (let [initial-map (assoc-in (build-test-map ["O"])
                                  [0 0 :contents] nil)]
        (set-test-world! initial-map)
        (waypoint/create-waypoint [0 0])
        (should-be-nil (:waypoint (get-in (test-utils/read-test-state :game-map) [0 0])))))

    (it "removes an existing waypoint when w is pressed again"
      (let [initial-map (-> (build-test-map ["#"])
                            (assoc-in [0 0 :contents] nil)
                            (assoc-in [0 0 :waypoint] {:marching-orders [5 5]}))]
        (set-test-world! initial-map)
        (should= true (waypoint/create-waypoint [0 0]))
        (should-be-nil (:waypoint (get-in (test-utils/read-test-state :game-map) [0 0]))))))

  (context "waypoint marching orders"
    (it "sets marching orders on a waypoint using current destination"
      (let [initial-map (-> (build-test-map ["#"])
                            (assoc-in [0 0 :contents] nil)
                            (assoc-in [0 0 :waypoint] {}))]
        (set-test-world! initial-map)
        (test-utils/set-test-state! :destination [6 6])
        (should= true (waypoint/set-waypoint-orders [0 0]))
        (should= [6 6] (:marching-orders (:waypoint (get-in (test-utils/read-test-state :game-map) [0 0]))))))

    (it "does not set orders on a non-waypoint cell"
      (let [initial-map (assoc-in (build-test-map ["#"])
                                  [0 0 :contents] nil)]
        (set-test-world! initial-map)
        (test-utils/set-test-state! :destination [6 6])
        (should-be-nil (waypoint/set-waypoint-orders [0 0]))))

    (it "sets marching orders on waypoint by direction to map edge"
      (let [initial-map (-> (build-test-map ["-----"
                                              "-#---"
                                              "-----"])
                            (assoc-in [1 1 :contents] nil)
                            (assoc-in [1 1 :waypoint] {}))]
        (set-test-world! initial-map)
        (should= true (waypoint/set-waypoint-orders-by-direction [1 1] [1 0]))  ; south
        (should= [4 1] (:marching-orders (:waypoint (get-in (test-utils/read-test-state :game-map) [1 1]))))))

    (it "sets marching orders by direction north to map edge"
      (let [initial-map (-> (build-test-map ["-----"
                                              "-#---"
                                              "-----"])
                            (assoc-in [1 1 :contents] nil)
                            (assoc-in [1 1 :waypoint] {}))]
        (set-test-world! initial-map)
        (waypoint/set-waypoint-orders-by-direction [1 1] [-1 0])
        (should= [0 1] (:marching-orders (:waypoint (get-in (test-utils/read-test-state :game-map) [1 1]))))))

    (it "sets marching orders by direction east to map edge"
      (let [initial-map (-> (build-test-map ["-----"
                                              "-#---"
                                              "-----"])
                            (assoc-in [1 1 :contents] nil)
                            (assoc-in [1 1 :waypoint] {}))]
        (set-test-world! initial-map)
        (waypoint/set-waypoint-orders-by-direction [1 1] [0 1])
        (should= [1 2] (:marching-orders (:waypoint (get-in (test-utils/read-test-state :game-map) [1 1]))))))

    (it "sets marching orders by direction west to map edge"
      (let [initial-map (-> (build-test-map ["-----"
                                              "-#---"
                                              "-----"])
                            (assoc-in [1 1 :contents] nil)
                            (assoc-in [1 1 :waypoint] {}))]
        (set-test-world! initial-map)
        (waypoint/set-waypoint-orders-by-direction [1 1] [0 -1])
        (should= [1 0] (:marching-orders (:waypoint (get-in (test-utils/read-test-state :game-map) [1 1])))))))

  (context "waypoint display"
    (it "has waypoint-color defined in config as green"
      (should= [0 255 0] config/waypoint-color)))

  (context "army interaction with waypoints"
    (it "army takes marching orders from waypoint without waking"
      (set-test-world! (-> (build-test-map ["A#"])
                           (assoc-in [1 0 :waypoint] {:marching-orders [4 8]})))
      (set-test-unit (test-utils/game-map-atom) "A" :mode :moving :target [1 0] :steps-remaining 1)
      (set-test-player-map! (build-test-map ["--"]))
      (game-loop/move-current-unit [0 0])
      (let [moved-unit (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))]
        (should= :moving (:mode moved-unit))
        (should= [4 8] (:target moved-unit))))

    (it "army wakes normally if waypoint has no marching orders"
      (set-test-world! (-> (build-test-map ["A#"])
                           (assoc-in [1 0 :waypoint] {})))
      (set-test-unit (test-utils/game-map-atom) "A" :mode :moving :target [1 0] :steps-remaining 1)
      (set-test-player-map! (build-test-map ["--"]))
      (game-loop/move-current-unit [0 0])
      (let [moved-unit (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))]
        (should= :awake (:mode moved-unit))))

    (it "army continues through multiple waypoints"
      (set-test-world! (-> (build-test-map ["A###"])
                           (assoc-in [1 0 :waypoint] {:marching-orders [2 0]})
                           (assoc-in [2 0 :waypoint] {:marching-orders [3 0]})))
      (set-test-unit (test-utils/game-map-atom) "A" :mode :moving :target [1 0] :steps-remaining 1)
      (set-test-player-map! (build-test-map ["----"]))
      ;; Move to first waypoint - army takes orders to [2 0]
      (game-loop/move-current-unit [0 0])
      (let [unit-at-1 (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))]
        (should= :moving (:mode unit-at-1))
        (should= [2 0] (:target unit-at-1))))

    (it "army passing through waypoint takes new orders even if not at target"
      (set-test-world! (-> (build-test-map ["A#-#"])
                           (assoc-in [1 0 :waypoint] {:marching-orders [4 2]})))
      (set-test-unit (test-utils/game-map-atom) "A" :mode :moving :target [3 0] :steps-remaining 1)
      (set-test-player-map! (build-test-map ["----"]))
      ;; Army is heading to [3 0] but passes through waypoint at [1 0]
      (game-loop/move-current-unit [0 0])
      (let [moved-unit (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))]
        ;; Army should take waypoint's orders, redirecting to [4 2]
        (should= :moving (:mode moved-unit))
        (should= [4 2] (:target moved-unit)))))

  (context "fighter interaction with waypoints"
    (it "fighter flies over waypoint with no effect"
      (set-test-world! (-> (build-test-map ["F#"])
                           (assoc-in [1 0 :waypoint] {:marching-orders [4 8]})))
      (set-test-unit (test-utils/game-map-atom) "F" :mode :moving :fuel 20 :target [1 0] :steps-remaining 1 :hits 1)
      (set-test-player-map! (build-test-map ["--"]))
      (game-loop/move-current-unit [0 0])
      (let [moved-unit (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))]
        ;; Fighter should wake (reached target) but NOT take waypoint orders
        (should= :awake (:mode moved-unit))
        (should-be-nil (:target moved-unit))))))
