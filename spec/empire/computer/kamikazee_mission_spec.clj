(ns empire.computer.kamikazee-mission-spec
  (:require [speclj.core :refer :all]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-world! set-test-computer-map! set-test-state! update-test-world!]]
            [empire.test.utils :as test-utils]
            [empire.computer.threat-response :as threat-response]
            [empire.computer.threat-response.kamikazee :as kamikazee]))

(describe "major invasion kamikazee mission"
  (before (reset-all-atoms!))

  (it "lands in the airport one cell away from a city route target"
    (let [world (build-test-map ["~fX"
                                 "~~~"])]
      (set-test-world! world)
      (set-test-computer-map! world)
      (test-utils/set-major-invasion-state! {:detection-points #{[2 0]}})
      (test-utils/set-kamikazee-fighter! [1 0]
                                         {:kamikazee-stage :route
                                          :kamikazee-route [[2 0]]
                                          :fuel 20})
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (with-redefs [rand-nth first]
        (kamikazee/process-kamikazee-fighter (test-utils/mission-ctx)
                                             [1 0]
                                             (get-in (test-utils/read-test-state :game-map) [1 0 :contents])))
      (should= nil (get-in (test-utils/read-test-state :game-map) [2 0 :contents]))
      (should= nil (get-in (test-utils/read-test-state :game-map) [1 0 :contents]))
      (should= 1 (get-in (test-utils/read-test-state :game-map) [2 0 :fighter-count]))
      (should= 1 (get-in (test-utils/read-test-state :game-map) [2 0 :kamikazee-fighter-count]))
      (should= 1 (get-in (test-utils/read-test-state :game-map) [2 0 :awake-kamikazee-fighters]))))

  (it "finishes a route node in place at a carrier site"
    (let [world (build-test-map ["fc~"])]
      (set-test-world! world)
      (set-test-computer-map! world)
      (test-utils/set-major-invasion-state! {})
      (test-utils/set-kamikazee-fighter! [0 0]
                                         {:kamikazee-stage :route
                                          :kamikazee-route [[1 0]]
                                          :fuel 7})
      (kamikazee/process-kamikazee-fighter (test-utils/mission-ctx)
                                           [0 0]
                                           (get-in (test-utils/read-test-state :game-map) [0 0 :contents]))
      (should= :hunt (get-in (test-utils/read-test-state :game-map) [0 0 :contents :kamikazee-stage]))
      (should= [] (get-in (test-utils/read-test-state :game-map) [0 0 :contents :kamikazee-route]))
      (should= [1 0] (get-in (test-utils/read-test-state :game-map) [0 0 :contents :kamikazee-terminal-site]))
      (should= 32 (get-in (test-utils/read-test-state :game-map) [0 0 :contents :fuel]))))

  (it "enters hunt once it is close enough to the final target without a route"
    (let [world (build-test-map ["f+"])]
      (set-test-world! world)
      (set-test-computer-map! world)
      (test-utils/set-major-invasion-state! {:target-land-set #{[1 0]}})
      (test-utils/set-kamikazee-fighter! [0 0]
                                         {:kamikazee-stage :route
                                          :kamikazee-route []})
      (kamikazee/process-kamikazee-fighter (test-utils/mission-ctx)
                                           [0 0]
                                           (get-in (test-utils/read-test-state :game-map) [0 0 :contents]))
      (should= :hunt (get-in (test-utils/read-test-state :game-map) [0 0 :contents :kamikazee-stage]))))

  (it "falls back to non-backtracking movement when route movement toward the goal fails"
    (let [world (build-test-map ["f~~A"
                                 "~~~~"])]
      (set-test-world! world)
      (set-test-computer-map! world)
      (test-utils/set-major-invasion-state! {:kamikazee-army-targets [{:pos [3 0] :seen-round 2}]})
      (test-utils/set-kamikazee-fighter! [0 0]
                                         {:kamikazee-stage :route
                                          :kamikazee-route []
                                          :fuel 8})
      (with-redefs [rand-nth first
                    empire.computer.fighter-movement/hop-over-friendly (constantly nil)]
        (kamikazee/process-kamikazee-fighter (test-utils/mission-ctx)
                                             [0 0]
                                             (get-in (test-utils/read-test-state :game-map) [0 0 :contents])))
      (should= :fighter (get-in (test-utils/read-test-state :game-map) [1 0 :contents :type]))))

  (it "relaunches a kamikazee fighter from a computer city airport in hunt mode at the root city"
    (let [world (build-test-map ["X~~A"
                                 "~~~~"])]
      (set-test-world! world)
      (set-test-computer-map! world)
      (test-utils/set-major-invasion-state!
       {:active? true
        :detection-points #{[3 0]}
        :target-land-set #{[3 0]}
        :kamikazee-root-city [0 0]
        :kamikazee-terminal-sites #{[0 0]}
        :kamikazee-city-next-hops {}
        :kamikazee-carrier-next-hops {}})
      (test-utils/seed-airport-kamikazees! [0 0] 2 1)
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (threat-response/launch-kamikazee-from-airport! [0 0])
      (let [fighter (get-in (test-utils/read-test-state :game-map) [1 0 :contents])]
        (should= :fighter (:type fighter))
        (should= true (:kamikazee fighter))
        (should= :hunt (:kamikazee-stage fighter))
        (should= [] (:kamikazee-route fighter))
        (should= 1 (get-in (test-utils/read-test-state :game-map) [0 0 :kamikazee-fighter-count]))
        (should= 0 (get-in (test-utils/read-test-state :game-map) [0 0 :awake-kamikazee-fighters])))))

  (it "delays kamikazee airport launch when the next route city lacks two free adjacent cells"
    (let [world (build-test-map ["X~~~"
                                 "~~~~"
                                 "~fff"
                                 "~fXf"])]
      (set-test-world! world)
      (set-test-computer-map! world)
      (test-utils/set-major-invasion-state!
       {:active? true
        :detection-points #{[2 3]}
        :target-land-set #{[2 3]}
        :kamikazee-root-city [2 3]
        :kamikazee-terminal-sites #{[2 3]}
        :kamikazee-city-next-hops {[0 0] [2 3]}
        :kamikazee-carrier-next-hops {}})
      (test-utils/seed-airport-kamikazees! [0 0] 1 1)
      (should= nil (threat-response/launch-kamikazee-from-airport! [0 0]))
      (should= 1 (get-in (test-utils/read-test-state :game-map) [0 0 :awake-kamikazee-fighters]))
      (should= nil (get-in (test-utils/read-test-state :game-map) [0 1 :contents]))
      (should= nil (get-in (test-utils/read-test-state :game-map) [1 0 :contents]))))

  (it "does not proactively refuel on the final leg at fuel sixteen"
    (let [world (build-test-map ["Xf~~A"
                                 "~~~~~"])]
      (set-test-world! world)
      (set-test-computer-map! world)
      (test-utils/set-major-invasion-state! {:kamikazee-army-targets [{:pos [4 0] :seen-round 1}]})
      (test-utils/set-kamikazee-fighter! [1 0]
                                         {:kamikazee-stage :route
                                          :fuel 16})
      (kamikazee/process-kamikazee-fighter (test-utils/mission-ctx)
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
      (test-utils/set-major-invasion-state! {})
      (test-utils/set-kamikazee-fighter! [0 1]
                                         {:kamikazee-stage :hunt
                                          :fuel 5})
      (kamikazee/process-kamikazee-fighter (test-utils/mission-ctx)
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
      (test-utils/set-major-invasion-state! {:detection-points #{[6 0]}})
      (test-utils/set-kamikazee-fighter! [0 0]
                                         {:kamikazee-stage :hunt
                                          :fuel 5})
      (with-redefs [rand-nth first]
        (kamikazee/process-kamikazee-fighter (test-utils/mission-ctx)
                                             [0 0]
                                             (get-in (test-utils/read-test-state :game-map) [0 0 :contents])))
      (should= :fighter (get-in (test-utils/read-test-state :game-map) [1 0 :contents :type]))
      (should= 4 (get-in (test-utils/read-test-state :game-map) [1 0 :contents :fuel]))
      (should= :hunt (get-in (test-utils/read-test-state :game-map) [1 0 :contents :kamikazee-stage]))))

  (it "does not create phantom contents when a hunt step destination is stale"
    (let [world (build-test-map ["f~~"
                                 "~~~"])]
      (set-test-world! world)
      (set-test-computer-map! world)
      (test-utils/set-major-invasion-state! {:detection-points #{[2 0]}})
      (test-utils/set-kamikazee-fighter! [0 0]
                                         {:kamikazee-stage :hunt
                                          :fuel 6})
      (with-redefs [rand-nth first
                    empire.computer.core/move-unit-to (fn [_ _]
                                                        (update-test-world! update-in [0 0] dissoc :contents)
                                                        [1 0])]
        (kamikazee/process-kamikazee-fighter (test-utils/mission-ctx)
                                             [0 0]
                                             (get-in (test-utils/read-test-state :game-map) [0 0 :contents])))
      (should= nil (get-in (test-utils/read-test-state :game-map) [1 0 :contents]))
      (should= nil (get-in (test-utils/read-test-state :game-map) [0 0 :contents]))))

  (it "removes a kamikazee fighter when the hunt step burns the last fuel point"
    (let [world (build-test-map ["f~~"
                                 "~~~"])]
      (set-test-world! world)
      (set-test-computer-map! world)
      (test-utils/set-major-invasion-state! {:detection-points #{[2 0]}})
      (test-utils/set-kamikazee-fighter! [0 0]
                                         {:kamikazee-stage :hunt
                                          :fuel 1})
      (with-redefs [rand-nth first]
        (kamikazee/process-kamikazee-fighter (test-utils/mission-ctx)
                                             [0 0]
                                             (get-in (test-utils/read-test-state :game-map) [0 0 :contents])))
      (should= nil (get-in (test-utils/read-test-state :game-map) [1 0 :contents]))))

  (it "stops processing cleanly when the kamikazee fighter is already gone"
    (let [world (build-test-map ["~~~"
                                 "~~~"])]
      (set-test-world! world)
      (set-test-computer-map! world)
      (test-utils/set-major-invasion-state! {:detection-points #{[2 0]}})
      (should= nil (kamikazee/process-kamikazee-fighter (test-utils/mission-ctx) [1 1] nil))))

  (it "attacks adjacent player armies while hunting"
    (let [world (build-test-map ["fA"])]
      (set-test-world! world)
      (set-test-computer-map! world)
      (test-utils/set-major-invasion-state! {})
      (test-utils/set-kamikazee-fighter! [0 0]
                                         {:kamikazee-stage :hunt
                                          :fuel 20})
      (with-redefs [rand (constantly 0.4)]
        (kamikazee/process-kamikazee-fighter (test-utils/mission-ctx)
                                             [0 0]
                                             (get-in (test-utils/read-test-state :game-map) [0 0 :contents])))
      (should= :computer (get-in (test-utils/read-test-state :game-map) [1 0 :contents :owner]))
      (should= :fighter (get-in (test-utils/read-test-state :game-map) [1 0 :contents :type]))))

  (it "does not fall through into a hunt move after losing an adjacent army combat"
    (let [world (build-test-map ["fA~"
                                 "~~~"])]
      (set-test-world! world)
      (set-test-computer-map! world)
      (test-utils/set-major-invasion-state! {})
      (test-utils/set-kamikazee-fighter! [0 0]
                                         {:kamikazee-stage :hunt
                                          :fuel 20})
      (with-redefs [empire.computer.fighter-movement/attack-enemy
                    (fn [from _]
                      (update-test-world! update-in from dissoc :contents)
                      nil)]
        (kamikazee/process-kamikazee-fighter (test-utils/mission-ctx)
                                             [0 0]
                                             (get-in (test-utils/read-test-state :game-map) [0 0 :contents])))
      (should= nil (get-in (test-utils/read-test-state :game-map) [0 0 :contents]))
      (should= nil (get-in (test-utils/read-test-state :game-map) [1 1 :contents]))
      (should= nil (get-in (test-utils/read-test-state :game-map) [2 0 :contents])))))
