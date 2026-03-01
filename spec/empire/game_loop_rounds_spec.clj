(ns empire.game-loop-rounds-spec
  (:require [speclj.core :refer :all]
            [empire.game-loop :as game-loop]
            [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.movement.movement :as movement]
            [empire.test-utils :refer [build-test-map set-test-unit get-test-unit reset-all-atoms! set-test-player-map! set-test-computer-map! make-initial-test-map set-test-world!]]))

(describe "round management"
  (before (reset-all-atoms!))

  (context "start-new-round"
    (before
      (set-test-world! (build-test-map ["O"]))
      (set-test-player-map! (build-test-map ["#"]))
      (set-test-computer-map! (build-test-map ["#"]))
      (reset! atoms/production {})
      (reset! atoms/round-number 0)
      (reset! atoms/player-items [])
      (reset! atoms/waiting-for-input true)
      (reset! atoms/attention-message "old message")
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
      (should= "" @atoms/attention-message))

    (it "clears cells-needing-attention"
      (game-loop/start-new-round)
      (should= [] @atoms/cells-needing-attention))

    (it "does not wake carrier fighters - they stay asleep until u is pressed"
      (set-test-world! (-> (build-test-map ["C"])
                                  (assoc-in [0 0 :contents :fighter-count] 2)
                                  (assoc-in [0 0 :contents :awake-fighters] 0)))
      (set-test-player-map! (build-test-map ["~"]))
      (set-test-computer-map! (build-test-map ["~"]))
      (game-loop/start-new-round)
      (let [carrier (:contents (get-in @atoms/game-map [0 0]))]
        (should= 0 (:awake-fighters carrier 0)))))

  (context "advance-game"
    (it "starts new round when player-items is empty"
      (set-test-world! (build-test-map ["O"]))
      (set-test-player-map! (build-test-map ["#"]))
      (set-test-computer-map! (build-test-map ["#"]))
      (reset! atoms/production {})
      (reset! atoms/player-items [])
      (reset! atoms/round-number 0)
      (game-loop/advance-game)
      (should= 1 @atoms/round-number))

    (it "sets waiting-for-input when item needs attention"
      (set-test-world! (build-test-map ["O"]))
      (reset! atoms/production {})
      (reset! atoms/player-items [[0 0]])
      (reset! atoms/waiting-for-input false)
      (reset! atoms/attention-message "")
      (game-loop/advance-game)
      (should= true @atoms/waiting-for-input))

    (it "does nothing when waiting for input"
      (set-test-world! (build-test-map ["A"]))
      (set-test-unit atoms/game-map "A" :mode :awake)
      (reset! atoms/production {})
      (reset! atoms/player-items [[0 0]])
      (reset! atoms/waiting-for-input true)
      (reset! atoms/round-number 5)
      (game-loop/advance-game)
      (should= 5 @atoms/round-number)
      (should= [[0 0]] @atoms/player-items))

    (it "moves to next item when unit does not need attention"
      (set-test-world! (build-test-map ["A"]))
      (set-test-unit atoms/game-map "A" :mode :sentry)
      (reset! atoms/production {})
      (reset! atoms/player-items [[0 0]])
      (reset! atoms/waiting-for-input false)
      (game-loop/advance-game)
      (should= [] (vec @atoms/player-items))))

  (context "update-map"
    (before
      (set-test-world! (build-test-map ["O"]))
      (set-test-player-map! (build-test-map ["#"]))
      (set-test-computer-map! (build-test-map ["#"]))
      (reset! atoms/production {})
      (reset! atoms/player-items [])
      (reset! atoms/round-number 0))

    (it "calls advance-game which starts new round when empty"
      (game-loop/update-map)
      (should= 1 @atoms/round-number)))

  (context "game pauses when load menu is open"
    (it "does not advance game when load-menu-open is true"
      (reset! atoms/load-menu-open true)
      (reset! atoms/player-items [])
      (reset! atoms/computer-items [])
      (reset! atoms/round-number 5)
      (game-loop/advance-game)
      (should= 5 @atoms/round-number)))

  (context "pause functionality"
    (before
      (reset! atoms/paused false)
      (reset! atoms/pause-requested false))

    (context "toggle-pause"
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

    (context "step-one-round"
      (it "does nothing when not paused"
        (reset! atoms/paused false)
        (reset! atoms/round-number 5)
        (game-loop/step-one-round)
        (should-not @atoms/paused)
        (should= 5 @atoms/round-number))

      (it "unpauses and requests pause when paused"
        (reset! atoms/paused true)
        (reset! atoms/pause-requested false)
        (reset! atoms/player-items [[0 0]])
        (game-loop/step-one-round)
        (should-not @atoms/paused)
        (should @atoms/pause-requested))

      (it "starts new round when paused and items empty"
        (set-test-world! (build-test-map ["O"]))
        (set-test-player-map! (build-test-map ["#"]))
        (set-test-computer-map! (build-test-map ["#"]))
        (reset! atoms/production {})
        (reset! atoms/paused true)
        (reset! atoms/player-items [])
        (reset! atoms/computer-items [])
        (reset! atoms/round-number 5)
        (game-loop/step-one-round)
        (should= 6 @atoms/round-number))

      (it "does not start new round when player-items not empty"
        (reset! atoms/paused true)
        (reset! atoms/player-items [[0 0]])
        (reset! atoms/computer-items [])
        (reset! atoms/round-number 5)
        (game-loop/step-one-round)
        (should= 5 @atoms/round-number)))

    (context "advance-game pauses at round end"
      (it "pauses at end of round when pause-requested"
        (set-test-world! (build-test-map ["#"]))
        (set-test-player-map! (build-test-map ["#"]))
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
        (set-test-world! (build-test-map ["#"]))
        (set-test-player-map! (build-test-map ["#"]))
        (reset! atoms/production {})
        (reset! atoms/player-items [])
        (reset! atoms/paused true)
        (let [round-before @atoms/round-number]
          (game-loop/advance-game)
          ;; Round should not advance while paused
          (should= round-before @atoms/round-number)))

      (it "starts new round normally when not paused"
        (set-test-world! (build-test-map ["#"]))
        (set-test-player-map! (build-test-map ["#"]))
        (reset! atoms/production {})
        (reset! atoms/player-items [])
        (reset! atoms/paused false)
        (reset! atoms/pause-requested false)
        (let [round-before @atoms/round-number]
          (game-loop/advance-game)
          ;; Round should advance
          (should= (inc round-before) @atoms/round-number)))))

  (context "advance-game-batch"
    (it "processes multiple sentry units in one batch"
      ;; 3 sentry units that don't need attention
      (set-test-world! (build-test-map ["AAA"]))
      (set-test-unit atoms/game-map "A1" :mode :sentry)
      (set-test-unit atoms/game-map "A2" :mode :sentry)
      (set-test-unit atoms/game-map "A3" :mode :sentry)
      (set-test-player-map! (build-test-map ["###"]))
      (set-test-computer-map! (build-test-map ["###"]))
      (reset! atoms/production {})
      (reset! atoms/player-items [[0 0] [1 0] [2 0]])
      (reset! atoms/waiting-for-input false)
      (game-loop/advance-game-batch)
      ;; All 3 should be processed in one batch call
      (should= [] (vec @atoms/player-items)))

    (it "stops when items exhausted before reaching limit"
      ;; 2 sentry units, advances-per-frame is 10
      (set-test-world! (build-test-map ["AA"]))
      (set-test-unit atoms/game-map "A1" :mode :sentry)
      (set-test-unit atoms/game-map "A2" :mode :sentry)
      (set-test-player-map! (build-test-map ["##"]))
      (set-test-computer-map! (build-test-map ["##"]))
      (reset! atoms/production {})
      (reset! atoms/player-items [[0 0] [1 0]])
      (reset! atoms/waiting-for-input false)
      (game-loop/advance-game-batch)
      ;; Both processed, started new round, player-items rebuilt
      (should-not @atoms/waiting-for-input))

    (it "stops when waiting for input"
      ;; First unit needs attention (awake), should block
      (set-test-world! (build-test-map ["AA"]))
      (set-test-unit atoms/game-map "A1" :mode :awake)
      (set-test-unit atoms/game-map "A2" :mode :sentry)
      (set-test-player-map! (build-test-map ["##"]))
      (set-test-computer-map! (build-test-map ["##"]))
      (reset! atoms/production {})
      (reset! atoms/player-items [[0 0] [1 0]])
      (reset! atoms/waiting-for-input false)
      (game-loop/advance-game-batch)
      ;; Should be waiting for input after first item
      (should @atoms/waiting-for-input)
      ;; Second item should still be in list
      (should (some #{[1 0]} @atoms/player-items)))

    (it "stops when paused"
      (set-test-world! (build-test-map ["A"]))
      (set-test-unit atoms/game-map "A" :mode :sentry)
      (set-test-player-map! (build-test-map ["#"]))
      (set-test-computer-map! (build-test-map ["#"]))
      (reset! atoms/production {})
      (reset! atoms/player-items [[0 0]])
      (reset! atoms/paused true)
      (let [items-before (vec @atoms/player-items)]
        (game-loop/advance-game-batch)
        ;; Items should be unchanged since game is paused
        (should= items-before (vec @atoms/player-items))))))

(describe "game over and victory"
  (before
    (reset-all-atoms!)
    (reset! atoms/game-over-check-enabled true))

  (context "game over"
    (it "pauses game when player has no cities and no units"
      (set-test-world! (build-test-map ["X#"]))  ;; Only computer city
      (set-test-player-map! (build-test-map ["##"]))
      (set-test-computer-map! (build-test-map ["##"]))
      (reset! atoms/production {})
      (reset! atoms/paused false)
      (game-loop/start-new-round)
      (should @atoms/paused))

    (it "displays ****GAME OVER***** in error message"
      (set-test-world! (build-test-map ["X#"]))  ;; Only computer city
      (set-test-player-map! (build-test-map ["##"]))
      (set-test-computer-map! (build-test-map ["##"]))
      (reset! atoms/production {})
      (reset! atoms/error-message "")
      (game-loop/start-new-round)
      (should= "****GAME OVER*****" @atoms/error-message))

    (it "does not trigger game over when player has a city"
      (set-test-world! (build-test-map ["OX"]))  ;; Player has a city
      (set-test-player-map! (build-test-map ["##"]))
      (set-test-computer-map! (build-test-map ["##"]))
      (reset! atoms/production {})
      (reset! atoms/paused false)
      (game-loop/start-new-round)
      (should-not @atoms/paused))

    (it "does not trigger game over when player has a unit"
      (set-test-world! (build-test-map ["AX"]))  ;; Player has an army
      (set-test-player-map! (build-test-map ["##"]))
      (set-test-computer-map! (build-test-map ["##"]))
      (reset! atoms/production {})
      (reset! atoms/paused false)
      (game-loop/start-new-round)
      (should-not @atoms/paused))

    (it "switches map display to actual-map on game over"
      (set-test-world! (build-test-map ["X#"]))  ;; Only computer city
      (set-test-player-map! (build-test-map ["##"]))
      (set-test-computer-map! (build-test-map ["##"]))
      (reset! atoms/production {})
      (reset! atoms/map-to-display :player-map)
      (game-loop/start-new-round)
      (should= :actual-map @atoms/map-to-display)))

  (context "player victory"
    (it "pauses game when computer has no cities and no units"
      (set-test-world! (build-test-map ["O#"]))  ;; Only player city
      (set-test-player-map! (build-test-map ["##"]))
      (set-test-computer-map! (build-test-map ["##"]))
      (reset! atoms/production {})
      (reset! atoms/paused false)
      (game-loop/start-new-round)
      (should @atoms/paused))

    (it "displays ****YOU WIN!***** in error message"
      (set-test-world! (build-test-map ["O#"]))  ;; Only player city
      (set-test-player-map! (build-test-map ["##"]))
      (set-test-computer-map! (build-test-map ["##"]))
      (reset! atoms/production {})
      (reset! atoms/error-message "")
      (game-loop/start-new-round)
      (should= "****YOU WIN!*****" @atoms/error-message))

    (it "does not trigger victory when computer has a city"
      (set-test-world! (build-test-map ["OX"]))  ;; Computer has a city
      (set-test-player-map! (build-test-map ["##"]))
      (set-test-computer-map! (build-test-map ["##"]))
      (reset! atoms/production {})
      (reset! atoms/paused false)
      (game-loop/start-new-round)
      (should-not @atoms/paused))

    (it "does not trigger victory when computer has a unit"
      (set-test-world! (build-test-map ["Oa"]))  ;; Computer has an army
      (set-test-player-map! (build-test-map ["##"]))
      (set-test-computer-map! (build-test-map ["##"]))
      (reset! atoms/production {})
      (reset! atoms/paused false)
      (game-loop/start-new-round)
      (should-not @atoms/paused))

    (it "switches map display to actual-map on victory"
      (set-test-world! (build-test-map ["O#"]))  ;; Only player city
      (set-test-player-map! (build-test-map ["##"]))
      (set-test-computer-map! (build-test-map ["##"]))
      (reset! atoms/production {})
      (reset! atoms/map-to-display :player-map)
      (game-loop/start-new-round)
      (should= :actual-map @atoms/map-to-display))

    (it "declares victory immediately after player move eliminates last computer"
      ;; Scenario: player has two armies, computer has one army
      ;; First army kills computer army, victory should be declared immediately
      ;; Second army should NOT get attention
      (set-test-world! (build-test-map ["Aa#A"]))
      (set-test-unit atoms/game-map "A1" :mode :awake :steps-remaining 1)
      (set-test-unit atoms/game-map "A2" :mode :awake :steps-remaining 1)
      (set-test-player-map! (build-test-map ["####"]))
      (set-test-computer-map! (build-test-map ["####"]))
      (reset! atoms/production {})
      (reset! atoms/player-items [[0 0] [3 0]])  ;; Both player armies in queue
      (reset! atoms/waiting-for-input false)
      (reset! atoms/paused false)
      ;; Step 1: first army asks for attention
      (game-loop/advance-game)
      (should @atoms/waiting-for-input)
      (should= [[0 0]] @atoms/cells-needing-attention)
      ;; Step 2: user moves first army to attack computer army
      (movement/set-unit-movement [0 0] [1 0])
      (game-loop/item-processed)
      ;; Step 3: advance-game triggers combat; mock rand so player wins
      (with-redefs [rand (constantly 0.0)]
        (game-loop/advance-game))
      (should @atoms/paused)
      (should= "****YOU WIN!*****" @atoms/error-message)
      (should= :actual-map @atoms/map-to-display)
      (should= [] (vec @atoms/player-items)))))
