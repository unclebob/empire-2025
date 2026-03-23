(ns empire.computer.fighter.flight-decisions-spec
  (:require [empire.computer.fighter.flight-decisions :as sut]
            [empire.test.utils :refer [build-test-map]]
            [speclj.core :refer :all]))

(describe "fighter flight decisions"
  (it "treats a computer city as a refueling site"
    (should= [0 0]
             (sut/current-refueling-site [[{:type :city :city-status :computer}]]
                                         [0 0])))

  (it "returns a regular-leg action when a target is reachable"
    (should= :assign-regular-leg
             (:action (sut/regular-leg-action [[{:type :city :city-status :computer}
                                               {}
                                               {:type :city :city-status :computer}]]
                                              [[0 0] [2 0]]
                                              {}
                                              [0 0]
                                              [0 0]))))

  (it "returns a handle-arrival action with a recorded leg"
    (should= {:leg-key #{[0 0] [2 0]}
              :last-flown 12}
             (:leg-record (sut/arrival-action [[{:type :city :city-status :computer}
                                                {}
                                                {:type :city :city-status :computer}]]
                                              [[0 0] [2 0]]
                                              {}
                                              12
                                              [2 0]
                                              {:flight-target-site [2 0]
                                               :flight-origin-site [0 0]}
                                              0.6))))

  (it "chooses the reachable landing city closest to unexplored cells"
    (let [world (build-test-map ["X###########X###########X########"])
          sites [[0 0] [12 0] [24 0]]]
      (with-redefs [empire.computer.fighter.exploration/count-unexplored-along-direction
                    (fn [start direction n]
                      (if (= direction [1 0]) 50 0))
                    empire.computer.fighter.exploration/nearest-unexplored-distance
                    (fn [site]
                      (case site
                        [12 0] 10
                        [24 0] 1
                        [0 0] 20
                        99))]
        (should= [24 0]
                 (:landing-site (sut/exploration-flight-action world
                                                               sites
                                                               [0 0]
                                                               [0 0]
                                                               0.5))))))

  (it "chooses the reachable city closest to unexplored cells even when it requires hops"
    (let [world (build-test-map ["X###################X###################X########"])
          sites [[0 0] [20 0] [40 0]]]
      (with-redefs [empire.computer.fighter.exploration/nearest-unexplored-distance
                    (fn [site]
                      (case site
                        [0 0] 40
                        [20 0] 10
                        [40 0] 1
                        99))]
        (should= {:city [40 0]
                  :path [[0 0] [20 0] [40 0]]
                  :unexplored-distance 1
                  :hop-count 2
                  :path-distance 40}
                 (sut/best-sortie-staging-plan world sites [0 0])))))

  (it "assigns a regular leg to the first hop before launching a sortie"
    (let [world (build-test-map ["X###################X###################X########"])
          sites [[0 0] [20 0] [40 0]]]
      (with-redefs [empire.computer.fighter.exploration/nearest-unexplored-distance
                    (fn [site]
                      (case site
                        [0 0] 40
                        [20 0] 10
                        [40 0] 1
                        99))]
        (should= {:action :assign-regular-leg
                  :pos [0 0]
                  :target [20 0]
                  :origin [0 0]}
                 (sut/ensure-flight-target-action world
                                                 sites
                                                 {}
                                                 [0 0]
                                                 {:type :fighter :owner :computer}
                                                 0.6
                                                 0.6))))))
