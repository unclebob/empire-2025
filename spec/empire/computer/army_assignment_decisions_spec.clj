(ns empire.computer.army-assignment-decisions-spec
  (:require [empire.computer.army.assignment-decisions :as sut]
            [speclj.core :refer :all]))

(describe "city-attack-assignments"
  (it "assigns up to six reachable armies per city without reusing armies"
    (let [armies (mapv (fn [n] {:pos [n 0]}) (range 8))
          cities [[10 10] [20 20]]
          result (sut/city-attack-assignments cities
                                              armies
                                              contains?
                                              (fn [city]
                                                (if (= city [10 10])
                                                  #{[0 0] [1 0] [2 0] [3 0] [4 0] [5 0] [6 0]}
                                                  #{[7 0]}))
                                              (fn [[a _] [b _]] (Math/abs ^long (- a b))))]
      (should= 7 (count (:assignments result)))
      (should= #{{:pos [1 0] :target [10 10]}
                 {:pos [2 0] :target [10 10]}
                 {:pos [3 0] :target [10 10]}
                 {:pos [4 0] :target [10 10]}
                 {:pos [5 0] :target [10 10]}
                 {:pos [6 0] :target [10 10]}
                 {:pos [7 0] :target [20 20]}}
               (set (:assignments result))))))
