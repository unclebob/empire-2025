(ns empire.movement.movement-resolution-spec
  (:require [empire.atoms :as atoms]
            [empire.movement.movement-resolution :as resolution]
            [empire.movement.visibility :as visibility]
            [empire.test-utils :refer [build-test-map reset-all-atoms! set-test-world! update-test-world!]]
            [speclj.core :refer :all]))

(describe "movement-resolution"
  (before
    (reset-all-atoms!)
    (reset! atoms/turn-message ""))

  (it "sidesteps around a friendly city for army movement"
    (set-test-world! (build-test-map ["AO#" "###"]))
    (let [cell {:type :land :contents {:type :army :owner :player :mode :moving :target [2 0] :steps-remaining 1}}
          current-map atoms/game-map]
      (with-redefs [empire.movement.movement-pathing/find-best-sidestep (constantly [0 1])
                    visibility/update-cell-visibility (fn [& _] nil)]
        (should= :sidestep (:result (resolution/move-unit [0 0] [2 0] cell current-map))))))

  (it "sidesteps around non-target city for fighter movement"
    (set-test-world! (build-test-map ["FX#" "###"]))
    (let [cell {:type :land :contents {:type :fighter :owner :player :mode :moving :target [2 0] :fuel 10 :steps-remaining 1}}
          current-map atoms/game-map]
      (with-redefs [empire.movement.movement-pathing/find-best-sidestep (constantly [0 1])
                    visibility/update-cell-visibility (fn [& _] nil)]
        (should= :sidestep (:result (resolution/move-unit [0 0] [2 0] cell current-map))))))

  (it "treats friendly blocking as sidestep-or-wake path"
    (set-test-world! (build-test-map ["AA#"]))
    (let [cell {:type :land :contents {:type :army :owner :player :mode :moving :target [2 0] :steps-remaining 1}}
          current-map atoms/game-map]
      (with-redefs [empire.movement.wake-conditions/wake-before-move
                    (fn [_ _] [{:type :army :owner :player :mode :awake :reason :somethings-in-the-way} true])
                    empire.movement.movement-pathing/find-best-sidestep (constantly nil)
                    visibility/update-cell-visibility (fn [& _] nil)]
        (should= :woke (:result (resolution/move-unit [0 0] [2 0] cell current-map))))))

  (it "uses combat path when enemy blocks and terrain is attackable"
    (set-test-world! (build-test-map ["Aa#"]))
    (let [cell {:type :land :contents {:type :army :owner :player :mode :moving :target [2 0] :steps-remaining 1}}
          current-map atoms/game-map]
      (with-redefs [empire.movement.wake-conditions/wake-before-move
                    (fn [_ _] [{:type :army :owner :player :mode :awake :reason :somethings-in-the-way} true])
                    empire.units.dispatcher/can-move-to? (fn [_ _] true)
                    empire.combat/attempt-attack (fn [_ _] true)
                    visibility/update-cell-visibility (fn [& _] nil)]
        (should= :combat (:result (resolution/move-unit [0 0] [2 0] cell current-map))))))

  (it "does not enter combat path when enemy blocker is a satellite"
    (set-test-world! (build-test-map ["Av#"]))
    (let [cell {:type :land :contents {:type :army :owner :player :mode :moving :target [2 0] :steps-remaining 1}}
          current-map atoms/game-map
          attack-called? (atom false)]
      (with-redefs [empire.movement.wake-conditions/wake-before-move
                    (fn [_ _] [{:type :army :owner :player :mode :awake :reason :somethings-in-the-way} true])
                    empire.units.dispatcher/can-move-to? (fn [_ _] true)
                    empire.combat/attempt-attack (fn [_ _] (reset! attack-called? true) true)
                    visibility/update-cell-visibility (fn [& _] nil)]
        (should= :woke (:result (resolution/move-unit [0 0] [2 0] cell current-map)))
        (should-not @attack-called?))))

  (it "does not set :extended when using 2-arity set-unit-movement"
    (set-test-world! (build-test-map ["A##"]))
    (update-test-world! assoc-in [0 0 :contents]
                        {:type :army :owner :player :mode :awake :hits 1})
    (resolution/set-unit-movement [0 0] [2 0])
    (should-be-nil (get-in @atoms/game-map [0 0 :contents :extended])))

  (it "clamps out-of-bounds movement target to right edge"
    (set-test-world! (build-test-map ["~~~"
                                      "~F~"
                                      "~~~"]))
    (let [cell (get-in @atoms/game-map [1 1])]
      (should= [2 1] (:pos (resolution/move-unit [1 1] [99 1] cell atoms/game-map)))))

  (it "clamps out-of-bounds movement target to left edge"
    (set-test-world! (build-test-map ["~~~"
                                      "~F~"
                                      "~~~"]))
    (let [cell (get-in @atoms/game-map [1 1])]
      (should= [0 1] (:pos (resolution/move-unit [1 1] [-99 1] cell atoms/game-map)))))

  (it "clamps out-of-bounds movement target to top edge"
    (set-test-world! (build-test-map ["~~~"
                                      "~F~"
                                      "~~~"]))
    (let [cell (get-in @atoms/game-map [1 1])]
      (should= [1 0] (:pos (resolution/move-unit [1 1] [1 -99] cell atoms/game-map)))))

  (it "clamps out-of-bounds movement target to bottom edge"
    (set-test-world! (build-test-map ["~~~"
                                      "~F~"
                                      "~~~"]))
    (let [cell (get-in @atoms/game-map [1 1])]
      (should= [1 2] (:pos (resolution/move-unit [1 1] [1 99] cell atoms/game-map)))))

  (it "applies movement target clamping consistently across unit types"
    (set-test-world! (build-test-map ["~~~"
                                      "~~~"
                                      "~~~"]))
    (doseq [unit-type [:army :fighter :battleship]]
      (let [cell {:type :sea
                  :contents {:type unit-type :owner :player :mode :moving :target [99 99] :steps-remaining 1}}]
        (with-redefs [empire.containers.helpers/ship-can-dock? (constantly false)
                      empire.movement.wake-conditions/wake-before-move (fn [u _] [u false])
                      empire.movement.wake-conditions/wake-after-move (fn [u _ _ _] u)
                      empire.movement.movement-execution/do-move (fn [& _] nil)]
          (should= [0 1] (:pos (resolution/move-unit [1 1] [-99 1] cell atoms/game-map)))
          (should= [2 1] (:pos (resolution/move-unit [1 1] [99 1] cell atoms/game-map)))
          (should= [1 0] (:pos (resolution/move-unit [1 1] [1 -99] cell atoms/game-map)))
          (should= [1 2] (:pos (resolution/move-unit [1 1] [1 99] cell atoms/game-map))))))))
