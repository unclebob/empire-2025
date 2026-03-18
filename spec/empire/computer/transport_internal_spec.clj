(ns empire.computer.transport-internal-spec
  "Tests for VMS Empire style computer transport movement."
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.computer.transport :as transport]

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
                    empire.computer.transport-unloading/has-nearby-unloadable-land? (fn [& _] true)]
        (@#'transport/process-load-for-invasion-with-armies
         [3 1] {:army-count 2} [4 4] false false)
        (should= [:sail [3 1]] @called))))

  (it "process-load-for-invasion-with-armies returns nil when no branch applies"
    (with-redefs [empire.computer.transport-unloading/has-nearby-unloadable-land? (fn [& _] false)]
      (should-be-nil
       (@#'transport/process-load-for-invasion-with-armies
        [0 0] {:army-count 1} [2 2] false false))))

  (it "passable-sea-cell? accepts empty sea and friendly-occupied sea only"
    (should (@#'transport/passable-sea-cell? {:type :sea :contents nil}))
    (should (@#'transport/passable-sea-cell? {:type :sea :contents {:owner :computer}}))
    (should-not (@#'transport/passable-sea-cell? {:type :sea :contents {:owner :player}}))
    (should-not (@#'transport/passable-sea-cell? {:type :land :contents nil})))

  (it "load-for-invasion-start! stamps mission and current round"
    (test-utils/set-test-state! :round-number 12)
    (set-test-world! (build-test-map ["~t~"]))
    (@#'transport/load-for-invasion-start! [1 0])
    (let [unit (get-in (test-utils/read-test-state :game-map) [1 0 :contents])]
      (should= :load-for-invasion (:transport-mission unit))
      (should= 12 (:invasion-load-since unit))))

  (it "sea-load-points returns passable sea adjacent to computer armies"
    (set-test-world! (build-test-map ["a~~"
                                      "~~~"
                                      "~~~"]))
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (let [points (set (@#'transport/sea-load-points))]
      (should (contains? points [1 0]))
      (should (contains? points [0 1]))
      (should-not (contains? points [2 2]))))

  (it "move-toward-position updates visibility and loads adjacent armies after a move"
    (let [calls (atom [])]
      (with-redefs [empire.computer.transport-core/get-passable-sea-neighbors (fn [_] [[2 1]])
                    empire.computer.core/move-toward (fn [pos target passable]
                                                       (swap! calls conj [:toward pos target passable])
                                                       [2 1])
                    empire.computer.core/move-unit-to (fn [from to]
                                                        (swap! calls conj [:move from to])
                                                        true)
                    empire.game-mechanics.movement.visibility/update-cell-visibility (fn [pos owner]
                                                                                       (swap! calls conj [:visibility pos owner]))
                    empire.computer.transport-loading/load-adjacent-armies (fn [pos]
                                                                              (swap! calls conj [:load pos]))]
        (should= [2 1] (@#'transport/move-toward-position [1 1] [4 4]))
        (should= [[:toward [1 1] [4 4] [[2 1]]]
                  [:move [1 1] [2 1]]
                  [:visibility [1 1] :computer]
                  [:visibility [2 1] :computer]
                  [:load [2 1]]]
                 @calls))))

  (it "transition-to-loading keeps sailing when the transport should never reload"
    (let [updates (atom [])]
      (set-test-world! [[{:contents {:never-reload? true :unload-target-city [9 9] :pickup-continent-pos [1 1]}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (with-redefs [empire.computer.transport-core/set-transport-mission (fn [pos mission]
                                                                           (swap! updates conj [:mission pos mission]))
                    empire.state.api/update-world! (fn [& args] (swap! updates conj args))]
        (@#'transport/transition-to-loading [0 0])
        (should= [:mission [0 0] :sailing] (first @updates)))))

  (it "transition-to-loading finds a new pickup continent for reloadable transports"
    (let [updates (atom [])]
      (set-test-world! [[{:contents {:unload-target-city [9 9]}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (with-redefs [empire.computer.transport-core/set-transport-mission (fn [pos mission]
                                                                           (swap! updates conj [:mission pos mission]))
                    empire.computer.transport-core/find-adjacent-land-pos (constantly [1 1])
                    empire.computer.land-objectives/flood-fill-continent (constantly #{[1 1] [1 2]})
                    empire.computer.transport-targeting/find-next-pickup-continent-pos (fn [pos continent]
                                                                                         (swap! updates conj [:pickup pos continent])
                                                                                         [4 4])
                    empire.state.api/update-world! (fn [& args] (swap! updates conj args))]
        (@#'transport/transition-to-loading [0 0])
        (should= [:mission [0 0] :loading] (first @updates))
        (should= [:pickup [0 0] #{[1 1] [1 2]}] (nth @updates 2)))))

  (it "stale empty loading transport without a pickup target falls back to sailing"
    (let [called (atom [])]
      (set-test-world! [[{:type :sea
                          :contents {:type :transport :owner :computer
                                     :transport-mission :loading
                                     :army-count 0}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (with-redefs [empire.computer.transport-targeting/find-next-pickup-continent-pos (fn [& _] nil)
                    empire.computer.transport/loading-crawl-move (fn [_] nil)
                    empire.computer.transport/start-sailing (fn [pos unit]
                                                              (reset! called [:sail pos (:army-count unit)]))]
        (@#'transport/handle-stale-loading [0 0] {:army-count 0} 0)
        (should= [:sail [0 0] 0] @called)))))
