(ns empire.atoms-spec
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.state.api :as sa]
            [empire.config.core :as config]
            [empire.config.domain.core.messages :as messages]
            [empire.config.domain.core.refueling :as refueling]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-world!]]))

(describe "set-error-message"
  (before (reset-all-atoms!))

  (it "sets the error message text"
    (sa/write-state! :error-message "test error")
    (sa/write-state! :error-until (messages/expires-at (System/currentTimeMillis) config/error-message-duration))
    (should= "test error" (test-utils/read-test-state :error-message)))

  (it "sets error-until to a future timestamp"
    (let [before (System/currentTimeMillis)]
      (sa/write-state! :error-message "test error")
      (sa/write-state! :error-until (messages/expires-at (System/currentTimeMillis) config/error-message-duration))
      (should (>= (test-utils/read-test-state :error-until) (+ before config/error-message-duration)))))

  (it "error expires after the specified duration"
    (sa/write-state! :error-message "vanishing error")
    (sa/write-state! :error-until (messages/expires-at (System/currentTimeMillis) 50))
    (should (< (System/currentTimeMillis) (test-utils/read-test-state :error-until)))
    (Thread/sleep 60)
    (should (>= (System/currentTimeMillis) (test-utils/read-test-state :error-until)))))

(describe "computer-city-cell?"
  (it "returns true for computer city"
    (should (refueling/computer-city-cell? {:type :city :city-status :computer})))

  (it "returns false for player city"
    (should-not (refueling/computer-city-cell? {:type :city :city-status :player})))

  (it "returns false for free city"
    (should-not (refueling/computer-city-cell? {:type :city :city-status :free})))

  (it "returns false for non-city"
    (should-not (refueling/computer-city-cell? {:type :sea}))))

(describe "computer-carrier-cell?"
  (it "returns true for computer carrier cell"
    (should (refueling/computer-carrier-cell? {:contents {:type :carrier :owner :computer}})))

  (it "returns false for player carrier cell"
    (should-not (refueling/computer-carrier-cell? {:contents {:type :carrier :owner :player}})))

  (it "returns false for computer non-carrier cell"
    (should-not (refueling/computer-carrier-cell? {:contents {:type :destroyer :owner :computer}})))

  (it "returns false for empty cell"
    (should-not (refueling/computer-carrier-cell? {:type :sea}))))

(describe "rebuild-refueling-caches!"
  (before (reset-all-atoms!))

  (it "populates computer-city-positions from game map"
    (let [game-map (build-test-map ["X~O"])]
      (set-test-world! game-map)
      (sa/rebuild-refueling-caches!)
      (should= #{[0 0]} (test-utils/read-test-state :computer-city-positions))))

  (it "populates computer-carrier-positions from game map"
    (let [game-map (build-test-map ["c~C"])]
      (set-test-world! game-map)
      (sa/rebuild-refueling-caches!)
      (should= #{[0 0]} (test-utils/read-test-state :computer-carrier-positions))))

  (it "finds both cities and carriers"
    (let [game-map (build-test-map ["X~c"])]
      (set-test-world! game-map)
      (sa/rebuild-refueling-caches!)
      (should= #{[0 0]} (test-utils/read-test-state :computer-city-positions))
      (should= #{[2 0]} (test-utils/read-test-state :computer-carrier-positions))))

  (it "ignores player cities and carriers"
    (let [game-map (build-test-map ["O~C"])]
      (set-test-world! game-map)
      (sa/rebuild-refueling-caches!)
      (should= #{} (test-utils/read-test-state :computer-city-positions))
      (should= #{} (test-utils/read-test-state :computer-carrier-positions))))

  (it "returns empty sets for empty map"
    (let [game-map (build-test-map ["~~"])]
      (set-test-world! game-map)
      (sa/rebuild-refueling-caches!)
      (should= #{} (test-utils/read-test-state :computer-city-positions))
      (should= #{} (test-utils/read-test-state :computer-carrier-positions))))

  (it "finds multiple computer cities"
    (let [game-map (build-test-map ["X~X"])]
      (set-test-world! game-map)
      (sa/rebuild-refueling-caches!)
      (should= #{[0 0] [2 0]} (test-utils/read-test-state :computer-city-positions))))

  (it "ignores free cities"
    (let [game-map (build-test-map ["+~X"])]
      (set-test-world! game-map)
      (sa/rebuild-refueling-caches!)
      (should= #{[2 0]} (test-utils/read-test-state :computer-city-positions)))))

(describe "merge-continents!"
  (before (reset-all-atoms!))

  (it "ignores nil country ids"
    (sa/merge-continents! nil 2)
    (should= {} (test-utils/read-test-state :continent-groups)))

  (it "ignores merge with same country id"
    (sa/merge-continents! 3 3)
    (should= {} (test-utils/read-test-state :continent-groups)))

  (it "merges two previously separate countries"
    (sa/merge-continents! 1 2)
    (should (sa/on-same-continent? 1 2))
    (should= {1 1 2 1} (test-utils/read-test-state :continent-groups)))

  (it "does nothing when groups are already merged"
    (test-utils/set-test-state! :continent-groups {1 1 2 1 3 3})
    (let [before (test-utils/read-test-state :continent-groups)]
      (sa/merge-continents! 1 2)
      (should= before (test-utils/read-test-state :continent-groups))))

  (it "merges by canonical group and rewrites existing members"
    (test-utils/set-test-state! :continent-groups {1 1 2 1 3 3 4 3})
    (sa/merge-continents! 2 3)
    (should (sa/on-same-continent? 1 3))
    (should (sa/on-same-continent? 1 4))
    (should= {1 1 2 1 3 1 4 1} (test-utils/read-test-state :continent-groups)))

  (it "supports transitive merging across multiple calls"
    (sa/merge-continents! 10 11)
    (sa/merge-continents! 11 12)
    (should (sa/on-same-continent? 10 12))
    (should= {10 10 11 10 12 10} (test-utils/read-test-state :continent-groups))))

(run-specs)
