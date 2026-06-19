(ns empire.init-spec
  (:require [empire.test.utils :as test-utils]
    [empire.game.initialization :refer :all]
    [empire.test.utils :refer [reset-all-atoms!]]
    [empire.config.core :as config]
    [speclj.core :refer :all]))

(describe "smooth-map"
  (before (reset-all-atoms!))
  (it "handles a 1x1 map by returning the same value"
    (let [input [[42]]
          result (smooth-map input)]
      (should= input result)))

  (it "smooths a 2x2 map correctly"
    (let [input [[2 4] [3 5]]
          result (smooth-map input)
          expected [[3 4] [3 4]]]
      (should= expected result))))

(declare map-size smooth-count land-fraction num-cities min-distance initial-map)
(describe "make-initial-map"
  (before (reset-all-atoms!))
  (with map-size [10 10])
  (with smooth-count 5)
  (with land-fraction 0.7)
  (with num-cities 5)
  (with min-distance 4)
  (with initial-map (do
                      (make-initial-map @map-size @smooth-count @land-fraction @num-cities @min-distance)
                      (test-utils/read-test-state :game-map)))

  (it "creates a map with correct dimensions"
    (should= 10 (count @initial-map))
    (should= 10 (count (first @initial-map))))

  (it "creates cells with correct structure"
    (doseq [row @initial-map]
      (doseq [cell row]
        (should (map? cell))
        (should (contains? cell :type))
        (should (#{:land :sea :city} (:type cell)))
        (should (nil? (:contents cell)))
        (when (= :city (:type cell))
          (should (#{:player :computer :free} (:city-status cell)))))))

  (it "has approximately correct land fraction"
    (let [land-count (count (for [row @initial-map
                                  cell row
                                  :when (not= :sea (:type cell))]
                              cell))
          expected-land (* @land-fraction (count @initial-map) (count (first @initial-map)))
          tolerance 10]
      (should (<= (- expected-land tolerance) land-count (+ expected-land tolerance)))))

  (it "places the correct number of cities"
    (let [city-count (count (for [i (range (count @initial-map))
                                  j (range (count (first @initial-map)))
                                  :let [cell (get-in @initial-map [i j])]
                                  :when (= :city (:type cell))]
                              [i j]))]
      (should (>= city-count 2))
      (should (<= city-count 6))))                          ;; Allow up to num-cities + occupied

  (it "assigns country-id 1 to computer starting city"
    (make-initial-map [30 30] @smooth-count @land-fraction 40 @min-distance)
    (let [game-map (test-utils/read-test-state :game-map)
          computer-city-pos (find-city-position game-map :computer)]
      (should-not-be-nil computer-city-pos)
      (should= 1 (:country-id (get-in game-map computer-city-pos)))))

  (it "sets next-country-id to 2 after init"
    (make-initial-map [30 30] @smooth-count @land-fraction 40 @min-distance)
    (let [game-map (test-utils/read-test-state :game-map)
          computer-city-pos (find-city-position game-map :computer)]
      (should-not-be-nil computer-city-pos)
      (should= 2 (test-utils/read-test-state :next-country-id))))

  (it "sets army production on computer starting city"
    (make-initial-map [30 30] @smooth-count @land-fraction 40 @min-distance)
    (let [game-map (test-utils/read-test-state :game-map)
          computer-city-pos (find-city-position game-map :computer)]
      (should-not-be-nil computer-city-pos)
      (let [prod (get (test-utils/read-test-state :production) computer-city-pos)]
        (should-not-be-nil prod)
        (should= :army (:item prod))
        (should= (config/item-cost :army) (:remaining-rounds prod)))))

  (it "computes lake-max-cells as 10% of map area at game start"
    @initial-map
    (should= 10 (test-utils/read-test-state :lake-max-cells)))

  (it "places cities with minimum distance"
    (let [city-positions (for [i (range (count @initial-map))
                               j (range (count (first @initial-map)))
                               :let [cell (get-in @initial-map [i j])]
                               :when (= :city (:type cell))]
                           [i j])]
      (doseq [[pos1 pos2] (for [p1 city-positions
                                p2 city-positions
                                :when (not= p1 p2)]
                            [p1 p2])]
        (let [[i1 j1] pos1
              [i2 j2] pos2
              distance (+ (abs (- i1 i2)) (abs (- j1 j2)))]
          (should (>= distance @min-distance)))))))

(describe "smooth-cell"
  (before (reset-all-atoms!))
  (it "smooths center cell correctly"
    (let [test-map [[1 4 7] [2 5 8] [3 6 9]]
          result (smooth-cell 1 1 test-map)]
      ;; Neighbors: 1,4,7,2,5,8,3,6,9 sum=45, avg=5.0, round=5
      (should= 5 result)))

  (it "smooths corner cell with clamping"
    (let [test-map [[1 4 7] [2 5 8] [3 6 9]]
          result (smooth-cell 0 0 test-map)]
      ;; Neighbors: 1,1,1,1,1,4,1,2,5
      ;; Sum: 1+1+1+1+1+4+1+2+5=17, avg=1.889, round=2
      (should= 2 result)))

  (it "smooths edge cell with clamping"
    (let [test-map [[1 4 7] [2 5 8] [3 6 9]]
          result (smooth-cell 0 1 test-map)]
      ;; Neighbors: [4 4 4] [1 4 7] [2 5 8] = 39/9=4.333.. round=4
      (should= 4 result)))

  (it "smooths another edge cell"
    (let [test-map [[1 4 7] [2 5 8] [3 6 9]]
          result (smooth-cell 1 0 test-map)]
      ;; Neighbors: [2 1 4] [2 2 5] [2 3 6] = 27/9=3.0 round=3
      (should= 3 result))))

(describe "coastal?"
  (before (reset-all-atoms!))
  (it "returns true for city adjacent to sea"
    (let [test-map [[{:type :sea} {:type :sea} {:type :land}]
                    [{:type :land} {:type :city :city-status :free} {:type :land}]
                    [{:type :land} {:type :land} {:type :land}]]]
      (should (coastal? [1 1] test-map))))

  (it "returns false for inland city"
    (let [test-map [[{:type :land} {:type :land} {:type :land}]
                    [{:type :land} {:type :city :city-status :free} {:type :land}]
                    [{:type :land} {:type :land} {:type :land}]]]
      (should-not (coastal? [1 1] test-map))))

  (it "returns true for city with diagonal sea neighbor"
    (let [test-map [[{:type :sea} {:type :land} {:type :land}]
                    [{:type :land} {:type :city :city-status :free} {:type :land}]
                    [{:type :land} {:type :land} {:type :land}]]]
      (should (coastal? [1 1] test-map)))))

(describe "occupy-random-free-city"
  (before (reset-all-atoms!))
  (it "selects only coastal cities for player"
    (let [;; Map with one inland city and one coastal city
          test-map [[{:type :sea} {:type :sea} {:type :land}]
                    [{:type :land} {:type :city :city-status :free} {:type :land}]
                    [{:type :land} {:type :land} {:type :city :city-status :free}]]
          ;; Run multiple times to ensure coastal is always chosen
          results (repeatedly 10 #(occupy-random-free-city test-map :player))
          occupied-positions (map (fn [m]
                                    (first (for [i (range 3) j (range 3)
                                                 :when (= :player (:city-status (get-in m [i j])))]
                                             [i j])))
                                  results)]
      ;; [1 1] is coastal, [2 2] is inland - should always pick [1 1]
      (should (every? #(= [1 1] %) occupied-positions))))

  (it "returns unchanged map when no coastal cities available"
    (let [test-map [[{:type :land} {:type :land} {:type :land}]
                    [{:type :land} {:type :city :city-status :free} {:type :land}]
                    [{:type :land} {:type :land} {:type :land}]]
          result (occupy-random-free-city test-map :player)]
      ;; No coastal cities, map should be unchanged
      (should= :free (:city-status (get-in result [1 1])))))

  (it "player and computer starting cities are coastal"
    (let [test-map [[{:type :sea}  {:type :sea}  {:type :land} {:type :land}]
                    [{:type :land} {:type :city :city-status :free} {:type :land} {:type :city :city-status :free}]
                    [{:type :land} {:type :land} {:type :land} {:type :land}]
                    [{:type :city :city-status :free} {:type :land} {:type :land} {:type :land}]]
          ;; [1 1] is coastal (adjacent to [0,0] and [0,1] sea)
          ;; [3 0] and [1 3] are inland (no adjacent sea)
          with-player (occupy-random-free-city test-map :player)
          with-both (occupy-random-free-city with-player :computer)
          player-pos (first (for [i (range 4) j (range 4)
                                  :when (= :player (:city-status (get-in with-both [i j])))]
                              [i j]))
          computer-pos (first (for [i (range 4) j (range 4)
                                    :when (= :computer (:city-status (get-in with-both [i j])))]
                                [i j]))]
      ;; Only [1 1] is coastal, so player gets it
      (should= [1 1] player-pos)
      ;; No coastal cities left, computer gets nothing
      (should= nil computer-pos)))

  (it "respects min-surrounding-land at exact boundary"
    ;; City at [1,1] has exactly 3 surrounding land cells
    ;; With min-surrounding-land=3, it should be selected (>=)
    ;; With min-surrounding-land=4, it should be rejected
    (let [test-map [[{:type :sea}  {:type :sea}  {:type :sea}]
                    [{:type :sea}  {:type :city :city-status :free} {:type :land}]
                    [{:type :sea}  {:type :land}  {:type :land}]]
          result-pass (occupy-random-free-city test-map :player nil 3)
          result-fail (occupy-random-free-city test-map :player nil 4)]
      (should= :player (:city-status (get-in result-pass [1 1])))
      (should= :free (:city-status (get-in result-fail [1 1])))))

  (it "filters by distance from non-origin reference point"
    (let [test-map [[{:type :sea}  {:type :sea}  {:type :land} {:type :land} {:type :land}]
                    [{:type :city :city-status :free} {:type :land} {:type :land} {:type :land} {:type :land}]
                    [{:type :land} {:type :land} {:type :land} {:type :land} {:type :land}]
                    [{:type :land} {:type :land} {:type :land} {:type :land} {:type :city :city-status :free}]
                    [{:type :land} {:type :land} {:type :land} {:type :land} {:type :sea}]]
          ;; [1,0] is coastal (near [0,0] and [0,1] sea). Distance from [2,2] = |1-2|+|0-2| = 3
          ;; [3,4] is coastal (near [4,4] sea). Distance from [2,2] = |3-2|+|4-2| = 3
          ;; Both are distance 3 from [2,2]. With min-dist 3 (>=), they qualify.
          result-at-boundary (occupy-random-free-city test-map :player [2 2 3])
          ;; With min-dist 4, neither qualifies → map unchanged
          result-beyond (occupy-random-free-city test-map :player [2 2 4])]
      ;; At exact boundary: at least one city occupied
      (should (or (= :player (:city-status (get-in result-at-boundary [1 0])))
                  (= :player (:city-status (get-in result-at-boundary [3 4])))))
      ;; Beyond boundary: neither occupied
      (should= :free (:city-status (get-in result-beyond [1 0])))
      (should= :free (:city-status (get-in result-beyond [3 4])))))

  (it "uses 3-arity version with min-distance-from"
    (let [test-map [[{:type :sea} {:type :sea} {:type :land} {:type :land} {:type :land}]
                    [{:type :city :city-status :free} {:type :land} {:type :land} {:type :land} {:type :land}]
                    [{:type :land} {:type :land} {:type :land} {:type :land} {:type :land}]
                    [{:type :land} {:type :land} {:type :land} {:type :land} {:type :city :city-status :free}]
                    [{:type :land} {:type :land} {:type :land} {:type :land} {:type :sea}]]
          ;; Two coastal cities: [1 0] and [3 4]
          ;; Distance from [0 0] to [1 0] = 1, to [3 4] = 7
          result (occupy-random-free-city test-map :player [0 0 5])]
      ;; Should only pick [3 4] since it's >= 5 away from [0 0]
      (should= :player (:city-status (get-in result [3 4]))))))

(describe "generate-cities"
  (before (reset-all-atoms!))
  (it "places cities on land cells"
    (let [test-map [[{:type :land} {:type :land}]
                    [{:type :land} {:type :sea}]]
          result (generate-cities test-map 2 1)
          city-count (count (for [i (range 2) j (range 2)
                                  :when (= :city (:type (get-in result [i j])))]
                              [i j]))]
      (should= 2 city-count)))

  (it "stops placement after 1000 attempts when impossible to place all cities"
    ;; A 2x2 map with only 2 land cells and min-distance of 10 makes it impossible
    ;; to place more than 1 city with proper spacing
    (let [test-map [[{:type :land} {:type :sea}]
                    [{:type :sea} {:type :land}]]
          ;; Request 5 cities with min-distance 10 on a 2x2 map - impossible
          result (generate-cities test-map 5 10)
          city-count (count (for [i (range 2) j (range 2)
                                  :when (= :city (:type (get-in result [i j])))]
                              [i j]))]
      ;; Should have placed 1 city (the first one), then hit 1000 attempts
      (should (< city-count 5)))))

(describe "make-map"
  (it "applies smoothing the specified number of times"
    (let [call-count (atom 0)]
      (with-redefs [smooth-map (fn [m] (swap! call-count inc) m)]
        (make-map 3 3 4)
        (should= 4 @call-count)))))

(describe "find-city-position"
  (it "finds the correct owner's city"
    (let [test-map [[{:type :land} {:type :city :city-status :player}]
                    [{:type :city :city-status :computer} {:type :city :city-status :free}]]]
      (should= [0 1] (find-city-position test-map :player))
      (should= [1 0] (find-city-position test-map :computer)))))

(describe "finalize-map"
  (it "values at exactly sea-level are sea, not land"
    (let [height-map [[100 200] [300 400]]
          result (finalize-map height-map 200)]
      (should= :sea (:type (get-in result [0 0])))    ; 100 < 200
      (should= :sea (:type (get-in result [0 1])))    ; 200 = 200 (not >)
      (should= :land (:type (get-in result [1 0])))   ; 300 > 200
      (should= :land (:type (get-in result [1 1]))))) ; 400 > 200
  )

(describe "find-sea-level"
  (it "returns correct threshold for given land fraction"
    ;; sorted: [100 200 300 400 500 600], total=6
    ;; land-fraction 0.5 → target-land=3, idx=max(0,min(5,3))=3 → 400
    (should= 400 (find-sea-level [[100 400] [200 500] [300 600]] 0.5)))

  (it "handles full land fraction"
    ;; land-fraction 1.0 → target-land=6, idx=max(0,min(5,0))=0 → 100
    (should= 100 (find-sea-level [[100 400] [200 500] [300 600]] 1.0)))

  (it "handles zero land fraction"
    ;; land-fraction 0.0 → target-land=0, idx=max(0,min(5,6))=5 → 600
    (should= 600 (find-sea-level [[100 400] [200 500] [300 600]] 0.0))))

(describe "count-surrounding-land"
  (it "counts all neighbors in 5x5 area at center"
    (let [test-map (vec (for [_ (range 5)]
                          (vec (for [_ (range 5)] {:type :land}))))]
      (should= 24 (count-surrounding-land [2 2] test-map))))

  (it "clamps to map bounds at corner"
    (let [test-map (vec (for [_ (range 5)]
                          (vec (for [_ (range 5)] {:type :land}))))]
      ;; [0,0]: valid di in {0,1,2}, valid dj in {0,1,2} → 9 - 1 center = 8
      (should= 8 (count-surrounding-land [0 0] test-map))))

  (it "counts cities as land"
    (let [test-map [[{:type :land} {:type :city :city-status :free} {:type :land}]
                    [{:type :land} {:type :land} {:type :land}]
                    [{:type :land} {:type :land} {:type :land}]]]
      (should= 8 (count-surrounding-land [1 1] test-map))))

  (it "excludes sea cells"
    (let [test-map [[{:type :sea} {:type :sea} {:type :sea}]
                    [{:type :sea} {:type :land} {:type :sea}]
                    [{:type :sea} {:type :sea} {:type :sea}]]]
      (should= 0 (count-surrounding-land [1 1] test-map)))))
