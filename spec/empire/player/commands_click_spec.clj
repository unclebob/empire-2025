(ns empire.player.commands-click-spec
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

(describe "handle-cell-click (mutation tests)"
  (before (reset-all-atoms!))

  (context "click adjacent hostile city (L221, L231, L259)"
    (it "attempts conquest when clicking diagonally adjacent hostile city"
      (set-test-world! (build-test-map ["###" "###" "###"]))
      (update-test-world! assoc-in [1 1 :contents]
                         {:type :army :mode :awake :owner :player :hits 1})
      (update-test-world! assoc-in [2 2] {:type :city :city-status :computer})
      (setup-unit-attention [1 1])
      (let [conquest-called (atom false)]
        (with-redefs [combat/attempt-conquest (fn [_ _ _] (reset! conquest-called true))
                      movement/set-unit-movement (fn [_ _] nil)
                      game-loop/item-processed (fn [])]
          (commands/handle-cell-click 2 2)
          (should @conquest-called)))))

  (context "click to disembark army aboard (L224, L225)"
    (it "disembarks army when clicking adjacent land"
      (set-test-world! (build-test-map ["~~~" "~T#" "~~~"]))
      (set-test-unit (test-utils/game-map-atom) "T" :mode :sentry :army-count 2 :awake-armies 1)
      (setup-unit-attention [1 1])
      (let [disembark-called (atom false)]
        (with-redefs [container-ops/disembark-army-from-transport
                      (fn [_ _] (reset! disembark-called true))
                      game-loop/item-processed (fn [])]
          (commands/handle-cell-click 2 1)
          (should @disembark-called))))))
