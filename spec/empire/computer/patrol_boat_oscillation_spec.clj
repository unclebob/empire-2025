(ns empire.computer.patrol-boat-oscillation-spec
  (:require [speclj.core :refer :all]
            [empire.computer.ship :as ship]
            [empire.test.utils :as tu :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-world! update-test-world!]]))

(describe "patrol boat oscillation recovery"
  (before (reset-all-atoms!))

  (it "enters 5-round random walk when oscillation is detected and then restores patrol state"
    (let [gm (build-test-map ["p"])
          oscillating-history [[0 0] [0 1] [0 0] [0 1]
                               [0 0] [0 1] [0 0] [0 1]
                               [0 0] [0 1] [0 0] [0 1]
                               [0 0] [0 1] [0 0] [0 1]]]
      (set-test-world! gm)
      (set-test-computer-map! gm)
      (update-test-world! assoc-in [0 0 :contents]
                          {:type :patrol-boat :owner :computer :hits 1
                           :patrol-mode :exploring
                           :explore-path [[9 9]]
                           :oscillation-history oscillating-history})

      (ship/process-ship [0 0] :patrol-boat)
      (should= 4 (get-in (tu/read-test-state :game-map) [0 0 :contents :oscillation-random-walk-rounds-left]))
      (should= :exploring (get-in (tu/read-test-state :game-map) [0 0 :contents :patrol-mode]))

      (dotimes [_ 4]
        (ship/process-ship [0 0] :patrol-boat))

      (let [unit (get-in (tu/read-test-state :game-map) [0 0 :contents])]
        (should= :exploring (:patrol-mode unit))
        (should= [[9 9]] (:explore-path unit))
        (should-be-nil (:oscillation-random-walk-rounds-left unit))
        (should-be-nil (:oscillation-restore unit))))))
