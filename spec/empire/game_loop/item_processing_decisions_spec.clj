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
    (should= :auto-move (decisions/player-item-action {})))

  (it "classifies move-result follow-up"
    (should= :advance-step (decisions/resolve-move-result-action {:result :normal}))
    (should= :combat-stop (decisions/combat-move-result-action {:fighter? false :moved-owner-matches? true :fighter-has-steps? false}))
    (should= :fighter-continue (decisions/resolve-move-result-action {:result :combat :fighter? true :moved-owner-matches? true :fighter-has-steps? true}))
    (should= :combat-stop (decisions/resolve-move-result-action {:result :combat :fighter? false :moved-owner-matches? true :fighter-has-steps? false}))
    (should= :stop (decisions/resolve-move-result-action {:result :combat :fighter? true :moved-owner-matches? false :fighter-has-steps? true}))
    (should= :stay-put (decisions/resolve-move-result-action {:result :woke}))
    (should= :stop (decisions/resolve-move-result-action {:result :docked})))

  (it "stops batch processing on pause, empty queue, input wait, or safety limit"
    (should (decisions/batch-stop? {:paused? true :processed 0}))
    (should (decisions/batch-stop? {:no-player-items? true :processed 0}))
    (should (decisions/batch-stop? {:waiting-for-input? true :processed 0}))
    (should (decisions/batch-stop? {:processed 100}))
    (should-not (decisions/batch-stop? {:processed 99}))))
