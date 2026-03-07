(ns empire.game-mechanics.services.threat-policy-spec
  (:require [speclj.core :refer :all]
            [empire.game-mechanics.services.threat-policy :as threat-policy]))

(describe "detection-trigger"
  (it "routes player army in stamped country to country-defense trigger"
    (should= :country-defense-trigger
             (threat-policy/detection-trigger
              {:type :land
               :country-id 7
               :contents {:type :army :owner :player}})))

  (it "routes player army without country-id to major-invasion trigger"
    (should= :major-invasion-trigger
             (threat-policy/detection-trigger
              {:type :land
               :contents {:type :army :owner :player}}))))
