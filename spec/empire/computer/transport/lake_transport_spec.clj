(ns empire.computer.transport.lake-transport-spec
  (:require [empire.computer.transport.mission-handlers :as handlers]
            [empire.computer.ship.lake-naval :as lake-naval]
            [empire.state.api :as sa]
            [empire.test.utils :refer [reset-all-atoms! build-test-map set-test-world! set-test-computer-map! update-test-world!]]
            [empire.test.utils :as test-utils]
            [speclj.core :refer :all]))

(describe "maybe-handle-lake-transport"
  (before (reset-all-atoms!))

  (it "returns nil for non-lake-locked transport"
    (set-test-world! (build-test-map ["~t~"]))
    (update-test-world! assoc-in [1 0 :contents :transport-mission] :loading)
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (let [transport (get-in (test-utils/read-test-state :game-map) [1 0 :contents])
          deps {:current-world (fn [] (sa/current-world))
                :read-computer-map (fn [] (sa/read-state :computer-map))
                :read-runtime-state (fn [k] (sa/read-state k))
                :update-game-map! sa/update-world!
                :sync-transport! (fn [_] nil)
                :set-transport-mission (fn [_ _] nil)
                :lake-cells lake-naval/lake-cells}]
      (should-be-nil (handlers/maybe-handle-lake-transport deps [1 0] transport))))

  (it "returns true for sentry lake-locked transport"
    (set-test-world! (build-test-map ["~t~"]))
    (update-test-world! assoc-in [1 0 :contents :mode] :sentry)
    (update-test-world! assoc-in [1 0 :contents :lake-locked?] true)
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (let [transport (get-in (test-utils/read-test-state :game-map) [1 0 :contents])
          deps {:current-world (fn [] (sa/current-world))
                :read-computer-map (fn [] (sa/read-state :computer-map))
                :read-runtime-state (fn [k] (sa/read-state k))
                :update-game-map! sa/update-world!
                :sync-transport! (fn [_] nil)
                :set-transport-mission (fn [_ _] nil)
                :lake-cells lake-naval/lake-cells}]
      (should (handlers/maybe-handle-lake-transport deps [1 0] transport)))))
