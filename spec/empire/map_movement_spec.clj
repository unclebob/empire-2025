(ns empire.map-movement-spec
  (:require [empire.game-mechanics.services.combat :as combat]
            [empire.game.loop.core :as game-loop]
            [empire.game-mechanics.movement.api :as movement]
            [empire.ui.util.input.actions :as input]
            [empire.config.core :as config]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [build-test-map set-test-unit reset-all-atoms! set-test-world! set-test-player-map!]]
            [speclj.core :refer :all]))

(describe "set-unit-movement"
  (before (reset-all-atoms!))

  (it "preserves existing steps-remaining when setting movement"
    (set-test-world! (build-test-map ["F#"]))
    (set-test-unit (test-utils/game-map-atom) "F" :mode :awake :steps-remaining 3)
    (movement/set-unit-movement [0 0] [1 0])
    (let [unit (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))]
      (should= :moving (:mode unit))
      (should= [1 0] (:target unit))
      (should= 3 (:steps-remaining unit))))

  (it "clamps out-of-bounds targets to map edge"
    (set-test-world! (build-test-map ["F##"]))
    (set-test-unit (test-utils/game-map-atom) "F" :mode :awake :steps-remaining 3)
    (movement/set-unit-movement [0 0] [99 99])
    (let [unit (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))]
      (should= :moving (:mode unit))
      (should= [2 0] (:target unit)))))

(describe "move-current-unit"
  (before (reset-all-atoms!))
  (before-all
    (set-test-player-map! {}))

  (it "decrements steps-remaining after each move"
    (set-test-world! (build-test-map ["F##"]))
    (set-test-unit (test-utils/game-map-atom) "F" :mode :moving :target [2 0] :steps-remaining 3)
    (game-loop/move-current-unit [0 0])
    (should= 2 (:steps-remaining (:contents (get-in (test-utils/read-test-state :game-map) [1 0])))))

  (it "returns new coords when steps remain"
    (set-test-world! (build-test-map ["F##"]))
    (set-test-unit (test-utils/game-map-atom) "F" :mode :moving :target [2 0] :steps-remaining 3)
    (should= [1 0] (game-loop/move-current-unit [0 0])))

  (it "returns nil when steps-remaining reaches zero"
    (set-test-world! (build-test-map ["A##"]))
    (set-test-unit (test-utils/game-map-atom) "A" :mode :moving :target [2 0] :steps-remaining 1)
    (should= nil (game-loop/move-current-unit [0 0])))

  (it "returns new coords when unit wakes up with steps remaining"
    (set-test-world! (build-test-map ["A#"]))
    (set-test-unit (test-utils/game-map-atom) "A" :mode :moving :target [1 0] :steps-remaining 3)
    (let [result (game-loop/move-current-unit [0 0])]
      (should= [1 0] result)
      (should= :awake (:mode (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))))))

  (it "returns nil when unit wakes up at target with no steps remaining and no reason"
    (set-test-world! (build-test-map ["A#"]))
    (set-test-unit (test-utils/game-map-atom) "A" :mode :moving :target [1 0] :steps-remaining 1)
    (should= nil (game-loop/move-current-unit [0 0]))
    (let [unit (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))]
      (should= :awake (:mode unit))
      (should= 0 (:steps-remaining unit))))

  (it "limits unit to its rate per round even with new orders"
    (set-test-world! (build-test-map ["A##"]))
    (set-test-unit (test-utils/game-map-atom) "A" :mode :moving :target [1 0] :steps-remaining 1)
    (game-loop/move-current-unit [0 0])
    (should= 0 (:steps-remaining (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))))
    (movement/set-unit-movement [1 0] [2 0])
    (should= 0 (:steps-remaining (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))))
    (should= nil (game-loop/move-current-unit [1 0]))))

(describe "attempt-fighter-overfly"
  (before (reset-all-atoms!))

  (it "shoots down fighter when flying over free city"
    (set-test-world! (build-test-map ["F+"]))
    (set-test-unit (test-utils/game-map-atom) "F" :mode :awake)
    (test-utils/set-test-state! :warning-message "")
    (combat/apply-combat-result! (combat/attempt-fighter-overfly (test-utils/read-test-state :game-map) [0 0] [1 0]))
    (should= nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))
    (let [fighter (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))]
      (should= 0 (:hits fighter))
      (should= 0 (:steps-remaining fighter))
      (should= :awake (:mode fighter))
      (should= :fighter-shot-down (:reason fighter)))
    (should= (:fighter-destroyed-by-city config/messages) (test-utils/read-test-state :warning-message)))

  (it "shoots down fighter when flying over computer city"
    (set-test-world! (build-test-map ["FX"]))
    (set-test-unit (test-utils/game-map-atom) "F" :mode :awake)
    (test-utils/set-test-state! :warning-message "")
    (combat/apply-combat-result! (combat/attempt-fighter-overfly (test-utils/read-test-state :game-map) [0 0] [1 0]))
    (should= nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))
    (let [fighter (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))]
      (should= 0 (:hits fighter))
      (should= 0 (:steps-remaining fighter))
      (should= :awake (:mode fighter))
      (should= :fighter-shot-down (:reason fighter)))
    (should= (:fighter-destroyed-by-city config/messages) (test-utils/read-test-state :warning-message))))

(describe "sentry mode"
  (before (reset-all-atoms!))

  (it "handle-key with 's' puts unit in sentry mode"
    (set-test-world! (build-test-map ["A"]))
    (set-test-unit (test-utils/game-map-atom) "A" :mode :awake)
    (test-utils/set-test-state! :cells-needing-attention [[0 0]])
    (input/handle-key :s)
    (should= :sentry (:mode (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))))

  (it "handle-key with 's' does not put unit in sentry when in city"
    (set-test-world! (assoc-in (build-test-map ["O"])
                               [0 0 :contents]
                               {:type :army :owner :player :mode :awake}))
    (test-utils/set-test-state! :cells-needing-attention [[0 0]])
    (input/handle-key :s)
    (should= :awake (:mode (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))))

  (it "sentry units do not move"
    (set-test-world! (build-test-map ["A#"]))
    (set-test-unit (test-utils/game-map-atom) "A" :mode :sentry)
    (should= nil (game-loop/move-current-unit [0 0]))
    (should= :sentry (:mode (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))))

  (it "consume-sentry-fighter-fuel decrements fuel each round"
    (set-test-world! (build-test-map ["F"]))
    (set-test-unit (test-utils/game-map-atom) "F" :mode :sentry :fuel 20)
    (game-loop/consume-sentry-fighter-fuel)
    (should= 19 (:fuel (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))))

  (it "consume-sentry-fighter-fuel wakes fighter with bingo warning when city in range"
    (set-test-world! (build-test-map ["FO"]))
    (set-test-unit (test-utils/game-map-atom) "F" :mode :sentry :fuel 9)
    (game-loop/consume-sentry-fighter-fuel)
    (let [fighter (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))]
      (should= 8 (:fuel fighter))
      (should= :awake (:mode fighter))
      (should= :fighter-bingo (:reason fighter))))

  (it "consume-sentry-fighter-fuel wakes fighter with out-of-fuel warning"
    (set-test-world! (build-test-map ["F"]))
    (set-test-unit (test-utils/game-map-atom) "F" :mode :sentry :fuel 2)
    (game-loop/consume-sentry-fighter-fuel)
    (let [fighter (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))]
      (should= 1 (:fuel fighter))
      (should= :awake (:mode fighter))
      (should= :fighter-out-of-fuel (:reason fighter))))

  (it "consume-sentry-fighter-fuel kills fighter when fuel hits zero"
    (set-test-world! (build-test-map ["F"]))
    (set-test-unit (test-utils/game-map-atom) "F" :mode :sentry :fuel 1)
    (game-loop/consume-sentry-fighter-fuel)
    (should= 0 (:hits (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))))))
