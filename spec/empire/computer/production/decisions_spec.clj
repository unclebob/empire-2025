(ns empire.computer.production.decisions-spec
  (:require [empire.test.utils :as test-utils]
            [empire.computer.production.decisions :as decisions]
            [empire.computer.production.stats :as stats]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-world! update-test-world! set-test-computer-map!]]
            [speclj.core :refer :all]))

(describe "production decisions module"
  (before (reset-all-atoms!))

  (it "keeps building armies while opening production is still army-first"
    (set-test-world! (build-test-map ["~Xaat~pppp"
                                      "~~~~~~~~~~"]))
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (update-test-world! assoc-in [1 0 :country-id] 1)
    (doseq [col [2 3]]
      (update-test-world! assoc-in [col 0 :country-id] 1)
      (update-test-world! assoc-in [col 0 :contents :country-id] 1))
    (update-test-world! assoc-in [4 0 :contents :country-id] 1)
    (update-test-world! assoc-in [4 0 :contents :transport-id] 1)
    (doseq [col [6 7 8 9]]
      (update-test-world! assoc-in [col 0 :contents :country-id] 1))
    (stats/rebuild-country-stats!)
    (should= :army (decisions/decide-production [1 0])))

  (it "produces fighter only when fighters are below computer city count"
    (set-test-world! (build-test-map ["X#Xf"]))
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (should= :fighter (#'decisions/decide-country-production [0 0] 1 false {:transport 0 :destroyer 0}))
    (update-test-world! assoc-in [1 0] {:type :sea :contents {:type :fighter :owner :computer}})
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (should-not= :fighter (#'decisions/decide-country-production [0 0] 1 false {:transport 0 :destroyer 0})))

  (it "chooses submarine in global production when carrier ratio requires it"
    (should= :submarine (#'decisions/decide-global-production true {:carrier 2 :battleship 2 :submarine 1 :satellite 1})))

  (it "chooses satellite in global production when carrier and capital-ship gates do not apply"
    (with-redefs [empire.computer.production.stats/count-computer-cities (constantly 20)]
      (should= :satellite (#'decisions/decide-global-production false {:carrier 0 :battleship 0 :submarine 0 :satellite 0}))))

  (it "uses opening strategy production before legacy heuristics"
    (set-test-world! (build-test-map ["aaa"
                                      "aXa"
                                      "aaa"]))
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (update-test-world! assoc-in [1 1 :country-id] 1)
    (doseq [pos [[0 0] [1 0] [2 0] [0 1] [2 1] [0 2]]]
      (update-test-world! assoc-in (conj pos :contents :country-id) 1))
    (test-utils/set-test-state! :round-number 30)
    (should= :fighter (decisions/decide-production [1 1])))

  (it "does not mark the opening satellite on the original continent"
    (set-test-world! (build-test-map ["X"]))
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (update-test-world! assoc-in [0 0 :country-id] 1)
    (test-utils/update-test-computer-map! assoc-in [0 0 :country-id] 1)
    (test-utils/set-test-state! :round-number 51)
    (should= :army (decisions/decide-production [0 0]))
    (should-not (test-utils/read-test-state :opening-satellite-produced?)))

  (it "marks the one-time opening satellite when assigned on a non-origin continent"
    (set-test-world! (build-test-map ["X"]))
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (update-test-world! assoc-in [0 0 :country-id] 2)
    (test-utils/update-test-computer-map! assoc-in [0 0 :country-id] 2)
    (test-utils/set-test-state! :round-number 51)
    (should= :satellite (decisions/decide-production [0 0]))
    (should (test-utils/read-test-state :opening-satellite-produced?)))

  (it "falls back to army when city has no country and no early production"
    (set-test-world! (build-test-map ["X"]))
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (test-utils/set-test-state! :round-number 1)
    (should= :army (decisions/decide-production [0 0])))

  (it "writes the last transport city when producing a transport"
    (with-redefs [empire.computer.production.stats/count-country-armies (constantly empire.config.core/armies-before-transport)
                  empire.computer.production.stats/country-has-waiting-armies? (constantly true)
                  empire.computer.production.stats/country-has-other-coastal-city? (constantly false)]
      (should= :transport (#'decisions/should-produce-transport? [4 4] 2 true))
      (should= [4 4] (get (test-utils/read-test-state :last-transport-city) 2))))

  (it "suppresses transport production when rotation would repeat the same city"
    (test-utils/set-test-state! :last-transport-city {2 [4 4]})
    (with-redefs [empire.computer.production.stats/count-country-armies (constantly empire.config.core/armies-before-transport)
                  empire.computer.production.stats/country-has-waiting-armies? (constantly true)
                  empire.computer.production.stats/country-has-other-coastal-city? (constantly true)]
      (should-be-nil (#'decisions/should-produce-transport? [4 4] 2 true))))

  (it "falls back to army when a transport production limit is reached"
    (test-utils/set-test-state! :computer-production-limits {:transport 2})
    (with-redefs [empire.computer.threat-response.kamikazee/invasion-production-override (constantly nil)
                  empire.computer.early-game.strategy/opening-production (constantly nil)
                  empire.computer.production.stats/city-is-coastal? (constantly true)
                  empire.computer.production.stats/country-army-limit-reached? (constantly false)
                  empire.computer.production.decisions/decide-country-production (constantly :transport)
                  empire.computer.production.decisions/decide-global-production (constantly nil)
                  empire.computer.production.stats/count-computer-units (constantly {:transport 2})]
      (set-test-computer-map! [[{:type :city :city-status :computer :country-id 1}]])
      (should= :army (decisions/decide-production [0 0]))))

  (it "falls back to army when a patrol-boat production limit is reached"
    (test-utils/set-test-state! :computer-production-limits {:patrol-boat 4})
    (with-redefs [empire.computer.threat-response.kamikazee/invasion-production-override (constantly nil)
                  empire.computer.early-game.strategy/opening-production (constantly nil)
                  empire.computer.production.stats/city-is-coastal? (constantly true)
                  empire.computer.production.stats/country-army-limit-reached? (constantly false)
                  empire.computer.production.decisions/decide-country-production (constantly :patrol-boat)
                  empire.computer.production.decisions/decide-global-production (constantly nil)
                  empire.computer.production.stats/count-computer-units (constantly {:patrol-boat 4})]
      (set-test-computer-map! [[{:type :city :city-status :computer :country-id 1}]])
      (should= :army (decisions/decide-production [0 0]))))

  (it "prefers battleships before submarines in global capital ship production"
    (should= :battleship (#'decisions/decide-global-production true {:carrier 2 :battleship 1 :submarine 5 :satellite 0})))

  (it "resets lake production and clears opening role when requested"
    (let [calls (atom [])]
      (with-redefs [empire.computer.early-game.strategy/should-reset-lake-production? (constantly true)
                    empire.computer.production.decisions/decide-production (constantly :army)
                    empire.computer.production.selection-decisions/process-city-action (fn [_]
                                                                                         {:reset-lake-production? true
                                                                                          :set-production? false})
                    empire.state.api/update-state! (fn [& args] (swap! calls conj [:state args]))
                    empire.state.api/update-world! (fn [& args] (swap! calls conj [:world args]))
                    empire.game-mechanics.services.city-production/set-city-production (fn [& args] (swap! calls conj [:set args]))]
        (decisions/process-computer-city [1 1])
        (should= [[:state [:production dissoc [1 1]]]
                  [:world [update-in [1 1] dissoc :opening-role]]]
                 @calls))))

  (it "sets city production when the selection action requests it"
    (let [calls (atom [])]
      (with-redefs [empire.computer.early-game.strategy/should-reset-lake-production? (constantly false)
                    empire.computer.production.decisions/decide-production (constantly :fighter)
                    empire.computer.production.selection-decisions/process-city-action (fn [_]
                                                                                         {:reset-lake-production? false
                                                                                          :set-production? true
                                                                                          :unit-type :fighter})
                    empire.game-mechanics.services.city-production/set-city-production (fn [pos unit-type]
                                                                                          (swap! calls conj [pos unit-type]))]
        (decisions/process-computer-city [3 2])
        (should= [[[3 2] :fighter]] @calls)))))
