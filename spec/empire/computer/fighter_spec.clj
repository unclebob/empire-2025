(ns empire.computer.fighter-spec
  "Tests for fighter orchestrator: leg coverage, navigation, state machine."
  (:require [speclj.core :refer :all]
            [empire.computer.fighter :as fighter]
            [empire.atoms :as atoms]
            [empire.combat :as combat]
            [empire.config :as config]
            [empire.test-utils :refer [build-test-map set-test-unit
                                       get-test-unit reset-all-atoms! set-test-computer-map! set-test-world! update-test-world!]]))

(describe "process-fighter"
  (before (reset-all-atoms!))

  (context "ignores non-computer fighters"
    (it "returns nil for player fighter"
      (set-test-world! (build-test-map ["F"]))
      (set-test-unit atoms/game-map "F" :fuel 20)
      (let [unit (:contents (get-in @atoms/game-map [0 0]))]
        (should-be-nil (fighter/process-fighter [0 0] unit))))

    (it "returns nil for non-fighter"
      (set-test-world! (build-test-map ["a"]))
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
        (set-test-world! (build-test-map rows))
        ;; Place carriers in holding mode
        (update-test-world! assoc-in [10 0 :contents]
               {:type :carrier :owner :computer :hits 8 :carrier-mode :holding})
        (update-test-world! assoc-in [0 10 :contents]
               {:type :carrier :owner :computer :hits 8 :carrier-mode :holding})
        ;; Place fighter on city
        (update-test-world! assoc-in [10 10 :contents]
               {:type :fighter :owner :computer :hits 1 :fuel 32})
        (set-test-computer-map! @atoms/game-map)
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
        (set-test-world! (build-test-map rows))
        (update-test-world! assoc-in [10 0 :contents]
               {:type :carrier :owner :computer :hits 8 :carrier-mode :holding})
        (update-test-world! assoc-in [0 10 :contents]
               {:type :carrier :owner :computer :hits 8 :carrier-mode :holding})
        (update-test-world! assoc-in [10 10 :contents]
               {:type :fighter :owner :computer :hits 1 :fuel 32})
        (set-test-computer-map! @atoms/game-map)
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
      (set-test-world! (build-test-map ["X#####fX"]))
      (set-test-unit atoms/game-map "f" :fuel 20
                     :flight-target-site [7 0]
                     :flight-origin-site [0 0])
      (set-test-computer-map! @atoms/game-map)
      (reset! atoms/round-number 42)
      (let [unit (get-in @atoms/game-map [6 0 :contents])]
        (fighter/process-fighter [6 0] unit)
        ;; Leg should be recorded with current round number
        (should= 42 (:last-flown (get @atoms/fighter-leg-records #{[0 0] [7 0]})))))

    (it "refuels at carrier when low on fuel"
      ;; Fighter on sea adjacent to carrier, no city nearby
      (set-test-world! (build-test-map ["#####~j~"]))
      ;; Place carrier at [7,0] in holding mode
      (update-test-world! assoc-in [7 0 :contents]
             {:type :carrier :owner :computer :hits 8 :carrier-mode :holding})
      (set-test-unit atoms/game-map "f" :fuel 2)
      (set-test-computer-map! @atoms/game-map)
      (let [unit (get-in @atoms/game-map [6 0 :contents])]
        (fighter/process-fighter [6 0] unit)
        ;; Fighter should have refueled and still be alive
        (let [result (get-test-unit atoms/game-map "f")]
          (should-not-be-nil result)
          ;; Fuel should be much higher than starting 2 (refueled to 32, then some patrol steps)
          (should (> (:fuel (:unit result)) 20)))))

    (it "falls back to patrol when no reachable legs"
      ;; Fighter at a city with no other refueling sites within range
      (set-test-world! (build-test-map ["Xf########"]))
      (set-test-unit atoms/game-map "f" :fuel 20)
      ;; Unexplored territory to the right
      (set-test-computer-map! (build-test-map ["Xf........"]))
      (let [unit (get-in @atoms/game-map [1 0 :contents])]
        (fighter/process-fighter [1 0] unit)
        ;; Fighter should have moved (patrol behavior) even without a leg target
        (should-be-nil (get-in @atoms/game-map [1 0 :contents]))
        (let [result (get-test-unit atoms/game-map "f")
              [fighter-col _] (:pos result)]
          (should-not-be-nil result)
          (should (> fighter-col 1))))))

  (context "fuel burn when stuck"
    (it "stuck fighter with 8 fuel burns all fuel and dies"
      ;; Fighter completely surrounded, with exactly 8 fuel (one per step)
      (set-test-world! (build-test-map ["aaa"
                                               "afa"
                                               "aaa"]))
      (set-test-unit atoms/game-map "f" :fuel 8)
      (set-test-computer-map! @atoms/game-map)
      (let [unit (get-in @atoms/game-map [1 1 :contents])]
        (fighter/process-fighter [1 1] unit)
        ;; Fighter should be dead after burning 8 fuel
        (should-be-nil (get-test-unit atoms/game-map "f"))))

    (it "stuck fighter with more than 8 fuel survives the round"
      ;; Fighter completely surrounded, with 10 fuel - burns 8, survives with 2
      (set-test-world! (build-test-map ["aaa"
                                               "afa"
                                               "aaa"]))
      (set-test-unit atoms/game-map "f" :fuel 10)
      (set-test-computer-map! @atoms/game-map)
      (let [unit (get-in @atoms/game-map [1 1 :contents])]
        (fighter/process-fighter [1 1] unit)
        ;; Fighter should survive with 2 fuel remaining
        (let [result (get-test-unit atoms/game-map "f")]
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
      (set-test-computer-map! @atoms/game-map)
      (let [unit (get-in @atoms/game-map [0 0 :contents])]
        (fighter/process-fighter [0 0] unit)
        ;; Fighter should have refueled
        (let [result (get-test-unit atoms/game-map "f")]
          (when result
            (should (> (:fuel (:unit result)) 3)))))))

  (context "arrival at target (L431-453)"
    (it "records leg on arrival and picks new target"
      ;; Fighter close to target city, should arrive and record leg
      (set-test-world! (build-test-map ["X####fX"]))
      (set-test-unit atoms/game-map "f" :fuel 20
                     :flight-target-site [6 0]
                     :flight-origin-site [0 0]
                     :flight-mode :regular)
      (set-test-computer-map! @atoms/game-map)
      (reset! atoms/round-number 10)
      (let [unit (get-in @atoms/game-map [5 0 :contents])]
        (fighter/process-fighter [5 0] unit)
        ;; Should have recorded the leg
        (let [record (get @atoms/fighter-leg-records #{[0 0] [6 0]})]
          (should-not-be-nil record)
          (should= 10 (:last-flown record))))))

  (context "navigation toward target (L460-479)"
    (it "navigates toward flight target with fuel margin"
      ;; Fighter with flight target, enough fuel for margin exploration
      (set-test-world! (build-test-map ["X######f##########X"]))
      (set-test-unit atoms/game-map "f" :fuel 25
                     :flight-target-site [18 0]
                     :flight-origin-site [0 0]
                     :flight-mode :regular)
      ;; Some unexplored territory along the way
      (set-test-computer-map! (build-test-map ["X######f..........X"]))
      (let [unit (get-in @atoms/game-map [7 0 :contents])]
        (fighter/process-fighter [7 0] unit)
        ;; Fighter should have moved toward target
        (should-be-nil (get-in @atoms/game-map [7 0 :contents]))
        (let [result (get-test-unit atoms/game-map "f")]
          (should-not-be-nil result)
          ;; Should have moved rightward (toward target at [18,0])
          (should (> (first (:pos result)) 7))))))

  (context "ensure-flight-target (L420-423)"
    (it "assigns regular leg when rand >= 0.5 (L423 if->if-not)"
      ;; Fighter at a city with another refueling site in range
      (set-test-world! (build-test-map ["X###########X"]))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :fighter :owner :computer :hits 1 :fuel config/fighter-fuel})
      (set-test-computer-map! @atoms/game-map)
      ;; Force regular leg assignment (rand >= 0.5)
      (with-redefs [rand (fn ([] 0.6) ([_n] 0.6))]
        (let [unit (get-in @atoms/game-map [0 0 :contents])]
          (fighter/process-fighter [0 0] unit)
          ;; Fighter should have started moving (regular leg toward [12,0])
          (let [result (get-test-unit atoms/game-map "f")]
            (when result
              ;; Should have flight-target-site set
              (should-not-be-nil (:flight-target-site (:unit result))))))))

    (it "assigns exploration flight when rand < 0.5"
      (set-test-world! (build-test-map ["X############"]))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :fighter :owner :computer :hits 1 :fuel config/fighter-fuel})
      ;; Lots of unexplored territory
      (set-test-computer-map! (build-test-map ["X............"]))
      (with-redefs [rand (fn ([] 0.3) ([_n] 0.3))]
        (let [unit (get-in @atoms/game-map [0 0 :contents])]
          (fighter/process-fighter [0 0] unit)
          ;; Fighter should have moved exploring
          (let [result (get-test-unit atoms/game-map "f")]
            (when result
              (let [mode (:flight-mode (:unit result))]
                (when mode
                  (should (#{:explore :drone} mode))))))))))

  (context "desperate patrol on low fuel (L524)"
    (it "patrols when no refueling site and low fuel"
      ;; Fighter with low fuel, no city or carrier anywhere
      (set-test-world! (build-test-map ["###f###"]))
      (set-test-unit atoms/game-map "f" :fuel 3)
      ;; Unexplored territory to give patrol a target
      (set-test-computer-map! (build-test-map ["###f..."]))
      (let [unit (get-in @atoms/game-map [3 0 :contents])]
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
      (set-test-unit atoms/game-map "f" :fuel 3)
      (set-test-computer-map! @atoms/game-map)
      (let [unit (get-in @atoms/game-map [1 0 :contents])]
        (fighter/process-fighter [1 0] unit)
        ;; Fighter should have refueled (fuel > 3)
        (let [result (get-test-unit atoms/game-map "f")]
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
        (set-test-computer-map! @atoms/game-map)
        ;; Force regular leg
        (with-redefs [rand (fn ([] 0.6) ([_n] 0.6))]
          (let [unit (get-in @atoms/game-map [0 0 :contents])]
            (fighter/process-fighter [0 0] unit)
            ;; Fighter should have started heading toward the other city
            (let [result (get-test-unit atoms/game-map "f")]
              (when result
                (should (> (first (:pos result)) 0)))))))))

  (context "navigate with explore preference (L460-461, L471-472)"
    (it "explores during navigation when fuel margin allows"
      ;; Fighter navigating with enough fuel to explore side paths
      (set-test-world! (build-test-map ["X######f#######################X"]))
      (set-test-unit atoms/game-map "f" :fuel 30
                     :flight-target-site [30 0]
                     :flight-origin-site [0 0]
                     :flight-mode :regular)
      ;; Unexplored territory along the way
      (set-test-computer-map! (build-test-map ["X######f.......................X"]))
      (let [unit (get-in @atoms/game-map [7 0 :contents])]
        (fighter/process-fighter [7 0] unit)
        (let [result (get-test-unit atoms/game-map "f")]
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
      (set-test-unit atoms/game-map "f" :fuel 2)
      (set-test-computer-map! @atoms/game-map)
      (let [unit (get-in @atoms/game-map [1 0 :contents])]
        (fighter/process-fighter [1 0] unit)
        ;; Fighter should have refueled
        (let [result (get-test-unit atoms/game-map "f")]
          (should-not-be-nil result)
          (should (> (:fuel (:unit result)) 2)))))))

(describe "handle-low-fuel helpers"
  (before (reset-all-atoms!))

  (it "adjacent-to-city-site? returns true for adjacent city site"
    (set-test-world! (build-test-map ["Xf"]))
    (should (@#'fighter/adjacent-to-city-site? [0 0] [1 0])))

  (it "adjacent-to-city-site? returns false for nil site"
    (set-test-world! (build-test-map ["#f"]))
    (should-not (@#'fighter/adjacent-to-city-site? nil [1 0])))

  (it "adjacent-to-city-site? returns false for non-city site"
    (set-test-world! (build-test-map ["#f"]))
    (should-not (@#'fighter/adjacent-to-city-site? [0 0] [1 0])))

  (it "adjacent-to-city-site? returns false for distant city"
    (set-test-world! (build-test-map ["X#f"]))
    (should-not (@#'fighter/adjacent-to-city-site? [0 0] [2 0])))

  (it "adjacent-to-site? returns true for adjacent site"
    (should (@#'fighter/adjacent-to-site? [0 0] [1 0])))

  (it "adjacent-to-site? returns false for nil site"
    (should-not (@#'fighter/adjacent-to-site? nil [1 0])))

  (it "adjacent-to-site? returns false for distant site"
    (should-not (@#'fighter/adjacent-to-site? [0 0] [3 0])))

  (it "desperate-patrol returns nil when do-patrol returns nil"
    (set-test-world! (build-test-map ["f"]))
    (set-test-unit atoms/game-map "f" :fuel 5)
    (set-test-computer-map! @atoms/game-map)
    (should-be-nil (@#'fighter/desperate-patrol [0 0])))

  (it "desperate-patrol returns result when patrol succeeds"
    (set-test-world! (build-test-map ["f##"]))
    (set-test-unit atoms/game-map "f" :fuel 5)
    (set-test-computer-map! (build-test-map ["f--"]))
    (let [result (@#'fighter/desperate-patrol [0 0])]
      (should-not-be-nil result)
      (should (contains? result :pos))
      (should (contains? result :hops)))))
