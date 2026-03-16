(ns empire.computer.threat-response-major-invasion-manager-decisions-spec
  (:require [empire.computer.threat-response.major-invasion-manager-decisions :as decisions]
            [speclj.core :refer :all]))

(describe "major invasion manager decisions"
  (it "rebuilds routing only when city count grows or is unset"
    (should (decisions/rebuild-routing? nil 3))
    (should (decisions/rebuild-routing? 2 3))
    (should-not (decisions/rebuild-routing? 3 3)))

  (it "records detections only when active state does not already cover nearby detection"
    (should (decisions/should-record-detection? {:active? false :nearby-existing? true}))
    (should (decisions/should-record-detection? {:active? true :nearby-existing? false}))
    (should-not (decisions/should-record-detection? {:active? true :nearby-existing? true})))

  (it "decides when to review a deferred invasion"
    (should (decisions/should-review-deferred? {:decision :deferred
                                                :failure-reason :unsustainable-losses
                                                :next-review-round 9
                                                :current-round 9}))
    (should-not (decisions/should-review-deferred? {:decision :ready
                                                    :failure-reason :unsustainable-losses
                                                    :next-review-round 9
                                                    :current-round 9})))

  (it "produces start-state updates"
    (should= {:active? true
              :decision :ready
              :failure-reason nil
              :next-review-round nil
              :first-landing-round nil
              :sea-reachable-detection-points #{[0 0]}}
             (decisions/invasion-start-update {:decision :ready
                                               :sea-reachable-detection-points #{[0 0]}}))
    (should= :deferred
             (:decision (decisions/invasion-start-update {:decision :deferred
                                                          :failure-reason :no-sea-path
                                                          :next-review-round 12})))))
