(ns empire.units.satellite-spec
  (:require [speclj.core :refer :all]
            [empire.units.satellite :as satellite]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [build-test-map reset-all-atoms! make-initial-test-map]]))

(describe "satellite unit module"
  (before (reset-all-atoms!))
  (describe "configuration"
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
      (should= 2 satellite/visibility-radius)))

  (describe "initial-state"
    (it "includes turns-remaining"
      (should= {:turns-remaining 50} (satellite/initial-state))))

  (describe "can-move-to?"
    (it "returns true for any cell"
      (should (satellite/can-move-to? {:type :land}))
      (should (satellite/can-move-to? {:type :sea}))
      (should (satellite/can-move-to? {:type :city}))))

  (describe "needs-attention?"
    (it "returns true when satellite has no target"
      (let [unit {:type :satellite :mode :awake}]
        (should (satellite/needs-attention? unit))))

    (it "returns false when satellite has target"
      (let [unit {:type :satellite :mode :awake :target [5 5]}]
        (should-not (satellite/needs-attention? unit)))))

  (describe "extend-target-to-boundary"
    (it "extends southeast target to corner"
      (should= [9 9] (satellite/extend-target-to-boundary [2 2] [5 5] 10 10)))

    (it "extends east target to right edge"
      (should= [5 9] (satellite/extend-target-to-boundary [5 2] [5 5] 10 10)))

    (it "extends south target to bottom edge"
      (should= [9 5] (satellite/extend-target-to-boundary [2 5] [5 5] 10 10))))

  (describe "calculate-bounce-target"
    (it "bounces from right edge to left edge"
      (let [target (satellite/calculate-bounce-target [5 9] 10 10)]
        (should= 0 (second target))))

    (it "bounces from bottom edge to top edge"
      (let [target (satellite/calculate-bounce-target [9 5] 10 10)]
        (should= 0 (first target))))

    (it "bounces from corner to one of two opposite edges"
      (let [target (satellite/calculate-bounce-target [9 9] 10 10)
            [tx ty] target]
        (should (or (= tx 0) (= ty 0))))))

  (describe "move-one-step"
    (before
      (reset! atoms/game-map (build-test-map ["##########"
                                               "##########"
                                               "##########"
                                               "##########"
                                               "##########"
                                               "##########"
                                               "##########"
                                               "##########"
                                               "##########"
                                               "##########"]))
      (reset! atoms/player-map (make-initial-test-map 10 10 nil)))

    (it "does not move without target"
      (swap! atoms/game-map assoc-in [5 5 :contents]
             {:type :satellite :owner :player :turns-remaining 50})
      (should= [5 5] (satellite/move-one-step [5 5])))

    (it "moves toward target"
      (swap! atoms/game-map assoc-in [5 5 :contents]
             {:type :satellite :owner :player :target [9 9] :turns-remaining 50})
      (should= [6 6] (satellite/move-one-step [5 5])))

    (it "bounces when reaching target at edge"
      (swap! atoms/game-map assoc-in [9 5 :contents]
             {:type :satellite :owner :player :target [9 5] :turns-remaining 50})
      (let [new-pos (satellite/move-one-step [9 5])]
        ;; Should stay at same position but get new target
        (should= [9 5] new-pos)
        (let [sat (:contents (get-in @atoms/game-map [9 5]))]
          ;; New target should be at opposite edge (top row = 0)
          (should= 0 (first (:target sat)))))))

  (describe "calculate-bounce-target edge cases"
    (it "returns same position when not at any edge"
      ;; This tests the :else branch when position is in the middle
      (let [result (satellite/calculate-bounce-target [5 5] 10 10)]
        (should= [5 5] result)))

    (it "bounces from left edge to right edge"
      (let [target (satellite/calculate-bounce-target [5 0] 10 10)]
        (should= 9 (second target))))

    (it "bounces from top edge to bottom edge"
      (let [target (satellite/calculate-bounce-target [0 5] 10 10)]
        (should= 9 (first target))))

    (it "corner bounce chooses vertical edge when rand-int returns 0 at top-left"
      (with-redefs [rand-int (constantly 0)]
        (let [target (satellite/calculate-bounce-target [0 0] 10 10)]
          ;; at-top? is true, so target x should be (dec map-height) = 9
          (should= 9 (first target)))))

    (it "corner bounce chooses vertical edge when rand-int returns 0 at bottom-left"
      (with-redefs [rand-int (constantly 0)]
        (let [target (satellite/calculate-bounce-target [9 0] 10 10)]
          ;; at-top? is false (at-bottom? is true), so target x should be 0
          (should= 0 (first target)))))

    (it "corner bounce chooses horizontal edge when rand-int returns 1"
      (with-redefs [rand-int (constantly 1)]
        (let [target (satellite/calculate-bounce-target [0 0] 10 10)]
          ;; at-left? is true, so target y should be (dec map-width) = 9
          (should= 9 (second target)))))))
