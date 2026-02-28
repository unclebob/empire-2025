(ns empire.atoms-spec
  (:require [speclj.core :refer :all]
            [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.test-utils :refer [build-test-map reset-all-atoms!]]))

(describe "set-error-message"
  (before (reset-all-atoms!))

  (it "sets the error message text"
    (atoms/set-error-message "test error" config/error-message-duration)
    (should= "test error" @atoms/error-message))

  (it "sets error-until to a future timestamp"
    (let [before (System/currentTimeMillis)]
      (atoms/set-error-message "test error" config/error-message-duration)
      (should (>= @atoms/error-until (+ before config/error-message-duration)))))

  (it "error expires after the specified duration"
    (atoms/set-error-message "vanishing error" 50)
    (should (< (System/currentTimeMillis) @atoms/error-until))
    (Thread/sleep 60)
    (should (>= (System/currentTimeMillis) @atoms/error-until))))

(describe "computer-city-cell?"
  (it "returns true for computer city"
    (should (atoms/computer-city-cell? {:type :city :city-status :computer})))

  (it "returns false for player city"
    (should-not (atoms/computer-city-cell? {:type :city :city-status :player})))

  (it "returns false for free city"
    (should-not (atoms/computer-city-cell? {:type :city :city-status :free})))

  (it "returns false for non-city"
    (should-not (atoms/computer-city-cell? {:type :sea}))))

(describe "computer-carrier-cell?"
  (it "returns true for computer carrier cell"
    (should (atoms/computer-carrier-cell? {:contents {:type :carrier :owner :computer}})))

  (it "returns false for player carrier cell"
    (should-not (atoms/computer-carrier-cell? {:contents {:type :carrier :owner :player}})))

  (it "returns false for computer non-carrier cell"
    (should-not (atoms/computer-carrier-cell? {:contents {:type :destroyer :owner :computer}})))

  (it "returns false for empty cell"
    (should-not (atoms/computer-carrier-cell? {:type :sea}))))

(describe "rebuild-refueling-caches!"
  (before (reset-all-atoms!))

  (it "populates computer-city-positions from game map"
    (let [game-map (build-test-map ["X~O"])]
      (reset! atoms/game-map game-map)
      (atoms/rebuild-refueling-caches!)
      (should= #{[0 0]} @atoms/computer-city-positions)))

  (it "populates computer-carrier-positions from game map"
    (let [game-map (build-test-map ["c~C"])]
      (reset! atoms/game-map game-map)
      (atoms/rebuild-refueling-caches!)
      (should= #{[0 0]} @atoms/computer-carrier-positions)))

  (it "finds both cities and carriers"
    (let [game-map (build-test-map ["X~c"])]
      (reset! atoms/game-map game-map)
      (atoms/rebuild-refueling-caches!)
      (should= #{[0 0]} @atoms/computer-city-positions)
      (should= #{[2 0]} @atoms/computer-carrier-positions)))

  (it "ignores player cities and carriers"
    (let [game-map (build-test-map ["O~C"])]
      (reset! atoms/game-map game-map)
      (atoms/rebuild-refueling-caches!)
      (should= #{} @atoms/computer-city-positions)
      (should= #{} @atoms/computer-carrier-positions)))

  (it "returns empty sets for empty map"
    (let [game-map (build-test-map ["~~"])]
      (reset! atoms/game-map game-map)
      (atoms/rebuild-refueling-caches!)
      (should= #{} @atoms/computer-city-positions)
      (should= #{} @atoms/computer-carrier-positions)))

  (it "finds multiple computer cities"
    (let [game-map (build-test-map ["X~X"])]
      (reset! atoms/game-map game-map)
      (atoms/rebuild-refueling-caches!)
      (should= #{[0 0] [2 0]} @atoms/computer-city-positions)))

  (it "ignores free cities"
    (let [game-map (build-test-map ["+~X"])]
      (reset! atoms/game-map game-map)
      (atoms/rebuild-refueling-caches!)
      (should= #{[2 0]} @atoms/computer-city-positions))))

(describe "merge-continents!"
  (before (reset-all-atoms!))

  (it "ignores nil country ids"
    (atoms/merge-continents! nil 2)
    (should= {} @atoms/continent-groups))

  (it "ignores merge with same country id"
    (atoms/merge-continents! 3 3)
    (should= {} @atoms/continent-groups))

  (it "merges two previously separate countries"
    (atoms/merge-continents! 1 2)
    (should (atoms/on-same-continent? 1 2))
    (should= {1 1 2 1} @atoms/continent-groups))

  (it "does nothing when groups are already merged"
    (reset! atoms/continent-groups {1 1 2 1 3 3})
    (let [before @atoms/continent-groups]
      (atoms/merge-continents! 1 2)
      (should= before @atoms/continent-groups)))

  (it "merges by canonical group and rewrites existing members"
    (reset! atoms/continent-groups {1 1 2 1 3 3 4 3})
    (atoms/merge-continents! 2 3)
    (should (atoms/on-same-continent? 1 3))
    (should (atoms/on-same-continent? 1 4))
    (should= {1 1 2 1 3 1 4 1} @atoms/continent-groups))

  (it "supports transitive merging across multiple calls"
    (atoms/merge-continents! 10 11)
    (atoms/merge-continents! 11 12)
    (should (atoms/on-same-continent? 10 12))
    (should= {10 10 11 10 12 10} @atoms/continent-groups)))

(run-specs)
