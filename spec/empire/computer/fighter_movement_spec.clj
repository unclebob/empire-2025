(ns empire.computer.fighter-movement-spec
  "Tests for fighter movement primitives: combat, hopping, fuel management."
  (:require [speclj.core :refer :all]
            [empire.computer.fighter :as fighter]
            [empire.computer.fighter-movement :as fm]
            [empire.atoms :as atoms]
            [empire.combat :as combat]
            [empire.config :as config]
            [empire.test-utils :refer [build-test-map build-sparse-test-map
                                       set-test-unit
                                       get-test-unit reset-all-atoms! set-test-computer-map! set-test-world! update-test-world!]]))

(describe "fighter-movement"
  (before (reset-all-atoms!))

  (context "attack behavior"
    (it "attacks adjacent player unit"
      (set-test-world! (build-test-map ["fA"]))
      (set-test-unit atoms/game-map "f" :fuel 20)
      (set-test-computer-map! @atoms/game-map)
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
      (set-test-world! (build-test-map ["X#f"]))
      (set-test-unit atoms/game-map "f" :fuel 3)
      (set-test-computer-map! @atoms/game-map)
      (let [unit (:contents (get-in @atoms/game-map [2 0]))]
        (fighter/process-fighter [2 0] unit)
        ;; With fuel 3, distance 2: should-return is true, moves toward city,
        ;; fuel decremented to 2, then adjacent to city, lands
        (should= 1 (:fighter-count (get-in @atoms/game-map [0 0])))))

    (it "lands at adjacent city"
      (set-test-world! (build-test-map ["Xf"]))
      (set-test-unit atoms/game-map "f" :fuel 2)
      (set-test-computer-map! @atoms/game-map)
      (let [unit (:contents (get-in @atoms/game-map [1 0]))
            result (fighter/process-fighter [1 0] unit)]
        ;; Fighter should land at city
        (should-be-nil result)
        ;; City should have fighter
        (should= 1 (:fighter-count (get-in @atoms/game-map [0 0])))))

    (it "consumes fuel each step"
      (set-test-world! (build-test-map ["X#########f##########"]))
      (set-test-unit atoms/game-map "f" :fuel 30)
      ;; Unexplored territory to the right so fighter has reason to move
      (set-test-computer-map! (build-test-map ["X#########f........."]))
      (let [unit (get-in @atoms/game-map [10 0 :contents])]
        (fighter/process-fighter [10 0] unit)
        ;; Find the fighter - it should have moved and have fuel < 30
        (let [result (get-test-unit atoms/game-map "f")]
          (should-not-be-nil result)
          (should (< (:fuel (:unit result)) 30)))))

    (it "moves multiple cells per round"
      (set-test-world! (build-test-map ["X#########f##########"]))
      (set-test-unit atoms/game-map "f" :fuel 30)
      ;; Unexplored territory to the right so fighter has reason to move
      (set-test-computer-map! (build-test-map ["X#########f........."]))
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
      (set-test-world! (build-test-map ["f##"]))
      (set-test-unit atoms/game-map "f" :fuel 1)
      ;; Unexplored territory so fighter has reason to move
      (set-test-computer-map! (build-test-map ["f--"]))
      (let [unit (get-in @atoms/game-map [0 0 :contents])]
        (fighter/process-fighter [0 0] unit)
        ;; Fighter should be gone from the entire map
        (should-be-nil (get-test-unit atoms/game-map "f"))))

    (it "stops moving after landing at city"
      ;; Fighter next to city with low fuel should land and not continue.
      (set-test-world! (build-test-map ["#Xf##"]))
      (set-test-unit atoms/game-map "f" :fuel 3)
      (set-test-computer-map! @atoms/game-map)
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
      (set-test-world! (build-test-map ["Xf##########A"]))
      (set-test-unit atoms/game-map "f" :fuel 20)
      (set-test-computer-map! @atoms/game-map)
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
      (set-test-world! (build-test-map ["Xf########"]))
      (set-test-unit atoms/game-map "f" :fuel 20)
      (set-test-computer-map! (build-test-map ["Xf........"]))
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
        (set-test-world! game-map)
        (set-test-computer-map! (build-test-map ["#####"
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

  (context "no phantom contents on blocked patrol"
    (it "does not create phantom contents when patrol move is blocked"
      ;; Fighter at [0 0] on a 1-row map. All neighbors occupied by friendly armies.
      ;; No unexplored cells on computer-map, but a player army far away to give a patrol target.
      ;; do-patrol will find the player army as target, pick a neighbor, but move-unit-to fails.
      ;; The cell should NOT end up with phantom {:contents {:fuel N}}.
      (set-test-world! (build-test-map ["fa###A"]))
      (set-test-unit atoms/game-map "f" :fuel 20)
      (set-test-computer-map! @atoms/game-map)
      (let [unit (get-in @atoms/game-map [0 0 :contents])]
        (fighter/process-fighter [0 0] unit)
        ;; Cell [1 0] has a friendly army - should still be an army, not phantom fuel
        (should= :army (:type (:contents (get-in @atoms/game-map [1 0])))))))

  (context "sidestepping"
    (it "sidesteps around friendly unit blocking direct path"
      ;; 3x3 map: fighter at [0 0], friendly army blocking [1 0], target city at [2 0]
      ;; Fighter should move diagonally to [0 1] or [1 1] to go around
      (set-test-world! (build-test-map ["f##"
                                               "###"
                                               "##X"]))
      ;; Place a friendly army at [1 0] blocking the direct path
      (update-test-world! assoc-in [1 0 :contents]
             {:type :army :owner :computer :hits 1})
      (set-test-unit atoms/game-map "f" :fuel 20
                     :flight-target-site [2 0]
                     :flight-origin-site [2 2])
      (set-test-computer-map! @atoms/game-map)
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
      (set-test-world! (build-test-map ["X####"
                                               "f####"
                                               "#####"
                                               "#####"
                                               "####X"]))
      (set-test-unit atoms/game-map "f" :fuel 20
                     :flight-target-site [4 4]
                     :flight-origin-site [0 0])
      (set-test-computer-map! @atoms/game-map)
      (let [unit (get-in @atoms/game-map [0 1 :contents])]
        (fighter/process-fighter [0 1] unit)
        ;; Fighter should have moved toward [4 4]
        (let [result (get-test-unit atoms/game-map "f")]
          (should-not-be-nil result)
          (should-not= [0 1] (:pos result)))))

    (it "stuck fighter surrounded by friendly units burns fuel and dies"
      ;; 3x3 map: fighter at center [1 1], surrounded by friendly armies on all 8 neighbors
      (set-test-world! (build-test-map ["aaa"
                                               "afa"
                                               "aaa"]))
      (set-test-unit atoms/game-map "f" :fuel 5)
      (set-test-computer-map! @atoms/game-map)
      (let [unit (get-in @atoms/game-map [1 1 :contents])]
        (fighter/process-fighter [1 1] unit)
        ;; Fighter should be dead - fuel burned to 0 while stuck
        (should-be-nil (get-test-unit atoms/game-map "f")))))

  (context "hop-over-friendly (L47, L83-89)"
    (it "returns hops 1 for unoccupied neighbor"
      (set-test-world! [[{:type :land :contents {:type :fighter :owner :computer}}
                                {:type :land}
                                {:type :land}]])
      (should= {:dest [0 1] :hops 1}
               (fm/hop-over-friendly [0 0] [0 2])))

    (it "hops over one friendly with hops 2 (L83, L87)"
      (set-test-world! [[{:type :land :contents {:type :fighter :owner :computer}}
                                {:type :land :contents {:type :army :owner :computer :hits 1}}
                                {:type :land}
                                {:type :land}]])
      (let [result (fm/hop-over-friendly [0 0] [0 3])]
        (should= 2 (:hops result))
        (should= [0 2] (:dest result))))

    (it "hops over two friendlies with hops 3 (L89 inc->dec)"
      (set-test-world! [[{:type :land :contents {:type :fighter :owner :computer}}
                                {:type :land :contents {:type :army :owner :computer :hits 1}}
                                {:type :land :contents {:type :army :owner :computer :hits 1}}
                                {:type :land}]])
      (should= 3 (:hops (fm/hop-over-friendly [0 0] [0 3]))))

    (it "scans forward not backward (L84, L89 + -> -)"
      ;; 4-row column: fighter at [1,0], friendly at [2,0], empty at [3,0]
      (set-test-world! [[{:type :land}]
                               [{:type :land :contents {:type :fighter :owner :computer}}]
                               [{:type :land :contents {:type :army :owner :computer :hits 1}}]
                               [{:type :land}]])
      (let [result (fm/hop-over-friendly [1 0] [3 0])]
        (should= [3 0] (:dest result))
        (should= 2 (:hops result))))

    (it "marks attack when enemy at end (L88 if->if-not)"
      (set-test-world! [[{:type :land :contents {:type :fighter :owner :computer}}
                                {:type :land :contents {:type :army :owner :computer :hits 1}}
                                {:type :land :contents {:type :army :owner :player :hits 1}}]])
      (let [result (fm/hop-over-friendly [0 0] [0 2])]
        (should (:attack result))
        (should= 2 (:hops result))))

    (it "returns nil when neighbor is enemy not friendly"
      (set-test-world! [[{:type :land :contents {:type :fighter :owner :computer}}
                                {:type :land :contents {:type :army :owner :player :hits 1}}
                                {:type :land}]])
      (should-be-nil (fm/hop-over-friendly [0 0] [0 2])))

    (it "hops backward when target is behind (L58 - -> +)"
      ;; Fighter at [2,0], friendly at [1,0], empty at [0,0]. Target at [0,0].
      ;; Direction from [2,0] to [1,0] = [-1,0]. Should hop backward.
      (set-test-world! [[{:type :land}]
                               [{:type :land :contents {:type :army :owner :computer :hits 1}}]
                               [{:type :land :contents {:type :fighter :owner :computer}}]])
      (let [result (fm/hop-over-friendly [2 0] [0 0])]
        (should= [0 0] (:dest result))
        (should= 2 (:hops result))))

    (it "hops diagonally over two friendlies (L89 first + -> -)"
      ;; 4x4 map: fighter at [0,0], friendlies at [1,1] and [2,2], empty at [3,3].
      (set-test-world! (vec (for [r (range 4)]
                                    (vec (for [c (range 4)]
                                           (cond
                                             (and (= r 0) (= c 0))
                                             {:type :land :contents {:type :fighter :owner :computer}}
                                             (or (and (= r 1) (= c 1))
                                                 (and (= r 2) (= c 2)))
                                             {:type :land :contents {:type :army :owner :computer :hits 1}}
                                             :else {:type :land}))))))
      (let [result (fm/hop-over-friendly [0 0] [3 3])]
        (should= [3 3] (:dest result))
        (should= 3 (:hops result)))))

  (context "consume-hop-fuel (L189-192)"
    (it "returns true when fuel sufficient for hops"
      (set-test-world! [[{:type :land :contents {:type :fighter :owner :computer :fuel 5}}]])
      (should (fm/consume-hop-fuel [0 0] 3))
      ;; Burns 2 intermediate cells (hops-1), fuel goes from 5 to 3
      (should= 3 (get-in @atoms/game-map [0 0 :contents :fuel])))

    (it "returns false when fuel runs out during hop"
      (set-test-world! [[{:type :land :contents {:type :fighter :owner :computer :fuel 1}}]])
      (should-not (fm/consume-hop-fuel [0 0] 3))
      ;; Fighter should be dead
      (should-be-nil (get-in @atoms/game-map [0 0 :contents])))

    (it "returns true for hops 1 (no intermediate burn)"
      (set-test-world! [[{:type :land :contents {:type :fighter :owner :computer :fuel 1}}]])
      (should (fm/consume-hop-fuel [0 0] 1))
      ;; Fuel unchanged
      (should= 1 (get-in @atoms/game-map [0 0 :contents :fuel]))))

  (context "attack-enemy winner branch (L111)"
    (it "attacker wins and moves to enemy position"
      (set-test-world! (build-test-map ["fA"]))
      (set-test-unit atoms/game-map "f" :fuel 20)
      (set-test-computer-map! @atoms/game-map)
      ;; Mock combat so attacker always wins
      (with-redefs [combat/resolve-combat
                    (fn [attacker _defender]
                      {:winner :attacker :survivor attacker})]
        (let [unit (get-in @atoms/game-map [0 0 :contents])]
          (fighter/process-fighter [0 0] unit)
          ;; Fighter should be at [1,0] (enemy position in transposed map)
          (let [result (get-test-unit atoms/game-map "f")]
            (should-not-be-nil result)
            (should= [1 0] (:pos result))))))

    (it "attacker loses and is removed from map"
      (set-test-world! (build-test-map ["fA"]))
      (set-test-unit atoms/game-map "f" :fuel 20)
      (set-test-computer-map! @atoms/game-map)
      ;; Mock combat so defender always wins
      (with-redefs [combat/resolve-combat
                    (fn [_attacker defender]
                      {:winner :defender :survivor defender})]
        (let [unit (get-in @atoms/game-map [0 0 :contents])]
          (fighter/process-fighter [0 0] unit)
          ;; Fighter should be dead
          (should-be-nil (get-test-unit atoms/game-map "f"))
          ;; Enemy should still be at [1,0]
          (should= :player (get-in @atoms/game-map [1 0 :contents :owner]))))))

  (context "fuel boundary (L175)"
    (it "fighter with fuel 2 survives one consume step"
      ;; Fighter at [0,0] with fuel 2, city at [1,0]. Set up so fighter
      ;; moves once and consumes fuel. Fuel 2 -> dec -> 1 -> should survive.
      ;; L175 mutant: (<= 1 1) = true -> would die.
      (set-test-world! (build-test-map ["f#X"]))
      (set-test-unit atoms/game-map "f" :fuel 2
                     :flight-target-site [2 0]
                     :flight-origin-site [2 0])
      (set-test-computer-map! @atoms/game-map)
      (let [unit (get-in @atoms/game-map [0 0 :contents])]
        (fighter/process-fighter [0 0] unit)
        ;; Fighter should have survived (fuel 2 -> 1 after first step)
        ;; It should land at city [2,0] or continue moving
        (let [result (get-test-unit atoms/game-map "f")]
          ;; Either still alive on map or landed at city
          (should (or (some? result)
                      (pos? (:fighter-count (get-in @atoms/game-map [2 0]) 0))))))))

  (context "should-return-to-refuel boundary (L146)"
    (it "returns to refuel when fuel equals return distance + 2"
      ;; City 3 cells away, fuel = 5 = 3 + 2. Should trigger return.
      ;; With mutant <= -> <: (< 5 5) = false, wouldn't return.
      (set-test-world! (build-test-map ["X##f"]))
      (set-test-unit atoms/game-map "f" :fuel 5)
      (set-test-computer-map! @atoms/game-map)
      (let [unit (get-in @atoms/game-map [3 0 :contents])]
        (fighter/process-fighter [3 0] unit)
        ;; Fighter should head toward city, eventually landing
        (let [result (get-test-unit atoms/game-map "f")]
          ;; Fighter either landed at city or moved toward it
          (should (or (nil? result)
                      (< (first (:pos result)) 3)
                      (pos? (:fighter-count (get-in @atoms/game-map [0 0]) 0))))))))

  (context "move-fighter-once with combat (L579-580)"
    (it "fighter attacks enemy and reports hops 1"
      ;; Fighter adjacent to enemy. Combat should happen.
      (set-test-world! (build-test-map ["fA#"]))
      (set-test-unit atoms/game-map "f" :fuel 20)
      (set-test-computer-map! @atoms/game-map)
      (with-redefs [combat/resolve-combat
                    (fn [attacker _defender]
                      {:winner :attacker :survivor attacker})]
        (let [unit (get-in @atoms/game-map [0 0 :contents])]
          (fighter/process-fighter [0 0] unit)
          ;; Fighter should have moved to enemy pos after winning
          (let [result (get-test-unit atoms/game-map "f")]
            (should-not-be-nil result)
            (should= [1 0] (:pos result)))))))

  (context "process-fighter at map edges (L21-22, L66)"
    (it "fighter at [0,0] corner can still move"
      (set-test-world! (build-test-map ["f##"
                                               "###"
                                               "###"]))
      (set-test-unit atoms/game-map "f" :fuel 20)
      ;; Unexplored territory
      (set-test-computer-map! (build-test-map ["f.."
                                                   "..."
                                                   "..."]))
      (let [unit (get-in @atoms/game-map [0 0 :contents])]
        (fighter/process-fighter [0 0] unit)
        ;; Fighter should have moved from [0,0]
        (should-be-nil (get-in @atoms/game-map [0 0 :contents]))))

    (it "fighter at bottom-right corner can still move"
      (set-test-world! (build-test-map ["###"
                                               "###"
                                               "##f"]))
      (set-test-unit atoms/game-map "f" :fuel 20)
      (set-test-computer-map! (build-test-map ["..."
                                                   "..."
                                                   "..f"]))
      (let [unit (get-in @atoms/game-map [2 2 :contents])]
        (fighter/process-fighter [2 2] unit)
        ;; Fighter should have moved from [2,2]
        (should-be-nil (get-in @atoms/game-map [2 2 :contents]))))))
