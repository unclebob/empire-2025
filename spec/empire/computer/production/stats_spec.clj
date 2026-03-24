(ns empire.computer.production.stats-spec
  (:require [empire.test.utils :as test-utils]
            [empire.computer.production.stats :as stats]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-world! update-test-world! set-test-computer-map!]]
            [speclj.core :refer :all]))

(describe "production stats module"
  (before (reset-all-atoms!))

  (it "tracks coastal cities and unoccupied coastal land from visible cells"
    (set-test-world! (build-test-map ["~X~#~X~"]))
    (update-test-world! assoc-in [1 0 :country-id] 7)
    (update-test-world! assoc-in [3 0 :country-id] 7)
    (update-test-world! assoc-in [5 0 :country-id] 7)
    (set-test-computer-map! [[{:type :sea}]
                             [{:type :city :city-status :computer :country-id 7}]
                             [{:type :sea}]
                             [{:type :land :country-id 7}]
                             [{:type :sea}]
                             [{:type :city :city-status :computer :country-id 7}]
                             [{:type :sea}]])
    (stats/rebuild-country-stats!)
    (should (stats/has-unoccupied-coastal-cells? 7))
    (should (stats/country-coastal-cells-explored? 7))
    (should (stats/country-has-other-coastal-city? [1 0] 7)))

  (it "returns defaults for missing country stats"
    (test-utils/set-test-state! :country-stats {})
    (should= 0 (stats/count-country-armies 99))
    (should= 0 (stats/count-country-coastal-cells 99))
    (should= 0 (stats/count-country-patrol-boats 99))
    (should (stats/country-coastal-cells-explored? 99)))

  (it "reads populated country stats getters"
    (test-utils/set-test-state! :country-stats {7 {:army-count 3
                :coastal-cell-count 4
                :coastal-explored? false
                :patrol-boat-count 2
                :coastal-city-positions #{[1 0] [3 0]}}
             8 {:coastal-city-positions #{[9 9]}}})
    (should= 3 (stats/count-country-armies 7))
    (should= 4 (stats/count-country-coastal-cells 7))
    (should-not (stats/country-coastal-cells-explored? 7))
    (should= 2 (stats/count-country-patrol-boats 7))
    (should (stats/country-has-other-coastal-city? [1 0] 7))
    (should-not (stats/country-has-other-coastal-city? [9 9] 8)))

  (it "counts only computer fighters globally"
    (set-test-world! (build-test-map ["fF~"]))
    (update-test-world! assoc-in [2 0 :contents] {:type :army :owner :computer :country-id 1})
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (should= 1 (stats/count-all-computer-fighters)))

  (it "reuses cached computer counts after rebuilding country stats"
    (set-test-world! (build-test-map ["aXd"]))
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (stats/rebuild-country-stats!)
    (should= {:army 1 :destroyer 1} (stats/count-computer-units))
    (should= 1 (stats/count-computer-cities))
    (should= 0 (stats/count-all-computer-fighters)))

  (it "falls back to rescanning when the computer map changes after caching"
    (set-test-world! (build-test-map ["a~"]))
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (stats/rebuild-country-stats!)
    (test-utils/set-test-computer-map! (build-test-map ["fX"]))
    (should= {:fighter 1} (stats/count-computer-units))
    (should= 1 (stats/count-computer-cities))
    (should= 1 (stats/count-all-computer-fighters)))

  (it "covers private helper predicates"
    (should= {:c {:k 1}} (@#'empire.computer.production.stats/update-country {} :c :k inc))
    (should (@#'empire.computer.production.stats/unoccupied-coastal-land? :land {:type :land}))
    (should (@#'empire.computer.production.stats/coastal-computer-city? :city {:city-status :computer}))
    (should (@#'empire.computer.production.stats/computer-unit-with-country?
              {:type :army :owner :computer :country-id 1}))))
