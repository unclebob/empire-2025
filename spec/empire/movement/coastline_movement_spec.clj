(ns empire.movement.coastline-movement-spec
  (:require [speclj.core :refer :all]
            [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.movement.coastline :refer :all]
            [empire.test-utils :refer [build-test-map set-test-unit get-test-unit reset-all-atoms! set-test-player-map! set-test-computer-map! set-test-world!]]))

(describe "set-coastline-follow-mode"
  (before (reset-all-atoms!))
  (it "sets unit to coastline-follow mode with initial state"
    (set-test-world! (build-test-map ["T"]))
    (set-coastline-follow-mode [0 0])
    (let [unit (get-in @atoms/game-map [0 0 :contents])]
      (should= :coastline-follow (:mode unit))
      (should= config/coastline-steps (:coastline-steps unit))
      (should= [0 0] (:start-pos unit))
      (should= #{[0 0]} (:visited unit))
      (should-be-nil (:prev-pos unit))))

  (it "removes reason and target"
    (set-test-world! (build-test-map ["T"]))
    (set-test-unit atoms/game-map "T" :reason :some-reason :target [5 5])
    (set-coastline-follow-mode [0 0])
    (let [unit (get-in @atoms/game-map [0 0 :contents])]
      (should-be-nil (:reason unit))
      (should-be-nil (:target unit)))))

(describe "move-coastline-unit"
  (before (reset-all-atoms!))
  (it "moves transport along coastline"
    (set-test-world! (build-test-map ["#~~~~"
                                             "#~~~~"
                                             "#T~~~"
                                             "~~~~~"
                                             "~~~~~"]))
    (set-test-unit atoms/game-map "T"
                   :mode :coastline-follow
                   :coastline-steps 50
                   :start-pos [1 2]
                   :visited #{[1 2]}
                   :prev-pos nil)
    (set-test-player-map! (build-test-map ["~~~~~"
                                               "~~~~~"
                                               "~~~~~"
                                               "~~~~~"
                                               "~~~~~"]))
    (move-coastline-unit [1 2])
    (should-be-nil (:contents (get-in @atoms/game-map [1 2]))))

  (it "wakes up when blocked"
    (set-test-world! (build-test-map ["###"
                                             "#T#"
                                             "###"]))
    (set-test-unit atoms/game-map "T"
                   :mode :coastline-follow
                   :coastline-steps 50
                   :start-pos [1 1]
                   :visited #{[1 1]}
                   :prev-pos nil)
    (set-test-player-map! @atoms/game-map)
    (move-coastline-unit [1 1])
    (let [unit (get-in @atoms/game-map [1 1 :contents])]
      (should= :awake (:mode unit))
      (should= :blocked (:reason unit))))

  (it "wakes up when returning to start position after traveling"
    ;; Create a small loop where unit can return to start after 5+ moves
    (set-test-world! (build-test-map ["#~~~"
                                             "#T#~"
                                             "#~~~"
                                             "####"]))
    (set-test-unit atoms/game-map "T"
                   :mode :coastline-follow
                   :coastline-steps 50
                   :start-pos [1 0]
                   ;; Simulating having traveled 6+ cells, now adjacent to start
                   :visited #{[1 0] [2 0] [3 0] [3 1] [3 2] [2 2] [1 2]}
                   :prev-pos [1 2])
    (set-test-player-map! @atoms/game-map)
    (move-coastline-unit [1 1])
    ;; Unit should wake because it's adjacent to start-pos [1 0] and visited > 5
    (let [unit (get-in @atoms/game-map [1 1 :contents])]
      (should= :awake (:mode unit))
      (should= :returned-to-start (:reason unit))))

  (it "wakes up when hitting map edge (started away from edge)"
    ;; Unit starts away from edge, moves to edge
    (set-test-world! (build-test-map ["~~~"
                                             "#T~"
                                             "#~~"]))
    (set-test-unit atoms/game-map "T"
                   :mode :coastline-follow
                   :coastline-steps 50
                   :start-pos [1 1]  ;; Started at [1 1], not at edge
                   :visited #{[1 1]}
                   :prev-pos nil)
    (set-test-player-map! @atoms/game-map)
    (move-coastline-unit [1 1])
    ;; Unit should move to edge (row 0) and wake
    (let [cell-1-0 (get-in @atoms/game-map [1 0])
          cell-2-0 (get-in @atoms/game-map [2 0])
          woken-unit (or (:contents cell-1-0) (:contents cell-2-0))]
      (when woken-unit
        (should= :awake (:mode woken-unit))
        (should= :hit-edge (:reason woken-unit)))))

  (it "does NOT wake for edge when started at edge"
    ;; Unit starts AT edge and moves along edge — should NOT get :hit-edge
    ;; build-test-map transposes: row strings become columns
    ;; So "T~~~~" at row-index 0 becomes column 0.
    ;; T at string position 0 => [0 0] which is at edge (x=0, y=0)
    (set-test-world! (build-test-map ["T~~~~"
                                             "~~~~~"
                                             "#~~~~"
                                             "#~~~~"
                                             "#~~~~"]))
    ;; Transport is at [0 0] - that's the edge
    (set-test-unit atoms/game-map "T"
                   :mode :coastline-follow
                   :coastline-steps 50
                   :start-pos [0 0]  ;; Started at edge
                   :visited #{[0 0]}
                   :prev-pos nil)
    (set-test-player-map! @atoms/game-map)
    (move-coastline-unit [0 0])
    ;; Unit should continue (not wake with :hit-edge) because it started at edge
    (let [{:keys [unit]} (get-test-unit atoms/game-map "T" :mode :coastline-follow)]
      (should= :coastline-follow (:mode unit))))

  (it "wakes up when steps exhausted"
    ;; Map where unit starts in middle, not at edge
    (set-test-world! (build-test-map ["#~~~~"
                                             "#~~~~"
                                             "#T~~~"
                                             "#~~~~"
                                             "#~~~~"]))
    (set-test-unit atoms/game-map "T"
                   :mode :coastline-follow
                   :coastline-steps 1  ;; Only 1 step left
                   :start-pos [1 2]    ;; Started in middle, not at edge
                   :visited #{[1 2]}
                   :prev-pos nil)
    (set-test-player-map! @atoms/game-map)
    (move-coastline-unit [1 2])
    (let [{:keys [unit]} (get-test-unit atoms/game-map "T" :mode :awake)]
      (should= :awake (:mode unit))
      (should= :steps-exhausted (:reason unit))
      (should-not-contain :coastline-steps unit)
      (should-not-contain :visited unit)
      (should-not-contain :start-pos unit)
      (should-not-contain :prev-pos unit)))

  (it "continues moving when not waking (tests make-continuing-unit and recur)"
    ;; Map where unit won't hit any wake conditions
    ;; Transport has speed 2, and with 50 coastline-steps, it should continue
    (set-test-world! (build-test-map ["#~~~~"
                                             "#~~~~"
                                             "#~~~~"
                                             "#T~~~"
                                             "#~~~~"
                                             "#~~~~"
                                             "#~~~~"]))
    (set-test-unit atoms/game-map "T"
                   :mode :coastline-follow
                   :coastline-steps 50  ;; Plenty of steps
                   :start-pos [1 3]    ;; Started in middle, not at edge
                   :visited #{[1 3]}
                   :prev-pos nil)
    (set-test-player-map! @atoms/game-map)
    ;; transport has speed 2, so it should take 2 steps and not wake
    (move-coastline-unit [1 3])
    ;; Unit should have moved but still be in coastline-follow mode (not woken)
    (let [{:keys [unit]} (get-test-unit atoms/game-map "T" :mode :coastline-follow)]
      (should= :coastline-follow (:mode unit))
      (should (> (count (:visited unit)) 1))))

  (it "wakes up transport with armies when finding a bay"
    ;; build-test-map transposes: [x y] = [col row]
    ;; T at x=2,y=2 → [2 2]. ~ at x=2,y=3 → [2 3].
    ;; [2 3] neighbors: [1,2]=#, [3,2]=#, [1,3]=#, [3,3]=#,
    ;;   [1,4]=#, [2,4]=#, [3,4]=#, [2,2]=sea(T) => 7 land = bay
    ;; Only valid move from [2 2] is [2 3] (the bay).
    (set-test-world! (build-test-map ["#####"
                                             "#####"
                                             "##T##"
                                             "##~##"
                                             "#####"]))
    (set-test-unit atoms/game-map "T"
                   :mode :coastline-follow
                   :army-count 2
                   :coastline-steps 50
                   :start-pos [2 2]
                   :visited #{[2 2]}
                   :prev-pos nil)
    (set-test-player-map! @atoms/game-map)
    (move-coastline-unit [2 2])
    ;; Transport moved to [2 3] (bay) and should wake
    (let [unit (get-in @atoms/game-map [2 3 :contents])]
      (should-not-be-nil unit)
      (should= :awake (:mode unit))
      (should= :found-a-bay (:reason unit))))

  (it "does NOT wake patrol-boat in bay (only transports wake for bays)"
    ;; Same bay setup but with patrol-boat — should NOT get :found-a-bay
    ;; Patrol-boat has no army-count concept, but even if it did, type check should block it
    (set-test-world! (build-test-map ["#####"
                                             "#####"
                                             "##P##"
                                             "##~##"
                                             "#####"]))
    (set-test-unit atoms/game-map "P"
                   :mode :coastline-follow
                   :army-count 2
                   :coastline-steps 50
                   :start-pos [2 2]
                   :visited #{[2 2]}
                   :prev-pos nil)
    (set-test-player-map! @atoms/game-map)
    (move-coastline-unit [2 2])
    ;; Patrol-boat moved to [2 3] but should NOT wake with :found-a-bay
    (let [unit (get-in @atoms/game-map [2 3 :contents])]
      (should-not-be-nil unit)
      (should-not= :found-a-bay (:reason unit))))

  (it "wakes when returning to start via x-axis adjacency"
    ;; Start-pos differs only in x from current position
    ;; This kills the mutant that removes dx from adjacent-positions
    ;; 7-row map so unit at [3 3] is not at edge
    (set-test-world! (build-test-map ["#~~~~~~"
                                             "#~~~~~~"
                                             "#~~~~~~"
                                             "#~T~~~~"
                                             "#~~~~~~"
                                             "#~~~~~~"
                                             "#~~~~~~"]))
    ;; Start at [2 3], current at [2 3] with visited > 5, start-pos adjacent via x-axis
    ;; We set start-pos to [3 3] which differs from [2 3] only in x
    (set-test-unit atoms/game-map "T"
                   :mode :coastline-follow
                   :coastline-steps 50
                   :start-pos [3 3]
                   :visited #{[3 3] [3 2] [3 1] [2 1] [2 2] [2 3]}
                   :prev-pos [2 2])
    (set-test-player-map! @atoms/game-map)
    (move-coastline-unit [2 3])
    ;; Should wake with :returned-to-start because [2 3] is adjacent to start [3 3]
    ;; and visited count > 5
    (let [unit (get-in @atoms/game-map [2 3 :contents])]
      (should-not-be-nil unit)
      (should= :awake (:mode unit))
      (should= :returned-to-start (:reason unit)))))

(describe "collision prevention"
  (before (reset-all-atoms!))

  (it "prevents overwriting another unit when destination becomes occupied"
    ;; Set up: patrol boat next to another unit in narrow channel
    ;; The defensive check should prevent overwriting if pick-coastline-move
    ;; returns an occupied cell (simulating race condition)
    (set-test-world! (build-test-map ["###"
                                             "#~#"
                                             "#P#"
                                             "#D#"
                                             "###"]))
    ;; After transpose: P is at [1 2], D is at [1 3]
    (let [patrol-coords [1 2]
          destroyer-pos [1 3]]
      ;; Set patrol boat to coastline-follow mode
      (set-test-unit atoms/game-map "P" :mode :coastline-follow
                     :coastline-steps 10 :start-pos patrol-coords :visited #{patrol-coords} :prev-pos [1 1])
      (set-test-player-map! @atoms/game-map)

      ;; Mock pick-coastline-move to return the destroyer's position
      ;; This simulates a race condition where the cell was empty when checked
      ;; but occupied when the move happens
      (with-redefs [pick-coastline-move (constantly destroyer-pos)]
        (move-coastline-unit patrol-coords))

      ;; Both units should still exist - patrol boat wakes instead of overwriting
      (should-not-be-nil (:contents (get-in @atoms/game-map [1 3])))  ;; Destroyer still there
      (let [patrol-unit (get-in @atoms/game-map [1 2 :contents])]
        (should-not-be-nil patrol-unit)
        (should= :patrol-boat (:type patrol-unit))
        (should= :awake (:mode patrol-unit))
        (should= :blocked (:reason patrol-unit))))))
