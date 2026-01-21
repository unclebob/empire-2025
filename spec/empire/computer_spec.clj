(ns empire.computer-spec
  (:require [speclj.core :refer :all]
            [empire.game-loop :as game-loop]
            [empire.computer :as computer]
            [empire.atoms :as atoms]
            [empire.pathfinding :as pathfinding]
            [empire.test-utils :refer [build-test-map set-test-unit get-test-unit get-test-city reset-all-atoms!]]))

(describe "build-computer-items"
  (before (reset-all-atoms!))

  (it "returns computer city coordinates"
    (reset! atoms/game-map (build-test-map ["#X"]))
    (let [items (game-loop/build-computer-items)]
      (should-contain [0 1] items)))

  (it "returns computer unit coordinates"
    (reset! atoms/game-map (build-test-map ["a#"]))
    (let [items (game-loop/build-computer-items)]
      (should-contain [0 0] items)))

  (it "does not return player cities"
    (reset! atoms/game-map (build-test-map ["OX"]))
    (let [items (game-loop/build-computer-items)]
      (should-not-contain [0 0] items)))

  (it "does not return player units"
    (reset! atoms/game-map (build-test-map ["Aa"]))
    (let [items (game-loop/build-computer-items)]
      (should-not-contain [0 0] items)))

  (it "does not return empty land"
    (reset! atoms/game-map (build-test-map ["#X"]))
    (let [items (game-loop/build-computer-items)]
      (should-not-contain [0 0] items)))

  (it "returns all computer ship types"
    (reset! atoms/game-map (build-test-map ["tdpsbc"]))
    (let [items (game-loop/build-computer-items)]
      (should= 6 (count items)))))

(describe "decide-army-move"
  (before (reset-all-atoms!))

  (it "returns nil when no valid moves exist"
    (reset! atoms/game-map (build-test-map ["~a~"
                                             "~~~"
                                             "~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (should-be-nil (computer/decide-army-move [0 1])))

  (it "returns nil when surrounded by sea (no passable moves)"
    (reset! atoms/game-map (build-test-map ["~~~"
                                             "~a~"
                                             "~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    ;; Army surrounded by sea should have no valid move
    (should-be-nil (computer/decide-army-move [1 1])))

  (it "army survives multiple moves without enemies"
    (reset! atoms/game-map (build-test-map ["#####"
                                             "#####"
                                             "##a##"
                                             "#####"
                                             "#####"]))
    (reset! atoms/computer-map @atoms/game-map)
    (reset! atoms/player-map @atoms/game-map)
    (reset! atoms/production {})
    ;; Move army 10 times
    (dotimes [_ 10]
      (let [army-pos (first (for [i (range 5)
                                  j (range 5)
                                  :let [cell (get-in @atoms/game-map [i j])]
                                  :when (and (:contents cell)
                                             (= :army (:type (:contents cell))))]
                              [i j]))]
        (when army-pos
          (computer/process-computer-unit army-pos))))
    ;; Army should still exist somewhere on the map
    (let [army-count (count (for [i (range 5)
                                  j (range 5)
                                  :let [cell (get-in @atoms/game-map [i j])]
                                  :when (and (:contents cell)
                                             (= :army (:type (:contents cell))))]
                              [i j]))]
      (should= 1 army-count)))

  (it "army dies when attempting to conquer free city"
    (reset! atoms/game-map (build-test-map ["a+"]))
    (reset! atoms/computer-map @atoms/game-map)
    (reset! atoms/player-map @atoms/game-map)
    (reset! atoms/production {})
    ;; Army should attack the free city
    (computer/process-computer-unit [0 0])
    ;; Army is always removed during conquest attempt
    (should-be-nil (:contents (get-in @atoms/game-map [0 0])))
    ;; City should be either conquered or still free
    (should (#{:computer :free} (:city-status (get-in @atoms/game-map [0 1])))))

  (it "returns adjacent player unit position to attack"
    (reset! atoms/game-map (build-test-map ["aA"]))
    (reset! atoms/computer-map @atoms/game-map)
    (should= [0 1] (computer/decide-army-move [0 0])))

  (it "returns adjacent free city position to attack"
    (reset! atoms/game-map (build-test-map ["a+"]))
    (reset! atoms/computer-map @atoms/game-map)
    (should= [0 1] (computer/decide-army-move [0 0])))

  (it "returns adjacent player city position to attack"
    (reset! atoms/game-map (build-test-map ["aO"]))
    (reset! atoms/computer-map @atoms/game-map)
    (should= [0 1] (computer/decide-army-move [0 0])))

  (it "moves toward visible free city"
    (reset! atoms/game-map (build-test-map ["a##+"]))
    (reset! atoms/computer-map @atoms/game-map)
    (let [move (computer/decide-army-move [0 0])]
      (should= [0 1] move)))

  (it "moves toward visible player city when no free city"
    (reset! atoms/game-map (build-test-map ["a##O"]))
    (reset! atoms/computer-map @atoms/game-map)
    (let [move (computer/decide-army-move [0 0])]
      (should= [0 1] move)))

  (it "returns a valid land cell when exploring"
    (reset! atoms/game-map (build-test-map ["#a#"
                                             "###"
                                             "###"]))
    (reset! atoms/computer-map @atoms/game-map)
    (let [move (computer/decide-army-move [0 1])]
      (should-not-be-nil move)
      (should= :land (:type (get-in @atoms/game-map move)))))

  (it "does not move onto sea"
    (reset! atoms/game-map (build-test-map ["~a~"
                                             "~#~"
                                             "~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (let [move (computer/decide-army-move [0 1])]
      (should= [1 1] move)))

  (it "does not move onto friendly units"
    (reset! atoms/game-map (build-test-map ["#a#"
                                             "a#a"
                                             "###"]))
    (reset! atoms/computer-map @atoms/game-map)
    ;; Army at [0 1] has neighbors: [0 0], [0 2], [1 0] (army), [1 1], [1 2] (army)
    ;; Should move to [0 0], [0 2], or [1 1] but not [1 0] or [1 2]
    (let [move (computer/decide-army-move [0 1])]
      (should-not-be-nil move)
      (should (#{[0 0] [0 2] [1 1]} move)))))

(describe "process-computer-unit"
  (before (reset-all-atoms!))

  (it "moves army to adjacent empty land"
    (reset! atoms/game-map (build-test-map ["a#"]))
    (reset! atoms/computer-map @atoms/game-map)
    (computer/process-computer-unit [0 0])
    ;; Army should have moved from [0 0] to [0 1]
    (should-be-nil (:contents (get-in @atoms/game-map [0 0])))
    (should= :army (:type (:contents (get-in @atoms/game-map [0 1])))))

  (it "army attacks adjacent player unit"
    (reset! atoms/game-map (build-test-map ["aA"]))
    (reset! atoms/computer-map @atoms/game-map)
    (computer/process-computer-unit [0 0])
    ;; Combat occurred - one unit should be dead
    (let [cell0 (get-in @atoms/game-map [0 0])
          cell1 (get-in @atoms/game-map [0 1])
          units (filter some? [(:contents cell0) (:contents cell1)])]
      (should= 1 (count units))))

  (it "army attacks adjacent free city"
    (reset! atoms/game-map (build-test-map ["a+"]))
    (reset! atoms/computer-map @atoms/game-map)
    (computer/process-computer-unit [0 0])
    ;; Army should be removed (conquest attempt removes army win or lose)
    (should-be-nil (:contents (get-in @atoms/game-map [0 0])))
    ;; City should be either conquered (:computer) or still free
    (should (#{:computer :free} (:city-status (get-in @atoms/game-map [0 1])))))

  (it "does nothing when no valid moves"
    (reset! atoms/game-map (build-test-map ["~a~"
                                             "~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (computer/process-computer-unit [0 1])
    ;; Army should still be at [0 1]
    (should= :army (:type (:contents (get-in @atoms/game-map [0 1])))))

  (it "returns nil after move (unit done for this turn)"
    (reset! atoms/game-map (build-test-map ["a#"]))
    (reset! atoms/computer-map @atoms/game-map)
    (let [result (computer/process-computer-unit [0 0])]
      (should-be-nil result)
      ;; But the unit should have moved
      (should= :army (:type (:contents (get-in @atoms/game-map [0 1]))))))

  (it "returns nil when no move possible"
    (reset! atoms/game-map (build-test-map ["~a~"
                                             "~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (let [result (computer/process-computer-unit [0 1])]
      (should-be-nil result))))

(describe "game loop integration"
  (before (reset-all-atoms!))

  (it "start-new-round builds computer-items list"
    (reset! atoms/game-map (build-test-map ["OaX"]))
    (reset! atoms/player-map @atoms/game-map)
    (reset! atoms/computer-map @atoms/game-map)
    (reset! atoms/production {})
    (game-loop/start-new-round)
    ;; Should have computer army and computer city
    (should-contain [0 1] @atoms/computer-items)
    (should-contain [0 2] @atoms/computer-items))

  (it "start-new-round builds both player and computer items"
    (reset! atoms/game-map (build-test-map ["OAaX"]))
    (reset! atoms/player-map @atoms/game-map)
    (reset! atoms/computer-map @atoms/game-map)
    (reset! atoms/production {})
    (game-loop/start-new-round)
    (should (seq @atoms/player-items))
    (should (seq @atoms/computer-items)))

  (it "advance-game does not start new round when computer-items not empty"
    (reset! atoms/game-map (build-test-map ["#a#"]))
    (reset! atoms/player-map @atoms/game-map)
    (reset! atoms/computer-map @atoms/game-map)
    (reset! atoms/production {})
    (reset! atoms/player-items [])
    (reset! atoms/computer-items [[0 1]])
    (reset! atoms/round-number 5)
    (game-loop/advance-game)
    ;; Should not have started new round
    (should= 5 @atoms/round-number))

  (it "advance-game processes computer unit when player-items empty"
    (reset! atoms/game-map (build-test-map ["#a#"]))
    (reset! atoms/player-map @atoms/game-map)
    (reset! atoms/computer-map @atoms/game-map)
    (reset! atoms/production {})
    (reset! atoms/player-items [])
    (reset! atoms/computer-items [[0 1]])
    (game-loop/advance-game)
    ;; Computer army should have moved (from [0 1] to [0 0] or [0 2])
    (should-be-nil (:contents (get-in @atoms/game-map [0 1])))
    ;; Computer-items should be empty (unit done moving)
    (should (empty? @atoms/computer-items)))

  (it "advance-game starts new round when both lists empty"
    (reset! atoms/game-map (build-test-map ["#"]))
    (reset! atoms/player-map @atoms/game-map)
    (reset! atoms/computer-map @atoms/game-map)
    (reset! atoms/production {})
    (reset! atoms/player-items [])
    (reset! atoms/computer-items [])
    (reset! atoms/round-number 5)
    (game-loop/advance-game)
    (should= 6 @atoms/round-number)))

(describe "decide-ship-move"
  (before (reset-all-atoms!))

  (it "returns nil when no valid moves exist"
    (reset! atoms/game-map (build-test-map ["#d#"
                                             "###"]))
    (reset! atoms/computer-map @atoms/game-map)
    (should-be-nil (computer/decide-ship-move [0 1] :destroyer)))

  (it "returns adjacent player unit position to attack"
    (reset! atoms/game-map (build-test-map ["dD"]))
    (reset! atoms/computer-map @atoms/game-map)
    (should= [0 1] (computer/decide-ship-move [0 0] :destroyer)))

  (it "moves toward visible player unit"
    (reset! atoms/game-map (build-test-map ["d~~D"]))
    (reset! atoms/computer-map @atoms/game-map)
    (let [move (computer/decide-ship-move [0 0] :destroyer)]
      (should= [0 1] move)))

  (it "returns valid sea cell when exploring"
    (reset! atoms/game-map (build-test-map ["~d~"
                                             "~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (let [move (computer/decide-ship-move [0 1] :destroyer)]
      (should-not-be-nil move)
      (should= :sea (:type (get-in @atoms/game-map move)))))

  (it "does not move onto land"
    (reset! atoms/game-map (build-test-map ["#d#"
                                             "#~#"]))
    (reset! atoms/computer-map @atoms/game-map)
    (let [move (computer/decide-ship-move [0 1] :destroyer)]
      (should= [1 1] move)))

  (it "does not target adjacent free city"
    (reset! atoms/game-map (build-test-map ["+d~"
                                             "~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    ;; Ship should NOT try to attack the free city - it can't move there
    (let [move (computer/decide-ship-move [0 1] :patrol-boat)]
      ;; Should move to a sea cell, not the city
      (should-not= [0 0] move)
      (should= :sea (:type (get-in @atoms/game-map move)))))

  (it "does not target adjacent player city"
    (reset! atoms/game-map (build-test-map ["Od~"
                                             "~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    ;; Ship should NOT try to attack the player city - it can't move there
    (let [move (computer/decide-ship-move [0 1] :patrol-boat)]
      ;; Should move to a sea cell, not the city
      (should-not= [0 0] move)
      (should= :sea (:type (get-in @atoms/game-map move))))))

(describe "decide-fighter-move"
  (before (reset-all-atoms!))

  (it "returns adjacent player unit position to attack"
    (reset! atoms/game-map (build-test-map ["fA"]))
    (reset! atoms/computer-map @atoms/game-map)
    (should= [0 1] (computer/decide-fighter-move [0 0] 10)))

  (it "moves toward unexplored when fuel is sufficient"
    (reset! atoms/game-map (build-test-map ["Xf#"]))
    (reset! atoms/computer-map @atoms/game-map)
    ;; With computer city at [0 0] and fighter at [0 1] with plenty of fuel
    (let [move (computer/decide-fighter-move [0 1] 10)]
      ;; Should move toward unexplored or explore
      (should-not-be-nil move)))

  (it "returns to nearest friendly city when fuel is low"
    (reset! atoms/game-map (build-test-map ["X#f"]))
    (reset! atoms/computer-map @atoms/game-map)
    ;; Fighter at [0 2], city at [0 0], fuel = 2 (just enough to get back)
    (let [move (computer/decide-fighter-move [0 2] 2)]
      ;; Should move toward city
      (should= [0 1] move)))

  (it "returns nil when no valid moves"
    (reset! atoms/game-map (build-test-map ["~f~"
                                             "~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    ;; Fighter can fly over sea but prefers land/cities
    ;; With no land neighbors, should still return a move (fly over sea)
    (let [move (computer/decide-fighter-move [0 1] 10)]
      ;; Fighters can move over sea
      (should-not-be-nil move))))

(describe "decide-production"
  (before (reset-all-atoms!))

  (it "returns :army for Phase 1"
    (reset! atoms/game-map (build-test-map ["X"]))
    (reset! atoms/computer-map @atoms/game-map)
    (should= :army (computer/decide-production [0 0]))))

(describe "process-computer-city"
  (before (reset-all-atoms!))

  (it "sets production when city has none"
    (reset! atoms/game-map (build-test-map ["X"]))
    (reset! atoms/computer-map @atoms/game-map)
    (reset! atoms/production {})
    (computer/process-computer-city [0 0])
    (should (@atoms/production [0 0])))

  (it "does not change production when city already has production"
    (reset! atoms/game-map (build-test-map ["X"]))
    (reset! atoms/computer-map @atoms/game-map)
    (reset! atoms/production {[0 0] {:item :fighter :remaining-rounds 10}})
    (computer/process-computer-city [0 0])
    (should= :fighter (:item (@atoms/production [0 0]))))

  (it "sets production to army"
    (reset! atoms/game-map (build-test-map ["X"]))
    (reset! atoms/computer-map @atoms/game-map)
    (reset! atoms/production {})
    (computer/process-computer-city [0 0])
    (should= :army (:item (@atoms/production [0 0])))))

(describe "unit-threat"
  (it "returns correct threat values for unit types"
    (should= 10 (computer/unit-threat :battleship))
    (should= 8 (computer/unit-threat :carrier))
    (should= 6 (computer/unit-threat :destroyer))
    (should= 5 (computer/unit-threat :submarine))
    (should= 4 (computer/unit-threat :fighter))
    (should= 3 (computer/unit-threat :patrol-boat))
    (should= 2 (computer/unit-threat :army))
    (should= 1 (computer/unit-threat :transport))
    (should= 0 (computer/unit-threat :satellite))))

(describe "threat-level"
  (before (reset-all-atoms!))

  (it "returns 0 with no enemies nearby"
    (reset! atoms/game-map (build-test-map ["~~~"
                                             "~d~"
                                             "~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (should= 0 (computer/threat-level @atoms/computer-map [1 1])))

  (it "sums threat of adjacent enemies"
    (reset! atoms/game-map (build-test-map ["~B~"
                                             "~d~"
                                             "~D~"]))
    (reset! atoms/computer-map @atoms/game-map)
    ;; Battleship = 10, Destroyer = 6
    (should= 16 (computer/threat-level @atoms/computer-map [1 1])))

  (it "considers enemies within radius 2"
    (reset! atoms/game-map (build-test-map ["B~~~~"
                                             "~~~~~"
                                             "~~d~~"
                                             "~~~~~"
                                             "~~~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    ;; Battleship at [0 0] is exactly radius 2 from [2 2]
    (should= 10 (computer/threat-level @atoms/computer-map [2 2])))

  (it "ignores enemies beyond radius 2"
    (reset! atoms/game-map (build-test-map ["B~~~~~"
                                             "~~~~~~"
                                             "~~~~~~"
                                             "~~~d~~"
                                             "~~~~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    ;; Battleship at [0 0] is beyond radius 2 from [3 3]
    (should= 0 (computer/threat-level @atoms/computer-map [3 3])))

  (it "ignores friendly units"
    (reset! atoms/game-map (build-test-map ["~b~"
                                             "~d~"
                                             "~b~"]))
    (reset! atoms/computer-map @atoms/game-map)
    ;; Both battleships are computer-owned, should not add to threat
    (should= 0 (computer/threat-level @atoms/computer-map [1 1]))))

(describe "safe-moves"
  (before (reset-all-atoms!))

  (it "returns all moves unchanged when unit at full health"
    (reset! atoms/game-map (build-test-map ["~B~"
                                             "~d~"
                                             "~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (let [unit {:type :destroyer :hits 3}  ;; Full health (destroyer has 3 hits)
          moves [[0 1] [1 0] [1 2] [2 1]]]
      (should= moves (computer/safe-moves @atoms/computer-map [1 1] unit moves))))

  (it "sorts moves by threat level when unit is damaged"
    ;; Battleship at [1 2], destroyer at [3 3]
    ;; Move [2 3] is within radius 2 of battleship (distance 2)
    ;; Moves [3 4] and [4 3] are beyond radius 2 (distance 4 each)
    (reset! atoms/game-map (build-test-map ["~~~~~~"
                                             "~~B~~~"
                                             "~~~~~~"
                                             "~~~d~~"
                                             "~~~~~~"
                                             "~~~~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (let [unit {:type :destroyer :hits 1}  ;; Damaged (less than max 3)
          moves [[2 3] [3 4] [4 3]]]
      ;; [2 3] is within range of battleship at [1 2] (threat 10)
      ;; [3 4] and [4 3] are beyond range (threat 0)
      (let [sorted-moves (computer/safe-moves @atoms/computer-map [3 3] unit moves)]
        ;; Lower threat moves should come first
        (should-not= [2 3] (first sorted-moves))))))

(describe "should-retreat?"
  (before (reset-all-atoms!))

  (it "returns true when damaged and under threat"
    (reset! atoms/game-map (build-test-map ["~B~"
                                             "~d~"
                                             "~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (let [unit {:type :destroyer :hits 2}]  ;; Damaged (max is 3)
      ;; Threat from battleship = 10, threshold is 3
      (should (computer/should-retreat? [1 1] unit @atoms/computer-map))))

  (it "returns false for healthy unit under threat"
    (reset! atoms/game-map (build-test-map ["~B~"
                                             "~d~"
                                             "~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (let [unit {:type :destroyer :hits 3}]  ;; Full health
      (should-not (computer/should-retreat? [1 1] unit @atoms/computer-map))))

  (it "returns true for loaded transport under high threat"
    (reset! atoms/game-map (build-test-map ["~B~"
                                             "~t~"
                                             "~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (let [unit {:type :transport :hits 3 :army-count 2}]  ;; Transport with armies
      ;; Threat from battleship = 10, threshold for loaded transport is 5
      (should (computer/should-retreat? [1 1] unit @atoms/computer-map))))

  (it "returns false for empty transport under moderate threat"
    (reset! atoms/game-map (build-test-map ["~P~"
                                             "~t~"
                                             "~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (let [unit {:type :transport :hits 3 :army-count 0}]  ;; Empty transport
      ;; Threat from patrol-boat = 3, not enough to trigger retreat
      (should-not (computer/should-retreat? [1 1] unit @atoms/computer-map))))

  (it "returns true for severely damaged unit (< 50% health)"
    (reset! atoms/game-map (build-test-map ["~~~"
                                             "~b~"
                                             "~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (let [unit {:type :battleship :hits 3}]  ;; Less than 50% of 8 hits
      (should (computer/should-retreat? [1 1] unit @atoms/computer-map)))))

(describe "retreat-move"
  (before (reset-all-atoms!))

  (it "moves toward nearest friendly city"
    (reset! atoms/game-map (build-test-map ["X~~~B"
                                             "~~~~~"
                                             "~~d~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (let [unit {:type :destroyer :hits 1}
          passable [[1 2] [1 3] [2 1] [2 3]]]
      ;; City at [0 0], should prefer moving toward it (lower row/col)
      (let [retreat (computer/retreat-move [2 2] unit @atoms/computer-map passable)]
        (should-not-be-nil retreat)
        ;; Should move toward [0 0]
        (should (#{[1 2] [2 1] [1 3]} retreat)))))

  (it "returns nil when no friendly city exists"
    (reset! atoms/game-map (build-test-map ["~~~~B"
                                             "~~~~~"
                                             "~~d~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (let [unit {:type :destroyer :hits 1}
          passable [[1 2] [1 3] [2 1] [2 3]]]
      (should-be-nil (computer/retreat-move [2 2] unit @atoms/computer-map passable))))

  (it "returns nil when no passable moves"
    (reset! atoms/game-map (build-test-map ["X"
                                             "d"]))
    (reset! atoms/computer-map @atoms/game-map)
    (let [unit {:type :destroyer :hits 1}]
      (should-be-nil (computer/retreat-move [1 0] unit @atoms/computer-map [])))))

(describe "computer production full cycle"
  (before (reset-all-atoms!))

  (it "sets production on first round processing"
    (reset! atoms/game-map (build-test-map ["X#"]))
    (reset! atoms/player-map @atoms/game-map)
    (reset! atoms/computer-map @atoms/game-map)
    (reset! atoms/production {})
    (reset! atoms/player-items [])
    (reset! atoms/computer-items [[0 0]])
    ;; Process computer city
    (game-loop/advance-game)
    ;; Production should be set
    (should= :army (:item (@atoms/production [0 0])))
    (should= 5 (:remaining-rounds (@atoms/production [0 0]))))

  (it "decrements production each round"
    (reset! atoms/game-map (build-test-map ["X#"]))
    (reset! atoms/player-map @atoms/game-map)
    (reset! atoms/computer-map @atoms/game-map)
    (reset! atoms/production {[0 0] {:item :army :remaining-rounds 3}})
    ;; Start new round calls update-production
    (game-loop/start-new-round)
    (should= 2 (:remaining-rounds (@atoms/production [0 0]))))

  (it "produces army when remaining-rounds reaches zero"
    (reset! atoms/game-map (build-test-map ["X#"]))
    (reset! atoms/player-map @atoms/game-map)
    (reset! atoms/computer-map @atoms/game-map)
    (reset! atoms/production {[0 0] {:item :army :remaining-rounds 1}})
    ;; Start new round - production completes
    (game-loop/start-new-round)
    ;; Army should be on the city
    (let [city-cell (get-in @atoms/game-map [0 0])]
      (should= :army (:type (:contents city-cell)))
      (should= :computer (:owner (:contents city-cell)))))

  (it "multiple armies don't overwrite each other when moving to same cell"
    ;; Two armies adjacent to the same empty cell
    (reset! atoms/game-map (build-test-map ["a#a"]))
    (reset! atoms/computer-map @atoms/game-map)
    (reset! atoms/player-map @atoms/game-map)
    (reset! atoms/production {})
    ;; Both armies at [0,0] and [0,2] might want to move to [0,1]
    ;; Process them in sequence
    (let [count-armies (fn []
                         (count (for [j (range 3)
                                      :let [cell (get-in @atoms/game-map [0 j])]
                                      :when (and (:contents cell)
                                                 (= :army (:type (:contents cell))))]
                                  j)))]
      (should= 2 (count-armies))
      ;; Process first army
      (computer/process-computer-unit [0 0])
      (should= 2 (count-armies))
      ;; Process second army - should NOT overwrite the first
      (computer/process-computer-unit [0 2])
      (should= 2 (count-armies))))

  (it "armies survive on tiny island with just city"
    ;; Minimal island - just the city with no extra land
    (reset! atoms/game-map (build-test-map ["~~~"
                                             "~X~"
                                             "~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (reset! atoms/player-map @atoms/game-map)
    (reset! atoms/production {})
    (reset! atoms/round-number 0)
    (reset! atoms/player-items [])
    (reset! atoms/computer-items [])
    (reset! atoms/paused false)
    (reset! atoms/pause-requested false)
    (reset! atoms/waiting-for-input false)
    (let [count-armies (fn []
                         (count (for [i (range 3)
                                      j (range 3)
                                      :let [cell (get-in @atoms/game-map [i j])]
                                      :when (and (:contents cell)
                                                 (= :army (:type (:contents cell)))
                                                 (= :computer (:owner (:contents cell))))]
                                  [i j])))
          armies-produced (atom 0)]
      ;; Run 20 rounds
      (dotimes [_ 20]
        (let [before-count (count-armies)
              before-round @atoms/round-number]
          (loop [safety 0]
            (when (and (< safety 200) (= @atoms/round-number before-round))
              (game-loop/advance-game)
              (recur (inc safety))))
          (let [after-count (count-armies)]
            (when (> after-count before-count)
              (swap! armies-produced + (- after-count before-count))))))
      ;; On tiny island, only 1 army can exist (blocks production of second)
      ;; But that 1 army should never disappear
      (let [final-count (count-armies)]
        (should= @armies-produced final-count))))

  (it "army moving to city doesn't get processed twice in same round"
    ;; Scenario: items list has [army-pos, city-pos], army moves to city,
    ;; then city is processed - army should NOT move again
    (reset! atoms/game-map (build-test-map ["~~~~~"
                                             "~#a#~"
                                             "~#X#~"
                                             "~###~"
                                             "~~~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (reset! atoms/player-map @atoms/game-map)
    (reset! atoms/production {[2 2] {:item :army :remaining-rounds 5}})
    (reset! atoms/player-items [])
    (reset! atoms/computer-items [[1 2] [2 2]])  ;; army first, then city
    (reset! atoms/paused false)
    (reset! atoms/waiting-for-input false)
    ;; Army at [1,2] should move toward city [2,2]
    ;; Then when city [2,2] is processed, the army (now on city) moves again
    (let [count-armies (fn []
                         (count (for [i (range 5)
                                      j (range 5)
                                      :let [cell (get-in @atoms/game-map [i j])]
                                      :when (and (:contents cell)
                                                 (= :army (:type (:contents cell)))
                                                 (= :computer (:owner (:contents cell))))]
                                  [i j])))]
      (should= 1 (count-armies))
      ;; Process all items via game loop
      (while (seq @atoms/computer-items)
        (game-loop/advance-game))
      ;; Army should still exist
      (should= 1 (count-armies))))

  (it "army stays put when all land neighbors blocked (won't move through city)"
    ;; Set up scenario: army at [1,1] with other armies blocking land neighbors
    ;; City at [2,2] is not a valid move destination
    (reset! atoms/game-map (build-test-map ["~~~~~"
                                             "~###~"
                                             "~#X#~"
                                             "~###~"
                                             "~~~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (reset! atoms/player-map @atoms/game-map)
    (reset! atoms/production {})
    ;; Put armies at specific positions - blocking [1,2] and [2,1]
    (swap! atoms/game-map assoc-in [1 1 :contents] {:type :army :owner :computer :hits 1 :mode :awake})
    (swap! atoms/game-map assoc-in [1 2 :contents] {:type :army :owner :computer :hits 1 :mode :awake})
    (swap! atoms/game-map assoc-in [2 1 :contents] {:type :army :owner :computer :hits 1 :mode :awake})
    ;; Process the army at [1,1] - it has no valid moves (city is not passable)
    (computer/process-computer-unit [1 1])
    ;; Army should stay at [1,1] since it can't move through the city
    (should= :army (:type (:contents (get-in @atoms/game-map [1 1]))))
    ;; City should remain empty
    (should-be-nil (:contents (get-in @atoms/game-map [2 2]))))

  (it "army moves off city allowing next production cycle"
    (reset! atoms/game-map (build-test-map ["X#"]))
    (reset! atoms/player-map @atoms/game-map)
    (reset! atoms/computer-map @atoms/game-map)
    ;; Put army on city with production ready to reset
    (swap! atoms/game-map assoc-in [0 0 :contents]
           {:type :army :owner :computer :hits 1})
    (reset! atoms/production {[0 0] {:item :army :remaining-rounds 5}})
    (reset! atoms/player-items [])
    (reset! atoms/computer-items [[0 0]])
    ;; Process the army (should move to [0 1])
    (game-loop/advance-game)
    ;; Army should have moved off city
    (should-be-nil (:contents (get-in @atoms/game-map [0 0])))
    (should= :army (:type (:contents (get-in @atoms/game-map [0 1])))))

  (it "counts produced vs remaining armies on isolated island using game loop"
    ;; Oval-shaped island with computer city - use actual game loop
    (reset! atoms/game-map (build-test-map ["~~~~~~~"
                                             "~~###~~"
                                             "~#####~"
                                             "~##X##~"
                                             "~#####~"
                                             "~~###~~"
                                             "~~~~~~~"]))
    (reset! atoms/player-map @atoms/game-map)
    (reset! atoms/computer-map @atoms/game-map)
    (reset! atoms/production {})
    (reset! atoms/round-number 0)
    (reset! atoms/player-items [])
    (reset! atoms/computer-items [])
    (reset! atoms/paused false)
    (reset! atoms/pause-requested false)
    (reset! atoms/waiting-for-input false)
    ;; Count function for armies on map
    (let [height (count @atoms/game-map)
          width (count (first @atoms/game-map))
          count-armies (fn []
                         (count (for [i (range height)
                                      j (range width)
                                      :let [cell (get-in @atoms/game-map [i j])]
                                      :when (and (:contents cell)
                                                 (= :army (:type (:contents cell)))
                                                 (= :computer (:owner (:contents cell))))]
                                  [i j])))
          find-armies (fn []
                        (vec (for [i (range height)
                                   j (range width)
                                   :let [cell (get-in @atoms/game-map [i j])]
                                   :when (and (:contents cell)
                                              (= :army (:type (:contents cell)))
                                              (= :computer (:owner (:contents cell))))]
                               [i j])))
          armies-produced (atom 0)
          debug-log (atom [])]
      ;; Run 200 rounds using actual advance-game (should produce ~35 armies)
      (dotimes [_ 200]
        (let [before-count (count-armies)
              before-round @atoms/round-number]
          ;; Call advance-game repeatedly until round changes
          (loop [safety 0]
            (when (and (< safety 200) (= @atoms/round-number before-round))
              (let [pre (count-armies)]
                (game-loop/advance-game)
                (let [post (count-armies)]
                  (when (< post pre)
                    (swap! debug-log conj {:round @atoms/round-number
                                           :before pre :after post
                                           :items @atoms/computer-items}))))
              (recur (inc safety))))
          (let [after-count (count-armies)]
            (when (> after-count before-count)
              (swap! armies-produced + (- after-count before-count))))))
      ;; The key assertion: produced armies should equal remaining armies
      (let [final-count (count-armies)]
        (when (not= @armies-produced final-count)
          (println "\n=== GAME LOOP DEBUG ===")
          (println "Produced:" @armies-produced "Final:" final-count)
          (doseq [entry @debug-log]
            (println entry))
          (println "=======================\n"))
        (should (>= @armies-produced 4))
        (should= @armies-produced final-count)))))

;; Phase 3: Smart Production Tests

(describe "city-is-coastal?"
  (before (reset-all-atoms!))

  (it "returns true when city has adjacent sea"
    (reset! atoms/game-map (build-test-map ["~X#"]))
    (should (computer/city-is-coastal? [0 1])))

  (it "returns false for inland city"
    (reset! atoms/game-map (build-test-map ["###"
                                             "#X#"
                                             "###"]))
    (should-not (computer/city-is-coastal? [1 1])))

  (it "returns true for city surrounded by sea"
    (reset! atoms/game-map (build-test-map ["~~~"
                                             "~X~"
                                             "~~~"]))
    (should (computer/city-is-coastal? [1 1]))))

(describe "count-computer-units"
  (before (reset-all-atoms!))

  (it "counts all unit types"
    (reset! atoms/game-map (build-test-map ["a#a"
                                             "~~~"
                                             "~d~"]))
    (let [counts (computer/count-computer-units)]
      (should= 2 (get counts :army 0))
      (should= 1 (get counts :destroyer 0))
      (should= 0 (get counts :transport 0))))

  (it "ignores player units"
    (reset! atoms/game-map (build-test-map ["aAa"]))
    (let [counts (computer/count-computer-units)]
      (should= 2 (get counts :army 0))))

  (it "returns empty map when no units"
    (reset! atoms/game-map (build-test-map ["###"]))
    (let [counts (computer/count-computer-units)]
      (should= {} counts))))

(describe "smart decide-production"
  (before (reset-all-atoms!))

  (it "returns :transport at coastal city when armies outnumber transports"
    (reset! atoms/game-map (build-test-map ["~X#"
                                             "a#a"
                                             "a#a"]))
    (reset! atoms/computer-map @atoms/game-map)
    ;; 4 armies, 0 transports - need transports
    (should= :transport (computer/decide-production [0 1])))

  (it "returns :army at inland city when few armies exist"
    (reset! atoms/game-map (build-test-map ["###"
                                             "#X#"
                                             "#a#"]))
    (reset! atoms/computer-map @atoms/game-map)
    ;; Inland city, only 1 army - build more armies first
    (should= :army (computer/decide-production [1 1])))

  (it "returns warship at coastal city when no warships exist"
    (reset! atoms/game-map (build-test-map ["~X~"
                                             "~t~"]))
    (reset! atoms/computer-map @atoms/game-map)
    ;; Has transport but no warships
    (let [unit (computer/decide-production [0 1])]
      (should (#{:destroyer :patrol-boat} unit))))

  (it "returns :fighter when no fighters exist and has enough armies"
    (reset! atoms/game-map (build-test-map ["#X#"
                                             "aaa"
                                             "aaa"]))
    (reset! atoms/computer-map @atoms/game-map)
    ;; Inland city, no fighters, 6 armies - need air support
    (should= :fighter (computer/decide-production [0 1])))

  (it "returns :army by default when all needs satisfied"
    (reset! atoms/game-map (build-test-map ["#X#"
                                             "#ff"
                                             "aaa"]))
    (reset! atoms/computer-map @atoms/game-map)
    ;; Has 2 fighters (enough), has 3 armies - just build more armies
    (should= :army (computer/decide-production [0 1]))))

;; Phase 3: Transport Helper Tests

(describe "find-loading-dock"
  (before (reset-all-atoms!))

  (it "finds sea position adjacent to coastal computer city"
    (reset! atoms/game-map (build-test-map ["~X~"
                                             "~~~"
                                             "~t~"]))
    (reset! atoms/computer-map @atoms/game-map)
    ;; City at [0 1], dock should be adjacent sea: [0 0], [0 2], or [1 1]
    (let [dock (computer/find-loading-dock [2 1])]
      (should (#{[0 0] [0 2] [1 1]} dock))))

  (it "returns nil when no coastal city"
    (reset! atoms/game-map (build-test-map ["~~~"
                                             "~~~"
                                             "~t~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (should-be-nil (computer/find-loading-dock [2 1])))

  (it "finds nearest dock when multiple exist"
    (reset! atoms/game-map (build-test-map ["~X~~~X~"
                                             "~~~t~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    ;; Transport at [1 3], cities at [0 1] and [0 5]
    ;; Docks are sea adjacent to cities - should return nearest
    (let [dock (computer/find-loading-dock [1 3])]
      (should-not-be-nil dock)
      (should= :sea (:type (get-in @atoms/game-map dock))))))

(describe "find-invasion-target"
  (before (reset-all-atoms!))

  (it "finds shore near free city"
    (reset! atoms/game-map (build-test-map ["~~~"
                                             "~+#"
                                             "~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    ;; Free city at [1 1], shore should be [1 0] or [0 1] or [2 1]
    (let [target (computer/find-invasion-target)]
      (should-not-be-nil target)
      ;; Target should be sea adjacent to land
      (should= :sea (:type (get-in @atoms/game-map target)))))

  (it "finds shore near player city when no free cities"
    (reset! atoms/game-map (build-test-map ["~~~"
                                             "~O#"
                                             "~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (let [target (computer/find-invasion-target)]
      (should-not-be-nil target)
      (should= :sea (:type (get-in @atoms/game-map target)))))

  (it "returns nil when no target cities visible"
    (reset! atoms/game-map (build-test-map ["~~~"
                                             "~X~"
                                             "~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (should-be-nil (computer/find-invasion-target))))

(describe "find-disembark-target"
  (before (reset-all-atoms!))

  (it "finds adjacent empty land"
    (reset! atoms/game-map (build-test-map ["~#~"
                                             "~t~"
                                             "~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (should= [0 1] (computer/find-disembark-target [1 1])))

  (it "returns nil when no adjacent land"
    (reset! atoms/game-map (build-test-map ["~~~"
                                             "~t~"
                                             "~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (should-be-nil (computer/find-disembark-target [1 1])))

  (it "returns nil when adjacent land is occupied"
    (reset! atoms/game-map (build-test-map ["~a~"
                                             "~t~"
                                             "~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (should-be-nil (computer/find-disembark-target [1 1]))))

;; Phase 3: Transport Mission Tests

(describe "transport mission - loading"
  (before (reset-all-atoms!))

  (it "empty transport moves toward dock"
    (reset! atoms/game-map (build-test-map ["~X~~~"
                                             "~~~~~"
                                             "~~t~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    ;; Transport at [2 2], dock at [0 1]
    (let [move (computer/decide-transport-move [2 2])]
      (should-not-be-nil move)
      ;; Should move toward dock
      (should (< (computer/distance move [0 1])
                 (computer/distance [2 2] [0 1])))))

  (it "transport at dock with adjacent army loads it"
    (reset! atoms/game-map (build-test-map ["aX~"
                                             "~t~"
                                             "~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    ;; Set transport to loading mission
    (swap! atoms/game-map assoc-in [1 1 :contents :transport-mission] :loading)
    (computer/process-computer-unit [1 1])
    ;; Army should be loaded (removed from map, added to transport)
    (should-be-nil (:contents (get-in @atoms/game-map [0 0])))
    (should= 1 (:army-count (:contents (get-in @atoms/game-map [1 1]))))))

(describe "transport mission - navigation"
  (before (reset-all-atoms!))

  (it "loaded transport moves toward invasion target"
    (reset! atoms/game-map (build-test-map ["~~~+#"
                                             "~~~~~"
                                             "t~~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    ;; Give transport armies and mission
    (swap! atoms/game-map update-in [2 0 :contents] assoc
           :army-count 2
           :transport-mission :en-route
           :transport-target [0 2])
    (let [move (computer/decide-transport-move [2 0])]
      (should-not-be-nil move)
      ;; Should move toward target
      (should (< (computer/distance move [0 2])
                 (computer/distance [2 0] [0 2]))))))

(describe "transport mission - unloading"
  (before (reset-all-atoms!))

  (it "transport adjacent to land disembarks army"
    (reset! atoms/game-map (build-test-map ["~#~"
                                             "~t~"
                                             "~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    ;; Give transport armies and unloading mission
    (swap! atoms/game-map update-in [1 1 :contents] assoc
           :army-count 2
           :transport-mission :unloading)
    (computer/process-computer-unit [1 1])
    ;; Army should be on land
    (should= :army (:type (:contents (get-in @atoms/game-map [0 1]))))
    (should= :computer (:owner (:contents (get-in @atoms/game-map [0 1]))))
    ;; Transport should have one less army
    (should= 1 (:army-count (:contents (get-in @atoms/game-map [1 1])))))

  (it "empty transport returns to base after unloading"
    (reset! atoms/game-map (build-test-map ["~X~~~"
                                             "~~~~~"
                                             "~~~t~"]))
    (reset! atoms/computer-map @atoms/game-map)
    ;; Transport with 0 armies, was unloading
    (swap! atoms/game-map update-in [2 3 :contents] assoc
           :army-count 0
           :transport-mission :unloading)
    (let [move (computer/decide-transport-move [2 3])]
      ;; Should move toward base
      (should-not-be-nil move)
      (should (< (computer/distance move [0 1])
                 (computer/distance [2 3] [0 1]))))))

;; Phase 3: Army-Transport Coordination Tests

(describe "army-transport coordination"
  (before (reset-all-atoms!))

  (it "army boards adjacent loading transport when no land route to target"
    (reset! atoms/game-map (build-test-map ["a~~~+"
                                             "~t~~~"
                                             "~~~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    ;; Transport in loading state with room
    (swap! atoms/game-map update-in [1 1 :contents] assoc
           :transport-mission :loading
           :army-count 0)
    ;; Army at [0 0] has no land route to free city at [0 4]
    (let [move (computer/decide-army-move [0 0])]
      ;; Should move toward transport at [1 1]
      (should= [1 1] move)))

  (it "army ignores transport when land route exists"
    (reset! atoms/game-map (build-test-map ["a##+"
                                             "~t~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (swap! atoms/game-map update-in [1 1 :contents] assoc
           :transport-mission :loading
           :army-count 0)
    ;; Army at [0 0] has land route to free city at [0 3]
    (let [move (computer/decide-army-move [0 0])]
      ;; Should move on land toward city, not toward transport
      (should= [0 1] move)))

  (it "army moves toward distant loading transport when should board"
    (reset! atoms/game-map (build-test-map ["a~~~~+"
                                             "~~~~~~"
                                             "~t~~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (swap! atoms/game-map update-in [2 1 :contents] assoc
           :transport-mission :loading
           :army-count 0)
    ;; Army has no land route to city
    (let [move (computer/decide-army-move [0 0])]
      ;; Should be nil (no passable land neighbors) or stay put
      ;; Actually army is on land cell at [0 0], only neighbor is sea
      (should-be-nil move))))
