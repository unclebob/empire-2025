(ns empire.computer.threat-response-country-defense-spec
  (:require [speclj.core :refer :all]
            [empire.computer.threat-response :as threat-response]
            [empire.test.utils :as test-utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-world! update-test-computer-map! update-test-world!]]))

(describe "country-defense threat response"
  (before (reset-all-atoms!))

  (it "assigns country-defense mission to armies and fighters in the detected country"
    (let [gm (build-test-map ["afAa"])]
      (set-test-world! gm)
      (set-test-computer-map! gm)
      (update-test-world! assoc-in [0 0 :contents :country-id] 1)
      (update-test-world! assoc-in [1 0 :contents :country-id] 1)
      (update-test-world! assoc-in [2 0 :country-id] 1)
      (update-test-world! assoc-in [3 0 :contents :country-id] 2)
      (update-test-computer-map! assoc-in [2 0 :country-id] 1)
      (threat-response/handle-detection! [2 0] (get-in (test-utils/read-test-state :game-map) [2 0]))
      (should= :country-defense (get-in (test-utils/read-test-state :game-map) [0 0 :contents :threat-mission]))
      (should= [2 0] (get-in (test-utils/read-test-state :game-map) [0 0 :contents :threat-center]))
      (should= :country-defense (get-in (test-utils/read-test-state :game-map) [1 0 :contents :threat-mission]))
      (should= [2 0] (get-in (test-utils/read-test-state :game-map) [1 0 :contents :threat-center]))
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [3 0 :contents :threat-mission]))))

  (it "restores previous threat fields when no player armies remain detected in that country"
    (let [gm (build-test-map ["fA"])]
      (set-test-world! gm)
      (set-test-computer-map! gm)
      (update-test-world! assoc-in [0 0 :contents :country-id] 1)
      (update-test-world! assoc-in [0 0 :contents :threat-mission] :fighter-sweep)
      (update-test-world! assoc-in [0 0 :contents :threat-center] [9 9])
      (update-test-world! assoc-in [0 0 :contents :threat-radius] 4)
      (update-test-world! assoc-in [0 0 :contents :threat-rounds-left] 3)
      (update-test-world! assoc-in [1 0 :country-id] 1)
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (threat-response/handle-detection! [1 0] (get-in (test-utils/read-test-state :game-map) [1 0]))
      (should= :country-defense (get-in (test-utils/read-test-state :game-map) [0 0 :contents :threat-mission]))

      (update-test-world! assoc-in [1 0] {:type :land :country-id 1})
      (set-test-computer-map! (build-test-map ["f#"]))
      (update-test-computer-map! assoc-in [0 0 :contents :country-id] 1)
      (update-test-computer-map! assoc-in [1 0 :country-id] 1)
      (threat-response/on-round-start!)

      (should= :fighter-sweep (get-in (test-utils/read-test-state :game-map) [0 0 :contents :threat-mission]))
      (should= [9 9] (get-in (test-utils/read-test-state :game-map) [0 0 :contents :threat-center]))
      (should= 4 (get-in (test-utils/read-test-state :game-map) [0 0 :contents :threat-radius]))
      (should= 3 (get-in (test-utils/read-test-state :game-map) [0 0 :contents :threat-rounds-left]))
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [0 0 :contents :country-defense-active]))
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [0 0 :contents :country-defense-prev-threat]))))

  (it "processes fighters on country-defense mission through threat logic"
    (set-test-world! (build-test-map ["f"]))
    (let [calls (atom 0)]
      (with-redefs [empire.computer.threat-response/fighter-step-threat
                    (fn [_ _]
                      (swap! calls inc)
                      nil)]
        (should (threat-response/process-fighter-threat
                 [0 0] {:threat-mission :country-defense :threat-center [1 1]}))
        (should= 1 @calls)))))
