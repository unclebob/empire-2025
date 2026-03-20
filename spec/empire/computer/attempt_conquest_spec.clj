(ns empire.computer.attempt-conquest-spec
  (:require [empire.computer.shared.action-resolution :as action-resolution]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-player-map! set-test-world! update-test-world!]]
            [speclj.core :refer :all]))

(describe "attempt-conquest-computer"
  (before (reset-all-atoms!))

  (it "conquers city on success (rand < 0.5)"
    (set-test-world! (build-test-map ["a+"]))
    (set-test-computer-map! (build-test-map ["a+"]))
    (test-utils/set-test-state! :production {})
    (with-redefs [rand (constantly 0.1)]
      (let [result (action-resolution/attempt-conquest-computer [0 0] [1 0])]
        (should-be-nil result)
        (should= :computer (:city-status (get-in (test-utils/read-test-state :game-map) [1 0])))
        (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))
        (should= :army (:item (get (test-utils/read-test-state :production) [1 0]))))))

  (it "army dies on failure (rand >= 0.5)"
    (set-test-world! (build-test-map ["a+"]))
    (set-test-computer-map! (build-test-map ["a+"]))
    (with-redefs [rand (constantly 0.9)]
      (let [result (action-resolution/attempt-conquest-computer [0 0] [1 0])]
        (should-be-nil result)
        (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))
        (should= :free (:city-status (get-in (test-utils/read-test-state :game-map) [1 0]))))))

  (it "updates player-map city status when computer conquers a player city"
    (set-test-world! (build-test-map ["aO"]))
    (set-test-computer-map! (build-test-map ["aO"]))
    (set-test-player-map! (build-test-map ["aO"]))
    (with-redefs [rand (constantly 0.1)]
      (action-resolution/attempt-conquest-computer [0 0] [1 0])
      (should= :computer (get-in (test-utils/read-test-state :player-map) [1 0 :city-status]))))

  (it "does not reveal undiscovered city on player-map when computer conquers free city"
    (set-test-world! (build-test-map ["a+"]))
    (set-test-computer-map! (build-test-map ["a+"]))
    (set-test-player-map! (build-test-map ["a."]))
    (with-redefs [rand (constantly 0.1)]
      (action-resolution/attempt-conquest-computer [0 0] [1 0])
      (should-be-nil (get-in (test-utils/read-test-state :player-map) [1 0]))))

  (it "does not change discovered free city on player-map when computer conquers it"
    (set-test-world! (build-test-map ["a+"]))
    (set-test-computer-map! (build-test-map ["a+"]))
    (set-test-player-map! (build-test-map ["a+"]))
    (with-redefs [rand (constantly 0.1)]
      (action-resolution/attempt-conquest-computer [0 0] [1 0])
      (should= :free (get-in (test-utils/read-test-state :player-map) [1 0 :city-status]))))

  (it "declares defeat when computer conquers the last player city"
    (set-test-world! (build-test-map ["aO"]))
    (set-test-computer-map! (build-test-map ["aO"]))
    (set-test-player-map! (build-test-map ["aO"]))
    (test-utils/set-test-state! :game-over-check-enabled true)
    (test-utils/set-test-state! :player-items [[0 0]])
    (test-utils/set-test-state! :computer-items [[1 0]])
    (with-redefs [rand (constantly 0.1)]
      (action-resolution/attempt-conquest-computer [0 0] [1 0])
      (should (test-utils/read-test-state :paused))
      (should-contain "You Lose" (test-utils/read-test-state :error-message))
      (should= :actual-map (test-utils/read-test-state :map-to-display))
      (should= [] (vec (test-utils/read-test-state :player-items)))
      (should= [] (vec (test-utils/read-test-state :computer-items))))))
