(ns empire.input-spec
  (:require [speclj.core :refer :all]
            [empire.input :as input]
            [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.game-loop :as game-loop]
            [empire.test-utils :refer [build-test-map get-test-city get-test-unit set-test-unit reset-all-atoms!]]))

(describe "set-city-lookaround"
  (around [it]
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["~O"
                                             "X#"]))
    (it))

  (it "sets marching orders to :lookaround on player city"
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (input/set-city-lookaround city-coords)
      (should= :lookaround (get-in @atoms/game-map (conj city-coords :marching-orders)))))

  (it "returns true when setting lookaround on player city"
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (should (input/set-city-lookaround city-coords))))

  (it "does not set marching orders on computer city"
    (let [city-coords (:pos (get-test-city atoms/game-map "X"))]
      (input/set-city-lookaround city-coords)
      (should-be-nil (get-in @atoms/game-map (conj city-coords :marching-orders)))))

  (it "returns nil when cell is not a player city"
    (let [city-coords (:pos (get-test-city atoms/game-map "X"))]
      (should-be-nil (input/set-city-lookaround city-coords))))

  (it "does not set marching orders on non-city cell"
    (input/set-city-lookaround [0 0])
    (should-be-nil (get-in @atoms/game-map [0 0 :marching-orders])))

  (it "returns nil for non-city cell"
    (should-be-nil (input/set-city-lookaround [0 0]))))

(describe "handle-key :space"
  (before (reset-all-atoms!))
  (it "sets reason to :skipping-this-round on the unit"
    (reset! atoms/game-map (build-test-map ["A"]))
    (set-test-unit atoms/game-map "A" :mode :awake)
    (let [unit-coords (:pos (get-test-unit atoms/game-map "A"))]
      (reset! atoms/cells-needing-attention [unit-coords])
      (reset! atoms/player-items [unit-coords])
      (reset! atoms/waiting-for-input true)
      (input/handle-key :space)
      (let [unit (:contents (get-in @atoms/game-map unit-coords))]
        (should= :skipping-this-round (:reason unit)))))

  (it "burns a full round of fuel for fighters when skipping"
    (let [initial-fuel 20
          fighter-speed (config/unit-speed :fighter)]
      (reset! atoms/game-map (build-test-map ["F"]))
      (set-test-unit atoms/game-map "F" :mode :awake :fuel initial-fuel)
      (let [unit-coords (:pos (get-test-unit atoms/game-map "F"))]
        (reset! atoms/cells-needing-attention [unit-coords])
        (reset! atoms/player-items [unit-coords])
        (reset! atoms/waiting-for-input true)
        (input/handle-key :space)
        (let [unit (:contents (get-in @atoms/game-map unit-coords))]
          (should= (- initial-fuel fighter-speed) (:fuel unit))))))

  (it "fighter crashes when skipping with insufficient fuel"
    (let [_fighter-speed (config/unit-speed :fighter)]
      (reset! atoms/game-map (build-test-map ["F"]))
      (set-test-unit atoms/game-map "F" :mode :awake :fuel 3 :hits 1)
      (let [unit-coords (:pos (get-test-unit atoms/game-map "F"))]
        (reset! atoms/cells-needing-attention [unit-coords])
        (reset! atoms/player-items [unit-coords])
        (reset! atoms/waiting-for-input true)
        (input/handle-key :space)
        (let [unit (:contents (get-in @atoms/game-map unit-coords))]
          (should= 0 (:hits unit))))))

  (it "includes fuel in reason when fighter skips"
    (reset! atoms/game-map (build-test-map ["F"]))
    (set-test-unit atoms/game-map "F" :mode :awake :fuel 20)
    (let [unit-coords (:pos (get-test-unit atoms/game-map "F"))]
      (reset! atoms/cells-needing-attention [unit-coords])
      (reset! atoms/player-items [unit-coords])
      (reset! atoms/waiting-for-input true)
      (input/handle-key :space)
      (let [unit (:contents (get-in @atoms/game-map unit-coords))]
        (should-contain "12" (:reason unit))))))

(describe "handle-key :space on city needing attention"
  (before (reset-all-atoms!))

  (it "clears attention when space is pressed on city without production"
    (reset! atoms/game-map (build-test-map ["O"]))
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (reset! atoms/cells-needing-attention [city-coords])
      (reset! atoms/waiting-for-input true)
      (input/handle-key :space)
      (should= [] @atoms/cells-needing-attention)
      (should= false @atoms/waiting-for-input)))

  (it "does not add production when space is pressed"
    (reset! atoms/game-map (build-test-map ["O"]))
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (reset! atoms/cells-needing-attention [city-coords])
      (reset! atoms/waiting-for-input true)
      (input/handle-key :space)
      (should-be-nil (get @atoms/production city-coords))))

  (it "removes city from player-items when space is pressed"
    (reset! atoms/game-map (build-test-map ["O"]))
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (reset! atoms/cells-needing-attention [city-coords])
      (reset! atoms/player-items [city-coords])
      (reset! atoms/waiting-for-input true)
      (input/handle-key :space)
      (should= [] @atoms/player-items)))

  (it "does nothing for computer cities"
    (reset! atoms/game-map (build-test-map ["X"]))
    (let [city-coords (:pos (get-test-city atoms/game-map "X"))]
      (reset! atoms/cells-needing-attention [city-coords])
      (reset! atoms/waiting-for-input true)
      (input/handle-key :space)
      ;; Should still be waiting - nothing happened
      (should= true @atoms/waiting-for-input))))

(describe "handle-key :l on army aboard transport"
  (before (reset-all-atoms!))

  (it "keeps transport in player-items when more awake armies remain"
    (reset! atoms/game-map (build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----T----"
                                             "----#----"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (set-test-unit atoms/game-map "T" :mode :sentry :hits 1 :army-count 3 :awake-armies 3)
    (let [transport-coords (:pos (get-test-unit atoms/game-map "T"))
          land-coords [(inc (first transport-coords)) (second transport-coords)]]
      (reset! atoms/cells-needing-attention [transport-coords])
      (reset! atoms/player-items [transport-coords])
      (reset! atoms/waiting-for-input true)
      (input/handle-key :l)
      ;; Transport should still be in player-items so remaining armies get attention
      (should (some #{transport-coords} @atoms/player-items))
      ;; Disembarked army should be at front of player-items
      (should= land-coords (first @atoms/player-items))
      ;; Transport should still have 2 awake armies
      (let [transport (:contents (get-in @atoms/game-map transport-coords))]
        (should= 2 (:awake-armies transport))))))

(describe "key-down :P"
  (before
    (reset-all-atoms!)
    (reset! atoms/paused false)
    (reset! atoms/pause-requested false)
    (reset! atoms/backtick-pressed false))

  (it "toggles pause when P is pressed"
    (input/key-down :P)
    (should @atoms/pause-requested)))

(describe "key-down :space when paused"
  (before
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["#"]))
    (reset! atoms/paused true)
    (reset! atoms/pause-requested false)
    (reset! atoms/backtick-pressed false)
    (reset! atoms/player-items [])
    (reset! atoms/computer-items [])
    (reset! atoms/round-number 5))

  (it "starts new round when both item lists are empty"
    (input/key-down :space)
    (should= 6 @atoms/round-number))

  (it "sets pause-requested to pause after round"
    (input/key-down :space)
    (should= true @atoms/pause-requested))

  (it "unpauses to allow game loop to process"
    (input/key-down :space)
    (should= false @atoms/paused)))
