(ns empire.computer.production.decisions-spec
  (:require [empire.atoms :as atoms]
            [empire.computer.production.decisions :as decisions]
            [empire.computer.production.stats :as stats]
            [empire.test-utils :refer [build-test-map reset-all-atoms!]]
            [speclj.core :refer :all]))

(describe "production decisions module"
  (before (reset-all-atoms!))

  (it "produces destroyer when escort is needed"
    (reset! atoms/game-map (build-test-map ["~Xaat~pppp"
                                             "~~~~~~~~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (doseq [col [2 3]]
      (swap! atoms/game-map assoc-in [col 0 :country-id] 1)
      (swap! atoms/game-map assoc-in [col 0 :contents :country-id] 1))
    (swap! atoms/game-map assoc-in [4 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [4 0 :contents :transport-id] 1)
    (doseq [col [6 7 8 9]]
      (swap! atoms/game-map assoc-in [col 0 :contents :country-id] 1))
    (stats/rebuild-country-stats!)
    (should= :destroyer (decisions/decide-production [1 0])))

  (it "produces fighter only when fighters are below computer city count"
    (reset! atoms/game-map (build-test-map ["X#Xf"]))
    (reset! atoms/computer-map @atoms/game-map)
    (should= :fighter (decisions/decide-country-production [0 0] 1 false {:transport 0 :destroyer 0}))
    (swap! atoms/game-map assoc-in [1 0] {:type :sea :contents {:type :fighter :owner :computer}})
    (should-not= :fighter (decisions/decide-country-production [0 0] 1 false {:transport 0 :destroyer 0})))

  (it "chooses submarine in global production when carrier ratio requires it"
    (should= :submarine (decisions/decide-global-production true {:carrier 2 :battleship 2 :submarine 1 :satellite 1})))

  (it "chooses satellite in global production when carrier and capital-ship gates do not apply"
    (with-redefs [empire.computer.production.stats/count-computer-cities (constantly 20)]
      (should= :satellite (decisions/decide-global-production false {:carrier 0 :battleship 0 :submarine 0 :satellite 0}))))

  (it "executes early production side effects"
    (reset! atoms/transport-fully-loaded? true)
    (reset! atoms/early-patrol-boat-produced? false)
    (reset! atoms/early-satellite-produced? false)
    (should= :patrol-boat (decisions/decide-early-production [0 0] true))
    (should @atoms/early-patrol-boat-produced?)
    (should= :satellite (decisions/decide-early-production [0 0] false))
    (should @atoms/early-satellite-produced?))

  (it "falls back to army when city has no country and no early production"
    (reset! atoms/game-map (build-test-map ["X"]))
    (reset! atoms/computer-map @atoms/game-map)
    (reset! atoms/transport-fully-loaded? false)
    (should= :army (decisions/decide-production [0 0]))))
