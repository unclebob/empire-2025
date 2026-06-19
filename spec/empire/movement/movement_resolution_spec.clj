(ns empire.game-mechanics.movement.movement-resolution-spec
  (:require [empire.test.utils :as test-utils]
            [empire.game-mechanics.movement.movement-resolution :as resolution]
            [empire.game-mechanics.visibility :as visibility]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-world! update-test-world!]]
            [speclj.core :refer :all]))

(describe "movement-resolution"
  (before
    (reset-all-atoms!)
    (test-utils/set-test-state! :command-message ""))

  (it "sidesteps around a friendly city for army movement"
    (set-test-world! (build-test-map ["AO#" "###"]))
    (let [cell {:type :land :contents {:type :army :owner :player :mode :moving :target [2 0] :steps-remaining 1}}
          current-map (test-utils/game-map-atom)]
      (with-redefs [empire.game-mechanics.movement.movement-pathing/find-best-sidestep (constantly [0 1])
                    visibility/update-cell-visibility (fn [& _] nil)]
        (should= :sidestep (:result (resolution/move-unit [0 0] [2 0] cell current-map))))))

  (it "sidesteps around non-target city for fighter movement"
    (set-test-world! (build-test-map ["FX#" "###"]))
    (let [cell {:type :land :contents {:type :fighter :owner :player :mode :moving :target [2 0] :fuel 10 :steps-remaining 1}}
          current-map (test-utils/game-map-atom)]
      (with-redefs [empire.game-mechanics.movement.movement-pathing/find-best-sidestep (constantly [0 1])
                    visibility/update-cell-visibility (fn [& _] nil)]
        (should= :sidestep (:result (resolution/move-unit [0 0] [2 0] cell current-map))))))

  (it "treats friendly blocking as sidestep-or-wake path"
    (set-test-world! (build-test-map ["AA#"]))
    (let [cell {:type :land :contents {:type :army :owner :player :mode :moving :target [2 0] :steps-remaining 1}}
          current-map (test-utils/game-map-atom)]
      (with-redefs [empire.game-mechanics.movement.wake-conditions/wake-before-move
                    (fn [_ _] [{:type :army :owner :player :mode :awake :reason :somethings-in-the-way} true])
                    empire.game-mechanics.movement.movement-pathing/find-best-sidestep (constantly nil)
                    visibility/update-cell-visibility (fn [& _] nil)]
        (should= :woke (:result (resolution/move-unit [0 0] [2 0] cell current-map))))))

  (it "uses combat path when enemy blocks and terrain is attackable"
    (set-test-world! (build-test-map ["Aa#"]))
    (let [cell {:type :land :contents {:type :army :owner :player :mode :moving :target [2 0] :steps-remaining 1}}
          current-map (test-utils/game-map-atom)]
      (with-redefs [empire.game-mechanics.movement.wake-conditions/wake-before-move
                    (fn [_ _] [{:type :army :owner :player :mode :awake :reason :somethings-in-the-way} true])
                    empire.config.units.dispatcher/can-move-to? (fn [_ _] true)
                    empire.game-mechanics.services.combat/attempt-attack (fn [_ _ _] true)
                    visibility/update-cell-visibility (fn [& _] nil)]
        (should= :combat (:result (resolution/move-unit [0 0] [2 0] cell current-map))))))

  (it "does not enter combat path when enemy blocker is a satellite"
    (set-test-world! (build-test-map ["Av#"]))
    (let [cell {:type :land :contents {:type :army :owner :player :mode :moving :target [2 0] :steps-remaining 1}}
          current-map (test-utils/game-map-atom)
          attack-called? (atom false)]
      (with-redefs [empire.game-mechanics.movement.wake-conditions/wake-before-move
                    (fn [_ _] [{:type :army :owner :player :mode :awake :reason :somethings-in-the-way} true])
                    empire.config.units.dispatcher/can-move-to? (fn [_ _] true)
                    empire.game-mechanics.services.combat/attempt-attack (fn [_ _ _] (reset! attack-called? true) true)
                    visibility/update-cell-visibility (fn [& _] nil)]
        (should= :woke (:result (resolution/move-unit [0 0] [2 0] cell current-map)))
        (should-not @attack-called?))))

  (it "does not set :extended when using 2-arity set-unit-movement"
    (set-test-world! (build-test-map ["A##"]))
    (update-test-world! assoc-in [0 0 :contents]
                        {:type :army :owner :player :mode :awake :hits 1})
    (resolution/set-unit-movement [0 0] [2 0])
    (should-be-nil (get-in (test-utils/read-test-state :game-map) [0 0 :contents :extended])))

  (it "clamps out-of-bounds movement target to right edge"
    (set-test-world! (build-test-map ["~~~"
                                      "~F~"
                                      "~~~"]))
    (let [cell (get-in (test-utils/read-test-state :game-map) [1 1])]
      (should= [2 1] (:pos (resolution/move-unit [1 1] [99 1] cell (test-utils/game-map-atom))))))

  (it "clamps out-of-bounds movement target to left edge"
    (set-test-world! (build-test-map ["~~~"
                                      "~F~"
                                      "~~~"]))
    (let [cell (get-in (test-utils/read-test-state :game-map) [1 1])]
      (should= [0 1] (:pos (resolution/move-unit [1 1] [-99 1] cell (test-utils/game-map-atom))))))

  (it "clamps out-of-bounds movement target to top edge"
    (set-test-world! (build-test-map ["~~~"
                                      "~F~"
                                      "~~~"]))
    (let [cell (get-in (test-utils/read-test-state :game-map) [1 1])]
      (should= [1 0] (:pos (resolution/move-unit [1 1] [1 -99] cell (test-utils/game-map-atom))))))

  (it "clamps out-of-bounds movement target to bottom edge"
    (set-test-world! (build-test-map ["~~~"
                                      "~F~"
                                      "~~~"]))
    (let [cell (get-in (test-utils/read-test-state :game-map) [1 1])]
      (should= [1 2] (:pos (resolution/move-unit [1 1] [1 99] cell (test-utils/game-map-atom))))))

  (it "applies movement target clamping consistently across unit types"
    (set-test-world! (build-test-map ["~~~"
                                      "~~~"
                                      "~~~"]))
    (let [unit-types [:army :fighter :battleship]
          clamped-positions
          (with-redefs [empire.game-mechanics.containers.helpers/ship-can-dock? (constantly false)
                        empire.game-mechanics.movement.wake-conditions/wake-before-move (fn [u _] [u false])
                        empire.game-mechanics.movement.wake-conditions/wake-after-move (fn [u _ _ _] u)
                        empire.game-mechanics.movement.movement-execution/do-move (fn [& _] nil)]
            (mapv (fn [unit-type]
                    (let [cell {:type :sea
                                :contents {:type unit-type :owner :player :mode :moving :target [99 99] :steps-remaining 1}}]
                      [(:pos (resolution/move-unit [1 1] [-99 1] cell (test-utils/game-map-atom)))
                       (:pos (resolution/move-unit [1 1] [99 1] cell (test-utils/game-map-atom)))
                       (:pos (resolution/move-unit [1 1] [1 -99] cell (test-utils/game-map-atom)))
                       (:pos (resolution/move-unit [1 1] [1 99] cell (test-utils/game-map-atom)))]))
                  unit-types))]
      (should= 3 (count clamped-positions))
      (should= (repeat 3 [[0 1] [2 1] [1 0] [1 2]]) clamped-positions))))
