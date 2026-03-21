(ns empire.computer.transport.sailing-regular-spec
  (:require [empire.computer.transport.sailing-regular :as regular]
            [empire.computer.transport.sailing-support :as support]
            [empire.test.utils :as test-utils]
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
      (with-redefs [empire.computer.shared.world-query/get-neighbors (constantly [[1 0] [0 1]])
                    empire.computer.shared.grid/chebyshev-distance (fn [a b]
                                                                     (swap! calls conj [:distance a b])
                                                                     ({[[1 0] [5 5]] 8
                                                                       [[0 1] [5 5]] 4}
                                                                      [a b]))
                    empire.computer.shared.action-resolution/move-unit-to (fn [from to]
                                                                            (swap! calls conj [:move from to])
                                                                            true)
                    empire.computer.transport.sailing-support/update-cell-visibility! (fn [pos owner]
                                                                                         (swap! calls conj [:visibility pos owner]))]
        (should= [0 1] (@#'regular/launch-from-city-to-sea [0 0] {:pickup-continent-pos [5 5]}))
        (should= [:move [0 0] [0 1]] (nth @calls 2)))))

  (it "leave-city moves a new transport out of the city and plans sail-to-load"
    (set-test-world! [[{:type :city
                        :city-status :computer
                        :contents {:type :transport
                                   :owner :computer
                                   :transport-id 7
                                   :transport-mission :leave-city
                                   :army-count 0}}
                       {:type :sea :contents nil}]])
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (with-redefs [empire.computer.transport.load-targeting/choose-load-target-cell (fn [& _] [3 0])
                  empire.computer.transport.load-targeting/path-to-load-target (fn [& _] [[1 0] [2 0] [3 0]])
                  empire.computer.army.assignment/assign-returning-transport-staging-at! (fn
                                                                                           ([_] [41 42])
                                                                                           ([_ _] [41 42]))]
      (regular/process-sailing-mission [0 0]))
    (let [transport (get-in (test-utils/read-test-state :game-map) [0 1 :contents])]
      (should= :transport (:type transport))
      (should= :sail-to-load (:transport-mission transport))
      (should= [3 0] (:load-target-cell transport))
      (should= [[1 0] [2 0] [3 0]] (:sail-path transport))
      (should= [41 42] (:load-manifest transport)))))

  (it "records the load-plan failure when a transport cannot find a target, manifest, or path"
    (set-test-world! [[{:type :sea
                        :contents {:type :transport
                                   :owner :computer
                                   :transport-id 7
                                   :transport-mission :hold-sail-to-load
                                   :army-count 0}}]])
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (with-redefs [empire.computer.transport.load-targeting/choose-load-target-cell (fn [& _] nil)
                  empire.computer.transport.sailing-support/compute-sail-to-load-path (fn [_] [])
                  empire.computer.army.assignment/assign-returning-transport-staging-at! (fn
                                                                                           ([_] [])
                                                                                           ([_ _] []))]
      (regular/enter-sail-to-load! [0 0]))
    (let [transport (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
      (should= :hold-sail-to-load (:transport-mission transport))
      (should= {:round 0
                :at [0 0]
                :reasons [:no-load-target :no-manifest :no-sail-path]
                :load-target-cell nil
                :manifest-count 0
                :sail-path-length 0}
               (:load-plan-failure transport))))

  (it "accepts an empty sail path when already adjacent to the chosen load target"
    (set-test-world! [[{:type :sea
                        :contents {:type :transport
                                   :owner :computer
                                   :transport-id 7
                                   :transport-mission :hold-sail-to-load
                                   :army-count 0}}
                       {:type :land :country-id 1}]])
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (with-redefs [empire.computer.transport.load-targeting/choose-load-target-cell (fn [& _] [0 1])
                  empire.computer.transport.load-targeting/path-to-load-target (fn [& _] [])
                  empire.computer.army.assignment/assign-returning-transport-staging-at! (fn [_ _] [41 42])]
      (regular/enter-sail-to-load! [0 0]))
    (let [transport (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
      (should= :sail-to-load (:transport-mission transport))
      (should= [0 1] (:load-target-cell transport))
      (should= [] (:sail-path transport))
      (should= [41 42] (:load-manifest transport))
      (should-be-nil (:load-plan-failure transport))))

  (it "switches to unloading when adjacent unclaimed land exists"
    (set-test-world! [[{:type :sea :contents {:type :transport :owner :computer
                                              :transport-mission :sail-to-unload
                                              :army-count 2}}
                       {:type :land}]])
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (with-redefs [empire.computer.transport.unloading/try-opportunistic-unload (fn [_] true)
                  empire.computer.transport.core/set-transport-mission (fn [pos mission] [pos mission])]
      (should= [[0 0] :unloading]
               (regular/process-sailing-mission [0 0]))))

  (it "recomputes an unload path when no loaded target is adjacent"
    (set-test-world! [[{:type :sea :contents {:type :transport :owner :computer
                                              :transport-mission :sail-to-unload
                                              :army-count 1
                                              :sail-path []}}]])
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (with-redefs [empire.computer.transport.unloading/try-opportunistic-unload (constantly false)
                  empire.computer.transport.sailing-support/compute-sail-to-unload-path (constantly [[1 0] [2 0]])
                  empire.state.api/update-world! (fn [& _])
                  empire.game-mechanics.visibility/sync-ai-unit-to-computer-map! (fn [& _])
                  empire.computer.shared.action-resolution/move-unit-to (fn [_ _] true)
                  empire.computer.transport.sailing-support/update-cell-visibility! (fn [& _])
                  empire.computer.transport.unloading/try-opportunistic-unload (fn [_] false)]
      (should= [1 0]
               (regular/process-sailing-mission [0 0]))))

  (it "does not launch from a city using sea visible only on game-map"
    (set-test-world! [[{:type :city :contents {:pickup-continent-pos [5 5]}} {:type :sea}]])
    (set-test-computer-map! [[{:type :city :contents {:pickup-continent-pos [5 5]}} nil]])
    (should-be-nil (@#'regular/launch-from-city-to-sea [0 0] {:pickup-continent-pos [5 5]})))

  (it "does not treat hidden adjacent land as a reason to unload"
    (set-test-world! [[{:type :sea :contents {:type :transport :owner :computer
                                              :transport-mission :sail-to-unload
                                              :army-count 1
                                              :sail-path []}}
                       {:type :land}]])
    (set-test-computer-map! [[{:type :sea :contents {:type :transport :owner :computer
                                                      :transport-mission :sail-to-unload
                                                      :army-count 1
                                                      :sail-path []}}
                              nil]])
    (with-redefs [empire.computer.transport.unloading/try-opportunistic-unload (constantly false)
                  empire.computer.transport.sailing-support/compute-sail-to-unload-path (constantly [[1 0]])
                  empire.state.api/update-world! (fn [& _])
                  empire.game-mechanics.visibility/sync-ai-unit-to-computer-map! (fn [& _])
                  empire.computer.shared.action-resolution/move-unit-to (fn [_ _] true)
                  empire.computer.transport.sailing-support/update-cell-visibility! (fn [& _])]
      (should= [1 0]
               (regular/process-sailing-mission [0 0]))))

  (it "treats adjacent computer cities as claimed land and keeps sailing"
    (set-test-world! [[{:type :sea :contents {:type :transport :owner :computer
                                              :transport-mission :sail-to-unload
                                              :army-count 4
                                              :sail-path []}}
                       {:type :city :city-status :computer}]])
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (with-redefs [empire.computer.transport.unloading/try-opportunistic-unload (constantly false)
                  empire.computer.transport.sailing-support/compute-sail-to-unload-path (constantly [[1 0]])
                  empire.state.api/update-world! (fn [& _])
                  empire.game-mechanics.visibility/sync-ai-unit-to-computer-map! (fn [& _])
                  empire.computer.shared.action-resolution/move-unit-to (fn [_ _] true)
                  empire.computer.transport.sailing-support/update-cell-visibility! (fn [& _])]
      (should= [1 0]
               (regular/process-sailing-mission [0 0]))))

  (it "keeps sailing when no loaded target exists"
    (set-test-world! [[{:contents {:transport-mission :sail-to-unload :sail-path [] :army-count 1 :never-reload? false}}]])
    (set-test-computer-map! [[{:contents {:transport-mission :sail-to-unload :sail-path [] :army-count 1 :never-reload? false}}]])
    (with-redefs [empire.computer.transport.unloading/try-opportunistic-unload (constantly false)
                  empire.computer.transport.sailing-support/compute-sail-to-unload-path (constantly nil)]
      (should-be-nil
       (regular/process-sailing-mission [0 0]))))

  (it "dispatches process-sailing-mission through the selected mission handler"
    (set-test-world! [[{:contents {:transport-mission :sail-to-unload :sail-path [[1 0]] :army-count 1 :never-reload? false}}]])
    (set-test-computer-map! [[{:contents {:transport-mission :sail-to-unload :sail-path [[1 0]] :army-count 1 :never-reload? false}}]])
    (with-redefs [empire.computer.transport.unloading/try-opportunistic-unload (constantly false)
                  empire.computer.shared.action-resolution/move-unit-to (fn [_ _] true)
                  empire.computer.transport.sailing-support/update-cell-visibility! (fn [& _])
                  empire.game-mechanics.visibility/sync-ai-unit-to-computer-map! (fn [& _])
                  empire.state.api/update-world! (fn [& _])]
      (should= [1 0]
               (regular/process-sailing-mission [0 0]))))

  (it "processes loaded-no-path without crashing when no target exists"
    (set-test-world! [[{:contents {:transport-mission :sail-to-unload :sail-path [] :army-count 1 :never-reload? false}}]])
    (set-test-computer-map! [[{:contents {:transport-mission :sail-to-unload :sail-path [] :army-count 1 :never-reload? false}}]])
    (with-redefs [empire.computer.transport.unloading/try-opportunistic-unload (constantly false)
                  empire.computer.transport.sailing-support/compute-sail-to-unload-path (constantly nil)]
      (should-be-nil
       (regular/process-sailing-mission [0 0]))))

  (it "ignores enemy ships near target that are visible only on game-map"
    (set-test-world! [[{:type :sea} {:type :sea}]
                      [{:type :sea} {:type :sea :contents {:type :destroyer :owner :player}}]])
    (set-test-computer-map! [[{:type :sea} {:type :sea}]
                             [{:type :sea} nil]])
    (should-not (support/enemy-ship-near-target? [0 0] 2)))
