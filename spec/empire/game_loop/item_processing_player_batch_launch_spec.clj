(ns empire.game-loop-item-processing-player-batch-launch-spec
  (:require [empire.game-mechanics.containers.ops :as container-ops]
            [empire.game.loop.item-processing :as ip]
            [empire.player.attention :as attention]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [reset-all-atoms! set-test-world!]]
            [speclj.core :refer :all]))

(defn- land-cell [] {:type :land})
(defn- sea-cell [] {:type :sea})

(describe "process-player-items-batch auto-launch-fighter"
  (before (reset-all-atoms!))

  (it "launches fighter from airport with flight-path"
    (set-test-world! [[{:type :city :city-status :player
                        :flight-path [1 0]
                        :awake-fighters 1 :fighter-count 1}
                       (land-cell)]])
    (test-utils/set-test-state! :player-items [[0 0]])
    (let [launched? (atom false)]
      (with-redefs [container-ops/launch-fighter-from-airport
                    (fn [_coords _fp]
                      (reset! launched? true)
                      (test-utils/update-test-world! assoc-in [0 0 :awake-fighters] 0)
                      [1 0])
                    attention/item-needs-attention? (fn [_] false)
                    attention/set-attention-message (fn [_])]
        (ip/process-player-items-batch)
        (should @launched?))))

  (it "launches fighter from carrier with flight-path"
    (set-test-world! [[{:type :sea :contents {:type :carrier :owner :player
                                              :mode :sentry :hits 8
                                              :flight-path [1 0]
                                              :awake-fighters 1 :fighter-count 1}}
                       (sea-cell)]])
    (test-utils/set-test-state! :player-items [[0 0]])
    (let [launched? (atom false)]
      (with-redefs [container-ops/launch-fighter-from-carrier
                    (fn [_coords _fp]
                      (reset! launched? true)
                      [1 0])
                    attention/item-needs-attention? (fn [_] false)
                    attention/set-attention-message (fn [_])]
        (ip/process-player-items-batch)
        (should @launched?))))

  (it "does not launch fighter without flight-path"
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

  (it "disembarks army from transport with marching-orders"
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
                    (fn [_tc vc _mo]
                      (reset! disembarked? true)
                      vc)
                    attention/item-needs-attention? (fn [_] false)
                    attention/set-attention-message (fn [_])]
        (ip/process-player-items-batch)
        (should @disembarked?))))

  (it "does not disembark when awake-armies key is missing"
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

  (it "does not disembark from non-transport"
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

  (it "does not disembark without marching-orders"
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

  (it "only targets empty land cells"
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

  (it "requires valid-target to disembark"
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

  (it "finds land at dx=0 position"
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

  (it "finds land at dx=1 position"
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

  (it "finds land at dy=0 position"
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

  (it "finds land at dy=1 position"
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
