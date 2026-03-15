(ns empire.game-loop-pause-spec
  (:require [empire.game.loop.core :as game-loop]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-player-map! set-test-unit set-test-world!]]
            [speclj.core :refer :all]))

(describe "advance-game"
  (before (reset-all-atoms!))

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
    (should= [] (vec (test-utils/read-test-state :player-items))))

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
    (should= [[0 0]] (test-utils/read-test-state :player-items))))

  (it "clears handicap display once the countdown has already reached zero"
    (test-utils/set-test-state! :handicap-rounds-remaining 0)
    (test-utils/set-test-state! :handicap-display-rounds 0)
    (#'game-loop/update-handicap-before-round!)
    (should= nil (test-utils/read-test-state :handicap-display-rounds)))

  (it "leaves handicap display untouched when it is already nil"
    (test-utils/set-test-state! :handicap-rounds-remaining 0)
    (test-utils/set-test-state! :handicap-display-rounds nil)
    (#'game-loop/update-handicap-before-round!)
    (should= nil (test-utils/read-test-state :handicap-display-rounds)))

  (it "treats a nil remaining count as expired only when display is zero"
    (should (#'game-loop/handicap-display-expired? nil 0))
    (should-not (#'game-loop/handicap-display-expired? nil nil)))

(describe "update-map"
  (before
    (reset-all-atoms!)
    (set-test-world! (build-test-map ["O"]))
    (set-test-player-map! (build-test-map ["#"]))
    (set-test-computer-map! (build-test-map ["#"]))
    (test-utils/set-test-state! :production {})
    (test-utils/set-test-state! :player-items [])
    (test-utils/set-test-state! :round-number 0))

  (it "calls advance-game which starts new round when empty"
    (game-loop/update-map)
    (should= 1 (test-utils/read-test-state :round-number))))

(describe "menu pauses"
  (before (reset-all-atoms!))

  (it "does not advance game when load-menu-open is true"
    (test-utils/set-test-state! :load-menu-open true)
    (test-utils/set-test-state! :player-items [])
    (test-utils/set-test-state! :computer-items [])
    (test-utils/set-test-state! :round-number 5)
    (game-loop/advance-game)
    (should= 5 (test-utils/read-test-state :round-number)))

  (it "does not advance game when save-menu-open is true"
    (test-utils/set-test-state! :save-menu-open true)
    (test-utils/set-test-state! :player-items [])
    (test-utils/set-test-state! :computer-items [])
    (test-utils/set-test-state! :round-number 5)
    (game-loop/advance-game)
    (should= 5 (test-utils/read-test-state :round-number))))

(describe "pause functionality"
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
      (test-utils/set-test-state! :player-items [])
      (test-utils/set-test-state! :pause-requested true)
      (test-utils/set-test-state! :paused false)
      (let [round-before (test-utils/read-test-state :round-number)]
        (game-loop/advance-game)
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
        (should= (inc round-before) (test-utils/read-test-state :round-number))))))
