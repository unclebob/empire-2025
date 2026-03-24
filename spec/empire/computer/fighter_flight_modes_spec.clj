(ns empire.computer.fighter-flight-modes-spec
  "Tests for VMS Empire style computer fighter movement."
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.computer.fighter :as fighter]
            [empire.game-mechanics.services.combat :as combat]
            [empire.config.core :as config]
            [empire.test.utils :refer [build-test-map build-sparse-test-map
                                       set-test-unit
                                       get-test-unit reset-all-atoms! set-test-computer-map!
                                       update-test-computer-map!
                                       set-test-world! update-test-world!]]))
(describe "process-fighter"
  (before (reset-all-atoms!))

  (context "flight mode selection"
    (it "assigns an exploration sortie when fighter has no flight plan"
      (set-test-world! (build-test-map ["X################X"]))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :fighter :owner :computer :hits 1 :fuel 32})
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (with-redefs [rand (fn
                           ([] 0.6)
                           ([_n] 0.6))]
        (let [unit (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
          (fighter/process-fighter [0 0] unit)
          (let [result (get-test-unit (test-utils/game-map-atom) "f")]
            (should-not-be-nil result)
            (should= :explore (:flight-mode (:unit result)))
            (should-not-be-nil (:explore-landing-site (:unit result)))
            (should (pos? (:explore-steps-remaining (:unit result))))))))

    (it "assigns exploration sortie regardless of the extra random roll"
      (set-test-world! (build-test-map ["X################X"]))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :fighter :owner :computer :hits 1 :fuel 32})
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [rolls (atom [0.6 0.1])]
        (with-redefs [rand (fn
                             ([] (let [v (first @rolls)] (swap! rolls rest) v))
                             ([_n] (let [v (first @rolls)] (swap! rolls rest) v)))
                      rand-nth first]
          (let [unit (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
            (fighter/process-fighter [0 0] unit)
            (let [result (get-test-unit (test-utils/game-map-atom) "f")]
              (should-not-be-nil result)
              (should= :explore (:flight-mode (:unit result)))
              (should-not-be-nil (:explore-origin (:unit result)))
              (should-not-be-nil (:explore-heading (:unit result)))
              (should-not-be-nil (:explore-landing-site (:unit result)))
              (should (pos? (:explore-steps-remaining (:unit result)))))))))

    (it "never assigns the drone label"
      (set-test-world! (build-test-map ["X################X"]))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :fighter :owner :computer :hits 1 :fuel 32})
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [rolls (atom [0.6 0.02])]
        (with-redefs [rand (fn
                             ([] (let [v (first @rolls)] (swap! rolls rest) v))
                             ([_n] (let [v (first @rolls)] (swap! rolls rest) v)))
                      rand-nth first]
          (let [unit (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
            (fighter/process-fighter [0 0] unit)
            (let [result (get-test-unit (test-utils/game-map-atom) "f")]
              (should-not-be-nil result)
              (should= :explore (:flight-mode (:unit result)))
              (should-not-be-nil (:explore-landing-site (:unit result))))))))

    (it "does not re-roll when fighter already has flight-mode"
      ;; Fighter already has :flight-mode :regular - ensure-flight-target should not reassign
      (set-test-world! (build-test-map ["X################X"]))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :fighter :owner :computer :hits 1 :fuel 32
              :flight-mode :regular :flight-target-site [17 0]
              :flight-origin-site [0 0]})
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [unit (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
        (fighter/process-fighter [0 0] unit)
        ;; Fighter should still have :flight-mode :regular (not reassigned)
        (let [result (get-test-unit (test-utils/game-map-atom) "f")]
          (should-not-be-nil result)
          (should= :regular (:flight-mode (:unit result))))))

    (it "hops toward the best exploration staging city before launching a sortie"
      (set-test-world! (build-test-map ["X###################X###################X########"]))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :fighter :owner :computer :hits 1 :fuel 32})
      (set-test-computer-map! (build-test-map ["X###################X###################X--------"]))
      (update-test-computer-map! assoc-in [0 0 :contents]
                                {:type :fighter :owner :computer :hits 1 :fuel 32})
      (with-redefs [rand (fn
                           ([] 0.6)
                           ([_n] 0.6))]
        (let [unit (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
          (fighter/process-fighter [0 0] unit)
          (let [result (get-test-unit (test-utils/game-map-atom) "f")]
            (should-not-be-nil result)
            (should= :regular (:flight-mode (:unit result)))
            (should= [20 0] (:flight-target-site (:unit result)))
            (should= [8 0] (:pos result)))))))

  (context "exploration heading"
    (it "picks direction with most unexplored cells"
      ;; 5x5 map: all explored except east side (columns 3-4 unexplored)
      ;; Fighter at [2 2] on city. Heading should favor east.
      (set-test-world! (build-test-map ["###--"
                                               "###--"
                                               "##X--"
                                               "###--"
                                               "###--"]))
      (update-test-world! assoc-in [2 2 :contents]
             {:type :fighter :owner :computer :hits 1 :fuel 32})
      ;; Computer map: west explored, east unexplored
      (set-test-computer-map! (build-test-map ["###--"
                                                   "###--"
                                                   "##X--"
                                                   "###--"
                                                   "###--"]))
      (let [rolls (atom [0.1])]
        (with-redefs [rand (fn
                             ([] (let [v (first @rolls)] (swap! rolls rest) v))
                             ([_n] (let [v (first @rolls)] (swap! rolls rest) v)))
                      rand-nth first]
          ((ns-resolve 'empire.computer.fighter 'assign-exploration-flight) [2 2] [2 2])
          ;; Fighter should have heading pointing east (dc > 0)
          (let [result (get-in (test-utils/read-test-state :game-map) [2 2 :contents])]
            (should-not-be-nil result)
            (should (pos? (first (:explore-heading result))))))))))

  (context "exploration sortie movement"
    (it "sortie flies outbound with steps-remaining decreasing"
      ;; Fighter mid-sortie, 10 steps remaining, heading east on wide map
      (set-test-world! (build-test-map ["X#f################"]))
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 20
                     :flight-mode :explore
                     :explore-origin [0 0]
                     :explore-landing-site [0 0]
                     :explore-heading [0 1]
                     :explore-steps-remaining 10
                     :flight-target-site [18 0])
      ;; Unexplored territory east
      (set-test-computer-map! (build-test-map ["X#f................"]))
      (let [unit (get-in (test-utils/read-test-state :game-map) [2 0 :contents])]
        (fighter/process-fighter [2 0] unit)
        ;; Fighter should have moved east and steps-remaining should be less than 10
        (let [result (get-test-unit (test-utils/game-map-atom) "f")
              [fc _] (:pos result)]
          (should-not-be-nil result)
          (should (> fc 2))
          (should (< (:explore-steps-remaining (:unit result)) 10)))))

    (it "switches to return mode after outbound steps exhausted"
      ;; Fighter with 1 step remaining, heading east. Origin far away so arrival
      ;; doesn't happen during the same round (8 steps total).
      (set-test-world! (build-test-map ["X#########f##############"]))
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 20
                     :flight-mode :explore
                     :explore-origin [0 0]
                     :explore-landing-site [0 0]
                     :explore-heading [0 1]
                     :explore-steps-remaining 1
                     :flight-target-site [24 0])
      (set-test-computer-map! (build-test-map ["X#########f.............."]))
      (let [unit (get-in (test-utils/read-test-state :game-map) [10 0 :contents])]
        (fighter/process-fighter [10 0] unit)
        ;; After 1 outbound step, should switch to :regular with target = landing site
        ;; Fighter navigates back but can't reach [0 0] in remaining 7 steps
        (let [result (get-test-unit (test-utils/game-map-atom) "f")]
          (should-not-be-nil result)
          (should= :regular (:flight-mode (:unit result)))
          (should= [0 0] (:flight-target-site (:unit result))))))

    (it "sortie step prefers cells with more unexplored neighbors"
      ;; 3-row map: fighter at [2 1], unexplored only at row 0
      ;; Exploration should prefer moving toward row 0 (more unexplored neighbors)
      (set-test-world! (build-test-map ["#####"
                                               "##f##"
                                               "#####"]))
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 20
                     :flight-mode :explore
                     :explore-origin [0 1]
                     :explore-landing-site [0 1]
                     :explore-heading [0 1]
                     :explore-steps-remaining 10
                     :flight-target-site [4 1])
      ;; Only row 0 is unexplored
      (set-test-computer-map! (build-test-map ["-----"
                                                   "##f##"
                                                   "#####"]))
      (let [unit (get-in (test-utils/read-test-state :game-map) [2 1 :contents])]
        (fighter/process-fighter [2 1] unit)
        ;; Fighter should have moved — preferring cells near unexplored row 0
        (let [result (get-test-unit (test-utils/game-map-atom) "f")]
          (should-not-be-nil result)
          (should-not= [2 1] (:pos result))))))

  (context "exploration movement"
    (it "exploration sortie decrements outbound steps"
      (set-test-world! (build-test-map ["X####f######"]))
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 20
                     :flight-mode :explore
                     :explore-origin [0 0]
                     :explore-landing-site [0 0]
                     :explore-heading [0 1]
                     :explore-steps-remaining 3
                     :flight-target-site [10 0])
      (set-test-computer-map! (build-test-map ["X####f......"]))
      (let [unit (get-in (test-utils/read-test-state :game-map) [5 0 :contents])]
        (fighter/process-fighter [5 0] unit)
        (let [result (get-test-unit (test-utils/game-map-atom) "f")]
          (should-not-be-nil result)
          (should (< (:explore-steps-remaining (:unit result)) 3))))))

  (context "handle-arrival cleanup"
    (it "arrival replaces stale exploration fields with a new sortie from the staging city"
      ;; Fighter arriving at target city should clear stale explore state and
      ;; re-seed exploration from the arrival city.
      (set-test-world! (build-test-map ["X#fX"]))
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 20
                     :flight-target-site [3 0]
                     :flight-origin-site [0 0]
                     :flight-mode :regular
                     :explore-origin [0 0]
                     :explore-landing-site [0 0]
                     :explore-heading [0 1]
                     :explore-steps-remaining 0)
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [unit (get-in (test-utils/read-test-state :game-map) [2 0 :contents])]
        (fighter/process-fighter [2 0] unit)
        ;; Stale exploration fields should be replaced with a fresh sortie from [3 0].
        (let [result (get-test-unit (test-utils/game-map-atom) "f")]
          (should-not-be-nil result)
          (should= [3 0] (:explore-origin (:unit result)))
          (should-not-be-nil (:explore-landing-site (:unit result)))
          (should-not-be-nil (:explore-heading (:unit result)))
          (should-not-be-nil (:flight-target-site (:unit result)))))))

  (context "returning sortie arrival"
    (it "does not crash when origin equals target (returning sortie)"
      ;; A returning sortie has flight-target-site == flight-origin-site (same city).
      ;; handle-arrival must not try to create #{origin origin} which throws.
      (set-test-world! (build-test-map ["XfX"]))
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 20
                     :flight-target-site [0 0]
                     :flight-origin-site [0 0]
                     :flight-mode :regular)
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [unit (get-in (test-utils/read-test-state :game-map) [1 0 :contents])]
        (fighter/process-fighter [1 0] unit)
        ;; Fighter should still exist (landed or moved, no crash)
        ;; Either the fighter landed at city or is somewhere on the map
        (let [fighter (get-test-unit (test-utils/game-map-atom) "f")
              city-fighters (:fighter-count (get-in (test-utils/read-test-state :game-map) [0 0]))]
          (should (or fighter (and city-fighters (pos? city-fighters))))))))
