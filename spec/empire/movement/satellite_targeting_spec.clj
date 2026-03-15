(ns empire.game-mechanics.movement.satellite-targeting-spec
  (:require [empire.test.utils :as test-utils]
            [empire.game.loop.core :as game-loop]
            [empire.game-mechanics.movement.api :refer [set-unit-movement]]
            [empire.game-mechanics.movement.visibility :refer [update-cell-visibility]]
            [empire.game-mechanics.movement.satellite :as sat :refer [move-satellite calculate-satellite-target]]
            [empire.test.utils :refer [build-test-map set-test-unit get-test-unit reset-all-atoms! set-test-player-map! set-test-computer-map! make-initial-test-map
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
