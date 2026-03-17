(ns empire.debug-dump-range-spec
  (:require [empire.game-mechanics.debug.dump :as debug-dump]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-player-map! set-test-world!]]
            [speclj.core :refer :all]))

(describe "debug dump region extraction"
  (before (reset-all-atoms!))

  (it "extracts cells from coordinate range"
    (set-test-world! (build-test-map ["###"
                                      "###"
                                      "###"]))
    (set-test-player-map! (build-test-map ["###"
                                           "###"
                                           "###"]))
    (set-test-computer-map! (build-test-map ["###"
                                             "###"
                                             "###"]))
    (let [result (debug-dump/dump-region [0 0] [1 1])]
      (should (map? (:game-map result)))
      (should (map? (:player-map result)))
      (should (map? (:computer-map result)))
      (should= 4 (count (:game-map result)))))

  (it "handles empty maps gracefully"
    (set-test-world! [[nil nil] [nil nil]])
    (set-test-player-map! [[nil nil] [nil nil]])
    (set-test-computer-map! [[nil nil] [nil nil]])
    (let [result (debug-dump/dump-region [0 0] [1 1])]
      (should= 0 (count (:game-map result))))))

(describe "debug dump screen coordinate conversion"
  (before (reset-all-atoms!))

  (it "converts screen coords to cell range"
    (test-utils/set-test-state! :map-screen-dimensions [100 100])
    (set-test-world! (build-test-map ["#####"
                                      "#####"
                                      "#####"
                                      "#####"
                                      "#####"]))
    (let [[[sr sc] [er ec]] (debug-dump/screen-coords-to-cell-range [0 0] [99 99])]
      (should= 0 sr)
      (should= 0 sc)
      (should= 4 er)
      (should= 4 ec)))

  (it "normalizes reversed coordinates"
    (test-utils/set-test-state! :map-screen-dimensions [100 100])
    (set-test-world! (build-test-map ["#####"
                                      "#####"
                                      "#####"
                                      "#####"
                                      "#####"]))
    (let [[[sr sc] [er ec]] (debug-dump/screen-coords-to-cell-range [99 99] [0 0])]
      (should= 0 sr)
      (should= 0 sc)
      (should= 4 er)
      (should= 4 ec)))

  (it "clamps to map bounds"
    (test-utils/set-test-state! :map-screen-dimensions [100 100])
    (set-test-world! (build-test-map ["###"
                                      "###"
                                      "###"]))
    (let [[[sr sc] [er ec]] (debug-dump/screen-coords-to-cell-range [0 0] [200 200])]
      (should (>= sr 0))
      (should (>= sc 0))
      (should (<= er 2))
      (should (<= ec 2)))))
