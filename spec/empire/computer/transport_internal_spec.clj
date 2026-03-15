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
      (should-not (contains? points [2 2])))))
