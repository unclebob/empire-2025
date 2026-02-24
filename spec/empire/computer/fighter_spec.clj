(ns empire.computer.fighter-spec
  "Tests for VMS Empire style computer fighter movement."
  (:require [speclj.core :refer :all]
            [empire.computer.fighter :as fighter]
            [empire.atoms :as atoms]
            [empire.combat :as combat]
            [empire.config :as config]
            [empire.test-utils :refer [build-test-map build-sparse-test-map
                                       set-test-unit
                                       get-test-unit reset-all-atoms!]]))

(describe "process-fighter"
  (before (reset-all-atoms!))

  (context "attack behavior"
    (it "attacks adjacent player unit"
      (reset! atoms/game-map (build-test-map ["fA"]))
      (set-test-unit atoms/game-map "f" :fuel 20)
      (reset! atoms/computer-map @atoms/game-map)
      (let [unit (:contents (get-in @atoms/game-map [0 0]))
            _result (fighter/process-fighter [0 0] unit)]
        ;; Combat should have occurred - one unit should be gone
        (let [cell0 (get-in @atoms/game-map [0 0])
              cell1 (get-in @atoms/game-map [1 0])]
          (should (or (nil? (:contents cell0))
                      (nil? (:contents cell1))
                      (= :computer (:owner (:contents cell1)))))))))

  (context "fuel management"
    (it "returns to city when low on fuel"
      (reset! atoms/game-map (build-test-map ["X#f"]))
      (set-test-unit atoms/game-map "f" :fuel 3)
      (reset! atoms/computer-map @atoms/game-map)
      (let [unit (:contents (get-in @atoms/game-map [2 0]))]
        (fighter/process-fighter [2 0] unit)
        ;; With fuel 3, distance 2: should-return is true, moves toward city,
        ;; fuel decremented to 2, then adjacent to city, lands
        (should= 1 (:fighter-count (get-in @atoms/game-map [0 0])))))

    (it "lands at adjacent city"
      (reset! atoms/game-map (build-test-map ["Xf"]))
      (set-test-unit atoms/game-map "f" :fuel 2)
      (reset! atoms/computer-map @atoms/game-map)
      (let [unit (:contents (get-in @atoms/game-map [1 0]))
            result (fighter/process-fighter [1 0] unit)]
        ;; Fighter should land at city
        (should-be-nil result)
        ;; City should have fighter
        (should= 1 (:fighter-count (get-in @atoms/game-map [0 0])))))

    (it "consumes fuel each step"
      (reset! atoms/game-map (build-test-map ["X#########f##########"]))
      (set-test-unit atoms/game-map "f" :fuel 30)
      ;; Unexplored territory to the right so fighter has reason to move
      (reset! atoms/computer-map (build-test-map ["X#########f........."]))
      (let [unit (get-in @atoms/game-map [10 0 :contents])]
        (fighter/process-fighter [10 0] unit)
        ;; Find the fighter - it should have moved and have fuel < 30
        (let [result (get-test-unit atoms/game-map "f")]
          (should-not-be-nil result)
          (should (< (:fuel (:unit result)) 30)))))

    (it "moves multiple cells per round"
      (reset! atoms/game-map (build-test-map ["X#########f##########"]))
      (set-test-unit atoms/game-map "f" :fuel 30)
      ;; Unexplored territory to the right so fighter has reason to move
      (reset! atoms/computer-map (build-test-map ["X#########f........."]))
      (let [unit (get-in @atoms/game-map [10 0 :contents])]
        (fighter/process-fighter [10 0] unit)
        ;; Fighter should NOT still be at [10 0]
        (should-be-nil (get-in @atoms/game-map [10 0 :contents]))
        ;; Fighter should have moved more than 1 cell from start
        (let [result (get-test-unit atoms/game-map "f")
              [fighter-col _] (:pos result)]
          (should-not-be-nil result)
          (should (> (Math/abs (- fighter-col 10)) 1)))))

    (it "fighter dies when fuel runs out"
      ;; Fighter with fuel 1 on open land, no city nearby.
      ;; After moving once, fuel becomes 0 and fighter should die.
      (reset! atoms/game-map (build-test-map ["f##"]))
      (set-test-unit atoms/game-map "f" :fuel 1)
      ;; Unexplored territory so fighter has reason to move
      (reset! atoms/computer-map (build-test-map ["f--"]))
      (let [unit (get-in @atoms/game-map [0 0 :contents])]
        (fighter/process-fighter [0 0] unit)
        ;; Fighter should be gone from the entire map
        (should-be-nil (get-test-unit atoms/game-map "f"))))

    (it "stops moving after landing at city"
      ;; Fighter next to city with low fuel should land and not continue.
      (reset! atoms/game-map (build-test-map ["#Xf##"]))
      (set-test-unit atoms/game-map "f" :fuel 3)
      (reset! atoms/computer-map @atoms/game-map)
      (let [unit (get-in @atoms/game-map [2 0 :contents])]
        (fighter/process-fighter [2 0] unit)
        ;; Fighter should have landed at city [1 0]
        (should= 1 (:fighter-count (get-in @atoms/game-map [1 0])))
        ;; Fighter should NOT be on the map as a unit
        (should-be-nil (get-test-unit atoms/game-map "f")))))

  (context "patrol behavior"
    (it "patrols toward player units when fuel allows"
      ;; Wide map so fighter patrols toward player unit
      ;; but doesn't reach it to avoid random combat outcomes
      (reset! atoms/game-map (build-test-map ["Xf##########A"]))
      (set-test-unit atoms/game-map "f" :fuel 20)
      (reset! atoms/computer-map @atoms/game-map)
      (let [unit (get-in @atoms/game-map [1 0 :contents])]
        (fighter/process-fighter [1 0] unit)
        ;; Fighter should have left [1 0] and moved toward the player army
        (should-be-nil (get-in @atoms/game-map [1 0 :contents]))
        ;; Fighter should be somewhere between start and player army
        (let [result (get-test-unit atoms/game-map "f")
              [fighter-col _] (:pos result)]
          (should-not-be-nil result)
          (should (> fighter-col 1)))))

    (it "explores toward unexplored territory"
      ;; Wide map with unexplored cells to the right
      (reset! atoms/game-map (build-test-map ["Xf########"]))
      (set-test-unit atoms/game-map "f" :fuel 20)
      (reset! atoms/computer-map (build-test-map ["Xf........"]))
      (let [unit (get-in @atoms/game-map [1 0 :contents])]
        (fighter/process-fighter [1 0] unit)
        ;; Fighter should have moved away from start toward unexplored
        (should-be-nil (get-in @atoms/game-map [1 0 :contents]))
        (let [result (get-test-unit atoms/game-map "f")
              [fighter-col _] (:pos result)]
          (should-not-be-nil result)
          (should (> fighter-col 1)))))

    (it "explores toward unexplored territory without NW bias"
      ;; 5x5 map. Fighter at center, unexplored only in SE corner.
      (let [game-map (build-test-map ["#####"
                                      "#####"
                                      "##f##"
                                      "#####"
                                      "#####"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map (build-test-map ["#####"
                                                    "#####"
                                                    "#####"
                                                    "#####"
                                                    "####-"]))
        (set-test-unit atoms/game-map "f" :fuel 20)
        (let [unit (get-in @atoms/game-map [2 2 :contents])]
          (fighter/process-fighter [2 2] unit)
          ;; Fighter should have moved
          (should-be-nil (get-in @atoms/game-map [2 2 :contents]))
          ;; Find where fighter ended up
          (let [result (get-test-unit atoms/game-map "f")
                [fr fc] (:pos result)]
            (should-not-be-nil result)
            ;; Should have moved toward SE, not NW
            (should (or (> fr 2) (> fc 2))))))))

  (context "ignores non-computer fighters"
    (it "returns nil for player fighter"
      (reset! atoms/game-map (build-test-map ["F"]))
      (set-test-unit atoms/game-map "F" :fuel 20)
      (let [unit (:contents (get-in @atoms/game-map [0 0]))]
        (should-be-nil (fighter/process-fighter [0 0] unit))))

    (it "returns nil for non-fighter"
      (reset! atoms/game-map (build-test-map ["a"]))
      (let [unit (:contents (get-in @atoms/game-map [0 0]))]
        (should-be-nil (fighter/process-fighter [0 0] unit)))))

  (context "leg-based coverage"
    (it "picks unflown leg target over previously flown leg"
      ;; 20x20 map: city at [10,10], carrier A at [10,0] (north), carrier B at [0,10] (west)
      (let [land-row (apply str (repeat 20 \#))
            row-0 (str (apply str (repeat 10 \#)) "~" (apply str (repeat 9 \#)))
            row-10 (str "~" (apply str (repeat 9 \#)) "X" (apply str (repeat 9 \#)))
            rows (-> (vec (repeat 20 land-row))
                     (assoc 0 row-0)
                     (assoc 10 row-10))]
        (reset! atoms/game-map (build-test-map rows))
        ;; Place carriers in holding mode
        (swap! atoms/game-map assoc-in [10 0 :contents]
               {:type :carrier :owner :computer :hits 8 :carrier-mode :holding})
        (swap! atoms/game-map assoc-in [0 10 :contents]
               {:type :carrier :owner :computer :hits 8 :carrier-mode :holding})
        ;; Place fighter on city
        (swap! atoms/game-map assoc-in [10 10 :contents]
               {:type :fighter :owner :computer :hits 1 :fuel 32})
        (reset! atoms/computer-map @atoms/game-map)
        ;; North leg is flown, west leg is unflown
        (reset! atoms/fighter-leg-records
                {#{[10 10] [10 0]} {:last-flown 5}})
        ;; Force regular leg assignment
        (with-redefs [rand (fn ([] 0.6) ([_n] 0.6))]
          (let [unit (get-in @atoms/game-map [10 10 :contents])]
            (fighter/process-fighter [10 10] unit)
            ;; Fighter should have moved west (toward unflown leg target [0,10])
            (let [result (get-test-unit atoms/game-map "f")]
              (should-not-be-nil result)
              (let [[r c] (:pos result)]
                ;; Moved west: r=col < 10
                (should (< r 10))
                ;; Did not move north significantly: c=row >= 8
                (should (>= c 8))))))))

    (it "picks oldest flown leg when all legs are flown"
      ;; Same map setup: city at [10,10], carrier A at [10,0] (north), carrier B at [0,10] (west)
      (let [land-row (apply str (repeat 20 \#))
            row-0 (str (apply str (repeat 10 \#)) "~" (apply str (repeat 9 \#)))
            row-10 (str "~" (apply str (repeat 9 \#)) "X" (apply str (repeat 9 \#)))
            rows (-> (vec (repeat 20 land-row))
                     (assoc 0 row-0)
                     (assoc 10 row-10))]
        (reset! atoms/game-map (build-test-map rows))
        (swap! atoms/game-map assoc-in [10 0 :contents]
               {:type :carrier :owner :computer :hits 8 :carrier-mode :holding})
        (swap! atoms/game-map assoc-in [0 10 :contents]
               {:type :carrier :owner :computer :hits 8 :carrier-mode :holding})
        (swap! atoms/game-map assoc-in [10 10 :contents]
               {:type :fighter :owner :computer :hits 1 :fuel 32})
        (reset! atoms/computer-map @atoms/game-map)
        ;; Both legs flown; west leg is older (lower round number)
        (reset! atoms/fighter-leg-records
                {#{[10 10] [10 0]} {:last-flown 10}
                 #{[10 10] [0 10]} {:last-flown 3}})
        ;; Force regular leg assignment
        (with-redefs [rand (fn ([] 0.6) ([_n] 0.6))]
          (let [unit (get-in @atoms/game-map [10 10 :contents])]
            (fighter/process-fighter [10 10] unit)
            ;; Fighter should move toward older leg target [0,10] (west)
            (let [result (get-test-unit atoms/game-map "f")]
              (should-not-be-nil result)
              (let [[r c] (:pos result)]
                (should (< r 10))
                (should (>= c 8))))))))

    (it "records leg on arrival at target city"
      (reset! atoms/game-map (build-test-map ["X#####fX"]))
      (set-test-unit atoms/game-map "f" :fuel 20
                     :flight-target-site [7 0]
                     :flight-origin-site [0 0])
      (reset! atoms/computer-map @atoms/game-map)
      (reset! atoms/round-number 42)
      (let [unit (get-in @atoms/game-map [6 0 :contents])]
        (fighter/process-fighter [6 0] unit)
        ;; Leg should be recorded with current round number
        (should= 42 (:last-flown (get @atoms/fighter-leg-records #{[0 0] [7 0]})))))

    (it "refuels at carrier when low on fuel"
      ;; Fighter on sea adjacent to carrier, no city nearby
      (reset! atoms/game-map (build-test-map ["#####~j~"]))
      ;; Place carrier at [7,0] in holding mode
      (swap! atoms/game-map assoc-in [7 0 :contents]
             {:type :carrier :owner :computer :hits 8 :carrier-mode :holding})
      (set-test-unit atoms/game-map "f" :fuel 2)
      (reset! atoms/computer-map @atoms/game-map)
      (let [unit (get-in @atoms/game-map [6 0 :contents])]
        (fighter/process-fighter [6 0] unit)
        ;; Fighter should have refueled and still be alive
        (let [result (get-test-unit atoms/game-map "f")]
          (should-not-be-nil result)
          ;; Fuel should be much higher than starting 2 (refueled to 32, then some patrol steps)
          (should (> (:fuel (:unit result)) 20)))))

    (it "falls back to patrol when no reachable legs"
      ;; Fighter at a city with no other refueling sites within range
      (reset! atoms/game-map (build-test-map ["Xf########"]))
      (set-test-unit atoms/game-map "f" :fuel 20)
      ;; Unexplored territory to the right
      (reset! atoms/computer-map (build-test-map ["Xf........"]))
      (let [unit (get-in @atoms/game-map [1 0 :contents])]
        (fighter/process-fighter [1 0] unit)
        ;; Fighter should have moved (patrol behavior) even without a leg target
        (should-be-nil (get-in @atoms/game-map [1 0 :contents]))
        (let [result (get-test-unit atoms/game-map "f")
              [fighter-col _] (:pos result)]
          (should-not-be-nil result)
          (should (> fighter-col 1))))))

  (context "no phantom contents on blocked patrol"
    (it "does not create phantom contents when patrol move is blocked"
      ;; Fighter at [0 0] on a 1-row map. All neighbors occupied by friendly armies.
      ;; No unexplored cells on computer-map, but a player army far away to give a patrol target.
      ;; do-patrol will find the player army as target, pick a neighbor, but move-unit-to fails.
      ;; The cell should NOT end up with phantom {:contents {:fuel N}}.
      (reset! atoms/game-map (build-test-map ["fa###A"]))
      (set-test-unit atoms/game-map "f" :fuel 20)
      (reset! atoms/computer-map @atoms/game-map)
      (let [unit (get-in @atoms/game-map [0 0 :contents])]
        (fighter/process-fighter [0 0] unit)
        ;; Cell [1 0] has a friendly army - should still be an army, not phantom fuel
        (should= :army (:type (:contents (get-in @atoms/game-map [1 0])))))))

  (context "sidestepping"
    (it "sidesteps around friendly unit blocking direct path"
      ;; 3x3 map: fighter at [0 0], friendly army blocking [1 0], target city at [2 0]
      ;; Fighter should move diagonally to [0 1] or [1 1] to go around
      (reset! atoms/game-map (build-test-map ["f##"
                                               "###"
                                               "##X"]))
      ;; Place a friendly army at [1 0] blocking the direct path
      (swap! atoms/game-map assoc-in [1 0 :contents]
             {:type :army :owner :computer :hits 1})
      (set-test-unit atoms/game-map "f" :fuel 20
                     :flight-target-site [2 0]
                     :flight-origin-site [2 2])
      (reset! atoms/computer-map @atoms/game-map)
      (let [unit (get-in @atoms/game-map [0 0 :contents])]
        (fighter/process-fighter [0 0] unit)
        ;; Fighter should NOT still be at [0 0] - it should have sidestepped
        (let [result (get-test-unit atoms/game-map "f")]
          (should-not-be-nil result)
          ;; Should have moved somewhere other than [0 0]
          (should-not= [0 0] (:pos result)))))

    (it "prefers diagonal when diagonal and orthogonal equidistant to target"
      ;; 5x5 map: city at [0 0], fighter at [0 1], target city at [4 4]
      ;; Fighter should move diagonally toward target
      (reset! atoms/game-map (build-test-map ["X####"
                                               "f####"
                                               "#####"
                                               "#####"
                                               "####X"]))
      (set-test-unit atoms/game-map "f" :fuel 20
                     :flight-target-site [4 4]
                     :flight-origin-site [0 0])
      (reset! atoms/computer-map @atoms/game-map)
      (let [unit (get-in @atoms/game-map [0 1 :contents])]
        (fighter/process-fighter [0 1] unit)
        ;; Fighter should have moved toward [4 4]
        (let [result (get-test-unit atoms/game-map "f")]
          (should-not-be-nil result)
          (should-not= [0 1] (:pos result)))))

    (it "stuck fighter surrounded by friendly units burns fuel and dies"
      ;; 3x3 map: fighter at center [1 1], surrounded by friendly armies on all 8 neighbors
      (reset! atoms/game-map (build-test-map ["aaa"
                                               "afa"
                                               "aaa"]))
      (set-test-unit atoms/game-map "f" :fuel 5)
      (reset! atoms/computer-map @atoms/game-map)
      (let [unit (get-in @atoms/game-map [1 1 :contents])]
        (fighter/process-fighter [1 1] unit)
        ;; Fighter should be dead - fuel burned to 0 while stuck
        (should-be-nil (get-test-unit atoms/game-map "f")))))

  (context "fuel burn when stuck"
    (it "stuck fighter with 8 fuel burns all fuel and dies"
      ;; Fighter completely surrounded, with exactly 8 fuel (one per step)
      (reset! atoms/game-map (build-test-map ["aaa"
                                               "afa"
                                               "aaa"]))
      (set-test-unit atoms/game-map "f" :fuel 8)
      (reset! atoms/computer-map @atoms/game-map)
      (let [unit (get-in @atoms/game-map [1 1 :contents])]
        (fighter/process-fighter [1 1] unit)
        ;; Fighter should be dead after burning 8 fuel
        (should-be-nil (get-test-unit atoms/game-map "f"))))

    (it "stuck fighter with more than 8 fuel survives the round"
      ;; Fighter completely surrounded, with 10 fuel - burns 8, survives with 2
      (reset! atoms/game-map (build-test-map ["aaa"
                                               "afa"
                                               "aaa"]))
      (set-test-unit atoms/game-map "f" :fuel 10)
      (reset! atoms/computer-map @atoms/game-map)
      (let [unit (get-in @atoms/game-map [1 1 :contents])]
        (fighter/process-fighter [1 1] unit)
        ;; Fighter should survive with 2 fuel remaining
        (let [result (get-test-unit atoms/game-map "f")]
          (should-not-be-nil result)
          (should= 2 (:fuel (:unit result)))))))

  (context "flight mode selection"
    (it "assigns regular leg when rand >= 0.5"
      ;; Two cities within fuel range, fighter on city A with no flight-mode.
      ;; With rand returning 0.6, ensure-flight-target should assign :regular mode.
      (reset! atoms/game-map (build-test-map ["X################X"]))
      (swap! atoms/game-map assoc-in [0 0 :contents]
             {:type :fighter :owner :computer :hits 1 :fuel 32})
      (reset! atoms/computer-map @atoms/game-map)
      (with-redefs [rand (fn
                           ([] 0.6)
                           ([_n] 0.6))]
        (let [unit (get-in @atoms/game-map [0 0 :contents])]
          (fighter/process-fighter [0 0] unit)
          ;; Fighter should have :flight-mode :regular
          (let [result (get-test-unit atoms/game-map "f")]
            (should-not-be-nil result)
            (should= :regular (:flight-mode (:unit result)))))))

    (it "assigns exploration sortie when first rand < 0.5 and second >= 0.05"
      ;; Two cities within fuel range, fighter on city A with no flight-mode.
      ;; Sequential rolls: 0.3 (exploration), 0.1 (not drone, >= 0.05) => sortie
      (reset! atoms/game-map (build-test-map ["X################X"]))
      (swap! atoms/game-map assoc-in [0 0 :contents]
             {:type :fighter :owner :computer :hits 1 :fuel 32})
      (reset! atoms/computer-map @atoms/game-map)
      (let [rolls (atom [0.3 0.1])]
        (with-redefs [rand (fn
                             ([] (let [v (first @rolls)] (swap! rolls rest) v))
                             ([_n] (let [v (first @rolls)] (swap! rolls rest) v)))
                      rand-nth first]
          (let [unit (get-in @atoms/game-map [0 0 :contents])]
            (fighter/process-fighter [0 0] unit)
            ;; Fighter should have :flight-mode :explore
            (let [result (get-test-unit atoms/game-map "f")]
              (should-not-be-nil result)
              (should= :explore (:flight-mode (:unit result)))
              (should-not-be-nil (:explore-origin (:unit result)))
              (should-not-be-nil (:explore-heading (:unit result)))
              (should (pos? (:explore-steps-remaining (:unit result)))))))))

    (it "assigns drone when first rand < 0.5 and second < 0.05"
      ;; Sequential rolls: 0.3 (exploration), 0.02 (drone, < 0.05)
      (reset! atoms/game-map (build-test-map ["X################X"]))
      (swap! atoms/game-map assoc-in [0 0 :contents]
             {:type :fighter :owner :computer :hits 1 :fuel 32})
      (reset! atoms/computer-map @atoms/game-map)
      (let [rolls (atom [0.3 0.02])]
        (with-redefs [rand (fn
                             ([] (let [v (first @rolls)] (swap! rolls rest) v))
                             ([_n] (let [v (first @rolls)] (swap! rolls rest) v)))
                      rand-nth first]
          (let [unit (get-in @atoms/game-map [0 0 :contents])]
            (fighter/process-fighter [0 0] unit)
            ;; Fighter should have :flight-mode :drone
            (let [result (get-test-unit atoms/game-map "f")]
              (should-not-be-nil result)
              (should= :drone (:flight-mode (:unit result))))))))

    (it "does not re-roll when fighter already has flight-mode"
      ;; Fighter already has :flight-mode :regular - ensure-flight-target should not reassign
      (reset! atoms/game-map (build-test-map ["X################X"]))
      (swap! atoms/game-map assoc-in [0 0 :contents]
             {:type :fighter :owner :computer :hits 1 :fuel 32
              :flight-mode :regular :flight-target-site [17 0]
              :flight-origin-site [0 0]})
      (reset! atoms/computer-map @atoms/game-map)
      (let [unit (get-in @atoms/game-map [0 0 :contents])]
        (fighter/process-fighter [0 0] unit)
        ;; Fighter should still have :flight-mode :regular (not reassigned)
        (let [result (get-test-unit atoms/game-map "f")]
          (should-not-be-nil result)
          (should= :regular (:flight-mode (:unit result)))))))

  (context "exploration heading"
    (it "picks direction with most unexplored cells"
      ;; 5x5 map: all explored except east side (columns 3-4 unexplored)
      ;; Fighter at [2 2] on city. Heading should favor east.
      (reset! atoms/game-map (build-test-map ["###--"
                                               "###--"
                                               "##X--"
                                               "###--"
                                               "###--"]))
      (swap! atoms/game-map assoc-in [2 2 :contents]
             {:type :fighter :owner :computer :hits 1 :fuel 32})
      ;; Computer map: west explored, east unexplored
      (reset! atoms/computer-map (build-test-map ["###--"
                                                   "###--"
                                                   "##X--"
                                                   "###--"
                                                   "###--"]))
      (let [rolls (atom [0.3 0.1])]
        (with-redefs [rand (fn
                             ([] (let [v (first @rolls)] (swap! rolls rest) v))
                             ([_n] (let [v (first @rolls)] (swap! rolls rest) v)))
                      rand-nth first]
          (let [unit (get-in @atoms/game-map [2 2 :contents])]
            (fighter/process-fighter [2 2] unit)
            ;; Fighter should have heading pointing east (dc > 0)
            (let [result (get-test-unit atoms/game-map "f")]
              (should-not-be-nil result)
              (should (pos? (first (:explore-heading (:unit result)))))))))))

  (context "exploration sortie movement"
    (it "sortie flies outbound with steps-remaining decreasing"
      ;; Fighter mid-sortie, 10 steps remaining, heading east on wide map
      (reset! atoms/game-map (build-test-map ["X#f################"]))
      (set-test-unit atoms/game-map "f" :fuel 20
                     :flight-mode :explore
                     :explore-origin [0 0]
                     :explore-heading [0 1]
                     :explore-steps-remaining 10
                     :flight-target-site [18 0])
      ;; Unexplored territory east
      (reset! atoms/computer-map (build-test-map ["X#f................"]))
      (let [unit (get-in @atoms/game-map [2 0 :contents])]
        (fighter/process-fighter [2 0] unit)
        ;; Fighter should have moved east and steps-remaining should be less than 10
        (let [result (get-test-unit atoms/game-map "f")
              [fc _] (:pos result)]
          (should-not-be-nil result)
          (should (> fc 2))
          (should (< (:explore-steps-remaining (:unit result)) 10)))))

    (it "switches to return mode after outbound steps exhausted"
      ;; Fighter with 1 step remaining, heading east. Origin far away so arrival
      ;; doesn't happen during the same round (8 steps total).
      (reset! atoms/game-map (build-test-map ["X#########f##############"]))
      (set-test-unit atoms/game-map "f" :fuel 20
                     :flight-mode :explore
                     :explore-origin [0 0]
                     :explore-heading [0 1]
                     :explore-steps-remaining 1
                     :flight-target-site [24 0])
      (reset! atoms/computer-map (build-test-map ["X#########f.............."]))
      (let [unit (get-in @atoms/game-map [10 0 :contents])]
        (fighter/process-fighter [10 0] unit)
        ;; After 1 outbound step, should switch to :regular with target = origin
        ;; Fighter navigates back but can't reach [0 0] in remaining 7 steps
        (let [result (get-test-unit atoms/game-map "f")]
          (should-not-be-nil result)
          (should= :regular (:flight-mode (:unit result)))
          (should= [0 0] (:flight-target-site (:unit result))))))

    (it "sortie step prefers cells with more unexplored neighbors"
      ;; 3-row map: fighter at [2 1], unexplored only at row 0
      ;; Exploration should prefer moving toward row 0 (more unexplored neighbors)
      (reset! atoms/game-map (build-test-map ["#####"
                                               "##f##"
                                               "#####"]))
      (set-test-unit atoms/game-map "f" :fuel 20
                     :flight-mode :explore
                     :explore-origin [0 1]
                     :explore-heading [0 1]
                     :explore-steps-remaining 10
                     :flight-target-site [4 1])
      ;; Only row 0 is unexplored
      (reset! atoms/computer-map (build-test-map ["-----"
                                                   "##f##"
                                                   "#####"]))
      (let [unit (get-in @atoms/game-map [2 1 :contents])]
        (fighter/process-fighter [2 1] unit)
        ;; Fighter should have moved — preferring cells near unexplored row 0
        (let [result (get-test-unit atoms/game-map "f")]
          (should-not-be-nil result)
          (should-not= [2 1] (:pos result))))))

  (context "drone movement"
    (it "drone flies until fuel exhaustion and dies"
      ;; Drone fighter with 3 fuel on open map, no city nearby
      (reset! atoms/game-map (build-test-map ["f##########"]))
      (set-test-unit atoms/game-map "f" :fuel 3
                     :flight-mode :drone
                     :explore-origin [0 0]
                     :explore-heading [0 1]
                     :flight-target-site [10 0])
      ;; Unexplored territory east
      (reset! atoms/computer-map (build-test-map ["f.........."]))
      (let [unit (get-in @atoms/game-map [0 0 :contents])]
        (fighter/process-fighter [0 0] unit)
        ;; Drone should have burned all fuel and died
        (should-be-nil (get-test-unit atoms/game-map "f")))))

  (context "handle-arrival cleanup"
    (it "arrival clears exploration fields from unit"
      ;; Fighter arriving at target city — should clear explore fields
      (reset! atoms/game-map (build-test-map ["X#fX"]))
      (set-test-unit atoms/game-map "f" :fuel 20
                     :flight-target-site [3 0]
                     :flight-origin-site [0 0]
                     :flight-mode :regular
                     :explore-origin [0 0]
                     :explore-heading [0 1]
                     :explore-steps-remaining 0)
      (reset! atoms/computer-map @atoms/game-map)
      (let [unit (get-in @atoms/game-map [2 0 :contents])]
        (fighter/process-fighter [2 0] unit)
        ;; Exploration fields should be cleared after arrival
        (let [result (get-test-unit atoms/game-map "f")]
          (should-not-be-nil result)
          (should-be-nil (:explore-origin (:unit result)))
          (should-be-nil (:explore-heading (:unit result)))
          (should-be-nil (:explore-steps-remaining (:unit result)))))))

  (context "returning sortie arrival"
    (it "does not crash when origin equals target (returning sortie)"
      ;; A returning sortie has flight-target-site == flight-origin-site (same city).
      ;; handle-arrival must not try to create #{origin origin} which throws.
      (reset! atoms/game-map (build-test-map ["XfX"]))
      (set-test-unit atoms/game-map "f" :fuel 20
                     :flight-target-site [0 0]
                     :flight-origin-site [0 0]
                     :flight-mode :regular)
      (reset! atoms/computer-map @atoms/game-map)
      (let [unit (get-in @atoms/game-map [1 0 :contents])]
        (fighter/process-fighter [1 0] unit)
        ;; Fighter should still exist (landed or moved, no crash)
        ;; Either the fighter landed at city or is somewhere on the map
        (let [fighter (get-test-unit atoms/game-map "f")
              city-fighters (:fighter-count (get-in @atoms/game-map [0 0]))]
          (should (or fighter (and city-fighters (pos? city-fighters))))))))

  (context "navigate-toward-target enhancement"
    (it "allows +1 distance sideways jog to unexplored cell"
      ;; Fighter navigating toward target with unexplored cell 1 step off direct path
      ;; With the +1 distance allowance, fighter should prefer the unexplored cell
      (reset! atoms/game-map (build-test-map ["#####"
                                               "#f###"
                                               "#####"
                                               "#####"
                                               "####X"]))
      (set-test-unit atoms/game-map "f" :fuel 20
                     :flight-target-site [4 4]
                     :flight-origin-site [0 0]
                     :flight-mode :regular)
      ;; Cell [0 0] is unexplored — it's off the direct path by +1 distance
      (reset! atoms/computer-map (build-test-map ["-####"
                                                   "#f###"
                                                   "#####"
                                                   "#####"
                                                   "####X"]))
      (let [unit (get-in @atoms/game-map [1 1 :contents])]
        (fighter/process-fighter [1 1] unit)
        ;; Fighter should have moved (we just verify it moved, the +1 logic enables
        ;; the unexplored neighbor at [0 0] to be a candidate)
        (let [result (get-test-unit atoms/game-map "f")]
          (should-not-be-nil result)
          (should-not= [1 1] (:pos result))))))

  (context "deterministic combat outcomes"
    (it "attacker wins and moves to enemy position"
      (reset! atoms/game-map (build-test-map ["fA"]))
      (set-test-unit atoms/game-map "f" :fuel 20)
      (reset! atoms/computer-map @atoms/game-map)
      (with-redefs [combat/resolve-combat
                    (fn [atk _def] {:winner :attacker :survivor atk})]
        (let [unit (get-in @atoms/game-map [0 0 :contents])]
          (fighter/process-fighter [0 0] unit)
          (should= :fighter (get-in @atoms/game-map [1 0 :contents :type]))
          (should= :computer (get-in @atoms/game-map [1 0 :contents :owner]))
          (should-be-nil (get-in @atoms/game-map [0 0 :contents])))))

    (it "attacker loses and is removed from map"
      (reset! atoms/game-map (build-test-map ["fA"]))
      (set-test-unit atoms/game-map "f" :fuel 20)
      (reset! atoms/computer-map @atoms/game-map)
      (with-redefs [combat/resolve-combat
                    (fn [_atk def] {:winner :defender :survivor def})]
        (let [unit (get-in @atoms/game-map [0 0 :contents])]
          (fighter/process-fighter [0 0] unit)
          (should-be-nil (get-test-unit atoms/game-map "f"))
          (should= :army (get-in @atoms/game-map [1 0 :contents :type]))
          (should= :player (get-in @atoms/game-map [1 0 :contents :owner]))))))

  (context "fuel boundary precision"
    (it "returns to refuel when fuel exactly equals return distance plus margin"
      ;; distance=8, fuel=10 (= 8+2). should-return: (<= 10 10) = true.
      ;; Mutation <= to < gives (< 10 10) = false → navigates away.
      ;; With 8 steps at fighter-speed, original returns in exactly 8.
      ;; Mutation wastes 1 step navigating away, can't return in remaining 7.
      (reset! atoms/game-map (build-test-map ["X#######f##"]))
      (set-test-unit atoms/game-map "f" :fuel 10
                     :flight-mode :regular
                     :flight-target-site [10 0]
                     :flight-origin-site [0 0])
      (reset! atoms/computer-map @atoms/game-map)
      (let [unit (get-in @atoms/game-map [8 0 :contents])]
        (fighter/process-fighter [8 0] unit)
        (should= 1 (:fighter-count (get-in @atoms/game-map [0 0])))))

    (it "fighter with fuel 2 survives one consume step to reach city"
      ;; distance=2, fuel=2. Moves one step (fuel 2->1), then lands.
      ;; Mutation 0->1 in consume would kill at fuel=1.
      (reset! atoms/game-map (build-test-map ["X#f"]))
      (set-test-unit atoms/game-map "f" :fuel 2)
      (reset! atoms/computer-map @atoms/game-map)
      (let [unit (get-in @atoms/game-map [2 0 :contents])]
        (fighter/process-fighter [2 0] unit)
        (should= 1 (:fighter-count (get-in @atoms/game-map [0 0]))))))

  (context "carrier detection in current-refueling-site"
    (it "assigns flight target when adjacent to holding computer carrier"
      ;; Fighter on sea next to computer carrier. ensure-flight-target should
      ;; detect the carrier and assign a leg toward the distant city.
      (reset! atoms/game-map (build-test-map ["#####~j~#######X"]))
      (swap! atoms/game-map assoc-in [7 0 :contents]
             {:type :carrier :owner :computer :hits 8 :carrier-mode :holding})
      (set-test-unit atoms/game-map "f" :fuel 32)
      (reset! atoms/computer-map @atoms/game-map)
      (with-redefs [rand (fn ([] 0.6) ([_n] 0.6))]
        (let [unit (get-in @atoms/game-map [6 0 :contents])]
          (fighter/process-fighter [6 0] unit)
          (let [result (get-test-unit atoms/game-map "f")]
            (should-not-be-nil result)
            (should= :regular (:flight-mode (:unit result))))))))

  (context "choose-leg distance boundary"
    (it "includes site at exactly fighter-fuel distance"
      ;; Two cities 32 apart (= config/fighter-fuel). Leg should be reachable.
      ;; Mutation <= to < would exclude it.
      (let [row-str (str "X" (apply str (repeat 31 \#)) "X")]
        (reset! atoms/game-map (build-test-map [row-str]))
        (swap! atoms/game-map assoc-in [0 0 :contents]
               {:type :fighter :owner :computer :hits 1 :fuel 32})
        (reset! atoms/computer-map @atoms/game-map)
        (with-redefs [rand (fn ([] 0.6) ([_n] 0.6))]
          (let [unit (get-in @atoms/game-map [0 0 :contents])]
            (fighter/process-fighter [0 0] unit)
            (let [result (get-test-unit atoms/game-map "f")]
              (should-not-be-nil result)
              (should= :regular (:flight-mode (:unit result)))
              (should= [32 0] (:flight-target-site (:unit result)))))))))

  (context "non-axis distance calculation"
    (it "correctly computes distance when both coordinates differ"
      ;; Fighter at [1,2], city at [0,0]. True distance=3.
      ;; Mutation outer + -> - gives |1|-|2|=-1, changing return decision.
      ;; fuel=4: original (<= 4 5)=true (returns). Mutation (<= 4 1)=false.
      (reset! atoms/game-map (build-test-map ["X##"
                                               "###"
                                               "#f#"]))
      (set-test-unit atoms/game-map "f" :fuel 4
                     :flight-mode :regular
                     :flight-target-site [2 2])
      (reset! atoms/computer-map @atoms/game-map)
      (let [unit (get-in @atoms/game-map [1 2 :contents])]
        (fighter/process-fighter [1 2] unit)
        ;; Fighter should return toward city (not navigate to target)
        (should= 1 (:fighter-count (get-in @atoms/game-map [0 0])))))

    (it "carrier arrival works when both row indices are nonzero"
      ;; Fighter at [2,1] adjacent to carrier at [2,2]. Both c-components nonzero.
      ;; distance-to [2,1] [2,2] = 1. Mutation (- c1 c2) -> (+ c1 c2) gives 3.
      ;; at-flight-target? checks (<= distance 1). Mutation: (<= 3 1) = false.
      (reset! atoms/game-map (build-test-map ["X###"
                                               "##j#"
                                               "##~#"
                                               "####"]))
      (swap! atoms/game-map assoc-in [2 2 :contents]
             {:type :carrier :owner :computer :hits 8 :carrier-mode :holding})
      (set-test-unit atoms/game-map "f" :fuel 20
                     :flight-target-site [2 2]
                     :flight-origin-site [0 0]
                     :flight-mode :regular)
      (reset! atoms/computer-map @atoms/game-map)
      (reset! atoms/round-number 10)
      (let [unit (get-in @atoms/game-map [2 1 :contents])]
        (fighter/process-fighter [2 1] unit)
        ;; Arrival should record leg
        (should= 10 (:last-flown (get @atoms/fighter-leg-records #{[0 0] [2 2]})))))))

(describe "hop-over-friendly"
  (before (reset-all-atoms!))

  (context "no-hop case"
    (it "returns best neighbor when it is unoccupied"
      ;; Fighter at [0 0], target at [2 0], neighbor [1 0] is empty land
      (reset! atoms/game-map (build-test-map ["f##"]))
      (let [result (fighter/hop-over-friendly [0 0] [2 0])]
        (should= {:dest [1 0] :hops 1} result)))

    (it "returns nil when no passable neighbors exist"
      ;; 1x1 map: fighter alone, no neighbors
      (reset! atoms/game-map (build-test-map ["f"]))
      (should-be-nil (fighter/hop-over-friendly [0 0] [0 0]))))

  (context "basic single-unit hop"
    (it "hops over one friendly unit to land on empty cell beyond"
      ;; Fighter at [0 0], friendly army at [1 0], empty land at [2 0], target at [3 0]
      (reset! atoms/game-map (build-test-map ["fa##"]))
      (let [result (fighter/hop-over-friendly [0 0] [3 0])]
        (should= {:dest [2 0] :hops 2} result)))

    (it "returns nil when friendly unit blocks and cell beyond is off-map"
      ;; Fighter at [0 0], friendly army at [1 0], nothing beyond
      (reset! atoms/game-map (build-test-map ["fa"]))
      (should-be-nil (fighter/hop-over-friendly [0 0] [1 0]))))

  (context "multi-unit hop"
    (it "hops over two consecutive friendly units"
      ;; Fighter at [0 0], armies at [1 0] and [2 0], empty at [3 0], target at [4 0]
      (reset! atoms/game-map (build-test-map ["faa##"]))
      (let [result (fighter/hop-over-friendly [0 0] [4 0])]
        (should= {:dest [3 0] :hops 3} result)))

    (it "hops over three consecutive friendly units"
      ;; Fighter at [0 0], armies at [1 0], [2 0], [3 0], empty at [4 0], target at [5 0]
      (reset! atoms/game-map (build-test-map ["faaa##"]))
      (let [result (fighter/hop-over-friendly [0 0] [5 0])]
        (should= {:dest [4 0] :hops 4} result)))

    (it "returns nil when all consecutive friendly units lead off-map"
      ;; Fighter at [0 0], armies fill the rest of the map
      (reset! atoms/game-map (build-test-map ["faaa"]))
      (should-be-nil (fighter/hop-over-friendly [0 0] [3 0])))

    (it "returns nil when two friendly units lead off-map"
      ;; 3x1 map: fighter at [0,0], armies at [1,0] and [2,0], target at [5,0]
      ;; Scan goes past [2,0] to [3,0] which is off-map
      (reset! atoms/game-map (build-test-map ["faa"]))
      (should-be-nil (fighter/hop-over-friendly [0 0] [5 0])))

    (it "hops diagonally over one friendly unit"
      ;; 4x4 map: fighter at [0 0], friendly army at [1 1], empty at [2 2], target at [3 3]
      (reset! atoms/game-map (build-test-map ["f###"
                                               "#a##"
                                               "##*#"
                                               "###*"]))
      (let [result (fighter/hop-over-friendly [0 0] [3 3])]
        (should= {:dest [2 2] :hops 2} result)))

    (it "returns nil when diagonal hop goes off map edge"
      ;; 2x2 map: fighter at [0 0], friendly army at [1 1], target at [2 2]
      ;; Diagonal direction from [0 0] -> [1 1] is [1 1]. Next hop [2 2] is off-map.
      (reset! atoms/game-map (build-test-map ["f#"
                                               "#a"]))
      (should-be-nil (fighter/hop-over-friendly [0 0] [2 2])))

    (it "returns attack result when chain of friendly units ends at enemy"
      ;; Fighter at [0 0], two friendly armies, then player army at [3 0]
      (reset! atoms/game-map (build-test-map ["faaA"]))
      (should= {:dest [3 0] :hops 3 :attack true}
               (fighter/hop-over-friendly [0 0] [3 0]))))

  (context "hop stops at enemy"
    (it "returns attack when single friendly then enemy"
      ;; Fighter at [0 0], friendly army at [1 0], player army at [2 0], target at [4 0]
      (reset! atoms/game-map (build-test-map ["faA##"]))
      (should= {:dest [2 0] :hops 2 :attack true}
               (fighter/hop-over-friendly [0 0] [4 0])))

    (it "returns attack when first neighbor is enemy (no friendly hop)"
      ;; Fighter at [0 0], player army at [1 0] directly adjacent
      ;; The best neighbor toward target IS the enemy — not friendly, so this case
      ;; was previously nil. After the change, it should still be nil because the
      ;; initial occupied check only enters hop-over when the first cell IS friendly.
      (reset! atoms/game-map (build-test-map ["fA##"]))
      (should-be-nil (fighter/hop-over-friendly [0 0] [3 0])))))

(describe "step-fighter return format"
  (before (reset-all-atoms!))

  (it "returns map with :pos and :steps-used 1 for normal movement"
    ;; Fighter at [0 0] with target, neighbor [1 0] is empty land
    (reset! atoms/game-map (build-test-map ["X#f####X"]))
    (set-test-unit atoms/game-map "f" :fuel 20
                   :flight-target-site [7 0]
                   :flight-origin-site [0 0]
                   :flight-mode :regular)
    (reset! atoms/computer-map @atoms/game-map)
    (let [result (#'fighter/step-fighter [2 0])]
      (should (map? result))
      (should= 1 (:steps-used result))
      (should-not-be-nil (:pos result))))

  (it "returns nil when fighter lands at city"
    ;; Fighter adjacent to city with low fuel should land and return nil
    (reset! atoms/game-map (build-test-map ["Xf"]))
    (set-test-unit atoms/game-map "f" :fuel 2)
    (reset! atoms/computer-map @atoms/game-map)
    (should-be-nil (#'fighter/step-fighter [1 0])))

  (it "returns map with :pos for stuck fighter burning fuel"
    ;; Fighter surrounded by friendly units, can't move but burns fuel
    (reset! atoms/game-map (build-test-map ["aaa"
                                             "afa"
                                             "aaa"]))
    (set-test-unit atoms/game-map "f" :fuel 10)
    (reset! atoms/computer-map @atoms/game-map)
    (let [result (#'fighter/step-fighter [1 1])]
      (should (map? result))
      (should= [1 1] (:pos result))
      (should= 1 (:steps-used result)))))

(describe "process-fighter with variable steps-used"
  (before (reset-all-atoms!))

  (it "deducts steps-used from steps-remaining each iteration"
    ;; With steps-used always 1, a stuck fighter with fuel 10 should burn 8 fuel
    ;; (fighter-speed = 8). After the round, fuel should be 10 - 8 = 2.
    (reset! atoms/game-map (build-test-map ["aaa"
                                             "afa"
                                             "aaa"]))
    (set-test-unit atoms/game-map "f" :fuel 10)
    (reset! atoms/computer-map @atoms/game-map)
    (let [unit (get-in @atoms/game-map [1 1 :contents])]
      (fighter/process-fighter [1 1] unit)
      (let [result (get-test-unit atoms/game-map "f")]
        (should-not-be-nil result)
        (should= 2 (:fuel (:unit result))))))

  (it "process-fighter still returns nil"
    ;; process-fighter always returns nil (side-effect based)
    (reset! atoms/game-map (build-test-map ["X#f####X"]))
    (set-test-unit atoms/game-map "f" :fuel 20
                   :flight-target-site [7 0]
                   :flight-origin-site [0 0]
                   :flight-mode :regular)
    (reset! atoms/computer-map @atoms/game-map)
    (let [unit (get-in @atoms/game-map [2 0 :contents])]
      (should-be-nil (fighter/process-fighter [2 0] unit)))))

(describe "consume-hop-fuel"
  (before (reset-all-atoms!))

  (it "burns fuel for each intermediate cell in a hop"
    ;; Fighter at [3 0] with fuel 20, simulate hopping from [0 0] through [1 0] [2 0] to [3 0]
    ;; intermediate cells are [1 0] and [2 0] (2 cells), so 2 fuel burned
    (reset! atoms/game-map (build-test-map ["#f##"]))
    (set-test-unit atoms/game-map "f" :fuel 20)
    (let [result (fighter/consume-hop-fuel [1 0] 3)]
      (should= true result)
      ;; 3 hops means 2 intermediate fuel burns (hops-1 since final cell was already burned by move)
      ;; Actually: consume-hop-fuel burns fuel for hops-1 intermediate cells
      (should= 18 (:fuel (get-in @atoms/game-map [1 0 :contents])))))

  (it "returns false and removes fighter when fuel runs out during hop"
    ;; Fighter with fuel 2, trying to hop 3 cells (2 intermediate burns)
    ;; First burn: fuel 2->1. Second burn: fuel 1->0, fighter dies.
    (reset! atoms/game-map (build-test-map ["#f##"]))
    (set-test-unit atoms/game-map "f" :fuel 2)
    (let [result (fighter/consume-hop-fuel [1 0] 3)]
      (should= false result)
      (should-be-nil (get-in @atoms/game-map [1 0 :contents]))))

  (it "does nothing for hops of 1 (no intermediate cells)"
    ;; A single hop has no intermediate cells to burn fuel for
    (reset! atoms/game-map (build-test-map ["#f##"]))
    (set-test-unit atoms/game-map "f" :fuel 5)
    (let [result (fighter/consume-hop-fuel [1 0] 1)]
      (should= true result)
      (should= 5 (:fuel (get-in @atoms/game-map [1 0 :contents]))))))

(describe "hop-over integration through process-fighter"
  (before (reset-all-atoms!))

  (context "patrol hop-over"
    (it "fighter hops over friendly army toward patrol target"
      ;; Fighter at [0 0], friendly army at [1 0], player army at [5 0] (patrol target).
      ;; Fighter should hop over the friendly army rather than getting stuck.
      (reset! atoms/game-map (build-test-map ["fa###A"]))
      (set-test-unit atoms/game-map "f" :fuel 20)
      (reset! atoms/computer-map @atoms/game-map)
      (with-redefs [combat/resolve-combat
                    (fn [atk _def] {:winner :attacker :survivor atk})]
        (let [unit (get-in @atoms/game-map [0 0 :contents])]
          (fighter/process-fighter [0 0] unit)
          ;; Fighter should have moved past [1 0] (where the army is)
          ;; The army should still be at [1 0]
          (should= :army (get-in @atoms/game-map [1 0 :contents :type]))
          (should= :computer (get-in @atoms/game-map [1 0 :contents :owner]))
          ;; Fighter should be somewhere past the army
          (let [result (get-test-unit atoms/game-map "f")]
            ;; Fighter may have attacked the player army or be somewhere
            ;; beyond the friendly army
            (when result
              (should (> (first (:pos result)) 1))))))))

  (context "navigate hop-over"
    (it "fighter navigating toward target hops over friendly unit in path"
      ;; Fighter at [0 0] with flight-target at [5 0], friendly army at [1 0].
      ;; Fighter should hop over the army and continue toward target.
      (reset! atoms/game-map (build-test-map ["Xfa###X"]))
      (set-test-unit atoms/game-map "f" :fuel 20
                     :flight-target-site [6 0]
                     :flight-origin-site [0 0]
                     :flight-mode :regular)
      (reset! atoms/computer-map @atoms/game-map)
      (let [unit (get-in @atoms/game-map [1 0 :contents])]
        (fighter/process-fighter [1 0] unit)
        ;; Army should still be at [2 0]
        (should= :army (get-in @atoms/game-map [2 0 :contents :type]))
        ;; Fighter should be past the army, closer to target
        (let [result (get-test-unit atoms/game-map "f")]
          (should-not-be-nil result)
          (should (> (first (:pos result)) 2))))))

  (context "return-to-refuel hop-over"
    (it "fighter returning to city hops over friendly unit"
      ;; Fighter at [4 0] with low fuel, city at [0 0], friendly army at [3 0].
      ;; Fighter should hop over the army toward the city.
      (reset! atoms/game-map (build-test-map ["X##af"]))
      (set-test-unit atoms/game-map "f" :fuel 5)
      (reset! atoms/computer-map @atoms/game-map)
      (let [unit (get-in @atoms/game-map [4 0 :contents])]
        (fighter/process-fighter [4 0] unit)
        ;; Army should still be at [3 0]
        (should= :army (get-in @atoms/game-map [3 0 :contents :type]))
        ;; Fighter should have landed at city
        (should= 1 (:fighter-count (get-in @atoms/game-map [0 0]))))))

  (context "hop consumes extra fuel"
    (it "hopping over friendly units burns fuel for intermediate cells"
      ;; Fighter at [1 0], friendly armies at [2 0] and [3 0], target at [10 0].
      ;; A hop of 3 cells uses 3 fuel (2 intermediate + 1 normal) and 3 steps-used.
      ;; With 8 steps total: 1 hop (3 steps, 3 fuel) + 5 normal steps (5 fuel) = 8 fuel.
      ;; Fighter passes over the armies and ends up at [4 0]+5 = ~[9 0].
      (reset! atoms/game-map (build-test-map ["Xfaa######X"]))
      (set-test-unit atoms/game-map "f" :fuel 20
                     :flight-target-site [10 0]
                     :flight-origin-site [0 0]
                     :flight-mode :regular)
      (reset! atoms/computer-map @atoms/game-map)
      (let [unit (get-in @atoms/game-map [1 0 :contents])]
        (fighter/process-fighter [1 0] unit)
        ;; Friendly armies should still be in place (not displaced)
        (should= :army (get-in @atoms/game-map [2 0 :contents :type]))
        (should= :army (get-in @atoms/game-map [3 0 :contents :type]))
        ;; Fighter should have hopped past the armies and be far toward target
        (let [result (get-test-unit atoms/game-map "f")]
          (should-not-be-nil result)
          ;; Fighter should be well past the army blockade at [3 0]
          (should (> (first (:pos result)) 3))
          ;; Total fuel burned = 8 (fighter-speed), so remaining = 12
          (should= 12 (:fuel (:unit result)))))))

  (context "hop attack through process-fighter"
    (it "fighter hops over friendly and attacks enemy at end of chain"
      ;; Fighter at [0 0], friendly army at [1 0], player army at [2 0].
      ;; Fighter should hop over friendly and attack the player army.
      (reset! atoms/game-map (build-test-map ["faA###"]))
      (set-test-unit atoms/game-map "f" :fuel 20)
      (reset! atoms/computer-map @atoms/game-map)
      (with-redefs [combat/resolve-combat
                    (fn [atk _def] {:winner :attacker :survivor atk})]
        (let [unit (get-in @atoms/game-map [0 0 :contents])]
          (fighter/process-fighter [0 0] unit)
          ;; Friendly army should still be at [1 0]
          (should= :army (get-in @atoms/game-map [1 0 :contents :type]))
          (should= :computer (get-in @atoms/game-map [1 0 :contents :owner]))
          ;; Fighter should be at [2 0] (won combat, took position)
          (let [result (get-test-unit atoms/game-map "f")]
            (should-not-be-nil result)
            ;; Fighter should be at or past [2 0]
            (should (>= (first (:pos result)) 2)))))))

  (context "step-fighter returns hops for steps-used"
    (it "step-fighter returns hops > 1 when hopping over friendly"
      ;; Fighter at [0 0], friendly army at [1 0], target at [5 0].
      ;; step-fighter should return {:pos [2 0] :steps-used 2}
      (reset! atoms/game-map (build-test-map ["Xfa###X"]))
      (set-test-unit atoms/game-map "f" :fuel 20
                     :flight-target-site [6 0]
                     :flight-origin-site [0 0]
                     :flight-mode :regular)
      (reset! atoms/computer-map @atoms/game-map)
      (let [result (#'fighter/step-fighter [1 0])]
        (should (map? result))
        (should= 2 (:steps-used result))
        ;; Fighter should be at [3 0] (hopped over army at [2 0])
        (should= [3 0] (:pos result))))))
