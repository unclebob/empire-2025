(ns empire.computer.random-away-spec
  (:require [empire.computer.core :as core]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-player-map! set-test-world! update-test-world!]]
            [speclj.core :refer :all]))

(describe "computer core"
  (before (reset-all-atoms!))

  (context "random-away-direction"
    (it "returns [1 1] when target is southeast of origin"
      (should= [1 1] (core/random-away-direction [0 0] [1 1])))

    (it "returns [-1 -1] when target is northwest of origin"
      (should= [-1 -1] (core/random-away-direction [5 5] [4 4])))

    (it "returns [1 -1] when target is south-west of origin"
      (should= [1 -1] (core/random-away-direction [3 3] [4 2])))

    (it "randomizes axis when aligned on column (dc=0)"
      (with-redefs [rand (constantly 0.1)]
        (should= [-1 1] (core/random-away-direction [3 3] [3 4]))))

    (it "randomizes opposite axis when aligned on column (dc=0, rand>0.5)"
      (with-redefs [rand (constantly 0.9)]
        (should= [1 1] (core/random-away-direction [3 3] [3 4]))))

    (it "randomizes axis when aligned on row (dr=0)"
      (with-redefs [rand (constantly 0.1)]
        (should= [1 -1] (core/random-away-direction [3 3] [4 3]))))

    (it "randomizes opposite axis when aligned on row (dr=0, rand>0.5)"
      (with-redefs [rand (constantly 0.9)]
        (should= [1 1] (core/random-away-direction [3 3] [4 3])))))

  (context "find-wakeable-sentries"
    (it "finds computer sentry armies within radius"
      (let [game-map [[{:type :land :contents {:type :army :owner :computer :mode :sentry}}]
                      [{:type :land :contents {:type :army :owner :computer :mode :awake}}]
                      [{:type :land}]
                      [{:type :land :contents {:type :army :owner :computer :mode :sentry}}]
                      [{:type :land :contents {:type :army :owner :player :mode :sentry}}]]]
        (should= [[0 0] [3 0]] (core/find-wakeable-sentries game-map [2 0] 3))))

    (it "excludes sentries outside radius"
      (let [game-map [[{:type :land :contents {:type :army :owner :computer :mode :sentry}}]
                      [{:type :land}]
                      [{:type :land}]
                      [{:type :land}]
                      [{:type :land :contents {:type :army :owner :computer :mode :sentry}}]]]
        (should= [[4 0]] (core/find-wakeable-sentries game-map [3 0] 2))))

    (it "excludes the origin position"
      (let [game-map [[{:type :land :contents {:type :army :owner :computer :mode :sentry}}]]]
        (should= [] (core/find-wakeable-sentries game-map [0 0] 3))))))
