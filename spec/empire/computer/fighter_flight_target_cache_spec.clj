(ns empire.computer.fighter.flight-target-cache-spec
  (:require [empire.computer.fighter.flight-decisions :as decisions]
            [empire.computer.fighter.movement :as fm]
            [speclj.core :refer :all]))

(describe "active flight targets cache"
  (before (decisions/clear-active-targets-cache!))

  (it "scans world only once per round for active targets"
    (let [world [[{:contents {:type :fighter :owner :computer :flight-target-site [0 1]}}
                  {:type :city :city-status :computer}]
                 [{:type :land}
                  {:type :city :city-status :computer}]]
          scan-count (atom 0)
          orig-scan decisions/scan-active-targets]
      (with-redefs [decisions/scan-active-targets
                    (fn [w] (swap! scan-count inc) (orig-scan w))
                    fm/distance-to (fn [a b] 1)]
        (decisions/choose-leg world [[0 1] [1 1]] {} [0 1])
        (decisions/choose-leg world [[0 1] [1 1]] {} [0 1])
        (should= 1 @scan-count))))

  (it "rescans after cache clear"
    (let [world [[{:type :land} {:type :city :city-status :computer}]]
          scan-count (atom 0)]
      (with-redefs [decisions/scan-active-targets
                    (fn [_] (swap! scan-count inc) #{})
                    fm/distance-to (fn [a b] 1)]
        (decisions/choose-leg world [[0 1]] {} [0 1])
        (decisions/clear-active-targets-cache!)
        (decisions/choose-leg world [[0 1]] {} [0 1])
        (should= 2 @scan-count)))))
