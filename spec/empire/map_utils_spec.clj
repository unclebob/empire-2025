(ns empire.map-utils-spec
  (:require [speclj.core :refer :all]
            [empire.map-utils :as map-utils]
            [empire.atoms :as atoms]))

(describe "process-map"
  (it "applies function to each cell returning transformed map"
    (let [input-map [[1 2] [3 4]]
          result (map-utils/process-map input-map (fn [i j the-map] (* (get-in the-map [i j]) 2)))]
      (should= [[2 4] [6 8]] result)))

  (it "provides correct i j indices to function"
    (let [input-map [[nil nil] [nil nil]]
          result (map-utils/process-map input-map (fn [i j _] [i j]))]
      (should= [[[0 0] [0 1]] [[1 0] [1 1]]] result)))

  (it "handles empty map"
    (let [result (map-utils/process-map [] (fn [_ _ _] :x))]
      (should= [] result)))

  (it "handles single cell map"
    (let [result (map-utils/process-map [[{:type :land}]] (fn [i j m] (assoc (get-in m [i j]) :processed true)))]
      (should= [[{:type :land :processed true}]] result)))

  (it "preserves map structure with game-like cells"
    (let [input-map [[{:type :sea} {:type :land}]
                     [{:type :city} {:type :sea}]]
          result (map-utils/process-map input-map
                                        (fn [i j m]
                                          (let [cell (get-in m [i j])]
                                            (if (= :sea (:type cell))
                                              (assoc cell :depth 100)
                                              cell))))]
      (should= :sea (:type (get-in result [0 0])))
      (should= 100 (:depth (get-in result [0 0])))
      (should= :land (:type (get-in result [0 1])))
      (should= nil (:depth (get-in result [0 1]))))))

(describe "filter-map"
  (it "returns positions where predicate is true"
    (let [input-map [[{:type :sea} {:type :land}]
                     [{:type :land} {:type :sea}]]
          result (map-utils/filter-map input-map #(= :land (:type %)))]
      (should= [[0 1] [1 0]] (vec result))))

  (it "returns empty list when no matches"
    (let [input-map [[{:type :sea} {:type :sea}]]
          result (map-utils/filter-map input-map #(= :land (:type %)))]
      (should= [] (vec result))))

  (it "returns all positions when all match"
    (let [input-map [[{:type :land} {:type :land}]]
          result (map-utils/filter-map input-map #(= :land (:type %)))]
      (should= [[0 0] [0 1]] (vec result))))

  (it "handles empty map"
    (let [result (map-utils/filter-map [] (constantly true))]
      (should= [] (vec result))))

  (it "finds cities by status"
    (let [input-map [[{:type :city :city-status :player} {:type :land}]
                     [{:type :city :city-status :computer} {:type :city :city-status :player}]]
          result (map-utils/filter-map input-map #(= :player (:city-status %)))]
      (should= [[0 0] [1 1]] (vec result)))))

(describe "on-coast?"
  (it "returns true when cell is adjacent to sea"
    (reset! atoms/game-map [[{:type :land} {:type :sea}]
                            [{:type :land} {:type :land}]])
    (should (map-utils/on-coast? 0 0)))

  (it "returns false when cell is not adjacent to sea"
    (reset! atoms/game-map [[{:type :land} {:type :land} {:type :land}]
                            [{:type :land} {:type :land} {:type :land}]
                            [{:type :land} {:type :land} {:type :land}]])
    (should-not (map-utils/on-coast? 1 1)))

  (it "handles corner cells"
    (reset! atoms/game-map [[{:type :land} {:type :land}]
                            [{:type :land} {:type :sea}]])
    (should (map-utils/on-coast? 0 0)))

  (it "handles edge cells"
    (reset! atoms/game-map [[{:type :sea}]
                            [{:type :land}]
                            [{:type :land}]])
    (should (map-utils/on-coast? 1 0))
    (should-not (map-utils/on-coast? 2 0))))

(describe "on-map?"
  (it "returns true for valid coordinates"
    (reset! atoms/map-screen-dimensions [800 600])
    (should (map-utils/on-map? 0 0))
    (should (map-utils/on-map? 400 300))
    (should (map-utils/on-map? 799 599)))

  (it "returns false for coordinates outside map"
    (reset! atoms/map-screen-dimensions [800 600])
    (should-not (map-utils/on-map? -1 0))
    (should-not (map-utils/on-map? 0 -1))
    (should-not (map-utils/on-map? 800 0))
    (should-not (map-utils/on-map? 0 600))))

(describe "determine-cell-coordinates"
  (it "converts pixel coordinates to cell coordinates"
    (reset! atoms/map-screen-dimensions [800 600])
    (reset! atoms/game-map (vec (repeat 8 (vec (repeat 6 nil)))))
    ;; 800/8 = 100 pixels per cell width, 600/6 = 100 pixels per cell height
    (should= [0 0] (map-utils/determine-cell-coordinates 0 0))
    (should= [0 0] (map-utils/determine-cell-coordinates 50 50))
    (should= [1 0] (map-utils/determine-cell-coordinates 100 50))
    (should= [0 1] (map-utils/determine-cell-coordinates 50 100))
    (should= [7 5] (map-utils/determine-cell-coordinates 750 550))))

(describe "city?"
  (it "returns true for city cells"
    (reset! atoms/game-map [[{:type :city}]])
    (should (map-utils/city? [0 0])))

  (it "returns false for non-city cells"
    (reset! atoms/game-map [[{:type :land}]])
    (should-not (map-utils/city? [0 0])))

  (it "returns false for sea cells"
    (reset! atoms/game-map [[{:type :sea}]])
    (should-not (map-utils/city? [0 0]))))
