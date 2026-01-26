(ns empire.movement.pathfinding-spec
  (:require [speclj.core :refer :all]
            [empire.movement.pathfinding :as pathfinding]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [reset-all-atoms! build-test-map]]))

(describe "Pathfinding"

  (describe "next-step-toward"

    (before
      (reset-all-atoms!)
      ;; Simple map with land and sea
      ;; #####
      ;; #####
      ;; ~~~~~
      ;; #####
      (reset! atoms/game-map (build-test-map ["#####"
                                               "#####"
                                               "~~~~~"
                                               "#####"])))

    (it "returns nil when already at destination"
      (let [result (pathfinding/next-step-toward [0 0] [0 0])]
        (should-be-nil result)))

    (it "returns adjacent cell when destination is one step away"
      (let [result (pathfinding/next-step-toward [0 0] [0 1])]
        (should= [0 1] result)))

    (it "returns next step toward distant destination"
      (let [result (pathfinding/next-step-toward [0 0] [1 4])]
        ;; Should move toward destination (either [0 1] or [1 0] or [1 1])
        (should-not-be-nil result)
        (should (some #(= result %) [[0 1] [1 0] [1 1]]))))

    (it "returns nil when destination is unreachable (blocked by sea)"
      (let [result (pathfinding/next-step-toward [0 0] [3 0])]
        ;; Sea at row 2 blocks path
        (should-be-nil result)))

    (it "finds path around obstacles"
      (reset! atoms/game-map (build-test-map ["###~#"
                                               "###~#"
                                               "#####"
                                               "###~#"]))
      ;; Path from [0 0] to [0 4] must go around the sea at column 3
      (let [result (pathfinding/next-step-toward [0 0] [0 4])]
        ;; Should start moving (not nil)
        (should-not-be-nil result)))

    (it "moves diagonally when possible"
      (let [result (pathfinding/next-step-toward [0 0] [1 1])]
        (should= [1 1] result)))

    (it "avoids sea cells"
      (reset! atoms/game-map (build-test-map ["#~#"
                                               "###"
                                               "###"]))
      (let [result (pathfinding/next-step-toward [0 0] [0 2])]
        ;; Must go around sea at [0 1], so first step should be down or diagonal
        (should (some #(= result %) [[1 0] [1 1]])))))

  (describe "find-path"

    (before
      (reset-all-atoms!)
      (reset! atoms/game-map (build-test-map ["#####"
                                               "#####"
                                               "#####"])))

    (it "returns empty path when already at destination"
      (let [result (pathfinding/find-path [0 0] [0 0])]
        (should= [] result)))

    (it "returns single-step path for adjacent destination"
      (let [result (pathfinding/find-path [0 0] [0 1])]
        (should= [[0 1]] result)))

    (it "returns full path to destination"
      (let [result (pathfinding/find-path [0 0] [2 2])]
        (should-not-be-nil result)
        (should (pos? (count result)))
        (should= [2 2] (last result))))

    (it "returns nil for unreachable destination"
      (reset! atoms/game-map (build-test-map ["###"
                                               "~~~"
                                               "###"]))
      (let [result (pathfinding/find-path [0 0] [2 0])]
        (should-be-nil result)))))
