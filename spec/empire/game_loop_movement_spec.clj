(ns empire.game-loop-movement-spec
  (:require [empire.test-utils :as test-utils]
            [speclj.core :refer :all]
            [empire.game-loop :as game-loop]
            [empire.config :as config]
            [empire.movement.movement :as movement]
            [empire.test-utils :refer [build-test-map set-test-unit get-test-unit reset-all-atoms! set-test-player-map! set-test-computer-map! make-initial-test-map set-test-world!]]))

(describe "unit movement"
  (before (reset-all-atoms!))

  (context "advance-game with moving unit"
    (it "continues processing when unit moves and has steps remaining"
      ;; Set up a moving unit with multiple steps, target far away
      (set-test-world! (build-test-map ["A###"]))
      (set-test-unit (test-utils/game-map-atom) "A" :mode :moving :target [3 0] :steps-remaining 2)
      (set-test-player-map! (build-test-map ["####"]))
      (test-utils/set-test-state! :production {})
      (test-utils/set-test-state! :player-items [[0 0]])
      (test-utils/set-test-state! :waiting-for-input false)
      (game-loop/advance-game)
      ;; Unit should have moved towards target
      (let [original-has-unit? (some? (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))]
        ;; Either original spot is empty or unit is somewhere else
        (should (or (not original-has-unit?)
                    (empty? (test-utils/read-test-state :player-items))))))

    (it "moves immediately after user gives movement command"
      ;; Bug reproduction: army asks for attention, user gives command,
      ;; army should move in the same processing cycle
      (set-test-world! (build-test-map ["A###"]))
      (set-test-unit (test-utils/game-map-atom) "A" :mode :awake :steps-remaining 1)
      (set-test-player-map! (build-test-map ["####"]))
      (test-utils/set-test-state! :production {})
      (test-utils/set-test-state! :player-items [[0 0]])
      (test-utils/set-test-state! :waiting-for-input false)
      ;; Step 1: advance-game should stop at attention
      (game-loop/advance-game)
      (should (test-utils/read-test-state :waiting-for-input))
      (should= [[0 0]] (test-utils/read-test-state :cells-needing-attention))
      ;; Step 2: simulate user giving movement command
      (movement/set-unit-movement [0 0] [3 0])
      (game-loop/item-processed)
      ;; Verify unit is now in moving mode
      (should= :moving (:mode (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))))
      (should-not (test-utils/read-test-state :waiting-for-input))
      ;; Step 3: advance-game should move the unit
      (game-loop/advance-game)
      ;; Unit should have moved from [0 0]
      (should-not (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))
      (should (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))))

    (it "moves immediately even with multiple units in queue"
      ;; Test with two armies - first one gets orders to distant target, should move before second asks attention
      (set-test-world! (build-test-map ["A#A###"]))
      (set-test-unit (test-utils/game-map-atom) "A1" :mode :awake :steps-remaining 1)
      (set-test-unit (test-utils/game-map-atom) "A2" :mode :awake :steps-remaining 1)
      (set-test-player-map! (build-test-map ["######"]))
      (test-utils/set-test-state! :production {})
      (test-utils/set-test-state! :player-items [[0 0] [2 0]])  ;; Both armies in queue
      (test-utils/set-test-state! :waiting-for-input false)
      ;; Step 1: first army should ask for attention
      (game-loop/advance-game)
      (should (test-utils/read-test-state :waiting-for-input))
      (should= [[0 0]] (test-utils/read-test-state :cells-needing-attention))
      ;; Step 2: give first army movement orders to DISTANT target (so it doesn't arrive in one step)
      (movement/set-unit-movement [0 0] [5 0])
      (game-loop/item-processed)
      (should= :moving (:mode (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))))
      ;; Step 3: advance-game should move first army BEFORE second asks attention
      (game-loop/advance-game)
      ;; First army should have moved from [0 0] to [1 0]
      (should-not (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))
      (should (:contents (get-in (test-utils/read-test-state :game-map) [1 0])))
      ;; First army should still be moving (not at target yet), so second army gets attention next
      (should= :moving (:mode (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))))
      ;; Second army should now be asking for attention
      (should (test-utils/read-test-state :waiting-for-input))
      (should= [[2 0]] (test-utils/read-test-state :cells-needing-attention)))

    (it "wakes up when army tries to move to invalid cell (water)"
      ;; Army told to move toward water - should wake up immediately, NOT move
      (set-test-world! (build-test-map ["A~##"]))
      (set-test-unit (test-utils/game-map-atom) "A" :mode :awake :steps-remaining 1)
      (set-test-player-map! (build-test-map ["####"]))
      (test-utils/set-test-state! :production {})
      (test-utils/set-test-state! :player-items [[0 0]])
      (test-utils/set-test-state! :waiting-for-input false)
      ;; Step 1: army asks for attention
      (game-loop/advance-game)
      (should (test-utils/read-test-state :waiting-for-input))
      ;; Step 2: tell army to move toward water
      (movement/set-unit-movement [0 0] [1 0])
      (game-loop/item-processed)
      (should= :moving (:mode (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))))
      ;; Step 3: advance-game - army should wake up because it can't enter water
      (game-loop/advance-game)
      ;; Army should still be at [0 0] and should be awake again
      (let [unit (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))]
        (should unit)
        (should= :awake (:mode unit))
        (should= :cant-move-into-water (:reason unit)))
      (should (test-utils/read-test-state :waiting-for-input)))

    (it "army with 0 steps-remaining is removed after given orders (expected behavior)"
      ;; When army has 0 steps-remaining (already moved this round), it can't move again
      ;; until next round. This is expected behavior.
      (set-test-world! (build-test-map ["#A#A#"]))
      ;; First army already moved this round (steps-remaining = 0), woke up at target
      (set-test-unit (test-utils/game-map-atom) "A1" :mode :awake :steps-remaining 0)
      ;; Second army hasn't moved yet
      (set-test-unit (test-utils/game-map-atom) "A2" :mode :awake :steps-remaining 1)
      (set-test-player-map! (build-test-map ["#####"]))
      (test-utils/set-test-state! :production {})
      (test-utils/set-test-state! :player-items [[1 0] [3 0]])
      (test-utils/set-test-state! :waiting-for-input false)
      ;; Step 1: first army asks for attention (it has 0 steps but is awake)
      (game-loop/advance-game)
      (should (test-utils/read-test-state :waiting-for-input))
      (should= [[1 0]] (test-utils/read-test-state :cells-needing-attention))
      ;; Step 2: user gives first army new orders to move further
      (movement/set-unit-movement [1 0] [4 0])
      (game-loop/item-processed)
      (should= :moving (:mode (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))))
      ;; Step 3: advance-game - first army can't move (0 steps), removed from queue
      (game-loop/advance-game)
      ;; First army should still be at [1 0] with mode :moving (waiting for next round)
      (let [first-army (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))]
        (should first-army)
        (should= :moving (:mode first-army)))
      ;; The attention should be for the second army
      (should (test-utils/read-test-state :waiting-for-input))
      (should= [[3 0]] (test-utils/read-test-state :cells-needing-attention)))

    (it "fresh army moves immediately when given orders"
      ;; A fresh army (at start of round) has steps-remaining set
      ;; and should move immediately when given orders
      (set-test-world! (build-test-map ["A####"]))
      (set-test-unit (test-utils/game-map-atom) "A" :mode :awake :steps-remaining 1)
      (set-test-player-map! (build-test-map ["#####"]))
      (test-utils/set-test-state! :production {})
      (test-utils/set-test-state! :player-items [[0 0]])
      (test-utils/set-test-state! :waiting-for-input false)
      ;; Step 1: army asks for attention
      (game-loop/advance-game)
      (should (test-utils/read-test-state :waiting-for-input))
      ;; Step 2: user gives orders to distant target
      (movement/set-unit-movement [0 0] [4 0])
      (game-loop/item-processed)
      ;; Step 3: advance-game should move army immediately
      (game-loop/advance-game)
      ;; Army should have moved from [0 0] to [1 0]
      (should-not (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))
      (should (:contents (get-in (test-utils/read-test-state :game-map) [1 0])))))

  (context "army on player city bug"
    (it "army on player city without production moves after given orders"
      ;; BUG: When army is on player city without production, giving the army orders
      ;; causes it to ask for attention again (because the city still needs production)
      ;; instead of moving.
      (set-test-world! (-> (build-test-map ["O###"])
                           (assoc-in [0 0 :contents] {:type :army :owner :player :mode :awake :steps-remaining 1 :hits 1})))
      (set-test-player-map! (build-test-map ["####"]))
      (set-test-computer-map! (build-test-map ["####"]))
      (test-utils/set-test-state! :production {})  ;; No production set for city
      (test-utils/set-test-state! :player-items [[0 0]])
      (test-utils/set-test-state! :waiting-for-input false)
      ;; Step 1: item asks for attention (could be army or city)
      (game-loop/advance-game)
      (should (test-utils/read-test-state :waiting-for-input))
      (should= [[0 0]] (test-utils/read-test-state :cells-needing-attention))
      ;; Step 2: user gives army movement orders
      (movement/set-unit-movement [0 0] [3 0])
      (game-loop/item-processed)
      (should= :moving (:mode (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))))
      ;; Step 3: advance-game should move the army, NOT ask for attention again
      (game-loop/advance-game)
      ;; Army should have moved from [0 0] to [1 0]
      (should-not (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))
      (should (:contents (get-in (test-utils/read-test-state :game-map) [1 0])))))

  (context "unit movement with two units"
    (it "first unit moves before second unit gets attention"
      ;; Two armies in queue. First gets orders, should move BEFORE second gets attention.
      (set-test-world! (build-test-map ["A#A###"]))
      (set-test-unit (test-utils/game-map-atom) "A1" :mode :awake :steps-remaining 1)
      (set-test-unit (test-utils/game-map-atom) "A2" :mode :awake :steps-remaining 1)
      (set-test-player-map! (build-test-map ["######"]))
      (test-utils/set-test-state! :production {})
      (test-utils/set-test-state! :player-items [[0 0] [2 0]])
      (test-utils/set-test-state! :waiting-for-input false)
      ;; Step 1: first army asks for attention
      (game-loop/advance-game)
      (should (test-utils/read-test-state :waiting-for-input))
      (should= [[0 0]] (test-utils/read-test-state :cells-needing-attention))
      ;; Step 2: user gives first army movement orders
      (movement/set-unit-movement [0 0] [5 0])
      (game-loop/item-processed)
      (should= :moving (:mode (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))))
      (should-not (test-utils/read-test-state :waiting-for-input))
      ;; Verify player-items still has first army at front
      (should= [0 0] (first (test-utils/read-test-state :player-items)))
      ;; Step 3: advance-game should move first army BEFORE second asks attention
      (game-loop/advance-game)
      ;; First army should have moved from [0 0] to [1 0]
      (should-not (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))
      (should (:contents (get-in (test-utils/read-test-state :game-map) [1 0])))
      ;; Now second army should be asking for attention
      (should (test-utils/read-test-state :waiting-for-input))
      (should= [[2 0]] (test-utils/read-test-state :cells-needing-attention)))

    (it "unit with 0 steps-remaining cannot move but stays in moving mode"
      ;; When a unit wakes up with 0 steps and user gives orders,
      ;; it can't move but should not immediately ask for attention again
      (set-test-world! (build-test-map ["A#A###"]))
      ;; First army already used its steps this round
      (set-test-unit (test-utils/game-map-atom) "A1" :mode :awake :steps-remaining 0)
      (set-test-unit (test-utils/game-map-atom) "A2" :mode :awake :steps-remaining 1)
      (set-test-player-map! (build-test-map ["######"]))
      (test-utils/set-test-state! :production {})
      (test-utils/set-test-state! :player-items [[0 0] [2 0]])
      (test-utils/set-test-state! :waiting-for-input false)
      ;; Step 1: first army asks for attention
      (game-loop/advance-game)
      (should (test-utils/read-test-state :waiting-for-input))
      (should= [[0 0]] (test-utils/read-test-state :cells-needing-attention))
      ;; Step 2: user gives first army movement orders
      (movement/set-unit-movement [0 0] [5 0])
      (game-loop/item-processed)
      (should= :moving (:mode (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))))
      ;; Step 3: advance-game - first army can't move (0 steps),
      ;; should be removed from queue, second army gets attention
      (game-loop/advance-game)
      ;; First army should still be at [0 0] (can't move)
      (should (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))
      (should= :moving (:mode (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))))
      ;; Second army should now be asking for attention
      (should (test-utils/read-test-state :waiting-for-input))
      (should= [[2 0]] (test-utils/read-test-state :cells-needing-attention)))))
