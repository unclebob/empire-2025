(ns empire.game-loop-item-processing-player-batch-flow-spec
  (:require [empire.game-mechanics.containers.ops :as container-ops]
            [empire.game-mechanics.movement.api :as movement]
            [empire.game.loop.item-processing :as ip]
            [empire.game.loop.item-processing-decisions :as decisions]
            [empire.player.attention :as attention]
            [empire.state.api :as sa]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [reset-all-atoms! set-test-world!]]
            [speclj.core :refer :all]))

(defn- land-cell [] {:type :land})

(defn- mock-move
  [result-type]
  (fn [from-coords _target cell _game-map-source]
    (let [unit (:contents cell)
          [c r] from-coords
          new-pos [(inc c) r]]
      (sa/update-world! assoc-in (conj from-coords :contents) nil)
      (sa/update-world! assoc-in (conj new-pos :contents) unit)
      {:result result-type :pos new-pos})))

(describe "process-player-items-batch auto-movement"
  (before (reset-all-atoms!))

  (it "keeps item in list when movement returns new coords"
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

  (it "removes item when movement returns nil"
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

  (it "skips satellite with target"
    (set-test-world! [[{:type :land :contents {:type :satellite :owner :player
                                               :mode :moving :target [5 0]
                                               :steps-remaining 10}}]])
    (test-utils/set-test-state! :player-items [[0 0]])
    (with-redefs [attention/item-needs-attention? (fn [_] false)
                  attention/set-attention-message (fn [_])]
      (ip/process-player-items-batch)
      (should (empty? (test-utils/read-test-state :player-items)))))

  (it "does not skip non-satellite unit with target"
    (set-test-world! [[{:type :land :contents {:type :army :owner :player
                                               :mode :awake :target [1 0]
                                               :steps-remaining 1}}]])
    (test-utils/set-test-state! :player-items [[0 0]])
    (with-redefs [attention/item-needs-attention? (fn [_] true)
                  attention/set-attention-message (fn [_])]
      (ip/process-player-items-batch)
      (should (test-utils/read-test-state :waiting-for-input))))

  (it "checks auto-launch for non-satellite"
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

  (it "sets waiting-for-input when item needs attention"
    (set-test-world! [[{:type :land :contents {:type :army :owner :player
                                               :mode :awake :steps-remaining 1}}]])
    (test-utils/set-test-state! :player-items [[0 0]])
    (with-redefs [attention/item-needs-attention? (fn [_] true)
                  attention/set-attention-message (fn [_])]
      (ip/process-player-items-batch)
      (should (test-utils/read-test-state :waiting-for-input))
      (should= [[0 0]] (test-utils/read-test-state :cells-needing-attention)))))

(describe "satellite-with-target?"
  (it "returns truthy for satellite with target"
    (should (decisions/satellite-with-target?
             {:type :satellite :target [5 0]})))

  (it "returns falsy for satellite without target"
    (should-not (decisions/satellite-with-target?
                 {:type :satellite})))

  (it "returns falsy for non-satellite with target"
    (should-not (decisions/satellite-with-target?
                 {:type :army :target [5 0]})))

  (it "returns falsy for nil unit"
    (should-not (decisions/satellite-with-target? nil))))

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
