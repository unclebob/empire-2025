(ns empire.computer.fighter.unexplored-cache-spec
  (:require [empire.computer.fighter.exploration :as fe]
            [empire.computer.fighter.movement :as fm]
            [empire.state.api :as sa]
            [empire.test.utils :refer [reset-all-atoms!]]
            [speclj.core :refer :all]))

(describe "nearest-unexplored-distance cache"
  (before (reset-all-atoms!)
          (fe/clear-unexplored-distance-cache!))

  (it "caches result for same position across calls"
    (let [scan-count (atom 0)
          orig fe/compute-nearest-unexplored-distance]
      (sa/write-state! :computer-map [[{:type :land} nil] [nil nil]])
      (with-redefs [fe/compute-nearest-unexplored-distance
                    (fn [pos] (swap! scan-count inc) (orig pos))]
        (fe/nearest-unexplored-distance [0 0])
        (fe/nearest-unexplored-distance [0 0])
        (should= 1 @scan-count))))

  (it "returns fresh data after cache clear"
    (let [scan-count (atom 0)]
      (sa/write-state! :computer-map [[{:type :land} nil]])
      (with-redefs [fe/compute-nearest-unexplored-distance
                    (fn [_] (swap! scan-count inc) 5)]
        (fe/nearest-unexplored-distance [0 0])
        (fe/clear-unexplored-distance-cache!)
        (fe/nearest-unexplored-distance [0 0])
        (should= 2 @scan-count)))))
