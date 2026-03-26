(ns empire.computer.transport-load-targeting-perf-spec
  (:require [empire.computer.transport.load-targeting :as load-targeting]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [build-test-map reset-all-atoms!]]
            [speclj.core :refer :all]))

(describe "choose-load-target-cell efficiency"
  (before (reset-all-atoms!))

  (it "computes coastal army positions only once across all candidates"
    (let [computer-map (build-test-map ["#~~~~#####"
                                        "~t~~~~~~~~"
                                        "~~~~~aaaa~"
                                        "~~~~~~~~~~"
                                        "~~~~~~~~~~"])
          _ (test-utils/set-test-world! computer-map)
          _ (test-utils/update-test-world! assoc-in [0 0 :country-id] 1)
          _ (doseq [pos [[5 0] [5 2] [6 2] [7 2] [8 2]]]
              (test-utils/update-test-world! assoc-in (conj pos :country-id) 2))
          cm (test-utils/read-test-state :game-map)
          scan-count (atom 0)
          orig load-targeting/all-coastal-army-positions]
      (with-redefs [load-targeting/all-coastal-army-positions
                    (fn [cmap rid] (swap! scan-count inc) (orig cmap rid))]
        (load-targeting/choose-load-target-cell [1 1] cm)
        (should= 1 @scan-count)))))
