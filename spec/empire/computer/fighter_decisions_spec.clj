(ns empire.computer.fighter.decisions-spec
  (:require [empire.computer.fighter.decisions :as decisions]
            [speclj.core :refer :all]))

(describe "objective-action"
  (it "prefers explore/drone"
    (should= :explore (decisions/objective-action {:exploring? true})))

  (it "returns arrive when at target"
    (should= :arrive (decisions/objective-action {:at-flight-target? true})))

  (it "returns low-fuel before navigation"
    (should= :low-fuel (decisions/objective-action {:low-fuel? true :has-target? true})))

  (it "returns navigate when target exists"
    (should= :navigate (decisions/objective-action {:has-target? true})))

  (it "falls back to patrol"
    (should= :patrol (decisions/objective-action {}))))

(describe "fighter-step-action"
  (it "attacks when enemy adjacent"
    (should= :attack (decisions/fighter-step-action [1 1] :patrol)))

  (it "uses objective action otherwise"
    (should= :patrol (decisions/fighter-step-action nil :patrol))))
