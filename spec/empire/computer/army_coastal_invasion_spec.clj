(ns empire.computer.army-coastal-invasion-spec
  (:require [speclj.core :refer :all]
            [empire.computer.army.coastal-invasion :as invasion]
            [empire.computer.core :as core]
            [empire.computer.army.movement :as movement]
            [empire.test.utils :refer [build-test-map reset-all-atoms!]]))

(describe "army coastal invasion helpers"
  (before (reset-all-atoms!))

  (describe "local-empty-coast-target"
    (it "returns nearest empty coastal cell in local BFS"
      (with-redefs [movement/get-passable-neighbors
                    (fn [pos _country-id]
                      (case pos
                        [0 0] [[1 0] [0 1]]
                        [1 0] []
                        [0 1] []
                        []))
                    invasion/empty-coastal-cell?
                    (fn [_ctx pos _country-id] (= pos [1 0]))]
        (let [ctx {:current-world (fn [] [])}]
          (should= [1 0] (invasion/local-empty-coast-target ctx [0 0] 1)))))

    (it "returns nil when no empty coastal cell is reachable within radius two"
      (with-redefs [movement/get-passable-neighbors
                    (fn [pos _country-id]
                      (case pos
                        [0 0] [[1 0]]
                        [1 0] [[2 0]]
                        [2 0] [[3 0]]
                        []))
                    invasion/empty-coastal-cell?
                    (fn [_ctx pos _country-id] (= pos [3 0]))]
        (let [ctx {:current-world (fn [] [])}]
          (should-be-nil (invasion/local-empty-coast-target ctx [0 0] 1))))))

  (describe "process-move-to-coast-for-invasion"
    (it "switches to sentry when should-sentry-on-coast? is true"
      (let [world (atom (build-test-map ["a"]))
            ctx {:current-world (fn [] @world)
                 :update-game-map! (fn [f & args] (apply swap! world f args))
                 :read-runtime-state (fn [_] nil)
                 :adjacent-to-ocean? (fn [_] true)
                 :should-sentry-on-coast? (fn [_ _] true)
                 :find-coast-target-once (fn [_ _] nil)}]
        (should= [0 0] (invasion/process-move-to-coast-for-invasion ctx [0 0] 1))
        (should= :sentry (get-in @world [0 0 :contents :mode]))))

    (it "returns nil when no coast target can be found"
      (let [world (atom (build-test-map ["a"]))
            ctx {:current-world (fn [] @world)
                 :update-game-map! (fn [f & args] (apply swap! world f args))
                 :read-runtime-state (fn [_] nil)
                 :adjacent-to-ocean? (fn [_] false)
                 :should-sentry-on-coast? (fn [_ _] false)
                 :find-coast-target-once (fn [_ _] nil)}]
        (should-be-nil (invasion/process-move-to-coast-for-invasion ctx [0 0] 1))))

    (it "repaths locally after failed main move when retry window allows"
      (let [world (atom (build-test-map ["a#"]))
            runtime (atom {:round-number 4})
            moves (atom [])
            ctx {:current-world (fn [] @world)
                 :update-game-map! (fn [f & args] (apply swap! world f args))
                 :read-runtime-state (fn [k] (get @runtime k))
                 :adjacent-to-ocean? (fn [_] false)
                 :should-sentry-on-coast? (fn [_ _] false)
                 :find-coast-target-once (fn [_ _] [1 0])}]
        (with-redefs [movement/move-toward-objective
                      (fn [pos target country-id]
                        (swap! moves conj [pos target country-id])
                        (when (= target [0 1]) [0 1]))
                      invasion/local-empty-coast-target
                      (fn [_ctx _pos _country-id] [0 1])]
          (should= [0 1] (invasion/process-move-to-coast-for-invasion ctx [0 0] 1))
          (should= [[[0 0] [1 0] 1]
                    [[0 0] [0 1] 1]]
                   @moves))))

    (it "uses the cheap lake-retask step when available"
      (let [world (atom (build-test-map ["a~"]))
            ctx {:current-world (fn [] @world)
                 :update-game-map! (fn [f & args] (apply swap! world f args))
                 :read-runtime-state (fn [_] nil)
                 :adjacent-to-ocean? (fn [_] true)
                 :should-sentry-on-coast? (fn [_ _] false)
                 :find-coast-target-once (fn [_ _] [1 0])}]
        (with-redefs [movement/get-empty-passable-neighbors (fn [_ _] [[1 0]])
                      movement/try-move (fn [_ _] [1 0])]
          (swap! world assoc-in [0 0 :contents :lake-retask?] true)
          (should= [1 0] (invasion/process-move-to-coast-for-invasion ctx [0 0] 1))))))

  (describe "staging and repath helpers"
    (it "tracks increasing bfs distances for passable coastal staging search"
      (should= {[0 0] 0 [1 0] 1 [2 0] 2}
               (@#'empire.computer.army.coastal-invasion/bfs-land-distances
                [0 0]
                1
                (fn [pos _]
                  (case pos
                    [0 0] [[1 0]]
                    [1 0] [[2 0]]
                    [])))))

    (it "chooses the closest staging cell when no empty coastal cell is reachable"
      (with-redefs [movement/get-passable-neighbors (fn [_ _] [[1 0] [2 0]])
                    movement/sovereign-passable? (fn [_ _] true)
                    invasion/empty-coastal-cell? (fn [_ _ _] false)
                    core/distance (fn [a b] (Math/abs ^long (- (first a) (first b))))]
        (should= [2 0]
                 (invasion/find-coast-target-once
                  {:current-world (fn [] [[{:type :land :contents {:type :army :owner :computer}}
                                           {:type :land}
                                           {:type :land}]])
                   :adjacent-to-ocean? (fn [pos] (= pos [2 0]))}
                  [0 0]
                  1))))

    (it "falls back to local repath when the direct coast move fails"
      (with-redefs [movement/move-toward-objective (fn [_ target _]
                                                     (when (= target [3 0]) [3 0]))
                    invasion/local-empty-coast-target (fn [_ _ _] [3 0])]
        (let [world (atom (build-test-map ["a~~~"]))
              ctx {:current-world (fn [] @world)
                   :update-game-map! (fn [f & args] (apply swap! world f args))
                   :read-runtime-state (fn [_] 4)
                   :adjacent-to-ocean? (fn [_] false)}]
          (should= [3 0]
                   (@#'empire.computer.army.coastal-invasion/move-toward-coast-target
                    ctx [0 0] 1 {:coast-repath-after-round 4 :pos [0 0]} [1 0])))))))
