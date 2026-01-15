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

(describe "wake-airport-fighters"
  (it "wakes fighters in player city airports"
    (reset! atoms/game-map [[{:type :city :city-status :player :fighter-count 3}]])
    (game-loop/wake-airport-fighters)
    (should= 3 (:awake-fighters (get-in @atoms/game-map [0 0]))))

  (it "does not wake resting fighters"
    (reset! atoms/game-map [[{:type :city :city-status :player :fighter-count 3 :resting-fighters 1}]])
    (game-loop/wake-airport-fighters)
    (should= 2 (:awake-fighters (get-in @atoms/game-map [0 0])))
    (should= 0 (:resting-fighters (get-in @atoms/game-map [0 0]))))

  (it "does not wake sleeping fighters"
    (reset! atoms/game-map [[{:type :city :city-status :player :fighter-count 3 :sleeping-fighters 2}]])
    (game-loop/wake-airport-fighters)
    (should= 1 (:awake-fighters (get-in @atoms/game-map [0 0]))))

  (it "ignores computer cities"
    (reset! atoms/game-map [[{:type :city :city-status :computer :fighter-count 3}]])
    (game-loop/wake-airport-fighters)
    (should-be-nil (:awake-fighters (get-in @atoms/game-map [0 0])))))

(describe "consume-sentry-fighter-fuel"
  (it "decrements fuel for sentry fighters"
    (reset! atoms/game-map [[{:type :land :contents {:type :fighter :mode :sentry :fuel 20}}]])
    (game-loop/consume-sentry-fighter-fuel)
    (should= 19 (:fuel (:contents (get-in @atoms/game-map [0 0])))))

  (it "wakes fighter when fuel reaches 1"
    (reset! atoms/game-map [[{:type :land :contents {:type :fighter :mode :sentry :fuel 2 :owner :player}}]])
    (game-loop/consume-sentry-fighter-fuel)
    (let [unit (:contents (get-in @atoms/game-map [0 0]))]
      (should= 1 (:fuel unit))
      (should= :awake (:mode unit))
      (should= :fighter-out-of-fuel (:reason unit))))

  (it "sets hits to 0 when fuel reaches 0"
    (reset! atoms/game-map [[{:type :land :contents {:type :fighter :mode :sentry :fuel 1 :owner :player}}]])
    (game-loop/consume-sentry-fighter-fuel)
    (should= 0 (:hits (:contents (get-in @atoms/game-map [0 0])))))

  (it "does not affect moving fighters"
    (reset! atoms/game-map [[{:type :land :contents {:type :fighter :mode :moving :fuel 20}}]])
    (game-loop/consume-sentry-fighter-fuel)
    (should= 20 (:fuel (:contents (get-in @atoms/game-map [0 0]))))))

(describe "start-new-round"
  (before
    (reset! atoms/game-map [[{:type :city :city-status :player}]])
    (reset! atoms/player-map [[{}]])
    (reset! atoms/computer-map [[{}]])
    (reset! atoms/production {})
    (reset! atoms/round-number 0)
    (reset! atoms/player-items [])
    (reset! atoms/waiting-for-input true)
    (reset! atoms/message "old message")
    (reset! atoms/cells-needing-attention [[0 0]]))

  (it "increments round number"
    (game-loop/start-new-round)
    (should= 1 @atoms/round-number))

  (it "builds player items list"
    (game-loop/start-new-round)
    (should-contain [0 0] @atoms/player-items))

  (it "resets waiting-for-input to false"
    (game-loop/start-new-round)
    (should= false @atoms/waiting-for-input))

  (it "clears message"
    (game-loop/start-new-round)
    (should= "" @atoms/message))

  (it "clears cells-needing-attention"
    (game-loop/start-new-round)
    (should= [] @atoms/cells-needing-attention)))

(describe "advance-game"
  (it "starts new round when player-items is empty"
    (reset! atoms/game-map [[{:type :city :city-status :player}]])
    (reset! atoms/player-map [[{}]])
    (reset! atoms/computer-map [[{}]])
    (reset! atoms/production {})
    (reset! atoms/player-items [])
    (reset! atoms/round-number 0)
    (game-loop/advance-game)
    (should= 1 @atoms/round-number))

  (it "sets waiting-for-input when item needs attention"
    (reset! atoms/game-map [[{:type :city :city-status :player}]])
    (reset! atoms/production {})
    (reset! atoms/player-items [[0 0]])
    (reset! atoms/waiting-for-input false)
    (reset! atoms/message "")
    (game-loop/advance-game)
    (should= true @atoms/waiting-for-input))

  (it "does nothing when waiting for input"
    (reset! atoms/game-map [[{:type :land :contents {:type :army :mode :awake :owner :player}}]])
    (reset! atoms/production {})
    (reset! atoms/player-items [[0 0]])
    (reset! atoms/waiting-for-input true)
    (reset! atoms/round-number 5)
    (game-loop/advance-game)
    (should= 5 @atoms/round-number)
    (should= [[0 0]] @atoms/player-items))

  (it "moves to next item when unit does not need attention"
    (reset! atoms/game-map [[{:type :land :contents {:type :army :mode :sentry :owner :player}}]])
    (reset! atoms/production {})
    (reset! atoms/player-items [[0 0]])
    (reset! atoms/waiting-for-input false)
    (game-loop/advance-game)
    (should= [] (vec @atoms/player-items))))

(describe "update-map"
  (before
    (reset! atoms/game-map [[{:type :city :city-status :player}]])
    (reset! atoms/player-map [[{}]])
    (reset! atoms/computer-map [[{}]])
    (reset! atoms/production {})
    (reset! atoms/player-items [])
    (reset! atoms/round-number 0))

  (it "calls advance-game which starts new round when empty"
    (game-loop/update-map)
    (should= 1 @atoms/round-number)))

(describe "move-explore-unit"
  (it "delegates to movement/move-explore-unit"
    (reset! atoms/game-map [[{:type :land :contents {:type :army :mode :explore :owner :player :visited #{[0 0]}}}
                             {:type :land}]])
    (reset! atoms/player-map [[{} {}]])
    (let [result (game-loop/move-explore-unit [0 0])]
      ;; Should return new coords if still exploring
      (should (or (nil? result) (vector? result))))))

(describe "auto-launch-fighter from airport"
  (it "launches fighter when city has flight-path and awake fighters"
    (reset! atoms/game-map [[{:type :city :city-status :player :flight-path [0 1] :awake-fighters 1 :fighter-count 1}
                             {:type :land}]])
    (reset! atoms/player-map [[{} {}]])
    (reset! atoms/production {})
    (reset! atoms/player-items [[0 0]])
    (reset! atoms/waiting-for-input false)
    (game-loop/advance-game)
    ;; Fighter should have been launched and added to front of player-items
    (let [first-item (first @atoms/player-items)]
      (should= [0 0] first-item)))

  (it "launches fighter from carrier with flight-path"
    (reset! atoms/game-map [[{:type :sea :contents {:type :carrier :owner :player :mode :sentry :flight-path [0 1] :awake-fighters 1 :fighter-count 1}}
                             {:type :sea}]])
    (reset! atoms/player-map [[{} {}]])
    (reset! atoms/production {})
    (reset! atoms/player-items [[0 0]])
    (reset! atoms/waiting-for-input false)
    (game-loop/advance-game)
    ;; Should have processed the carrier
    (should (seq @atoms/player-items))))

(describe "auto-disembark-army"
  (it "disembarks army when transport has marching-orders and awake armies"
    (reset! atoms/game-map [[{:type :sea :contents {:type :transport :owner :player :mode :sentry :marching-orders [1 0] :awake-armies 1 :army-count 1}}
                             {:type :land}]])
    (reset! atoms/player-map [[{} {}]])
    (reset! atoms/production {})
    (reset! atoms/player-items [[0 0]])
    (reset! atoms/waiting-for-input false)
    (game-loop/advance-game)
    ;; Army should have been disembarked
    (let [land-cell (get-in @atoms/game-map [0 1])]
      (should= :army (:type (:contents land-cell))))))

(describe "advance-game with explore mode"
  (it "processes exploring unit"
    (reset! atoms/game-map [[{:type :land :contents {:type :army :mode :explore :owner :player :visited #{[0 0]}}}
                             {:type :land}]])
    (reset! atoms/player-map [[{} {}]])
    (reset! atoms/production {})
    (reset! atoms/player-items [[0 0]])
    (reset! atoms/waiting-for-input false)
    (game-loop/advance-game)
    ;; Should have moved the exploring unit
    (should-not= [[0 0]] @atoms/player-items)))
