(ns empire.game.initialization-coastal-index-spec
  (:require [empire.game.initialization :as init]
            [empire.test.utils :refer [reset-all-atoms! build-test-map]]
            [speclj.core :refer :all]))

(describe "build-coastal-index"
  (it "identifies coastal land cells"
    (let [game-map (build-test-map ["~~#" "~~~" "~~~"])
          {:keys [coastal-land-cells]} (init/build-coastal-index game-map)]
      (should (contains? coastal-land-cells [2 0]))))

  (it "identifies coastal sea cells"
    (let [game-map (build-test-map ["~~#" "~~~" "~~~"])
          {:keys [coastal-sea-cells]} (init/build-coastal-index game-map)]
      (should (contains? coastal-sea-cells [1 0]))
      (should-not (contains? coastal-sea-cells [0 1]))))

  (it "excludes interior land cells"
    (let [game-map (build-test-map ["###" "###" "###"])
          {:keys [coastal-land-cells]} (init/build-coastal-index game-map)]
      (should (empty? coastal-land-cells))))

  (it "excludes open ocean cells far from land"
    (let [game-map (build-test-map ["~~~~" "~#~~" "~~~~" "~~~~"])
          {:keys [coastal-sea-cells]} (init/build-coastal-index game-map)]
      (should-not (contains? coastal-sea-cells [3 3]))
      (should-not (contains? coastal-sea-cells [0 3]))))

  (it "builds coastal-sea neighbor map"
    (let [game-map (build-test-map ["~~~" "~#~" "~~~"])
          {:keys [coastal-sea-neighbors]} (init/build-coastal-index game-map)]
      ;; [0 0] is coastal sea (adjacent to land at [1 1])
      ;; [1 0] is also coastal sea
      ;; They should be neighbors of each other in the map
      (should (contains? coastal-sea-neighbors [0 0]))
      (should (contains? (get coastal-sea-neighbors [0 0]) [1 0])))))
