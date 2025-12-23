(ns empire.init-spec
  (:require
    [empire.init :refer :all]
    [empire.map :as map]
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
                      @map/game-map))

  (it "creates a map with correct dimensions"
    (should= 10 (count @initial-map))
    (should= 10 (count (first @initial-map))))

  (it "creates cells with correct structure"
    (doseq [row @initial-map]
      (doseq [cell row]
        (should (vector? cell))
        (should= 2 (count cell))
        (should (#{:land :sea} (first cell)))
        (should (#{:empty :free-city :my-city :his-city} (second cell))))))

  (it "has approximately correct land fraction"
    (let [land-count (count (for [row @initial-map
                                  cell row
                                  :when (= :land (first cell))]
                              cell))
          expected-land (* @land-fraction (count @initial-map) (count (first @initial-map)))
          tolerance 10]
      (should (<= (- expected-land tolerance) land-count (+ expected-land tolerance)))))

  (it "places the correct number of cities"
    (let [city-count (count (for [i (range (count @initial-map))
                                  j (range (count (first @initial-map)))
                                  :let [cell (get-in @initial-map [i j])]
                                  :when (#{:free-city :my-city :his-city} (second cell))]
                              [i j]))]
      (should (>= city-count 2))
      (should (<= city-count 6))))                          ;; Allow up to num-cities + occupied

  (it "places cities with minimum distance"
    (let [city-positions (for [i (range (count @initial-map))
                               j (range (count (first @initial-map)))
                               :let [cell (get-in @initial-map [i j])]
                               :when (#{:free-city :my-city :his-city} (second cell))]
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