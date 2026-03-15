(ns empire.game-mechanics.movement.map-utils-transform-spec
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.game-mechanics.movement.map-utils :as map-utils]
            [empire.test.utils :refer [build-test-map reset-all-atoms! make-initial-test-map set-test-world!]]))

(describe "process-map"
  (before (reset-all-atoms!))
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
    (let [result (map-utils/process-map (build-test-map ["#"]) (fn [i j m] (assoc (get-in m [i j]) :processed true)))]
      (should= [[{:type :land :processed true}]] result)))

  (it "preserves map structure with game-like cells"
    (let [input-map (build-test-map ["~#"
                                     "+~"])
          result (map-utils/process-map input-map
                                        (fn [i j m]
                                          (let [cell (get-in m [i j])]
                                            (if (= :sea (:type cell))
                                              (assoc cell :depth 100)
                                              cell))))]
      (should= :sea (:type (get-in result [0 0])))
      (should= 100 (:depth (get-in result [0 0])))
      (should= :land (:type (get-in result [1 0])))
      (should= nil (:depth (get-in result [1 0]))))))

(describe "filter-map"
  (before (reset-all-atoms!))
  (it "returns positions where predicate is true"
    (let [input-map (build-test-map ["~#"
                                     "#~"])
          result (map-utils/filter-map input-map #(= :land (:type %)))]
      (should= [[0 1] [1 0]] (vec result))))

  (it "returns empty list when no matches"
    (let [input-map (build-test-map ["~~"])
          result (map-utils/filter-map input-map #(= :land (:type %)))]
      (should= [] (vec result))))

  (it "returns all positions when all match"
    (let [input-map (build-test-map ["##"])
          result (map-utils/filter-map input-map #(= :land (:type %)))]
      (should= [[0 0] [1 0]] (vec result))))

  (it "handles empty map"
    (let [result (map-utils/filter-map [] (constantly true))]
      (should= [] (vec result))))

  (it "finds cities by status"
    (let [input-map (build-test-map ["O#"
                                     "XO"])
          result (map-utils/filter-map input-map #(= :player (:city-status %)))]
      (should= [[0 0] [1 1]] (vec result)))))

(describe "on-map?"
  (before (reset-all-atoms!))
  (it "returns true for valid coordinates"
    (test-utils/set-test-state! :map-screen-dimensions [800 600])
    (should (map-utils/on-map? 0 0))
    (should (map-utils/on-map? 400 300))
    (should (map-utils/on-map? 799 599)))

  (it "returns false for coordinates outside map"
    (test-utils/set-test-state! :map-screen-dimensions [800 600])
    (should-not (map-utils/on-map? -1 0))
    (should-not (map-utils/on-map? 0 -1))
    (should-not (map-utils/on-map? 800 0))
    (should-not (map-utils/on-map? 0 600))))

(describe "determine-cell-coordinates"
  (before (reset-all-atoms!))
  (it "converts pixel coordinates to cell coordinates"
    (test-utils/set-test-state! :map-screen-dimensions [800 600])
    (set-test-world! (make-initial-test-map 6 8 nil))
    (should= [0 0] (map-utils/determine-cell-coordinates 0 0))
    (should= [0 0] (map-utils/determine-cell-coordinates 50 50))
    (should= [1 0] (map-utils/determine-cell-coordinates 100 50))
    (should= [0 1] (map-utils/determine-cell-coordinates 50 100))
    (should= [7 5] (map-utils/determine-cell-coordinates 750 550))))

(describe "city?"
  (before (reset-all-atoms!))
  (it "returns true for city cells"
    (set-test-world! (build-test-map ["+"]))
    (should (map-utils/city? [0 0])))

  (it "returns false for non-city cells"
    (set-test-world! (build-test-map ["#"]))
    (should-not (map-utils/city? [0 0])))

  (it "returns false for sea cells"
    (set-test-world! (build-test-map ["~"]))
    (should-not (map-utils/city? [0 0]))))

(describe "blink?"
  (before (reset-all-atoms!))
  (it "returns a boolean"
    (should (boolean? (map-utils/blink? 500))))

  (it "returns true or false based on time period"
    (let [result (map-utils/blink? 1)]
      (should (or (true? result) (false? result))))))
