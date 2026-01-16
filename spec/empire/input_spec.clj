(ns empire.input-spec
  (:require [speclj.core :refer :all]
            [empire.input :as input]
            [empire.atoms :as atoms]
            [empire.config :as config]))

(describe "set-city-lookaround"
  (around [it]
    (reset! atoms/game-map [[{:type :sea} {:type :city :city-status :player}]
                            [{:type :city :city-status :computer} {:type :land}]])
    (it))

  (it "sets marching orders to :lookaround on player city"
    (input/set-city-lookaround [0 1])
    (should= :lookaround (get-in @atoms/game-map [0 1 :marching-orders])))

  (it "returns true when setting lookaround on player city"
    (should (input/set-city-lookaround [0 1])))

  (it "does not set marching orders on computer city"
    (input/set-city-lookaround [1 0])
    (should-be-nil (get-in @atoms/game-map [1 0 :marching-orders])))

  (it "returns nil when cell is not a player city"
    (should-be-nil (input/set-city-lookaround [1 0])))

  (it "does not set marching orders on non-city cell"
    (input/set-city-lookaround [0 0])
    (should-be-nil (get-in @atoms/game-map [0 0 :marching-orders])))

  (it "returns nil for non-city cell"
    (should-be-nil (input/set-city-lookaround [0 0]))))

(describe "handle-key :space"
  (it "sets reason to :skipping-this-round on the unit"
    (reset! atoms/game-map [[{:type :land :contents {:type :army :mode :awake :owner :player}}]])
    (reset! atoms/cells-needing-attention [[0 0]])
    (reset! atoms/player-items [[0 0]])
    (reset! atoms/waiting-for-input true)
    (input/handle-key :space)
    (let [unit (:contents (get-in @atoms/game-map [0 0]))]
      (should= :skipping-this-round (:reason unit))))

  (it "burns a full round of fuel for fighters when skipping"
    (let [initial-fuel 20
          fighter-speed (config/unit-speed :fighter)]
      (reset! atoms/game-map [[{:type :land :contents {:type :fighter :mode :awake :owner :player :fuel initial-fuel}}]])
      (reset! atoms/cells-needing-attention [[0 0]])
      (reset! atoms/player-items [[0 0]])
      (reset! atoms/waiting-for-input true)
      (input/handle-key :space)
      (let [unit (:contents (get-in @atoms/game-map [0 0]))]
        (should= (- initial-fuel fighter-speed) (:fuel unit)))))

  (it "fighter crashes when skipping with insufficient fuel"
    (let [fighter-speed (config/unit-speed :fighter)]
      (reset! atoms/game-map [[{:type :land :contents {:type :fighter :mode :awake :owner :player :fuel 3 :hits 1}}]])
      (reset! atoms/cells-needing-attention [[0 0]])
      (reset! atoms/player-items [[0 0]])
      (reset! atoms/waiting-for-input true)
      (input/handle-key :space)
      (let [unit (:contents (get-in @atoms/game-map [0 0]))]
        (should= 0 (:hits unit)))))

  (it "includes fuel in reason when fighter skips"
    (reset! atoms/game-map [[{:type :land :contents {:type :fighter :mode :awake :owner :player :fuel 20}}]])
    (reset! atoms/cells-needing-attention [[0 0]])
    (reset! atoms/player-items [[0 0]])
    (reset! atoms/waiting-for-input true)
    (input/handle-key :space)
    (let [unit (:contents (get-in @atoms/game-map [0 0]))]
      (should-contain "12" (:reason unit)))))
