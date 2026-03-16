(ns empire.computer.army-assignment-decisions-spec
  (:require [empire.computer.army.assignment-decisions :as sut]
            [speclj.core :refer :all]))

(describe "city-attack-assignments"
  (it "collects assignable non-coast-walk computer armies"
    (let [world [[{:type :land
                   :contents {:type :army :owner :computer :mode :awake}}
                  {:type :land
                   :contents {:type :army :owner :computer :mode :coast-walk}}]
                 [{:type :land
                   :contents {:type :army :owner :player :mode :awake}}
                  {:type :land}]]]
      (should= [{:pos [0 0] :unit {:type :army :owner :computer :mode :awake}}]
               (vec (sut/assignable-armies world)))))

  (it "collects visible free and player target cities"
    (let [computer-map [[{:type :city :city-status :player}
                         {:type :city :city-status :computer}]
                        [nil
                         {:type :city :city-status :free}]]]
      (should= [[0 0] [1 1]]
               (vec (sut/visible-target-cities computer-map)))))

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
               (set (:assignments result)))))

  (it "returns assignment updates directly"
    (should= [{:pos [0 0] :target [9 9]}]
             (sut/assignment-updates [[9 9]]
                                     [{:pos [0 0]}]
                                     contains?
                                     (constantly #{[0 0]})
                                     (constantly 1)))))
