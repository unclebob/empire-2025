(ns empire.computer.transport-internal-spec
  "Tests for VMS Empire style computer transport movement."
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.computer.transport :as transport]
            [empire.computer.transport.mission-handlers :as mission-handlers]

            [empire.computer.land-objectives :as land-objectives]
            [empire.player.production :as player-prod]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-world! update-test-world!]]))
(describe "transport internals"
  (before (reset-all-atoms!))

  (it "process-load-for-invasion-with-armies prioritizes unload-zone transition"
    (let [called (atom nil)]
      (with-redefs [empire.computer.transport/transition-load-for-invasion-to-unloading!
                    (fn [pos target] (reset! called [:unload pos target]))
                    empire.computer.transport/transition-load-for-invasion-to-sailing!
                    (fn [_] (reset! called [:sail]))]
        (@#'transport/process-load-for-invasion-with-armies
         [2 3] {:army-count 1} [9 9] true false)
        (should= [:unload [2 3] [9 9]] @called))))

  (it "process-load-for-invasion-with-armies transitions to sailing on timeout"
    (let [called (atom nil)]
      (with-redefs [empire.computer.transport/transition-load-for-invasion-to-unloading!
                    (fn [& _] (reset! called [:unload]))
                    empire.computer.transport/transition-load-for-invasion-to-sailing!
                    (fn [pos] (reset! called [:sail pos]))]
        (@#'transport/process-load-for-invasion-with-armies
         [1 1] {:army-count 1} [4 4] false true)
        (should= [:sail [1 1]] @called))))

  (it "process-load-for-invasion-with-armies transitions to sailing when unloadable land is nearby"
    (let [called (atom nil)]
      (with-redefs [empire.computer.transport/transition-load-for-invasion-to-sailing!
                    (fn [pos] (reset! called [:sail pos]))
                    empire.computer.transport.unloading/has-nearby-unloadable-land? (fn [& _] true)]
        (@#'transport/process-load-for-invasion-with-armies
         [3 1] {:army-count 2} [4 4] false false)
        (should= [:sail [3 1]] @called))))

  (it "process-load-for-invasion-with-armies returns nil when no branch applies"
    (with-redefs [empire.computer.transport.unloading/has-nearby-unloadable-land? (fn [& _] false)]
      (should-be-nil
       (@#'transport/process-load-for-invasion-with-armies
        [0 0] {:army-count 1} [2 2] false false))))

  (it "load-for-invasion-start! stamps mission and current round"
    (test-utils/set-test-state! :round-number 12)
    (set-test-world! (build-test-map ["~t~"]))
    (@#'transport/load-for-invasion-start! [1 0])
    (let [unit (get-in (test-utils/read-test-state :game-map) [1 0 :contents])]
      (should= :load-for-invasion (:transport-mission unit))
      (should= 12 (:invasion-load-since unit))))

  (it "move-toward-position updates visibility and loads adjacent armies after a move"
    (let [calls (atom [])]
      (with-redefs [empire.computer.transport.core/get-passable-sea-neighbors (fn [_] [[2 1]])
                    empire.computer.shared.grid/move-toward (fn [pos target passable]
                                                              (swap! calls conj [:toward pos target passable])
                                                              [2 1])
                    empire.computer.shared.action-resolution/move-unit-to (fn [from to]
                                                                            (swap! calls conj [:move from to])
                                                                            true)
                    empire.game-mechanics.visibility/update-cell-visibility (fn [pos owner]
                                                                                       (swap! calls conj [:visibility pos owner]))
                    empire.computer.transport.loading/load-adjacent-armies (fn [pos]
                                                                              (swap! calls conj [:load pos]))]
        (should= [2 1] (@#'transport/move-toward-position [1 1] [4 4]))
        (should= [[:toward [1 1] [4 4] [[2 1]]]
                  [:move [1 1] [2 1]]
                  [:visibility [1 1] :computer]
                  [:visibility [2 1] :computer]
                  [:load [2 1]]]
                 @calls))))

  (it "transition-to-loading keeps a never-reload transport in sail-to-load"
    (let [updates (atom [])]
      (set-test-world! [[{:contents {:never-reload? true :unload-target-city [9 9]}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (with-redefs [empire.computer.transport.sailing/enter-sail-to-load! (fn [pos]
                                                                            (swap! updates conj [:enter pos]))]
        (@#'transport/transition-to-loading [0 0])
        (should= [:enter [0 0]] (first @updates)))))

  (it "transition-to-loading assigns nearby staging armies for the chosen load coast"
    (let [assigned (atom nil)]
      (set-test-world! [[{:contents {:type :transport
                                     :owner :computer
                                     :hits 1
                                     :transport-mission :unloading
                                     :army-count 0}}]
                       [{:type :city :city-status :computer}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (with-redefs [empire.computer.transport.sailing/enter-sail-to-load! (fn [pos]
                                                                            (reset! assigned pos))]
        (@#'transport/transition-to-loading [0 0])
        (should= [0 0] @assigned))))

  (it "loading with no crawl move transitions to sail-to-load"
    (let [called (atom nil)]
      (mission-handlers/process-loading-mission
       {:read-computer-map (constantly [[{:contents {:type :transport
                                                     :owner :computer
                                                     :transport-mission :loading
                                                     :army-count 1}}]])
        :load-adjacent-armies (fn [_] nil)
        :should-start-sailing? (fn [& _] false)
        :start-sailing (fn [& _] (reset! called :start-sailing))
        :loading-crawl-move (fn [_] nil)
        :transition-to-loading (fn [pos] (reset! called [:transition pos]))}
       [0 0])
      (should= [:transition [0 0]] @called)))

  )
