(ns empire.computer.army-coastal-spec
  (:require [speclj.core :refer :all]
            [empire.computer.army.coastal :as coastal]
            [empire.computer.army.movement :as movement]
            [empire.test-utils :refer [reset-all-atoms!]]))

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
                    coastal/update-game-map! (fn [& args] (swap! updates conj args))
                    coastal/current-world (fn [] {[0 0] {:contents {:mode :move-to-coast :coast-target [2 2]}}})]
        (should= [0 0] (coastal/process-move-to-coast-for-invasion [0 0] 1))
        (should= 1 (count @updates)))))

  (it "uses cached coast-target when present"
    (let [moves (atom [])]
      (with-redefs [coastal/should-sentry-on-coast? (fn [_ _] false)
                    coastal/current-world (fn [] [[{:contents {:coast-target [3 3]}}]])
                    coastal/update-game-map! (fn [& _] nil)
                    movement/move-toward-objective
                    (fn [pos target country-id]
                      (swap! moves conj [pos target country-id])
                      [1 0])]
        (should= [1 0] (coastal/process-move-to-coast-for-invasion [0 0] 7))
        (should= [[[0 0] [3 3] 7]] @moves))))

  (it "falls back to local target when move toward main target fails"
    (let [moves (atom [])]
      (with-redefs [coastal/should-sentry-on-coast? (fn [_ _] false)
                    coastal/current-world (fn [] {[0 0] {:contents {}}})
                    coastal/find-coast-target-once (fn [_ _] [4 4])
                    coastal/local-empty-coast-target (fn [_ _] [2 2])
                    coastal/update-game-map! (fn [& _] nil)
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
                  coastal/current-world (fn [] {[0 0] {:contents {}}})
                  coastal/find-coast-target-once (fn [_ _] nil)
                  coastal/update-game-map! (fn [& _] nil)
                  movement/move-toward-objective (fn [& _] :should-not-run)]
      (should-be-nil (coastal/process-move-to-coast-for-invasion [0 0] 1)))))
