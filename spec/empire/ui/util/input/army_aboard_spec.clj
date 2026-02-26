(ns empire.ui.util.input.army-aboard-spec
  (:require [speclj.core :refer :all]
            [empire.ui.util.input.actions :as actions]
            [empire.atoms :as atoms]
            [empire.combat :as combat]
            [empire.containers.ops :as container-ops]
            [empire.game-loop :as game-loop]
            [empire.test-utils :refer [build-test-map reset-all-atoms!]]))

(describe "handle-army-aboard-movement"
  (before (reset-all-atoms!))

  (context "disembark to empty land (not extended)"
    (it "calls disembark-army-from-transport and item-processed"
      (reset! atoms/game-map (build-test-map ["t#"]))
      (let [disembarked (atom nil)
            processed (atom false)]
        (with-redefs [container-ops/disembark-army-from-transport
                      (fn [from to] (reset! disembarked {:from from :to to}))
                      game-loop/item-processed (fn [] (reset! processed true))]
          (#'actions/handle-army-aboard-movement [0 0] [1 0] [1 0] false
                                                (get-in @atoms/game-map [1 0])))
        (should= {:from [0 0] :to [1 0]} @disembarked)
        (should= true @processed))))

  (context "disembark with extended target"
    (it "calls disembark-army-with-target and item-processed"
      (reset! atoms/game-map (build-test-map ["t#"]))
      (let [disembarked (atom nil)
            processed (atom false)]
        (with-redefs [container-ops/disembark-army-with-target
                      (fn [from adj tgt] (reset! disembarked {:from from :adj adj :tgt tgt}))
                      game-loop/item-processed (fn [] (reset! processed true))]
          (#'actions/handle-army-aboard-movement [0 0] [1 0] [5 0] true
                                                (get-in @atoms/game-map [1 0])))
        (should= {:from [0 0] :adj [1 0] :tgt [5 0]} @disembarked)
        (should= true @processed))))

  (context "conquest of hostile city"
    (it "removes army and attempts city conquest"
      (reset! atoms/game-map (build-test-map ["tX"]))
      (let [removed (atom false)
            conquered (atom nil)
            processed (atom false)]
        (with-redefs [container-ops/remove-army-from-transport
                      (fn [coords] (reset! removed true))
                      combat/attempt-city-conquest
                      (fn [coords] (reset! conquered coords))
                      game-loop/item-processed (fn [] (reset! processed true))]
          (#'actions/handle-army-aboard-movement [0 0] [1 0] [1 0] false
                                                (get-in @atoms/game-map [1 0])))
        (should= true @removed)
        (should= [1 0] @conquered)
        (should= true @processed))))

  (context "ignore (target is sea or occupied land)"
    (it "takes no action and returns true"
      (reset! atoms/game-map (build-test-map ["t~"]))
      (let [processed (atom false)]
        (with-redefs [game-loop/item-processed (fn [] (reset! processed true))]
          (let [result (#'actions/handle-army-aboard-movement [0 0] [1 0] [1 0] false
                                                             (get-in @atoms/game-map [1 0]))]
            (should= true result)
            (should= false @processed)))))))

(run-specs)
