(ns empire.ui.util.input.army-aboard-spec
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.ui.util.input.actions :as actions]
            [empire.ui.util.input.actions.movement :as actions-movement]
            [empire.game-mechanics.services.combat :as combat]
            [empire.game-mechanics.containers.ops :as container-ops]
            [empire.game.loop.core :as game-loop]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-world!]]))

(describe "handle-army-aboard-movement"
  (before (reset-all-atoms!))

  (context "disembark to empty land (not extended)"
    (it "calls disembark-army-from-transport and item-processed"
      (set-test-world! (build-test-map ["t#"]))
      (let [disembarked (atom nil)
            processed (atom false)]
        (with-redefs [container-ops/disembark-army-from-transport
                      (fn [from to] (reset! disembarked {:from from :to to}))
                      game-loop/item-processed (fn [] (reset! processed true))]
          (#'actions-movement/handle-army-aboard-movement [0 0] [1 0] [1 0] false
                                                (get-in (test-utils/read-test-state :game-map) [1 0])))
        (should= {:from [0 0] :to [1 0]} @disembarked)
        (should= true @processed))))

  (context "disembark with extended target"
    (it "calls disembark-army-with-target and item-processed"
      (set-test-world! (build-test-map ["t#"]))
      (let [disembarked (atom nil)
            processed (atom false)]
        (with-redefs [container-ops/disembark-army-with-target
                      (fn [from adj tgt] (reset! disembarked {:from from :adj adj :tgt tgt}))
                      game-loop/item-processed (fn [] (reset! processed true))]
          (#'actions-movement/handle-army-aboard-movement [0 0] [1 0] [5 0] true
                                                (get-in (test-utils/read-test-state :game-map) [1 0])))
        (should= {:from [0 0] :adj [1 0] :tgt [5 0]} @disembarked)
        (should= true @processed))))

  (context "conquest of hostile city"
    (it "removes army and attempts city conquest"
      (set-test-world! (build-test-map ["tX"]))
      (let [removed (atom false)
            conquered (atom nil)
            processed (atom false)]
        (with-redefs [container-ops/remove-army-from-transport
                      (fn [coords] (reset! removed true))
                      combat/attempt-city-conquest
                      (fn [_world coords] (reset! conquered coords))
                      game-loop/item-processed (fn [] (reset! processed true))]
          (#'actions-movement/handle-army-aboard-movement [0 0] [1 0] [1 0] false
                                                (get-in (test-utils/read-test-state :game-map) [1 0])))
        (should= true @removed)
        (should= [1 0] @conquered)
        (should= true @processed))))

  (context "ignore (target is sea or occupied land)"
    (it "takes no action and returns true"
      (set-test-world! (build-test-map ["t~"]))
      (let [processed (atom false)]
        (with-redefs [game-loop/item-processed (fn [] (reset! processed true))]
          (let [result (#'actions-movement/handle-army-aboard-movement [0 0] [1 0] [1 0] false
                                                             (get-in (test-utils/read-test-state :game-map) [1 0]))]
            (should= true result)
            (should= false @processed)))))))

(run-specs)
