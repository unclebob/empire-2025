(ns empire.computer.production-stats-cache-spec
  (:require [empire.computer.production.stats :as stats]
            [empire.state.api :as sa]
            [empire.test.utils :refer [reset-all-atoms!]]
            [speclj.core :refer :all]))

(describe "asset counts cache"
  (before (reset-all-atoms!))

  (it "scans computer map only once per round for unit counts"
    (let [scan-count (atom 0)
          orig stats/scan-computer-assets]
      (sa/write-state! :computer-map [[{:type :sea}]])
      (stats/rebuild-country-stats!)
      (with-redefs [stats/scan-computer-assets
                    (fn [cm] (swap! scan-count inc) (orig cm))]
        (stats/count-computer-units)
        (sa/write-state! :computer-map [[{:type :land}]])
        (stats/count-computer-units)
        (should= 0 @scan-count))))

  (it "rescans after cache clear"
    (let [scan-count (atom 0)
          orig stats/scan-computer-assets]
      (sa/write-state! :computer-map [[{:type :sea}]])
      (stats/rebuild-country-stats!)
      (with-redefs [stats/scan-computer-assets
                    (fn [cm] (swap! scan-count inc) (orig cm))]
        (stats/count-computer-units)
        (stats/clear-asset-cache!)
        (stats/count-computer-units)
        (should= 1 @scan-count)))))
