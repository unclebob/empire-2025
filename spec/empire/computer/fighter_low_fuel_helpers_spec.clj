(ns empire.computer.fighter-low-fuel-helpers-spec
  "Tests for fighter orchestrator: leg coverage, navigation, state machine."
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.computer.fighter :as fighter]
            [empire.computer.fighter.movement :as fighter-movement]
            [empire.game-mechanics.services.combat :as combat]
            [empire.config.core :as config]
            [empire.test.utils :refer [build-test-map set-test-unit
                                       get-test-unit reset-all-atoms! set-test-computer-map! set-test-world! update-test-world!]]))
(describe "handle-low-fuel helpers"
  (before (reset-all-atoms!))

  (it "adjacent-to-city-site? returns true for adjacent city site"
    (set-test-world! (build-test-map ["Xf"]))
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (should (@#'fighter/adjacent-to-city-site? [0 0] [1 0])))

  (it "adjacent-to-city-site? returns false for nil site"
    (set-test-world! (build-test-map ["#f"]))
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (should-not (@#'fighter/adjacent-to-city-site? nil [1 0])))

  (it "adjacent-to-city-site? returns false for non-city site"
    (set-test-world! (build-test-map ["#f"]))
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (should-not (@#'fighter/adjacent-to-city-site? [0 0] [1 0])))

  (it "adjacent-to-city-site? returns false for distant city"
    (set-test-world! (build-test-map ["X#f"]))
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (should-not (@#'fighter/adjacent-to-city-site? [0 0] [2 0])))

  (it "adjacent-to-site? returns true for adjacent site"
    (should (@#'fighter/adjacent-to-site? [0 0] [1 0])))

  (it "adjacent-to-site? returns false for nil site"
    (should-not (@#'fighter/adjacent-to-site? nil [1 0])))

  (it "adjacent-to-site? returns false for distant site"
    (should-not (@#'fighter/adjacent-to-site? [0 0] [3 0])))

  (it "candidate-refueling-sites excludes the exploration endpoint"
    (with-redefs [empire.computer.fighter.movement/find-nearest-refueling-site (constantly [9 9])]
      (should= '([0 0] [7 0] [9 9])
               (seq (@#'fighter/candidate-refueling-sites
                     [1 0]
                     {:flight-mode :explore
                      :explore-landing-site [0 0]
                      :flight-target-site [2 0]
                      :flight-origin-site [7 0]})))))

  (it "desperate-patrol returns nil when do-patrol returns nil"
    (set-test-world! (build-test-map ["f"]))
    (set-test-unit (test-utils/game-map-atom) "f" :fuel 5)
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (should-be-nil (@#'fighter/desperate-patrol [0 0])))

  (it "desperate-patrol returns result when patrol succeeds"
    (set-test-world! (build-test-map ["f#A"]))
    (set-test-unit (test-utils/game-map-atom) "f" :fuel 5)
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (let [result (@#'fighter/desperate-patrol [0 0])]
      (should-not-be-nil result)
      (should (contains? result :pos))
      (should (contains? result :hops)))))
