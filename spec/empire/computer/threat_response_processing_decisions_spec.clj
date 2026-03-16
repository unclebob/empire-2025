(ns empire.computer.threat-response-processing-decisions-spec
  (:require [empire.computer.threat-response.processing-decisions :as decisions]
            [speclj.core :refer :all]))

(describe "threat response processing decisions"
  (it "detects active fighter threats"
    (should (decisions/fighter-threat-active? {:threat-mission :fighter-sweep}))
    (should (decisions/fighter-threat-active? {:major-invasion true}))
    (should-not (decisions/fighter-threat-active? {:threat-mission :none})))

  (it "prefers explicit centers and falls back to nearest major target"
    (should= [1 1]
             (decisions/fighter-threat-center {:unit {:threat-center [1 1]}
                                               :pos [0 0]
                                               :nearest-major-target (constantly [9 9])}))
    (should= [9 9]
             (decisions/fighter-threat-center {:unit {}
                                               :pos [0 0]
                                               :nearest-major-target (constantly [9 9])})))

  (it "chooses fighter threat action priority"
    (should= :attack (decisions/fighter-threat-action {:enemy? true}))
    (should= :refuel (decisions/fighter-threat-action {:low-fuel? true}))
    (should= :outside-radius (decisions/fighter-threat-action {:outside-radius? true}))
    (should= :patrol (decisions/fighter-threat-action {})))

  (it "reduces remaining threat steps"
    (should= {:pos [2 2] :remaining 5}
             (decisions/next-threat-state 7 {:pos [2 2] :steps-used 2}))
    (should-be-nil (decisions/next-threat-state 0 {:pos [2 2] :steps-used 2}))))
