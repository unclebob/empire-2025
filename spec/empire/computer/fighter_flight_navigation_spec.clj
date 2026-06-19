(ns empire.computer.fighter-flight-navigation-spec
  "Tests for VMS Empire style computer fighter movement."
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.computer.fighter :as fighter]
            [empire.computer.fighter.flight-decisions :as flight-decisions]
            [empire.game-mechanics.services.combat :as combat]
            [empire.config.core :as config]
            [empire.test.utils :refer [build-test-map build-sparse-test-map
                                       set-test-unit
                                       get-test-unit reset-all-atoms! set-test-computer-map!
                                       set-test-world! update-test-world!]]))
(describe "process-fighter"
  (before (reset-all-atoms!))

  (context "navigate-toward-target enhancement"
    (it "allows +1 distance sideways jog to unexplored cell"
      ;; Fighter navigating toward target with unexplored cell 1 step off direct path
      ;; With the +1 distance allowance, fighter should prefer the unexplored cell
      (set-test-world! (build-test-map ["#####"
                                               "#f###"
                                               "#####"
                                               "#####"
                                               "####X"]))
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 20
                     :flight-target-site [4 4]
                     :flight-origin-site [0 0]
                     :flight-mode :regular)
      ;; Cell [0 0] is unexplored — it's off the direct path by +1 distance
      (set-test-computer-map! (build-test-map ["-####"
                                                   "#f###"
                                                   "#####"
                                                   "#####"
                                                   "####X"]))
      (let [unit (get-in (test-utils/read-test-state :game-map) [1 1 :contents])]
        (fighter/process-fighter [1 1] unit)
        ;; Fighter should have moved (we just verify it moved, the +1 logic enables
        ;; the unexplored neighbor at [0 0] to be a candidate)
        (let [result (get-test-unit (test-utils/game-map-atom) "f")]
          (should-not-be-nil result)
          (should-not= [1 1] (:pos result)))))

    (it "prefers neighbor with highest unexplored-neighbor count"
      ;; 10-col x 5-row map. Fighter at [2,2], target city at [9,4].
      ;; Row 0 is unexplored; rows 1-4 explored on computer-map.
      ;; No neighbor of [2,2] is itself unexplored, so old code goes direct.
      ;; Row-1 neighbors ([1,1],[2,1],[3,1]) border row 0 (unexplored) so
      ;; count-unexplored-neighbors is high.  Row-3 neighbors have 0 unexplored.
      ;; New code should prefer row-1 neighbors.
      ;; Fuel=30, direct-dist=9 => margin=19, plenty for detour.
      ;; After 8 speed steps, old code ends near [9,4] or has arrived and
      ;; re-launched (final pos varies). New code detours toward row 1 first.
      ;; We check final row < 2 (detoured toward unexplored).
      (set-test-world! (build-test-map ["##########"
                                               "##########"
                                               "##f#######"
                                               "##########"
                                               "#########X"]))
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 30
                     :flight-target-site [9 4]
                     :flight-origin-site [0 0]
                     :flight-mode :regular)
      ;; Row 0 unexplored (nil), rows 1-4 explored
      (set-test-computer-map! (build-test-map ["----------"
                                                   "##########"
                                                   "##f#######"
                                                   "##########"
                                                   "#########X"]))
      (let [unit (get-in (test-utils/read-test-state :game-map) [2 2 :contents])]
        (fighter/process-fighter [2 2] unit)
        ;; Fighter should move toward row 0/1 (near unexplored), not row 3+ (direct)
        (let [result (get-test-unit (test-utils/game-map-atom) "f")]
          (should-not-be-nil result)
          ;; Old code: goes direct toward [9,4] via row 3+. New code: detours to row 1.
          ;; After a full round (8 steps), old code arrives at target and re-launches.
          ;; But with new code, the first step goes toward row 1 (high unexplored score).
          ;; We just need to verify the detour happened. If the fighter ever visited
          ;; row 0 or 1, visibility updates would reveal row-0 cells. Check that.
          (should (some (fn [col] (some? (get-in (test-utils/read-test-state :computer-map) [col 0])))
                        (range 10))))))

    (it "takes direct path when fuel is tight despite unexplored neighbors"
      ;; Fighter at [2,2] heading to city at [4,4]. Fuel = direct-dist + 2 = 6.
      ;; Should NOT detour even though row 0 is unexplored.
      ;; Second city at [0,4] prevents post-arrival patrol toward unexplored area.
      (set-test-world! (build-test-map ["#####"
                                               "#####"
                                               "##f##"
                                               "#####"
                                               "X###X"]))
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 6
                     :flight-target-site [4 4]
                     :flight-origin-site [0 4]
                     :flight-mode :regular)
      (set-test-computer-map! (build-test-map ["-----"
                                                   "#####"
                                                   "##f##"
                                                   "#####"
                                                   "X###X"]))
      (let [unit (get-in (test-utils/read-test-state :game-map) [2 2 :contents])]
        (fighter/process-fighter [2 2] unit)
        ;; With tight fuel, row-0 should remain unexplored (no detour happened)
        (should-not (some (fn [col] (some? (get-in (test-utils/read-test-state :computer-map) [col 0])))
                          (range 5)))))

    (it "falls back to direct navigation when all neighbors fully explored"
      ;; Entire map explored — no unexplored neighbors anywhere.
      ;; Fighter should navigate directly toward target.
      (set-test-world! (build-test-map ["#####"
                                               "#####"
                                               "##f##"
                                               "#####"
                                               "####X"]))
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 30
                     :flight-target-site [4 4]
                     :flight-origin-site [0 0]
                     :flight-mode :regular)
      ;; Everything explored
      (set-test-computer-map! (build-test-map ["#####"
                                                   "#####"
                                                   "##f##"
                                                   "#####"
                                                   "####X"]))
      (let [unit (get-in (test-utils/read-test-state :game-map) [2 2 :contents])]
        (fighter/process-fighter [2 2] unit)
        ;; Fighter should move toward target [4,4] — check it moved
        (let [result (get-test-unit (test-utils/game-map-atom) "f")]
          ;; Fighter may have arrived at city and landed, or may still be on map
          ;; Either way, it should not be stuck at [2,2]
          (should (or (and result (not= [2 2] (:pos result)))
                      ;; Landed at city
                      (pos? (:fighter-count (get-in (test-utils/read-test-state :game-map) [4 4])))))))))

  (context "deterministic combat outcomes"
    (it "attacker wins and moves to enemy position"
      (set-test-world! (build-test-map ["fA"]))
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 20)
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (with-redefs [combat/resolve-combat
                    (fn [atk _def] {:winner :attacker :survivor atk})]
        (let [unit (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
          (fighter/process-fighter [0 0] unit)
          (should= :fighter (get-in (test-utils/read-test-state :game-map) [1 0 :contents :type]))
          (should= :computer (get-in (test-utils/read-test-state :game-map) [1 0 :contents :owner]))
          (should-be-nil (get-in (test-utils/read-test-state :game-map) [0 0 :contents])))))

    (it "attacker loses and is removed from map"
      (set-test-world! (build-test-map ["fA"]))
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 20)
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (with-redefs [combat/resolve-combat
                    (fn [_atk def] {:winner :defender :survivor def})]
        (let [unit (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
          (fighter/process-fighter [0 0] unit)
          (should-be-nil (get-test-unit (test-utils/game-map-atom) "f"))
          (should= :army (get-in (test-utils/read-test-state :game-map) [1 0 :contents :type]))
          (should= :player (get-in (test-utils/read-test-state :game-map) [1 0 :contents :owner]))))))

  (context "fuel boundary precision"
    (it "returns to refuel when fuel exactly equals return distance plus margin"
      ;; distance=8, fuel=10 (= 8+2). should-return: (<= 10 10) = true.
      ;; Mutation <= to < gives (< 10 10) = false → navigates away.
      ;; With 8 steps at fighter-speed, original returns in exactly 8.
      ;; Mutation wastes 1 step navigating away, can't return in remaining 7.
      (set-test-world! (build-test-map ["X#######f##"]))
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 10
                     :flight-mode :regular
                     :flight-target-site [10 0]
                     :flight-origin-site [0 0])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [unit (get-in (test-utils/read-test-state :game-map) [8 0 :contents])]
        (fighter/process-fighter [8 0] unit)
        (should= 1 (:fighter-count (get-in (test-utils/read-test-state :game-map) [0 0])))))

    (it "fighter with fuel 2 survives one consume step to reach city"
      ;; distance=2, fuel=2. Moves one step (fuel 2->1), then lands.
      ;; Mutation 0->1 in consume would kill at fuel=1.
      (set-test-world! (build-test-map ["X#f"]))
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 2)
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [unit (get-in (test-utils/read-test-state :game-map) [2 0 :contents])]
        (fighter/process-fighter [2 0] unit)
        (should= 1 (:fighter-count (get-in (test-utils/read-test-state :game-map) [0 0]))))))

  (context "carrier detection in current-refueling-site"
    (it "detects a holding computer carrier as the current refueling site"
      (set-test-world! (build-test-map ["#####~j~#######X"]))
      (update-test-world! assoc-in [7 0 :contents]
             {:type :carrier :owner :computer :hits 8 :carrier-mode :holding})
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 32)
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (should= [7 0]
               (flight-decisions/current-refueling-site
                (test-utils/read-test-state :game-map)
                [6 0]))))

  (context "choose-leg distance boundary"
    (it "can use a refueling site at exactly fighter-fuel distance as a landing site"
      (let [row-str (str "X" (apply str (repeat 31 \#)) "X")]
        (set-test-world! (build-test-map [row-str]))
        (update-test-world! assoc-in [0 0 :contents]
               {:type :fighter :owner :computer :hits 1 :fuel 32})
        (set-test-computer-map! (test-utils/read-test-state :game-map))
        (with-redefs [rand (fn ([] 0.6) ([_n] 0.6))]
          (let [unit (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
            (fighter/process-fighter [0 0] unit)
            (let [result (get-test-unit (test-utils/game-map-atom) "f")]
              (should-not-be-nil result)
              (should= :explore (:flight-mode (:unit result)))
              (should= [32 0] (:explore-landing-site (:unit result)))))))))

  (context "non-axis distance calculation"
    (it "correctly computes distance when both coordinates differ"
      ;; Fighter at [1,2], city at [0,0]. True distance=3.
      ;; Mutation outer + -> - gives |1|-|2|=-1, changing return decision.
      ;; fuel=4: original (<= 4 5)=true (returns). Mutation (<= 4 1)=false.
      (set-test-world! (build-test-map ["X##"
                                               "###"
                                               "#f#"]))
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 4
                     :flight-mode :regular
                     :flight-target-site [2 2])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [unit (get-in (test-utils/read-test-state :game-map) [1 2 :contents])]
        (fighter/process-fighter [1 2] unit)
        ;; Fighter should return toward city (not navigate to target)
        (should= 1 (:fighter-count (get-in (test-utils/read-test-state :game-map) [0 0])))))

    (it "carrier arrival works when both row indices are nonzero"
      ;; Fighter at [2,1] adjacent to carrier at [2,2]. Both c-components nonzero.
      ;; distance-to [2,1] [2,2] = 1. Mutation (- c1 c2) -> (+ c1 c2) gives 3.
      ;; at-flight-target? checks (<= distance 1). Mutation: (<= 3 1) = false.
      (set-test-world! (build-test-map ["X###"
                                               "##j#"
                                               "##~#"
                                               "####"]))
      (update-test-world! assoc-in [2 2 :contents]
             {:type :carrier :owner :computer :hits 8 :carrier-mode :holding})
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 20
                     :flight-target-site [2 2]
                     :flight-origin-site [0 0]
                     :flight-mode :regular)
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (test-utils/set-test-state! :round-number 10)
      (let [unit (get-in (test-utils/read-test-state :game-map) [2 1 :contents])]
        (fighter/process-fighter [2 1] unit)
        ;; Arrival should record leg
        (should= 10 (:last-flown (get (test-utils/read-test-state :fighter-leg-records) #{[0 0] [2 2]})))))))
