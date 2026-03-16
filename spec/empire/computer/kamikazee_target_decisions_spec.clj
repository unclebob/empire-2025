(ns empire.computer.kamikazee-target-decisions-spec
  (:require [empire.computer.threat-response.kamikazee-target-decisions :as sut]
            [speclj.core :refer :all]))

(describe "kamikazee target decisions"
  (it "trims dead army targets from invasion state"
    (let [world [[{:type :land
                   :contents {:type :army :owner :player}}]
                 [{:type :land}]]]
      (should= {:kamikazee-army-targets [{:pos [0 0] :seen-round 2}]}
               (sut/trim-dead-army-targets-state
                world
                {:kamikazee-army-targets [{:pos [0 0] :seen-round 2}
                                          {:pos [1 0] :seen-round 3}]}))))

  (it "finds all kamikazee fighters that should receive refreshed targets"
    (let [world [[{:type :land
                   :contents {:type :fighter :owner :computer :kamikazee true}}]
                 [{:type :land
                   :contents {:type :fighter :owner :computer}}]]]
      (should= [{:pos [0 0] :targets [[8 8]]}]
               (sut/fighter-target-writes world [[8 8]])))))
