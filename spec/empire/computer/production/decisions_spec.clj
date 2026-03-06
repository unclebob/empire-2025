(ns empire.computer.production.decisions-spec
  (:require [empire.test.utils :as test-utils]
            [empire.computer.production.decisions :as decisions]
            [empire.computer.production.stats :as stats]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-world! update-test-world! set-test-computer-map!]]
            [speclj.core :refer :all]))

(describe "production decisions module"
  (before (reset-all-atoms!))

  (it "produces destroyer when escort is needed"
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
    (should= :destroyer (decisions/decide-production [1 0])))

  (it "produces fighter only when fighters are below computer city count"
    (set-test-world! (build-test-map ["X#Xf"]))
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (should= :fighter (#'decisions/decide-country-production [0 0] 1 false {:transport 0 :destroyer 0}))
    (update-test-world! assoc-in [1 0] {:type :sea :contents {:type :fighter :owner :computer}})
    (should-not= :fighter (#'decisions/decide-country-production [0 0] 1 false {:transport 0 :destroyer 0})))

  (it "chooses submarine in global production when carrier ratio requires it"
    (should= :submarine (#'decisions/decide-global-production true {:carrier 2 :battleship 2 :submarine 1 :satellite 1})))

  (it "chooses satellite in global production when carrier and capital-ship gates do not apply"
    (with-redefs [empire.computer.production.stats/count-computer-cities (constantly 20)]
      (should= :satellite (#'decisions/decide-global-production false {:carrier 0 :battleship 0 :submarine 0 :satellite 0}))))

  (it "executes early production side effects"
    (test-utils/set-test-state! :transport-fully-loaded? true)
    (test-utils/set-test-state! :early-patrol-boat-produced? false)
    (test-utils/set-test-state! :early-satellite-produced? false)
    (should= :patrol-boat (#'decisions/decide-early-production [0 0] true))
    (should (test-utils/read-test-state :early-patrol-boat-produced?))
    (should= :satellite (#'decisions/decide-early-production [0 0] false))
    (should (test-utils/read-test-state :early-satellite-produced?)))

  (it "falls back to army when city has no country and no early production"
    (set-test-world! (build-test-map ["X"]))
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (test-utils/set-test-state! :transport-fully-loaded? false)
    (should= :army (decisions/decide-production [0 0]))))
