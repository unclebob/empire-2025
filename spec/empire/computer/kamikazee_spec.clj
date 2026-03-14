(ns empire.computer.kamikazee-spec
  (:require [speclj.core :refer :all]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-world! set-test-computer-map! set-test-state! update-test-world!]]
            [empire.test.utils :as test-utils]
            [empire.computer.threat-response :as threat-response]
            [empire.computer.threat-response.kamikazee-mission :as mission]
            [empire.computer.threat-response.kamikazee :as kamikazee]
            [empire.computer.production :as production]))

(defn- mission-ctx []
  {:current-world test-utils/read-test-world
   :update-game-map! test-utils/update-test-world!
   :load-major-invasion-state #(test-utils/read-test-state :major-invasion-state)})

(describe "major invasion kamikazee"
  (before (reset-all-atoms!))

  (it "marks fighters as kamikazee on major invasion assignment"
    (let [gm (build-test-map ["f~t~O"
                              "~~~~~"])]
      (set-test-world! gm)
      (set-test-computer-map! gm)
      (update-test-world! assoc-in [2 0 :contents :transport-mission] :sailing)
      (update-test-world! assoc-in [2 0 :contents :army-count] 4)
      (threat-response/handle-detection! [4 0] (get-in (test-utils/read-test-state :game-map) [4 0]))
      (threat-response/on-round-start!)
      (let [fighter (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
        (should= true (:kamikazee fighter))
        (should= true (:major-invasion fighter)))))

  (it "records newly detected player armies as kamikazee targets"
    (let [gm (build-test-map ["f~t~O~~"
                              "~~~~~p~"
                              "######A"])]
      (set-test-world! gm)
      (set-test-computer-map! gm)
      (update-test-world! assoc-in [2 0 :contents :transport-mission] :sailing)
      (update-test-world! assoc-in [2 0 :contents :army-count] 4)
      (threat-response/handle-detection! [4 0] (get-in (test-utils/read-test-state :game-map) [4 0]))
      (threat-response/on-round-start!)
      (threat-response/handle-detection! [6 2] (get-in (test-utils/read-test-state :game-map) [6 2]))
      (let [fighter (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
        (should= [[6 2]] (:kamikazee-targets fighter)))))

  (it "forces fighter production during major invasion while a loaded transport remains"
    (let [gm (build-test-map ["X~t~O"
                              "X####"])]
      (set-test-world! gm)
      (set-test-computer-map! gm)
      (update-test-world! assoc-in [0 0 :country-id] 1)
      (update-test-world! assoc-in [0 1 :country-id] 2)
      (update-test-world! assoc-in [2 0 :contents :transport-mission] :sailing)
      (update-test-world! assoc-in [2 0 :contents :army-count] 4)
      (threat-response/handle-detection! [4 0] (get-in (test-utils/read-test-state :game-map) [4 0]))
      (threat-response/on-round-start!)
      (production/rebuild-country-stats!)
      (should= :fighter (production/decide-production [0 0]))
      (should= :fighter (production/decide-production [0 1]))))

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
          graph (#'empire.computer.threat-response.kamikazee-routing/rebuild-routing-graph world state)]
      (should= [0 0] (:kamikazee-root-city graph))
      (should= [0 0] (get (:kamikazee-city-next-hops graph) [2 2]))))

  (it "uses a single carrier bridge when cities are otherwise out of range"
    (let [world (build-test-map ["XO~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"
                                 "~~~~~~~~~~~~~~~~~c~~~~~~~~~~~~~~~~~"
                                 "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~X"])
          state {:detection-points #{[1 0]}}
          graph (#'empire.computer.threat-response.kamikazee-routing/rebuild-routing-graph world state)]
      (should= [0 0] (:kamikazee-root-city graph))
      (should= [17 1] (get (:kamikazee-city-next-hops graph) [34 2]))
      (should= [0 0] (get (:kamikazee-carrier-next-hops graph) [17 1]))
      (should= #{[17 1]} (:kamikazee-bridge-carriers graph))))

  (it "keeps a fixed invasion carrier in place during ship processing"
    (let [world (build-test-map ["XO~~~~"
                                 "~~c~~~"
                                 "~~~~~~"])]
      (set-test-world! world)
      (set-test-computer-map! world)
      (set-test-state! :major-invasion-state
                       {:active? true
                        :kamikazee-bridge-carriers #{[2 1]}})
      (update-test-world! update-in [2 1 :contents]
                          merge
                          {:major-invasion true
                           :mode :sentry
                           :major-invasion-target [2 1]})
      (threat-response/process-ship-threat [2 1] :carrier (get-in (test-utils/read-test-state :game-map) [2 1 :contents]))
      (should= :carrier (get-in (test-utils/read-test-state :game-map) [2 1 :contents :type]))
      (should= nil (get-in (test-utils/read-test-state :game-map) [1 1 :contents]))
      (should= nil (get-in (test-utils/read-test-state :game-map) [2 0 :contents]))
      (should= nil (get-in (test-utils/read-test-state :game-map) [2 2 :contents]))))

  (it "degrades into hunt one cell away from a city target instead of entering the city"
    (let [world (build-test-map ["~fO"
                                 "~~~"])]
      (set-test-world! world)
      (set-test-computer-map! world)
      (set-test-state! :major-invasion-state {:detection-points #{[2 0]}})
      (update-test-world! update-in [1 0 :contents]
                          merge
                          {:major-invasion true
                           :kamikazee true
                           :kamikazee-stage :route
                           :fuel 20})
      (with-redefs [rand-nth first]
        (#'mission/process-kamikazee-fighter (mission-ctx)
                                             [1 0]
                                             (get-in (test-utils/read-test-state :game-map) [1 0 :contents])))
      (should= nil (get-in (test-utils/read-test-state :game-map) [2 0 :contents]))
      (should= :hunt (get-in (test-utils/read-test-state :game-map) [2 1 :contents :kamikazee-stage]))))

  (it "does not proactively refuel on the final leg at fuel sixteen"
    (let [world (build-test-map ["Xf~~A"
                                 "~~~~~"])]
      (set-test-world! world)
      (set-test-computer-map! world)
      (set-test-state! :major-invasion-state {:kamikazee-army-targets [{:pos [4 0] :seen-round 1}]})
      (update-test-world! update-in [1 0 :contents]
                          merge
                          {:major-invasion true
                           :kamikazee true
                           :kamikazee-stage :route
                           :fuel 16})
      (#'mission/process-kamikazee-fighter (mission-ctx)
                                           [1 0]
                                           (get-in (test-utils/read-test-state :game-map) [1 0 :contents]))
      (should= :fighter (get-in (test-utils/read-test-state :game-map) [2 0 :contents :type]))
      (should= 16 (get-in (test-utils/read-test-state :game-map) [2 0 :contents :fuel]))
      (should= nil (get-in (test-utils/read-test-state :game-map) [0 0 :contents]))))

  (it "refuels only from hunt at fuel five and records a return point"
    (let [world (build-test-map ["c~"
                                 "f~"])]
      (set-test-world! world)
      (set-test-computer-map! world)
      (set-test-state! :major-invasion-state {})
      (update-test-world! update-in [0 1 :contents]
                          merge
                          {:major-invasion true
                           :kamikazee true
                           :kamikazee-stage :hunt
                           :fuel 5})
      (#'mission/process-kamikazee-fighter (mission-ctx)
                                           [0 1]
                                           (get-in (test-utils/read-test-state :game-map) [0 1 :contents]))
      (should= :return (get-in (test-utils/read-test-state :game-map) [0 1 :contents :kamikazee-stage]))
      (should= 32 (get-in (test-utils/read-test-state :game-map) [0 1 :contents :fuel]))
      (should= [0 1] (get-in (test-utils/read-test-state :game-map) [0 1 :contents :kamikazee-hunt-resume-pos]))))

  (it "keeps hunting at fuel five when no refueling site is close enough"
    (let [world (build-test-map ["f~~~~~~"
                                 "~~~~~~~"])]
      (set-test-world! world)
      (set-test-computer-map! world)
      (set-test-state! :major-invasion-state {:detection-points #{[6 0]}})
      (update-test-world! update-in [0 0 :contents]
                          merge
                          {:major-invasion true
                           :kamikazee true
                           :kamikazee-stage :hunt
                           :fuel 5})
      (with-redefs [rand-nth first]
        (#'mission/process-kamikazee-fighter (mission-ctx)
                                             [0 0]
                                             (get-in (test-utils/read-test-state :game-map) [0 0 :contents])))
      (should= :fighter (get-in (test-utils/read-test-state :game-map) [1 0 :contents :type]))
      (should= 4 (get-in (test-utils/read-test-state :game-map) [1 0 :contents :fuel]))
      (should= :hunt (get-in (test-utils/read-test-state :game-map) [1 0 :contents :kamikazee-stage]))))

  (it "attacks adjacent player armies while hunting"
    (let [world (build-test-map ["fA"])]
      (set-test-world! world)
      (set-test-computer-map! world)
      (set-test-state! :major-invasion-state {})
      (update-test-world! update-in [0 0 :contents]
                          merge
                          {:major-invasion true
                           :kamikazee true
                           :kamikazee-stage :hunt
                           :fuel 20})
      (with-redefs [rand (constantly 0.4)]
        (#'mission/process-kamikazee-fighter (mission-ctx)
                                             [0 0]
                                             (get-in (test-utils/read-test-state :game-map) [0 0 :contents])))
      (should= :computer (get-in (test-utils/read-test-state :game-map) [1 0 :contents :owner]))
      (should= :fighter (get-in (test-utils/read-test-state :game-map) [1 0 :contents :type])))))
