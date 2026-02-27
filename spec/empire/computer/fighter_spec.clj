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
          (should= 2 (:fuel (:unit result))))))))

(describe "handle-low-fuel helpers"
  (before (reset-all-atoms!))

  (it "adjacent-to-city-site? returns true for adjacent city site"
    (reset! atoms/game-map (build-test-map ["Xf"]))
    (should (@#'fighter/adjacent-to-city-site? [0 0] [1 0])))

  (it "adjacent-to-city-site? returns false for nil site"
    (reset! atoms/game-map (build-test-map ["#f"]))
    (should-not (@#'fighter/adjacent-to-city-site? nil [1 0])))

  (it "adjacent-to-city-site? returns false for non-city site"
    (reset! atoms/game-map (build-test-map ["#f"]))
    (should-not (@#'fighter/adjacent-to-city-site? [0 0] [1 0])))

  (it "adjacent-to-city-site? returns false for distant city"
    (reset! atoms/game-map (build-test-map ["X#f"]))
    (should-not (@#'fighter/adjacent-to-city-site? [0 0] [2 0])))

  (it "adjacent-to-site? returns true for adjacent site"
    (should (@#'fighter/adjacent-to-site? [0 0] [1 0])))

  (it "adjacent-to-site? returns false for nil site"
    (should-not (@#'fighter/adjacent-to-site? nil [1 0])))

  (it "adjacent-to-site? returns false for distant site"
    (should-not (@#'fighter/adjacent-to-site? [0 0] [3 0])))

  (it "desperate-patrol returns nil when do-patrol returns nil"
    (reset! atoms/game-map (build-test-map ["f"]))
    (set-test-unit atoms/game-map "f" :fuel 5)
    (reset! atoms/computer-map @atoms/game-map)
    (should-be-nil (@#'fighter/desperate-patrol [0 0])))

  (it "desperate-patrol returns result when patrol succeeds"
    (reset! atoms/game-map (build-test-map ["f##"]))
    (set-test-unit atoms/game-map "f" :fuel 5)
    (reset! atoms/computer-map (build-test-map ["f--"]))
    (let [result (@#'fighter/desperate-patrol [0 0])]
      (should-not-be-nil result)
      (should (contains? result :pos))
      (should (contains? result :hops)))))
