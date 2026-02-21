(ns empire.ui.coordinates-spec
  (:require [speclj.core :refer :all]
            [empire.ui.coordinates :as coords]))

(describe "screen->cell"
  (it "converts screen center to correct cell"
    (should= [5 3] (coords/screen->cell 55 48 110 160 10 10)))

  (it "converts origin to [0 0]"
    (should= [0 0] (coords/screen->cell 0 0 100 100 10 10)))

  (it "converts last cell"
    (should= [9 9] (coords/screen->cell 99 99 100 100 10 10)))

  (it "handles non-square maps"
    (should= [2 1] (coords/screen->cell 50 30 100 60 4 2)))

  (it "handles pixel at cell boundary"
    (should= [1 0] (coords/screen->cell 10 0 100 100 10 10)))

  (it "handles single-pixel cells"
    (should= [3 4] (coords/screen->cell 3 4 10 10 10 10))))
