(ns empire.movement.map-utils-spec
  (:require [speclj.core :refer :all]
            [empire.movement.map-utils :as map-utils]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [build-test-map reset-all-atoms! make-initial-test-map]]))

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
      (should= :land (:type (get-in result [0 1])))
      (should= nil (:depth (get-in result [0 1]))))))

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
      (should= [[0 0] [0 1]] (vec result))))

  (it "handles empty map"
    (let [result (map-utils/filter-map [] (constantly true))]
      (should= [] (vec result))))

  (it "finds cities by status"
    (let [input-map (build-test-map ["O#"
                                      "XO"])
          result (map-utils/filter-map input-map #(= :player (:city-status %)))]
      (should= [[0 0] [1 1]] (vec result)))))

(describe "on-coast?"
  (before (reset-all-atoms!))
  (it "returns true when cell is adjacent to sea"
    (reset! atoms/game-map (build-test-map ["#~"
                                             "##"]))
    (should (map-utils/on-coast? 0 0)))

  (it "returns false when cell is not adjacent to sea"
    (reset! atoms/game-map (build-test-map ["###"
                                             "###"
                                             "###"]))
    (should-not (map-utils/on-coast? 1 1)))

  (it "handles corner cells"
    (reset! atoms/game-map (build-test-map ["##"
                                             "#~"]))
    (should (map-utils/on-coast? 0 0)))

  (it "handles edge cells"
    (reset! atoms/game-map (build-test-map ["~"
                                             "#"
                                             "#"]))
    (should (map-utils/on-coast? 1 0))
    (should-not (map-utils/on-coast? 2 0))))

(describe "on-map?"
  (before (reset-all-atoms!))
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
  (before (reset-all-atoms!))
  (it "converts pixel coordinates to cell coordinates"
    (reset! atoms/map-screen-dimensions [800 600])
    (reset! atoms/game-map (make-initial-test-map 8 6 nil))
    ;; 800/8 = 100 pixels per cell width, 600/6 = 100 pixels per cell height
    (should= [0 0] (map-utils/determine-cell-coordinates 0 0))
    (should= [0 0] (map-utils/determine-cell-coordinates 50 50))
    (should= [1 0] (map-utils/determine-cell-coordinates 100 50))
    (should= [0 1] (map-utils/determine-cell-coordinates 50 100))
    (should= [7 5] (map-utils/determine-cell-coordinates 750 550))))

(describe "city?"
  (before (reset-all-atoms!))
  (it "returns true for city cells"
    (reset! atoms/game-map (build-test-map ["+"]))
    (should (map-utils/city? [0 0])))

  (it "returns false for non-city cells"
    (reset! atoms/game-map (build-test-map ["#"]))
    (should-not (map-utils/city? [0 0])))

  (it "returns false for sea cells"
    (reset! atoms/game-map (build-test-map ["~"]))
    (should-not (map-utils/city? [0 0]))))

(describe "blink?"
  (before (reset-all-atoms!))
  (it "returns a boolean"
    (should (boolean? (map-utils/blink? 500))))

  (it "returns true or false based on time period"
    (let [result (map-utils/blink? 1)]
      (should (or (true? result) (false? result))))))

(describe "adjacent-to-land?"
  (before (reset-all-atoms!))
  (it "returns true when position is adjacent to land"
    (let [game-map (atom (build-test-map ["~#"
                                    "~~"]))]
      (should (map-utils/adjacent-to-land? [0 0] game-map))))

  (it "returns false when position is not adjacent to land"
    (let [game-map (atom (build-test-map ["~~~"
                                    "~~~"
                                    "~~~"]))]
      (should-not (map-utils/adjacent-to-land? [1 1] game-map))))

  (it "handles corner positions"
    (let [game-map (atom (build-test-map ["~~"
                                    "#~"]))]
      (should (map-utils/adjacent-to-land? [0 0] game-map))))

  (it "returns true for diagonal adjacency"
    (let [game-map (atom (build-test-map ["~~~"
                                    "~~~"
                                    "~~#"]))]
      (should (map-utils/adjacent-to-land? [1 1] game-map)))))

(describe "orthogonally-adjacent-to-land?"
  (before (reset-all-atoms!))
  (it "returns true when orthogonally adjacent to land"
    (let [game-map (atom (build-test-map ["~#"
                                    "~~"]))]
      (should (map-utils/orthogonally-adjacent-to-land? [0 0] game-map))))

  (it "returns false for only diagonal adjacency"
    (let [game-map (atom (build-test-map ["~~~"
                                    "~~~"
                                    "~~#"]))]
      (should-not (map-utils/orthogonally-adjacent-to-land? [1 1] game-map))))

  (it "returns false when not adjacent to land"
    (let [game-map (atom (build-test-map ["~~~"
                                    "~~~"
                                    "~~~"]))]
      (should-not (map-utils/orthogonally-adjacent-to-land? [1 1] game-map)))))

(describe "completely-surrounded-by-sea?"
  (before (reset-all-atoms!))
  (it "returns true when no adjacent land"
    (let [game-map (atom (build-test-map ["~~~"
                                    "~~~"
                                    "~~~"]))]
      (should (map-utils/completely-surrounded-by-sea? [1 1] game-map))))

  (it "returns false when adjacent to land"
    (let [game-map (atom (build-test-map ["~#~"
                                    "~~~"
                                    "~~~"]))]
      (should-not (map-utils/completely-surrounded-by-sea? [1 1] game-map)))))

(describe "in-bay?"
  (before (reset-all-atoms!))
  (it "returns true when surrounded by 4 or more land cells"
    (let [game-map (atom (build-test-map ["##~"
                                    "#~#"
                                    "~~~"]))]
      (should (map-utils/in-bay? [1 1] game-map))))

  (it "returns true when surrounded by exactly 4 land cells"
    (let [game-map (atom (build-test-map ["#~~"
                                    "#~#"
                                    "~#~"]))]
      (should (map-utils/in-bay? [1 1] game-map))))

  (it "returns false when surrounded by only 3 land cells"
    (let [game-map (atom (build-test-map ["~#~"
                                    "#~#"
                                    "~~~"]))]
      (should-not (map-utils/in-bay? [1 1] game-map))))

  (it "returns false when surrounded by only 2 land cells"
    (let [game-map (atom (build-test-map ["~#~"
                                    "#~~"
                                    "~~~"]))]
      (should-not (map-utils/in-bay? [1 1] game-map))))

  (it "returns true when surrounded by land on all 8 sides"
    (let [game-map (atom (build-test-map ["###"
                                    "#~#"
                                    "###"]))]
      (should (map-utils/in-bay? [1 1] game-map)))))

(describe "adjacent-to-sea?"
  (before (reset-all-atoms!))
  (it "returns true when adjacent to sea"
    (let [game-map (atom (build-test-map ["#~"
                                    "##"]))]
      (should (map-utils/adjacent-to-sea? [0 0] game-map))))

  (it "returns false when not adjacent to sea"
    (let [game-map (atom (build-test-map ["###"
                                    "###"
                                    "###"]))]
      (should-not (map-utils/adjacent-to-sea? [1 1] game-map)))))

(describe "at-map-edge?"
  (before (reset-all-atoms!))
  (it "returns true for top edge"
    (let [game-map (atom (build-test-map ["#####"
                                    "#####"
                                    "#####"
                                    "#####"
                                    "#####"]))]
      (should (map-utils/at-map-edge? [0 2] game-map))))

  (it "returns true for bottom edge"
    (let [game-map (atom (build-test-map ["#####"
                                    "#####"
                                    "#####"
                                    "#####"
                                    "#####"]))]
      (should (map-utils/at-map-edge? [4 2] game-map))))

  (it "returns true for left edge"
    (let [game-map (atom (build-test-map ["#####"
                                    "#####"
                                    "#####"
                                    "#####"
                                    "#####"]))]
      (should (map-utils/at-map-edge? [2 0] game-map))))

  (it "returns true for right edge"
    (let [game-map (atom (build-test-map ["#####"
                                    "#####"
                                    "#####"
                                    "#####"
                                    "#####"]))]
      (should (map-utils/at-map-edge? [2 4] game-map))))

  (it "returns false for interior position"
    (let [game-map (atom (build-test-map ["#####"
                                    "#####"
                                    "#####"
                                    "#####"
                                    "#####"]))]
      (should-not (map-utils/at-map-edge? [2 2] game-map))))

  (it "returns true for corner"
    (let [game-map (atom (build-test-map ["#####"
                                    "#####"
                                    "#####"
                                    "#####"
                                    "#####"]))]
      (should (map-utils/at-map-edge? [0 0] game-map)))))
