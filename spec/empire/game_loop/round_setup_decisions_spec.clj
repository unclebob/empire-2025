(ns empire.game-loop.round-setup-decisions-spec
  (:require [empire.game.loop.round-setup-decisions :as decisions]
            [speclj.core :refer :all]))

(describe "round setup decisions"
  (it "collects dead unit effects"
    (let [world [[{:contents {:type :carrier :owner :computer :hits 0}}
                  {:contents {:type :army :owner :player :hits 1}}]]
          effects (vec (decisions/dead-unit-effects world #(<= (:hits %) 0) #(= :carrier (:type %))))]
      (should= [{:pos [0 0]
                 :owner :computer
                 :computer-carrier? true
                 :cell-without-contents {}}]
               effects)))

  (it "collects player step reset effects"
    (let [world [[{:contents {:type :army :owner :player :hits 1}}
                  {:contents {:type :army :owner :computer :hits 1}}]]
          effects (vec (decisions/step-reset-effects world (fn [_ _] 3)))]
      (should= [{:pos [0 0] :steps 3}] effects)))

  (it "defaults player step reset to one when effective speed is nil"
    (let [world [[{:contents {:type :army :owner :player :hits 1}}]]
          effects (vec (decisions/step-reset-effects world (fn [_ _] nil)))]
      (should= [{:pos [0 0] :steps 1}] effects)))

  (it "passes through the satellite move collaborators unchanged"
    (let [plan (decisions/move-satellites-plan {:current-world :world
                                                :update-game-map! :update
                                                :update-visibility! :vis
                                                :move-satellite :move
                                                :satellite-speed 4})]
      (should= {:current-world :world
                :update-game-map! :update
                :update-visibility! :vis
                :move-satellite :move
                :satellite-speed 4}
               plan))))
