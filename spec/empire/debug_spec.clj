(ns empire.debug-spec
  (:require [speclj.core :refer :all]
            [empire.debug :as debug]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [reset-all-atoms! build-test-map]]))

(describe "log-action!"
  (before (reset-all-atoms!))

  (it "appends action to empty log"
    (debug/log-action! [:move :army [4 6] [4 7]])
    (should= 1 (count @atoms/action-log))
    (should= [:move :army [4 6] [4 7]] (:action (first @atoms/action-log)))
    (should (number? (:timestamp (first @atoms/action-log)))))

  (it "appends multiple actions"
    (debug/log-action! [:move :army [0 0] [0 1]])
    (debug/log-action! [:attack :fighter [1 1] [2 2]])
    (should= 2 (count @atoms/action-log))
    (should= [:move :army [0 0] [0 1]] (:action (first @atoms/action-log)))
    (should= [:attack :fighter [1 1] [2 2]] (:action (second @atoms/action-log))))

  (it "caps log at 100 entries by dropping oldest"
    (doseq [i (range 105)]
      (debug/log-action! [:test i]))
    (should= 100 (count @atoms/action-log))
    ;; Oldest should be action 5 (0-4 were dropped)
    (should= [:test 5] (:action (first @atoms/action-log)))
    ;; Newest should be action 104
    (should= [:test 104] (:action (last @atoms/action-log)))))

(describe "dump-region"
  (before
    (reset-all-atoms!)
    (reset! atoms/game-map
            (build-test-map ["###"
                             "#A#"
                             "~~~"]))
    ;; player-map and computer-map are 2D vectors like game-map
    (reset! atoms/player-map
            (build-test-map ["## "
                             "#A "
                             "   "]))
    (reset! atoms/computer-map
            (build-test-map ["   "
                             "   "
                             "~~ "])))

  (it "extracts cells from all three maps for coordinate range"
    (let [result (debug/dump-region [0 0] [1 1])]
      (should (contains? result :game-map))
      (should (contains? result :player-map))
      (should (contains? result :computer-map))))

  (it "returns correct game-map cells"
    (let [result (debug/dump-region [0 0] [1 1])
          gm (:game-map result)]
      (should= :land (:type (get gm [0 0])))
      (should= :land (:type (get gm [0 1])))
      (should= :land (:type (get gm [1 0])))
      (should= :army (get-in gm [[1 1] :contents :type]))))

  (it "returns correct player-map cells"
    (let [result (debug/dump-region [0 0] [1 1])
          pm (:player-map result)]
      (should= {:type :land} (get pm [0 0]))
      (should= :army (get-in pm [[1 1] :contents :type]))))

  (it "returns correct computer-map cells"
    (let [result (debug/dump-region [2 0] [2 1])
          cm (:computer-map result)]
      (should= {:type :sea} (get cm [2 0]))
      (should= {:type :sea} (get cm [2 1]))))

  (it "handles single cell region"
    (let [result (debug/dump-region [1 1] [1 1])
          gm (:game-map result)]
      (should= 1 (count gm))
      (should= :army (get-in gm [[1 1] :contents :type])))))

(describe "format-cell"
  (it "formats land cell"
    (let [output (debug/format-cell [0 0] {:type :land})]
      (should-contain "[0,0]" output)
      (should-contain ":land" output)))

  (it "formats sea cell"
    (let [output (debug/format-cell [2 3] {:type :sea})]
      (should-contain "[2,3]" output)
      (should-contain ":sea" output)))

  (it "formats city cell with status"
    (let [output (debug/format-cell [1 1] {:type :city :city-status :player})]
      (should-contain "[1,1]" output)
      (should-contain ":city" output)
      (should-contain ":player" output)))

  (it "formats cell with unit contents"
    (let [output (debug/format-cell [4 6]
                                    {:type :land
                                     :contents {:type :army
                                                :owner :player
                                                :mode :awake
                                                :hits 1}})]
      (should-contain "[4,6]" output)
      (should-contain ":army" output)
      (should-contain ":player" output)
      (should-contain ":awake" output)))

  (it "formats nil cell"
    (let [output (debug/format-cell [0 0] nil)]
      (should-contain "[0,0]" output)
      (should-contain "nil" output))))

(describe "format-dump"
  (before
    (reset-all-atoms!)
    (reset! atoms/game-map
            (build-test-map ["##"
                             "~~"]))
    ;; player-map and computer-map are 2D vectors like game-map
    (reset! atoms/player-map
            (build-test-map ["# "
                             "  "]))
    (reset! atoms/computer-map
            (build-test-map ["  "
                             "  "]))
    (reset! atoms/round-number 42)
    (reset! atoms/cells-needing-attention [[1 0] [1 1]])
    (reset! atoms/player-items [[0 0]])
    (reset! atoms/waiting-for-input true)
    (reset! atoms/destination [5 10])
    (debug/log-action! [:move :army [0 0] [0 1]])
    (debug/log-action! [:capture :city [1 1]]))

  (it "includes header with round number"
    (let [output (debug/format-dump [0 0] [1 1])]
      (should-contain "Round: 42" output)))

  (it "includes selection coordinates"
    (let [output (debug/format-dump [0 0] [1 1])]
      (should-contain "[0,0]" output)
      (should-contain "[1,1]" output)))

  (it "includes global state"
    (let [output (debug/format-dump [0 0] [1 1])]
      (should-contain "cells-needing-attention" output)
      (should-contain "player-items" output)
      (should-contain "waiting-for-input" output)
      (should-contain "destination" output)))

  (it "includes recent actions"
    (let [output (debug/format-dump [0 0] [1 1])]
      (should-contain "Recent Actions" output)
      (should-contain ":move" output)
      (should-contain ":capture" output)))

  (it "includes cells from all three maps"
    (let [output (debug/format-dump [0 0] [1 1])]
      (should-contain "game-map" output)
      (should-contain "player-map" output)
      (should-contain "computer-map" output))))

(describe "format-dump action limiting"
  (before
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["#"]))
    (reset! atoms/player-map (build-test-map [" "]))
    (reset! atoms/computer-map (build-test-map [" "]))
    ;; Add 30 actions
    (doseq [i (range 30)]
      (debug/log-action! [:action i])))

  (it "limits to last 20 actions in dump"
    (let [output (debug/format-dump [0 0] [0 0])]
      ;; Should contain actions 10-29 (last 20)
      (should-contain "[:action 29]" output)
      (should-contain "[:action 10]" output)
      ;; Should not contain earlier actions
      (should-not-contain "[:action 9]" output)
      (should-not-contain "[:action 0]" output))))

(describe "write-dump!"
  (before (reset-all-atoms!))

  (it "generates correct filename format"
    (let [filename (debug/generate-dump-filename)]
      ;; Should match pattern debug-YYYY-MM-DD-HHMMSS.txt
      (should (re-matches #"debug-\d{4}-\d{2}-\d{2}-\d{6}\.txt" filename))))

  (it "writes dump file and returns filename"
    (reset! atoms/game-map (build-test-map ["##" "~~"]))
    (reset! atoms/player-map (build-test-map ["##"
                                               "~~"]))
    (reset! atoms/computer-map (build-test-map ["  "
                                                 "  "]))
    (reset! atoms/round-number 99)
    (let [filename (debug/write-dump! [0 0] [1 1])]
      (try
        (should (re-matches #"debug-\d{4}-\d{2}-\d{2}-\d{6}\.txt" filename))
        (should (.exists (java.io.File. filename)))
        (let [content (slurp filename)]
          (should-contain "Round: 99" content)
          (should-contain "game-map" content))
        (finally
          (.delete (java.io.File. filename)))))))

(describe "screen-coords-to-cell-range"
  (before
    (reset-all-atoms!)
    (reset! atoms/map-screen-dimensions [400 300])
    (reset! atoms/game-map (build-test-map ["####"  ; 4 cols
                                            "####"  ; 3 rows
                                            "####"])))

  (it "converts screen coordinates to cell coordinates"
    ;; With 400x300 pixels for 3 rows x 4 cols:
    ;; cell width = 400/3 = 133.33, cell height = 300/4 = 75
    ;; (based on the swapped behavior in coordinates.cljc)
    (let [[start end] (debug/screen-coords-to-cell-range [0 0] [200 150])]
      (should (vector? start))
      (should (vector? end))))

  (it "normalizes coordinates so start is top-left"
    ;; Even if end coords are less than start, should normalize
    (let [[start end] (debug/screen-coords-to-cell-range [200 150] [0 0])]
      ;; start should be smaller coords
      (should (<= (first start) (first end)))
      (should (<= (second start) (second end)))))

  (it "clamps coordinates to map bounds"
    ;; Coordinates outside map should be clamped
    (let [[start end] (debug/screen-coords-to-cell-range [-100 -100] [1000 1000])]
      (should (>= (first start) 0))
      (should (>= (second start) 0))
      ;; end should be within map bounds (3 rows, 4 cols => max [2,3])
      (should (<= (first end) 2))
      (should (<= (second end) 3))))

  (it "returns correct cell coordinates for known screen positions"
    ;; Map is 3 rows x 4 cols, screen is 400x300 pixels
    ;; Legacy formula uses: cell-w = 400/3 = 133.33, cell-h = 300/4 = 75
    ;; For first=1 (row): x in [133.33, 266.67)
    ;; For second=2 (col): y in [150, 225)
    ;; So pixel (150, 160) should give [row=1, col=2]
    (let [[[start-row start-col] [end-row end-col]]
          (debug/screen-coords-to-cell-range [150 160] [200 200])]
      ;; The selection should include row 1, col 2
      (should= 1 start-row)
      (should= 2 start-col)
      (should= 1 end-row)
      (should= 2 end-col))))
