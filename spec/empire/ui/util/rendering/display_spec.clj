(ns empire.ui.util.rendering.display-spec
  (:require [speclj.core :refer :all]
            [empire.ui.util.rendering.display :as display]))

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

  (it "returns nil for attention cell with awake airport when blink is off"
    (let [cell {:type :city :awake-fighters 1 :fighter-count 1}]
      (should-not (display/determine-display-unit 0 0 cell [[0 0]] false))))

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
      ;; army cost is 5, remaining 3, progress = (5-3)/5 = 0.4
      (should= 0.4 (:progress result))))

  (it "computes dark color as half of base color"
    (let [cell {:type :city :city-status :player}
          production {[0 0] {:item :army :remaining-rounds 3}}
          result (display/production-indicator-data 0 0 cell production)]
      (should (every? #(<= % 128) (:dark-color result))))))

(describe "group-cells-by-color"
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
      (should= 1 (count (get result [0 0 0])))))

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

  (it "attention blink takes priority over completed blink"
    (let [the-map [[{:type :city :city-status :player}]]
          production {[0 0] {:item :army :remaining-rounds 0}}
          result (display/group-cells-by-color the-map [[0 0]] production true true)]
      (should= 1 (count (get result [0 0 0]))))))

(describe "compute-hover-message"
  (it "returns formatted status for cell with unit"
    (let [the-map [[{:type :land :contents {:type :army :hits 1 :mode :awake :owner :player}}]]
          production {}]
      (should= "[0,0] player army [1/1] awake"
               (display/compute-hover-message the-map production [0 0]))))

  (it "returns empty string for empty land cell"
    (let [the-map [[{:type :land}]]
          production {}]
      (should= "" (display/compute-hover-message the-map production [0 0]))))

  (it "returns city status with production"
    (let [the-map [[{:type :city :city-status :player :fighter-count 0}]]
          production {[0 0] {:item :army :remaining-rounds 3}}]
      (should= "[0,0] city:player producing:army"
               (display/compute-hover-message the-map production [0 0]))))

  (it "looks up correct cell in multi-cell map"
    (let [the-map [[{:type :land} {:type :sea}]
                   [{:type :city :city-status :free :fighter-count 0} {:type :land}]]
          production {}]
      (should= "[1,0] city:free"
               (display/compute-hover-message the-map production [1 0])))))

(describe "resolve-turn-text"
  (it "returns turn-message when present"
    (should= "Hello" (display/resolve-turn-text "Hello" nil)))

  (it "returns formatted destination when no turn-message"
    (should= "Dest: 5,10" (display/resolve-turn-text nil [5 10])))

  (it "prefers turn-message over destination"
    (should= "Msg" (display/resolve-turn-text "Msg" [5 10])))

  (it "returns nil when neither turn-message nor destination"
    (should-be-nil (display/resolve-turn-text nil nil)))

  (it "returns nil for empty string turn-message and no destination"
    (should-be-nil (display/resolve-turn-text "" nil)))

  (it "returns formatted destination for empty string turn-message"
    (should= "Dest: 3,7" (display/resolve-turn-text "" [3 7]))))

(describe "resolve-round-status-text"
  (it "returns round string when not paused"
    (let [result (display/resolve-round-status-text 5 false false)]
      (should= "Round: 5" (:text result))
      (should-not (:paused? result))))

  (it "returns paused prefix when paused"
    (let [result (display/resolve-round-status-text 3 true false)]
      (should= "PAUSED  Round: 3" (:text result))
      (should (:paused? result))
      (should= "Round: 3" (:round-str result))))

  (it "returns paused prefix when pause-requested"
    (let [result (display/resolve-round-status-text 1 false true)]
      (should= "PAUSED  Round: 1" (:text result))
      (should (:paused? result))))

  (it "returns paused prefix when both paused and requested"
    (let [result (display/resolve-round-status-text 10 true true)]
      (should= "PAUSED  Round: 10" (:text result))
      (should (:paused? result))))

  (it "returns exact non-paused shape when both paused flags are false"
    (should= {:text "Round: 9" :paused? false}
             (display/resolve-round-status-text 9 false false))))

(describe "should-show-error?"
  (it "returns true when error-until is in the future"
    (should (display/should-show-error? (+ (System/currentTimeMillis) 10000))))

  (it "returns false when error-until is in the past"
    (should-not (display/should-show-error? (- (System/currentTimeMillis) 10000))))

  (it "returns false when error-until equals current time"
    (let [t (System/currentTimeMillis)]
      (should-not (display/should-show-error? t)))))

(describe "compute-hover-result"
  (it "returns hover message using player-map"
    (let [player-map [[{:type :land :contents {:type :army :hits 1 :mode :awake :owner :player}}]]
          computer-map [[{:type :sea}]]
          game-map [[{:type :land}]]]
      (should= "[0,0] player army [1/1] awake"
               (display/compute-hover-result :player-map player-map computer-map game-map {} [0 0]))))

  (it "returns hover message using computer-map"
    (let [player-map [[{:type :land}]]
          computer-map [[{:type :city :city-status :computer :fighter-count 0}]]
          game-map [[{:type :land}]]]
      (should= "[0,0] city:computer"
               (display/compute-hover-result :computer-map player-map computer-map game-map {} [0 0]))))

  (it "returns empty string for empty cell"
    (let [player-map [[{:type :land}]]
          computer-map [[{:type :sea}]]
          game-map [[{:type :land}]]]
      (should= ""
               (display/compute-hover-result :player-map player-map computer-map game-map {} [0 0])))))

(describe "resolve-display-map"
  (it "returns player-map for :player-map"
    (should= :p (display/resolve-display-map :player-map :p :c :g)))

  (it "returns computer-map for :computer-map"
    (should= :c (display/resolve-display-map :computer-map :p :c :g)))

  (it "returns game-map for :actual-map"
    (should= :g (display/resolve-display-map :actual-map :p :c :g))))
