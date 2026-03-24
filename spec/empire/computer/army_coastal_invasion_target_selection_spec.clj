(ns empire.computer.army-coastal-invasion-target-selection-spec
  (:require [empire.computer.army.coastal :as coastal]
            [empire.computer.army.movement :as movement]
            [empire.state.api :as sa]
            [empire.test.utils :refer [reset-all-atoms!]]
            [speclj.core :refer :all]))

(describe "process-move-to-coast-for-invasion target selection"
  (before (reset-all-atoms!))

  (it "uses cached coast-target when present"
    (let [moves (atom [])]
      (with-redefs [coastal/should-sentry-on-coast? (fn [_ _] false)
                    sa/read-state (fn [k]
                                    (when (= k :computer-map)
                                      [[{:contents {:coast-target [3 3]}}]]))
                    sa/update-world! (fn [& _] nil)
                    empire.game-mechanics.visibility/sync-ai-unit-to-computer-map!
                    (fn [_] nil)
                    movement/move-toward-objective
                    (fn [pos target country-id]
                      (swap! moves conj [pos target country-id])
                      [1 0])]
        (should= [1 0] (coastal/process-move-to-coast-for-invasion [0 0] 7))
        (should= [[[0 0] [3 3] 7]] @moves))))

  (it "does not recompute a coast target when one is already cached"
    (with-redefs [coastal/should-sentry-on-coast? (fn [_ _] false)
                  sa/read-state (fn [k]
                                  (when (= k :computer-map)
                                    [[{:contents {:coast-target [3 3]}}]]))
                  coastal/find-coast-target-once
                  (fn [& _] (should-not "should not recompute coast target when cached"))
                  sa/update-world! (fn [& _] nil)
                  empire.game-mechanics.visibility/sync-ai-unit-to-computer-map!
                  (fn [_] nil)
                  movement/move-toward-objective (fn [_ _ _] [1 0])]
      (should= [1 0] (coastal/process-move-to-coast-for-invasion [0 0] 7))))

  (it "falls back to a local target when moving toward the main target fails"
    (let [moves (atom [])]
      (with-redefs [coastal/should-sentry-on-coast? (fn [_ _] false)
                    sa/read-state (fn [k]
                                    (when (= k :computer-map)
                                      {[0 0] {:contents {}}}))
                    coastal/find-coast-target-once (fn [_ _] [4 4])
                    empire.computer.army.coastal-invasion/local-empty-coast-target (fn [_ _ _] [2 2])
                    sa/update-world! (fn [& _] nil)
                    empire.game-mechanics.visibility/sync-ai-unit-to-computer-map!
                    (fn [_] nil)
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
                  sa/read-state (fn [k]
                                  (when (= k :computer-map)
                                    {[0 0] {:contents {}}}))
                  coastal/find-coast-target-once (fn [_ _] nil)
                  sa/update-world! (fn [& _] nil)
                  empire.game-mechanics.visibility/sync-ai-unit-to-computer-map!
                  (fn [_] nil)
                  movement/move-toward-objective (fn [& _] :should-not-run)]
      (should-be-nil (coastal/process-move-to-coast-for-invasion [0 0] 1)))))
