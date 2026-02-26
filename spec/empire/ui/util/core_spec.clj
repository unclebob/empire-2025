(ns empire.ui.util.core-spec
  (:require [empire.atoms :as atoms]
            [empire.ui.util.core :as util-core]
            [empire.test-utils :refer [reset-all-atoms!]]
            [speclj.core :refer :all]))

(describe "key-released"
  (before (reset-all-atoms!))
  (it "resets last-key atom to nil"
    (reset! atoms/last-key :a)
    (util-core/key-released nil nil)
    (should-be-nil @atoms/last-key))

  (it "returns nil when last-key was already nil"
    (reset! atoms/last-key nil)
    (util-core/key-released nil nil)
    (should-be-nil @atoms/last-key)))

(describe "compute-screen-dimensions"
  (before (reset-all-atoms!))
  (it "calculates pixel dimensions from known map-size and cell-size"
    (let [result (util-core/compute-screen-dimensions 100 36 10 20)]
      ;; map-display-w = 100 * 10 = 1000
      (should= 1000 (first (:map-screen-dimensions result)))
      ;; map-display-h = 36 * 20 = 720
      (should= 720 (second (:map-screen-dimensions result)))
      ;; text-x = 0
      (should= 0 (first (:text-area-dimensions result)))
      ;; text-y = 720 + 7 = 727
      (should= 727 (second (:text-area-dimensions result)))
      ;; text-w = 1000
      (should= 1000 (nth (:text-area-dimensions result) 2))
      ;; text-h = 3 * 20 = 60
      (should= 60 (nth (:text-area-dimensions result) 3))))

  (it "calculates dimensions for small map"
    (let [result (util-core/compute-screen-dimensions 80 26 8 16)]
      ;; map-display-w = 80 * 8 = 640
      (should= 640 (first (:map-screen-dimensions result)))
      ;; map-display-h = 26 * 16 = 416
      (should= 416 (second (:map-screen-dimensions result)))))

  (it "calculates dimensions for wide map"
    (let [result (util-core/compute-screen-dimensions 160 41 12 24)]
      ;; map-display-w = 160 * 12 = 1920
      (should= 1920 (first (:map-screen-dimensions result)))
      ;; map-display-h = 41 * 24 = 984
      (should= 984 (second (:map-screen-dimensions result))))))

(describe "screen->cell"
  (it "converts screen center to correct cell"
    (should= [5 3] (util-core/screen->cell 55 48 110 160 10 10)))

  (it "converts origin to [0 0]"
    (should= [0 0] (util-core/screen->cell 0 0 100 100 10 10)))

  (it "converts last cell"
    (should= [9 9] (util-core/screen->cell 99 99 100 100 10 10)))

  (it "handles non-square maps"
    (should= [2 1] (util-core/screen->cell 50 30 100 60 4 2)))

  (it "handles pixel at cell boundary"
    (should= [1 0] (util-core/screen->cell 10 0 100 100 10 10)))

  (it "handles single-pixel cells"
    (should= [3 4] (util-core/screen->cell 3 4 10 10 10 10))))
