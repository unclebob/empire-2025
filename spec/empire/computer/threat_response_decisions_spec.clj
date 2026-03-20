(ns empire.computer.threat-response.decisions-spec
  (:require [empire.computer.threat-response.decisions :as decisions]
            [speclj.core :refer :all]))

(describe "threat response decisions"
  (it "chooses transport prep only for active major invasion transport"
    (should= :prepare-transport
             (decisions/prepare-transport-action {:major-invasion-active? true
                                                  :unit {:type :transport}}))
    (should-be-nil
     (decisions/prepare-transport-action {:major-invasion-active? false
                                          :unit {:type :transport}})))

  (it "chooses fighter threat round style"
    (should= :kamikazee (decisions/fighter-threat-round-action {:major-invasion true :kamikazee true}))
    (should= :standard (decisions/fighter-threat-round-action {:threat-mission :fighter-sweep}))
    (should-be-nil (decisions/fighter-threat-round-action {:threat-mission :none})))

  (it "holds fixed major invasion carriers"
    (should= :hold (decisions/ship-threat-action {:ship-type :carrier :major-invasion? true :fixed-carrier? true}))
    (should= :process (decisions/ship-threat-action {:ship-type :destroyer :major-invasion? true :fixed-carrier? false}))))
