(ns empire.ui.util.rendering.format-spec
  (:require [speclj.core :refer :all]
            [empire.ui.util.rendering.format :as fmt]))

(describe "format-unit-status"
  (it "formats basic player army status"
    (let [unit {:type :army :hits 1 :mode :awake :owner :player}]
      (should= "player army [1/1] awake" (fmt/format-unit-status unit))))

  (it "formats computer army status"
    (let [unit {:type :army :hits 1 :mode :sentry :owner :computer}]
      (should= "computer army [1/1] sentry" (fmt/format-unit-status unit))))

  (it "formats fighter with fuel"
    (let [unit {:type :fighter :hits 1 :mode :sentry :fuel 15 :owner :player}]
      (should= "player fighter [1/1] fuel:15 sentry" (fmt/format-unit-status unit))))

  (it "formats transport with cargo"
    (let [unit {:type :transport :hits 1 :mode :awake :army-count 4 :owner :player}]
      (should= "player transport [1/1] cargo:4 awake" (fmt/format-unit-status unit))))

  (it "formats transport with mission"
    (let [unit {:type :transport :hits 1 :mode :awake :army-count 2 :owner :computer
                :transport-mission :loading}]
      (should= "computer transport [1/1] cargo:2 loading awake" (fmt/format-unit-status unit))))

  (it "formats transport with en-route mission"
    (let [unit {:type :transport :hits 1 :mode :awake :army-count 6 :owner :computer
                :transport-mission :en-route}]
      (should= "computer transport [1/1] cargo:6 en-route awake" (fmt/format-unit-status unit))))

  (it "formats carrier with cargo"
    (let [unit {:type :carrier :hits 8 :mode :moving :fighter-count 3 :owner :player}]
      (should= "player carrier [8/8] cargo:3 moving" (fmt/format-unit-status unit))))

  (it "formats unit with marching orders"
    (let [unit {:type :army :hits 1 :mode :moving :marching-orders [[1 2] [3 4]] :owner :player}]
      (should= "player army [1/1] march moving" (fmt/format-unit-status unit))))

  (it "formats unit with flight path"
    (let [unit {:type :fighter :hits 1 :mode :moving :fuel 10 :flight-path [[1 2]] :owner :player}]
      (should= "player fighter [1/1] fuel:10 flight moving" (fmt/format-unit-status unit))))

  (it "formats army with mission"
    (let [unit {:type :army :hits 1 :mode :sentry :owner :computer :mission :loading}]
      (should= "computer army [1/1] mission:loading sentry" (fmt/format-unit-status unit))))

  (it "formats transport with loading-timeout"
    (let [unit {:type :transport :hits 1 :mode :sentry :army-count 2 :owner :computer
                :transport-mission :loading :loading-timeout 3}]
      (should= "computer transport [1/1] cargo:2 loading timeout:3 sentry" (fmt/format-unit-status unit))))

  (it "formats patrol-boat with patrol mode"
    (let [unit {:type :patrol-boat :hits 1 :mode :awake :owner :player :patrol-mode :coastal}]
      (should= "player patrol-boat [1/1] coastal awake" (fmt/format-unit-status unit))))

  (it "formats destroyer with no patrol mode"
    (let [unit {:type :destroyer :hits 3 :mode :sentry :owner :player}]
      (should= "player destroyer [3/3] sentry" (fmt/format-unit-status unit))))

  (it "falls back to unknown labels for malformed units"
    (let [unit {:hits nil :owner nil :type :fighter :mode nil :fuel 7}]
      (should= "unknown fighter [1/1] fuel:7 unknown"
               (fmt/format-unit-status unit)))))

(describe "format-city-status"
  (it "formats player city with production"
    (let [cell {:type :city :city-status :player :fighter-count 0}
          production {:item :army :remaining-rounds 5}]
      (should= "city:player producing:army rounds:5" (fmt/format-city-status cell production))))

  (it "formats player city with no production"
    (let [cell {:type :city :city-status :player :fighter-count 0}]
      (should= "city:player" (fmt/format-city-status cell nil))))

  (it "formats city with fighters"
    (let [cell {:type :city :city-status :player :fighter-count 3}]
      (should= "city:player fighters:3" (fmt/format-city-status cell nil))))

  (it "formats city with marching orders"
    (let [cell {:type :city :city-status :player :fighter-count 0 :marching-orders [[1 2]]}]
      (should= "city:player march" (fmt/format-city-status cell nil))))

  (it "formats computer city"
    (let [cell {:type :city :city-status :computer :fighter-count 2}]
      (should= "city:computer fighters:2" (fmt/format-city-status cell nil))))

  (it "formats city with one ship in shipyard"
    (let [cell {:type :city :city-status :player
                :shipyard [{:type :destroyer :hits 2}]}]
      (should= "city:player dock:D[2/3]" (fmt/format-city-status cell nil))))

  (it "formats city with multiple ships in shipyard"
    (let [cell {:type :city :city-status :player
                :shipyard [{:type :destroyer :hits 2}
                           {:type :battleship :hits 7}]}]
      (should= "city:player dock:D[2/3],B[7/10]" (fmt/format-city-status cell nil))))

  (it "formats city with shipyard and other info"
    (let [cell {:type :city :city-status :player :fighter-count 2
                :shipyard [{:type :submarine :hits 1}]}
          production {:item :army :remaining-rounds 3}]
      (should= "city:player producing:army rounds:3 fighters:2 dock:S[1/2]"
               (fmt/format-city-status cell production))))

  (it "formats city with transport in shipyard"
    (let [cell {:type :city :city-status :player
                :shipyard [{:type :transport :hits 1}]}]
      (should= "city:player dock:T[1/1]" (fmt/format-city-status cell nil))))

  (it "formats city with carrier in shipyard"
    (let [cell {:type :city :city-status :player
                :shipyard [{:type :carrier :hits 5}]}]
      (should= "city:player dock:C[5/8]" (fmt/format-city-status cell nil))))

  (it "formats city with patrol-boat in shipyard"
    (let [cell {:type :city :city-status :player
                :shipyard [{:type :patrol-boat :hits 1}]}]
      (should= "city:player dock:P[1/1]" (fmt/format-city-status cell nil))))

  (it "formats city with empty shipyard"
    (let [cell {:type :city :city-status :player :shipyard []}]
      (should= "city:player" (fmt/format-city-status cell nil))))

  (it "formats city with lookaround marching orders"
    (let [cell {:type :city :city-status :player :fighter-count 0
                :marching-orders :lookaround}]
      (should= "city:player lookaround" (fmt/format-city-status cell nil))))

  (it "formats city with flight path"
    (let [cell {:type :city :city-status :player :fighter-count 0
                :flight-path [5 10]}]
      (should= "city:player flight" (fmt/format-city-status cell nil))))

  (it "formats free city"
    (let [cell {:type :city :city-status :free :fighter-count 0}]
      (should= "city:free" (fmt/format-city-status cell nil))))

  (it "formats city with production none"
    (let [cell {:type :city :city-status :player :fighter-count 0}]
      (should= "city:player producing:none" (fmt/format-city-status cell :none)))))

(describe "format-waypoint-status"
  (it "formats waypoint with marching orders"
    (should= "waypoint -> 5,10"
             (fmt/format-waypoint-status {:marching-orders [5 10]})))

  (it "formats waypoint with no orders"
    (should= "waypoint (no orders)"
             (fmt/format-waypoint-status {})))

  (it "formats waypoint with nil orders"
    (should= "waypoint (no orders)"
             (fmt/format-waypoint-status {:marching-orders nil}))))

(describe "format-hover-status"
  (it "returns unit status with coordinates"
    (let [cell {:contents {:type :army :hits 1 :mode :awake :owner :player}}]
      (should= "[5,10] player army [1/1] awake" (fmt/format-hover-status [5 10] cell nil))))

  (it "returns city status with coordinates"
    (let [cell {:type :city :city-status :free :fighter-count 0}]
      (should= "[3,7] city:free" (fmt/format-hover-status [3 7] cell nil))))

  (it "prefers city status over unit status for occupied cities"
    (let [cell {:type :city
                :city-status :player
                :fighter-count 0
                :contents {:type :army :hits 1 :mode :awake :owner :player}}]
      (should= "[3,7] city:player" (fmt/format-hover-status [3 7] cell nil))))

  (it "returns land status with country-id for empty land"
    (let [cell {:type :land :country-id 7}]
      (should= "[0,0] land cid:7" (fmt/format-hover-status [0 0] cell nil))))

  (it "returns nil country-id for unclaimed land"
    (let [cell {:type :land}]
      (should= "[0,0] land cid:nil" (fmt/format-hover-status [0 0] cell nil))))

  (it "includes city country-id when present"
    (let [cell {:type :city :city-status :computer :fighter-count 0 :country-id 3}]
      (should= "[3,7] city:computer cid:3" (fmt/format-hover-status [3 7] cell nil))))

  (it "returns waypoint status with coordinates"
    (let [cell {:type :land :waypoint {:marching-orders [2 3]}}]
      (should= "[1,4] waypoint -> 2,3" (fmt/format-hover-status [1 4] cell nil))))

  (it "returns waypoint status for waypoint with no orders"
    (let [cell {:type :land :waypoint {}}]
      (should= "[0,0] waypoint (no orders)" (fmt/format-hover-status [0 0] cell nil))))

  (it "does not crash on malformed unit hover data"
    (let [cell {:contents {:type :fighter :fuel 7 :hits nil :owner nil :mode nil}}]
      (should= "[2,3] unknown fighter [1/1] fuel:7 unknown"
               (fmt/format-hover-status [2 3] cell nil)))))

(describe "split-hover-status"
  (it "splits a unit hover message into summary and detail"
    (should= {:summary "[12,7] player carrier [5/8]"
              :detail "cargo:3 sentry"}
             (fmt/split-hover-status "[12,7] player carrier [5/8] cargo:3 sentry")))

  (it "keeps city summary compact and moves the rest into detail"
    (should= {:summary "[3,7] Player City"
              :detail "prod:army rnd:3 ftrs:2 dock:S[1/2]"}
             (fmt/split-hover-status
              "[3,7] city:player producing:army rounds:3 fighters:2 dock:S[1/2]")))

  (it "shortens verbose detail tokens for unit inspector text"
    (should= {:summary "[12,7] computer transport [1/1]"
              :detail "cargo:2 loading to:3"}
             (fmt/split-hover-status
              "[12,7] computer transport [1/1] cargo:2 loading timeout:3")))

  (it "keeps waypoint headline on the summary line"
    (should= {:summary "[1,4] Waypoint"
              :detail "-> 2,3"}
             (fmt/split-hover-status "[1,4] waypoint -> 2,3")))

  (it "keeps short hover text on the summary line"
    (should= {:summary "[0,0] Free City"
              :detail nil}
             (fmt/split-hover-status "[0,0] city:free")))

  (it "splits terrain hover into terrain summary and country-id detail"
    (should= {:summary "[0,0] Land"
              :detail "cid:7"}
             (fmt/split-hover-status "[0,0] land cid:7")))

  (it "returns empty lines for blank hover text"
    (should= {:summary nil :detail nil}
             (fmt/split-hover-status ""))))

(describe "format-production-status"
  (it "returns all zeros and 0% for empty map"
    (let [game-map [[{:type :sea} {:type :land}]]
          player-map [[nil nil]]]
      (should= "A:0 F:0 T:0 D:0 S:0 P:0 C:0 B:0 Z:0 | 0%"
               (fmt/format-production-status game-map player-map))))

  (it "counts player units by type"
    (let [game-map [[{:type :land :contents {:type :army :owner :player}}
                     {:type :sea :contents {:type :destroyer :owner :player}}
                     {:type :land :contents {:type :army :owner :player}}]]
          player-map [[nil nil nil]]]
      (should= "A:2 F:0 T:0 D:1 S:0 P:0 C:0 B:0 Z:0 | 0%"
               (fmt/format-production-status game-map player-map))))

  (it "does not count computer units"
    (let [game-map [[{:type :land :contents {:type :army :owner :computer}}
                     {:type :land :contents {:type :army :owner :player}}]]
          player-map [[nil nil]]]
      (should= "A:1 F:0 T:0 D:0 S:0 P:0 C:0 B:0 Z:0 | 0%"
               (fmt/format-production-status game-map player-map))))

  (it "computes exploration percentage"
    (let [game-map [[{:type :land} {:type :sea} {:type :land} {:type :sea}]]
          player-map [[{:type :land} nil {:type :land} nil]]]
      (should= "A:0 F:0 T:0 D:0 S:0 P:0 C:0 B:0 Z:0 | 50%"
               (fmt/format-production-status game-map player-map))))

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
               (fmt/format-production-status game-map player-map))))

  (it "does not count unexplored player-map cells as explored"
    (let [game-map [[{:type :land} {:type :sea}]]
          player-map [[{:type :unexplored} {:type :sea}]]]
      (should= "A:0 F:0 T:0 D:0 S:0 P:0 C:0 B:0 Z:0 | 50%"
               (fmt/format-production-status game-map player-map))))

  (it "computes 100% exploration"
    (let [game-map [[{:type :land} {:type :sea}]]
          player-map [[{:type :land} {:type :sea}]]]
      (should= "A:0 F:0 T:0 D:0 S:0 P:0 C:0 B:0 Z:0 | 100%"
               (fmt/format-production-status game-map player-map))))

  (it "handles multi-column map"
    (let [game-map [[{:type :land :contents {:type :army :owner :player}}
                     {:type :sea}]
                    [{:type :land}
                     {:type :sea :contents {:type :destroyer :owner :player}}]]
          player-map [[{:type :land} {:type :sea}]
                      [{:type :land} nil]]]
      (should= "A:1 F:0 T:0 D:1 S:0 P:0 C:0 B:0 Z:0 | 75%"
               (fmt/format-production-status game-map player-map))))

  (it "returns 0% for zero-cell map"
    (let [game-map []
          player-map []]
      (should= "A:0 F:0 T:0 D:0 S:0 P:0 C:0 B:0 Z:0 | 0%"
               (fmt/format-production-status game-map player-map)))))

(describe "compact-production-status"
  (it "keeps up to three non-zero unit counts plus exploration"
    (should= "A2 D1 C1 | 75%"
             (fmt/compact-production-status
              "A:2 F:0 T:0 D:1 S:0 P:0 C:1 B:0 Z:0 | 75%")))

  (it "reports how many additional non-zero unit types are hidden"
    (should= "A2 F1 T1 +2 | 75%"
             (fmt/compact-production-status
              "A:2 F:1 T:1 D:1 S:0 P:0 C:1 B:0 Z:0 | 75%")))

  (it "reports zero units when nothing is built yet"
    (should= "0 units | 0%"
             (fmt/compact-production-status
              "A:0 F:0 T:0 D:0 S:0 P:0 C:0 B:0 Z:0 | 0%")))

  (it "returns nil for blank input"
    (should= nil
             (fmt/compact-production-status ""))))

(describe "hidden-production-status"
  (it "returns omitted non-zero counts beyond the first three"
    (should= "D1 C1"
             (fmt/hidden-production-status
              "A:2 F:1 T:1 D:1 S:0 P:0 C:1 B:0 Z:0 | 75%")))

  (it "returns nil when nothing is hidden"
    (should-be-nil
     (fmt/hidden-production-status
      "A:2 F:1 T:1 D:0 S:0 P:0 C:0 B:0 Z:0 | 75%"))))

(describe "should-show-paused?"
  (it "returns true when paused is true"
    (should (fmt/should-show-paused? true false)))

  (it "returns true when pause-requested is true"
    (should (fmt/should-show-paused? false true)))

  (it "returns true when both are true"
    (should (fmt/should-show-paused? true true)))

  (it "returns false when both are false"
    (should-not (fmt/should-show-paused? false false))))
