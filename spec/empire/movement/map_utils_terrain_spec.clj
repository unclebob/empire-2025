(ns empire.game-mechanics.movement.map-utils-terrain-spec
  (:require [speclj.core :refer :all]
            [empire.game-mechanics.movement.map-utils :as map-utils]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-world!]]))

(describe "on-coast?"
  (before (reset-all-atoms!))
  (it "returns true when cell is adjacent to sea"
    (set-test-world! (build-test-map ["#~"
                                      "##"]))
    (should (map-utils/on-coast? 0 0)))

  (it "returns false when cell is not adjacent to sea"
    (set-test-world! (build-test-map ["###"
                                      "###"
                                      "###"]))
    (should-not (map-utils/on-coast? 1 1)))

  (it "handles corner cells"
    (set-test-world! (build-test-map ["##"
                                      "#~"]))
    (should (map-utils/on-coast? 0 0)))

  (it "handles edge cells"
    (set-test-world! (build-test-map ["~"
                                      "#"
                                      "#"]))
    (should (map-utils/on-coast? 0 1))
    (should-not (map-utils/on-coast? 0 2))))

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
      (should-not (map-utils/orthogonally-adjacent-to-land? [1 1] game-map))))

  (it "detects land only to the west"
    (let [game-map (atom (build-test-map ["#~~"
                                          "~~~"]))]
      (should (map-utils/orthogonally-adjacent-to-land? [1 0] game-map))))

  (it "detects land only to the north"
    (let [game-map (atom (build-test-map ["~~~"
                                          "#~~"
                                          "~~~"]))]
      (should (map-utils/orthogonally-adjacent-to-land? [0 2] game-map))))

  (it "detects land only to the south"
    (let [game-map (atom (build-test-map ["~~"
                                          "#~"
                                          "~~"]))]
      (should (map-utils/orthogonally-adjacent-to-land? [0 0] game-map)))))

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
      (should (map-utils/at-map-edge? [2 0] game-map))))

  (it "returns true for bottom edge"
    (let [game-map (atom (build-test-map ["#####"
                                          "#####"
                                          "#####"
                                          "#####"
                                          "#####"]))]
      (should (map-utils/at-map-edge? [2 4] game-map))))

  (it "returns true for left edge"
    (let [game-map (atom (build-test-map ["#####"
                                          "#####"
                                          "#####"
                                          "#####"
                                          "#####"]))]
      (should (map-utils/at-map-edge? [0 2] game-map))))

  (it "returns true for right edge"
    (let [game-map (atom (build-test-map ["#####"
                                          "#####"
                                          "#####"
                                          "#####"
                                          "#####"]))]
      (should (map-utils/at-map-edge? [4 2] game-map))))

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
