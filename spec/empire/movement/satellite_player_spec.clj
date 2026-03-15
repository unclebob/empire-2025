(ns empire.game-mechanics.movement.satellite-player-spec
  (:require [empire.test.utils :as test-utils]
            [empire.game.loop.core :as game-loop]
            [empire.game-mechanics.movement.api :refer [set-unit-movement]]
            [empire.game-mechanics.movement.visibility :refer [update-cell-visibility]]
            [empire.game-mechanics.movement.satellite :as sat :refer [move-satellite calculate-satellite-target]]
            [empire.test.utils :refer [build-test-map set-test-unit get-test-unit reset-all-atoms! set-test-player-map! set-test-computer-map! make-initial-test-map
                                       set-test-world!]]
            [speclj.core :refer :all]))
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
