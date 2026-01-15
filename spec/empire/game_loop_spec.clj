(ns empire.game-loop-spec
  (:require [speclj.core :refer :all]
            [empire.game-loop :as game-loop]
            [empire.atoms :as atoms]
            [empire.config :as config]))

(describe "item-processed"
  (it "resets waiting-for-input to false"
    (reset! atoms/waiting-for-input true)
    (game-loop/item-processed)
    (should= false @atoms/waiting-for-input))

  (it "clears message"
    (reset! atoms/message "test message")
    (game-loop/item-processed)
    (should= "" @atoms/message))

  (it "clears cells-needing-attention"
    (reset! atoms/cells-needing-attention [[1 2] [3 4]])
    (game-loop/item-processed)
    (should= [] @atoms/cells-needing-attention)))

(describe "build-player-items"
  (before
    (reset! atoms/game-map [[{:type :land} {:type :city :city-status :player}]
                            [{:type :land :contents {:type :army :owner :player}} {:type :city :city-status :computer}]]))

  (it "returns player city coordinates"
    (let [items (game-loop/build-player-items)]
      (should-contain [0 1] items)))

  (it "returns player unit coordinates"
    (let [items (game-loop/build-player-items)]
      (should-contain [1 0] items)))

  (it "does not return computer cities"
    (let [items (game-loop/build-player-items)]
      (should-not-contain [1 1] items)))

  (it "does not return empty land"
    (let [items (game-loop/build-player-items)]
      (should-not-contain [0 0] items))))

(describe "remove-dead-units"
  (before
    (reset! atoms/game-map [[{:type :land :contents {:type :army :owner :player :hits 0}}
                             {:type :land :contents {:type :fighter :owner :player :hits 1}}]
                            [{:type :land} {:type :land}]])
    (reset! atoms/player-map [[{} {}] [{} {}]]))

  (it "removes units with hits <= 0"
    (game-loop/remove-dead-units)
    (should-be-nil (:contents (get-in @atoms/game-map [0 0]))))

  (it "keeps units with hits > 0"
    (game-loop/remove-dead-units)
    (should= {:type :fighter :owner :player :hits 1} (:contents (get-in @atoms/game-map [0 1])))))

(describe "reset-steps-remaining"
  (before
    (reset! atoms/game-map [[{:type :land :contents {:type :army :owner :player}}
                             {:type :land :contents {:type :fighter :owner :player}}]
                            [{:type :land :contents {:type :army :owner :computer}} {:type :land}]]))

  (it "sets steps-remaining for player army"
    (game-loop/reset-steps-remaining)
    (should= (config/unit-speed :army) (:steps-remaining (:contents (get-in @atoms/game-map [0 0])))))

  (it "sets steps-remaining for player fighter"
    (game-loop/reset-steps-remaining)
    (should= (config/unit-speed :fighter) (:steps-remaining (:contents (get-in @atoms/game-map [0 1])))))

  (it "does not set steps-remaining for computer units"
    (game-loop/reset-steps-remaining)
    (should-be-nil (:steps-remaining (:contents (get-in @atoms/game-map [1 0]))))))
