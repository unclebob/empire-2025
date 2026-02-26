(ns empire.debug-spec
  (:require [speclj.core :refer :all]
            [empire.debug :as debug]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [build-test-map reset-all-atoms!]]
            [clojure.string :as str]))

(describe "format-cell handles nil contents fields"
  (before (reset-all-atoms!))

  (it "does not crash when contents has nil type or owner"
    (let [cell {:type :sea :contents {:type nil :owner nil}}
          result (debug/format-cell [0 0] cell)]
      (should-contain "contents:" result)))

  (it "formats cell with valid contents"
    (let [cell {:type :sea :contents {:type :destroyer :owner :computer :hits 3}}
          result (debug/format-cell [0 0] cell)]
      (should-contain "type:destroyer" result)
      (should-contain "owner:computer" result))))

(describe "format-cell"
  (it "formats nil cell"
    (should= "[3,5] nil" (debug/format-cell [3 5] nil)))

  (it "formats land cell"
    (should-contain ":land" (debug/format-cell [0 0] {:type :land})))

  (it "formats city cell with status"
    (let [result (debug/format-cell [1 2] {:type :city :city-status :player})]
      (should-contain ":city" result)
      (should-contain "city-status:player" result)))

  (it "formats cell with unit contents including optional fields"
    (let [cell {:type :sea :contents {:type :fighter :owner :player
                                      :mode :sentry :hits 1 :fuel 20}}
          result (debug/format-cell [0 0] cell)]
      (should-contain "type:fighter" result)
      (should-contain "owner:player" result)
      (should-contain "mode::sentry" result)
      (should-contain "fuel:20" result)))

  (it "includes coordinate prefix"
    (let [result (debug/format-cell [7 12] {:type :sea})]
      (should-contain "[7,12]" result))))

(describe "log-player-movement!"
  (before (reset-all-atoms!))

  (it "appends movement entry to log"
    (debug/log-player-movement! :army [1 2] [1 3] :moving :move nil)
    (should= 1 (count @atoms/player-movement-log))
    (let [entry (first @atoms/player-movement-log)]
      (should= :army (:unit-type entry))
      (should= [1 2] (:from entry))
      (should= [1 3] (:to entry))
      (should= :move (:event entry))))

  (it "includes round number"
    (reset! atoms/round-number 5)
    (debug/log-player-movement! :army [0 0] [0 1] :explore :move nil)
    (should= 5 (:round (first @atoms/player-movement-log))))

  (it "keeps exactly 500 entries without truncating"
    (dotimes [_ 500]
      (debug/log-player-movement! :army [0 0] [0 1] :moving :move nil))
    (should= 500 (count @atoms/player-movement-log)))

  (it "truncates to 500 when exceeding limit"
    (dotimes [_ 501]
      (debug/log-player-movement! :army [0 0] [0 1] :moving :move nil))
    (should= 500 (count @atoms/player-movement-log)))

  (it "includes wake reason"
    (debug/log-player-movement! :army [0 0] [0 1] :explore :wake :steps-exhausted)
    (should= :steps-exhausted (:reason (first @atoms/player-movement-log)))))

(describe "log-action!"
  (before (reset-all-atoms!))

  (it "appends action to log"
    (debug/log-action! [:move :army [4 6] [4 7]])
    (should= 1 (count @atoms/action-log))
    (should= [:move :army [4 6] [4 7]] (:action (first @atoms/action-log))))

  (it "includes timestamp"
    (debug/log-action! [:test])
    (should (number? (:timestamp (first @atoms/action-log)))))

  (it "caps log at 100 entries"
    (dotimes [i 110]
      (debug/log-action! [:action i]))
    (should= 100 (count @atoms/action-log))))

(describe "dump-region"
  (before (reset-all-atoms!))

  (it "extracts cells from coordinate range"
    (reset! atoms/game-map (build-test-map ["###"
                                             "###"
                                             "###"]))
    (reset! atoms/player-map (build-test-map ["###"
                                               "###"
                                               "###"]))
    (reset! atoms/computer-map (build-test-map ["###"
                                                 "###"
                                                 "###"]))
    (let [result (debug/dump-region [0 0] [1 1])]
      (should (map? (:game-map result)))
      (should (map? (:player-map result)))
      (should (map? (:computer-map result)))
      ;; Should have 4 cells in range [0 0] to [1 1]
      (should= 4 (count (:game-map result)))))

  (it "handles empty maps gracefully"
    (reset! atoms/game-map [[nil nil] [nil nil]])
    (reset! atoms/player-map [[nil nil] [nil nil]])
    (reset! atoms/computer-map [[nil nil] [nil nil]])
    (let [result (debug/dump-region [0 0] [1 1])]
      (should= 0 (count (:game-map result))))))

(describe "screen-coords-to-cell-range"
  (before (reset-all-atoms!))

  (it "converts screen coords to cell range"
    (reset! atoms/map-screen-dimensions [100 100])
    (reset! atoms/game-map (build-test-map ["#####"
                                             "#####"
                                             "#####"
                                             "#####"
                                             "#####"]))
    (let [[[sr sc] [er ec]] (debug/screen-coords-to-cell-range [0 0] [99 99])]
      (should= 0 sr)
      (should= 0 sc)
      (should= 4 er)
      (should= 4 ec)))

  (it "normalizes reversed coordinates"
    (reset! atoms/map-screen-dimensions [100 100])
    (reset! atoms/game-map (build-test-map ["#####"
                                             "#####"
                                             "#####"
                                             "#####"
                                             "#####"]))
    (let [[[sr sc] [er ec]] (debug/screen-coords-to-cell-range [99 99] [0 0])]
      (should= 0 sr)
      (should= 0 sc)
      (should= 4 er)
      (should= 4 ec)))

  (it "clamps to map bounds"
    (reset! atoms/map-screen-dimensions [100 100])
    (reset! atoms/game-map (build-test-map ["###"
                                             "###"
                                             "###"]))
    (let [[[sr sc] [er ec]] (debug/screen-coords-to-cell-range [0 0] [200 200])]
      (should (>= sr 0))
      (should (>= sc 0))
      (should (<= er 2))
      (should (<= ec 2)))))

(describe "generate-dump-filename"
  (it "generates filename with debug prefix"
    (let [filename (debug/generate-dump-filename)]
      (should (str/starts-with? filename "debug-"))
      (should (str/ends-with? filename ".txt"))))

  (it "contains date pattern"
    (let [filename (debug/generate-dump-filename)]
      ;; Should match debug-YYYY-MM-DD-HHMMSS.txt
      (should (re-find #"debug-\d{4}-\d{2}-\d{2}-\d{6}\.txt" filename)))))
