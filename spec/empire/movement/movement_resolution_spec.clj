(ns empire.movement.movement-resolution-spec
  (:require [empire.atoms :as atoms]
            [empire.movement.movement-resolution :as resolution]
            [empire.movement.visibility :as visibility]
            [empire.test-utils :refer [build-test-map reset-all-atoms!]]
            [speclj.core :refer :all]))

(describe "movement-resolution"
  (before
    (reset-all-atoms!)
    (reset! atoms/turn-message ""))

  (it "sidesteps around a friendly city for army movement"
    (reset! atoms/game-map (build-test-map ["AO#" "###"]))
    (let [cell {:type :land :contents {:type :army :owner :player :mode :moving :target [2 0] :steps-remaining 1}}
          current-map atoms/game-map]
      (with-redefs [empire.movement.movement-pathing/find-best-sidestep (constantly [0 1])
                    visibility/update-cell-visibility (fn [& _] nil)]
        (should= :sidestep (:result (resolution/move-unit [0 0] [2 0] cell current-map))))))

  (it "sidesteps around non-target city for fighter movement"
    (reset! atoms/game-map (build-test-map ["FX#" "###"]))
    (let [cell {:type :land :contents {:type :fighter :owner :player :mode :moving :target [2 0] :fuel 10 :steps-remaining 1}}
          current-map atoms/game-map]
      (with-redefs [empire.movement.movement-pathing/find-best-sidestep (constantly [0 1])
                    visibility/update-cell-visibility (fn [& _] nil)]
        (should= :sidestep (:result (resolution/move-unit [0 0] [2 0] cell current-map))))))

  (it "treats friendly blocking as sidestep-or-wake path"
    (reset! atoms/game-map (build-test-map ["AA#"]))
    (let [cell {:type :land :contents {:type :army :owner :player :mode :moving :target [2 0] :steps-remaining 1}}
          current-map atoms/game-map]
      (with-redefs [empire.movement.wake-conditions/wake-before-move
                    (fn [_ _] [{:type :army :owner :player :mode :awake :reason :somethings-in-the-way} true])
                    empire.movement.movement-pathing/find-best-sidestep (constantly nil)
                    visibility/update-cell-visibility (fn [& _] nil)]
        (should= :woke (:result (resolution/move-unit [0 0] [2 0] cell current-map))))))

  (it "uses combat path when enemy blocks and terrain is attackable"
    (reset! atoms/game-map (build-test-map ["Aa#"]))
    (let [cell {:type :land :contents {:type :army :owner :player :mode :moving :target [2 0] :steps-remaining 1}}
          current-map atoms/game-map]
      (with-redefs [empire.movement.wake-conditions/wake-before-move
                    (fn [_ _] [{:type :army :owner :player :mode :awake :reason :somethings-in-the-way} true])
                    empire.units.dispatcher/can-move-to? (fn [_ _] true)
                    empire.combat/attempt-attack (fn [_ _] true)
                    visibility/update-cell-visibility (fn [& _] nil)]
        (should= :combat (:result (resolution/move-unit [0 0] [2 0] cell current-map))))))

  (it "does not set :extended when using 2-arity set-unit-movement"
    (reset! atoms/game-map (build-test-map ["A##"]))
    (swap! atoms/game-map assoc-in [0 0 :contents]
           {:type :army :owner :player :mode :awake :hits 1})
    (resolution/set-unit-movement [0 0] [2 0])
    (should-be-nil (get-in @atoms/game-map [0 0 :contents :extended]))))
