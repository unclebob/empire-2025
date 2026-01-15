(ns empire.init-spec
  (:require
    [empire.atoms :as atoms]
    [empire.init :refer :all]
    [speclj.core :refer :all]))

(describe "smooth-map"
  (it "handles a 1x1 map by returning the same value"
    (let [input [[42]]
          result (smooth-map input)]
      (should= input result)))

  (it "smooths a 2x2 map correctly"
    (let [input [[2 3] [4 5]]
          result (smooth-map input)
          expected [[3 3] [4 4]]]
      (should= expected result))))

(declare map-size smooth-count land-fraction num-cities min-distance initial-map)
(describe "make-initial-map"
  (with map-size [10 10])
  (with smooth-count 5)
  (with land-fraction 0.7)
  (with num-cities 5)
  (with min-distance 4)
  (with initial-map (do
                      (make-initial-map @map-size @smooth-count @land-fraction @num-cities @min-distance)
                      @atoms/game-map))

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
  (it "smooths center cell correctly"
    (let [test-map [[1 2 3] [4 5 6] [7 8 9]]
          result (smooth-cell 1 1 test-map)]
      ;; Neighbors: 1,2,3,4,5,6,7,8,9 sum=45, avg=5.0, round=5
      (should= 5 result)))

  (it "smooths corner cell with clamping"
    (let [test-map [[1 2 3] [4 5 6] [7 8 9]]
          result (smooth-cell 0 0 test-map)]
      ;; Neighbors: [0,0]=1, [0,0]=1, [0,1]=2, [0,0]=1, [0,0]=1, [0,1]=2, [1,0]=4, [1,0]=4, [1,1]=5
      ;; Sum: 1+1+2+1+1+2+4+4+5=21, avg=2.333, round=2
      (should= 2 result)))

  (it "smooths edge cell with clamping"
    (let [test-map [[1 2 3] [4 5 6] [7 8 9]]
          result (smooth-cell 1 0 test-map)]
      ;; Neighbors: [4 1 2] [4 4 5] [4 7 8] = 39/9=4.333.. round=4
      (should= 4 result)))

  (it "smooths another edge cell"
    (let [test-map [[1 2 3] [4 5 6] [7 8 9]]
          result (smooth-cell 0 1 test-map)]
      ;; Neighbors: [1 1 1] [1 2 3] [4 5 6] = 24/9=2.666.. round=3
      (should= 3 result))))

(describe "coastal?"
  (it "returns true for city adjacent to sea"
    (let [test-map [[{:type :sea} {:type :land} {:type :land}]
                    [{:type :sea} {:type :city :city-status :free} {:type :land}]
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
  (it "selects only coastal cities for player"
    (let [;; Map with one inland city and one coastal city
          test-map [[{:type :sea} {:type :land} {:type :land}]
                    [{:type :sea} {:type :city :city-status :free} {:type :land}]
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
    (let [test-map [[{:type :sea}  {:type :land} {:type :land} {:type :city :city-status :free}]
                    [{:type :sea}  {:type :city :city-status :free} {:type :land} {:type :land}]
                    [{:type :land} {:type :land} {:type :land} {:type :land}]
                    [{:type :land} {:type :city :city-status :free} {:type :land} {:type :land}]]
          ;; [1 1] is coastal (adjacent to [0,0] and [1,0] sea)
          ;; [0 3] and [3 1] are inland (no adjacent sea)
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

  (it "uses 3-arity version with min-distance-from"
    (let [test-map [[{:type :sea} {:type :city :city-status :free} {:type :land} {:type :land} {:type :land}]
                    [{:type :sea} {:type :land} {:type :land} {:type :land} {:type :land}]
                    [{:type :land} {:type :land} {:type :land} {:type :land} {:type :land}]
                    [{:type :land} {:type :land} {:type :land} {:type :land} {:type :land}]
                    [{:type :land} {:type :land} {:type :land} {:type :city :city-status :free} {:type :sea}]]
          ;; Two coastal cities: [0 1] and [4 3]
          ;; Distance from [0 0] to [0 1] = 1, to [4 3] = 7
          result (occupy-random-free-city test-map :player [0 0 5])]
      ;; Should only pick [4 3] since it's >= 5 away from [0 0]
      (should= :player (:city-status (get-in result [4 3]))))))

(describe "generate-cities"
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