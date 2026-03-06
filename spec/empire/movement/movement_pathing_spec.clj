(ns empire.game-mechanics.movement.movement-pathing-spec
  (:require [empire.game-mechanics.movement.movement-pathing :as pathing]
            [speclj.core :refer :all]))

(describe "movement-pathing"
  (it "computes next step toward target"
    (should= [3 4] (pathing/next-step-pos [2 3] [5 6]))
    (should= [2 2] (pathing/next-step-pos [2 3] [2 1])))

  (it "computes chebyshev distance"
    (should= 5 (pathing/chebyshev-distance [1 2] [6 4]))
    (should= 3 (pathing/chebyshev-distance [3 3] [0 0])))

  (it "returns vertical sidestep candidates"
    (should= [[1 1] [-1 1] [1 0] [-1 0]]
             (pathing/get-sidestep-directions [0 1]))))
