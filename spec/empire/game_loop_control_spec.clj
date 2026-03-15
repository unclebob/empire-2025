(ns empire.game-loop-control-spec
  (:require [empire.config.core :as config]
            [empire.game.loop.core :as game-loop]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-player-map! set-test-world!]]
            [speclj.core :refer :all]))

(describe "start-new-round"
  (before (reset-all-atoms!))

  (it "sets waiting-for-input to false"
    (let [m (build-test-map ["OX"])]
      (set-test-world! m)
      (set-test-player-map! m)
      (set-test-computer-map! m)
      (test-utils/set-test-state! :waiting-for-input true)
      (game-loop/start-new-round)
      (should= false (test-utils/read-test-state :waiting-for-input))))

  (it "detects game over when no player items"
    (let [m (build-test-map ["X"])]
      (set-test-world! m)
      (set-test-player-map! m)
      (set-test-computer-map! m)
      (test-utils/set-test-state! :game-over-check-enabled true)
      (game-loop/start-new-round)
      (should= true (test-utils/read-test-state :paused))
      (should-contain "You Lose" (test-utils/read-test-state :error-message))))

  (it "does not set game over when player items exist"
    (let [m (build-test-map ["OX"])]
      (set-test-world! m)
      (set-test-player-map! m)
      (set-test-computer-map! m)
      (test-utils/set-test-state! :game-over-check-enabled true)
      (game-loop/start-new-round)
      (should= false (test-utils/read-test-state :paused))))

  (it "detects resignation when no computer items"
    (let [m (build-test-map ["O"])]
      (set-test-world! m)
      (set-test-player-map! m)
      (set-test-computer-map! m)
      (test-utils/set-test-state! :game-over-check-enabled true)
      (game-loop/start-new-round)
      (should= true (test-utils/read-test-state :paused))
      (should-contain "I Resign" (test-utils/read-test-state :error-message))))

  (it "does not set victory when computer items exist"
    (let [m (build-test-map ["OX"])]
      (set-test-world! m)
      (set-test-player-map! m)
      (set-test-computer-map! m)
      (test-utils/set-test-state! :game-over-check-enabled true)
      (game-loop/start-new-round)
      (should= false (test-utils/read-test-state :paused)))))

(describe "advance-game pause logic"
  (before (reset-all-atoms!))

  (it "pauses when both lists empty and pause-requested"
    (test-utils/set-test-state! :player-items [])
    (test-utils/set-test-state! :computer-items [])
    (test-utils/set-test-state! :pause-requested true)
    (game-loop/advance-game)
    (should= true (test-utils/read-test-state :paused))
    (should= false (test-utils/read-test-state :pause-requested)))

  (it "starts new round when both lists empty and no pause requested"
    (let [m (build-test-map ["OX"])]
      (set-test-world! m)
      (set-test-player-map! m)
      (set-test-computer-map! m)
      (test-utils/set-test-state! :player-items [])
      (test-utils/set-test-state! :computer-items [])
      (test-utils/set-test-state! :pause-requested false)
      (game-loop/advance-game)
      (should= 1 (test-utils/read-test-state :round-number)))))

(describe "advance-game-batch"
  (before (reset-all-atoms!))

  (it "calls advance-game at least once"
    (let [call-count (atom 0)]
      (with-redefs [game-loop/advance-game #(swap! call-count inc)]
        (game-loop/advance-game-batch)
        (should (<= 1 @call-count)))))

  (it "calls advance-game exactly advances-per-frame times"
    (let [call-count (atom 0)]
      (test-utils/set-test-state! :player-items [[0 0]])
      (with-redefs [game-loop/advance-game #(swap! call-count inc)]
        (game-loop/advance-game-batch)
        (should= config/advances-per-frame @call-count))))

  (it "calls advance-game multiple times when items remain"
    (let [call-count (atom 0)]
      (test-utils/set-test-state! :player-items [[0 0] [1 0]])
      (with-redefs [game-loop/advance-game
                    (fn []
                      (swap! call-count inc)
                      (when (= @call-count 3)
                        (test-utils/set-test-state! :player-items [])))]
        (game-loop/advance-game-batch)
        (should (> @call-count 1))))))

(describe "toggle-pause"
  (before (reset-all-atoms!))

  (it "resumes when paused"
    (test-utils/set-test-state! :paused true)
    (test-utils/set-test-state! :pause-requested true)
    (game-loop/toggle-pause)
    (should= false (test-utils/read-test-state :paused))
    (should= false (test-utils/read-test-state :pause-requested)))

  (it "requests pause when running"
    (test-utils/set-test-state! :paused false)
    (game-loop/toggle-pause)
    (should= true (test-utils/read-test-state :pause-requested))))

(describe "step-one-round"
  (before (reset-all-atoms!))

  (it "does nothing when not paused"
    (test-utils/set-test-state! :paused false)
    (game-loop/step-one-round)
    (should= false (test-utils/read-test-state :paused))
    (should= false (test-utils/read-test-state :pause-requested)))

  (it "unpauses and requests pause when paused"
    (test-utils/set-test-state! :paused true)
    (test-utils/set-test-state! :player-items [[0 0]])
    (game-loop/step-one-round)
    (should= false (test-utils/read-test-state :paused))
    (should= true (test-utils/read-test-state :pause-requested)))

  (it "starts new round when paused and no items"
    (let [m (build-test-map ["OX"])]
      (set-test-world! m)
      (set-test-player-map! m)
      (set-test-computer-map! m)
      (test-utils/set-test-state! :paused true)
      (test-utils/set-test-state! :player-items [])
      (test-utils/set-test-state! :computer-items [])
      (game-loop/step-one-round)
      (should= 1 (test-utils/read-test-state :round-number)))))
