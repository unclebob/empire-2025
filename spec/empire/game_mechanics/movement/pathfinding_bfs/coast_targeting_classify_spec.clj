(ns empire.game-mechanics.movement.pathfinding-bfs.coast-targeting-classify-spec
  (:require [empire.game-mechanics.movement.pathfinding-bfs.coast-targeting :as ct]
            [speclj.core :refer :all]))

(describe "classify-coastal"
  (it "returns false for start position"
    (should-not (@#'ct/classify-coastal [0 0] [0 0] [[{:type :sea}]] 1)))

  (it "returns true when adjacent to unclaimed land with positive army count"
    (let [computer-map [[{:type :sea} {:type :land}]]]
      (should (@#'ct/classify-coastal [0 0] [1 0] computer-map 1))))

  (it "returns false when not adjacent to any land"
    (let [computer-map [[{:type :sea} {:type :sea}]
                        [{:type :sea} {:type :sea}]]]
      (should-not (@#'ct/classify-coastal [0 0] [1 0] computer-map 1))))

  (it "returns false when zero army count and within radius"
    (let [computer-map [[{:type :sea} {:type :land :country-id 1}]]]
      (should-not (@#'ct/classify-coastal [0 0] [0 0] computer-map 0))))

  (it "returns true when zero army count and outside radius next to claimed land"
    (let [computer-map [[{:type :sea} {:type :sea}]
                        [{:type :sea} {:type :sea}]
                        [{:type :sea} {:type :sea}]
                        [{:type :sea} {:type :sea}]
                        [{:type :sea} {:type :sea}]
                        [{:type :sea} {:type :land :country-id 1}]]]
      (should (@#'ct/classify-coastal [5 0] [0 0] computer-map 0)))))
