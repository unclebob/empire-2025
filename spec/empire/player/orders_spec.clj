(ns empire.player.orders-spec
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.player.orders :as orders]
            [empire.config.core :as config]
            [empire.movement.waypoint :as waypoint]
            [empire.test.utils :refer [build-test-map reset-all-atoms! get-test-city set-test-world!]]))

(describe "own-city-at"
  (before (reset-all-atoms!))

  (it "claims a free city for the player"
    (set-test-world! (build-test-map ["+~"
                                            "~~"]))
    (should (orders/own-city-at [0 0]))
    (should= :player (get-in (test-utils/read-test-state :game-map) [0 0 :city-status])))

  (it "claims a computer city for the player"
    (set-test-world! (build-test-map ["X~"
                                            "~~"]))
    (should (orders/own-city-at [0 0]))
    (should= :player (get-in (test-utils/read-test-state :game-map) [0 0 :city-status])))

  (it "returns nil for a land cell"
    (set-test-world! (build-test-map ["#~"
                                            "~~"]))
    (should-be-nil (orders/own-city-at [0 0])))

  (it "returns nil for a sea cell"
    (set-test-world! (build-test-map ["~~"
                                            "~~"]))
    (should-be-nil (orders/own-city-at [0 0])))

  (it "returns true for an already-player city"
    (set-test-world! (build-test-map ["O~"
                                            "~~"]))
    (should (orders/own-city-at [0 0]))
    (should= :player (get-in (test-utils/read-test-state :game-map) [0 0 :city-status]))))

(describe "set-city-lookaround"
  (before (reset-all-atoms!))

  (it "sets lookaround marching orders on a player city"
    (set-test-world! (build-test-map ["O~"
                                            "~~"]))
    (should (orders/set-city-lookaround [0 0]))
    (should= :lookaround (get-in (test-utils/read-test-state :game-map) [0 0 :marching-orders])))

  (it "sets a turn message on success"
    (set-test-world! (build-test-map ["O~"
                                            "~~"]))
    (orders/set-city-lookaround [0 0])
    (should= "Marching orders set to lookaround" (test-utils/read-test-state :turn-message)))

  (it "returns nil for a computer city"
    (set-test-world! (build-test-map ["X~"
                                            "~~"]))
    (should-be-nil (orders/set-city-lookaround [0 0])))

  (it "returns nil for a free city"
    (set-test-world! (build-test-map ["+~"
                                            "~~"]))
    (should-be-nil (orders/set-city-lookaround [0 0])))

  (it "returns nil for a non-city cell"
    (set-test-world! (build-test-map ["#~"
                                            "~~"]))
    (should-be-nil (orders/set-city-lookaround [0 0]))))

(describe "set-destination-at"
  (before (reset-all-atoms!))

  (it "sets the destination atom and returns true"
    (set-test-world! (build-test-map ["~~"
                                            "~~"]))
    (should (orders/set-destination-at [3 7]))
    (should= [3 7] (test-utils/read-test-state :destination)))

  (it "overwrites a previously set destination"
    (test-utils/set-test-state! :destination [1 1])
    (orders/set-destination-at [5 10])
    (should= [5 10] (test-utils/read-test-state :destination))))

(describe "set-marching-orders-at"
  (before (reset-all-atoms!))

  (it "sets marching orders on a player city when destination exists"
    (set-test-world! (build-test-map ["O~"
                                            "~~"]))
    (test-utils/set-test-state! :destination [5 10])
    (should (orders/set-marching-orders-at [0 0]))
    (should= [5 10] (get-in (test-utils/read-test-state :game-map) [0 0 :marching-orders]))
    (should-be-nil (test-utils/read-test-state :destination)))

  (it "sets a turn message with destination coordinates"
    (set-test-world! (build-test-map ["O~"
                                            "~~"]))
    (test-utils/set-test-state! :destination [5 10])
    (orders/set-marching-orders-at [0 0])
    (should= "Marching orders set to 5,10" (test-utils/read-test-state :turn-message)))

  (it "sets marching orders on a player transport when destination exists"
    (set-test-world! (build-test-map ["~T"
                                            "~~"]))
    (test-utils/set-test-state! :destination [3 4])
    (should (orders/set-marching-orders-at [1 0]))
    (should= [3 4] (get-in (test-utils/read-test-state :game-map) [1 0 :contents :marching-orders]))
    (should-be-nil (test-utils/read-test-state :destination)))

  (it "delegates to waypoint/set-waypoint-orders for a waypoint cell"
    (set-test-world! (build-test-map ["*~"
                                            "~~"]))
    (test-utils/set-test-state! :destination [2 3])
    (let [called (atom false)]
      (with-redefs [waypoint/set-waypoint-orders (fn [_] (reset! called true))]
        (should (orders/set-marching-orders-at [0 0]))
        (should @called))))

  (it "returns nil when no destination is set"
    (set-test-world! (build-test-map ["O~"
                                            "~~"]))
    (test-utils/set-test-state! :destination nil)
    (should-be-nil (orders/set-marching-orders-at [0 0])))

  (it "returns nil for a computer city"
    (set-test-world! (build-test-map ["X~"
                                            "~~"]))
    (test-utils/set-test-state! :destination [5 10])
    (should-be-nil (orders/set-marching-orders-at [0 0])))

  (it "returns nil for a regular land cell"
    (set-test-world! (build-test-map ["#~"
                                            "~~"]))
    (test-utils/set-test-state! :destination [5 10])
    (should-be-nil (orders/set-marching-orders-at [0 0]))))

(describe "set-flight-path-at"
  (before (reset-all-atoms!))

  (it "sets flight path on a player city when destination exists"
    (set-test-world! (build-test-map ["O~"
                                            "~~"]))
    (test-utils/set-test-state! :destination [8 12])
    (should (orders/set-flight-path-at [0 0]))
    (should= [1 1] (get-in (test-utils/read-test-state :game-map) [0 0 :flight-path]))
    (should-be-nil (test-utils/read-test-state :destination)))

  (it "sets a turn message with flight path coordinates"
    (set-test-world! (build-test-map ["O~"
                                            "~~"]))
    (test-utils/set-test-state! :destination [8 12])
    (orders/set-flight-path-at [0 0])
    (should= "Flight path set to 1,1" (test-utils/read-test-state :turn-message)))

  (it "sets flight path on a player carrier when destination exists"
    (set-test-world! (build-test-map ["~C"
                                            "~~"]))
    (test-utils/set-test-state! :destination [6 9])
    (should (orders/set-flight-path-at [1 0]))
    (should= [1 1] (get-in (test-utils/read-test-state :game-map) [1 0 :contents :flight-path]))
    (should-be-nil (test-utils/read-test-state :destination)))

  (it "returns nil when no destination is set"
    (set-test-world! (build-test-map ["O~"
                                            "~~"]))
    (test-utils/set-test-state! :destination nil)
    (should-be-nil (orders/set-flight-path-at [0 0])))

  (it "returns nil for a non-matching cell like land"
    (set-test-world! (build-test-map ["#~"
                                            "~~"]))
    (test-utils/set-test-state! :destination [5 10])
    (should-be-nil (orders/set-flight-path-at [0 0])))

  (it "returns nil for a computer carrier"
    (set-test-world! (build-test-map ["~c"
                                            "~~"]))
    (test-utils/set-test-state! :destination [5 10])
    (should-be-nil (orders/set-flight-path-at [1 0])))

  (it "clamps flight path destination to right edge"
    (set-test-world! (build-test-map ["###"
                                      "#O#"
                                      "###"]))
    (test-utils/set-test-state! :destination [99 1])
    (should (orders/set-flight-path-at [1 1]))
    (should= [2 1] (get-in (test-utils/read-test-state :game-map) [1 1 :flight-path])))

  (it "clamps flight path destination to left edge"
    (set-test-world! (build-test-map ["###"
                                      "#O#"
                                      "###"]))
    (test-utils/set-test-state! :destination [-99 1])
    (should (orders/set-flight-path-at [1 1]))
    (should= [0 1] (get-in (test-utils/read-test-state :game-map) [1 1 :flight-path])))

  (it "clamps flight path destination to top edge"
    (set-test-world! (build-test-map ["###"
                                      "#O#"
                                      "###"]))
    (test-utils/set-test-state! :destination [1 -99])
    (should (orders/set-flight-path-at [1 1]))
    (should= [1 0] (get-in (test-utils/read-test-state :game-map) [1 1 :flight-path])))

  (it "clamps flight path destination to bottom edge"
    (set-test-world! (build-test-map ["###"
                                      "#O#"
                                      "###"]))
    (test-utils/set-test-state! :destination [1 99])
    (should (orders/set-flight-path-at [1 1]))
    (should= [1 2] (get-in (test-utils/read-test-state :game-map) [1 1 :flight-path]))))

(describe "set-waypoint-at"
  (before (reset-all-atoms!))

  (it "creates a waypoint on empty land and returns true"
    (set-test-world! (build-test-map ["#~"
                                            "~~"]))
    (should (orders/set-waypoint-at [0 0]))
    (should (:waypoint (get-in (test-utils/read-test-state :game-map) [0 0]))))

  (it "sets placed message when waypoint is created"
    (set-test-world! (build-test-map ["#~"
                                            "~~"]))
    (orders/set-waypoint-at [0 0])
    (should= "Waypoint placed at 0,0" (test-utils/read-test-state :turn-message)))

  (it "toggles waypoint off and sets removed message"
    (set-test-world! (build-test-map ["*~"
                                            "~~"]))
    (orders/set-waypoint-at [0 0])
    (should= "Waypoint removed from 0,0" (test-utils/read-test-state :turn-message))
    (should-not (:waypoint (get-in (test-utils/read-test-state :game-map) [0 0]))))

  (it "returns nil when create-waypoint fails"
    (set-test-world! (build-test-map ["~~"
                                            "~~"]))
    (should-be-nil (orders/set-waypoint-at [0 0]))))

(describe "set-city-marching-orders-by-direction-at"
  (before (reset-all-atoms!))

  (it "sets marching orders to the east edge for direction :d"
    (set-test-world! (build-test-map ["O~~~#"
                                            "~~~~~"
                                            "~~~~~"]))
    (should (orders/set-city-marching-orders-by-direction-at [0 0] :d))
    (should= [4 0] (get-in (test-utils/read-test-state :game-map) [0 0 :marching-orders])))

  (it "sets marching orders to the south edge for direction :x"
    (set-test-world! (build-test-map ["O~~"
                                            "~~~"
                                            "~~~"
                                            "~~~"]))
    (should (orders/set-city-marching-orders-by-direction-at [0 0] :x))
    (should= [0 3] (get-in (test-utils/read-test-state :game-map) [0 0 :marching-orders])))

  (it "sets marching orders to the southeast corner for direction :c"
    (set-test-world! (build-test-map ["O~~"
                                            "~~~"
                                            "~~~"]))
    (should (orders/set-city-marching-orders-by-direction-at [0 0] :c))
    (should= [2 2] (get-in (test-utils/read-test-state :game-map) [0 0 :marching-orders])))

  (it "sets marching orders to the northwest corner for direction :q"
    (set-test-world! (build-test-map ["~~~"
                                            "~~~"
                                            "~~O"]))
    (should (orders/set-city-marching-orders-by-direction-at [2 2] :q))
    (should= [0 0] (get-in (test-utils/read-test-state :game-map) [2 2 :marching-orders])))

  (it "sets a turn message with target coordinates"
    (set-test-world! (build-test-map ["O~~"
                                            "~~~"
                                            "~~~"]))
    (orders/set-city-marching-orders-by-direction-at [0 0] :d)
    (should= "Marching orders set to 2,0" (test-utils/read-test-state :turn-message)))

  (it "returns nil for a non-player city"
    (set-test-world! (build-test-map ["X~~"
                                            "~~~"]))
    (should-be-nil (orders/set-city-marching-orders-by-direction-at [0 0] :d)))

  (it "returns nil for an invalid key that is not a direction"
    (set-test-world! (build-test-map ["O~~"
                                            "~~~"]))
    (should-be-nil (orders/set-city-marching-orders-by-direction-at [0 0] :j)))

  (it "delegates to waypoint/set-waypoint-orders-by-direction for waypoint cell"
    (set-test-world! (build-test-map ["*~~"
                                            "~~~"
                                            "~~~"]))
    (let [called-args (atom nil)]
      (with-redefs [waypoint/set-waypoint-orders-by-direction
                    (fn [pos dir]
                      (reset! called-args {:pos pos :dir dir})
                      true)]
        (orders/set-city-marching-orders-by-direction-at [0 0] :d)
        (should= {:pos [0 0] :dir [1 0]} @called-args)))))

(run-specs)
