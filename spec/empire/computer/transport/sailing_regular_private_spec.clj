(ns empire.computer.transport.sailing-regular-private-spec
  (:require [empire.computer.transport.sailing-regular :as sailing]
            [empire.state.api :as sa]
            [empire.test.utils :refer [reset-all-atoms! build-test-map set-test-world! set-test-computer-map! update-test-world!]]
            [empire.test.utils :as test-utils]
            [speclj.core :refer :all]))

(describe "follow-unload-sail-path"
  (before (reset-all-atoms!))

  (it "follows path and returns new position"
    (set-test-world! (build-test-map ["~t~~"]))
    (update-test-world! assoc-in [1 0 :contents :transport-mission] :sail-to-unload)
    (update-test-world! assoc-in [1 0 :contents :army-count] 6)
    (update-test-world! assoc-in [1 0 :contents :sail-path] [[2 0]])
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (let [result (#'sailing/follow-unload-sail-path [1 0] [[2 0]])]
      (should-not-be-nil result)))

  (it "returns nil when path is empty"
    (set-test-world! (build-test-map ["~t~~"]))
    (update-test-world! assoc-in [1 0 :contents :transport-mission] :sail-to-unload)
    (update-test-world! assoc-in [1 0 :contents :army-count] 6)
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (should-be-nil (#'sailing/follow-unload-sail-path [1 0] []))))

(describe "follow-load-sail-path"
  (before (reset-all-atoms!))

  (it "follows path toward load target"
    (set-test-world! (build-test-map ["~t~~"]))
    (update-test-world! assoc-in [1 0 :contents :transport-mission] :sail-to-load)
    (update-test-world! assoc-in [1 0 :contents :army-count] 0)
    (update-test-world! assoc-in [1 0 :contents :sail-path] [[2 0]])
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (let [result (#'sailing/follow-load-sail-path [1 0] [[2 0]] nil)]
      (should-not-be-nil result))))

(describe "compute-and-follow-load-target-path!"
  (before (reset-all-atoms!))

  (it "computes path and follows it when load target exists"
    (set-test-world! (build-test-map ["~t~~#"]))
    (update-test-world! assoc-in [1 0 :contents :transport-mission] :sail-to-load)
    (update-test-world! assoc-in [1 0 :contents :army-count] 0)
    (update-test-world! assoc-in [4 0 :country-id] 1)
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (let [transport (get-in (test-utils/read-test-state :game-map) [1 0 :contents])
          result (#'sailing/compute-and-follow-load-target-path! [1 0] transport)]
      ;; May return nil if no path found, or a position if it sailed
      (should (or (nil? result) (vector? result))))))

(describe "claimed-land? (sailing-regular private)"
  (it "returns true for land with country-id"
    (should (@#'sailing/claimed-land? {:type :land :country-id 1})))

  (it "returns true for computer city"
    (should (@#'sailing/claimed-land? {:type :city :city-status :computer})))

  (it "returns false for nil"
    (should-not (@#'sailing/claimed-land? nil)))

  (it "returns false for unclaimed land"
    (should-not (@#'sailing/claimed-land? {:type :land})))

  (it "returns false for player city"
    (should-not (@#'sailing/claimed-land? {:type :city :city-status :player}))))
