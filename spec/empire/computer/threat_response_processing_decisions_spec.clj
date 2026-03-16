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
    (should-be-nil (decisions/next-threat-state 0 {:pos [2 2] :steps-used 2})))

  (it "classifies fighter and ship threat round modes"
    (should= :random-walk (decisions/fighter-threat-round-mode {:random-walk? true}))
    (should= :active-threat (decisions/fighter-threat-round-mode {:random-walk? false}))
    (should= :random-walk (decisions/ship-threat-mode {:random-walk? true}))
    (should= :sea-scout (decisions/ship-threat-mode {:sea-scout? true}))
    (should= :major-invasion (decisions/ship-threat-mode {:major-invasion? true}))
    (should-be-nil (decisions/ship-threat-mode {})))

  (it "chooses ship movement targets"
    (should= [4 4]
             (decisions/sea-scout-move-target {:pos [0 0] :center [4 4] :radius 2 :distance-fn (fn [_ _] 5)}))
    (should-be-nil
     (decisions/sea-scout-move-target {:pos [0 0] :center [4 4] :radius 5 :distance-fn (fn [_ _] 5)}))
    (should= [1 1]
             (decisions/major-invasion-move-target {:center [1 1] :nearest-major-target (fn [_] [2 2]) :pos [0 0]}))
    (should= [2 2]
             (decisions/major-invasion-move-target {:center nil :nearest-major-target (fn [_] [2 2]) :pos [0 0]}))))
