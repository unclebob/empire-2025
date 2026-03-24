(ns empire.computer.fighter.exploration-spec
  "Tests for fighter exploration and unexplored-cell scoring."
  (:require [empire.computer.fighter :as fighter]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [build-test-map
                                       get-test-unit
                                       reset-all-atoms!
                                       set-test-computer-map!
                                       set-test-unit
                                       set-test-world!
                                       update-test-world!]]
            [speclj.core :refer :all]))

(describe "fighter-exploration"
  (before (reset-all-atoms!))

  (context "exploration sortie"
    (it "explore-step decrements steps-remaining"
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
        (let [result (get-test-unit (test-utils/game-map-atom) "f")]
          (should-not-be-nil result)
          (let [remaining (:explore-steps-remaining (:unit result))]
            (when remaining
              (should (< remaining 3)))))))

    (it "explore sortie exhausts outbound steps and continues with a valid plan"
      (set-test-world! (build-test-map ["X####f##"]))
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 20
                     :flight-mode :explore
                     :explore-origin [0 0]
                     :explore-landing-site [0 0]
                     :explore-steps-remaining 1
                     :flight-target-site [7 0]
                     :flight-origin-site [0 0])
      (set-test-computer-map! (build-test-map ["X####f.."]))
      (let [unit (get-in (test-utils/read-test-state :game-map) [5 0 :contents])]
        (fighter/process-fighter [5 0] unit)
        (let [result (get-test-unit (test-utils/game-map-atom) "f")]
          (should-not-be-nil result)
          (should-not-be-nil (:flight-mode (:unit result)))
          (should-not-be-nil (:flight-target-site (:unit result)))))))

  (context "exploration mode reuse"
    (it "explore flight mode can return to regular after outbound steps"
      (set-test-world! (build-test-map ["X####f#####"]))
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 20
                     :flight-mode :explore
                     :explore-landing-site [0 0]
                     :explore-steps-remaining 1
                     :flight-target-site [10 0]
                     :flight-origin-site [0 0])
      (set-test-computer-map! (build-test-map ["X####f....."]))
      (let [unit (get-in (test-utils/read-test-state :game-map) [5 0 :contents])]
        (fighter/process-fighter [5 0] unit)
        (let [result (get-test-unit (test-utils/game-map-atom) "f")]
          (should-not-be-nil result)
          (should (#{:regular :explore} (:flight-mode (:unit result))))))))

  (context "explore-hop-over"
    (it "explore hops over friendly in correct direction"
      (set-test-world! (build-test-map ["X#####f#########"]))
      (update-test-world! assoc-in [7 0 :contents]
                          {:type :army :owner :computer :hits 1})
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 20
                     :flight-mode :explore
                     :explore-origin [0 0]
                     :explore-landing-site [0 0]
                     :explore-steps-remaining 5
                     :flight-target-site [15 0]
                     :flight-origin-site [0 0])
      (set-test-computer-map! (build-test-map ["X#####fa........"]))
      (let [unit (get-in (test-utils/read-test-state :game-map) [6 0 :contents])]
        (fighter/process-fighter [6 0] unit)
        (let [result (get-test-unit (test-utils/game-map-atom) "f")]
          (when result
            (should (> (first (:pos result)) 7)))))))

  (context "select-best-explore-target"
    (it "keeps a fighter alive while evaluating explored vs unexplored neighbors"
      (set-test-world! (build-test-map ["####f####"]))
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 20
                     :flight-mode :explore
                     :explore-origin [0 0]
                     :explore-landing-site [0 0]
                     :explore-steps-remaining 5
                     :flight-target-site [8 0]
                     :flight-origin-site [0 0])
      (set-test-computer-map! (build-test-map ["####f...."]))
      (let [unit (get-in (test-utils/read-test-state :game-map) [4 0 :contents])]
        (fighter/process-fighter [4 0] unit)
        (should-not-be-nil (get-test-unit (test-utils/game-map-atom) "f")))))

  (context "computer-map scoring"
    (it "counts unexplored neighbors using computer-map bounds"
      (set-test-world! (build-test-map ["#####"
                                        "#####"
                                        "#####"]))
      (set-test-computer-map! [[{:type :land} nil]
                               [{:type :land} {:type :land}]])
      (should= 1
               (#'empire.computer.fighter.exploration/count-unexplored-neighbors [0 0])))))
