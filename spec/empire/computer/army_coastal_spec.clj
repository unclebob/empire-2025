(ns empire.computer.army-coastal-spec
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.state.api :as sa]
            [empire.computer.army.coastal :as coastal]
            [empire.computer.army.movement :as movement]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-world! update-test-world!]]))

(describe "army coastal lake handling"
  (before (reset-all-atoms!))

  (it "does not sentry on a lake-only shore"
    (set-test-world! (build-test-map ["#####"
                                      "#####"
                                      "##~##"
                                      "#####"
                                      "#####"]))
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (test-utils/set-test-state! :lake-max-cells 20)
    (should-not (coastal/should-sentry-on-coast? [2 1] 1)))

  (it "finds a sea-shore target instead of a lake-shore target"
    (set-test-world! (build-test-map ["~~~~~~"
                                      "######"
                                      "######"
                                      "##~###"
                                      "######"]))
    (doseq [c (range 6)
            r (range 5)
            :when (= :land (get-in (test-utils/read-test-state :game-map) [c r :type]))]
      (update-test-world! assoc-in [c r :country-id] 1))
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    ;; Ocean is size 6; keep lake limit below that so only the inland sea is a lake.
    (test-utils/set-test-state! :lake-max-cells 5)
    (let [target (coastal/find-coast-target-once [2 4] 1)]
      (should-not-be-nil target)
      ;; Row 1 is ocean coast; row 3 surrounds an inland lake.
      (should= 1 (second target))))

  (it "settles to sentry when it reaches its staged coast target"
    (set-test-world! (build-test-map ["~~~~~"
                                      "#####"
                                      "#####"
                                      "#####"
                                      "#####"]))
    (doseq [c (range 5)
            r (range 5)
            :when (= :land (get-in (test-utils/read-test-state :game-map) [c r :type]))]
      (update-test-world! assoc-in [c r :country-id] 1))
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (test-utils/set-test-state! :lake-max-cells 2)
    (update-test-world! assoc-in [2 3 :contents]
                       {:type :army :owner :computer :hits 1
                        :mode :move-to-coast-for-invasion
                        :country-id 1
                        :coast-target [2 3]})
    (coastal/process-move-to-coast-for-invasion [2 3] 1)
    (should= :sentry (get-in (test-utils/read-test-state :game-map) [2 3 :contents :mode])))

  (it "lake-retask army goes sentry when blocked and cannot progress"
    (set-test-world! (build-test-map ["~~~~~"
                                      "#####"
                                      "#####"]))
    (doseq [c (range 5)
            r (range 3)
            :when (= :land (get-in (test-utils/read-test-state :game-map) [c r :type]))]
      (update-test-world! assoc-in [c r :country-id] 1))
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    ;; Army at [2 2], target [2 1]. Block all empty passable neighbors.
    (update-test-world! assoc-in [2 2 :contents]
                       {:type :army :owner :computer :hits 1
                        :mode :move-to-coast-for-invasion
                        :country-id 1
                        :coast-target [2 1]
                        :lake-retask? true})
    (doseq [p [[1 1] [2 1] [3 1] [1 2] [3 2]]]
      (update-test-world! assoc-in (conj p :contents)
                         {:type :army :owner :computer :hits 1 :mode :sentry :country-id 1}))
    (coastal/process-move-to-coast-for-invasion [2 2] 1)
    (should= :sentry (get-in (test-utils/read-test-state :game-map) [2 2 :contents :mode]))))

(describe "local-empty-coast-target"
  (before (reset-all-atoms!))

  (it "returns nearest empty coastal cell in local BFS"
    (with-redefs [movement/get-passable-neighbors
                  (fn [pos _country-id]
                    (case pos
                      [0 0] [[1 0] [0 1]]
                      [1 0] []
                      [0 1] []
                      []))
                  empire.computer.army.coastal/empty-coastal-cell?
                  (fn [pos _country-id] (= pos [1 0]))]
      (should= [1 0] (@#'coastal/local-empty-coast-target [0 0] 1))))

  (it "returns nil when no empty coastal cell is reachable within radius two"
    (with-redefs [movement/get-passable-neighbors
                  (fn [pos _country-id]
                    (case pos
                      [0 0] [[1 0]]
                      [1 0] [[2 0]]
                      [2 0] [[3 0]]
                      []))
                  empire.computer.army.coastal/empty-coastal-cell?
                  (fn [pos _country-id] (= pos [3 0]))]
      (should-be-nil (@#'coastal/local-empty-coast-target [0 0] 1))))

  (it "finds an empty coastal cell exactly at depth two"
    (with-redefs [movement/get-passable-neighbors
                  (fn [pos _country-id]
                    (case pos
                      [0 0] [[1 0]]
                      [1 0] [[2 0]]
                      []))
                  empire.computer.army.coastal/empty-coastal-cell?
                  (fn [pos _country-id] (= pos [2 0]))]
      (should= [2 0] (@#'coastal/local-empty-coast-target [0 0] 1)))))

(describe "process-move-to-coast-for-invasion"
  (before (reset-all-atoms!))

  (it "switches to sentry when already on coastal cell"
    (let [updates (atom [])]
      (with-redefs [coastal/should-sentry-on-coast? (fn [_ _] true)
                    sa/update-world! (fn [& args] (swap! updates conj args))
                    sa/current-world (fn [] {[0 0] {:contents {:mode :move-to-coast :coast-target [2 2]}}})]
        (should= [0 0] (coastal/process-move-to-coast-for-invasion [0 0] 1))
        (should= 1 (count @updates)))))

  (it "uses cached coast-target when present"
    (let [moves (atom [])]
      (with-redefs [coastal/should-sentry-on-coast? (fn [_ _] false)
                    sa/current-world (fn [] [[{:contents {:coast-target [3 3]}}]])
                    sa/update-world! (fn [& _] nil)
                    movement/move-toward-objective
                    (fn [pos target country-id]
                      (swap! moves conj [pos target country-id])
                      [1 0])]
        (should= [1 0] (coastal/process-move-to-coast-for-invasion [0 0] 7))
        (should= [[[0 0] [3 3] 7]] @moves))))

  (it "falls back to local target when move toward main target fails"
    (let [moves (atom [])]
      (with-redefs [coastal/should-sentry-on-coast? (fn [_ _] false)
                    sa/current-world (fn [] {[0 0] {:contents {}}})
                    coastal/find-coast-target-once (fn [_ _] [4 4])
                    empire.computer.army.coastal-invasion/local-empty-coast-target (fn [_ _ _] [2 2])
                    sa/update-world! (fn [& _] nil)
                    movement/move-toward-objective
                    (fn [pos target country-id]
                      (swap! moves conj [pos target country-id])
                      (when (= target [2 2]) [1 1]))]
        (should= [1 1] (coastal/process-move-to-coast-for-invasion [0 0] 5))
        (should= [[[0 0] [4 4] 5]
                  [[0 0] [2 2] 5]]
                 @moves))))

  (it "returns nil when no target can be found"
    (with-redefs [coastal/should-sentry-on-coast? (fn [_ _] false)
                  sa/current-world (fn [] {[0 0] {:contents {}}})
                  coastal/find-coast-target-once (fn [_ _] nil)
                  sa/update-world! (fn [& _] nil)
                  movement/move-toward-objective (fn [& _] :should-not-run)]
      (should-be-nil (coastal/process-move-to-coast-for-invasion [0 0] 1)))))

(describe "fill-coastal-cell"
  (before (reset-all-atoms!))

  (it "settles in place when already on a valid coast and no city blocks it"
    (let [updates (atom [])]
      (with-redefs [coastal/should-sentry-on-coast? (fn [_ _] true)
                    sa/update-world! (fn [& args] (swap! updates conj args))
                    empire.game-mechanics.debug.logging/log-computer-event! (fn [& _] nil)]
        (should= [2 2] (coastal/fill-coastal-cell [2 2] 7))
        (should= 1 (count @updates)))))

  (it "wakes nearby sentries when no coastal move or queue target exists"
    (with-redefs [coastal/should-sentry-on-coast? (fn [_ _] false)
                  empire.computer.army.coastal/find-nearest-unoccupied-coastal-cell (fn [_ _] nil)
                  coastal/can-settle-here? (fn [_ _] false)
                  empire.computer.army.coastal/find-nearest-cell-close-to-coast (fn [_ _] nil)
                  empire.computer.core/wake-nearby-sentries (fn [_ _] 2)
                  empire.game-mechanics.debug.logging/log-computer-event! (fn [& _] nil)]
      (should-be-nil (coastal/fill-coastal-cell [1 1] 1)))))
