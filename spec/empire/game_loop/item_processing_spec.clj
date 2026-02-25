(ns empire.game-loop.item-processing-spec
  (:require [speclj.core :refer :all]
            [empire.game-loop.item-processing :as ip]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [reset-all-atoms!]]))

(defn- land-cell [] {:type :land})

(defn- make-land-map [n]
  (vec (repeat n (vec (repeat n (land-cell))))))

(describe "process-computer-items"
  (before (reset-all-atoms!))

  (it "does nothing when computer-items is empty"
    (reset! atoms/computer-items [])
    (reset! atoms/game-map (make-land-map 5))
    (ip/process-computer-items)
    (should= [] @atoms/computer-items))

  (it "processes all items when fewer than 100"
    (reset! atoms/game-map (make-land-map 5))
    (reset! atoms/computer-items [[0 0] [1 1] [2 2] [3 3] [4 4]])
    (ip/process-computer-items)
    (should= [] @atoms/computer-items))

  (it "stops after 100 items"
    (let [n 5
          coords (for [c (range n) r (range n)] [c r])]
      (reset! atoms/game-map (make-land-map n))
      (reset! atoms/computer-items (vec (apply concat (repeat 10 coords))))
      (should= 250 (count @atoms/computer-items))
      (ip/process-computer-items)
      (should= 150 (count @atoms/computer-items)))))
