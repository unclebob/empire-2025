(ns empire.ui.util.input.actions-attention-spec
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.ui.util.input.actions :as actions]
            [empire.ui.util.input.actions.movement :as actions-movement]
            [empire.ui.util.input.dispatch :as dispatch]
            [empire.config.core :as config]
            [empire.game.loop.core :as game-loop]
            [empire.game-mechanics.services.combat :as combat]
            [empire.player.orders :as orders]
            [empire.player.production :as production]
            [empire.test.utils :refer [build-test-map get-test-city get-test-unit set-test-unit reset-all-atoms!
                                       set-test-world! update-test-world!]]))
(describe "set-city-lookaround"
  (around [it]
    (reset-all-atoms!)
    (set-test-world! (build-test-map ["~O"
                                             "X#"]))
    (it))

  (it "sets marching orders to :lookaround on player city"
    (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "O"))]
      (orders/set-city-lookaround city-coords)
      (should= :lookaround (get-in (test-utils/read-test-state :game-map) (conj city-coords :marching-orders)))))

  (it "returns true when setting lookaround on player city"
    (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "O"))]
      (should (orders/set-city-lookaround city-coords))))

  (it "does not set marching orders on computer city"
    (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "X"))]
      (orders/set-city-lookaround city-coords)
      (should-be-nil (get-in (test-utils/read-test-state :game-map) (conj city-coords :marching-orders)))))

  (it "returns nil when cell is not a player city"
    (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "X"))]
      (should-be-nil (orders/set-city-lookaround city-coords))))

  (it "does not set marching orders on non-city cell"
    (orders/set-city-lookaround [0 0])
    (should-be-nil (get-in (test-utils/read-test-state :game-map) [0 0 :marching-orders])))

  (it "returns nil for non-city cell"
    (should-be-nil (orders/set-city-lookaround [0 0]))))

(describe "handle-key :space"
  (before (reset-all-atoms!))
  (it "sets reason to :skipping-this-round on the unit"
    (set-test-world! (build-test-map ["A"]))
    (set-test-unit (test-utils/game-map-atom) "A" :mode :awake)
    (let [unit-coords (:pos (get-test-unit (test-utils/game-map-atom) "A"))]
      (test-utils/set-test-state! :cells-needing-attention [unit-coords])
      (test-utils/set-test-state! :player-items [unit-coords])
      (test-utils/set-test-state! :waiting-for-input true)
      (actions/handle-key :space)
      (let [unit (:contents (get-in (test-utils/read-test-state :game-map) unit-coords))]
        (should= :skipping-this-round (:reason unit)))))

  (it "burns a full round of fuel for fighters when skipping"
    (let [initial-fuel 20
          fighter-speed (config/unit-speed :fighter)]
      (set-test-world! (build-test-map ["F"]))
      (set-test-unit (test-utils/game-map-atom) "F" :mode :awake :fuel initial-fuel)
      (let [unit-coords (:pos (get-test-unit (test-utils/game-map-atom) "F"))]
        (test-utils/set-test-state! :cells-needing-attention [unit-coords])
        (test-utils/set-test-state! :player-items [unit-coords])
        (test-utils/set-test-state! :waiting-for-input true)
        (actions/handle-key :space)
        (let [unit (:contents (get-in (test-utils/read-test-state :game-map) unit-coords))]
          (should= (- initial-fuel fighter-speed) (:fuel unit))))))

  (it "fighter crashes when skipping with insufficient fuel"
    (let [_fighter-speed (config/unit-speed :fighter)]
      (set-test-world! (build-test-map ["F"]))
      (set-test-unit (test-utils/game-map-atom) "F" :mode :awake :fuel 3 :hits 1)
      (let [unit-coords (:pos (get-test-unit (test-utils/game-map-atom) "F"))]
        (test-utils/set-test-state! :cells-needing-attention [unit-coords])
        (test-utils/set-test-state! :player-items [unit-coords])
        (test-utils/set-test-state! :waiting-for-input true)
        (actions/handle-key :space)
        (let [unit (:contents (get-in (test-utils/read-test-state :game-map) unit-coords))]
          (should= 0 (:hits unit))))))

  (it "includes fuel in reason when fighter skips"
    (set-test-world! (build-test-map ["F"]))
    (set-test-unit (test-utils/game-map-atom) "F" :mode :awake :fuel 20)
    (let [unit-coords (:pos (get-test-unit (test-utils/game-map-atom) "F"))]
      (test-utils/set-test-state! :cells-needing-attention [unit-coords])
      (test-utils/set-test-state! :player-items [unit-coords])
      (test-utils/set-test-state! :waiting-for-input true)
      (actions/handle-key :space)
      (let [unit (:contents (get-in (test-utils/read-test-state :game-map) unit-coords))]
        (should-contain "12" (:reason unit))))))

(describe "handle-key :space on city needing attention"
  (before (reset-all-atoms!))

  (it "clears attention when space is pressed on city without production"
    (set-test-world! (build-test-map ["O"]))
    (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "O"))]
      (test-utils/set-test-state! :cells-needing-attention [city-coords])
      (test-utils/set-test-state! :waiting-for-input true)
      (actions/handle-key :space)
      (should= [] (test-utils/read-test-state :cells-needing-attention))
      (should= false (test-utils/read-test-state :waiting-for-input))))

  (it "does not add production when space is pressed"
    (set-test-world! (build-test-map ["O"]))
    (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "O"))]
      (test-utils/set-test-state! :cells-needing-attention [city-coords])
      (test-utils/set-test-state! :waiting-for-input true)
      (actions/handle-key :space)
      (should-be-nil (get (test-utils/read-test-state :production) city-coords))))

  (it "removes city from player-items when space is pressed"
    (set-test-world! (build-test-map ["O"]))
    (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "O"))]
      (test-utils/set-test-state! :cells-needing-attention [city-coords])
      (test-utils/set-test-state! :player-items [city-coords])
      (test-utils/set-test-state! :waiting-for-input true)
      (actions/handle-key :space)
      (should= [] (test-utils/read-test-state :player-items))))

  (it "does nothing for computer cities"
    (set-test-world! (build-test-map ["X"]))
    (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "X"))]
      (test-utils/set-test-state! :cells-needing-attention [city-coords])
      (test-utils/set-test-state! :waiting-for-input true)
      (actions/handle-key :space)
      ;; Should still be waiting - nothing happened
      (should= true (test-utils/read-test-state :waiting-for-input)))))

(describe "handle-key :l (look-around)"
  (before (reset-all-atoms!))

  (it "sets army to explore mode"
    (set-test-world! (build-test-map ["###"
                                             "#A#"
                                             "###"]))
    (set-test-unit (test-utils/game-map-atom) "A" :mode :awake)
    (let [coords (:pos (get-test-unit (test-utils/game-map-atom) "A"))]
      (test-utils/set-test-state! :cells-needing-attention [coords])
      (test-utils/set-test-state! :player-items [coords])
      (test-utils/set-test-state! :waiting-for-input true)
      (actions/handle-key :l)
      (let [unit (:contents (get-in (test-utils/read-test-state :game-map) coords))]
        (should= :explore (:mode unit)))))

  (it "sets transport to coastline-follow mode when near coast"
    (set-test-world! (build-test-map ["~#~"
                                             "~T~"
                                             "~~~"]))
    (set-test-unit (test-utils/game-map-atom) "T" :mode :awake :hits 1 :army-count 0)
    (let [coords (:pos (get-test-unit (test-utils/game-map-atom) "T"))]
      (test-utils/set-test-state! :cells-needing-attention [coords])
      (test-utils/set-test-state! :player-items [coords])
      (test-utils/set-test-state! :waiting-for-input true)
      (actions/handle-key :l)
      (let [unit (:contents (get-in (test-utils/read-test-state :game-map) coords))]
        (should= :coastline-follow (:mode unit)))))

  (it "shows rejection reason for transport not near coast"
    (set-test-world! (build-test-map ["~~~~~"
                                             "~~~~~"
                                             "~~T~~"
                                             "~~~~~"
                                             "~~~~~"]))
    (set-test-unit (test-utils/game-map-atom) "T" :mode :awake :hits 1 :army-count 0)
    (let [coords (:pos (get-test-unit (test-utils/game-map-atom) "T"))]
      (test-utils/set-test-state! :cells-needing-attention [coords])
      (test-utils/set-test-state! :player-items [coords])
      (test-utils/set-test-state! :waiting-for-input true)
      (test-utils/set-test-state! :attention-message "")
      (actions/handle-key :l)
      (should-contain "coast" (test-utils/read-test-state :warning-message)))))
