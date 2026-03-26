(ns empire.computer.transport-query-cache-spec
  (:require [empire.computer.shared.transport-query :as tq]
            [empire.state.api :as sa]
            [empire.test.utils :refer [reset-all-atoms! build-test-map set-test-computer-map!]]
            [speclj.core :refer :all]))

(describe "loading transport position cache"
  (before (reset-all-atoms!)
          (tq/clear-loading-transport-cache!))

  (it "scans map only once for loading transports"
    (let [scan-count (atom 0)
          orig tq/scan-loading-transports]
      (set-test-computer-map! (build-test-map ["~t~"]))
      (sa/update-world! assoc-in [1 0 :contents :transport-mission] :loading)
      (set-test-computer-map! (sa/read-state :game-map))
      (with-redefs [tq/scan-loading-transports
                    (fn [w eid] (swap! scan-count inc) (orig w eid))]
        (tq/find-nearby-loading-transport [0 0] 5)
        (tq/find-nearby-loading-transport [2 0] 5)
        (should= 1 @scan-count))))

  (it "rescans after cache clear"
    (let [scan-count (atom 0)
          orig tq/scan-loading-transports]
      (set-test-computer-map! (build-test-map ["~t~"]))
      (sa/update-world! assoc-in [1 0 :contents :transport-mission] :loading)
      (set-test-computer-map! (sa/read-state :game-map))
      (with-redefs [tq/scan-loading-transports
                    (fn [w eid] (swap! scan-count inc) (orig w eid))]
        (tq/find-nearby-loading-transport [0 0] 5)
        (tq/clear-loading-transport-cache!)
        (tq/find-nearby-loading-transport [0 0] 5)
        (should= 2 @scan-count)))))
