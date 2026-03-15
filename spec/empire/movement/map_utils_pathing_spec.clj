(ns empire.game-mechanics.movement.map-utils-pathing-spec
  (:require [speclj.core :refer :all]
            [empire.game-mechanics.movement.map-utils :as map-utils]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-world!]]
            [empire.test.utils :as test-utils]))

(describe "get-matching-neighbors"
  (before (reset-all-atoms!))
  (it "returns matching neighbors in offset order"
    (let [the-map (build-test-map ["~#~"
                                   "#~#"
                                   "~#~"])]
      (should= [[0 1] [1 0] [1 2] [2 1]]
               (vec (map-utils/get-matching-neighbors [1 1] the-map
                                                      map-utils/neighbor-offsets #(= :land (:type %)))))))

  (it "finds neighbors at map origin boundaries"
    (let [the-map (build-test-map ["~#"
                                   "#~"])]
      (should= [[0 1] [1 0]]
               (vec (map-utils/get-matching-neighbors [0 0] the-map
                                                      map-utils/neighbor-offsets #(= :land (:type %)))))))

  (it "returns empty when no neighbors match"
    (let [the-map (build-test-map ["~~"
                                   "~~"])]
      (should= []
               (vec (map-utils/get-matching-neighbors [0 0] the-map
                                                      map-utils/neighbor-offsets #(= :land (:type %))))))))

(describe "passable?"
  (before (reset-all-atoms!))

  (it "returns false for unexplored cells"
    (should-not (map-utils/passable? :army {:type :unexplored})))

  (it "returns false for nil cell"
    (should-not (map-utils/passable? :army nil)))

  (it "returns true for land cell with army"
    (should (map-utils/passable? :army {:type :land})))

  (it "returns false for sea cell with army"
    (should-not (map-utils/passable? :army {:type :sea})))

  (it "returns true for sea cell with ship"
    (should (map-utils/passable? :destroyer {:type :sea})))

  (it "returns false for land cell with ship"
    (should-not (map-utils/passable? :destroyer {:type :land})))

  (it "returns true for any explored cell with fighter"
    (should (map-utils/passable? :fighter {:type :land}))
    (should (map-utils/passable? :fighter {:type :sea}))
    (should (map-utils/passable? :fighter {:type :city}))))

(describe "get-passable-neighbors"
  (before (reset-all-atoms!))

  (it "returns land neighbors for army"
    (set-test-world! (build-test-map ["###"
                                      "#a#"
                                      "~~~"]))
    (let [neighbors (map-utils/get-passable-neighbors [1 1] :army (test-utils/read-test-state :game-map))]
      (should= 5 (count neighbors))
      (should-contain [0 0] neighbors)
      (should-contain [1 0] neighbors)
      (should-contain [2 0] neighbors)
      (should-contain [0 1] neighbors)
      (should-contain [2 1] neighbors)))

  (it "returns sea neighbors for ship"
    (set-test-world! (build-test-map ["~~~"
                                      "~d~"
                                      "###"]))
    (let [neighbors (map-utils/get-passable-neighbors [1 1] :destroyer (test-utils/read-test-state :game-map))]
      (should= 5 (count neighbors))
      (should-contain [0 0] neighbors)
      (should-contain [1 0] neighbors)
      (should-contain [2 0] neighbors)
      (should-contain [0 1] neighbors)
      (should-contain [2 1] neighbors)))

  (it "returns all neighbors for fighter"
    (set-test-world! (build-test-map ["#~#"
                                      "~f~"
                                      "#~#"]))
    (let [neighbors (map-utils/get-passable-neighbors [1 1] :fighter (test-utils/read-test-state :game-map))]
      (should= 8 (count neighbors)))))
