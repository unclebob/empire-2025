(ns empire.computer.transport-oscillation-spec
  (:require [speclj.core :refer :all]
            [empire.computer.transport :as transport]
            [empire.test.utils :as tu :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-world! update-test-world!]]))

(describe "transport oscillation recovery"
  (before (reset-all-atoms!))

  (it "enters 5-round random walk when oscillation is detected and then restores transport mission state"
    (let [gm (build-test-map ["t"])
          oscillating-history [[0 0] [0 1] [0 0] [0 1] [0 0] [0 1] [0 0] [0 1] [0 0] [0 1]]]
      (set-test-world! gm)
      (set-test-computer-map! gm)
      (update-test-world! assoc-in [0 0 :contents]
                          {:type :transport :owner :computer
                           :transport-mission :sailing
                           :army-count 2
                           :sail-path [[4 0] [5 0]]
                           :oscillation-history oscillating-history})
      (set-test-computer-map! (tu/read-test-state :game-map))

      (transport/process-transport [0 0])
      (should= 4 (get-in (tu/read-test-state :game-map) [0 0 :contents :oscillation-random-walk-rounds-left]))

      (dotimes [_ 4]
        (transport/process-transport [0 0]))

      (let [unit (get-in (tu/read-test-state :game-map) [0 0 :contents])]
        (should= :sailing (:transport-mission unit))
        (should= [[4 0] [5 0]] (:sail-path unit))
        (should-be-nil (:oscillation-random-walk-rounds-left unit))
        (should-be-nil (:oscillation-restore unit))))))
