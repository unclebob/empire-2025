(ns empire.computer.fighter-flight-spec
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
