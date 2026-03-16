(ns empire.game-loop.computer-item-decisions-spec
  (:require [empire.game.loop.item-processing.computer-item-decisions :as sut]
            [speclj.core :refer :all]))

(describe "computer item decisions"
  (it "normalizes a raw coord pair into a one-item queue"
    (should= [[0 0]]
             (sut/normalize-computer-items [0 0])))

  (it "chooses launch when airport launch produced a new position"
    (should= {:action :launch
              :requeue-city? true
              :launched-pos [1 0]}
             (sut/computer-item-action {:cell {:type :city :city-status :computer}
                                        :launched-pos [1 0]
                                        :should-requeue-city? true})))

  (it "chooses unit continuation when unit processing returns new coords"
    (should= {:action :unit-continue :new-coords [2 0]}
             (sut/computer-item-action {:cell {:contents {:owner :computer}}
                                        :new-coords [2 0]})))

  (it "requeues launched unit and source city when requested"
    (should= [[0 0] [1 0] [2 2]]
             (sut/next-computer-items [[0 0] [2 2]]
                                      {:action :launch
                                       :requeue-city? true
                                       :launched-pos [1 0]})))

  (it "produces the next queue and continue result from action"
    (should= {:computer-items [[1 0] [2 2]]
              :result :continue}
             (sut/computer-item-state {:items [[0 0] [2 2]]
                                       :action {:action :unit-continue
                                                :new-coords [1 0]}}))))
