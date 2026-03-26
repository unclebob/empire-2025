(ns empire.computer.attempt-conquest-spec
  (:require [empire.computer.shared.action-resolution :as action-resolution]
            [empire.game-mechanics.debug.logging :as debug-logging]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-player-map! set-test-world! update-test-world!]]
            [speclj.core :refer :all]))

(describe "attempt-conquest-computer"
  (before (reset-all-atoms!))

  (it "conquers city on success (rand < 0.5)"
    (set-test-world! (build-test-map ["a+"]))
    (update-test-world! assoc-in [0 0 :contents :computer-unit-id] 41)
    (set-test-computer-map! (build-test-map ["a+"]))
    (test-utils/set-test-computer-map! (test-utils/read-test-state :game-map))
    (test-utils/set-test-state! :production {})
    (test-utils/set-test-state! :computer-unit-log-file "test.log")
    (with-redefs [rand (constantly 0.1)]
      (let [result (debug-logging/with-computer-unit-context
                     41
                     #(action-resolution/attempt-conquest-computer [0 0] [1 0]))]
        (should-be-nil result)
        (should= :computer (:city-status (get-in (test-utils/read-test-state :game-map) [1 0])))
        (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))
        (should= :army (:item (get (test-utils/read-test-state :production) [1 0])))
        (should= {41 1} (test-utils/read-test-state :computer-unit-round-conquests))
        (let [entry (first (test-utils/read-test-state :computer-event-log))]
          (should= :army-conquest-success (:event entry))
          (should= [1 0] (:city entry))
          (should= [0 0] (:continent-id entry))
          (should= 41 (:computer-unit-id entry))))))

  (it "army dies on failure (rand >= 0.5)"
    (set-test-world! (build-test-map ["a+"]))
    (set-test-computer-map! (build-test-map ["a+"]))
    (with-redefs [rand (constantly 0.9)]
      (let [result (action-resolution/attempt-conquest-computer [0 0] [1 0])]
        (should-be-nil result)
        (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))
        (should= :free (:city-status (get-in (test-utils/read-test-state :game-map) [1 0]))))))

  (it "clears an unanchored stamped region after failed conquest"
    (set-test-world! (build-test-map ["a+"]))
    (update-test-world! assoc-in [0 0 :country-id] 7)
    (update-test-world! assoc-in [0 0 :contents :country-id] 7)
    (update-test-world! assoc-in [1 0 :country-id] 7)
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (with-redefs [rand (constantly 0.9)]
      (action-resolution/attempt-conquest-computer [0 0] [1 0])
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [0 0 :country-id]))
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [1 0 :country-id]))
      (should-be-nil (get-in (test-utils/read-test-state :computer-map) [0 0 :country-id]))
      (should-be-nil (get-in (test-utils/read-test-state :computer-map) [1 0 :country-id]))))

  (it "restamps cleared territory when another computer army anchors the region"
    (set-test-world! (build-test-map ["a+a"]))
    (update-test-world! assoc-in [0 0 :country-id] 7)
    (update-test-world! assoc-in [0 0 :contents :country-id] 7)
    (update-test-world! assoc-in [1 0 :country-id] 7)
    (update-test-world! assoc-in [2 0 :country-id] 7)
    (update-test-world! assoc-in [2 0 :contents :country-id] 7)
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (with-redefs [rand (constantly 0.9)]
      (action-resolution/attempt-conquest-computer [0 0] [1 0])
      (should= 7 (get-in (test-utils/read-test-state :game-map) [0 0 :country-id]))
      (should= 7 (get-in (test-utils/read-test-state :game-map) [1 0 :country-id]))
      (should= 7 (get-in (test-utils/read-test-state :computer-map) [0 0 :country-id]))
      (should= 7 (get-in (test-utils/read-test-state :computer-map) [1 0 :country-id]))))

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
