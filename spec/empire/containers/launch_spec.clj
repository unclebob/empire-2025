(ns empire.containers.launch-spec
  (:require [empire.game-mechanics.containers.launch :as launch]
            [speclj.core :refer :all]))

(describe "launch-steps-toward"
  (it "orders nearest launch cells first toward the target"
    (should= [[3 2] [3 1] [3 3] [1 2] [2 1] [2 3] [1 1] [1 3]]
             (launch/launch-steps-toward [2 2] [4 2]))))
