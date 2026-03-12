(ns empire.ui.util.core-spec
  (:require [empire.test.utils :as test-utils]
            [empire.ui.util.core :as util-core]
            [empire.test.utils :refer [reset-all-atoms!]]
            [speclj.core :refer :all]))

(describe "key-released"
  (before (reset-all-atoms!))
  (it "resets last-key atom to nil"
    (test-utils/set-test-state! :last-key :a)
    (util-core/key-released nil nil)
    (should-be-nil (test-utils/read-test-state :last-key)))

  (it "returns nil when last-key was already nil"
    (test-utils/set-test-state! :last-key nil)
    (util-core/key-released nil nil)
    (should-be-nil (test-utils/read-test-state :last-key))))

(describe "compute-screen-dimensions"
  (before (reset-all-atoms!))
  (it "calculates pixel dimensions from known map-size and cell-size"
    (let [result (util-core/compute-screen-dimensions 100 36 10 20)]
      ;; map-display-w = 100 * 10 = 1000
      (should= 1000 (first (:map-screen-dimensions result)))
      ;; map-display-h = 36 * 20 = 720
      (should= 720 (second (:map-screen-dimensions result)))
      ;; text-x = 0
      (should= 0 (first (:text-area-dimensions result)))
      ;; text-y = 720 + 7 = 727
      (should= 727 (second (:text-area-dimensions result)))
      ;; text-w = 1000
      (should= 1000 (nth (:text-area-dimensions result) 2))
      ;; text-h = 4 * 20 = 80
      (should= 80 (nth (:text-area-dimensions result) 3))))

  (it "calculates dimensions for small map"
    (let [result (util-core/compute-screen-dimensions 80 26 8 16)]
      ;; map-display-w = 80 * 8 = 640
      (should= 640 (first (:map-screen-dimensions result)))
      ;; map-display-h = 26 * 16 = 416
      (should= 416 (second (:map-screen-dimensions result)))))

  (it "calculates dimensions for wide map"
    (let [result (util-core/compute-screen-dimensions 160 41 12 24)]
      ;; map-display-w = 160 * 12 = 1920
      (should= 1920 (first (:map-screen-dimensions result)))
      ;; map-display-h = 41 * 24 = 984
      (should= 984 (second (:map-screen-dimensions result))))))

(describe "screen->cell"
  (it "converts screen center to correct cell"
    (should= [5 3] (util-core/screen->cell 55 48 110 160 10 10)))

  (it "converts origin to [0 0]"
    (should= [0 0] (util-core/screen->cell 0 0 100 100 10 10)))

  (it "converts last cell"
    (should= [9 9] (util-core/screen->cell 99 99 100 100 10 10)))

  (it "handles non-square maps"
    (should= [2 1] (util-core/screen->cell 50 30 100 60 4 2)))

  (it "handles pixel at cell boundary"
    (should= [1 0] (util-core/screen->cell 10 0 100 100 10 10)))

  (it "handles single-pixel cells"
    (should= [3 4] (util-core/screen->cell 3 4 10 10 10 10))))

(describe "parse-args"
  (it "returns default map size when no args"
    (let [result (util-core/parse-args [] 2000 2000)]
      (should= 100 (:cols result))
      (should= 60 (:rows result))))

  (it "parses cols and rows from positional args"
    (let [result (util-core/parse-args ["80" "40"] 2000 2000)]
      (should= 80 (:cols result))
      (should= 40 (:rows result))))

  (it "extracts --seed=N"
    (let [result (util-core/parse-args ["--seed=42"] 2000 2000)]
      (should= 42 (:seed result))))

  (it "returns nil seed when no seed arg"
    (let [result (util-core/parse-args ["80" "40"] 2000 2000)]
      (should-be-nil (:seed result))))

  (it "computes window dimensions from cols and rows"
    (let [result (util-core/parse-args ["80" "40"] 2000 2000)]
      (should= (* 80 11) (:window-w result))
      (should= (+ (* 40 16) (* 4 16) 7) (:window-h result))))

  (it "throws ex-info when map exceeds screen"
    (should-throw clojure.lang.ExceptionInfo
      (util-core/parse-args ["200" "100"] 1000 1000)))

  (it "ex-data contains max-cols and max-rows"
    (try
      (util-core/parse-args ["200" "100"] 1000 1000)
      (should-fail "expected exception")
      (catch clojure.lang.ExceptionInfo e
        (let [data (ex-data e)]
          (should= (quot 1000 11) (:max-cols data))
          (should= (quot (- 1000 (* 4 16) 7) 16) (:max-rows data))))))

  (it "ignores seed arg when computing dimensions"
    (let [result (util-core/parse-args ["--seed=99" "50" "30"] 2000 2000)]
      (should= 50 (:cols result))
      (should= 30 (:rows result))
      (should= 99 (:seed result)))))
