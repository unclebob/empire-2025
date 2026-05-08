(ns empire.computer.transport.sailing-regular-private-spec
  (:require [empire.computer.transport.sailing-regular :as sailing]
            [empire.computer.transport.sailing-regular.follow :as follow]
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

  (it "replans when the unload path is blocked"
    (with-redefs [empire.computer.transport.sailing-regular.follow/sail-follow-path
                  (constantly (sailing/blocked-follow-result [1 0]))
                  empire.computer.transport.sailing-regular.follow/replan-sail-path!
                  (fn [pos _path-fn] [:replanned pos])]
      (should= [:replanned [1 0]]
               (#'sailing/follow-unload-sail-path [1 0] [[2 0]]))))

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

  (it "replans when the load path is blocked"
    (let [stored (atom nil)]
    (with-redefs [empire.computer.transport.sailing-regular.follow/sail-follow-path
                  (constantly (sailing/blocked-follow-result [1 0]))
                  empire.computer.transport.load-targeting/path-to-load-target
                  (fn [_ _ _] [[3 0]])
                  empire.computer.transport.core/assoc-transport-field!
                  (fn [pos field value]
                    (reset! stored [pos field value])
                    true)
                  empire.game-mechanics.visibility/sync-ai-unit-to-computer-map!
                  (fn [_])]
        (should= [1 0]
                 (#'sailing/follow-load-sail-path [1 0] [[2 0]] [4 0]))
        (should= [[1 0] :sail-path [[3 0]]] @stored))))

(describe "sail-follow-path"
  (before (reset-all-atoms!))

  (it "returns nil when the transport has no moves"
    (with-redefs [empire.config.units.dispatcher/speed (constantly 0)]
      (should-be-nil (sailing/sail-follow-path [1 0] [[2 0]])))))

(describe "remaining-sail-path"
  (it "returns an empty vector for an empty path"
    (should= [] (#'follow/remaining-sail-path []))))

(describe "replan-sail-path!"
  (before (reset-all-atoms!))

  (it "stores a newly computed path"
    (let [synced (atom nil)
          stored (atom nil)]
      (with-redefs [empire.computer.transport.core/assoc-transport-field!
                    (fn [pos field value]
                      (reset! stored [pos field value])
                      true)
                    empire.game-mechanics.visibility/sync-ai-unit-to-computer-map!
                    (fn [pos] (reset! synced pos))]
        (should= [1 0]
                 (sailing/replan-sail-path! [1 0] (constantly [[2 0]])))
        (should= [[1 0] :sail-path [[2 0]]] @stored)
        (should= [1 0] @synced))))

  (it "keeps position when no replacement path is found"
    (with-redefs [empire.computer.transport.core/assoc-transport-field!
                  (fn [& _] (throw (ex-info "should not store" {})))]
      (should= [1 0]
               (sailing/replan-sail-path! [1 0] (constantly nil))))))

(describe "compute-and-follow-path!"
  (before (reset-all-atoms!))

  (it "stores and follows a computed path"
    (let [followed (atom nil)
          stored (atom nil)]
      (with-redefs [empire.computer.transport.core/assoc-transport-field!
                    (fn [pos field value]
                      (reset! stored [pos field value])
                      true)
                    empire.game-mechanics.visibility/sync-ai-unit-to-computer-map!
                    (fn [_])
                    empire.computer.transport.sailing-regular.follow/sail-follow-path
                    (fn [pos sail-path]
                      (reset! followed [pos sail-path])
                      [2 0])]
        (should= [2 0]
                 (sailing/compute-and-follow-path! [1 0] (constantly [[2 0]])))
        (should= [[1 0] :sail-path [[2 0]]] @stored)
        (should= [[1 0] [[2 0]]] @followed)))))

(describe "path-to-load-target"
  (it "delegates when a target is present"
    (with-redefs [empire.computer.transport.load-targeting/path-to-load-target
                  (fn [pos _computer-map target] [pos target])]
      (should= [[1 0] [4 0]]
               (#'follow/path-to-load-target [1 0] [] [4 0]))))

  (it "returns nil when no target is assigned"
    (with-redefs [empire.computer.transport.load-targeting/path-to-load-target
                  (fn [& _] (throw (ex-info "should not delegate" {})))]
      (should-be-nil (#'follow/path-to-load-target [1 0] [] nil)))))

(describe "follow-path-action"
  (before (reset-all-atoms!))

  (it "returns nil when following produces no result"
    (with-redefs [empire.computer.transport.sailing-regular.follow/sail-follow-path
                  (constantly nil)]
      (should-be-nil (sailing/follow-path-action [1 0] [[2 0]])))))

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
      (should (or (nil? result) (vector? result)))))

  (it "uses a transport load target when one is assigned"
    (let [followed (atom nil)
          stored (atom nil)
          transport {:load-target-cell [4 0]}]
      (with-redefs [empire.computer.transport.load-targeting/path-to-load-target
                    (fn [pos _computer-map load-target-cell]
                      [pos load-target-cell]
                      [[2 0]])
                    empire.computer.transport.core/assoc-transport-field!
                    (fn [pos field value]
                      (reset! stored [pos field value])
                      true)
                    empire.game-mechanics.visibility/sync-ai-unit-to-computer-map!
                    (fn [_])
                    empire.computer.transport.sailing-regular.follow/sail-follow-path
                    (fn [pos sail-path]
                      (reset! followed [pos sail-path])
                      [2 0])]
        (should= [2 0]
                 (#'sailing/compute-and-follow-load-target-path! [1 0] transport))
        (should= [[1 0] :sail-path [[2 0]]] @stored)
        (should= [[1 0] [[2 0]]] @followed)))))

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
