(ns empire.game-loop.item-processing-decisions-spec
  (:require [empire.game.loop.item-processing-decisions :as decisions]
            [speclj.core :refer :all]))

(describe "item processing decisions"
  (it "normalizes flat numeric queues"
    (should= [[0 0] [1 2]]
             (decisions/normalize-item-queue [0 0 1 2]))
    (should= [[0 0]]
             (decisions/normalize-item-queue [0 0])))

  (it "recognizes satellite and unit auto modes"
    (should (decisions/satellite-with-target? {:type :satellite :target [1 1]}))
    (should-not (decisions/satellite-with-target? {:type :satellite}))
    (should (decisions/unit-auto-mode? {:mode :moving}))
    (should-not (decisions/unit-auto-mode? {:mode :awake})))

  (it "prioritizes item actions"
    (should= :skip-satellite (decisions/player-item-action {:sat-moving? true}))
    (should= :auto-move (decisions/player-item-action {:auto-coords [1 1]}))
    (should= :attention (decisions/player-item-action {:needs-attention? true}))
    (should= :auto-move (decisions/player-item-action {}))))
