(ns empire.computer.transport-sailing-regular-spec
  (:require [empire.computer.transport-sailing-regular :as regular]
            [empire.computer.transport-sailing-support :as support]
            [empire.test.utils :refer [reset-all-atoms! set-test-computer-map! set-test-world!]]
            [speclj.core :refer :all]))

(describe "regular transport sailing"
  (before (reset-all-atoms!))

  (it "launches from a city into the nearest open sea toward the target reference"
    (let [calls (atom [])]
      (set-test-world! [[{:type :city :contents {:pickup-continent-pos [5 5]}} {:type :sea :contents nil}]
                        [{:type :sea :contents nil} {:type :sea :contents {:owner :computer}}]])
      (set-test-computer-map! [[{:type :city :contents {:pickup-continent-pos [5 5]}} {:type :sea :contents nil}]
                               [{:type :sea :contents nil} {:type :sea :contents {:owner :computer}}]])
      (with-redefs [empire.computer.core/get-neighbors (constantly [[1 0] [0 1]])
                    empire.computer.core/chebyshev-distance (fn [a b]
                                                              (swap! calls conj [:distance a b])
                                                              ({[[1 0] [5 5]] 8
                                                                [[0 1] [5 5]] 4}
                                                               [a b]))
                    empire.computer.core/move-unit-to (fn [from to]
                                                        (swap! calls conj [:move from to])
                                                        true)
                    empire.computer.transport-sailing-support/update-cell-visibility! (fn [pos owner]
                                                                                         (swap! calls conj [:visibility pos owner]))]
        (should= [0 1] (@#'regular/launch-from-city-to-sea [0 0] {:pickup-continent-pos [5 5]}))
        (should= [:move [0 0] [0 1]] (nth @calls 2)))))

  (it "switches to unloading when land is nearby"
    (with-redefs [empire.computer.transport-unloading/has-nearby-unloadable-land? (constantly true)
                  empire.computer.transport-sailing-support/set-unloading-and-try! (fn [pos] [:unload pos])]
      (should= [:unload [2 2]]
               (@#'regular/maybe-unload-or-sail! [2 2] {:army-count 2}))))

  (it "falls back to a safe random sail path when no loaded target exists"
    (with-redefs [empire.computer.transport-unloading/has-nearby-unloadable-land? (constantly false)
                  empire.computer.transport-sailing-support/compute-sail-path (constantly nil)
                  empire.computer.transport-sailing-support/random-sail-path (constantly [[1 0] [2 0]])
                  empire.state.api/update-world! (fn [& _])
                  empire.game-mechanics.movement.visibility/sync-ai-unit-to-computer-map! (fn [& _])
                  empire.computer.core/move-unit-to (fn [_ _] true)
                  empire.computer.transport-sailing-support/update-cell-visibility! (fn [& _])
                  empire.computer.transport-unloading/try-opportunistic-unload (fn [pos] pos)]
      (should= [2 0]
               (@#'regular/maybe-unload-or-sail! [0 0] {:army-count 1}))))

  (it "does not launch from a city using sea visible only on game-map"
    (set-test-world! [[{:type :city :contents {:pickup-continent-pos [5 5]}} {:type :sea}]])
    (set-test-computer-map! [[{:type :city :contents {:pickup-continent-pos [5 5]}} nil]])
    (should-be-nil (@#'regular/launch-from-city-to-sea [0 0] {:pickup-continent-pos [5 5]})))

  (it "does not treat hidden adjacent land as a reason to unload"
    (set-test-world! [[{:type :sea :contents {:type :transport :owner :computer}}
                       {:type :land}]])
    (set-test-computer-map! [[{:type :sea :contents {:type :transport :owner :computer}}
                              nil]])
    (with-redefs [empire.computer.transport-unloading/has-nearby-unloadable-land? (constantly false)
                  empire.computer.transport-sailing-support/compute-sail-path (constantly nil)
                  empire.computer.transport-sailing-support/random-sail-path (constantly [[1 0]])
                  empire.state.api/update-world! (fn [& _])
                  empire.game-mechanics.movement.visibility/sync-ai-unit-to-computer-map! (fn [& _])
                  empire.computer.core/move-unit-to (fn [_ _] true)
                  empire.computer.transport-sailing-support/update-cell-visibility! (fn [& _])
                  empire.computer.transport-unloading/try-opportunistic-unload (fn [pos] pos)]
      (should= [1 0]
               (@#'regular/maybe-unload-or-sail! [0 0] {:army-count 1}))))

  (it "dispatches process-sailing-mission through the selected mission handler"
    (set-test-world! [[{:contents {:sail-path [[1 0]] :army-count 1 :never-reload? false}}]])
    (set-test-computer-map! [[{:contents {:sail-path [[1 0]] :army-count 1 :never-reload? false}}]])
    (with-redefs [empire.computer.transport-sailing-decisions/sailing-action (fn [_ _ _] {:action :follow-path})
                  empire.computer.transport-sailing-regular/follow-path-action (fn [pos sail-path] [:follow pos sail-path])]
      (should= [:follow [0 0] [[1 0]]]
               (regular/process-sailing-mission [0 0]))))

  (it "ignores enemy ships near target that are visible only on game-map"
    (set-test-world! [[{:type :sea} {:type :sea}]
                      [{:type :sea} {:type :sea :contents {:type :destroyer :owner :player}}]])
    (set-test-computer-map! [[{:type :sea} {:type :sea}]
                             [{:type :sea} nil]])
    (should-not (support/enemy-ship-near-target? [0 0] 2))))
