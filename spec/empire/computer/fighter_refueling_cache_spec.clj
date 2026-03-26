(ns empire.computer.fighter.refueling-cache-spec
  (:require [empire.computer.fighter.movement-impl :as impl]
            [empire.config.domain.core.refueling :as refueling]
            [empire.test.utils :refer [reset-all-atoms!]]
            [empire.state.api :as sa]
            [speclj.core :refer :all]))

(describe "refueling position cache"
  (before (reset-all-atoms!)
          (impl/clear-refueling-cache!))

  (it "caches scan-refueling-positions across calls"
    (let [scan-count (atom 0)]
      (with-redefs [refueling/scan-refueling-positions
                    (fn [_] (swap! scan-count inc)
                      {:cities #{[0 0]} :carriers #{}})]
        (impl/find-nearest-refueling-site [1 1])
        (impl/find-nearest-refueling-site [2 2])
        (should= 1 @scan-count))))

  (it "returns fresh data after cache clear"
    (let [scan-count (atom 0)]
      (with-redefs [refueling/scan-refueling-positions
                    (fn [_] (swap! scan-count inc)
                      {:cities #{[0 0]} :carriers #{}})]
        (impl/find-nearest-refueling-site [1 1])
        (impl/clear-refueling-cache!)
        (impl/find-nearest-refueling-site [1 1])
        (should= 2 @scan-count)))))
