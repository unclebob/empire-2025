(ns empire.computer.kamikazee-launch-decisions-spec
  (:require [speclj.core :refer :all]
            [empire.computer.threat-response.kamikazee-launch-decisions :as decisions]))

(describe "kamikazee-launch-decisions"
  (it "refuses launch when the next route city lacks capacity"
    (should-be-nil
      (decisions/launch-decision {:current-city-capacity? true
                                  :next-route-city-capacity? false
                                  :next-route-city [5 5]
                                  :launch-pos [1 0]
                                  :major-target [9 9]
                                  :targets [[9 9]]
                                  :plan {:route [[5 5]] :terminal-site [5 5]}
                                  :fighter-fuel 32})))

  (it "builds a launched fighter in hunt stage when the route is empty"
    (let [action (decisions/launch-decision {:current-city-capacity? true
                                             :next-route-city-capacity? true
                                             :next-route-city nil
                                             :launch-pos [1 0]
                                             :major-target [9 9]
                                             :targets [[9 9]]
                                             :plan {:route [] :terminal-site [5 5]}
                                             :fighter-fuel 32})]
      (should= [1 0] (:launch-pos action))
      (should= :hunt (get-in action [:fighter :kamikazee-stage])))))
