(ns empire.computer.transport-sailing-state-spec
  "Tests for VMS Empire style computer transport movement."
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.computer.core :as core]
            [empire.computer.transport :as transport]
            [empire.computer.transport-core :as tc]

            [empire.computer.land-objectives :as land-objectives]
            [empire.player.production :as player-prod]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-world! update-test-world!]]))
(describe "process-transport"
  (before (reset-all-atoms!))

  (context "full transport sailing"
    (it "full loading transport enters sailing toward fog"
      ;; Full transport with no adjacent loadable armies enters sailing
      (let [game-map (build-test-map ["t~~~~~"
                                      "~~~~~~"])]
        (set-test-world! game-map)
        (set-test-computer-map!
                (vec (for [c (range 6)]
                       (vec (for [r (range 2)]
                              (if (< c 4)
                                (get-in game-map [c r])
                                nil))))))
        (update-test-world! assoc-in [0 0 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 6})
        (transport/process-transport [0 0])
        (let [t (first (for [c (range 6) r (range 2)
                               :let [unit (get-in (test-utils/read-test-state :game-map) [c r :contents])]
                               :when (= :transport (:type unit))]
                           unit))]
          (should= :sail-to-unload (:transport-mission t)))))

  (context "lake transport behavior"
    (it "lake transport unloads first, then parks in deep water as sentry"
      (set-test-world! (build-test-map ["#####"
                                        "#t~~#"
                                        "#~~~#"
                                        "#~~~#"
                                        "#####"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (test-utils/set-test-state! :lake-max-cells 20)
      (update-test-world! assoc-in [1 1 :contents]
                         {:type :transport :owner :computer :hits 1
                          :transport-mission :sailing :army-count 1 :mode :awake
                          :lake-locked? true})
      ;; First pass should prioritize unloading.
      (transport/process-transport [1 1])
      (let [tpos (first (for [c (range 5) r (range 5)
                              :when (= :transport (get-in (test-utils/read-test-state :game-map) [c r :contents :type]))]
                          [c r]))]
        (should-not-be-nil tpos)
        (should= 0 (get-in (test-utils/read-test-state :game-map) (conj tpos :contents :army-count)))
        (should= true (get-in (test-utils/read-test-state :game-map) (conj tpos :contents :never-reload?)))
        (should= :land-locked (get-in (test-utils/read-test-state :game-map) (conj tpos :contents :transport-mission)))
        ;; Next pass should back away from shore and park as sentry.
        (transport/process-transport tpos))
      ;; Next pass should back away from shore and park as sentry.
      (should= :transport (get-in (test-utils/read-test-state :game-map) [2 2 :contents :type]))
      (should= :sentry (get-in (test-utils/read-test-state :game-map) [2 2 :contents :mode]))
      ;; Once parked, it should not move again.
      (transport/process-transport [2 2])
      (should= :transport (get-in (test-utils/read-test-state :game-map) [2 2 :contents :type]))
      (should= :sentry (get-in (test-utils/read-test-state :game-map) [2 2 :contents :mode]))))

    (it "lake-locked major-invasion transport unloads on adjacent land outside target area"
      (set-test-world! [[{:type :land} {:type :land} {:type :land}]
                        [{:type :land}
                         {:type :sea}
                         {:type :land}]
                        [{:type :land}
                         {:type :sea}
                         {:type :land}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (test-utils/set-test-state! :lake-max-cells 4)
      (update-test-world! assoc-in [1 1 :contents]
                         {:type :transport :owner :computer :hits 1
                          :transport-mission :land-locked
                          :major-invasion true
                          :army-count 1 :mode :awake
                          :lake-locked? true})
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/process-transport [1 1])
      (let [tpos (first (for [c (range 3) r (range 3)
                              :when (= :transport (get-in (test-utils/read-test-state :game-map) [c r :contents :type]))]
                          [c r]))
            unloaded? (some true?
                            (for [c (range 3) r (range 3)]
                              (= :army (get-in (test-utils/read-test-state :game-map) [c r :contents :type]))))]
        (should unloaded?)
        (should-not-be-nil tpos)
        (should= 0 (get-in (test-utils/read-test-state :game-map) (conj tpos :contents :army-count)))))

    (it "invading transport sidesteps when invasion path step is blocked"
      (set-test-world! (build-test-map ["~~~"
                                        "td~"
                                        "~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      ;; Force blocker to be a computer transport occupying the first invasion step.
      (update-test-world! assoc-in [1 1 :contents]
                         {:type :transport :owner :computer :transport-mission :invading :army-count 2})
      (update-test-world! assoc-in [0 1 :contents]
                         {:type :transport :owner :computer
                          :transport-mission :invading
                          :army-count 4
                          :invasion-target [2 1]
                          :invasion-path [[1 1] [2 1]]
                          :invasion-path-origin [0 1]})
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/process-transport [0 1])
      (should= :transport (get-in (test-utils/read-test-state :game-map) [1 0 :contents :type]))
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [0 1 :contents]))))

    (it "full transport sails even when nearby armies exist"
      ;; Full transport in narrow channel with armies on adjacent land
      ;; Should still sail because it can't load any more
      ;; a##    army at [0,0], land at [1,0] and [2,0]
      ;; ~t~    transport at [1,1], sea at [0,1] and [2,1]
      ;; ~~~    open sea rows 2-4
      ;; ~~~
      ;; ~~~
      (let [game-map (build-test-map ["a##"
                                      "~t~"
                                      "~~~"
                                      "~~~"
                                      "~~~"])]
        (set-test-world! game-map)
        (set-test-computer-map!
                (vec (for [c (range 3)]
                       (vec (for [r (range 5)]
                              (if (< r 3) (get-in game-map [c r]) nil))))))
        (update-test-world! assoc-in [1 1 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 6})
        (set-test-computer-map!
                (assoc-in (test-utils/read-test-state :computer-map)
                          [1 1 :contents]
                          {:type :transport :owner :computer
                           :transport-mission :loading :army-count 6}))
        (transport/process-transport [1 1])
        (let [t (first (for [c (range 3) r (range 5)
                             :let [unit (get-in (test-utils/read-test-state :game-map) [c r :contents])]
                             :when (= :transport (:type unit))]
                         unit))]
          (should-not-be-nil t)
          (should= :sail-to-unload (:transport-mission t))))))
