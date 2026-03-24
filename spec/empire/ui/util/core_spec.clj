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
      ;; text-h = 5 * 20 = 100
      (should= 100 (nth (:text-area-dimensions result) 3))))

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

(describe "calculate-screen-dimensions"
  (before (reset-all-atoms!))

  (it "stores derived dimensions in application state"
    (test-utils/set-test-state! :map-size [80 40])
    (util-core/calculate-screen-dimensions)
    (should= [(* 80 11) (* 40 16)]
             (test-utils/read-test-state :map-screen-dimensions))
    (should= [0 (+ (* 40 16) 7) (* 80 11) (* 5 16)]
             (test-utils/read-test-state :text-area-dimensions))))

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
      (should= 60 (:rows result))
      (should= 50 (:handicap result))))

  (it "parses cols and rows from positional args"
    (let [result (util-core/parse-args ["80" "40"] 2000 2000)]
      (should= 80 (:cols result))
      (should= 40 (:rows result))))

  (it "extracts --seed=N"
    (let [result (util-core/parse-args ["--seed=42"] 2000 2000)]
      (should= 42 (:seed result))))

  (it "extracts --handicap=N"
    (let [result (util-core/parse-args ["--handicap=12"] 2000 2000)]
      (should= 12 (:handicap result))))

  (it "extracts --log"
    (let [result (util-core/parse-args ["--log"] 2000 2000)]
      (should= true (:log-enabled result))))

  (it "extracts --debug-dump"
    (let [result (util-core/parse-args ["--debug-dump"] 2000 2000)]
      (should= true (:debug-dump-enabled result))))

  (it "extracts --slow-round-analysis=T:N"
    (let [result (util-core/parse-args ["--slow-round-analysis=500:20"] 2000 2000)]
      (should= {:threshold-ms 500 :rounds 20}
               (:slow-round-analysis result))))

  (it "extracts --limits=SPEC"
    (let [result (util-core/parse-args ["--limits=t:8,p:4"] 2000 2000)]
      (should= {:transport 8 :patrol-boat 4}
               (:production-limits result))))

  (it "extracts --headless=N and uses it as handicap"
    (let [result (util-core/parse-args ["--headless=30"] 2000 2000)]
      (should= 30 (:headless-rounds result))
      (should= 30 (:handicap result))))

  (it "returns nil seed when no seed arg"
    (let [result (util-core/parse-args ["80" "40"] 2000 2000)]
      (should-be-nil (:seed result))))

  (it "computes window dimensions from cols and rows"
    (let [result (util-core/parse-args ["80" "40"] 2000 2000)]
      (should= (* 80 11) (:window-w result))
      (should= (+ (* 40 16) (* 5 16) 7) (:window-h result))))

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
          (should= (quot (- 1000 (* 5 16) 7) 16) (:max-rows data))))))

  (it "ignores seed arg when computing dimensions"
    (let [result (util-core/parse-args ["--seed=99" "50" "30"] 2000 2000)]
      (should= 50 (:cols result))
      (should= 30 (:rows result))
      (should= 99 (:seed result))))

  (it "ignores handicap arg when computing dimensions"
    (let [result (util-core/parse-args ["--handicap=7" "50" "30"] 2000 2000)]
      (should= 50 (:cols result))
      (should= 30 (:rows result))
      (should= 7 (:handicap result))))

  (it "ignores headless arg when computing dimensions"
    (let [result (util-core/parse-args ["--headless=15" "50" "30"] 2000 2000)]
      (should= 50 (:cols result))
      (should= 30 (:rows result))
      (should= 15 (:headless-rounds result))
      (should= 15 (:handicap result))))

  (it "ignores log arg when computing dimensions"
    (let [result (util-core/parse-args ["--log" "50" "30"] 2000 2000)]
      (should= 50 (:cols result))
      (should= 30 (:rows result))
      (should= true (:log-enabled result))))

  (it "ignores debug dump arg when computing dimensions"
    (let [result (util-core/parse-args ["--debug-dump" "50" "30"] 2000 2000)]
      (should= 50 (:cols result))
      (should= 30 (:rows result))
      (should= true (:debug-dump-enabled result))))

  (it "ignores slow-round-analysis arg when computing dimensions"
    (let [result (util-core/parse-args ["--slow-round-analysis=750:12" "50" "30"] 2000 2000)]
      (should= 50 (:cols result))
      (should= 30 (:rows result))
      (should= {:threshold-ms 750 :rounds 12}
               (:slow-round-analysis result))))

  (it "ignores limits arg when computing dimensions"
    (let [result (util-core/parse-args ["--limits=t:8,p:4" "50" "30"] 2000 2000)]
      (should= 50 (:cols result))
      (should= 30 (:rows result))
      (should= {:transport 8 :patrol-boat 4}
               (:production-limits result))))

  (it "skips screen bounds checks when screen dimensions are absent"
    (let [result (util-core/parse-args ["--headless=10" "200" "100"] nil nil)]
      (should= 200 (:cols result))
      (should= 100 (:rows result))
      (should= 10 (:headless-rounds result))))

  (it "rejects malformed slow-round-analysis values"
    (should-throw clojure.lang.ExceptionInfo
      (util-core/parse-args ["--slow-round-analysis=500"] 2000 2000)))

  )

(describe "help-requested?"
  (it "returns true for --help"
    (should (util-core/help-requested? ["--help"])))

  (it "returns true for -h"
    (should (util-core/help-requested? ["-h"])))

  (it "returns false when help is absent"
    (should-not (util-core/help-requested? ["--seed=42"]))))

(describe "headless-requested?"
  (it "returns true for --headless=N"
    (should (util-core/headless-requested? ["--headless=40"])))

  (it "returns false when headless is absent"
    (should-not (util-core/headless-requested? ["--seed=42"]))))

(describe "log-requested?"
  (it "returns true for --log"
    (should (util-core/log-requested? ["--log"])))

  (it "returns false when log is absent"
    (should-not (util-core/log-requested? ["--seed=42"]))))

(describe "debug-dump-requested?"
  (it "returns true for --debug-dump"
    (should (util-core/debug-dump-requested? ["--debug-dump"])))

  (it "returns false when debug dump is absent"
    (should-not (util-core/debug-dump-requested? ["--seed=42"]))))

(describe "slow-round-analysis-requested?"
  (it "parses threshold and round count"
    (should= {:threshold-ms 500 :rounds 20}
             (util-core/slow-round-analysis-requested? ["--slow-round-analysis=500:20"])))

  (it "returns nil when slow round analysis is absent"
    (should-be-nil
     (util-core/slow-round-analysis-requested? ["--seed=42"]))))

(describe "unit-log-filename"
  (it "uses the empire-units timestamped naming convention"
    (with-redefs [util-core/startup-timestamp (constantly "2026-03-19-120000")]
      (should= "empire-units2026-03-19-120000.log"
               (util-core/unit-log-filename)))))

(describe "usage-text"
  (it "documents help handicap seed headless log and debug dump options"
    (let [usage (util-core/usage-text)]
      (should-contain "--help" usage)
      (should-contain "--log" usage)
      (should-contain "--debug-dump" usage)
      (should-contain "--slow-round-analysis=T:N" usage)
      (should-contain "--seed=N" usage)
      (should-contain "--limits=SPEC" usage)
      (should-contain "--headless=N" usage)
      (should-contain "--handicap=N" usage)
      (should-contain "Default: 50" usage))))
