(ns empire.computer.transport.sailing-regular-helpers-spec
  (:require [empire.computer.transport.sailing-regular :as sailing]
            [empire.state.api :as sa]
            [empire.test.utils :refer [reset-all-atoms! build-test-map set-test-computer-map! set-test-world! update-test-world!]]
            [empire.test.utils :as test-utils]
            [speclj.core :refer :all]))

(describe "claimed-land? (sailing-regular)"
  (it "returns true for land with country-id"
    (should (@#'sailing/claimed-land? {:type :land :country-id 1})))

  (it "returns true for computer city"
    (should (@#'sailing/claimed-land? {:type :city :city-status :computer})))

  (it "returns false for nil cell"
    (should-not (@#'sailing/claimed-land? nil)))

  (it "returns false for unclaimed land"
    (should-not (@#'sailing/claimed-land? {:type :land})))

  (it "returns false for sea"
    (should-not (@#'sailing/claimed-land? {:type :sea}))))

(describe "transition-to-loading!"
  (before (reset-all-atoms!))

  (it "sets mission to loading when transport has load-manifest"
    (set-test-world! (build-test-map ["~t~"]))
    (update-test-world! assoc-in [1 0 :contents :transport-mission] :sail-to-load)
    (update-test-world! assoc-in [1 0 :contents :load-manifest] [[0 0]])
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (#'sailing/transition-to-loading! [1 0])
    (should= :loading (get-in (test-utils/read-test-state :game-map) [1 0 :contents :transport-mission])))

  (it "enters hold-sail-to-load when no load-manifest"
    (set-test-world! (build-test-map ["~t~"]))
    (update-test-world! assoc-in [1 0 :contents :transport-mission] :sail-to-load)
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (#'sailing/transition-to-loading! [1 0])
    (should= :hold-sail-to-load (get-in (test-utils/read-test-state :game-map) [1 0 :contents :transport-mission]))))
