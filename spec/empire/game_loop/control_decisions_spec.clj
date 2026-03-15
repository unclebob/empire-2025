(ns empire.game-loop.control-decisions-spec
  (:require [empire.game.loop.control-decisions :as sut]
            [speclj.core :refer :all]))

(describe "control decisions"
  (it "counts down handicap"
    (should= {:action :count-down
              :remaining 4
              :display 4}
             (sut/handicap-update 5 5)))

  (it "clears handicap display when the countdown has reached zero"
    (should= {:action :clear-display}
             (sut/handicap-update 0 0)))

  (it "leaves handicap display alone when it is nil"
    (should-be-nil (sut/handicap-update 0 nil)))

  (it "returns a lose decision when no player items remain"
    (should= :lose
             (:action (sut/game-over-action true [] [[0 0]] 0))))

  (it "returns a win decision when no computer items remain"
    (should= :win
             (:action (sut/game-over-action true [[0 0]] [] 0))))

  (it "suppresses game over while handicap remains active"
    (should-be-nil (sut/game-over-action true [] [[0 0]] 2)))

  (it "routes advance-game to player processing when player items remain"
    (should= :process-player
             (sut/advance-game-action {:player-items [[0 0]]
                                       :computer-items []
                                       :both-lists-empty? false})))

  (it "routes advance-game to new-round when both lists are empty"
    (should= :new-round
             (sut/advance-game-action {:player-items []
                                       :computer-items []
                                       :both-lists-empty? true
                                       :pause-requested false})))

  (it "routes advance-game to pause when pause was requested at round end"
    (should= :pause
             (sut/advance-game-action {:player-items []
                                       :computer-items []
                                       :both-lists-empty? true
                                       :pause-requested true})))

  (it "continues the batch only while work remains"
    (should (sut/continue-batch? 2 false false [[0 0]] []))
    (should-not (sut/continue-batch? 1 false false [[0 0]] []))))
