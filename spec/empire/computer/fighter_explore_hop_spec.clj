(ns empire.computer.fighter-explore-hop-spec
  (:require [speclj.core :refer :all]
            [empire.computer.fighter :as fighter]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [build-test-map set-test-unit
                                       get-test-unit reset-all-atoms!]]))

(describe "explore-hop-over"
  (before (reset-all-atoms!))

  (context "hop over one friendly to empty cell"
    (it "moves fighter past friendly army to empty inland cell"
      ;; Wide map so fighter stays in explore mode for all 8 steps
      (reset! atoms/game-map (build-test-map ["Xfa###########"]))
      (set-test-unit atoms/game-map "f"
                     :fuel 20
                     :flight-mode :explore
                     :explore-steps-remaining 20
                     :flight-target-site [13 0]
                     :explore-origin [0 0])
      (reset! atoms/computer-map (build-test-map ["Xfa..........."]))
      (let [unit (get-in @atoms/game-map [1 0 :contents])]
        (fighter/process-fighter [1 0] unit))
      ;; Army should still be at [2,0]
      (should= :army (get-in @atoms/game-map [2 0 :contents :type]))
      ;; Fighter should have hopped over army and moved well past it
      (should-be-nil (get-in @atoms/game-map [1 0 :contents]))
      (let [result (get-test-unit atoms/game-map "f")]
        (should-not-be-nil result)
        (should (> (first (:pos result)) 2)))))

  (context "hop over multiple friendlies"
    (it "hops over two consecutive friendly armies"
      (reset! atoms/game-map (build-test-map ["Xfaa###########"]))
      (set-test-unit atoms/game-map "f"
                     :fuel 20
                     :flight-mode :explore
                     :explore-steps-remaining 20
                     :flight-target-site [14 0]
                     :explore-origin [0 0])
      (reset! atoms/computer-map (build-test-map ["Xfaa..........."]))
      (let [unit (get-in @atoms/game-map [1 0 :contents])]
        (fighter/process-fighter [1 0] unit))
      ;; Armies untouched
      (should= :army (get-in @atoms/game-map [2 0 :contents :type]))
      (should= :army (get-in @atoms/game-map [3 0 :contents :type]))
      ;; Fighter past both armies
      (let [result (get-test-unit atoms/game-map "f")]
        (should-not-be-nil result)
        (should (> (first (:pos result)) 3)))))

  (context "hop reaches map edge"
    (it "returns nil when next cell after friendly is off-map"
      ;; 3-col map: fighter at [1,0], army at [2,0], [3,0] is OOB
      (reset! atoms/game-map (build-test-map ["Xfa"]))
      (set-test-unit atoms/game-map "f"
                     :fuel 20
                     :flight-mode :explore
                     :explore-steps-remaining 20
                     :flight-target-site [2 0]
                     :explore-origin [0 0])
      (reset! atoms/computer-map @atoms/game-map)
      (let [unit (get-in @atoms/game-map [1 0 :contents])]
        (fighter/process-fighter [1 0] unit))
      ;; Fighter couldn't hop — stayed at [1,0] and burned fuel
      (let [result (get-test-unit atoms/game-map "f")]
        (should-not-be-nil result)
        (should= [1 0] (:pos result)))))

  (context "hop blocked by enemy"
    (it "returns nil when cell after friendly chain is enemy-occupied"
      ;; [0,0]=city [1,0]=fighter [2,0]=comp-army [3,0]=player-army [4..13]=land
      (reset! atoms/game-map (build-test-map ["XfaA##########"]))
      (set-test-unit atoms/game-map "f"
                     :fuel 20
                     :flight-mode :explore
                     :explore-steps-remaining 20
                     :flight-target-site [13 0]
                     :explore-origin [0 0])
      (reset! atoms/computer-map (build-test-map ["Xfa..........."]))
      (let [unit (get-in @atoms/game-map [1 0 :contents])]
        (fighter/process-fighter [1 0] unit))
      ;; The friendly army at [2,0] should be untouched
      (should= :army (get-in @atoms/game-map [2 0 :contents :type]))
      (should= :computer (get-in @atoms/game-map [2 0 :contents :owner])))))

(run-specs)
