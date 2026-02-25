(ns empire.ui.rendering-util-spec
  (:require [speclj.core :refer :all]
            [empire.ui.rendering-util :as ru]))

(describe "format-unit-status"
  (it "formats basic player army status"
    (let [unit {:type :army :hits 1 :mode :awake :owner :player}]
      (should= "player army [1/1] awake" (ru/format-unit-status unit))))

  (it "formats computer army status"
    (let [unit {:type :army :hits 1 :mode :sentry :owner :computer}]
      (should= "computer army [1/1] sentry" (ru/format-unit-status unit))))

  (it "formats fighter with fuel"
    (let [unit {:type :fighter :hits 1 :mode :sentry :fuel 15 :owner :player}]
      (should= "player fighter [1/1] fuel:15 sentry" (ru/format-unit-status unit))))

  (it "formats transport with cargo"
    (let [unit {:type :transport :hits 1 :mode :awake :army-count 4 :owner :player}]
      (should= "player transport [1/1] cargo:4 awake" (ru/format-unit-status unit))))

  (it "formats transport with mission"
    (let [unit {:type :transport :hits 1 :mode :awake :army-count 2 :owner :computer
                :transport-mission :loading}]
      (should= "computer transport [1/1] cargo:2 loading awake" (ru/format-unit-status unit))))

  (it "formats transport with en-route mission"
    (let [unit {:type :transport :hits 1 :mode :awake :army-count 6 :owner :computer
                :transport-mission :en-route}]
      (should= "computer transport [1/1] cargo:6 en-route awake" (ru/format-unit-status unit))))

  (it "formats carrier with cargo"
    (let [unit {:type :carrier :hits 8 :mode :moving :fighter-count 3 :owner :player}]
      (should= "player carrier [8/8] cargo:3 moving" (ru/format-unit-status unit))))

  (it "formats unit with marching orders"
    (let [unit {:type :army :hits 1 :mode :moving :marching-orders [[1 2] [3 4]] :owner :player}]
      (should= "player army [1/1] march moving" (ru/format-unit-status unit))))

  (it "formats unit with flight path"
    (let [unit {:type :fighter :hits 1 :mode :moving :fuel 10 :flight-path [[1 2]] :owner :player}]
      (should= "player fighter [1/1] fuel:10 flight moving" (ru/format-unit-status unit))))

  (it "formats army with mission"
    (let [unit {:type :army :hits 1 :mode :sentry :owner :computer :mission :loading}]
      (should= "computer army [1/1] mission:loading sentry" (ru/format-unit-status unit))))

  (it "formats transport with loading-timeout"
    (let [unit {:type :transport :hits 1 :mode :sentry :army-count 2 :owner :computer
                :transport-mission :loading :loading-timeout 3}]
      (should= "computer transport [1/1] cargo:2 loading timeout:3 sentry" (ru/format-unit-status unit)))))

(describe "format-city-status"
  (it "formats player city with production"
    (let [cell {:type :city :city-status :player :fighter-count 0}
          production {:item :army :remaining-rounds 5}]
      (should= "city:player producing:army" (ru/format-city-status cell production))))

  (it "formats player city with no production"
    (let [cell {:type :city :city-status :player :fighter-count 0}]
      (should= "city:player" (ru/format-city-status cell nil))))

  (it "formats city with fighters"
    (let [cell {:type :city :city-status :player :fighter-count 3}]
      (should= "city:player fighters:3" (ru/format-city-status cell nil))))

  (it "formats city with marching orders"
    (let [cell {:type :city :city-status :player :fighter-count 0 :marching-orders [[1 2]]}]
      (should= "city:player march" (ru/format-city-status cell nil))))

  (it "formats computer city"
    (let [cell {:type :city :city-status :computer :fighter-count 2}]
      (should= "city:computer fighters:2" (ru/format-city-status cell nil))))

  (it "formats city with one ship in shipyard"
    (let [cell {:type :city :city-status :player
                :shipyard [{:type :destroyer :hits 2}]}]
      (should= "city:player dock:D[2/3]" (ru/format-city-status cell nil))))

  (it "formats city with multiple ships in shipyard"
    (let [cell {:type :city :city-status :player
                :shipyard [{:type :destroyer :hits 2}
                           {:type :battleship :hits 7}]}]
      (should= "city:player dock:D[2/3],B[7/10]" (ru/format-city-status cell nil))))

  (it "formats city with shipyard and other info"
    (let [cell {:type :city :city-status :player :fighter-count 2
                :shipyard [{:type :submarine :hits 1}]}
          production {:item :army :remaining-rounds 3}]
      (should= "city:player producing:army fighters:2 dock:S[1/2]"
               (ru/format-city-status cell production))))

  (it "formats city with transport in shipyard"
    (let [cell {:type :city :city-status :player
                :shipyard [{:type :transport :hits 1}]}]
      (should= "city:player dock:T[1/1]" (ru/format-city-status cell nil))))

  (it "formats city with carrier in shipyard"
    (let [cell {:type :city :city-status :player
                :shipyard [{:type :carrier :hits 5}]}]
      (should= "city:player dock:C[5/8]" (ru/format-city-status cell nil))))

  (it "formats city with patrol-boat in shipyard"
    (let [cell {:type :city :city-status :player
                :shipyard [{:type :patrol-boat :hits 1}]}]
      (should= "city:player dock:P[1/1]" (ru/format-city-status cell nil))))

  (it "formats city with empty shipyard"
    (let [cell {:type :city :city-status :player :shipyard []}]
      (should= "city:player" (ru/format-city-status cell nil))))

  (it "formats city with lookaround marching orders"
    (let [cell {:type :city :city-status :player :fighter-count 0
                :marching-orders :lookaround}]
      (should= "city:player lookaround" (ru/format-city-status cell nil))))

  (it "formats city with flight path"
    (let [cell {:type :city :city-status :player :fighter-count 0
                :flight-path [5 10]}]
      (should= "city:player flight" (ru/format-city-status cell nil))))

  (it "formats free city"
    (let [cell {:type :city :city-status :free :fighter-count 0}]
      (should= "city:free" (ru/format-city-status cell nil))))

  (it "formats city with production none"
    (let [cell {:type :city :city-status :player :fighter-count 0}]
      (should= "city:player producing:none" (ru/format-city-status cell :none)))))

(describe "format-hover-status"
  (it "returns unit status with coordinates"
    (let [cell {:contents {:type :army :hits 1 :mode :awake :owner :player}}]
      (should= "[5,10] player army [1/1] awake" (ru/format-hover-status [5 10] cell nil))))

  (it "returns city status with coordinates"
    (let [cell {:type :city :city-status :free :fighter-count 0}]
      (should= "[3,7] city:free" (ru/format-hover-status [3 7] cell nil))))

  (it "returns nil for empty non-city cell"
    (let [cell {:type :land}]
      (should-not (ru/format-hover-status [0 0] cell nil)))))

(describe "determine-display-unit"
  (it "returns contents for normal cell with unit"
    (let [cell {:contents {:type :army :mode :awake}}]
      (should= {:type :army :mode :awake}
               (ru/determine-display-unit 5 5 cell nil false))))

  (it "returns nil for empty cell"
    (let [cell {:type :land}]
      (should-not (ru/determine-display-unit 5 5 cell nil false))))

  (it "returns blinking fighter for attention cell with awake airport when blink is on"
    (let [cell {:type :city :awake-fighters 1 :fighter-count 1}]
      (should= {:type :fighter :mode :awake}
               (ru/determine-display-unit 0 0 cell [[0 0]] true))))

  (it "returns nil for attention cell with awake airport when blink is off"
    (let [cell {:type :city :awake-fighters 1 :fighter-count 1}]
      (should-not (ru/determine-display-unit 0 0 cell [[0 0]] false))))

  (it "returns blinking fighter for attention cell with carrier with awake fighters"
    (let [cell {:contents {:type :carrier :awake-fighters 1}}]
      (should= {:type :fighter :mode :awake}
               (ru/determine-display-unit 0 0 cell [[0 0]] true))))

  (it "returns blinking army for attention cell with transport with awake armies"
    (let [cell {:contents {:type :transport :awake-armies 1}}]
      (should= {:type :army :mode :awake}
               (ru/determine-display-unit 0 0 cell [[0 0]] true))))

  (it "returns normal display for non-attention cell with airport"
    (let [cell {:type :city :awake-fighters 1 :fighter-count 1}]
      (should= {:type :fighter :mode :awake}
               (ru/determine-display-unit 5 5 cell [[0 0]] true))))

  (it "returns sentry fighter for city with sleeping fighters only"
    (let [cell {:type :city :fighter-count 2 :awake-fighters 0}]
      (should= {:type :fighter :mode :sentry}
               (ru/determine-display-unit 5 5 cell nil false))))

  (it "preserves mission field for army with loading mission"
    (let [cell {:contents {:type :army :mode :sentry :mission :loading :owner :computer}}]
      (should= {:type :army :mode :sentry :mission :loading :owner :computer}
               (ru/determine-display-unit 5 5 cell nil false))))

  (it "returns nil for cell with contents lacking :type"
    (let [cell {:type :land :contents {:fuel 31}}]
      (should-not (ru/determine-display-unit 5 5 cell nil false))))

  (it "returns nil for cell with contents lacking :type even with :awake mode"
    (let [cell {:type :land :contents {:mode :awake :fuel 31}}]
      (should-not (ru/determine-display-unit 5 5 cell nil false)))))

(describe "production-indicator-data"
  (it "returns nil for non-city cell"
    (should-not (ru/production-indicator-data 0 0 {:type :land} {})))

  (it "returns nil for city with no production"
    (should-not (ru/production-indicator-data 0 0 {:type :city :city-status :player} {})))

  (it "returns nil for city with non-map production"
    (should-not (ru/production-indicator-data 0 0 {:type :city :city-status :player} {[0 0] :none})))

  (it "returns nil for city with production missing :item"
    (should-not (ru/production-indicator-data 0 0 {:type :city :city-status :player}
                  {[0 0] {:remaining-rounds 5}})))

  (it "returns data for city with valid production"
    (let [cell {:type :city :city-status :player}
          production {[3 2] {:item :army :remaining-rounds 3}}
          result (ru/production-indicator-data 2 3 cell production)]
      (should-not-be-nil result)
      (should= "A" (:prod-char result))
      (should= 3 (:remaining result))))

  (it "computes progress correctly"
    (let [cell {:type :city :city-status :player}
          production {[0 0] {:item :army :remaining-rounds 3}}
          result (ru/production-indicator-data 0 0 cell production)]
      ;; army cost is 5, remaining 3, progress = (5-3)/5 = 0.4
      (should= 0.4 (:progress result))))

  (it "computes dark color as half of base color"
    (let [cell {:type :city :city-status :player}
          production {[0 0] {:item :army :remaining-rounds 3}}
          result (ru/production-indicator-data 0 0 cell production)]
      (should (every? #(<= % 128) (:dark-color result))))))

(describe "group-cells-by-color"
  (it "groups cells by their base color"
    (let [the-map [[{:type :land} {:type :sea}]
                   [{:type :land} {:type :sea}]]
          result (ru/group-cells-by-color the-map nil {} false false)]
      (should= 2 (count result))
      (should= 2 (count (get result [139 69 19])))
      (should= 2 (count (get result [0 191 255])))))

  (it "skips unexplored cells"
    (let [the-map [[{:type :land} {:type :unexplored}]]
          result (ru/group-cells-by-color the-map nil {} false false)]
      (should= 1 (count result))
      (should= 1 (count (get result [139 69 19])))))

  (it "flashes attention cell black when blink-attention is true"
    (let [the-map [[{:type :land}]]
          result (ru/group-cells-by-color the-map [[0 0]] {} true false)]
      (should= 1 (count (get result [0 0 0])))))

  (it "shows normal color for attention cell when blink-attention is false"
    (let [the-map [[{:type :land}]]
          result (ru/group-cells-by-color the-map [[0 0]] {} false false)]
      (should= 1 (count (get result [139 69 19])))))

  (it "flashes completed city white when blink-completed is true"
    (let [the-map [[{:type :city :city-status :player}]]
          production {[0 0] {:item :army :remaining-rounds 0}}
          result (ru/group-cells-by-color the-map nil production false true)]
      (should= 1 (count (get result [255 255 255])))))

  (it "shows normal color for completed city when blink-completed is false"
    (let [the-map [[{:type :city :city-status :player}]]
          production {[0 0] {:item :army :remaining-rounds 0}}
          result (ru/group-cells-by-color the-map nil production false false)]
      (should= 1 (count (get result [0 255 0])))))

  (it "attention blink takes priority over completed blink"
    (let [the-map [[{:type :city :city-status :player}]]
          production {[0 0] {:item :army :remaining-rounds 0}}
          result (ru/group-cells-by-color the-map [[0 0]] production true true)]
      (should= 1 (count (get result [0 0 0]))))))

(describe "format-production-status"
  (it "returns all zeros and 0% for empty map"
    (let [game-map [[{:type :sea} {:type :land}]]
          player-map [[nil nil]]]
      (should= "A:0 F:0 T:0 D:0 S:0 P:0 C:0 B:0 Z:0 | 0%"
               (ru/format-production-status game-map player-map))))

  (it "counts player units by type"
    (let [game-map [[{:type :land :contents {:type :army :owner :player}}
                     {:type :sea :contents {:type :destroyer :owner :player}}
                     {:type :land :contents {:type :army :owner :player}}]]
          player-map [[nil nil nil]]]
      (should= "A:2 F:0 T:0 D:1 S:0 P:0 C:0 B:0 Z:0 | 0%"
               (ru/format-production-status game-map player-map))))

  (it "does not count computer units"
    (let [game-map [[{:type :land :contents {:type :army :owner :computer}}
                     {:type :land :contents {:type :army :owner :player}}]]
          player-map [[nil nil]]]
      (should= "A:1 F:0 T:0 D:0 S:0 P:0 C:0 B:0 Z:0 | 0%"
               (ru/format-production-status game-map player-map))))

  (it "computes exploration percentage"
    (let [game-map [[{:type :land} {:type :sea} {:type :land} {:type :sea}]]
          player-map [[{:type :land} nil {:type :land} nil]]]
      (should= "A:0 F:0 T:0 D:0 S:0 P:0 C:0 B:0 Z:0 | 50%"
               (ru/format-production-status game-map player-map))))

  (it "counts all unit types"
    (let [game-map [[{:type :land :contents {:type :army :owner :player}}
                     {:type :land :contents {:type :fighter :owner :player}}
                     {:type :sea :contents {:type :transport :owner :player}}
                     {:type :sea :contents {:type :destroyer :owner :player}}
                     {:type :sea :contents {:type :submarine :owner :player}}
                     {:type :sea :contents {:type :patrol-boat :owner :player}}
                     {:type :sea :contents {:type :carrier :owner :player}}
                     {:type :sea :contents {:type :battleship :owner :player}}
                     {:type :land :contents {:type :satellite :owner :player}}]]
          player-map [[nil nil nil nil nil nil nil nil nil]]]
      (should= "A:1 F:1 T:1 D:1 S:1 P:1 C:1 B:1 Z:1 | 0%"
               (ru/format-production-status game-map player-map)))))

(describe "should-show-paused?"
  (it "returns true when paused is true"
    (should (ru/should-show-paused? true false)))

  (it "returns true when pause-requested is true"
    (should (ru/should-show-paused? false true)))

  (it "returns true when both are true"
    (should (ru/should-show-paused? true true)))

  (it "returns false when both are false"
    (should-not (ru/should-show-paused? false false))))

(describe "compute-hover-message"
  (it "returns formatted status for cell with unit"
    (let [the-map [[{:type :land :contents {:type :army :hits 1 :mode :awake :owner :player}}]]
          production {}]
      (should= "[0,0] player army [1/1] awake"
               (ru/compute-hover-message the-map production [0 0]))))

  (it "returns empty string for empty land cell"
    (let [the-map [[{:type :land}]]
          production {}]
      (should= "" (ru/compute-hover-message the-map production [0 0]))))

  (it "returns city status with production"
    (let [the-map [[{:type :city :city-status :player :fighter-count 0}]]
          production {[0 0] {:item :army :remaining-rounds 3}}]
      (should= "[0,0] city:player producing:army"
               (ru/compute-hover-message the-map production [0 0]))))

  (it "looks up correct cell in multi-cell map"
    (let [the-map [[{:type :land} {:type :sea}]
                   [{:type :city :city-status :free :fighter-count 0} {:type :land}]]
          production {}]
      (should= "[1,0] city:free"
               (ru/compute-hover-message the-map production [1 0])))))

(describe "resolve-display-map"
  (it "returns player-map for :player-map"
    (should= :p (ru/resolve-display-map :player-map :p :c :g)))

  (it "returns computer-map for :computer-map"
    (should= :c (ru/resolve-display-map :computer-map :p :c :g)))

  (it "returns game-map for :actual-map"
    (should= :g (ru/resolve-display-map :actual-map :p :c :g))))
