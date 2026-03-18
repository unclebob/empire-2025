(ns empire.computer.early-game.strategy-spec
  (:require [empire.computer.early-game.strategy :as strategy]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-world! update-test-world!]]
            [speclj.core :refer :all]))

(describe "early game strategy"
  (before (reset-all-atoms!))

  (it "stays in phase 1 before any breakpoint is reached"
    (set-test-world! (build-test-map ["~X#"
                                      "###"]))
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (update-test-world! assoc-in [1 0 :country-id] 1)
    (test-utils/set-test-state! :round-number 10)
    (should= :phase-1 (:phase (strategy/theater-summary [1 0]))))

  (it "switches to phase 2 when six armies exist on the landmass"
    (set-test-world! (build-test-map ["aaa"
                                      "aXa"
                                      "aaa"]))
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (update-test-world! assoc-in [1 1 :country-id] 1)
    (doseq [pos [[0 0] [1 0] [2 0] [0 1] [2 1] [0 2] [1 2] [2 2]]]
      (update-test-world! assoc-in (conj pos :contents :country-id) 1))
    (should= :phase-2 (:phase (strategy/theater-summary [1 1]))))

  (it "assigns transport to the only coastal city when inland support exists"
    (set-test-world! (build-test-map ["~Xaa"
                                      "##aa"
                                      "##Xa"
                                      "##Xa"]))
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (doseq [pos [[1 0] [2 2] [2 3]]]
      (update-test-world! assoc-in (conj pos :country-id) 1))
    (doseq [pos [[2 0] [3 0] [2 1] [3 1] [3 2] [3 3]]]
      (update-test-world! assoc-in (conj pos :contents :country-id) 1))
    (test-utils/set-test-state! :round-number 30)
    (should= :CT (strategy/assigned-role [1 0])))

  (it "does not treat hidden adjacent sea as usable coast"
    (set-test-world! (build-test-map ["~Xaa"
                                      "##aa"
                                      "##Xa"
                                      "##Xa"]))
    (set-test-computer-map! (build-test-map ["#Xaa"
                                             "##aa"
                                             "##Xa"
                                             "##Xa"]))
    (doseq [pos [[1 0] [2 2] [2 3]]]
      (update-test-world! assoc-in (conj pos :country-id) 1)
      (test-utils/update-test-computer-map! assoc-in (conj pos :country-id) 1))
    (test-utils/set-test-state! :round-number 30)
    (should-not (strategy/city-usable-coastal? [1 0])))

  (it "keeps the opening active when invasion units are hidden on the computer map"
    (set-test-world! (build-test-map ["tX"]))
    (set-test-computer-map! [[nil {:type :city :city-status :computer :country-id 1}]])
    (update-test-world! assoc-in [0 0 :contents :transport-mission] :invading)
    (test-utils/set-test-state! :round-number 5)
    (should-not (strategy/invasion-started?))
    (should (strategy/opening-active?)))

  (it "builds fighters for C=0 landmasses only after six armies"
    (set-test-world! (build-test-map ["aaa"
                                      "aXa"
                                      "aaa"]))
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (update-test-world! assoc-in [1 1 :country-id] 1)
    (doseq [pos [[0 0] [1 0] [2 0] [0 1] [2 1] [0 2]]]
      (update-test-world! assoc-in (conj pos :contents :country-id) 1))
    (test-utils/set-test-state! :round-number 30)
    (should= :fighter (strategy/opening-production [1 1])))

  (it "does not build the opening satellite on the original continent"
    (set-test-world! (build-test-map ["X"]))
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (update-test-world! assoc-in [0 0 :country-id] 1)
    (test-utils/update-test-computer-map! assoc-in [0 0 :country-id] 1)
    (test-utils/set-test-state! :round-number 51)
    (should= :army (strategy/opening-production [0 0])))

  (it "still allows the opening satellite on a non-origin continent"
    (set-test-world! (build-test-map ["X"]))
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (update-test-world! assoc-in [0 0 :country-id] 2)
    (test-utils/update-test-computer-map! assoc-in [0 0 :country-id] 2)
    (test-utils/set-test-state! :round-number 51)
    (should= :satellite (strategy/opening-production [0 0])))

  (it "delays coastal staging until transport production is close enough"
    (set-test-world! (build-test-map ["~X##"
                                      "####"
                                      "a###"]))
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (update-test-world! assoc-in [1 0 :country-id] 1)
    (update-test-world! assoc-in [0 2 :contents :country-id] 1)
    (test-utils/set-test-state! :round-number 30)
    (test-utils/set-test-state! :production {[1 0] {:item :transport :remaining-rounds 5}})
    (should-not (strategy/allow-coastal-staging? [0 2]))
    (test-utils/set-test-state! :production {[1 0] {:item :transport :remaining-rounds 2}})
    (should (strategy/allow-coastal-staging? [0 2])))

  (it "keeps one army city on all-coastal theaters before the army backlog is strong"
    (should= {:CA 1 :CF 0 :CT 2 :CP 0}
             (strategy/desired-role-counts {:coastal-count 3
                                            :landlocked-count 0
                                            :army-count 5
                                            :phase :phase-2})))

  (it "adds a patrol boat on all-coastal theaters once the army backlog is strong"
    (should= {:CA 1 :CF 0 :CT 2 :CP 1}
             (strategy/desired-role-counts {:coastal-count 4
                                            :landlocked-count 0
                                            :army-count 6
                                            :phase :phase-2})))

  (it "uses one inland fighter city when a large coast has one inland support city"
    (should= {:CA 1 :CF 1 :CT 2 :CP 1}
             (strategy/desired-role-counts {:coastal-count 4
                                            :landlocked-count 1
                                            :army-count 6
                                            :phase :phase-2})))

  (it "keeps the rest of large inland support on armies"
    (should= {:CA 2 :CF 1 :CT 3 :CP 1}
             (strategy/desired-role-counts {:coastal-count 4
                                            :landlocked-count 3
                                            :army-count 6
                                            :phase :phase-2})))

  (it "returns nil for opening production when the opening is inactive"
    (with-redefs [strategy/opening-active? (fn [] false)]
      (should-be-nil (strategy/opening-production [0 0]))))

  (it "resets lake production only for opening coastal roles that lost coast access"
    (with-redefs [strategy/opening-active? (fn [] true)
                  strategy/city-usable-coastal? (fn [_] false)
                  empire.state.api/read-state (fn [k]
                                                (case k
                                                  :computer-map [[{:opening-role :CT}]]
                                                  :production {[0 0] {:item :transport}}
                                                  nil))]
      (should (strategy/should-reset-lake-production? [0 0]))))

  (it "does not reset lake production for non-coastal opening roles"
    (with-redefs [strategy/opening-active? (fn [] true)
                  strategy/city-usable-coastal? (fn [_] false)
                  empire.state.api/read-state (fn [k]
                                                (case k
                                                  :computer-map [[{:opening-role :CA}]]
                                                  :production {[0 0] {:item :army}}
                                                  nil))]
      (should-not (strategy/should-reset-lake-production? [0 0]))))

  (it "allows coastal staging immediately when the opening is inactive"
    (with-redefs [strategy/opening-active? (fn [] false)]
      (should (strategy/allow-coastal-staging? [2 2])))))

(run-specs)
