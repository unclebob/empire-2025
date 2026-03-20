(ns empire.computer.transport.targeting-decisions-spec
  (:require [empire.computer.transport.targeting-decisions :as sut]
            [speclj.core :refer :all]))

(describe "transport targeting decisions"
  (it "prefers an unclaimed target when available"
    (should= {:best [3 3] :claimed #{[3 3] [5 5]}}
             (sut/claimed-target-choice [[3 3] [5 5]]
                                        #{[5 5]}
                                        { [3 3] 1 [5 5] 2 })))

  (it "chooses the nearest army on a qualifying pickup continent"
    (should= [2 2]
             (sut/pickup-continent-choice
              [0 0]
              [{:continent #{[2 2] [2 3]} :armies [[2 2] [2 3] [3 3] [4 4]]}
               {:continent #{[8 8]} :armies [[8 8] [8 9] [9 9] [9 8]]}]
              3
              (fn [[ax ay] [bx by]] (+ (Math/abs ^long (- ax bx))
                                       (Math/abs ^long (- ay by))))))))
