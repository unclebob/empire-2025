(ns empire.ui.coordinates-spec
  "Tests for pure coordinate conversion functions."
  (:require [speclj.core :refer :all]
            [empire.ui.coordinates :as coords]))

(describe "coordinates"

  (describe "in-map-bounds?"
    (it "returns true for coordinates inside bounds"
      (should (coords/in-map-bounds? 50 50 100 100)))

    (it "returns true for coordinates at origin"
      (should (coords/in-map-bounds? 0 0 100 100)))

    (it "returns false for coordinates at edge"
      (should-not (coords/in-map-bounds? 100 50 100 100))
      (should-not (coords/in-map-bounds? 50 100 100 100)))

    (it "returns false for negative coordinates"
      (should-not (coords/in-map-bounds? -1 50 100 100))
      (should-not (coords/in-map-bounds? 50 -1 100 100))))

  (describe "adjacent?"
    (it "returns true for horizontally adjacent positions"
      (should (coords/adjacent? [5 5] [5 6]))
      (should (coords/adjacent? [5 5] [5 4])))

    (it "returns true for vertically adjacent positions"
      (should (coords/adjacent? [5 5] [6 5]))
      (should (coords/adjacent? [5 5] [4 5])))

    (it "returns true for diagonally adjacent positions"
      (should (coords/adjacent? [5 5] [6 6]))
      (should (coords/adjacent? [5 5] [4 4]))
      (should (coords/adjacent? [5 5] [4 6]))
      (should (coords/adjacent? [5 5] [6 4])))

    (it "returns false for same position"
      (should-not (coords/adjacent? [5 5] [5 5])))

    (it "returns false for non-adjacent positions"
      (should-not (coords/adjacent? [5 5] [5 7]))
      (should-not (coords/adjacent? [5 5] [7 5]))
      (should-not (coords/adjacent? [5 5] [7 7]))))

  (describe "chebyshev-distance"
    (it "returns 0 for same position"
      (should= 0 (coords/chebyshev-distance [5 5] [5 5])))

    (it "returns 1 for adjacent positions"
      (should= 1 (coords/chebyshev-distance [5 5] [5 6]))
      (should= 1 (coords/chebyshev-distance [5 5] [6 6])))

    (it "returns max of row/col differences"
      (should= 3 (coords/chebyshev-distance [0 0] [3 2]))
      (should= 5 (coords/chebyshev-distance [0 0] [5 3]))))

  (describe "manhattan-distance"
    (it "returns 0 for same position"
      (should= 0 (coords/manhattan-distance [5 5] [5 5])))

    (it "returns 1 for orthogonally adjacent positions"
      (should= 1 (coords/manhattan-distance [5 5] [5 6]))
      (should= 1 (coords/manhattan-distance [5 5] [6 5])))

    (it "returns 2 for diagonally adjacent positions"
      (should= 2 (coords/manhattan-distance [5 5] [6 6])))

    (it "returns sum of row/col differences"
      (should= 5 (coords/manhattan-distance [0 0] [2 3]))))

  (describe "extend-to-edge"
    (it "extends north to edge"
      (should= [0 5] (coords/extend-to-edge [5 5] [-1 0] 10 10)))

    (it "extends south to edge"
      (should= [9 5] (coords/extend-to-edge [5 5] [1 0] 10 10)))

    (it "extends east to edge"
      (should= [5 9] (coords/extend-to-edge [5 5] [0 1] 10 10)))

    (it "extends west to edge"
      (should= [5 0] (coords/extend-to-edge [5 5] [0 -1] 10 10)))

    (it "extends diagonally to edge"
      (should= [9 9] (coords/extend-to-edge [5 5] [1 1] 10 10))
      (should= [0 0] (coords/extend-to-edge [5 5] [-1 -1] 10 10)))))
