(ns empire.game-loop-rounds-spec
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.game.loop.core :as game-loop]
            [empire.game-mechanics.debug.integrity :as integrity]
            [empire.config.core :as config]
            [empire.game-mechanics.movement.api :as movement]
            [empire.test.utils :refer [build-test-map set-test-unit get-test-unit reset-all-atoms! set-test-player-map! set-test-computer-map! make-initial-test-map set-test-world!]]))

(describe "round management"
  (before (reset-all-atoms!))

  (context "start-new-round"
    (before
      (set-test-world! (build-test-map ["O"]))
      (set-test-player-map! (build-test-map ["#"]))
      (set-test-computer-map! (build-test-map ["#"]))
      (test-utils/set-test-state! :production {})
      (test-utils/set-test-state! :round-number 0)
      (test-utils/set-test-state! :handicap-rounds-remaining 0)
      (test-utils/set-test-state! :handicap-display-rounds nil)
      (test-utils/set-test-state! :player-items [])
      (test-utils/set-test-state! :waiting-for-input true)
      (test-utils/set-test-state! :attention-message "old message")
      (test-utils/set-test-state! :cells-needing-attention [[0 0]]))

    (it "increments round number"
      (game-loop/start-new-round)
      (should= 1 (test-utils/read-test-state :round-number)))

    (it "builds player items list"
      (game-loop/start-new-round)
      (should-contain [0 0] (test-utils/read-test-state :player-items)))

    (it "suppresses player items while handicap rounds remain"
      (test-utils/set-test-state! :handicap-rounds-remaining 2)
      (test-utils/set-test-state! :handicap-display-rounds 2)
      (game-loop/start-new-round)
      (should= [] (vec (test-utils/read-test-state :player-items))))

    (it "resets waiting-for-input to false"
      (game-loop/start-new-round)
      (should= false (test-utils/read-test-state :waiting-for-input)))

    (it "clears message"
      (game-loop/start-new-round)
      (should= "" (test-utils/read-test-state :attention-message)))

    (it "clears cells-needing-attention"
      (game-loop/start-new-round)
      (should= [] (test-utils/read-test-state :cells-needing-attention)))

    (it "checks world integrity once per round"
      (let [calls (atom 0)]
        (with-redefs [integrity/check-world-integrity! (fn []
                                                         (swap! calls inc)
                                                         nil)]
          (game-loop/start-new-round)
          (should= 1 @calls))))

    (it "does not wake carrier fighters - they stay asleep until u is pressed"
      (set-test-world! (-> (build-test-map ["C"])
                                  (assoc-in [0 0 :contents :fighter-count] 2)
                                  (assoc-in [0 0 :contents :awake-fighters] 0)))
      (set-test-player-map! (build-test-map ["~"]))
      (set-test-computer-map! (build-test-map ["~"]))
      (game-loop/start-new-round)
      (let [carrier (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))]
        (should= 0 (:awake-fighters carrier 0)))))

  (context "advance-game"
    (it "starts new round when player-items is empty"
      (set-test-world! (build-test-map ["O"]))
      (set-test-player-map! (build-test-map ["#"]))
      (set-test-computer-map! (build-test-map ["#"]))
      (test-utils/set-test-state! :production {})
      (test-utils/set-test-state! :player-items [])
      (test-utils/set-test-state! :round-number 0)
      (game-loop/advance-game)
      (should= 1 (test-utils/read-test-state :round-number)))

    (it "counts handicap down between rounds"
      (set-test-world! (build-test-map ["O"]))
      (set-test-player-map! (build-test-map ["#"]))
      (set-test-computer-map! (build-test-map ["#"]))
      (test-utils/set-test-state! :production {})
      (test-utils/set-test-state! :player-items [])
      (test-utils/set-test-state! :computer-items [])
      (test-utils/set-test-state! :round-number 1)
      (test-utils/set-test-state! :handicap-rounds-remaining 2)
      (test-utils/set-test-state! :handicap-display-rounds 2)
      (game-loop/advance-game)
      (should= 2 (test-utils/read-test-state :round-number))
      (should= 1 (test-utils/read-test-state :handicap-rounds-remaining))
      (should= 1 (test-utils/read-test-state :handicap-display-rounds))
      (should= [] (vec (test-utils/read-test-state :player-items)))))

    (it "sets waiting-for-input when item needs attention"
      (set-test-world! (build-test-map ["O"]))
      (test-utils/set-test-state! :production {})
      (test-utils/set-test-state! :player-items [[0 0]])
      (test-utils/set-test-state! :waiting-for-input false)
      (test-utils/set-test-state! :attention-message "")
      (game-loop/advance-game)
      (should= true (test-utils/read-test-state :waiting-for-input)))

    (it "does nothing when waiting for input"
      (set-test-world! (build-test-map ["A"]))
      (set-test-unit (test-utils/game-map-atom) "A" :mode :awake)
      (test-utils/set-test-state! :production {})
      (test-utils/set-test-state! :player-items [[0 0]])
      (test-utils/set-test-state! :waiting-for-input true)
      (test-utils/set-test-state! :round-number 5)
      (game-loop/advance-game)
      (should= 5 (test-utils/read-test-state :round-number))
      (should= [[0 0]] (test-utils/read-test-state :player-items)))

    (it "moves to next item when unit does not need attention"
      (set-test-world! (build-test-map ["A"]))
      (set-test-unit (test-utils/game-map-atom) "A" :mode :sentry)
      (test-utils/set-test-state! :production {})
      (test-utils/set-test-state! :player-items [[0 0]])
      (test-utils/set-test-state! :waiting-for-input false)
      (game-loop/advance-game)
      (should= [] (vec (test-utils/read-test-state :player-items)))))

  (context "update-map"
    (before
      (set-test-world! (build-test-map ["O"]))
      (set-test-player-map! (build-test-map ["#"]))
      (set-test-computer-map! (build-test-map ["#"]))
      (test-utils/set-test-state! :production {})
      (test-utils/set-test-state! :player-items [])
      (test-utils/set-test-state! :round-number 0))

    (it "calls advance-game which starts new round when empty"
      (game-loop/update-map)
      (should= 1 (test-utils/read-test-state :round-number))))

  (context "game pauses when load menu is open"
    (it "does not advance game when load-menu-open is true"
      (test-utils/set-test-state! :load-menu-open true)
      (test-utils/set-test-state! :player-items [])
      (test-utils/set-test-state! :computer-items [])
      (test-utils/set-test-state! :round-number 5)
      (game-loop/advance-game)
      (should= 5 (test-utils/read-test-state :round-number))))

  (context "game pauses when save menu is open"
    (it "does not advance game when save-menu-open is true"
      (test-utils/set-test-state! :save-menu-open true)
      (test-utils/set-test-state! :player-items [])
      (test-utils/set-test-state! :computer-items [])
      (test-utils/set-test-state! :round-number 5)
      (game-loop/advance-game)
      (should= 5 (test-utils/read-test-state :round-number))))

  (context "pause functionality"
    (before
      (reset-all-atoms!)
      (test-utils/set-test-state! :paused false)
      (test-utils/set-test-state! :pause-requested false)
      (test-utils/set-test-state! :load-menu-open false)
      (test-utils/set-test-state! :save-menu-open false)
      (test-utils/set-test-state! :player-items [])
      (test-utils/set-test-state! :computer-items [])
      (test-utils/set-test-state! :handicap-rounds-remaining 0)
      (test-utils/set-test-state! :handicap-display-rounds nil))

    (context "toggle-pause"
      (it "sets pause-requested when game is running"
        (test-utils/set-test-state! :paused false)
        (game-loop/toggle-pause)
        (should (test-utils/read-test-state :pause-requested)))

      (it "unpauses when game is paused"
        (test-utils/set-test-state! :paused true)
        (test-utils/set-test-state! :pause-requested false)
        (game-loop/toggle-pause)
        (should-not (test-utils/read-test-state :paused))
        (should-not (test-utils/read-test-state :pause-requested))))

    (context "step-one-round"
      (it "does nothing when not paused"
        (test-utils/set-test-state! :paused false)
        (test-utils/set-test-state! :round-number 5)
        (game-loop/step-one-round)
        (should-not (test-utils/read-test-state :paused))
        (should= 5 (test-utils/read-test-state :round-number)))

      (it "unpauses and requests pause when paused"
        (test-utils/set-test-state! :paused true)
        (test-utils/set-test-state! :pause-requested false)
        (test-utils/set-test-state! :player-items [[0 0]])
        (game-loop/step-one-round)
        (should-not (test-utils/read-test-state :paused))
        (should (test-utils/read-test-state :pause-requested)))

      (it "starts new round when paused and items empty"
        (set-test-world! (build-test-map ["O"]))
        (set-test-player-map! (build-test-map ["#"]))
        (set-test-computer-map! (build-test-map ["#"]))
        (test-utils/set-test-state! :production {})
        (test-utils/set-test-state! :paused true)
        (test-utils/set-test-state! :player-items [])
        (test-utils/set-test-state! :computer-items [])
        (test-utils/set-test-state! :round-number 5)
        (game-loop/step-one-round)
        (should= 6 (test-utils/read-test-state :round-number)))

      (it "does not start new round when player-items not empty"
        (test-utils/set-test-state! :paused true)
        (test-utils/set-test-state! :player-items [[0 0]])
        (test-utils/set-test-state! :computer-items [])
        (test-utils/set-test-state! :round-number 5)
        (game-loop/step-one-round)
        (should= 5 (test-utils/read-test-state :round-number))))

    (context "advance-game pauses at round end"
      (it "pauses at end of round when pause-requested"
        (set-test-world! (build-test-map ["#"]))
        (set-test-player-map! (build-test-map ["#"]))
        (test-utils/set-test-state! :production {})
        (test-utils/set-test-state! :player-items [])  ;; Empty means end of round
        (test-utils/set-test-state! :pause-requested true)
        (test-utils/set-test-state! :paused false)
        (let [round-before (test-utils/read-test-state :round-number)]
          (game-loop/advance-game)
          ;; Should be paused, round should not have advanced
          (should (test-utils/read-test-state :paused))
          (should-not (test-utils/read-test-state :pause-requested))
          (should= round-before (test-utils/read-test-state :round-number))))

      (it "does not start new round when paused"
        (set-test-world! (build-test-map ["#"]))
        (set-test-player-map! (build-test-map ["#"]))
        (test-utils/set-test-state! :production {})
        (test-utils/set-test-state! :player-items [])
        (test-utils/set-test-state! :paused true)
        (let [round-before (test-utils/read-test-state :round-number)]
          (game-loop/advance-game)
          ;; Round should not advance while paused
          (should= round-before (test-utils/read-test-state :round-number))))

      (it "starts new round normally when not paused"
        (set-test-world! (build-test-map ["#"]))
        (set-test-player-map! (build-test-map ["#"]))
        (test-utils/set-test-state! :production {})
        (test-utils/set-test-state! :player-items [])
        (test-utils/set-test-state! :paused false)
        (test-utils/set-test-state! :pause-requested false)
        (let [round-before (test-utils/read-test-state :round-number)]
          (game-loop/advance-game)
          ;; Round should advance
          (should= (inc round-before) (test-utils/read-test-state :round-number))))))

  (context "advance-game-batch"
    (before
      (reset-all-atoms!)
      (test-utils/set-test-state! :load-menu-open false)
      (test-utils/set-test-state! :save-menu-open false)
      (test-utils/set-test-state! :paused false)
      (test-utils/set-test-state! :pause-requested false)
      (test-utils/set-test-state! :handicap-rounds-remaining 0)
      (test-utils/set-test-state! :handicap-display-rounds nil))

    (it "processes multiple sentry units in one batch"
      ;; 3 sentry units that don't need attention
      (set-test-world! (build-test-map ["AAA"]))
      (set-test-unit (test-utils/game-map-atom) "A1" :mode :sentry)
      (set-test-unit (test-utils/game-map-atom) "A2" :mode :sentry)
      (set-test-unit (test-utils/game-map-atom) "A3" :mode :sentry)
      (set-test-player-map! (build-test-map ["###"]))
      (set-test-computer-map! (build-test-map ["###"]))
      (test-utils/set-test-state! :production {})
      (test-utils/set-test-state! :player-items [[0 0] [1 0] [2 0]])
      (test-utils/set-test-state! :waiting-for-input false)
      (game-loop/advance-game-batch)
      ;; All 3 should be processed in one batch call
      (should= [] (vec (test-utils/read-test-state :player-items))))

    (it "stops when items exhausted before reaching limit"
      ;; 2 sentry units, advances-per-frame is 10
      (set-test-world! (build-test-map ["AA"]))
      (set-test-unit (test-utils/game-map-atom) "A1" :mode :sentry)
      (set-test-unit (test-utils/game-map-atom) "A2" :mode :sentry)
      (set-test-player-map! (build-test-map ["##"]))
      (set-test-computer-map! (build-test-map ["##"]))
      (test-utils/set-test-state! :production {})
      (test-utils/set-test-state! :player-items [[0 0] [1 0]])
      (test-utils/set-test-state! :waiting-for-input false)
      (game-loop/advance-game-batch)
      ;; Both processed, started new round, player-items rebuilt
      (should-not (test-utils/read-test-state :waiting-for-input)))

    (it "stops when waiting for input"
      ;; First unit needs attention (awake), should block
      (set-test-world! (build-test-map ["AA"]))
      (set-test-unit (test-utils/game-map-atom) "A1" :mode :awake)
      (set-test-unit (test-utils/game-map-atom) "A2" :mode :sentry)
      (set-test-player-map! (build-test-map ["##"]))
      (set-test-computer-map! (build-test-map ["##"]))
      (test-utils/set-test-state! :production {})
      (test-utils/set-test-state! :player-items [[0 0] [1 0]])
      (test-utils/set-test-state! :waiting-for-input false)
      (game-loop/advance-game-batch)
      ;; Should be waiting for input after first item
      (should (test-utils/read-test-state :waiting-for-input))
      ;; Second item should still be in list
      (should (some #{[1 0]} (test-utils/read-test-state :player-items))))

    (it "stops when paused"
      (set-test-world! (build-test-map ["A"]))
      (set-test-unit (test-utils/game-map-atom) "A" :mode :sentry)
      (set-test-player-map! (build-test-map ["#"]))
      (set-test-computer-map! (build-test-map ["#"]))
      (test-utils/set-test-state! :production {})
      (test-utils/set-test-state! :player-items [[0 0]])
      (test-utils/set-test-state! :paused true)
      (let [items-before (vec (test-utils/read-test-state :player-items))]
        (game-loop/advance-game-batch)
        ;; Items should be unchanged since game is paused
        (should= items-before (vec (test-utils/read-test-state :player-items))))))

(describe "game over and victory"
  (before
    (reset-all-atoms!)
    (test-utils/set-test-state! :game-over-check-enabled true)
    (test-utils/set-test-state! :handicap-rounds-remaining 0)
    (test-utils/set-test-state! :handicap-display-rounds nil))

  (context "round start elimination with empty item lists"
    (it "pauses game when player has no cities or units"
      (set-test-world! (build-test-map ["X#"]))  ;; Only computer city
      (set-test-player-map! (build-test-map ["##"]))
      (set-test-computer-map! (build-test-map ["##"]))
      (test-utils/set-test-state! :production {})
      (test-utils/set-test-state! :paused false)
      (game-loop/start-new-round)
      (should (test-utils/read-test-state :paused)))

    (it "does not pause game when player only has a unit at round start"
      (set-test-world! (build-test-map ["AX"]))  ;; Player has an army
      (set-test-player-map! (build-test-map ["##"]))
      (set-test-computer-map! (build-test-map ["##"]))
      (test-utils/set-test-state! :production {})
      (test-utils/set-test-state! :paused false)
      (game-loop/start-new-round)
      (should-not (test-utils/read-test-state :paused))))

  (context "round start resignation with empty item lists"
    (it "pauses game when computer has no cities or units"
      (set-test-world! (build-test-map ["O#"]))  ;; Only player city
      (set-test-player-map! (build-test-map ["##"]))
      (set-test-computer-map! (build-test-map ["##"]))
      (test-utils/set-test-state! :production {})
      (test-utils/set-test-state! :paused false)
      (game-loop/start-new-round)
      (should (test-utils/read-test-state :paused)))

    (it "does not pause game when computer only has a unit at round start"
      (set-test-world! (build-test-map ["Oa"]))  ;; Computer has an army
      (set-test-player-map! (build-test-map ["##"]))
      (set-test-computer-map! (build-test-map ["##"]))
      (test-utils/set-test-state! :production {})
      (test-utils/set-test-state! :paused false)
      (game-loop/start-new-round)
      (should-not (test-utils/read-test-state :paused)))

    (it "does not end game when player only eliminates the last computer unit"
      ;; Eliminating units alone should not end the game; city elimination triggers game over.
      (set-test-world! (build-test-map ["Aa#A"]))
      (set-test-unit (test-utils/game-map-atom) "A1" :mode :awake :steps-remaining 1)
      (set-test-unit (test-utils/game-map-atom) "A2" :mode :awake :steps-remaining 1)
      (set-test-player-map! (build-test-map ["####"]))
      (set-test-computer-map! (build-test-map ["####"]))
      (test-utils/set-test-state! :production {})
      (test-utils/set-test-state! :player-items [[0 0] [3 0]])  ;; Both player armies in queue
      (test-utils/set-test-state! :waiting-for-input false)
      (test-utils/set-test-state! :paused false)
      ;; Step 1: first army asks for attention
      (game-loop/advance-game)
      (should (test-utils/read-test-state :waiting-for-input))
      (should= [[0 0]] (test-utils/read-test-state :cells-needing-attention))
      ;; Step 2: user moves first army to attack computer army
      (movement/set-unit-movement [0 0] [1 0])
      (game-loop/item-processed)
      ;; Step 3: advance-game triggers combat; mock rand so player wins
      (with-redefs [rand (constantly 0.0)]
        (game-loop/advance-game))
      (should-not (test-utils/read-test-state :paused)))))
