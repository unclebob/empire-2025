(ns empire.game-loop-item-processing-player-batch-spec
  (:require [empire.game-mechanics.containers.ops :as container-ops]
            [empire.game-mechanics.movement.api :as movement]
            [empire.game.loop.item-processing :as ip]
            [empire.player.attention :as attention]
            [empire.state.api :as sa]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-world! update-test-world!]]
            [speclj.core :refer :all]))

(defn- land-cell [] {:type :land})
(defn- sea-cell [] {:type :sea})

(defn- make-land-map [n]
  (vec (repeat n (vec (repeat n (land-cell))))))

(defn- mock-move
  [result-type]
  (fn [from-coords _target cell _game-map-source]
    (let [unit (:contents cell)
          [c r] from-coords
          new-pos [(inc c) r]]
      (sa/update-world! assoc-in (conj from-coords :contents) nil)
      (sa/update-world! assoc-in (conj new-pos :contents) unit)
      {:result result-type :pos new-pos})))

(describe "process-player-items-batch auto-launch-fighter"
  (before (reset-all-atoms!))

  (it "launches fighter from airport with flight-path (L113)"
    (set-test-world! [[{:type :city :city-status :player
                        :flight-path [1 0]
                        :awake-fighters 1 :fighter-count 1}
                       (land-cell)]])
    (test-utils/set-test-state! :player-items [[0 0]])
    (let [launched? (atom false)]
      (with-redefs [container-ops/launch-fighter-from-airport
                    (fn [coords fp]
                      (reset! launched? true)
                      (test-utils/update-test-world! assoc-in [0 0 :awake-fighters] 0)
                      [1 0])
                    attention/item-needs-attention? (fn [_] false)
                    attention/set-attention-message (fn [_])]
        (ip/process-player-items-batch)
        (should @launched?))))

  (it "launches fighter from carrier with flight-path (L111)"
    (set-test-world! [[{:type :sea :contents {:type :carrier :owner :player
                                              :mode :sentry :hits 8
                                              :flight-path [1 0]
                                              :awake-fighters 1 :fighter-count 1}}
                       (sea-cell)]])
    (test-utils/set-test-state! :player-items [[0 0]])
    (let [launched? (atom false)]
      (with-redefs [container-ops/launch-fighter-from-carrier
                    (fn [coords fp]
                      (reset! launched? true)
                      [1 0])
                    attention/item-needs-attention? (fn [_] false)
                    attention/set-attention-message (fn [_])]
        (ip/process-player-items-batch)
        (should @launched?))))

  (it "does not launch fighter without flight-path (L113 when→when-not)"
    (set-test-world! [[{:type :city :city-status :player
                        :awake-fighters 1 :fighter-count 1}]])
    (test-utils/set-test-state! :player-items [[0 0]])
    (let [launched? (atom false)]
      (with-redefs [container-ops/launch-fighter-from-airport
                    (fn [_ _] (reset! launched? true) [0 0])
                    attention/item-needs-attention? (fn [_] false)
                    attention/set-attention-message (fn [_])]
        (ip/process-player-items-batch)
        (should-not @launched?)))))

(describe "process-player-items-batch auto-disembark-army"
  (before (reset-all-atoms!))

  (it "disembarks army from transport with marching-orders (L128, L129, L130)"
    (set-test-world! [[(sea-cell) (sea-cell) (sea-cell)]
                      [{:type :land}
                       {:type :sea :contents {:type :transport :owner :player
                                              :mode :sentry :hits 3
                                              :marching-orders [0 0]
                                              :awake-armies 1 :army-count 1}}
                       (sea-cell)]
                      [(sea-cell) (sea-cell) (sea-cell)]])
    (test-utils/set-test-state! :player-items [[1 1]])
    (let [disembarked? (atom false)]
      (with-redefs [container-ops/disembark-army-with-target
                    (fn [tc vc mo]
                      (reset! disembarked? true)
                      vc)
                    attention/item-needs-attention? (fn [_] false)
                    attention/set-attention-message (fn [_])]
        (ip/process-player-items-batch)
        (should @disembarked?))))

  (it "does not disembark when awake-armies key is missing (L129 0→1)"
    (set-test-world! [[(sea-cell) (sea-cell)]
                      [{:type :land}
                       {:type :sea :contents {:type :transport :owner :player
                                              :mode :sentry :hits 3
                                              :marching-orders [0 0]
                                              :army-count 0}}]])
    (test-utils/set-test-state! :player-items [[1 1]])
    (let [disembarked? (atom false)]
      (with-redefs [container-ops/disembark-army-with-target
                    (fn [_ _ _] (reset! disembarked? true) [0 0])
                    attention/item-needs-attention? (fn [_] false)
                    attention/set-attention-message (fn [_])]
        (ip/process-player-items-batch)
        (should-not @disembarked?))))

  (it "does not disembark from non-transport (L128 =→not=)"
    (set-test-world! [[{:type :sea :contents {:type :destroyer :owner :player
                                              :mode :sentry :hits 3
                                              :marching-orders [0 0]
                                              :awake-armies 1}}]])
    (test-utils/set-test-state! :player-items [[0 0]])
    (let [disembarked? (atom false)]
      (with-redefs [container-ops/disembark-army-with-target
                    (fn [_ _ _] (reset! disembarked? true) [0 0])
                    attention/item-needs-attention? (fn [_] false)
                    attention/set-attention-message (fn [_])]
        (ip/process-player-items-batch)
        (should-not @disembarked?))))

  (it "does not disembark without marching-orders (L130 when→when-not)"
    (set-test-world! [[(sea-cell) (sea-cell)]
                      [{:type :land}
                       {:type :sea :contents {:type :transport :owner :player
                                              :mode :sentry :hits 3
                                              :awake-armies 1 :army-count 1}}]])
    (test-utils/set-test-state! :player-items [[1 1]])
    (let [disembarked? (atom false)]
      (with-redefs [container-ops/disembark-army-with-target
                    (fn [_ _ _] (reset! disembarked? true) [0 0])
                    attention/item-needs-attention? (fn [_] false)
                    attention/set-attention-message (fn [_])]
        (ip/process-player-items-batch)
        (should-not @disembarked?))))

  (it "only targets empty land cells (L138 =→not=)"
    (set-test-world! [[(sea-cell) (sea-cell) (sea-cell)]
                      [(sea-cell)
                       {:type :sea :contents {:type :transport :owner :player
                                              :mode :sentry :hits 3
                                              :marching-orders [0 0]
                                              :awake-armies 1 :army-count 1}}
                       (sea-cell)]
                      [(sea-cell) (sea-cell) (sea-cell)]])
    (test-utils/set-test-state! :player-items [[1 1]])
    (let [disembarked? (atom false)]
      (with-redefs [container-ops/disembark-army-with-target
                    (fn [_ _ _] (reset! disembarked? true) [0 0])
                    attention/item-needs-attention? (fn [_] false)
                    attention/set-attention-message (fn [_])]
        (ip/process-player-items-batch)
        (should-not @disembarked?))))

  (it "requires valid-target to disembark (L141 when→when-not)"
    (set-test-world! [[(sea-cell) (sea-cell) (sea-cell)]
                      [{:type :land :contents {:type :army :owner :player :hits 1}}
                       {:type :sea :contents {:type :transport :owner :player
                                              :mode :sentry :hits 3
                                              :marching-orders [0 0]
                                              :awake-armies 1 :army-count 1}}
                       (sea-cell)]
                      [(sea-cell) (sea-cell) (sea-cell)]])
    (test-utils/set-test-state! :player-items [[1 1]])
    (let [disembarked? (atom false)]
      (with-redefs [container-ops/disembark-army-with-target
                    (fn [_ _ _] (reset! disembarked? true) [0 0])
                    attention/item-needs-attention? (fn [_] false)
                    attention/set-attention-message (fn [_])]
        (ip/process-player-items-batch)
        (should-not @disembarked?))))

  (it "finds land at dx=0 position (L132 dx 0→1)"
    (set-test-world! [[(sea-cell) (sea-cell) (sea-cell)]
                      [{:type :land}
                       {:type :sea :contents {:type :transport :owner :player
                                              :mode :sentry :hits 3
                                              :marching-orders [0 0]
                                              :awake-armies 1 :army-count 1}}
                       (sea-cell)]
                      [(sea-cell) (sea-cell) (sea-cell)]])
    (test-utils/set-test-state! :player-items [[1 1]])
    (let [target-pos (atom nil)]
      (with-redefs [container-ops/disembark-army-with-target
                    (fn [_tc vc _mo] (reset! target-pos vc) vc)
                    attention/item-needs-attention? (fn [_] false)
                    attention/set-attention-message (fn [_])]
        (ip/process-player-items-batch)
        (should= [1 0] @target-pos))))

  (it "finds land at dx=1 position (L132 dx 1→0)"
    (set-test-world! [[(sea-cell) (sea-cell) (sea-cell)]
                      [(sea-cell)
                       {:type :sea :contents {:type :transport :owner :player
                                              :mode :sentry :hits 3
                                              :marching-orders [0 0]
                                              :awake-armies 1 :army-count 1}}
                       (sea-cell)]
                      [{:type :land} (sea-cell) (sea-cell)]])
    (test-utils/set-test-state! :player-items [[1 1]])
    (let [target-pos (atom nil)]
      (with-redefs [container-ops/disembark-army-with-target
                    (fn [_tc vc _mo] (reset! target-pos vc) vc)
                    attention/item-needs-attention? (fn [_] false)
                    attention/set-attention-message (fn [_])]
        (ip/process-player-items-batch)
        (should= [2 0] @target-pos))))

  (it "finds land at dy=0 position (L132 dy 0→1)"
    (set-test-world! [[(sea-cell) {:type :land} (sea-cell)]
                      [(sea-cell)
                       {:type :sea :contents {:type :transport :owner :player
                                              :mode :sentry :hits 3
                                              :marching-orders [0 0]
                                              :awake-armies 1 :army-count 1}}
                       (sea-cell)]
                      [(sea-cell) (sea-cell) (sea-cell)]])
    (test-utils/set-test-state! :player-items [[1 1]])
    (let [target-pos (atom nil)]
      (with-redefs [container-ops/disembark-army-with-target
                    (fn [_tc vc _mo] (reset! target-pos vc) vc)
                    attention/item-needs-attention? (fn [_] false)
                    attention/set-attention-message (fn [_])]
        (ip/process-player-items-batch)
        (should= [0 1] @target-pos))))

  (it "finds land at dy=1 position (L132 dy 1→0)"
    (set-test-world! [[(sea-cell) (sea-cell) {:type :land}]
                      [(sea-cell)
                       {:type :sea :contents {:type :transport :owner :player
                                              :mode :sentry :hits 3
                                              :marching-orders [0 0]
                                              :awake-armies 1 :army-count 1}}
                       (sea-cell)]
                      [(sea-cell) (sea-cell) (sea-cell)]])
    (test-utils/set-test-state! :player-items [[1 1]])
    (let [target-pos (atom nil)]
      (with-redefs [container-ops/disembark-army-with-target
                    (fn [_tc vc _mo] (reset! target-pos vc) vc)
                    attention/item-needs-attention? (fn [_] false)
                    attention/set-attention-message (fn [_])]
        (ip/process-player-items-batch)
        (should= [0 2] @target-pos)))))

(describe "process-player-items-batch auto-movement"
  (before (reset-all-atoms!))

  (it "keeps item in list when movement returns new coords (L150 if→if-not)"
    (set-test-world! [[{:type :land :contents {:type :army :owner :player
                                               :mode :moving :target [2 0]
                                               :steps-remaining 3}}
                       (land-cell) (land-cell)]])
    (test-utils/set-test-state! :player-items [[0 0]])
    (with-redefs [movement/move-unit (mock-move :normal)
                  attention/item-needs-attention? (fn [_] false)
                  attention/set-attention-message (fn [_])]
      (ip/process-player-items-batch)
      (should-not (test-utils/read-test-state :waiting-for-input))))

  (it "removes item when movement returns nil (L150)"
    (set-test-world! [[{:type :land :contents {:type :army :owner :player
                                               :mode :moving :target [1 0]
                                               :steps-remaining 1}}
                       (land-cell)]])
    (test-utils/set-test-state! :player-items [[0 0]])
    (with-redefs [movement/move-unit (mock-move :normal)
                  attention/item-needs-attention? (fn [_] false)
                  attention/set-attention-message (fn [_])]
      (ip/process-player-items-batch)
      (should (empty? (test-utils/read-test-state :player-items))))))

(describe "process-player-items-batch satellite handling"
  (before (reset-all-atoms!))

  (it "skips satellite with target (L161)"
    (set-test-world! [[{:type :land :contents {:type :satellite :owner :player
                                               :mode :moving :target [5 0]
                                               :steps-remaining 10}}]])
    (test-utils/set-test-state! :player-items [[0 0]])
    (with-redefs [attention/item-needs-attention? (fn [_] false)
                  attention/set-attention-message (fn [_])]
      (ip/process-player-items-batch)
      (should (empty? (test-utils/read-test-state :player-items)))))

  (it "does not skip non-satellite unit with target (L161 =→not=)"
    (set-test-world! [[{:type :land :contents {:type :army :owner :player
                                               :mode :awake :target [1 0]
                                               :steps-remaining 1}}]])
    (test-utils/set-test-state! :player-items [[0 0]])
    (with-redefs [attention/item-needs-attention? (fn [_] true)
                  attention/set-attention-message (fn [_])]
      (ip/process-player-items-batch)
      (should (test-utils/read-test-state :waiting-for-input))))

  (it "checks auto-launch for non-satellite (L163 when-not→when)"
    (set-test-world! [[{:type :city :city-status :player
                        :flight-path [1 0]
                        :awake-fighters 1 :fighter-count 1}
                       (land-cell)]])
    (test-utils/set-test-state! :player-items [[0 0]])
    (let [launched? (atom false)]
      (with-redefs [container-ops/launch-fighter-from-airport
                    (fn [_ _]
                      (reset! launched? true)
                      (test-utils/update-test-world! assoc-in [0 0 :awake-fighters] 0)
                      [1 0])
                    attention/item-needs-attention? (fn [_] false)
                    attention/set-attention-message (fn [_])]
        (ip/process-player-items-batch)
        (should @launched?)))))

(describe "process-player-items-batch attention"
  (before (reset-all-atoms!))

  (it "sets waiting-for-input when item needs attention (L180)"
    (set-test-world! [[{:type :land :contents {:type :army :owner :player
                                               :mode :awake :steps-remaining 1}}]])
    (test-utils/set-test-state! :player-items [[0 0]])
    (with-redefs [attention/item-needs-attention? (fn [_] true)
                  attention/set-attention-message (fn [_])]
      (ip/process-player-items-batch)
      (should (test-utils/read-test-state :waiting-for-input))
      (should= [[0 0]] (test-utils/read-test-state :cells-needing-attention)))))

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

(describe "satellite-with-target?"
  (it "returns truthy for satellite with target"
    (should (#'empire.game.loop.item-processing/satellite-with-target?
             {:type :satellite :target [5 0]})))

  (it "returns falsy for satellite without target"
    (should-not (#'empire.game.loop.item-processing/satellite-with-target?
                 {:type :satellite})))

  (it "returns falsy for non-satellite with target"
    (should-not (#'empire.game.loop.item-processing/satellite-with-target?
                 {:type :army :target [5 0]})))

  (it "returns falsy for nil unit"
    (should-not (#'empire.game.loop.item-processing/satellite-with-target? nil))))

(describe "process-player-items-batch else branch"
  (before (reset-all-atoms!))

  (it "unit with nil mode falls through to else (process-auto-movement)"
    (set-test-world! [[{:type :land :contents {:type :army :owner :player
                                               :mode nil :hits 1}}]])
    (test-utils/set-test-state! :player-items [[0 0]])
    (with-redefs [attention/item-needs-attention? (fn [_] false)
                  attention/set-attention-message (fn [_])]
      (ip/process-player-items-batch)
      (should (empty? (test-utils/read-test-state :player-items))))))

(describe "process-player-items-batch raw coord queue"
  (before (reset-all-atoms!))

  (it "normalizes a flat [x y] player-items queue before processing"
    (set-test-world! [[{:type :land :contents {:type :army :owner :player
                                               :mode :awake :hits 1}}]])
    (test-utils/set-test-state! :player-items [0 0])
    (with-redefs [attention/item-needs-attention? (fn [_] true)
                  attention/set-attention-message (fn [_] nil)]
      (ip/process-player-items-batch)
      (should= [[0 0]] (test-utils/read-test-state :cells-needing-attention))
      (should= true (test-utils/read-test-state :waiting-for-input)))))

(describe "process-player-items-batch loop (mutation coverage)"
  (before (reset-all-atoms!))

  (it "processes items counting from 0 (L219 0→1)"
    (let [processed (atom 0)]
      (set-test-world! (make-land-map 15))
      (test-utils/set-test-state! :player-items (vec (for [c (range 10) r (range 10)] [c r])))
      (with-redefs [attention/item-needs-attention? (fn [_] false)
                    attention/set-attention-message (fn [_])]
        (ip/process-player-items-batch)
        (should (empty? (test-utils/read-test-state :player-items))))))

  (it "stops at batch limit of 100 (L224 >=→>)"
    (let [n 5]
      (set-test-world! (make-land-map n))
      (test-utils/set-test-state! :player-items (vec (for [_ (range 6) c (range n) r (range n)] [c r])))
      (should= 150 (count (test-utils/read-test-state :player-items)))
      (with-redefs [attention/item-needs-attention? (fn [_] false)
                    attention/set-attention-message (fn [_])]
        (ip/process-player-items-batch)
        (should= 50 (count (test-utils/read-test-state :player-items))))))

  (it "increments processed counter on :done (L230 inc→dec)"
    (let [call-count (atom 0)]
      (set-test-world! (vec (repeat 110 [(land-cell)])))
      (update-test-world! assoc-in [0 0 :contents]
                          {:type :army :owner :player :mode :moving :target [109 0] :steps-remaining 200})
      (test-utils/set-test-state! :player-items [[0 0]])
      (with-redefs [movement/move-unit
                    (fn [from _t cell gm]
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
