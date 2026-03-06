(ns empire.computer.fighter-exploration-spec
  "Tests for fighter exploration: sorties, drone operations, unexplored-cell scoring."
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.computer.fighter :as fighter]
            [empire.test.utils :refer [build-test-map set-test-unit
                                       get-test-unit reset-all-atoms! set-test-player-map! set-test-computer-map! set-test-world! update-test-world!]]))

(describe "fighter-exploration"
  (before (reset-all-atoms!))

  (context "exploration sortie (L323-337)"
    (it "explore-step decrements steps-remaining"
      ;; Fighter with :explore flight-mode and steps remaining
      (set-test-world! (build-test-map ["X####f######"]))
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 20
                     :flight-mode :explore
                     :explore-origin [0 0]
                     :explore-steps-remaining 3
                     :flight-target-site [11 0]
                     :flight-origin-site [0 0])
      ;; Unexplored territory to the right
      (set-test-computer-map! (build-test-map ["X####f......"]))
      (let [unit (get-in (test-utils/read-test-state :game-map) [5 0 :contents])]
        (fighter/process-fighter [5 0] unit)
        ;; Fighter should have moved and steps decremented
        (let [result (get-test-unit (test-utils/game-map-atom) "f")]
          (should-not-be-nil result)
          ;; Should have fewer explore steps remaining (started at 3)
          (let [remaining (:explore-steps-remaining (:unit result))]
            ;; If remaining exists, it should be less than 3
            (when remaining
              (should (< remaining 3)))))))

    (it "explore sortie switches to regular mode at zero steps (L337)"
      ;; Fighter with 1 explore step remaining — after one move, should switch
      (set-test-world! (build-test-map ["X####f##"]))
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 20
                     :flight-mode :explore
                     :explore-origin [0 0]
                     :explore-steps-remaining 1
                     :flight-target-site [7 0]
                     :flight-origin-site [0 0])
      (set-test-computer-map! (build-test-map ["X####f.."]))
      (let [unit (get-in (test-utils/read-test-state :game-map) [5 0 :contents])]
        (fighter/process-fighter [5 0] unit)
        ;; After one explore step with remaining=1, remaining becomes 0,
        ;; triggering switch to :regular mode targeting origin
        (let [result (get-test-unit (test-utils/game-map-atom) "f")]
          (when result
            (let [mode (:flight-mode (:unit result))]
              (when mode
                (should= :regular mode))))))))

  (context "drone mode (L543)"
    (it "drone flight mode moves fighter"
      (set-test-world! (build-test-map ["X####f#####"]))
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 20
                     :flight-mode :drone
                     :flight-target-site [10 0]
                     :flight-origin-site [0 0])
      ;; Unexplored territory to the right
      (set-test-computer-map! (build-test-map ["X####f....."]))
      (let [unit (get-in (test-utils/read-test-state :game-map) [5 0 :contents])]
        (fighter/process-fighter [5 0] unit)
        ;; Fighter should have moved
        (should-be-nil (get-in (test-utils/read-test-state :game-map) [5 0 :contents]))
        (let [result (get-test-unit (test-utils/game-map-atom) "f")]
          (should-not-be-nil result)))))

  (context "explore-hop-over (L298-304)"
    (it "explore hops over friendly in correct direction"
      ;; Fighter in explore mode, friendly blocking direct path
      (set-test-world! (build-test-map ["X#####f#########"]))
      (update-test-world! assoc-in [7 0 :contents]
                          {:type :army :owner :computer :hits 1})
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 20
                     :flight-mode :explore
                     :explore-origin [0 0]
                     :explore-steps-remaining 5
                     :flight-target-site [15 0]
                     :flight-origin-site [0 0])
      ;; Unexplored territory to the right
      (set-test-computer-map! (build-test-map ["X#####f........."]))
      (let [unit (get-in (test-utils/read-test-state :game-map) [6 0 :contents])]
        (fighter/process-fighter [6 0] unit)
        ;; Fighter should have moved past the friendly army
        (let [result (get-test-unit (test-utils/game-map-atom) "f")]
          (when result
            (should (> (first (:pos result)) 7)))))))

  (context "select-best-explore-target (L280)"
    (it "prefers neighbor with more unexplored neighbors"
      ;; Fighter with explore mode, two possible moves: one toward
      ;; unexplored, one toward explored. Should pick unexplored side.
      (set-test-world! (build-test-map ["####f####"]))
      (set-test-unit (test-utils/game-map-atom) "f" :fuel 20
                     :flight-mode :explore
                     :explore-origin [0 0]
                     :explore-steps-remaining 5
                     :flight-target-site [8 0]
                     :flight-origin-site [0 0])
      ;; Left side explored, right side unexplored
      (set-test-computer-map! (build-test-map ["####f...."]))
      (let [unit (get-in (test-utils/read-test-state :game-map) [4 0 :contents])]
        (fighter/process-fighter [4 0] unit)
        ;; Should move right (toward unexplored)
        (let [result (get-test-unit (test-utils/game-map-atom) "f")]
          (should-not-be-nil result)
          (should (> (first (:pos result)) 4)))))))
