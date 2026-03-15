(ns empire.game-loop-batch-spec
  (:require [empire.game.loop.core :as game-loop]
            [empire.game-mechanics.movement.api :as movement]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-player-map! set-test-unit set-test-world!]]
            [speclj.core :refer :all]))

(describe "advance-game-batch"
  (before
    (reset-all-atoms!)
    (test-utils/set-test-state! :load-menu-open false)
    (test-utils/set-test-state! :save-menu-open false)
    (test-utils/set-test-state! :paused false)
    (test-utils/set-test-state! :pause-requested false)
    (test-utils/set-test-state! :handicap-rounds-remaining 0)
    (test-utils/set-test-state! :handicap-display-rounds nil))

  (it "processes multiple sentry units in one batch"
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
    (should= [] (vec (test-utils/read-test-state :player-items))))

  (it "stops when items exhausted before reaching limit"
    (set-test-world! (build-test-map ["AA"]))
    (set-test-unit (test-utils/game-map-atom) "A1" :mode :sentry)
    (set-test-unit (test-utils/game-map-atom) "A2" :mode :sentry)
    (set-test-player-map! (build-test-map ["##"]))
    (set-test-computer-map! (build-test-map ["##"]))
    (test-utils/set-test-state! :production {})
    (test-utils/set-test-state! :player-items [[0 0] [1 0]])
    (test-utils/set-test-state! :waiting-for-input false)
    (game-loop/advance-game-batch)
    (should-not (test-utils/read-test-state :waiting-for-input)))

  (it "stops when waiting for input"
    (set-test-world! (build-test-map ["AA"]))
    (set-test-unit (test-utils/game-map-atom) "A1" :mode :awake)
    (set-test-unit (test-utils/game-map-atom) "A2" :mode :sentry)
    (set-test-player-map! (build-test-map ["##"]))
    (set-test-computer-map! (build-test-map ["##"]))
    (test-utils/set-test-state! :production {})
    (test-utils/set-test-state! :player-items [[0 0] [1 0]])
    (test-utils/set-test-state! :waiting-for-input false)
    (game-loop/advance-game-batch)
    (should (test-utils/read-test-state :waiting-for-input))
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
      (should= items-before (vec (test-utils/read-test-state :player-items))))))

(describe "game over and victory"
  (before
    (reset-all-atoms!)
    (test-utils/set-test-state! :game-over-check-enabled true)
    (test-utils/set-test-state! :handicap-rounds-remaining 0)
    (test-utils/set-test-state! :handicap-display-rounds nil))

  (context "round start elimination with empty item lists"
    (it "pauses game when player has no cities or units"
      (set-test-world! (build-test-map ["X#"]))
      (set-test-player-map! (build-test-map ["##"]))
      (set-test-computer-map! (build-test-map ["##"]))
      (test-utils/set-test-state! :production {})
      (test-utils/set-test-state! :paused false)
      (game-loop/start-new-round)
      (should (test-utils/read-test-state :paused)))

    (it "does not pause game when player only has a unit at round start"
      (set-test-world! (build-test-map ["AX"]))
      (set-test-player-map! (build-test-map ["##"]))
      (set-test-computer-map! (build-test-map ["##"]))
      (test-utils/set-test-state! :production {})
      (test-utils/set-test-state! :paused false)
      (game-loop/start-new-round)
      (should-not (test-utils/read-test-state :paused))))

  (context "round start resignation with empty item lists"
    (it "pauses game when computer has no cities or units"
      (set-test-world! (build-test-map ["O#"]))
      (set-test-player-map! (build-test-map ["##"]))
      (set-test-computer-map! (build-test-map ["##"]))
      (test-utils/set-test-state! :production {})
      (test-utils/set-test-state! :paused false)
      (game-loop/start-new-round)
      (should (test-utils/read-test-state :paused)))

    (it "does not pause game when computer only has a unit at round start"
      (set-test-world! (build-test-map ["Oa"]))
      (set-test-player-map! (build-test-map ["##"]))
      (set-test-computer-map! (build-test-map ["##"]))
      (test-utils/set-test-state! :production {})
      (test-utils/set-test-state! :paused false)
      (game-loop/start-new-round)
      (should-not (test-utils/read-test-state :paused)))

    (it "does not end game when player only eliminates the last computer unit"
      (set-test-world! (build-test-map ["Aa#A"]))
      (set-test-unit (test-utils/game-map-atom) "A1" :mode :awake :steps-remaining 1)
      (set-test-unit (test-utils/game-map-atom) "A2" :mode :awake :steps-remaining 1)
      (set-test-player-map! (build-test-map ["####"]))
      (set-test-computer-map! (build-test-map ["####"]))
      (test-utils/set-test-state! :production {})
      (test-utils/set-test-state! :player-items [[0 0] [3 0]])
      (test-utils/set-test-state! :waiting-for-input false)
      (test-utils/set-test-state! :paused false)
      (game-loop/advance-game)
      (should (test-utils/read-test-state :waiting-for-input))
      (should= [[0 0]] (test-utils/read-test-state :cells-needing-attention))
      (movement/set-unit-movement [0 0] [1 0])
      (game-loop/item-processed)
      (with-redefs [rand (constantly 0.0)]
        (game-loop/advance-game))
      (should-not (test-utils/read-test-state :paused)))))
