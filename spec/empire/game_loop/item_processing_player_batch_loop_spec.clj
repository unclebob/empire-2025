(ns empire.game-loop-item-processing-player-batch-loop-spec
  (:require [empire.game-mechanics.movement.api :as movement]
            [empire.game.loop.item-processing :as ip]
            [empire.player.attention :as attention]
            [empire.state.api :as sa]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [reset-all-atoms! set-test-world! update-test-world!]]
            [speclj.core :refer :all]))

(defn- land-cell [] {:type :land})

(defn- make-land-map [n]
  (vec (repeat n (vec (repeat n (land-cell))))))

(describe "process-player-items-batch"
  (before (reset-all-atoms!))

  (it "stops when player-items is empty"
    (test-utils/set-test-state! :player-items '())
    (set-test-world! (make-land-map 3))
    (ip/process-player-items-batch)
    (should= '() (test-utils/read-test-state :player-items)))

  (it "stops when paused (victory declared)"
    (set-test-world! (make-land-map 3))
    (test-utils/set-test-state! :player-items (list [0 0] [1 1]))
    (test-utils/set-test-state! :paused true)
    (ip/process-player-items-batch)
    (should= 2 (count (test-utils/read-test-state :player-items))))

  (it "stops when waiting-for-input is set"
    (set-test-world! (make-land-map 3))
    (test-utils/set-test-state! :player-items (list [0 0] [1 1]))
    (test-utils/set-test-state! :waiting-for-input true)
    (ip/process-player-items-batch)
    (should= 2 (count (test-utils/read-test-state :player-items))))

  (it "stops after processing 100 items"
    (set-test-world! (make-land-map 5))
    (let [coords (for [c (range 5) r (range 5)] [c r])]
      (test-utils/set-test-state! :player-items (apply list (apply concat (repeat 6 coords)))))
    (should= 150 (count (test-utils/read-test-state :player-items)))
    (ip/process-player-items-batch)
    (should= 50 (count (test-utils/read-test-state :player-items))))

  (it "stops when process-one-item returns :waiting (awake unit needs attention)"
    (let [game-map (make-land-map 3)
          unit {:type :army :owner :player :mode :awake :hits 1}
          game-map (assoc-in game-map [1 1 :contents] unit)]
      (set-test-world! game-map)
      (test-utils/set-test-state! :player-items (list [1 1] [0 0]))
      (ip/process-player-items-batch)
      (should (test-utils/read-test-state :waiting-for-input))
      (should= [[1 1]] (test-utils/read-test-state :cells-needing-attention))))

  (it "does not declare resignation when computer has no cities left"
    (let [game-map (make-land-map 3)]
      (set-test-world! game-map)
      (test-utils/set-test-state! :game-over-check-enabled true)
      (test-utils/set-test-state! :player-items (list [0 0] [1 1]))
      (ip/process-player-items-batch)
      (should-not (test-utils/read-test-state :paused)))))

(describe "process-player-items-batch loop (mutation coverage)"
  (before (reset-all-atoms!))

  (it "processes items counting from 0"
    (set-test-world! (make-land-map 15))
    (test-utils/set-test-state! :player-items (vec (for [c (range 10) r (range 10)] [c r])))
    (with-redefs [attention/item-needs-attention? (fn [_] false)
                  attention/set-attention-message (fn [_])]
      (ip/process-player-items-batch)
      (should (empty? (test-utils/read-test-state :player-items)))))

  (it "stops at batch limit of 100"
    (let [n 5]
      (set-test-world! (make-land-map n))
      (test-utils/set-test-state! :player-items (vec (for [_ (range 6) c (range n) r (range n)] [c r])))
      (should= 150 (count (test-utils/read-test-state :player-items)))
      (with-redefs [attention/item-needs-attention? (fn [_] false)
                    attention/set-attention-message (fn [_])]
        (ip/process-player-items-batch)
        (should= 50 (count (test-utils/read-test-state :player-items))))))

  (it "increments processed counter on :done"
    (let [call-count (atom 0)]
      (set-test-world! (vec (repeat 110 [(land-cell)])))
      (update-test-world! assoc-in [0 0 :contents]
                          {:type :army :owner :player :mode :moving :target [109 0] :steps-remaining 200})
      (test-utils/set-test-state! :player-items [[0 0]])
      (with-redefs [movement/move-unit
                    (fn [from _t cell _gm]
                      (swap! call-count inc)
                      (let [unit (:contents cell)
                            [c r] from
                            new-pos [(inc c) r]]
                        (sa/update-world! assoc-in (conj from :contents) nil)
                        (sa/update-world! assoc-in (conj new-pos :contents) unit)
                        {:result :normal :pos new-pos}))
                    attention/item-needs-attention? (fn [_] false)
                    attention/set-attention-message (fn [_])]
        (ip/process-player-items-batch)
        (should (<= @call-count 100)))))

  (it "does not pause when no city conquest occurred mid-batch"
    (test-utils/set-test-state! :game-over-check-enabled true)
    (set-test-world! [[{:type :land} {:type :land}]])
    (test-utils/set-test-state! :player-items [[0 0] [1 0]])
    (with-redefs [attention/item-needs-attention? (fn [_] false)
                  attention/set-attention-message (fn [_])]
      (ip/process-player-items-batch)
      (should-not (test-utils/read-test-state :paused)))))
