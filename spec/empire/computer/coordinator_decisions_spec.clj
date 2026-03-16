(ns empire.computer.coordinator-decisions-spec
  (:require [empire.computer.coordinator-decisions :as decisions]
            [speclj.core :refer :all]))

(describe "coordinator decisions"
  (it "classifies dispatch action by computer unit type"
    (should= :army (decisions/dispatch-action {:type :army :owner :computer}))
    (should= :fighter (decisions/dispatch-action {:type :fighter :owner :computer}))
    (should= :transport (decisions/dispatch-action {:type :transport :owner :computer}))
    (should= :ship (decisions/dispatch-action {:type :destroyer :owner :computer})))

  (it "returns nil for non-computer or unknown units"
    (should-be-nil (decisions/dispatch-action {:type :army :owner :player}))
    (should-be-nil (decisions/dispatch-action {:type :city :owner :computer})))

  (it "builds a dispatch plan for routable computer units"
    (should= {:action :ship :unit-type :destroyer}
             (decisions/dispatch-plan {:type :destroyer :owner :computer}))
    (should-be-nil
      (decisions/dispatch-plan {:type :city :owner :computer}))))
