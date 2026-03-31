(ns empire.player.commands-movement-spec
  (:require [empire.game.loop.core :as game-loop]
            [empire.game-mechanics.containers.ops :as container-ops]
            [empire.game-mechanics.services.combat :as combat]
            [empire.game-mechanics.movement.api :as movement]
            [empire.player.commands :as commands]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-unit set-test-world! update-test-world!]]
            [speclj.core :refer :all]))

(defn- setup-unit-attention
  [coords]
  (test-utils/set-test-state! :cells-needing-attention [coords])
  (test-utils/set-test-state! :player-items (list coords)))

(describe "handle-key - movement (mutation tests)"
  (before (reset-all-atoms!))

  (context "extended NW target calculation (L48-L50)"
    (it "reaches [0,0] from center of 5x5 map"
      (set-test-world! (build-test-map ["#####" "#####" "#####" "#####" "#####"]))
      (update-test-world! assoc-in [2 2 :contents]
                         {:type :army :mode :awake :owner :player :hits 1})
      (setup-unit-attention [2 2])
      (let [target (atom nil)]
        (with-redefs [movement/set-unit-movement (fn [_ to] (reset! target to))
                      game-loop/item-processed (fn [])]
          (commands/handle-key :Q)
          (should= [0 0] @target)))))

  (context "extended SE target at column edge (L50)"
    (it "reaches [4,4] from [0,0] on 5x5 map"
      (set-test-world! (build-test-map ["#####" "#####" "#####" "#####" "#####"]))
      (update-test-world! assoc-in [0 0 :contents]
                         {:type :army :mode :awake :owner :player :hits 1})
      (setup-unit-attention [0 0])
      (let [target (atom nil)]
        (with-redefs [movement/set-unit-movement (fn [_ to] (reset! target to))
                      game-loop/item-processed (fn [])]
          (commands/handle-key :C)
          (should= [4 4] @target)))))

  (context "extended south target at row edge (L50)"
    (it "reaches [0,4] from [0,0] on 5x5 map"
      (set-test-world! (build-test-map ["#####" "#####" "#####" "#####" "#####"]))
      (update-test-world! assoc-in [0 0 :contents]
                         {:type :army :mode :awake :owner :player :hits 1})
      (setup-unit-attention [0 0])
      (let [target (atom nil)]
        (with-redefs [movement/set-unit-movement (fn [_ to] (reset! target to))
                      game-loop/item-processed (fn [])]
          (commands/handle-key :X)
          (should= [0 4] @target)))))

  (context "standard diagonal movement (L105, L107, L110)"
    (it "moves army NE to correct adjacent cell"
      (set-test-world! (build-test-map ["###" "###" "###"]))
      (update-test-world! assoc-in [1 1 :contents]
                         {:type :army :mode :awake :owner :player :hits 1})
      (setup-unit-attention [1 1])
      (let [target (atom nil)]
        (with-redefs [movement/set-unit-movement (fn [_ to] (reset! target to))
                      game-loop/item-processed (fn [])]
          (commands/handle-key :e)
          (should= [2 0] @target)))))

  (context "extended east uses calculated target (L112)"
    (it "reaches far east edge"
      (set-test-world! (build-test-map ["#####"]))
      (update-test-world! assoc-in [0 0 :contents]
                         {:type :army :mode :awake :owner :player :hits 1})
      (setup-unit-attention [0 0])
      (let [target (atom nil)]
        (with-redefs [movement/set-unit-movement (fn [_ to] (reset! target to))
                      game-loop/item-processed (fn [])]
          (commands/handle-key :D)
          (should= [4 0] @target)))))

  (context "airport fighter launch (L56)"
    (it "resets waiting-for-input to false"
      (set-test-world! (build-test-map ["O#"]))
      (update-test-world! assoc-in [0 0 :awake-fighters] 1)
      (update-test-world! assoc-in [0 0 :fighter-count] 1)
      (setup-unit-attention [0 0])
      (test-utils/set-test-state! :production {[0 0] {:item :army :remaining-rounds 5}})
      (test-utils/set-test-state! :waiting-for-input true)
      (with-redefs [container-ops/launch-fighter-from-airport (fn [_ _] [1 0])]
        (commands/handle-key :d)
        (should= false (test-utils/read-test-state :waiting-for-input)))))

  (context "army aboard disembark via key (L63-L65)"
    (it "calls disembark for non-extended direction toward land"
      (set-test-world! (build-test-map ["T#"]))
      (set-test-unit (test-utils/game-map-atom) "T" :mode :sentry :army-count 2 :awake-armies 1)
      (setup-unit-attention [0 0])
      (let [disembark-called (atom false)]
        (with-redefs [container-ops/disembark-army-from-transport
                      (fn [_ _] (reset! disembark-called true))
                      container-ops/disembark-army-with-target
                      (fn [_ _ _] nil)
                      game-loop/item-processed (fn [])]
          (commands/handle-key :d)
          (should @disembark-called)))))

  (context "undamaged ship at friendly city (L76-L78)"
    (it "shows error for undamaged destroyer entering player city"
      (set-test-world! (build-test-map ["~O"]))
      (update-test-world! assoc-in [0 0 :contents]
                         {:type :destroyer :mode :awake :owner :player :hits 3})
      (setup-unit-attention [0 0])
      (with-redefs [movement/set-unit-movement (fn [_ _] nil)
                    game-loop/item-processed (fn [])]
        (commands/handle-key :d)
        (should-contain "not damaged" (test-utils/read-test-state :warning-message)))))

  (context "army conquest via direction key (L82)"
    (it "attempts conquest when moving to hostile city"
      (set-test-world! (build-test-map ["A+"]))
      (set-test-unit (test-utils/game-map-atom) "A" :mode :awake)
      (setup-unit-attention [0 0])
      (let [conquest-called (atom false)]
        (with-redefs [combat/attempt-conquest (fn [_ _ _] (reset! conquest-called true))
                      game-loop/item-processed (fn [])]
          (commands/handle-key :d)
          (should @conquest-called)))))

  (context "coastal army attack via direction key"
    (it "attacks hostile ship from the coast"
      (set-test-world! (build-test-map ["Ad"]))
      (set-test-unit (test-utils/game-map-atom) "A" :mode :awake)
      (set-test-unit (test-utils/game-map-atom) "d" :owner :computer :hits 3)
      (setup-unit-attention [0 0])
      (let [attack-called (atom false)]
        (with-redefs [combat/attempt-coastal-army-attack (fn [_ _ _] (reset! attack-called true) {})
                      combat/apply-combat-result! (fn [_] nil)
                      game-loop/item-processed (fn [])]
          (commands/handle-key :d)
          (should @attack-called)))))

  (context "fighter overfly via direction key (L87)"
    (it "attempts overfly when moving to hostile city"
      (set-test-world! (build-test-map ["F+"]))
      (set-test-unit (test-utils/game-map-atom) "F" :mode :awake)
      (setup-unit-attention [0 0])
      (let [overfly-called (atom false)]
        (with-redefs [combat/attempt-fighter-overfly (fn [_ _ _] (reset! overfly-called true))
                      movement/set-unit-movement (fn [_ _] nil)
                      game-loop/item-processed (fn [])]
          (commands/handle-key :d)
          (should @overfly-called)))))

  (context "carrier-fighter launch via direction key"
    (it "launches fighter from carrier when carrier has awake fighters"
      (set-test-world! (build-test-map ["C~"]))
      (set-test-unit (test-utils/game-map-atom) "C" :mode :sentry :fighter-count 1 :awake-fighters 1)
      (setup-unit-attention [0 0])
      (let [launch-called (atom false)]
        (with-redefs [container-ops/launch-fighter-from-carrier (fn [_ _] (reset! launch-called true) [1 0])]
          (commands/handle-key :d)
          (should @launch-called)))))

  (context "non-player unit at attention cell"
    (it "returns nil when unit is computer-owned"
      (set-test-world! (build-test-map ["A#"]))
      (update-test-world! assoc-in [0 0 :contents]
                         {:type :army :mode :awake :owner :computer :hits 1})
      (setup-unit-attention [0 0])
      (should-be-nil (commands/handle-key :d)))))
