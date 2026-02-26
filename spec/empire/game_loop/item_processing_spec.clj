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

(describe "process-player-items-batch"
  (before (reset-all-atoms!))

  (it "stops when player-items is empty"
    (reset! atoms/player-items '())
    (reset! atoms/game-map (make-land-map 3))
    (ip/process-player-items-batch)
    (should= '() @atoms/player-items))

  (it "stops when paused (victory declared)"
    (reset! atoms/game-map (make-land-map 3))
    (reset! atoms/player-items (list [0 0] [1 1]))
    (reset! atoms/paused true)
    (ip/process-player-items-batch)
    (should= 2 (count @atoms/player-items)))

  (it "stops when waiting-for-input is set"
    (reset! atoms/game-map (make-land-map 3))
    (reset! atoms/player-items (list [0 0] [1 1]))
    (reset! atoms/waiting-for-input true)
    (ip/process-player-items-batch)
    (should= 2 (count @atoms/player-items)))

  (it "stops after processing 100 items"
    (reset! atoms/game-map (make-land-map 5))
    (let [coords (for [c (range 5) r (range 5)] [c r])]
      (reset! atoms/player-items (apply list (apply concat (repeat 6 coords)))))
    (should= 150 (count @atoms/player-items))
    (ip/process-player-items-batch)
    (should= 50 (count @atoms/player-items)))

  (it "stops when process-one-item returns :waiting (awake unit needs attention)"
    (let [game-map (make-land-map 3)
          unit {:type :army :owner :player :mode :awake :hits 1}
          game-map (assoc-in game-map [1 1 :contents] unit)]
      (reset! atoms/game-map game-map)
      (reset! atoms/player-items (list [1 1] [0 0]))
      (ip/process-player-items-batch)
      (should @atoms/waiting-for-input)
      (should= [[1 1]] @atoms/cells-needing-attention)))

  (it "declares victory when computer has no items left"
    (let [game-map (make-land-map 3)]
      (reset! atoms/game-map game-map)
      (reset! atoms/game-over-check-enabled true)
      (reset! atoms/player-items (list [0 0] [1 1]))
      (ip/process-player-items-batch)
      (should @atoms/paused)
      (should= "****YOU WIN!*****" @atoms/error-message))))
