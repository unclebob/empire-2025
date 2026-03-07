(ns empire.computer.threat-response-invasion-decision-spec
  (:require [speclj.core :refer :all]
            [empire.computer.threat-response.invasion-decision :as decision]
            [empire.test.utils :refer [build-test-map]]))

(describe "threat-response invasion-decision"
  (it "defers when no sea path exists to any detected target"
    (let [world (build-test-map ["aO"])
          computer-map world
          result (decision/evaluate-invasion-start
                  {:world world
                   :computer-map computer-map
                   :detection-points #{[1 0]}
                   :computer-sea-unit-types #{:transport :patrol-boat :destroyer}})]
      (should= :deferred (:decision result))
      (should= :no-sea-path (:failure-reason result))))

  (it "defers when no transports and fewer than six armies"
    (let [world (build-test-map ["paaaaO"])
          computer-map (build-test-map ["p~~~~O"])
          result (decision/evaluate-invasion-start
                  {:world world
                   :computer-map computer-map
                   :detection-points #{[5 0]}
                   :computer-sea-unit-types #{:transport :patrol-boat :destroyer}})]
      (should= :deferred (:decision result))
      (should= :insufficient-resources (:failure-reason result))))

  (it "is ready when a sea path exists and resources meet minimum"
    (let [world (build-test-map ["t~~~~O"])
          computer-map world
          result (decision/evaluate-invasion-start
                  {:world world
                   :computer-map computer-map
                   :detection-points #{[5 0]}
                   :computer-sea-unit-types #{:transport :patrol-boat :destroyer}})]
      (should= :ready (:decision result))
      (should-be-nil (:failure-reason result))))

  (it "counts armies in transit to target continent from invasion transports"
    (let [world [[{:type :sea :contents {:type :transport :owner :computer :major-invasion true
                                         :transport-mission :invading :army-count 3
                                         :invasion-target [2 2]}}]]
          target-land #{[2 2] [2 3]}]
      (should= 3 (decision/armies-in-transports-to-target-continent world target-land))))

  (it "counts computer armies currently on target continent"
    (let [world (build-test-map ["a~"
                                 "~a"])
          target-land #{[0 0]}]
      (should= 1 (decision/invasion-armies-on-target-continent world target-land)))))
