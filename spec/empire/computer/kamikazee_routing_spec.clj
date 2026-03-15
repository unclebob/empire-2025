(ns empire.computer.kamikazee-routing-spec
  (:require [speclj.core :refer :all]
            [empire.test.utils :refer [build-test-map reset-all-atoms!]]
            [empire.computer.threat-response.kamikazee :as kamikazee]
            [empire.computer.threat-response.kamikazee-routing :as routing]))

(describe "major invasion kamikazee routing"
  (before (reset-all-atoms!))

  (it "prefers newer army detections when choosing targets"
    (let [world (build-test-map ["A~A"])]
      (with-redefs [rand-nth first]
        (should= [2 0]
                 (kamikazee/choose-army-target
                  {:kamikazee-army-targets [{:pos [0 0] :seen-round 1}
                                            {:pos [2 0] :seen-round 5}]}
                  5
                  world)))))

  (it "builds city next hops back from the root city"
    (let [world (build-test-map ["X~~~O"
                                 "~~~~~"
                                 "~~X~~"])
          state {:detection-points #{[4 0]}}
          graph (#'routing/rebuild-routing-graph world state)]
      (should= [0 0] (:kamikazee-root-city graph))
      (should= [0 0] (get (:kamikazee-city-next-hops graph) [2 2]))))

  (it "uses a single carrier bridge when cities are otherwise out of range"
    (let [world (build-test-map ["XO~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"
                                 "~~~~~~~~~~~~~~~~~c~~~~~~~~~~~~~~~~~"
                                 "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~X"])
          state {:detection-points #{[1 0]}}
          graph (#'routing/rebuild-routing-graph world state)]
      (should= [0 0] (:kamikazee-root-city graph))
      (should= [17 1] (get (:kamikazee-city-next-hops graph) [34 2]))
      (should= [0 0] (get (:kamikazee-carrier-next-hops graph) [17 1]))
      (should= #{[17 1]} (:kamikazee-bridge-carriers graph))))

  (it "chooses fighters for cities that can already reach the invasion target"
    (let [world (build-test-map ["X~~~A"])
          state {:active? true
                 :detection-points #{[4 0]}
                 :target-land-set #{[4 0]}}]
      (with-redefs [empire.state.api/current-world (constantly world)
                    empire.state.api/read-state (fn [k]
                                                  (when (= k :major-invasion-state)
                                                    state))]
        (should= :fighter
                 (routing/invasion-production-override [0 0])))))

  (it "chooses fighters when loaded invasion transports exist"
    (let [world (build-test-map ["X~~"
                                 "t~~"])]
      (with-redefs [empire.state.api/current-world (constantly (assoc-in world [0 1 :contents]
                                                                         {:type :transport
                                                                          :owner :computer
                                                                          :major-invasion true
                                                                          :army-count 1}))
                    empire.state.api/read-state (fn [k]
                                                  (when (= k :major-invasion-state)
                                                    {:active? true
                                                     :detection-points #{}
                                                     :target-land-set #{}}))]
        (should= :fighter
                 (routing/invasion-production-override [0 0])))))

  (it "returns nil for invasion production when the invasion is inactive"
    (with-redefs [empire.state.api/read-state (fn [k]
                                                (when (= k :major-invasion-state)
                                                  {:active? false}))
                  empire.state.api/current-world (constantly (build-test-map ["X"]))]
      (should= nil
               (routing/invasion-production-override [0 0])))))
