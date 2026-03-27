(ns empire.computer.ship.patrol.explore-spec
  (:require [empire.computer.ship.patrol.explore :as explore]
            [empire.game.initialization :as init]
            [empire.state.api :as sa]
            [empire.test.utils :refer [reset-all-atoms! build-test-map set-test-world! set-test-computer-map!]]
            [speclj.core :refer :all]))

(describe "find-nearest-unseen-coast-target"
  (before (reset-all-atoms!))

  (it "returns nearest unseen coastal sea cell"
    (let [game-map (build-test-map ["~~~~#" "~~~~~"])]
      (set-test-world! game-map)
      (set-test-computer-map! game-map)
      (sa/write-state! :coastal-index (init/build-coastal-index game-map))
      (sa/write-state! :seen-coast #{})
      (let [result (explore/find-nearest-unseen-coast-target [0 0])]
        (should-not-be-nil result))))

  (it "excludes cells in seen-coast"
    (let [game-map (build-test-map ["~~#" "~~~"])]
      (set-test-world! game-map)
      (set-test-computer-map! game-map)
      (sa/write-state! :coastal-index (init/build-coastal-index game-map))
      (let [all-coastal (:coastal-sea-cells (sa/read-state :coastal-index))]
        (sa/write-state! :seen-coast all-coastal)
        (should-be-nil (explore/find-nearest-unseen-coast-target [0 0])))))

  (it "excludes claimed patrol targets"
    (let [game-map (build-test-map ["~~#" "~~~"])]
      (set-test-world! game-map)
      (set-test-computer-map! game-map)
      (sa/write-state! :coastal-index (init/build-coastal-index game-map))
      (let [all-coastal (:coastal-sea-cells (sa/read-state :coastal-index))]
        (sa/write-state! :claimed-patrol-targets all-coastal)
        (should-be-nil (explore/find-nearest-unseen-coast-target [0 0])))))

  (it "returns nil when no coastal index"
    (let [game-map (build-test-map ["~~~"])]
      (set-test-world! game-map)
      (set-test-computer-map! game-map)
      (sa/write-state! :coastal-index nil)
      (should-be-nil (explore/find-nearest-unseen-coast-target [0 0])))))
