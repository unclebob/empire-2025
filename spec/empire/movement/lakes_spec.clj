(ns empire.movement.lakes-spec
  (:require [speclj.core :refer :all]
            [empire.movement.lakes :as lakes]
            [empire.test.utils :refer [build-test-map]]))

(describe "lake-cells"
  (it "returns only sea cells in components at or below size limit"
    (let [the-map (build-test-map ["~~##~~"
                                   "~~##~~"
                                   "~~##~~"])
          ;; two sea components separated by full-height land wall; each side size 6
          result (lakes/lake-cells the-map 8)]
      (should= 12 (count result))))

  (it "excludes sea components larger than size limit"
    (let [the-map (build-test-map ["~~~~"
                                   "~~~~"
                                   "~~~~"])
          result (lakes/lake-cells the-map 10)]
      (should= #{} result)))

  (it "returns empty set when limit is zero"
    (let [the-map (build-test-map ["~~"
                                   "~~"])]
      (should= #{} (lakes/lake-cells the-map 0))))

  (it "does not classify sea as lake when adjacent to nil unexplored"
    (let [the-map (build-test-map ["....."
                                   ".~~~."
                                   ".~#~."
                                   ".~~~."
                                   "....."])
          result (lakes/lake-cells the-map 20)]
      (should= #{} result)))

  (it "does not classify sea as lake when adjacent to :unexplored cells"
    (let [the-map [[{:type :sea} {:type :sea} {:type :sea}]
                   [{:type :sea} {:type :sea} {:type :sea}]
                   [{:type :unexplored} {:type :sea} {:type :sea}]]
          result (lakes/lake-cells the-map 20)]
      (should= #{} result))))
