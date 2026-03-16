(ns empire.movement.api-decisions-spec
  (:require [empire.game-mechanics.movement.api-decisions :as sut]
            [speclj.core :refer :all]))

(describe "movement api decisions"
  (it "normalizes move-unit result maps"
    (should= {:result :normal :pos [1 0]}
             (sut/move-unit-result {:result :normal :pos [1 0]})))

  (it "fills in default move-unit result keys"
    (should= {:result nil :pos nil}
             (sut/move-unit-result nil)))

  (it "normalizes set-unit-movement args"
    (should= {:unit-coords [0 0] :target-coords [2 2] :extended? true}
             (sut/set-unit-movement-args [0 0] [2 2] true))))
