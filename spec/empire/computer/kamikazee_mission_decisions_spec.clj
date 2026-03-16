(ns empire.computer.kamikazee-mission-decisions-spec
  (:require [speclj.core :refer :all]
            [empire.computer.threat-response.kamikazee-mission-decisions :as decisions]))

(describe "kamikazee-mission-decisions"
  (it "classifies top-level kamikazee stage handling"
    (should= :hunt-stage
             (decisions/kamikazee-stage-action {:stage :hunt}))
    (should= :route-stage
             (decisions/kamikazee-stage-action {:stage :route})))

  (it "prefers attack during hunt when an adjacent player army exists"
    (should= :attack
             (decisions/hunt-stage-action {:stage :hunt
                                           :has-wait-site? false
                                           :has-resume-pos? false
                                           :fuel 8
                                           :refuel-threshold 5
                                           :has-adjacent-player-army? true
                                           :has-reachable-refuel-site? true})))

  (it "starts refuel only when fuel is low and a site is reachable"
    (should= :start-refuel
             (decisions/hunt-stage-action {:stage :hunt
                                           :has-wait-site? false
                                           :has-resume-pos? false
                                           :fuel 5
                                           :refuel-threshold 5
                                           :has-adjacent-player-army? false
                                           :has-reachable-refuel-site? true})))

  (it "enters hunt when the route has no next site and the goal is close"
    (should= :enter-hunt
             (decisions/route-stage-action {:adjacent-route-city? false
                                            :at-route-site? false
                                            :has-next-site? false
                                            :close-enough-to-goal? true
                                            :has-goal? true})))

  (it "creates a destruction result when a hunt step burns the last fuel"
    (should= {:action :destroy :pos [4 4]}
             (decisions/hunt-step-result {:fuel 1}
                                         [4 4]
                                         [3 4]
                                         4
                                         32)))

  (it "builds airport launch state with next-city capacity semantics"
    (should= {:current-city-capacity? true
              :next-route-city-capacity? true
              :next-route-city [1 1]
              :launch-pos [2 2]
              :major-target [5 5]
              :targets [[5 5]]
              :plan {:route [[1 1]]}
              :fighter-fuel 32}
             (decisions/airport-launch-state
              {:city-has-capacity? true
               :next-route-city-capacity? true
               :next-route-city [1 1]
               :launch-pos [2 2]
               :major-target [5 5]
               :targets [[5 5]]
               :plan {:route [[1 1]]}
               :fighter-fuel 32}))))
