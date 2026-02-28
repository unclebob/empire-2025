(ns empire.computer.production.stats-spec
  (:require [empire.atoms :as atoms]
            [empire.computer.production.stats :as stats]
            [empire.test-utils :refer [build-test-map reset-all-atoms!]]
            [speclj.core :refer :all]))

(describe "production stats module"
  (before (reset-all-atoms!))

  (it "tracks coastal cities, unexplored coast, and unoccupied coastal land"
    (reset! atoms/game-map (build-test-map ["~X~#~X~"]))
    (swap! atoms/game-map assoc-in [1 0 :country-id] 7)
    (swap! atoms/game-map assoc-in [3 0 :country-id] 7)
    (swap! atoms/game-map assoc-in [5 0 :country-id] 7)
    (reset! atoms/computer-map [[{:type :sea}] [nil] [{:type :sea}] [{:type :land}] [{:type :sea}] [{:type :city :city-status :computer}] [{:type :sea}]])
    (stats/rebuild-country-stats!)
    (should (stats/has-unoccupied-coastal-cells? 7))
    (should-not (stats/country-coastal-cells-explored? 7))
    (should (stats/country-has-other-coastal-city? [1 0] 7)))

  (it "returns defaults for missing country stats"
    (reset! atoms/country-stats {})
    (should= 0 (stats/count-country-armies 99))
    (should= 0 (stats/count-country-coastal-cells 99))
    (should= 0 (stats/count-country-patrol-boats 99))
    (should (stats/country-coastal-cells-explored? 99)))

  (it "reads populated country stats getters"
    (reset! atoms/country-stats
            {7 {:army-count 3
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
    (reset! atoms/game-map (build-test-map ["fF~"]))
    (swap! atoms/game-map assoc-in [2 0 :contents] {:type :army :owner :computer :country-id 1})
    (should= 1 (stats/count-all-computer-fighters)))

  (it "covers private helper predicates"
    (should= {:c {:k 1}} (@#'empire.computer.production.stats/update-country {} :c :k inc))
    (should (@#'empire.computer.production.stats/unoccupied-coastal-land? :land {:type :land}))
    (should (@#'empire.computer.production.stats/coastal-computer-city? :city {:city-status :computer}))
    (should (@#'empire.computer.production.stats/computer-unit-with-country?
              {:type :army :owner :computer :country-id 1}))))
