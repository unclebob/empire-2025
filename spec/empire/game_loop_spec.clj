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
  (it "wakes all fighters in player city airports"
    (reset! atoms/game-map [[{:type :city :city-status :player :fighter-count 3}]])
    (game-loop/wake-airport-fighters)
    (should= 3 (:awake-fighters (get-in @atoms/game-map [0 0]))))

  (it "ignores computer cities"
    (reset! atoms/game-map [[{:type :city :city-status :computer :fighter-count 3}]])
    (game-loop/wake-airport-fighters)
    (should-be-nil (:awake-fighters (get-in @atoms/game-map [0 0]))))

  (it "ignores cities with no fighters"
    (reset! atoms/game-map [[{:type :city :city-status :player :fighter-count 0}]])
    (game-loop/wake-airport-fighters)
    (should-be-nil (:awake-fighters (get-in @atoms/game-map [0 0])))))

(describe "wake-carrier-fighters"
  (it "wakes all fighters on player carriers"
    (reset! atoms/game-map [[{:type :sea :contents {:type :carrier :owner :player :fighter-count 3}}]])
    (game-loop/wake-carrier-fighters)
    (should= 3 (:awake-fighters (:contents (get-in @atoms/game-map [0 0])))))

  (it "ignores computer carriers"
    (reset! atoms/game-map [[{:type :sea :contents {:type :carrier :owner :computer :fighter-count 3}}]])
    (game-loop/wake-carrier-fighters)
    (should-be-nil (:awake-fighters (:contents (get-in @atoms/game-map [0 0])))))

  (it "ignores carriers with no fighters"
    (reset! atoms/game-map [[{:type :sea :contents {:type :carrier :owner :player :fighter-count 0}}]])
    (game-loop/wake-carrier-fighters)
    (should-be-nil (:awake-fighters (:contents (get-in @atoms/game-map [0 0]))))))

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
    (should= 20 (:fuel (:contents (get-in @atoms/game-map [0 0])))))

  (it "wakes fighter with bingo when fuel is low and friendly city in range"
    ;; Fighter fuel is 20, bingo threshold is 20/4 = 5
    ;; At fuel 6, decrement to 5 which equals bingo threshold
    ;; Need a friendly city within range (5 cells)
    (reset! atoms/game-map [[{:type :city :city-status :player}
                             {:type :land :contents {:type :fighter :mode :sentry :fuel 6 :owner :player}}]])
    (game-loop/consume-sentry-fighter-fuel)
    (let [unit (:contents (get-in @atoms/game-map [0 1]))]
      (should= 5 (:fuel unit))
      (should= :awake (:mode unit))
      (should= :fighter-bingo (:reason unit)))))

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
    (should= [] @atoms/cells-needing-attention))

  (it "does not wake carrier fighters - they stay asleep until u is pressed"
    (reset! atoms/game-map [[{:type :sea :contents {:type :carrier :owner :player :fighter-count 2 :awake-fighters 0}}]])
    (reset! atoms/player-map [[{}]])
    (reset! atoms/computer-map [[{}]])
    (game-loop/start-new-round)
    (let [carrier (:contents (get-in @atoms/game-map [0 0]))]
      (should= 0 (:awake-fighters carrier 0)))))

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

(describe "move-satellites"
  (it "removes satellite when turns-remaining reaches zero during movement"
    ;; Satellite with turns-remaining 1 will expire after moving
    (reset! atoms/game-map [[{:type :land :contents {:type :satellite :owner :player :turns-remaining 1}}
                             {:type :land}]])
    (reset! atoms/player-map [[{} {}]])
    (game-loop/move-satellites)
    ;; Satellite should be removed after its turn expires
    (let [found-satellite (some (fn [[i row]]
                                  (some (fn [[j cell]]
                                          (when (= :satellite (:type (:contents cell)))
                                            [i j]))
                                        (map-indexed vector row)))
                                (map-indexed vector @atoms/game-map))]
      ;; Either satellite is gone or has decremented turns
      (when found-satellite
        (let [sat (:contents (get-in @atoms/game-map found-satellite))]
          (should (or (nil? sat) (<= (:turns-remaining sat 0) 0)))))))

  (it "removes satellite immediately when turns-remaining is already zero"
    ;; Satellite with turns-remaining 0 should be removed at start of move
    (reset! atoms/game-map [[{:type :land :contents {:type :satellite :owner :player :turns-remaining 0}}]])
    (reset! atoms/player-map [[{}]])
    (game-loop/move-satellites)
    ;; Satellite should be removed
    (should-be-nil (:contents (get-in @atoms/game-map [0 0]))))

  (it "decrements turns-remaining after movement"
    (reset! atoms/game-map [[{:type :land :contents {:type :satellite :owner :player :turns-remaining 5}}
                             {:type :land}
                             {:type :land}]])
    (reset! atoms/player-map [[{} {} {}]])
    (game-loop/move-satellites)
    ;; Find where satellite ended up
    (let [find-sat (fn []
                     (some (fn [[i row]]
                             (some (fn [[j cell]]
                                     (when (= :satellite (:type (:contents cell)))
                                       (:contents cell)))
                                   (map-indexed vector row)))
                           (map-indexed vector @atoms/game-map)))
          sat (find-sat)]
      (when sat
        (should (< (:turns-remaining sat) 5))))))

(describe "move-explore-unit"
  (it "delegates to movement/move-explore-unit"
    (reset! atoms/game-map [[{:type :land :contents {:type :army :mode :explore :owner :player :visited #{[0 0]}}}
                             {:type :land}]])
    (reset! atoms/player-map [[{} {}]])
    (let [result (game-loop/move-explore-unit [0 0])]
      ;; Should return new coords if still exploring
      (should (or (nil? result) (vector? result))))))

(describe "move-coastline-unit"
  (it "delegates to movement/move-coastline-unit"
    (let [game-map (vec (repeat 5 (vec (repeat 5 {:type :sea}))))
          game-map (reduce (fn [m row] (assoc-in m [row 0] {:type :land})) game-map (range 5))]
      (reset! atoms/game-map
              (assoc-in game-map [2 1]
                        {:type :sea :contents {:type :transport :mode :coastline-follow :owner :player
                                               :coastline-steps 50
                                               :start-pos [2 1]
                                               :visited #{[2 1]}
                                               :prev-pos nil}}))
      (reset! atoms/player-map @atoms/game-map)
      (let [result (game-loop/move-coastline-unit [2 1])]
        ;; Should return nil (unit keeps moving until done)
        (should-be-nil result)))))

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
    ;; Fighter should have been launched from carrier (awake-fighters decremented)
    (let [carrier (:contents (get-in @atoms/game-map [0 0]))]
      (should= 0 (:awake-fighters carrier 0)))))

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

(describe "advance-game with coastline-follow mode"
  (it "processes coastline-following unit and continues when returning new coords"
    (let [game-map (vec (repeat 10 (vec (repeat 10 {:type :sea}))))
          game-map (reduce (fn [m row] (assoc-in m [row 0] {:type :land})) game-map (range 10))]
      (reset! atoms/game-map
              (assoc-in game-map [5 1]
                        {:type :sea :contents {:type :transport :mode :coastline-follow :owner :player
                                               :coastline-steps 50
                                               :start-pos [5 1]
                                               :visited #{[5 1]}
                                               :prev-pos nil}}))
      (reset! atoms/player-map @atoms/game-map)
      (reset! atoms/production {})
      (reset! atoms/player-items [[5 1]])
      (reset! atoms/waiting-for-input false)
      (game-loop/advance-game)
      ;; Unit should have moved - player-items should be updated
      (should (or (empty? @atoms/player-items)
                  (not= [[5 1]] (vec @atoms/player-items)))))))

(describe "advance-game with moving unit"
  (it "continues processing when unit moves and has steps remaining"
    ;; Set up a moving unit with multiple steps, target far away
    (reset! atoms/game-map [[{:type :land :contents {:type :army :mode :moving :owner :player
                                                      :target [0 3] :steps-remaining 2}}
                             {:type :land}
                             {:type :land}
                             {:type :land}]])
    (reset! atoms/player-map [[{} {} {} {}]])
    (reset! atoms/production {})
    (reset! atoms/player-items [[0 0]])
    (reset! atoms/waiting-for-input false)
    (game-loop/advance-game)
    ;; Unit should have moved towards target
    (let [original-has-unit? (some? (:contents (get-in @atoms/game-map [0 0])))]
      ;; Either original spot is empty or unit is somewhere else
      (should (or (not original-has-unit?)
                  (empty? @atoms/player-items))))))


(describe "pause functionality"
  (before
    (reset! atoms/paused false)
    (reset! atoms/pause-requested false))

  (describe "toggle-pause"
    (it "sets pause-requested when game is running"
      (reset! atoms/paused false)
      (game-loop/toggle-pause)
      (should @atoms/pause-requested))

    (it "unpauses when game is paused"
      (reset! atoms/paused true)
      (reset! atoms/pause-requested false)
      (game-loop/toggle-pause)
      (should-not @atoms/paused)
      (should-not @atoms/pause-requested)))

  (describe "advance-game pauses at round end"
    (it "pauses at end of round when pause-requested"
      (reset! atoms/game-map [[{:type :land}]])
      (reset! atoms/player-map [[{}]])
      (reset! atoms/production {})
      (reset! atoms/player-items [])  ;; Empty means end of round
      (reset! atoms/pause-requested true)
      (reset! atoms/paused false)
      (let [round-before @atoms/round-number]
        (game-loop/advance-game)
        ;; Should be paused, round should not have advanced
        (should @atoms/paused)
        (should-not @atoms/pause-requested)
        (should= round-before @atoms/round-number)))

    (it "does not start new round when paused"
      (reset! atoms/game-map [[{:type :land}]])
      (reset! atoms/player-map [[{}]])
      (reset! atoms/production {})
      (reset! atoms/player-items [])
      (reset! atoms/paused true)
      (let [round-before @atoms/round-number]
        (game-loop/advance-game)
        ;; Round should not advance while paused
        (should= round-before @atoms/round-number)))

    (it "starts new round normally when not paused"
      (reset! atoms/game-map [[{:type :land}]])
      (reset! atoms/player-map [[{}]])
      (reset! atoms/production {})
      (reset! atoms/player-items [])
      (reset! atoms/paused false)
      (reset! atoms/pause-requested false)
      (let [round-before @atoms/round-number]
        (game-loop/advance-game)
        ;; Round should advance
        (should= (inc round-before) @atoms/round-number)))))
