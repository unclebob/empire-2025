(ns empire.units.satellite-spec
  (:require [speclj.core :refer :all]
            [empire.units.satellite :as satellite]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [build-test-map reset-all-atoms! set-test-player-map! set-test-computer-map! make-initial-test-map set-test-world! update-test-world!]]))

(describe "satellite unit module"
  (before (reset-all-atoms!))
  (context "configuration"
    (it "has speed of 10"
      (should= 10 satellite/speed))

    (it "has cost of 50"
      (should= 50 satellite/cost))

    (it "has 1 hit point"
      (should= 1 satellite/hits))

    (it "displays as Z"
      (should= "Z" satellite/display-char))

    (it "has 50 turns lifespan"
      (should= 50 satellite/turns))

    (it "has visibility radius of 2"
      (should= 2 satellite/visibility-radius))

    (it "has strength of 1"
      (should= 1 satellite/strength)))

  (context "initial-state"
    (it "includes turns-remaining"
      (should= {:turns-remaining 50} (satellite/initial-state))))

  (context "can-move-to?"
    (it "returns true for any cell"
      (should= true (satellite/can-move-to? {:type :land}))
      (should= true (satellite/can-move-to? {:type :sea}))
      (should= true (satellite/can-move-to? {:type :city}))))

  (context "needs-attention?"
    (it "returns true when satellite has no target"
      (let [unit {:type :satellite :mode :awake}]
        (should (satellite/needs-attention? unit))))

    (it "returns false when satellite has target"
      (let [unit {:type :satellite :mode :awake :target [5 5]}]
        (should-not (satellite/needs-attention? unit)))))

  (context "extend-target-to-boundary"
    (it "extends southeast target to corner"
      (should= [9 9] (satellite/extend-target-to-boundary [2 2] [5 5] 10 10)))

    (it "extends east target to right edge"
      (should= [5 9] (satellite/extend-target-to-boundary [5 2] [5 5] 10 10)))

    (it "extends south target to bottom edge"
      (should= [9 5] (satellite/extend-target-to-boundary [2 5] [5 5] 10 10)))

    (it "extends northwest target to top-left corner"
      (should= [0 0] (satellite/extend-target-to-boundary [5 5] [3 3] 10 10)))

    (it "extends north target to top edge"
      (should= [0 5] (satellite/extend-target-to-boundary [5 5] [3 5] 10 10)))

    (it "extends west target to left edge"
      (should= [5 0] (satellite/extend-target-to-boundary [5 5] [5 3] 10 10))))

  (context "calculate-bounce-target"
    (it "bounces from right edge to left edge"
      (let [target (satellite/calculate-bounce-target [5 9] 10 10)]
        (should= 0 (second target))))

    (it "bounces from bottom edge to top edge"
      (let [target (satellite/calculate-bounce-target [9 5] 10 10)]
        (should= 0 (first target))))

    (it "bounces from corner to one of two opposite edges"
      (let [target (satellite/calculate-bounce-target [9 9] 10 10)
            [tx ty] target]
        (should (or (= tx 0) (= ty 0)))))

    (it "bounces from top edge to bottom edge"
      (let [target (satellite/calculate-bounce-target [0 5] 10 10)]
        (should= 9 (first target))))

    (it "bounces from left edge to right edge"
      (let [target (satellite/calculate-bounce-target [5 0] 10 10)]
        (should= 9 (second target))))

    (it "mid-left edge bounces to right edge"
      (with-redefs [rand-int (constantly 3)]
        (should= [3 9] (satellite/calculate-bounce-target [5 0] 10 10))))

    (it "mid-right edge bounces to left edge"
      (with-redefs [rand-int (constantly 3)]
        (should= [3 0] (satellite/calculate-bounce-target [5 9] 10 10))))

    (it "mid-top edge bounces to bottom edge"
      (with-redefs [rand-int (constantly 3)]
        (should= [9 3] (satellite/calculate-bounce-target [0 5] 10 10))))

    (it "mid-bottom edge bounces to top edge"
      (with-redefs [rand-int (constantly 3)]
        (should= [0 3] (satellite/calculate-bounce-target [9 5] 10 10))))

    (it "corner bounce with rand=0 bounces vertically"
      (with-redefs [rand-nth first rand-int (constantly 0)]
        (let [target (satellite/calculate-bounce-target [0 0] 10 10)]
          (should= 9 (first target)))))

    (it "corner bounce with rand=1 bounces horizontally"
      (with-redefs [rand-nth second rand-int (constantly 0)]
        (let [target (satellite/calculate-bounce-target [0 0] 10 10)]
          (should= 9 (second target)))))

    (it "bottom-right corner bounce with rand=0 targets row 0"
      (with-redefs [rand-nth first rand-int (constantly 0)]
        (let [target (satellite/calculate-bounce-target [9 9] 10 10)]
          (should= 0 (first target)))))

    (it "bottom-right corner bounce with rand=1 targets col 0"
      (with-redefs [rand-nth second rand-int (constantly 0)]
        (let [target (satellite/calculate-bounce-target [9 9] 10 10)]
          (should= 0 (second target))))))

  (context "move-one-step"
    (before
      (set-test-world! (build-test-map ["##########"
                                        "##########"
                                        "##########"
                                        "##########"
                                        "##########"
                                        "##########"
                                        "##########"
                                        "##########"
                                        "##########"
                                        "##########"]))
      (set-test-player-map! (make-initial-test-map 10 10 nil)))

    (it "does not move without target"
      (update-test-world! assoc-in [5 5 :contents]
                          {:type :satellite :owner :player :turns-remaining 50})
      (should= [5 5] (satellite/move-one-step [5 5])))

    (it "moves toward target"
      (update-test-world! assoc-in [5 5 :contents]
                          {:type :satellite :owner :player :target [9 9] :turns-remaining 50})
      (should= [6 6] (satellite/move-one-step [5 5])))

    (it "moves toward lower coordinates"
      (update-test-world! assoc-in [5 5 :contents]
                          {:type :satellite :owner :player :target [2 2] :turns-remaining 50})
      (should= [4 4] (satellite/move-one-step [5 5])))

    (it "moves along same row toward target"
      (update-test-world! assoc-in [5 3 :contents]
                          {:type :satellite :owner :player :target [5 9] :turns-remaining 50})
      (should= [5 4] (satellite/move-one-step [5 3])))

    (it "moves along same column toward target"
      (update-test-world! assoc-in [3 5 :contents]
                          {:type :satellite :owner :player :target [9 5] :turns-remaining 50})
      (should= [4 5] (satellite/move-one-step [3 5])))))
