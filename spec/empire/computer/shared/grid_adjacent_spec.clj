(ns empire.computer.shared.grid-adjacent-spec
  (:require [empire.computer.shared.grid :as grid]
            [speclj.core :refer :all]))

(describe "adjacent?"
  (it "returns true for horizontally adjacent"
    (should (grid/adjacent? [5 5] [5 6]))
    (should (grid/adjacent? [5 5] [5 4])))

  (it "returns true for vertically adjacent"
    (should (grid/adjacent? [5 5] [6 5]))
    (should (grid/adjacent? [5 5] [4 5])))

  (it "returns true for diagonally adjacent"
    (should (grid/adjacent? [5 5] [6 6]))
    (should (grid/adjacent? [5 5] [4 4])))

  (it "returns false for same position"
    (should-not (grid/adjacent? [5 5] [5 5])))

  (it "returns false for distant positions"
    (should-not (grid/adjacent? [0 0] [2 0]))
    (should-not (grid/adjacent? [0 0] [0 2]))))
