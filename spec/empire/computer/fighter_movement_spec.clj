(ns empire.computer.fighter-movement-spec
  "Tests for fighter movement primitives: combat, hopping, fuel management."
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.computer.fighter :as fighter]
            [empire.computer.fighter-movement :as fm]
            [empire.game-mechanics.services.combat :as combat]
            [empire.config.core :as config]
            [empire.test.utils :refer [build-test-map build-sparse-test-map
                                       set-test-unit
                                       get-test-unit reset-all-atoms! set-test-computer-map! set-test-world! update-test-world!]]))

(describe "fighter-movement"
  (before (reset-all-atoms!))

  (context "attack behavior"
    (it "attacks adjacent player unit"
      (set-test-world! (build-test-map ["fA"]))
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 20)
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [unit (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))
            _result (fighter/process-fighter [0 0] unit)]
        ;; Combat should have occurred - one unit should be gone
        (let [cell0 (get-in (test-utils/read-test-state :game-map) [0 0])
              cell1 (get-in (test-utils/read-test-state :game-map) [1 0])]
          (should (or (nil? (:contents cell0))
                      (nil? (:contents cell1))
                      (= :computer (:owner (:contents cell1)))))))))

  (context "fuel management"
    (it "returns to city when low on fuel"
      (set-test-world! (build-test-map ["X#f"]))
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 3)
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [unit (:contents (get-in (test-utils/read-test-state :game-map) [2 0]))]
        (fighter/process-fighter [2 0] unit)
        ;; With fuel 3, distance 2: should-return is true, moves toward city,
        ;; fuel decremented to 2, then adjacent to city, lands
        (should= 1 (:fighter-count (get-in (test-utils/read-test-state :game-map) [0 0])))))

    (it "lands at adjacent city"
      (set-test-world! (build-test-map ["Xf"]))
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 2)
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [unit (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))
            result (fighter/process-fighter [1 0] unit)]
        ;; Fighter should land at city
        (should-be-nil result)
        ;; City should have fighter
        (should= 1 (:fighter-count (get-in (test-utils/read-test-state :game-map) [0 0])))))

    (it "consumes fuel each step"
      (set-test-world! (build-test-map ["X#########f##########"]))
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 30)
      ;; Unexplored territory to the right so fighter has reason to move
      (set-test-computer-map! (build-test-map ["X#########f........."]))
      (let [unit (get-in (test-utils/read-test-state :game-map) [10 0 :contents])]
        (fighter/process-fighter [10 0] unit)
        ;; Find the fighter - it should have moved and have fuel < 30
        (let [result (get-test-unit (test-utils/game-map-atom) "f")]
          (should-not-be-nil result)
          (should (< (:fuel (:unit result)) 30)))))

    (it "moves multiple cells per round"
      (set-test-world! (build-test-map ["X#########f##########"]))
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 30)
      ;; Unexplored territory to the right so fighter has reason to move
      (set-test-computer-map! (build-test-map ["X#########f........."]))
      (let [unit (get-in (test-utils/read-test-state :game-map) [10 0 :contents])]
        (fighter/process-fighter [10 0] unit)
        ;; Fighter should NOT still be at [10 0]
        (should-be-nil (get-in (test-utils/read-test-state :game-map) [10 0 :contents]))
        ;; Fighter should have moved more than 1 cell from start
        (let [result (get-test-unit (test-utils/game-map-atom) "f")
              [fighter-col _] (:pos result)]
          (should-not-be-nil result)
          (should (> (Math/abs (- fighter-col 10)) 1)))))

    (it "fighter dies when fuel runs out"
      ;; Fighter with fuel 1 on open land, no city nearby.
      ;; After moving once, fuel becomes 0 and fighter should die.
      (set-test-world! (build-test-map ["f##"]))
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 1)
      ;; Unexplored territory so fighter has reason to move
      (set-test-computer-map! (build-test-map ["f--"]))
      (let [unit (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
        (fighter/process-fighter [0 0] unit)
        ;; Fighter should be gone from the entire map
        (should-be-nil (get-test-unit (test-utils/game-map-atom) "f"))))

    (it "stops moving after landing at city"
      ;; Fighter next to city with low fuel should land and not continue.
      (set-test-world! (build-test-map ["#Xf##"]))
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 3)
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [unit (get-in (test-utils/read-test-state :game-map) [2 0 :contents])]
        (fighter/process-fighter [2 0] unit)
        ;; Fighter should have landed at city [1 0]
        (should= 1 (:fighter-count (get-in (test-utils/read-test-state :game-map) [1 0])))
        ;; Fighter should NOT be on the map as a unit
        (should-be-nil (get-test-unit (test-utils/game-map-atom) "f")))))

    (it "logs and skips fuel burn when position no longer contains a computer fighter"
      (set-test-world! [[{:type :land}]])
      (let [err (java.io.StringWriter.)
            err-writer (java.io.PrintWriter. err)]
        (binding [*err* err-writer]
          (should-not-throw (fm/consume-fighter-fuel [0 0]))
          (.flush err-writer)
          (should-contain "Invalid fighter fuel update at [0 0]" (str err))
          (should-contain "consume-fighter-fuel called on non-computer-fighter at [0 0]" (str err))
          (should-be-nil (get-in (test-utils/read-test-state :game-map) [0 0 :contents]))))))

  (context "patrol behavior"
    (it "patrols toward player units when fuel allows"
      ;; Wide map so fighter patrols toward player unit
      ;; but doesn't reach it to avoid random combat outcomes
      (set-test-world! (build-test-map ["Xf##########A"]))
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 20)
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [unit (get-in (test-utils/read-test-state :game-map) [1 0 :contents])]
        (fighter/process-fighter [1 0] unit)
        ;; Fighter should have left [1 0] and moved toward the player army
        (should-be-nil (get-in (test-utils/read-test-state :game-map) [1 0 :contents]))
        ;; Fighter should be somewhere between start and player army
        (let [result (get-test-unit (test-utils/game-map-atom) "f")
              [fighter-col _] (:pos result)]
          (should-not-be-nil result)
          (should (> fighter-col 1)))))

    (it "explores toward unexplored territory"
      ;; Wide map with unexplored cells to the right
      (set-test-world! (build-test-map ["Xf########"]))
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 20)
      (set-test-computer-map! (build-test-map ["Xf........"]))
      (let [unit (get-in (test-utils/read-test-state :game-map) [1 0 :contents])]
        (fighter/process-fighter [1 0] unit)
        ;; Fighter should have moved away from start toward unexplored
        (should-be-nil (get-in (test-utils/read-test-state :game-map) [1 0 :contents]))
        (let [result (get-test-unit (test-utils/game-map-atom) "f")
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
        (set-test-unit (test-utils/game-map-atom) "f" :fuel 20)
        (let [unit (get-in (test-utils/read-test-state :game-map) [2 2 :contents])]
          (fighter/process-fighter [2 2] unit)
          ;; Fighter should have moved
          (should-be-nil (get-in (test-utils/read-test-state :game-map) [2 2 :contents]))
          ;; Find where fighter ended up
          (let [result (get-test-unit (test-utils/game-map-atom) "f")
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
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 20)
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [unit (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
        (fighter/process-fighter [0 0] unit)
        ;; Cell [1 0] has a friendly army - should still be an army, not phantom fuel
        (should= :army (:type (:contents (get-in (test-utils/read-test-state :game-map) [1 0])))))))

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
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 20
                     :flight-target-site [2 0]
                     :flight-origin-site [2 2])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [unit (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
        (fighter/process-fighter [0 0] unit)
        ;; Fighter should NOT still be at [0 0] - it should have sidestepped
        (let [result (get-test-unit (test-utils/game-map-atom) "f")]
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
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 20
                     :flight-target-site [4 4]
                     :flight-origin-site [0 0])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [unit (get-in (test-utils/read-test-state :game-map) [0 1 :contents])]
        (fighter/process-fighter [0 1] unit)
        ;; Fighter should have moved toward [4 4]
        (let [result (get-test-unit (test-utils/game-map-atom) "f")]
          (should-not-be-nil result)
          (should-not= [0 1] (:pos result)))))

    (it "stuck fighter surrounded by friendly units burns fuel and dies"
      ;; 3x3 map: fighter at center [1 1], surrounded by friendly armies on all 8 neighbors
      (set-test-world! (build-test-map ["aaa"
                                               "afa"
                                               "aaa"]))
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 5)
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [unit (get-in (test-utils/read-test-state :game-map) [1 1 :contents])]
        (fighter/process-fighter [1 1] unit)
        ;; Fighter should be dead - fuel burned to 0 while stuck
        (should-be-nil (get-test-unit (test-utils/game-map-atom) "f")))))
