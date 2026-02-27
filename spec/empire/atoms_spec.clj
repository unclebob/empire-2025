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

(run-specs)
