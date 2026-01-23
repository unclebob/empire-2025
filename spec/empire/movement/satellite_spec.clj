(ns empire.movement.satellite-spec
  (:require
    [empire.atoms :as atoms]
    [empire.game-loop :as game-loop]
    [empire.movement.movement :refer [set-unit-movement]]
    [empire.movement.visibility :refer [update-cell-visibility]]
    [empire.movement.satellite :refer [move-satellite calculate-satellite-target]]
    [empire.test-utils :refer [build-test-map set-test-unit get-test-unit reset-all-atoms! make-initial-test-map]]
    [speclj.core :refer :all]))

(describe "bounce-satellite"
  (before (reset-all-atoms!))

  (it "updates satellite with new target when at boundary"
    (reset! atoms/game-map (build-test-map ["###"
                                             "##V"
                                             "###"]))
    (set-test-unit atoms/game-map "V" :target [1 2] :turns-remaining 50)
    (reset! atoms/player-map (make-initial-test-map 3 3 nil))
    (let [result (empire.movement.satellite/bounce-satellite [1 2])]
      ;; Should return same position
      (should= [1 2] result)
      ;; Satellite should have new target on opposite boundary
      (let [sat (:contents (get-in @atoms/game-map [1 2]))]
        (should= 0 (second (:target sat))))))

  (it "returns current position unchanged"
    (reset! atoms/game-map (build-test-map ["###"
                                             "V##"
                                             "###"]))
    (set-test-unit atoms/game-map "V" :target [1 0] :turns-remaining 50)
    (reset! atoms/player-map (make-initial-test-map 3 3 nil))
    (let [result (empire.movement.satellite/bounce-satellite [1 0])]
      (should= [1 0] result))))

(describe "move-satellite-toward-target"
  (before (reset-all-atoms!))

  (it "moves satellite one step toward target"
    (reset! atoms/game-map (build-test-map ["####"
                                             "#V##"
                                             "####"
                                             "####"]))
    (set-test-unit atoms/game-map "V" :target [3 3] :turns-remaining 50)
    (reset! atoms/player-map (make-initial-test-map 4 4 nil))
    (let [result (empire.movement.satellite/move-satellite-toward-target [1 1])]
      ;; Should move diagonally toward [3 3]
      (should= [2 2] result)
      ;; Old position should be empty
      (should-be-nil (:contents (get-in @atoms/game-map [1 1])))
      ;; New position should have satellite
      (should (:contents (get-in @atoms/game-map [2 2])))))

  (it "moves horizontally when target is directly east"
    (reset! atoms/game-map (build-test-map ["#####"
                                             "#V###"
                                             "#####"]))
    (set-test-unit atoms/game-map "V" :target [1 4] :turns-remaining 50)
    (reset! atoms/player-map (make-initial-test-map 3 5 nil))
    (let [result (empire.movement.satellite/move-satellite-toward-target [1 1])]
      (should= [1 2] result))))

(describe "calculate-satellite-target"
  (before (reset-all-atoms!))
  (it "extends target to boundary in direction of travel"
    (reset! atoms/game-map (make-initial-test-map 5 5 {:type :land}))
    ;; From [1 1] toward [2 2] should extend to [4 4]
    (should= [4 4] (calculate-satellite-target [1 1] [2 2])))

  (it "extends target to right edge when moving east"
    (reset! atoms/game-map (make-initial-test-map 5 5 {:type :land}))
    (should= [2 4] (calculate-satellite-target [2 1] [2 2])))

  (it "extends target to bottom edge when moving south"
    (reset! atoms/game-map (make-initial-test-map 5 5 {:type :land}))
    (should= [4 2] (calculate-satellite-target [1 2] [2 2])))

  (it "extends target to top-left corner when moving northwest"
    (reset! atoms/game-map (make-initial-test-map 5 5 {:type :land}))
    (should= [0 0] (calculate-satellite-target [2 2] [1 1]))))

(describe "satellite movement"
  (before (reset-all-atoms!))
  (it "does not move without a target"
    (reset! atoms/game-map (build-test-map ["###"
                                             "#V#"
                                             "###"]))
    (set-test-unit atoms/game-map "V" :turns-remaining 50)
    (reset! atoms/player-map (make-initial-test-map 3 3 nil))
    (move-satellite [1 1])
    ;; Satellite should stay in place - no target set
    (should (:contents (get-in @atoms/game-map [1 1])))
    (should-be-nil (:target (:contents (get-in @atoms/game-map [1 1])))))

  (it "still decrements turns even without a target"
    (reset! atoms/game-map (build-test-map ["###"
                                             "#V#"
                                             "###"]))
    (set-test-unit atoms/game-map "V" :turns-remaining 5)
    (reset! atoms/player-map (make-initial-test-map 3 3 nil))
    ;; Run move-satellites (which calls move-satellite-steps)
    (game-loop/move-satellites)
    ;; Satellite should still be at [1 1] but with decremented turns
    (let [sat (:contents (get-in @atoms/game-map [1 1]))]
      (should sat)
      (should= 4 (:turns-remaining sat))))

  (it "moves toward its target"
    (reset! atoms/game-map (build-test-map ["####"
                                             "#V##"
                                             "####"
                                             "####"]))
    (set-test-unit atoms/game-map "V" :target [3 3] :turns-remaining 50)
    (reset! atoms/player-map (make-initial-test-map 4 4 nil))
    (move-satellite [1 1])
    ;; Satellite should have moved toward target [3 3], so to [2 2]
    (should (:contents (get-in @atoms/game-map [2 2])))
    (should-be-nil (:contents (get-in @atoms/game-map [1 1])))
    (should= [3 3] (:target (:contents (get-in @atoms/game-map [2 2])))))

  (it "moves horizontally when target is directly east"
    (reset! atoms/game-map (build-test-map ["#####"
                                             "#V###"
                                             "#####"]))
    (set-test-unit atoms/game-map "V" :target [1 4] :turns-remaining 50)
    (reset! atoms/player-map (make-initial-test-map 3 5 nil))
    (move-satellite [1 1])
    ;; Satellite should move east to [1 2]
    (should (:contents (get-in @atoms/game-map [1 2])))
    (should-be-nil (:contents (get-in @atoms/game-map [1 1]))))

  (it "moves vertically when target is directly south"
    (reset! atoms/game-map (build-test-map ["###"
                                             "#V#"
                                             "###"
                                             "###"
                                             "###"]))
    (set-test-unit atoms/game-map "V" :target [4 1] :turns-remaining 50)
    (reset! atoms/player-map (make-initial-test-map 5 3 nil))
    (move-satellite [1 1])
    ;; Satellite should move south to [2 1]
    (should (:contents (get-in @atoms/game-map [2 1])))
    (should-be-nil (:contents (get-in @atoms/game-map [1 1]))))

  (it "gets new target on opposite boundary when reaching right edge"
    (reset! atoms/game-map (build-test-map ["###"
                                             "##V"
                                             "###"]))
    (set-test-unit atoms/game-map "V" :target [1 2] :turns-remaining 50)
    (reset! atoms/player-map (make-initial-test-map 3 3 nil))
    (move-satellite [1 2])
    ;; Satellite at target on right edge should get new target on left edge (column 0)
    (let [sat (:contents (get-in @atoms/game-map [1 2]))]
      (should sat)
      (should= 0 (second (:target sat)))))

  (it "gets new target on opposite boundary when reaching bottom edge"
    (reset! atoms/game-map (build-test-map ["###"
                                             "###"
                                             "#V#"]))
    (set-test-unit atoms/game-map "V" :target [2 1] :turns-remaining 50)
    (reset! atoms/player-map (make-initial-test-map 3 3 nil))
    (move-satellite [2 1])
    ;; Satellite at target on bottom edge should get new target on top edge (row 0)
    (let [sat (:contents (get-in @atoms/game-map [2 1]))]
      (should sat)
      (should= 0 (first (:target sat)))))

  (it "gets new target on one of opposite boundaries when at corner"
    (reset! atoms/game-map (build-test-map ["###"
                                             "###"
                                             "##V"]))
    (set-test-unit atoms/game-map "V" :target [2 2] :turns-remaining 50)
    (reset! atoms/player-map (make-initial-test-map 3 3 nil))
    (move-satellite [2 2])
    ;; Satellite at corner should get new target on either top edge (row 0) or left edge (column 0)
    (let [sat (:contents (get-in @atoms/game-map [2 2]))
          [tx ty] (:target sat)]
      (should sat)
      (should (or (= tx 0) (= ty 0)))))

  (it "extends non-boundary target to wall when setting movement"
    (reset! atoms/game-map (build-test-map ["#####"
                                             "#V###"
                                             "#####"
                                             "#####"
                                             "#####"]))
    (set-test-unit atoms/game-map "V" :mode :awake :turns-remaining 50)
    (reset! atoms/player-map (make-initial-test-map 5 5 nil))
    ;; Set movement to non-boundary target [2 2] - should extend to [4 4]
    (set-unit-movement [1 1] [2 2])
    (let [sat (:contents (get-in @atoms/game-map [1 1]))
          [tx ty] (:target sat)]
      (should sat)
      (should= :moving (:mode sat))
      ;; Target should be extended to boundary at [4 4] (southeast corner)
      (should= [4 4] [tx ty])))

  (it "decrements turns-remaining once per round not per step"
    (reset! atoms/game-map (build-test-map ["#####"
                                             "#V###"
                                             "#####"
                                             "#####"
                                             "#####"]))
    (set-test-unit atoms/game-map "V" :target [4 4] :turns-remaining 50)
    (reset! atoms/player-map (make-initial-test-map 5 5 nil))
    (game-loop/move-satellites)
    ;; After one round of movement (10 steps), turns-remaining should only decrement by 1
    (let [{:keys [unit]} (get-test-unit atoms/game-map "V")]
      (should= 49 (:turns-remaining unit))))

  (it "is removed when turns-remaining reaches zero"
    (reset! atoms/game-map (build-test-map ["#####"
                                             "#####"
                                             "##V##"
                                             "#####"
                                             "#####"]))
    (set-test-unit atoms/game-map "V" :target [4 4] :turns-remaining 1)
    (reset! atoms/player-map (make-initial-test-map 5 5 nil))
    (game-loop/move-satellites)
    ;; Satellite should be removed after round ends with turns-remaining at 0
    ;; Check that satellite is gone from both original and any moved position
    (let [sat-count (count (for [i (range 5) j (range 5)
                                 :let [cell (get-in @atoms/game-map [i j])]
                                 :when (= :satellite (:type (:contents cell)))]
                             [i j]))]
      (should= 0 sat-count)))

  (it "dies after correct number of rounds"
    (reset! atoms/game-map (build-test-map ["#####"
                                             "#####"
                                             "##V##"
                                             "#####"
                                             "#####"]))
    (set-test-unit atoms/game-map "V" :target [4 4] :turns-remaining 5)
    (reset! atoms/player-map (make-initial-test-map 5 5 nil))
    ;; Run 4 rounds - satellite should still exist
    (dotimes [_ 4]
      (game-loop/move-satellites))
    (let [sat-count (count (for [i (range 5) j (range 5)
                                 :let [cell (get-in @atoms/game-map [i j])]
                                 :when (= :satellite (:type (:contents cell)))]
                             [i j]))]
      (should= 1 sat-count))
    ;; Run 1 more round - satellite should be removed
    (game-loop/move-satellites)
    (let [sat-count (count (for [i (range 5) j (range 5)
                                 :let [cell (get-in @atoms/game-map [i j])]
                                 :when (= :satellite (:type (:contents cell)))]
                             [i j]))]
      (should= 0 sat-count)))

  (it "dies through full game loop with start-new-round"
    (reset! atoms/game-map (build-test-map ["#####"
                                             "#####"
                                             "##V##"
                                             "#####"
                                             "#####"]))
    (set-test-unit atoms/game-map "V" :target [4 4] :turns-remaining 3)
    (reset! atoms/player-map (make-initial-test-map 5 5 nil))
    (reset! atoms/player-items [])
    (reset! atoms/production {})
    (reset! atoms/round-number 0)
    ;; Run 3 full rounds via start-new-round
    (dotimes [_ 3]
      (game-loop/start-new-round)
      ;; Process all player items (the satellite should be skipped because it has a target)
      (while (seq @atoms/player-items)
        (game-loop/advance-game)))
    ;; Satellite should be dead after 3 rounds
    (let [sat-count (count (for [i (range 5) j (range 5)
                                 :let [cell (get-in @atoms/game-map [i j])]
                                 :when (= :satellite (:type (:contents cell)))]
                             [i j]))]
      (should= 0 sat-count)))

  (it "dies even when bouncing off corners multiple times"
    ;; Satellite starting near corner, will bounce multiple times in 5 turns
    (reset! atoms/game-map (build-test-map ["###"
                                             "#V#"
                                             "###"]))
    (set-test-unit atoms/game-map "V" :target [2 2] :turns-remaining 5)
    (reset! atoms/player-map (make-initial-test-map 3 3 nil))
    ;; Run 5 rounds - satellite should die
    (dotimes [_ 5]
      (game-loop/move-satellites))
    (let [sat-count (count (for [i (range 3) j (range 3)
                                 :let [cell (get-in @atoms/game-map [i j])]
                                 :when (= :satellite (:type (:contents cell)))]
                             [i j]))]
      (should= 0 sat-count)))

  (it "is removed from visibility map when it dies"
    (reset! atoms/game-map (build-test-map ["#####"
                                             "#####"
                                             "##V##"
                                             "#####"
                                             "#####"]))
    (set-test-unit atoms/game-map "V" :target [4 4] :turns-remaining 1)
    (reset! atoms/player-map (make-initial-test-map 5 5 nil))
    ;; Update visibility so satellite appears on player-map
    (update-cell-visibility [2 2] :player)
    ;; Verify satellite is visible
    (should= :satellite (:type (:contents (get-in @atoms/player-map [2 2]))))
    ;; Run one round - satellite should die and be removed from both maps
    (game-loop/move-satellites)
    ;; Verify satellite is gone from both maps
    (should-be-nil (get-test-unit atoms/game-map "V"))
    (should-be-nil (get-test-unit atoms/player-map "V")))

  (it "reveals two rectangular rings around its position"
    (reset! atoms/game-map (build-test-map ["#####"
                                             "#####"
                                             "##V##"
                                             "#####"
                                             "#####"]))
    (set-test-unit atoms/game-map "V" :target [4 4] :turns-remaining 50)
    (reset! atoms/player-map (make-initial-test-map 5 5 nil))
    (update-cell-visibility [2 2] :player)
    ;; All 25 cells in the 5x5 map should be visible (rings 1 and 2 plus center)
    (doseq [row (range 5)
            col (range 5)]
      (should (get-in @atoms/player-map [row col])))))
