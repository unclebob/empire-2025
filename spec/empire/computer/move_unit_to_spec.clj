(ns empire.computer.move-unit-to-spec
  (:require [empire.computer.core :as core]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-player-map! set-test-world! update-test-world!]]
            [speclj.core :refer :all]))

(describe "move-unit-to"
  (before (reset-all-atoms!))

  (it "moves unit from source to destination"
    (set-test-world! (build-test-map ["a~"]))
    (set-test-computer-map! (build-test-map ["a~"]))
    (let [unit (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))]
      (should= [1 0] (core/move-unit-to [0 0] [1 0]))
      (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))
      (should= unit (:contents (get-in (test-utils/read-test-state :game-map) [1 0])))))

  (it "returns nil when destination is occupied"
    (set-test-world! (build-test-map ["ad"]))
    (should-be-nil (core/move-unit-to [0 0] [1 0]))
    (should (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))))

  (it "returns nil when blocked by foreign territory"
    (set-test-world! (build-test-map ["a#"]))
    (update-test-world! assoc-in [0 0 :contents :country-id] 1)
    (update-test-world! assoc-in [1 0 :country-id] 2)
    (should-be-nil (core/move-unit-to [0 0] [1 0]))
    (should (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))))

  (it "allows movement to land with same country-id"
    (set-test-world! (build-test-map ["a#"]))
    (set-test-computer-map! (build-test-map ["a#"]))
    (update-test-world! assoc-in [0 0 :contents :country-id] 1)
    (update-test-world! assoc-in [1 0 :country-id] 1)
    (should= [1 0] (core/move-unit-to [0 0] [1 0])))

  (it "clears old fighter position on computer-map for long move"
    (set-test-world! (build-test-map ["f######"]))
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (should= [6 0] (core/move-unit-to [0 0] [6 0]))
    (should-be-nil (get-in (test-utils/read-test-state :computer-map) [0 0 :contents]))
    (should= :fighter (get-in (test-utils/read-test-state :computer-map) [6 0 :contents :type]))))
