(ns empire.game-mechanics.services.game-over-spec
  (:require [empire.game-mechanics.services.game-over :as sut]
            [empire.notifications :as notifications]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [reset-all-atoms!]]
            [speclj.core :refer :all]))

(describe "declare-game-over!"
  (before (reset-all-atoms!))

  (it "pauses, warns, shows the actual map, and clears items"
    (let [alerts (atom 0)]
      (notifications/set-alert-port!
       (reify notifications/AlertPort
         (play-alert! [_] (swap! alerts inc))))
      (test-utils/set-test-state! :player-items [[0 0]])
      (test-utils/set-test-state! :computer-items [[1 1]])
      (test-utils/set-test-state! :map-to-display :player-map)
      (sut/declare-game-over! "You Lose")
      (should= true (test-utils/read-test-state :paused))
      (should= "You Lose" (test-utils/read-test-state :warning-message))
      (should= 1 @alerts)
      (should= :actual-map (test-utils/read-test-state :map-to-display))
      (should= [] (test-utils/read-test-state :player-items))
      (should= [] (test-utils/read-test-state :computer-items)))))
