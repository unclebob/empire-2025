(ns empire.movement.satellite-spec
  (:require [empire.test-utils :as test-utils]
    [empire.game-loop.core :as game-loop]
    [empire.movement.api :refer [set-unit-movement]]
    [empire.movement.visibility :refer [update-cell-visibility]]
    [empire.movement.satellite :as sat :refer [move-satellite calculate-satellite-target]]
    [empire.test-utils :refer [build-test-map set-test-unit get-test-unit reset-all-atoms! set-test-player-map! set-test-computer-map! make-initial-test-map
                               set-test-world!]]
    [speclj.core :refer :all]))

(describe "calculate-satellite-target"
  (before (reset-all-atoms!))
  (it "extends target to boundary in direction of travel"
    (set-test-world! (make-initial-test-map 5 5 {:type :land}))
    ;; From [1 1] toward [2 2] should extend to [4 4]
    (should= [4 4] (calculate-satellite-target [1 1] [2 2])))

  (it "extends target to right edge when moving east"
    (set-test-world! (make-initial-test-map 5 5 {:type :land}))
    (should= [2 4] (calculate-satellite-target [2 1] [2 2])))

  (it "extends target to bottom edge when moving south"
    (set-test-world! (make-initial-test-map 5 5 {:type :land}))
    (should= [4 2] (calculate-satellite-target [1 2] [2 2])))

  (it "extends target to top-left corner when moving northwest"
    (set-test-world! (make-initial-test-map 5 5 {:type :land}))
    (should= [0 0] (calculate-satellite-target [2 2] [1 1]))))

(describe "satellite movement"
  (before (reset-all-atoms!))
  (it "does not move without a target"
    (set-test-world! (build-test-map ["###"
                                             "#V#"
                                             "###"]))
    (set-test-unit (test-utils/game-map-atom) "V" :turns-remaining 50)
    (set-test-player-map! (make-initial-test-map 3 3 nil))
    (move-satellite [1 1])
    ;; Satellite should stay in place - no target set
    (should (:contents (get-in (test-utils/read-test-state :game-map) [1 1])))
    (should-be-nil (:target (:contents (get-in (test-utils/read-test-state :game-map) [1 1])))))

  (it "still decrements turns even without a target"
    (set-test-world! (build-test-map ["###"
                                             "#V#"
                                             "###"]))
    (set-test-unit (test-utils/game-map-atom) "V" :turns-remaining 5)
    (set-test-player-map! (make-initial-test-map 3 3 nil))
    ;; Run move-satellites (which calls move-satellite-steps)
    (game-loop/move-satellites)
    ;; Satellite should still be at [1 1] but with decremented turns
    (let [sat (:contents (get-in (test-utils/read-test-state :game-map) [1 1]))]
      (should sat)
      (should= 4 (:turns-remaining sat))))

  (it "reflects when hop-over chain runs off-map due to blocker at map edge"
    (set-test-world! (build-test-map ["VAa"
                                      "###"
                                      "###"]))
    (set-test-unit (test-utils/game-map-atom) "V" :direction [1 0] :turns-remaining 50)
    (set-test-player-map! (make-initial-test-map 3 3 nil))
    (with-redefs [empire.movement.satellite/bounce-direction (fn [& _] [0 1])]
      (move-satellite [0 0]))
    ;; East path is fully blocked to edge; reflection should move inward (south).
    (should (:contents (get-in (test-utils/read-test-state :game-map) [0 1])))
    (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))))

  (it "moves toward its target"
    (set-test-world! (build-test-map ["####"
                                             "#V##"
                                             "####"
                                             "####"]))
    (set-test-unit (test-utils/game-map-atom) "V" :target [3 3] :turns-remaining 50)
    (set-test-player-map! (make-initial-test-map 4 4 nil))
    (move-satellite [1 1])
    ;; Satellite should have moved toward target [3 3], so to [2 2]
    (should (:contents (get-in (test-utils/read-test-state :game-map) [2 2])))
    (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [1 1])))
    (should= [3 3] (:target (:contents (get-in (test-utils/read-test-state :game-map) [2 2])))))

  (it "moves horizontally when target is directly east"
    (set-test-world! (build-test-map ["#####"
                                             "#V###"
                                             "#####"]))
    (set-test-unit (test-utils/game-map-atom) "V" :target [4 1] :turns-remaining 50)
    (set-test-player-map! (make-initial-test-map 3 5 nil))
    (move-satellite [1 1])
    ;; Satellite should move east to [2 1]
    (should (:contents (get-in (test-utils/read-test-state :game-map) [2 1])))
    (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [1 1]))))

  (it "moves vertically when target is directly south"
    (set-test-world! (build-test-map ["###"
                                             "#V#"
                                             "###"
                                             "###"
                                             "###"]))
    (set-test-unit (test-utils/game-map-atom) "V" :target [1 4] :turns-remaining 50)
    (set-test-player-map! (make-initial-test-map 5 3 nil))
    (move-satellite [1 1])
    ;; Satellite should move south to [1 2]
    (should (:contents (get-in (test-utils/read-test-state :game-map) [1 2])))
    (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [1 1]))))

  (it "gets new target on opposite boundary when reaching right edge"
    (set-test-world! (build-test-map ["###"
                                             "##V"
                                             "###"]))
    (set-test-unit (test-utils/game-map-atom) "V" :target [2 1] :turns-remaining 50)
    (set-test-player-map! (make-initial-test-map 3 3 nil))
    (move-satellite [2 1])
    ;; Satellite at target on right edge should get new target on left edge (column 0)
    (let [sat (:contents (get-in (test-utils/read-test-state :game-map) [2 1]))]
      (should sat)
      (should= 0 (first (:target sat)))))

  (it "gets new target on opposite boundary when reaching bottom edge"
    (set-test-world! (build-test-map ["###"
                                             "###"
                                             "#V#"]))
    (set-test-unit (test-utils/game-map-atom) "V" :target [1 2] :turns-remaining 50)
    (set-test-player-map! (make-initial-test-map 3 3 nil))
    (move-satellite [1 2])
    ;; Satellite at target on bottom edge should get new target on top edge (row 0)
    (let [sat (:contents (get-in (test-utils/read-test-state :game-map) [1 2]))]
      (should sat)
      (should= 0 (second (:target sat)))))

  (it "gets new target on one of opposite boundaries when at corner"
    (set-test-world! (build-test-map ["###"
                                             "###"
                                             "##V"]))
    (set-test-unit (test-utils/game-map-atom) "V" :target [2 2] :turns-remaining 50)
    (set-test-player-map! (make-initial-test-map 3 3 nil))
    (move-satellite [2 2])
    ;; Satellite at corner should get new target on either top edge (row 0) or left edge (column 0)
    (let [sat (:contents (get-in (test-utils/read-test-state :game-map) [2 2]))
          [tx ty] (:target sat)]
      (should sat)
      (should (or (= tx 0) (= ty 0)))))

  (it "extends non-boundary target to wall when setting movement"
    (set-test-world! (build-test-map ["#####"
                                             "#V###"
                                             "#####"
                                             "#####"
                                             "#####"]))
    (set-test-unit (test-utils/game-map-atom) "V" :mode :awake :turns-remaining 50)
    (set-test-player-map! (make-initial-test-map 5 5 nil))
    ;; Set movement to non-boundary target [2 2] - should extend to [4 4]
    (set-unit-movement [1 1] [2 2])
    (let [sat (:contents (get-in (test-utils/read-test-state :game-map) [1 1]))
          [tx ty] (:target sat)]
      (should sat)
      (should= :moving (:mode sat))
      ;; Target should be extended to boundary at [4 4] (southeast corner)
      (should= [4 4] [tx ty])))

  (it "decrements turns-remaining once per round not per step"
    (set-test-world! (build-test-map ["#####"
                                             "#V###"
                                             "#####"
                                             "#####"
                                             "#####"]))
    (set-test-unit (test-utils/game-map-atom) "V" :target [4 4] :turns-remaining 50)
    (set-test-player-map! (make-initial-test-map 5 5 nil))
    (game-loop/move-satellites)
    ;; After one round of movement (10 steps), turns-remaining should only decrement by 1
    (let [{:keys [unit]} (get-test-unit (test-utils/game-map-atom) "V")]
      (should= 49 (:turns-remaining unit))))

  (it "is removed when turns-remaining reaches zero"
    (set-test-world! (build-test-map ["#####"
                                             "#####"
                                             "##V##"
                                             "#####"
                                             "#####"]))
    (set-test-unit (test-utils/game-map-atom) "V" :target [4 4] :turns-remaining 1)
    (set-test-player-map! (make-initial-test-map 5 5 nil))
    (game-loop/move-satellites)
    ;; Satellite should be removed after round ends with turns-remaining at 0
    ;; Check that satellite is gone from both original and any moved position
    (let [sat-count (count (for [i (range 5) j (range 5)
                                 :let [cell (get-in (test-utils/read-test-state :game-map) [i j])]
                                 :when (= :satellite (:type (:contents cell)))]
                             [i j]))]
      (should= 0 sat-count)))

  (it "dies after correct number of rounds"
    (set-test-world! (build-test-map ["#####"
                                             "#####"
                                             "##V##"
                                             "#####"
                                             "#####"]))
    (set-test-unit (test-utils/game-map-atom) "V" :target [4 4] :turns-remaining 5)
    (set-test-player-map! (make-initial-test-map 5 5 nil))
    ;; Run 4 rounds - satellite should still exist
    (dotimes [_ 4]
      (game-loop/move-satellites))
    (let [sat-count (count (for [i (range 5) j (range 5)
                                 :let [cell (get-in (test-utils/read-test-state :game-map) [i j])]
                                 :when (= :satellite (:type (:contents cell)))]
                             [i j]))]
      (should= 1 sat-count))
    ;; Run 1 more round - satellite should be removed
    (game-loop/move-satellites)
    (let [sat-count (count (for [i (range 5) j (range 5)
                                 :let [cell (get-in (test-utils/read-test-state :game-map) [i j])]
                                 :when (= :satellite (:type (:contents cell)))]
                             [i j]))]
      (should= 0 sat-count)))

  (it "dies through full game loop with start-new-round"
    (set-test-world! (build-test-map ["#####"
                                             "#####"
                                             "##V##"
                                             "#####"
                                             "#####"]))
    (set-test-unit (test-utils/game-map-atom) "V" :target [4 4] :turns-remaining 3)
    (set-test-player-map! (make-initial-test-map 5 5 nil))
    (test-utils/set-test-state! :player-items [])
    (test-utils/set-test-state! :production {})
    (test-utils/set-test-state! :round-number 0)
    ;; Run 3 full rounds via start-new-round
    (dotimes [_ 3]
      (game-loop/start-new-round)
      ;; Process all player items (the satellite should be skipped because it has a target)
      (while (seq (test-utils/read-test-state :player-items))
        (game-loop/advance-game)))
    ;; Satellite should be dead after 3 rounds
    (let [sat-count (count (for [i (range 5) j (range 5)
                                 :let [cell (get-in (test-utils/read-test-state :game-map) [i j])]
                                 :when (= :satellite (:type (:contents cell)))]
                             [i j]))]
      (should= 0 sat-count)))

  (it "dies even when bouncing off corners multiple times"
    ;; Satellite starting near corner, will bounce multiple times in 5 turns
    (set-test-world! (build-test-map ["###"
                                             "#V#"
                                             "###"]))
    (set-test-unit (test-utils/game-map-atom) "V" :target [2 2] :turns-remaining 5)
    (set-test-player-map! (make-initial-test-map 3 3 nil))
    ;; Run 5 rounds - satellite should die
    (dotimes [_ 5]
      (game-loop/move-satellites))
    (let [sat-count (count (for [i (range 3) j (range 3)
                                 :let [cell (get-in (test-utils/read-test-state :game-map) [i j])]
                                 :when (= :satellite (:type (:contents cell)))]
                             [i j]))]
      (should= 0 sat-count)))

  (it "is removed from visibility map when it dies"
    (set-test-world! (build-test-map ["#####"
                                             "#####"
                                             "##V##"
                                             "#####"
                                             "#####"]))
    (set-test-unit (test-utils/game-map-atom) "V" :target [4 4] :turns-remaining 1)
    (set-test-player-map! (make-initial-test-map 5 5 nil))
    ;; Update visibility so satellite appears on player-map
    (update-cell-visibility [2 2] :player)
    ;; Verify satellite is visible
    (should= :satellite (:type (:contents (get-in (test-utils/read-test-state :player-map) [2 2]))))
    ;; Run one round - satellite should die and be removed from both maps
    (game-loop/move-satellites)
    ;; Verify satellite is gone from both maps
    (should-be-nil (get-test-unit (test-utils/game-map-atom) "V"))
    (should-be-nil (get-test-unit (test-utils/player-map-atom) "V")))

  (it "reveals two rectangular rings around its position"
    (set-test-world! (build-test-map ["#####"
                                             "#####"
                                             "##V##"
                                             "#####"
                                             "#####"]))
    (set-test-unit (test-utils/game-map-atom) "V" :target [4 4] :turns-remaining 50)
    (set-test-player-map! (make-initial-test-map 5 5 nil))
    (update-cell-visibility [2 2] :player)
    ;; All 25 cells in the 5x5 map should be visible (rings 1 and 2 plus center)
    (doseq [row (range 5)
            col (range 5)]
      (should (get-in (test-utils/read-test-state :player-map) [row col])))))

(describe "computer satellite direction-based movement"
  (before (reset-all-atoms!))

  (it "moves in straight line using :direction"
    (set-test-world! (build-test-map ["#####"
                                             "#####"
                                             "##v##"
                                             "#####"
                                             "#####"]))
    (set-test-unit (test-utils/game-map-atom) "v" :direction [1 1] :turns-remaining 50)
    (set-test-computer-map! (make-initial-test-map 5 5 nil))
    (move-satellite [2 2])
    ;; Should move one step in direction [1 1] to [3 3]
    (should (:contents (get-in (test-utils/read-test-state :game-map) [3 3])))
    (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [2 2])))
    (should= [1 1] (:direction (:contents (get-in (test-utils/read-test-state :game-map) [3 3])))))

  (it "bounces at map edge with new direction"
    (set-test-world! (build-test-map ["###"
                                             "###"
                                             "##v"]))
    (set-test-unit (test-utils/game-map-atom) "v" :direction [1 1] :turns-remaining 50)
    (set-test-computer-map! (make-initial-test-map 3 3 nil))
    (let [new-pos (move-satellite [2 2])]
      ;; At corner [2 2] with direction [1 1], should bounce to a valid cell
      (should-not= [2 2] new-pos)
      ;; New position should be in bounds
      (should (>= (first new-pos) 0))
      (should (< (first new-pos) 3))
      (should (>= (second new-pos) 0))
      (should (< (second new-pos) 3))
      ;; Satellite should be at new position with a new direction
      (let [sat (:contents (get-in (test-utils/read-test-state :game-map) new-pos))]
        (should sat)
        (should-not= [1 1] (:direction sat)))))

  (it "bounces at top edge"
    (set-test-world! (build-test-map ["#v###"
                                             "#####"
                                             "#####"
                                             "#####"
                                             "#####"]))
    (set-test-unit (test-utils/game-map-atom) "v" :direction [0 -1] :turns-remaining 50)
    (set-test-computer-map! (make-initial-test-map 5 5 nil))
    (let [new-pos (move-satellite [1 0])]
      ;; Should bounce away from top edge (drow >= 0)
      (should (>= (second new-pos) 0))
      (let [sat (:contents (get-in (test-utils/read-test-state :game-map) new-pos))
            [_ dy] (:direction sat)]
        (should (>= dy 0)))))

  (it "bounces at bottom edge"
    (set-test-world! (build-test-map ["#####"
                                             "#####"
                                             "#####"
                                             "#####"
                                             "#v###"]))
    (set-test-unit (test-utils/game-map-atom) "v" :direction [0 1] :turns-remaining 50)
    (set-test-computer-map! (make-initial-test-map 5 5 nil))
    (let [new-pos (move-satellite [1 4])]
      ;; Should bounce away from bottom edge (drow <= 0)
      (should (<= (second new-pos) 4))
      (let [sat (:contents (get-in (test-utils/read-test-state :game-map) new-pos))
            [_ dy] (:direction sat)]
        (should (<= dy 0)))))

  (it "satellite continues moving after bounce"
    (set-test-world! (build-test-map ["#####"
                                             "#####"
                                             "#####"
                                             "#####"
                                             "#v###"]))
    (set-test-unit (test-utils/game-map-atom) "v" :direction [1 0] :turns-remaining 50)
    (set-test-computer-map! (make-initial-test-map 5 5 nil))
    (let [pos1 (move-satellite [1 4])
          pos2 (move-satellite pos1)]
      ;; After bouncing, satellite should continue moving
      (should-not= pos1 pos2)
      (should (:contents (get-in (test-utils/read-test-state :game-map) pos2)))))

  (it "skips over a city when moving in direction"
    (set-test-world! (build-test-map ["##v##O##"]))
    (set-test-unit (test-utils/game-map-atom) "v" :direction [1 0] :turns-remaining 50)
    (set-test-computer-map! (make-initial-test-map 1 8 nil))
    (let [new-pos (move-satellite [2 0])]
      ;; Should skip city at [5 0] and land at [4 0] (one step east from [3 0]... wait, it starts at [2 0] going east, next is [3 0])
      ;; Actually: from [2 0], direction [1 0], next cell is [3 0] which is land — normal move
      (should= [3 0] new-pos)
      (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [2 0])))
      (should (:contents (get-in (test-utils/read-test-state :game-map) [3 0])))))

  (it "skips directly over adjacent city"
    (set-test-world! (build-test-map ["##vO####"]))
    (set-test-unit (test-utils/game-map-atom) "v" :direction [1 0] :turns-remaining 50)
    (set-test-computer-map! (make-initial-test-map 1 8 nil))
    (let [new-pos (move-satellite [2 0])]
      ;; Next cell [3 0] is a city — should skip to [4 0]
      (should= [4 0] new-pos)
      (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [2 0])))
      (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [3 0])))
      (should (:contents (get-in (test-utils/read-test-state :game-map) [4 0])))))

  (it "skips over a unit when moving in direction"
    (set-test-world! (build-test-map ["##va####"]))
    (set-test-unit (test-utils/game-map-atom) "v" :direction [1 0] :turns-remaining 50)
    (set-test-computer-map! (make-initial-test-map 1 8 nil))
    (let [new-pos (move-satellite [2 0])]
      ;; Next cell [3 0] has an army — should skip to [4 0]
      (should= [4 0] new-pos)
      (should (:contents (get-in (test-utils/read-test-state :game-map) [3 0])))  ;; army still there
      (should (:contents (get-in (test-utils/read-test-state :game-map) [4 0])))))

  (it "clears old satellite position on computer-map after long hop"
    (set-test-world! (build-test-map ["vaaaaaa#"]))
    (set-test-unit (test-utils/game-map-atom) "v" :direction [1 0] :turns-remaining 50)
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (let [new-pos (move-satellite [0 0])]
      (should= [7 0] new-pos)
      (should-be-nil (get-in (test-utils/read-test-state :computer-map) [0 0 :contents]))
      (should= :satellite (get-in (test-utils/read-test-state :computer-map) [7 0 :contents :type]))))

  (it "skips multiple consecutive blocked cells"
    (set-test-world! (build-test-map ["##vOO###"]))
    (set-test-unit (test-utils/game-map-atom) "v" :direction [1 0] :turns-remaining 50)
    (set-test-computer-map! (make-initial-test-map 1 8 nil))
    (let [new-pos (move-satellite [2 0])]
      ;; Cells [3 0] and [4 0] are both cities — should skip to [5 0]
      (should= [5 0] new-pos)))

  (it "bounces left when blocked chain runs off map at right edge"
    (set-test-world! (build-test-map ["##vO"]))
    (set-test-unit (test-utils/game-map-atom) "v" :direction [1 0] :turns-remaining 50)
    (set-test-computer-map! (make-initial-test-map 1 4 nil))
    (with-redefs [empire.movement.satellite/bounce-direction (fn [& _] [-1 0])]
      (let [new-pos (move-satellite [2 0])]
        ;; Blocked chain reaches right edge; satellite bounces left to [1 0]
        (should= [1 0] new-pos)
        (should= [-1 0] (get-in (test-utils/read-test-state :game-map) [1 0 :contents :direction]))))))

  (it "player satellite without :direction uses target-based movement"
    (set-test-world! (build-test-map ["#####"
                                             "#V###"
                                             "#####"
                                             "#####"
                                             "#####"]))
    (set-test-unit (test-utils/game-map-atom) "V" :target [4 4] :turns-remaining 50)
    (set-test-player-map! (make-initial-test-map 5 5 nil))
    (move-satellite [1 1])
    ;; Player satellite should use target-based movement, moving toward [4 4]
    (should (:contents (get-in (test-utils/read-test-state :game-map) [2 2])))
    (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [1 1]))))

(describe "player satellite skipping"
  (before (reset-all-atoms!))

  (it "skips over a city when moving toward target"
    (set-test-world! (build-test-map ["#VO####"]))
    (set-test-unit (test-utils/game-map-atom) "V" :target [6 0] :turns-remaining 50)
    (set-test-player-map! (make-initial-test-map 1 7 nil))
    (let [new-pos (move-satellite [1 0])]
      ;; Next cell [2 0] is a player city — should skip to [3 0]
      (should= [3 0] new-pos)
      (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [1 0])))
      (should (:contents (get-in (test-utils/read-test-state :game-map) [3 0])))))

  (it "skips over a unit when moving toward target"
    (set-test-world! (build-test-map ["#VA####"]))
    (set-test-unit (test-utils/game-map-atom) "V" :target [6 0] :turns-remaining 50)
    (set-test-player-map! (make-initial-test-map 1 7 nil))
    (let [new-pos (move-satellite [1 0])]
      ;; Next cell [2 0] has a player army — should skip to [3 0]
      (should= [3 0] new-pos)
      (should (:contents (get-in (test-utils/read-test-state :game-map) [2 0])))  ;; army still there
      (should (:contents (get-in (test-utils/read-test-state :game-map) [3 0])))))

  (it "clears old satellite position on player-map after long hop"
    (set-test-world! (build-test-map ["VAAAAAA#"]))
    (set-test-unit (test-utils/game-map-atom) "V" :target [7 0] :turns-remaining 50)
    (set-test-player-map! (test-utils/read-test-state :game-map))
    (let [new-pos (move-satellite [0 0])]
      (should= [7 0] new-pos)
      (should-be-nil (get-in (test-utils/read-test-state :player-map) [0 0 :contents]))
      (should= :satellite (get-in (test-utils/read-test-state :player-map) [7 0 :contents :type]))))

  (it "skips over a free city"
    (set-test-world! (build-test-map ["#V+####"]))
    (set-test-unit (test-utils/game-map-atom) "V" :target [6 0] :turns-remaining 50)
    (set-test-player-map! (make-initial-test-map 1 7 nil))
    (let [new-pos (move-satellite [1 0])]
      ;; Next cell [2 0] is a free city — should skip to [3 0]
      (should= [3 0] new-pos)))

  (it "skips over a computer city"
    (set-test-world! (build-test-map ["#VX####"]))
    (set-test-unit (test-utils/game-map-atom) "V" :target [6 0] :turns-remaining 50)
    (set-test-player-map! (make-initial-test-map 1 7 nil))
    (let [new-pos (move-satellite [1 0])]
      ;; Next cell [2 0] is a computer city — should skip to [3 0]
      (should= [3 0] new-pos))))

(describe "satellite target recalculation at edges"
  (before (reset-all-atoms!))

  (it "recalculates to bottom when at top edge non-corner"
    (set-test-world! (build-test-map ["#####"
                                             "#####"
                                             "V####"
                                             "#####"
                                             "#####"]))
    (set-test-unit (test-utils/game-map-atom) "V" :target [0 2] :turns-remaining 50)
    (set-test-player-map! (make-initial-test-map 5 5 nil))
    (with-redefs [rand-int (constantly 2)]
      (move-satellite [0 2])
      (should= [4 2] (:target (:contents (get-in (test-utils/read-test-state :game-map) [0 2]))))))

  (it "recalculates to right when at left edge non-corner"
    (set-test-world! (build-test-map ["##V##"
                                             "#####"
                                             "#####"
                                             "#####"
                                             "#####"]))
    (set-test-unit (test-utils/game-map-atom) "V" :target [2 0] :turns-remaining 50)
    (set-test-player-map! (make-initial-test-map 5 5 nil))
    (with-redefs [rand-int (constantly 2)]
      (move-satellite [2 0])
      (should= [2 4] (:target (:contents (get-in (test-utils/read-test-state :game-map) [2 0]))))))

  (it "recalculates to bottom at top-left corner when rand=0"
    (set-test-world! (build-test-map ["V####"
                                             "#####"
                                             "#####"
                                             "#####"
                                             "#####"]))
    (set-test-unit (test-utils/game-map-atom) "V" :target [0 0] :turns-remaining 50)
    (set-test-player-map! (make-initial-test-map 5 5 nil))
    (let [vals (atom [0 2])]
      (with-redefs [rand-int (fn [_] (let [v (first @vals)] (swap! vals rest) v))]
        (move-satellite [0 0])
        (should= [4 2] (:target (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))))))

  (it "recalculates to top at bottom-left corner when rand=0"
    (set-test-world! (build-test-map ["####V"
                                             "#####"
                                             "#####"
                                             "#####"
                                             "#####"]))
    (set-test-unit (test-utils/game-map-atom) "V" :target [4 0] :turns-remaining 50)
    (set-test-player-map! (make-initial-test-map 5 5 nil))
    (let [vals (atom [0 2])]
      (with-redefs [rand-int (fn [_] (let [v (first @vals)] (swap! vals rest) v))]
        (move-satellite [4 0])
        (should= [0 2] (:target (:contents (get-in (test-utils/read-test-state :game-map) [4 0])))))))

  (it "recalculates to right at top-left corner when rand=1"
    (set-test-world! (build-test-map ["V####"
                                             "#####"
                                             "#####"
                                             "#####"
                                             "#####"]))
    (set-test-unit (test-utils/game-map-atom) "V" :target [0 0] :turns-remaining 50)
    (set-test-player-map! (make-initial-test-map 5 5 nil))
    (let [vals (atom [1 3])]
      (with-redefs [rand-int (fn [_] (let [v (first @vals)] (swap! vals rest) v))]
        (move-satellite [0 0])
        (should= [3 4] (:target (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))))))))

(describe "satellite skips blocked cells near boundaries"
  (before (reset-all-atoms!))

  (it "scans past city and lands at row 0"
    (set-test-world! (build-test-map ["#####"
                                             "#####"
                                             "#####"
                                             "#OV##"
                                             "#####"]))
    (set-test-unit (test-utils/game-map-atom) "V" :target [0 3] :turns-remaining 50)
    (set-test-player-map! (make-initial-test-map 5 5 nil))
    (let [new-pos (move-satellite [2 3])]
      (should= [0 3] new-pos)))

  (it "stays put when scan hits column boundary"
    (set-test-world! (build-test-map ["#####"
                                             "#####"
                                             "##V##"
                                             "##O##"
                                             "##O##"]))
    (set-test-unit (test-utils/game-map-atom) "V" :target [2 8] :turns-remaining 50)
    (set-test-player-map! (make-initial-test-map 5 5 nil))
    (let [new-pos (move-satellite [2 2])]
      (should= [2 2] new-pos)))

  (it "scans forward not backward past blocked cell"
    (set-test-world! (build-test-map ["##V##"
                                             "##O##"
                                             "#####"
                                             "#####"
                                             "#####"]))
    (set-test-unit (test-utils/game-map-atom) "V" :target [2 4] :turns-remaining 50)
    (set-test-player-map! (make-initial-test-map 5 5 nil))
    (let [new-pos (move-satellite [2 0])]
      (should= [2 2] new-pos))))

(describe "computer satellite boundary and bounce"
  (before (reset-all-atoms!))

  (it "moves to row 0 boundary"
    (set-test-world! (build-test-map ["#####"
                                             "#####"
                                             "#v###"
                                             "#####"
                                             "#####"]))
    (set-test-unit (test-utils/game-map-atom) "v" :direction [-1 0] :turns-remaining 50)
    (set-test-computer-map! (make-initial-test-map 5 5 nil))
    (let [new-pos (move-satellite [1 2])]
      (should= [0 2] new-pos)))

  (it "bounces at bottom edge"
    (set-test-world! (build-test-map ["#####"
                                             "#####"
                                             "####v"
                                             "#####"
                                             "#####"]))
    (set-test-unit (test-utils/game-map-atom) "v" :direction [1 0] :turns-remaining 50)
    (set-test-computer-map! (make-initial-test-map 5 5 nil))
    (with-redefs [rand-nth first]
      (let [new-pos (move-satellite [4 2])]
        (should-not= [4 2] new-pos)
        (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [4 2]))))))

  (it "bounces in correct direction"
    (set-test-world! (build-test-map ["##v##"
                                             "#####"
                                             "#####"
                                             "#####"
                                             "#####"]))
    (set-test-unit (test-utils/game-map-atom) "v" :direction [0 -1] :turns-remaining 50)
    (set-test-computer-map! (make-initial-test-map 5 5 nil))
    (with-redefs [rand-nth first]
      (let [new-pos (move-satellite [2 0])]
        (should= [1 0] new-pos))))

  (it "stays put when bounce destination is blocked"
    (set-test-world! (build-test-map ["#####"
                                             "+####"
                                             "v####"
                                             "#####"
                                             "#####"]))
    (set-test-unit (test-utils/game-map-atom) "v" :direction [-1 0] :turns-remaining 50)
    (set-test-computer-map! (make-initial-test-map 5 5 nil))
    (with-redefs [rand-nth first]
      (let [new-pos (move-satellite [0 2])]
        (should= [0 2] new-pos)))))

(describe "boundary-type classification"
  (it "returns :corner for top-left"
    (should= :corner (#'sat/boundary-type [0 0] 5 5)))
  (it "returns :corner for bottom-right"
    (should= :corner (#'sat/boundary-type [4 4] 5 5)))
  (it "returns :row for top edge non-corner"
    (should= :row (#'sat/boundary-type [0 2] 5 5)))
  (it "returns :row for bottom edge non-corner"
    (should= :row (#'sat/boundary-type [4 2] 5 5)))
  (it "returns :col for left edge non-corner"
    (should= :col (#'sat/boundary-type [2 0] 5 5)))
  (it "returns :col for right edge non-corner"
    (should= :col (#'sat/boundary-type [2 4] 5 5)))
  (it "returns nil for interior position"
    (should-be-nil (#'sat/boundary-type [2 2] 5 5))))
