(ns empire.computer-module-dispatch-spec
  "Tests for computer AI modules - post CommandingGeneral refactor.
   Decision logic has been gutted; these tests cover preserved utilities."
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.game.loop.core :as game-loop]
            [empire.computer.coordinator :as computer]
            [empire.computer.army :as army]
            [empire.computer.fighter :as fighter]
            [empire.computer.production :as computer-production]
            [empire.computer.ship :as ship]
            [empire.computer.shared.threat :as threat]
            [empire.computer.transport :as transport]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-world! update-test-world! set-test-player-map! set-test-computer-map!]]))
(describe "VMS AI Unit Modules"
  (before (reset-all-atoms!))

  (context "VMS army module"
    (it "process-army moves army in random-explore direction"
      (set-test-world! (build-test-map ["a#"]))
      (set-test-computer-map! (build-test-map ["a#"]))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :army :owner :computer :hits 1
              :mode :random-explore :random-explore-direction [1 0]})
      (army/process-army [0 0])
      ;; Army should have moved to [1 0]
      (should= :army (get-in (test-utils/read-test-state :game-map) [1 0 :contents :type]))))

  (context "VMS fighter module"
    (it "process-fighter patrols when fuel allows"
      (set-test-world! (build-test-map ["Xf##########A"]))
      (update-test-world! assoc-in [1 0 :contents]
                         {:type :fighter :owner :computer :fuel 20 :hits 1})
      ;; The player army is visible, so patrol has a concrete eastward target.
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (test-utils/update-test-computer-map! assoc-in [1 0 :contents]
                                            {:type :fighter :owner :computer :fuel 20 :hits 1})
      (let [unit (get-in (test-utils/read-test-state :game-map) [1 0 :contents])]
        (fighter/process-fighter [1 0] unit)
        (should-be-nil (get-in (test-utils/read-test-state :game-map) [1 0 :contents]))
        (let [fighter-pos (first (for [c (range 13)
                                       :when (= :fighter (get-in (test-utils/read-test-state :game-map)
                                                                 [c 0 :contents :type]))]
                                   c))]
          (should-not-be-nil fighter-pos)
          (should (> fighter-pos 1))
          (should (< fighter-pos 12))))))

  (context "VMS ship module"
    (it "process-ship stays put when all sea explored"
      (set-test-world! (build-test-map ["d~"]))
      (set-test-computer-map! (build-test-map ["d~"]))
      (ship/process-ship [0 0] :destroyer)
      ;; Destroyer stays put - no unexplored territory
      (should= :destroyer (get-in (test-utils/read-test-state :game-map) [0 0 :contents :type]))))

  (context "VMS transport module"
    (it "process-transport stays put when loading in open sea with no unexplored"
      (set-test-world! [[{:type :sea :contents {:type :transport :owner :computer
                                                       :transport-mission :loading
                                                       :army-count 0}}
                                {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/process-transport [0 0])
      ;; Transport stays put - open sea, no coastal targets, no unexplored territory
      (should= :transport (get-in (test-utils/read-test-state :game-map) [0 0 :contents :type]))))

  (context "VMS production module"
    (it "process-computer-city sets production"
      (set-test-world! (build-test-map ["X+#"]))
      (set-test-computer-map! (build-test-map ["X+#"]))
      (update-test-world! assoc-in [0 0 :country-id] 1)
      (test-utils/set-test-state! :production {})
      (computer-production/process-computer-city [0 0])
      ;; Per-country production fires (0 armies < 10)
      (should-not-be-nil (get (test-utils/read-test-state :production) [0 0])))))
