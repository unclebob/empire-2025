(ns empire.ui.util.rendering.display-units-spec
  (:require [empire.config.core :as config]
            [empire.test.utils :as test-utils]
            [empire.ui.util.rendering.display :as display]
            [speclj.core :refer :all]))

(describe "determine-display-unit"
  (it "returns contents for normal cell with unit"
    (let [cell {:contents {:type :army :mode :awake}}]
      (should= {:type :army :mode :awake}
               (display/determine-display-unit 5 5 cell nil false))))

  (it "returns nil for empty cell"
    (let [cell {:type :land}]
      (should-not (display/determine-display-unit 5 5 cell nil false))))

  (it "returns blinking fighter for attention cell with awake airport when blink is on"
    (let [cell {:type :city :awake-fighters 1 :fighter-count 1}]
      (should= {:type :fighter :mode :awake}
               (display/determine-display-unit 0 0 cell [[0 0]] true))))

  (it "keeps attention fighter visible when blink is off"
    (let [cell {:type :city :awake-fighters 1 :fighter-count 1}]
      (should= {:type :fighter :mode :awake}
               (display/determine-display-unit 0 0 cell [[0 0]] false))))

  (it "returns blinking fighter for attention cell with carrier with awake fighters"
    (let [cell {:contents {:type :carrier :awake-fighters 1}}]
      (should= {:type :fighter :mode :awake}
               (display/determine-display-unit 0 0 cell [[0 0]] true))))

  (it "returns blinking army for attention cell with transport with awake armies"
    (let [cell {:contents {:type :transport :awake-armies 1}}]
      (should= {:type :army :mode :awake}
               (display/determine-display-unit 0 0 cell [[0 0]] true))))

  (it "returns normal display for non-attention cell with airport"
    (let [cell {:type :city :awake-fighters 1 :fighter-count 1}]
      (should= {:type :fighter :mode :awake}
               (display/determine-display-unit 5 5 cell [[0 0]] true))))

  (it "returns sentry fighter for city with sleeping fighters only"
    (let [cell {:type :city :fighter-count 2 :awake-fighters 0}]
      (should= {:type :fighter :mode :sentry}
               (display/determine-display-unit 5 5 cell nil false))))

  (it "preserves mission field for army with loading mission"
    (let [cell {:contents {:type :army :mode :sentry :mission :loading :owner :computer}}]
      (should= {:type :army :mode :sentry :mission :loading :owner :computer}
               (display/determine-display-unit 5 5 cell nil false))))

  (it "returns nil for cell with contents lacking :type"
    (let [cell {:type :land :contents {:fuel 31}}]
      (should-not (display/determine-display-unit 5 5 cell nil false))))

  (it "returns nil for cell with contents lacking :type even with :awake mode"
    (let [cell {:type :land :contents {:mode :awake :fuel 31}}]
      (should-not (display/determine-display-unit 5 5 cell nil false)))))

(describe "production-indicator-data"
  (it "returns nil for non-city cell"
    (should-not (display/production-indicator-data 0 0 {:type :land} {})))

  (it "returns nil for city with no production"
    (should-not (display/production-indicator-data 0 0 {:type :city :city-status :player} {})))

  (it "returns nil for computer city production on player-map"
    (should-not (display/production-indicator-data 0 0 {:type :city :city-status :computer}
                                                   {[0 0] {:item :army :remaining-rounds 3}})))

  (it "returns data for computer city production on computer-map"
    (let [cell {:type :city :city-status :computer}
          production {[0 0] {:item :army :remaining-rounds 3}}
          result (display/production-indicator-data 0 0 cell production :computer-map)]
      (should-not-be-nil result)
      (should= "A" (:prod-char result))
      (should= 3 (:remaining result))))

  (it "returns nil for city with non-map production"
    (should-not (display/production-indicator-data 0 0 {:type :city :city-status :player} {[0 0] :none})))

  (it "returns nil for city with production missing :item"
    (should-not (display/production-indicator-data 0 0 {:type :city :city-status :player}
                                                   {[0 0] {:remaining-rounds 5}})))

  (it "returns data for city with valid production"
    (let [cell {:type :city :city-status :player}
          production {[3 2] {:item :army :remaining-rounds 3}}
          result (display/production-indicator-data 2 3 cell production)]
      (should-not-be-nil result)
      (should= "A" (:prod-char result))
      (should= 3 (:remaining result))))

  (it "computes progress correctly"
    (let [cell {:type :city :city-status :player}
          production {[0 0] {:item :army :remaining-rounds 3}}
          result (display/production-indicator-data 0 0 cell production)]
      (should= 0.4 (:progress result))))

  (it "computes dark color as half of base color"
    (let [cell {:type :city :city-status :player}
          production {[0 0] {:item :army :remaining-rounds 3}}
          result (display/production-indicator-data 0 0 cell production)]
      (should (every? #(<= % 128) (:dark-color result))))))

(describe "group-cells-by-color"
  (before (test-utils/reset-all-atoms!))

  (it "groups cells by their base color"
    (let [the-map [[{:type :land} {:type :sea}]
                   [{:type :land} {:type :sea}]]
          result (display/group-cells-by-color the-map nil {} false false)]
      (should= 2 (count result))
      (should= 2 (count (get result [139 69 19])))
      (should= 2 (count (get result [0 191 255])))))

  (it "skips unexplored cells"
    (let [the-map [[{:type :land} {:type :unexplored}]]
          result (display/group-cells-by-color the-map nil {} false false)]
      (should= 1 (count result))
      (should= 1 (count (get result [139 69 19])))))

  (it "uses default color for nil cells instead of crashing"
    (let [the-map [[nil]]
          result (display/group-cells-by-color the-map nil {} false false)]
      (should= 1 (count (get result [0 0 0])))))

  (it "uses default color for unknown cell type instead of crashing"
    (let [the-map [[{:type :mystery-cell}]]
          result (display/group-cells-by-color the-map nil {} false false)]
      (should= 1 (count (get result [0 0 0])))))

  (it "flashes attention cell black when blink-attention is true"
    (let [the-map [[{:type :land}]]
          result (display/group-cells-by-color the-map [[0 0]] {} true false)]
      (should= 1 (count (get result [255 255 255])))))

  (it "shows normal color for attention cell when blink-attention is false"
    (let [the-map [[{:type :land}]]
          result (display/group-cells-by-color the-map [[0 0]] {} false false)]
      (should= 1 (count (get result [139 69 19])))))

  (it "flashes completed city white when blink-completed is true"
    (let [the-map [[{:type :city :city-status :player}]]
          production {[0 0] {:item :army :remaining-rounds 0}}
          result (display/group-cells-by-color the-map nil production false true)]
      (should= 1 (count (get result [255 255 255])))))

  (it "shows normal color for completed city when blink-completed is false"
    (let [the-map [[{:type :city :city-status :player}]]
          production {[0 0] {:item :army :remaining-rounds 0}}
          result (display/group-cells-by-color the-map nil production false false)]
      (should= 1 (count (get result [0 255 0])))))

  (it "does not flash completed computer city white"
    (let [the-map [[{:type :city :city-status :computer}]]
          production {[0 0] {:item :army :remaining-rounds 0}}
          result (display/group-cells-by-color the-map nil production false true)]
      (should-not (get result [255 255 255]))
      (should= 1 (count (get result [255 0 0])))))

  (it "attention blink takes priority over completed blink"
    (let [the-map [[{:type :city :city-status :player}]]
          production {[0 0] {:item :army :remaining-rounds 0}}
          result (display/group-cells-by-color the-map [[0 0]] production true true)]
      (should= 1 (count (get result [255 255 255])))))

  (it "renders lake cells as deeper blue on computer-map display"
    (let [the-map [[{:type :sea} {:type :sea}]
                   [{:type :sea} {:type :land}]]]
      (test-utils/set-test-state! :lake-max-cells 3)
      (let [result (display/group-cells-by-color the-map nil {} false false :computer-map)]
        (should= 3 (count (get result [0 120 220])))))))

(describe "attention-unit-color"
  (it "uses black for an attention unit during the flash phase"
    (should= [0 0 0]
             (display/attention-unit-color {:type :army :owner :player} 0 0 [[0 0]] true)))

  (it "uses white for an attention unit during the normal cell phase"
    (should= [255 255 255]
             (display/attention-unit-color {:type :army :owner :player} 0 0 [[0 0]] false)))

  (it "uses the normal unit color for non-attention units"
    (should= (config/unit->color {:type :army :owner :player})
             (display/attention-unit-color {:type :army :owner :player} 1 0 [[0 0]] true))))
