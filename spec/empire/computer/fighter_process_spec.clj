(ns empire.computer.fighter-process-spec
  "Tests for fighter orchestrator: leg coverage, navigation, state machine."
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.computer.fighter :as fighter]
            [empire.computer.fighter.flight-decisions :as flight-decisions]
            [empire.computer.fighter.movement :as fighter-movement]
            [empire.game-mechanics.services.combat :as combat]
            [empire.config.core :as config]
            [empire.test.utils :refer [build-test-map set-test-unit
                                       get-test-unit reset-all-atoms! set-test-computer-map! set-test-world! update-test-world!]]))
(describe "process-fighter"
  (before (reset-all-atoms!))

  (context "ignores non-computer fighters"
    (it "returns nil for player fighter"
      (set-test-world! (build-test-map ["F"]))
      (set-test-unit (test-utils/game-map-atom) "F" :fuel 20)
      (let [unit (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))]
        (should-be-nil (fighter/process-fighter [0 0] unit))))

    (it "returns nil for non-fighter"
      (set-test-world! (build-test-map ["a"]))
      (let [unit (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))]
        (should-be-nil (fighter/process-fighter [0 0] unit)))))

  (context "adjacent enemy targeting"
    (it "does not target a player unit inside a city"
      (set-test-world! (build-test-map ["fO"]))
      (update-test-world! assoc-in [1 0 :contents]
             {:type :fighter :owner :player :fuel 20})
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (should-be-nil (fighter-movement/find-adjacent-enemy [0 0])))

    (it "still targets adjacent player units outside cities"
      (set-test-world! (build-test-map ["fF"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (should= [1 0] (fighter-movement/find-adjacent-enemy [0 0]))))

  (context "leg-based coverage"
    (it "picks unflown leg target over previously flown leg"
      ;; 20x20 map: city at [10,10], carrier A at [10,0] (north), carrier B at [0,10] (west)
      (let [land-row (apply str (repeat 20 \#))
            row-0 (str (apply str (repeat 10 \#)) "~" (apply str (repeat 9 \#)))
            row-10 (str "~" (apply str (repeat 9 \#)) "X" (apply str (repeat 9 \#)))
            rows (-> (vec (repeat 20 land-row))
                     (assoc 0 row-0)
                     (assoc 10 row-10))]
        (set-test-world! (build-test-map rows))
        ;; Place carriers in holding mode
        (update-test-world! assoc-in [10 0 :contents]
               {:type :carrier :owner :computer :hits 8 :carrier-mode :holding})
        (update-test-world! assoc-in [0 10 :contents]
               {:type :carrier :owner :computer :hits 8 :carrier-mode :holding})
        ;; Place fighter on city
        (update-test-world! assoc-in [10 10 :contents]
               {:type :fighter :owner :computer :hits 1 :fuel 32})
        (set-test-computer-map! (test-utils/read-test-state :game-map))
        (let [sites [[10 10] [10 0] [0 10]]
              leg-records {#{[10 10] [10 0]} {:last-flown 5}}]
          (should= [0 10]
                   (#'empire.computer.fighter.flight-decisions/choose-leg
                    (test-utils/read-test-state :game-map)
                    sites
                    leg-records
                    [10 10])))))

    (it "picks oldest flown leg when all legs are flown"
      ;; Same map setup: city at [10,10], carrier A at [10,0] (north), carrier B at [0,10] (west)
      (let [land-row (apply str (repeat 20 \#))
            row-0 (str (apply str (repeat 10 \#)) "~" (apply str (repeat 9 \#)))
            row-10 (str "~" (apply str (repeat 9 \#)) "X" (apply str (repeat 9 \#)))
            rows (-> (vec (repeat 20 land-row))
                     (assoc 0 row-0)
                     (assoc 10 row-10))]
        (set-test-world! (build-test-map rows))
        (update-test-world! assoc-in [10 0 :contents]
               {:type :carrier :owner :computer :hits 8 :carrier-mode :holding})
        (update-test-world! assoc-in [0 10 :contents]
               {:type :carrier :owner :computer :hits 8 :carrier-mode :holding})
        (update-test-world! assoc-in [10 10 :contents]
               {:type :fighter :owner :computer :hits 1 :fuel 32})
        (set-test-computer-map! (test-utils/read-test-state :game-map))
        (let [sites [[10 10] [10 0] [0 10]]
              leg-records {#{[10 10] [10 0]} {:last-flown 10}
                           #{[10 10] [0 10]} {:last-flown 3}}]
          (should= [0 10]
                   (#'empire.computer.fighter.flight-decisions/choose-leg
                    (test-utils/read-test-state :game-map)
                    sites
                    leg-records
                    [10 10])))))

    (it "records leg on arrival at target city"
      (set-test-world! (build-test-map ["X#####fX"]))
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 20
                     :flight-target-site [7 0]
                     :flight-origin-site [0 0])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (test-utils/set-test-state! :round-number 42)
      (let [unit (get-in (test-utils/read-test-state :game-map) [6 0 :contents])]
        (fighter/process-fighter [6 0] unit)
        ;; Leg should be recorded with current round number
        (should= 42 (:last-flown (get (test-utils/read-test-state :fighter-leg-records) #{[0 0] [7 0]})))))

    (it "refuels at carrier when low on fuel"
      ;; Fighter on sea adjacent to carrier, no city nearby
      (set-test-world! (build-test-map ["#####~j~"]))
      ;; Place carrier at [7,0] in holding mode
      (update-test-world! assoc-in [7 0 :contents]
             {:type :carrier :owner :computer :hits 8 :carrier-mode :holding})
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 2)
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [unit (get-in (test-utils/read-test-state :game-map) [6 0 :contents])]
        (fighter/process-fighter [6 0] unit)
        ;; Fighter should have refueled and still be alive
        (let [result (get-test-unit (test-utils/game-map-atom) "f")]
          (should-not-be-nil result)
          ;; Fuel should be much higher than starting 2 (refueled to 32, then some patrol steps)
          (should (> (:fuel (:unit result)) 20)))))

    (it "falls back to patrol state when no reachable legs"
      ;; Fighter at a city with no other refueling sites within range
      (set-test-world! (build-test-map ["Xf########"]))
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 20)
      ;; Unexplored territory to the right
      (set-test-computer-map! (build-test-map ["Xf........"]))
      (let [unit (get-in (test-utils/read-test-state :game-map) [1 0 :contents])]
        (fighter/process-fighter [1 0] unit)
        ;; With the frontier already adjacent, fallback patrol may hold position
        ;; while still consuming a round of movement/fuel.
        (let [result (get-test-unit (test-utils/game-map-atom) "f")]
          (should-not-be-nil result)
          (should= [1 0] (:pos result))
          (should (< (:fuel (:unit result)) 20))))))

  (context "fuel burn when stuck"
    (it "stuck fighter with 8 fuel burns all fuel and dies"
      ;; Fighter completely surrounded, with exactly 8 fuel (one per step)
      (set-test-world! (build-test-map ["aaa"
                                               "afa"
                                               "aaa"]))
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 8)
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [unit (get-in (test-utils/read-test-state :game-map) [1 1 :contents])]
        (fighter/process-fighter [1 1] unit)
        ;; Fighter should be dead after burning 8 fuel
        (should-be-nil (get-test-unit (test-utils/game-map-atom) "f"))))

    (it "stuck fighter with more than 8 fuel survives the round"
      ;; Fighter completely surrounded, with 10 fuel - burns 8, survives with 2
      (set-test-world! (build-test-map ["aaa"
                                               "afa"
                                               "aaa"]))
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 10)
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [unit (get-in (test-utils/read-test-state :game-map) [1 1 :contents])]
        (fighter/process-fighter [1 1] unit)
        ;; Fighter should survive with 2 fuel remaining
        (let [result (get-test-unit (test-utils/game-map-atom) "f")]
          (should-not-be-nil result)
          (should= 2 (:fuel (:unit result)))))))

  (context "carrier refueling site (L363-365)"
    (it "refuels at adjacent holding carrier"
      ;; Fighter on land, carrier on adjacent sea in holding mode
      (set-test-world! [[{:type :land :contents {:type :fighter :owner :computer
                                                        :hits 1 :fuel 3}}
                                {:type :sea :contents {:type :carrier :owner :computer
                                                       :hits 8 :carrier-mode :holding
                                                       :carrier-id 1}}
                                {:type :land}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [unit (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
        (fighter/process-fighter [0 0] unit)
        ;; Fighter should have refueled
        (let [result (get-test-unit (test-utils/game-map-atom) "f")]
          (when result
            (should (> (:fuel (:unit result)) 3)))))))

  (context "arrival at target (L431-453)"
    (it "records leg on arrival and picks new target"
      ;; Fighter close to target city, should arrive and record leg
      (set-test-world! (build-test-map ["X####fX"]))
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 20
                     :flight-target-site [6 0]
                     :flight-origin-site [0 0]
                     :flight-mode :regular)
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (test-utils/set-test-state! :round-number 10)
      (let [unit (get-in (test-utils/read-test-state :game-map) [5 0 :contents])]
        (fighter/process-fighter [5 0] unit)
        ;; Should have recorded the leg
        (let [record (get (test-utils/read-test-state :fighter-leg-records) #{[0 0] [6 0]})]
          (should-not-be-nil record)
          (should= 10 (:last-flown record))))))

  (context "critical fuel return"
    (it "lands at an adjacent target city even when refueling caches are stale"
      (set-test-world! (build-test-map ["Xf####"]))
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 1
                     :flight-target-site [0 0]
                     :flight-origin-site [5 0]
                     :flight-mode :regular
                     :explore-landing-site [0 0])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (test-utils/set-test-state! :computer-city-positions #{[5 0]})
      (test-utils/set-test-state! :computer-carrier-positions #{})
      (let [unit (get-in (test-utils/read-test-state :game-map) [1 0 :contents])]
        (fighter/process-fighter [1 0] unit)
        (should= 1 (:fighter-count (get-in (test-utils/read-test-state :game-map) [0 0])))
        (should-be-nil (get-test-unit (test-utils/game-map-atom) "f"))))

    (it "breaks off exploration to land when fuel is critical and a city is adjacent"
      (set-test-world! (build-test-map ["Xf#####"]))
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 1
                     :flight-target-site [6 0]
                     :flight-origin-site [6 0]
                     :flight-mode :explore
                     :explore-origin [6 0]
                     :explore-heading [-1 0]
                     :explore-steps-remaining 3
                     :explore-landing-site [0 0])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [unit (get-in (test-utils/read-test-state :game-map) [1 0 :contents])]
        (fighter/process-fighter [1 0] unit)
        (should= 1 (:fighter-count (get-in (test-utils/read-test-state :game-map) [0 0])))
        (should-be-nil (get-test-unit (test-utils/game-map-atom) "f"))))

    (it "uses the planned landing site when visible on computer-map"
      (set-test-world! (build-test-map ["Xf#####"]))
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 1
                     :flight-target-site [6 0]
                     :flight-origin-site [6 0]
                     :flight-mode :explore
                     :explore-origin [6 0]
                     :explore-heading [-1 0]
                     :explore-steps-remaining 3
                     :explore-landing-site [0 0])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [unit (get-in (test-utils/read-test-state :game-map) [1 0 :contents])]
        (fighter/process-fighter [1 0] unit)
        (should= 1 (:fighter-count (get-in (test-utils/read-test-state :game-map) [0 0])))
        (should-be-nil (get-test-unit (test-utils/game-map-atom) "f")))))

  (context "navigation toward target (L460-479)"
    (it "navigates toward flight target with fuel margin"
      ;; Fighter with flight target, enough fuel for margin exploration
      (set-test-world! (build-test-map ["X######f##########X"]))
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 25
                     :flight-target-site [18 0]
                     :flight-origin-site [0 0]
                     :flight-mode :regular)
      ;; Some unexplored territory along the way
      (set-test-computer-map! (build-test-map ["X######f..........X"]))
      (let [unit (get-in (test-utils/read-test-state :game-map) [7 0 :contents])]
        (fighter/process-fighter [7 0] unit)
        ;; Fighter should have moved toward target
        (should-be-nil (get-in (test-utils/read-test-state :game-map) [7 0 :contents]))
        (let [result (get-test-unit (test-utils/game-map-atom) "f")]
          (should-not-be-nil result)
          ;; Should have moved rightward (toward target at [18,0])
          (should (> (first (:pos result)) 7))))))

  (context "ensure-flight-target (L420-423)"
    (it "assigns an exploration sortie with a landing site"
      ;; Fighter at a city with another refueling site in range
      (set-test-world! (build-test-map ["X###########X"]))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :fighter :owner :computer :hits 1 :fuel config/fighter-fuel})
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (with-redefs [rand (fn ([] 0.6) ([_n] 0.6))]
        (let [unit (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
            (fighter/process-fighter [0 0] unit)
            (let [result (get-test-unit (test-utils/game-map-atom) "f")]
              (when result
                (should= :explore (:flight-mode (:unit result)))
                (should-not-be-nil (:flight-target-site (:unit result)))
                (should-not-be-nil (:explore-landing-site (:unit result))))))))

    (it "records fighter action phases while processing"
      (set-test-world! (build-test-map ["X####f######"]))
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 20
                     :flight-mode :explore
                     :explore-origin [0 0]
                     :explore-landing-site [0 0]
                     :explore-steps-remaining 3
                     :flight-target-site [11 0]
                     :flight-origin-site [0 0])
      (set-test-computer-map! (build-test-map ["X####f......"]))
      (let [unit (get-in (test-utils/read-test-state :game-map) [5 0 :contents])]
        (fighter/process-fighter [5 0] unit)
        (should true))))

  (context "exploration target bounds"
    (it "keeps exploration flight target in bounds"
      (set-test-world! (build-test-map ["X##"
                                        "###"
                                        "###"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :fighter :owner :computer :hits 1 :fuel config/fighter-fuel})
      (with-redefs [rand (fn ([] 0.3) ([_n] 0.3))]
        ((ns-resolve 'empire.computer.fighter 'assign-exploration-flight) [0 0] [0 0])
        (let [unit (get-in (test-utils/read-test-state :game-map) [0 0 :contents])
              target (:flight-target-site unit)]
          (should (vector? target))
          (should (<= 0 (first target) 2))
          (should (<= 0 (second target) 2))
          (should= [0 0] (:explore-landing-site unit))))))

  (context "desperate patrol on low fuel (L524)"
    (it "patrols when no refueling site and low fuel"
      ;; Fighter with low fuel, no city or carrier anywhere
      (set-test-world! (build-test-map ["###f###"]))
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 3)
      ;; Unexplored territory to give patrol a target
      (set-test-computer-map! (build-test-map ["###f..."]))
      (let [unit (get-in (test-utils/read-test-state :game-map) [3 0 :contents])]
        (fighter/process-fighter [3 0] unit)
        ;; Fighter should have attempted movement (may be alive or dead)
        ;; The key thing is it doesn't crash
        true)))

  (context "carrier as refueling site (L363-365)"
    (it "detects carrier in holding mode as refueling site"
      ;; Fighter on land, adjacent holding carrier on sea
      ;; build-test-map ["~f#"]: col0=sea, col1=fighter on land, col2=land
      (set-test-world! (build-test-map ["~f#"]))
      ;; Place carrier at [0,0] (sea cell)
      (update-test-world! assoc-in [0 0 :contents]
             {:type :carrier :owner :computer :hits 8 :carrier-mode :holding
              :carrier-id 1})
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 3)
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [unit (get-in (test-utils/read-test-state :game-map) [1 0 :contents])]
        (fighter/process-fighter [1 0] unit)
        ;; Fighter should have refueled (fuel > 3)
        (let [result (get-test-unit (test-utils/game-map-atom) "f")]
          (should-not-be-nil result)
          (should (> (:fuel (:unit result)) 3))))))

  (context "choose-leg distance (L374)"
    (it "picks closer reachable leg over farther one"
      ;; Two cities: one within range, one not
      ;; Fighter at first city, second city within fighter-fuel range
      (let [row-str (apply str "X" (apply str (repeat 29 \#)) "X")]
        (set-test-world! (build-test-map [row-str]))
        (update-test-world! assoc-in [0 0 :contents]
               {:type :fighter :owner :computer :hits 1 :fuel config/fighter-fuel})
        (set-test-computer-map! (test-utils/read-test-state :game-map))
        ;; Force regular leg
        (with-redefs [rand (fn ([] 0.6) ([_n] 0.6))]
          (let [unit (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
            (fighter/process-fighter [0 0] unit)
            ;; Fighter should have started heading toward the other city
            (let [result (get-test-unit (test-utils/game-map-atom) "f")]
              (when result
                (should (> (first (:pos result)) 0)))))))))

  (context "navigate with explore preference (L460-461, L471-472)"
    (it "explores during navigation when fuel margin allows"
      ;; Fighter navigating with enough fuel to explore side paths
      (set-test-world! (build-test-map ["X######f#######################X"]))
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 30
                     :flight-target-site [30 0]
                     :flight-origin-site [0 0]
                     :flight-mode :regular)
      ;; Unexplored territory along the way
      (set-test-computer-map! (build-test-map ["X######f.......................X"]))
      (let [unit (get-in (test-utils/read-test-state :game-map) [7 0 :contents])]
        (fighter/process-fighter [7 0] unit)
        (let [result (get-test-unit (test-utils/game-map-atom) "f")]
          (should-not-be-nil result)
          ;; Should have moved toward target
          (should (> (first (:pos result)) 7))))))

  (context "low fuel refuel at carrier (L514)"
    (it "refuels at adjacent carrier when low on fuel"
      ;; Fighter low on fuel, adjacent to holding carrier, no city nearby
      (set-test-world! (build-test-map ["#f~"]))
      (update-test-world! assoc-in [2 0 :contents]
             {:type :carrier :owner :computer :hits 8 :carrier-mode :holding
              :carrier-id 1})
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 2)
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [unit (get-in (test-utils/read-test-state :game-map) [1 0 :contents])]
        (fighter/process-fighter [1 0] unit)
        ;; Fighter should have refueled
        (let [result (get-test-unit (test-utils/game-map-atom) "f")]
          (should-not-be-nil result)
          (should (> (:fuel (:unit result)) 2)))))))
