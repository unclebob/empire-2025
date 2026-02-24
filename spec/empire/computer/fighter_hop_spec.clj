(ns empire.computer.fighter-hop-spec
  "Tests for VMS Empire style computer fighter movement."
  (:require [speclj.core :refer :all]
            [empire.computer.fighter :as fighter]
            [empire.atoms :as atoms]
            [empire.combat :as combat]
            [empire.config :as config]
            [empire.test-utils :refer [build-test-map build-sparse-test-map
                                       set-test-unit
                                       get-test-unit reset-all-atoms!]]))

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
