(ns empire.map-lifecycle-spec
  (:require [empire.player.attention :as attention]
            [empire.game.loop.core :as game-loop]
            [empire.test.utils :as test-utils]
            [empire.config.core :as config]
            [empire.test.utils :refer [build-test-map set-test-unit reset-all-atoms! set-test-world! set-test-player-map!]]
            [speclj.core :refer :all]))

(describe "build-player-items"
  (before (reset-all-atoms!))

  (it "returns coordinates of player cities"
    (set-test-world! (build-test-map ["OX"]))
    (should= [[0 0]] (vec (game-loop/build-player-items))))

  (it "returns coordinates of player units"
    (set-test-world! (build-test-map ["Aa"]))
    (should= [[0 0]] (vec (game-loop/build-player-items))))

  (it "returns both cities and units"
    (set-test-world! (build-test-map ["OA"
                                      "#X"]))
    (should= [[0 0] [1 0]] (vec (game-loop/build-player-items))))

  (it "returns empty list when no player items"
    (set-test-world! (build-test-map ["~#"]))
    (should= [] (vec (game-loop/build-player-items)))))

(describe "item-processed"
  (before (reset-all-atoms!))

  (it "resets waiting-for-input to false"
    (test-utils/set-test-state! :waiting-for-input true)
    (game-loop/item-processed)
    (should= false (test-utils/read-test-state :waiting-for-input)))

  (it "preserves attention-message"
    (test-utils/set-test-state! :attention-message "Some message")
    (game-loop/item-processed)
    (should= "Some message" (test-utils/read-test-state :attention-message)))

  (it "clears cells-needing-attention"
    (test-utils/set-test-state! :cells-needing-attention [[0 0] [1 1]])
    (game-loop/item-processed)
    (should= [] (test-utils/read-test-state :cells-needing-attention))))

(describe "cells-needing-attention"
  (before (reset-all-atoms!))

  (it "returns empty list when no player cells"
    (set-test-player-map! (build-test-map ["~#"
                                           "X#"]))
    (test-utils/set-test-state! :production {})
    (should= [] (attention/cells-needing-attention)))

  (it "returns coordinates of awake units"
    (set-test-player-map! (build-test-map ["A#"
                                           "##"]))
    (set-test-unit (test-utils/player-map-atom) "A" :mode :awake)
    (test-utils/set-test-state! :production {})
    (should= [[0 0]] (attention/cells-needing-attention)))

  (it "returns coordinates of cities with no production"
    (set-test-player-map! (build-test-map ["#O"
                                           "##"]))
    (test-utils/set-test-state! :production {})
    (should= [[1 0]] (attention/cells-needing-attention)))

  (it "excludes cities with production"
    (set-test-player-map! (build-test-map ["O#"]))
    (test-utils/set-test-state! :production {[0 0] {:item :army :remaining-rounds 5}})
    (should= [] (attention/cells-needing-attention)))

  (it "returns multiple coordinates"
    (set-test-player-map! (build-test-map ["AO"
                                           "##"]))
    (set-test-unit (test-utils/player-map-atom) "A" :mode :awake)
    (test-utils/set-test-state! :production {})
    (should= [[0 0] [1 0]] (attention/cells-needing-attention))))

(describe "remove-dead-units"
  (before (reset-all-atoms!))

  (it "removes units with hits at or below zero"
    (set-test-world! (build-test-map ["AFA"
                                      "###"]))
    (set-test-unit (test-utils/game-map-atom) "A" :hits 0)
    (set-test-unit (test-utils/game-map-atom) "F" :hits 1)
    (set-test-unit (test-utils/game-map-atom) "A2" :hits -1)
    (game-loop/remove-dead-units)
    (should= {:type :land} (get-in (test-utils/read-test-state :game-map) [0 0]))
    (should= {:type :land :contents {:type :fighter :hits 1 :owner :player :fuel 32}}
             (get-in (test-utils/read-test-state :game-map) [1 0]))
    (should= {:type :land} (get-in (test-utils/read-test-state :game-map) [2 0]))))

(describe "reset-steps-remaining"
  (before (reset-all-atoms!))

  (it "initializes steps-remaining for player units based on unit speed"
    (set-test-world! (build-test-map ["AF"
                                      "a#"]))
    (game-loop/reset-steps-remaining)
    (should= (config/unit-speed :army) (:steps-remaining (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))))
    (should= (config/unit-speed :fighter) (:steps-remaining (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))))
    (should= nil (:steps-remaining (:contents (get-in (test-utils/read-test-state :game-map) [0 1])))))

  (it "overwrites existing steps-remaining values"
    (set-test-world! (build-test-map ["F"]))
    (set-test-unit (test-utils/game-map-atom) "F" :steps-remaining 2)
    (game-loop/reset-steps-remaining)
    (should= (config/unit-speed :fighter) (:steps-remaining (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))))))
